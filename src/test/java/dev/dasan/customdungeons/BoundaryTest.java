package dev.dasan.customdungeons;

import com.sun.source.tree.ImportTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** RF-MOB-08: parse Java so comments, strings, static imports and qualified names are distinct. */
class BoundaryTest {
    private static final String ROOT = "dev.dasan.customdungeons.";
    private static final Set<String> PACKAGES = Set.of("mob", "ability", "boss", "intelligence", "runtime", "text");
    private static final Set<String> MODELS = Set.of("MobTemplate", "MobAttributes", "PhaseDef",
            "AbilityInstance", "ComboDef", "ComboStep", "EquipmentDef", "PotionDef", "ScalingDef",
            "TargetMode", "Trigger", "RewardDef", "WaveEntry"); // PhaseDef's minion entries.
    private static final Set<String> CONFIG = Set.of(
            "NumericRange", "NumericRanges", // Mob stats/ability parameter validation.
            "PluginConfig", // Only its PerformanceLimits value type is consumed by mobs.
            "EntityHeights"); // Mob dimensions for safe live-test spawning, no dungeon data.
    private static final List<String> EXTERNAL = List.of("java.", "javax.", "org.bukkit.",
            "io.papermc.paper.", "com.destroystokyo.paper.", "net.kyori.adventure.", "org.jspecify.annotations.");

    private static boolean allowed(String name) {
        if (!name.startsWith(ROOT)) return EXTERNAL.stream().anyMatch(name::startsWith);
        String[] parts = name.substring(ROOT.length()).split("\\.");
        if (PACKAGES.contains(parts[0])) return true;
        if (parts.length < 2) return false;
        if (parts[0].equals("model")) return MODELS.contains(parts[1]);
        return parts[0].equals("config") && CONFIG.contains(parts[1]);
    }

    private static String qualifiedName(Tree tree) {
        if (tree instanceof IdentifierTree id) return id.getName().toString();
        if (tree instanceof MemberSelectTree select) {
            String parent = qualifiedName(select.getExpression());
            return parent == null ? null : parent + "." + select.getIdentifier();
        }
        return null;
    }

    static boolean crossesBoundary(String source) {
        var file = new SimpleJavaFileObject(URI.create("string:///BoundaryProbe.java"),
                javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, manager,
                    diagnostic -> {}, List.of("-proc:none"), null, List.of(file));
            var scanner = new TreeScanner<Void, Void>() {
                boolean forbidden;
                @Override public Void visitImport(ImportTree tree, Void unused) {
                    forbidden |= !allowed(tree.getQualifiedIdentifier().toString());
                    return null; // Evaluate the whole name, including .* and static members.
                }
                @Override public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                    String name = qualifiedName(tree);
                    if (name != null && name.startsWith(ROOT)) {
                        forbidden |= !allowed(name);
                        return null; // Its partial package prefixes are not type dependencies.
                    }
                    return super.visitMemberSelect(tree, unused);
                }
            };
            for (var unit : task.parse()) scanner.scan(unit, null);
            return scanner.forbidden;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    @Test void rejectsDefinitionStoreAndRootWildcard() {
        assertTrue(crossesBoundary("import dev.dasan.customdungeons.config.DefinitionStore; class X { DefinitionStore store; }"));
        assertTrue(crossesBoundary("import dev.dasan.customdungeons.*; class X { CustomDungeonsPlugin plugin; }"));
    }

    @Test void mobsHaveNoDungeonDependencies() throws Exception {
        Path root = Path.of("src/main/java/dev/dasan/customdungeons");
        for (String name : List.of("mob", "ability", "boss", "intelligence", "runtime")) {
            Path directory = root.resolve(name);
            if (!Files.exists(directory)) continue;
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList())
                    assertFalse(crossesBoundary(Files.readString(file)), file.toString());
            }
        }
    }

    @Test void detectsEveryUnapprovedPackageAndImportForm() {
        for (String name : List.of("session.Forbidden", "storage.Forbidden", "reward.Forbidden",
                "command.Forbidden", "listener.Forbidden", "gui.menu.Forbidden", "tool.Forbidden",
                "model.DungeonDef", "model.RoomDef", "model.Region", "config.DefinitionStore", "CustomDungeonsPlugin")) {
            String type = ROOT + name;
            assertTrue(crossesBoundary("import " + type + ";"), type);
            assertTrue(crossesBoundary("import static " + type + ".*;"), type);
            assertTrue(crossesBoundary("import static " + type + ".VALUE;"), type);
            assertTrue(crossesBoundary("class Example { " + type + " value; }"), type);
            assertTrue(crossesBoundary("class Example { Object value = " + type + ".VALUE; }"), type);
        }
        for (String name : List.of("*", "model.*", "config.*", "session.*", "gui.menu.*"))
            assertTrue(crossesBoundary("import " + ROOT + name + ";"), name);
    }

    @Test void allowsOnlyExplicitMobModelsConfigAndIndependentPackages() {
        for (String model : MODELS) assertFalse(crossesBoundary("import " + ROOT + "model." + model + ";"));
        for (String config : CONFIG) assertFalse(crossesBoundary("import " + ROOT + "config." + config + ";"));
        for (String name : PACKAGES) {
            assertFalse(crossesBoundary("import " + ROOT + name + ".*;"));
            assertFalse(crossesBoundary("import static " + ROOT + name + ".Example.VALUE;"));
        }
        assertFalse(crossesBoundary("import " + ROOT + "config.PluginConfig.PerformanceLimits;"));
        assertFalse(crossesBoundary("// " + ROOT + "session.Example\n/* import " + ROOT + "storage.*; */"));
        assertFalse(crossesBoundary("class Example { String s = \"" + ROOT + "session.Example\"; }"));
        assertTrue(crossesBoundary("import example.other.Library;"));
        assertTrue(crossesBoundary("class X { Object x = " + ROOT + "mob.Factory.make(new "
                + ROOT + "config.DefinitionStore()).value; }"));
        // The Java parser also normalizes whitespace and Java Unicode escapes.
        assertTrue(crossesBoundary("class X { dev . dasan . customdungeons . config . DefinitionStore x; }"));
        assertTrue(crossesBoundary("import dev.dasan.customdungeons." + "\\u0063onfig.DefinitionStore;"));
    }
}

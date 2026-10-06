package dev.dasan.customdungeons.regression;

import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.lang.model.element.ExecutableElement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageKeysTest {
    @Test void literalMessageKeysExistInSpanishAndEffectiveEnglishCatalogs() throws Exception {
        var spanish = catalog("messages.yml");
        var english = catalog("messages_en.yml");
        // The command loader fills missing English entries from bundled Spanish.
        for (String key : spanish.getKeys(true))
            if (spanish.isString(key) && !english.isString(key)) english.set(key, spanish.getString(key));
        var missing = new ArrayList<String>();
        var keys = new TreeSet<String>();
        try (var source = new SourceChecks()) {
            for (var unit : source.units) new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    var symbol = source.trees.getElement(getCurrentPath());
                    if (symbol instanceof ExecutableElement method) {
                        String owner = method.getEnclosingElement().toString();
                        int index = -1;
                        String prefix = "";
                        if (owner.equals("dev.dasan.customdungeons.text.Messages")) {
                            index = switch (method.getSimpleName().toString()) {
                                case "get" -> 0;
                                case "send" -> 1;
                                default -> -1;
                            };
                        } else if (owner.equals("dev.dasan.customdungeons.gui.menu.DungeonEditor")
                                || owner.equals("dev.dasan.customdungeons.gui.menu.DungeonPage")) {
                            // Dungeon helpers build gui.dungeon.<key>, and buttons also use <key>-lore.
                            prefix = "gui.dungeon.";
                            for (int i = 0; i < method.getParameters().size(); i++) {
                                String parameter = method.getParameters().get(i).getSimpleName().toString();
                                if (parameter.equals("key") || parameter.equals("title")) index = i;
                            }
                        }
                        for (String key : index < 0 ? List.<String>of() : literalValues(call.getArguments().get(index))) {
                            var used = new ArrayList<String>();
                            used.add(prefix + key);
                            if (!prefix.isEmpty() && Set.of("action", "add", "number", "integer", "decimal", "text", "toggle", "point", "region")
                                    .contains(method.getSimpleName().toString())) used.add(prefix + key + "-lore");
                            for (String path : used) {
                                keys.add(path);
                                if (!spanish.isString(path) || !english.isString(path))
                                    missing.add(source.location(getCurrentPath()) + " missing " + path);
                            }
                        }
                    }
                    return super.visitMethodInvocation(call, unused);
                }
            }.scan(unit, null);
        }
        assertTrue(keys.contains("gui.dungeon.title"), "Must cover the dungeon editor");
        assertTrue(keys.size() > 50, "Must scan the entire source tree");
        assertEquals(List.of(), missing);
    }
    @Test void dynamicStatKeysExistInBothBundledLanguages() throws Exception {
        for(String resource : List.of("messages.yml","messages_en.yml")) {
            var yaml=catalog(resource);
            for(String key : List.of("health","damage","speed","resistance","scale")) {
                assertTrue(yaml.isString("gui.mob."+key),resource+": "+key);
                assertTrue(yaml.isString("gui.mob."+key+"-lore"),resource+": "+key+" lore");
            }
        }
    }
    @Test void dynamicDungeonControlAndHookKeysExistInBothLanguages() throws Exception {
        for(String resource:List.of("messages.yml","messages_en.yml")) {
            var yaml=catalog(resource);
            for(String key:List.of("control-start","control-test","control-stop")) {
                assertTrue(yaml.isString("gui.dungeon."+key));
                assertTrue(yaml.isString("gui.dungeon."+key+"-lore"));
            }
        }
        var yaml=catalog("messages.yml");
        for(var event:dev.dasan.customdungeons.model.HookEvent.values()) {
            String key="gui.dungeon.hook-"+event.name().toLowerCase(Locale.ROOT);
            assertTrue(yaml.isString(key)); assertTrue(yaml.isString(key+"-lore"));
        }
    }
    private static List<String> literalValues(ExpressionTree expression) {
        return switch (expression) {
            case LiteralTree literal when literal.getValue() instanceof String value -> List.of(value);
            case ParenthesizedTree parenthesized -> literalValues(parenthesized.getExpression());
            case ConditionalExpressionTree conditional -> {
                var values = new ArrayList<>(literalValues(conditional.getTrueExpression()));
                values.addAll(literalValues(conditional.getFalseExpression()));
                yield values;
            }
            case BinaryTree binary when binary.getKind() == Tree.Kind.PLUS -> {
                var values = new ArrayList<String>();
                for (String left : literalValues(binary.getLeftOperand()))
                    for (String right : literalValues(binary.getRightOperand())) values.add(left + right);
                yield values;
            }
            default -> List.of(); // Runtime ids and parameters are not literal message keys.
        };
    }
    private static YamlConfiguration catalog(String resource) throws Exception {
        try (var in = MessageKeysTest.class.getResourceAsStream("/" + resource)) {
            assertNotNull(in);
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}

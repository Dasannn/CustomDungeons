package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.gui.snapshot.SnapshotText;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the real menu traversal, every registered ability, variants and every page. */
class NumericMenuRangesTest extends GuiSnapshotExportTest {
    private int menusChecked,buttonsChecked;
    @TempDir Path temporary;
    @AfterEach void coverage() { System.out.println("Numeric ranges: "+buttonsChecked+" buttons across "+menusChecked+" menu pages"); }
    @BeforeEach void destination() { output=temporary; }
    @Override protected void verifyNumericButtons(String id, Menu menu, List<Map<String,Object>> slots) {
        if(id.endsWith("load-warning")) {
            int slot=(menu instanceof MobMenuBase)?6:40;
            assertEquals("YELLOW_DYE",slots.get(slot).get("material"),id);
            assertTrue(((List<?>)slots.get(slot).get("lore")).stream().anyMatch(line->line.toString().contains("recortado")&&line.toString().contains("archivo original")),id);
        }
        var expected=new LinkedHashMap<Integer,NumericRange>();
        if(menu instanceof AmbienceMenu) {
            expected.put(32,NumericRanges.ambience("door-density"));
            expected.put(39,NumericRanges.ambience("density"));
        }
        if(menu.getClass().getEnclosingClass()==AmbienceMenu.class) {
            expected.put(37,NumericRanges.ambience("effect-ticks"));
            expected.put(38,NumericRanges.ambience("title-seconds"));
            expected.put(39,NumericRanges.ambience("shake-ticks"));
            expected.put(40,NumericRanges.ambience("density"));
            expected.put(41,NumericRanges.ambience("door-density"));
            for(var slot:slots)if(slot.get("material").equals("POTION"))expected.put((Integer)slot.get("slot"),NumericRanges.POTION_LEVEL);
        }
        if(menu instanceof StatsMenu) {
            for(int i=0;i<dev.dasan.customdungeons.model.MobAttributes.KEYS.size();i++)
                expected.put(StatsMenu.POSITIONS[i],NumericRanges.attribute(dev.dasan.customdungeons.model.MobAttributes.KEYS.get(i)));
            assertFalse(slots.get(20).get("name").toString().contains("HP"),id);
            if(id.equals("stats-scale-sixteen")) assertTrue(((List<?>)slots.get(23).get("lore")).contains("Necesita salas muy altas; la IA puede fallar."));
        }
        if(menu instanceof DungeonSettingsMenu) {
            String[] keys={"min","max","lives","countdown","time","cooldown"};int[] positions={19,28,21,23,32,41};
            for(int i=0;i<keys.length;i++) if(Boolean.TRUE.equals(slots.get(positions[i]).get("action"))) expected.put(positions[i],NumericRanges.dungeon(keys[i]));
            assertTrue(((List<?>)slots.get(21).get("lore")).contains("Rango: 1–100 · límite del plugin"),id);
        }
        if(menu instanceof StartSettingsMenu) {
            expected.put(37,NumericRanges.dungeon("plate-countdown"));expected.put(41,NumericRanges.dungeon("intro-seconds"));
            if(!slots.get(34).get("material").equals("GRAY_DYE")) expected.put(34,NumericRanges.dungeon("exit-grace"));
        }
        if(menu instanceof ScalingMenu) {expected.put(21,NumericRanges.dungeon("extra-mobs"));expected.put(23,NumericRanges.dungeon("extra-health"));}
        if(menu instanceof RewardMenu) {expected.put(11,NumericRanges.MONEY);expected.put(13,NumericRanges.XP);}
        if(menu instanceof SpawnerPresetMenu) expected.put(29,NumericRanges.SPAWNER_RADIUS);
        if(menu instanceof SpawnerMenu && slots.get(22).get("material").equals("TARGET")) expected.put(22,NumericRanges.SPAWNER_RADIUS);
        if(menu instanceof WaveEntryMenu) {expected.put(13,NumericRanges.WAVE_COUNT);expected.put(15,NumericRanges.SECONDS);}
        if(menu instanceof WaveMenu) {
            expected.put(22,NumericRanges.SECONDS);
            if(slots.get(29).get("material").equals("CLOCK")) expected.put(29,NumericRanges.dungeon("interval"));
        }
        if(menu instanceof ComboMenu) {
            expected.put(21,NumericRanges.mob("trigger-value"));expected.put(23,NumericRanges.mob("range"));expected.put(24,NumericRanges.TICKS);
            for(int n=36;n<45;n++) if(slots.get(n).get("material").equals("CLOCK")) expected.put(n,NumericRanges.SECONDS);
        }
        if(menu instanceof PhaseMenu) {expected.put(19,NumericRanges.mob("threshold"));expected.put(28,NumericRanges.mob("heal"));expected.put(37,NumericRanges.mob("invulnerable-ticks"));}
        if(menu instanceof EquipmentMenu) for(int n=28;n<=34;n++)
            if(slots.get(n).get("material").equals("GOLD_NUGGET")) expected.put(n,NumericRanges.mob("drop-chance"));
        if(menu instanceof EnchantMenu) for(var slot:slots)
            if(slot.get("material").equals("ENCHANTED_BOOK")) expected.put((Integer)slot.get("slot"),NumericRanges.ENCHANTMENT_LEVEL);
        if(menu instanceof ParamEditorMenu) {
            var value=(AbilityInstance)field(menu,"value");boolean common=(boolean)field(menu,"common");
            var registry=MobMenuBase.registry();var ability=registry.get(value.abilityId());
            var indices=new LinkedHashMap<Integer,NumericRange>();
            if(common) {String[] keys={"trigger-value","range","cooldown","chance","telegraph"};for(int i=0;i<keys.length;i++) indices.put(i+2,NumericRanges.common(keys[i]));}
            ability.ifPresent(a->{for(int i=0;i<a.params().size();i++) if(NumericRanges.numeric(a.params().get(i))) indices.put((common?7:0)+i,NumericRanges.parameter(a.params().get(i)));});
            int capacity=(slots.size()/9-3)*7,start=(int)field(menu,"page")*capacity;
            indices.forEach((index,range)->{if(index>=start && index<start+capacity) expected.put(19+(index-start)/7*9+(index-start)%7,range);});
        }
        if(id.equals("potion-editor")) expected.put(22,NumericRanges.POTION_LEVEL);
        if(id.equals("phase-summon-editor")) {expected.put(22,NumericRanges.summonCount(MobMenuBase.config()));expected.put(24,NumericRanges.TICKS);}
        if(id.equals("numeric-input")) {
            var range=new NumericRange(0,100,2,NumericRange.Origin.PLUGIN);
            for(int n:new int[]{4,12,14}) expected.put(n,range);
        }
        menusChecked++;buttonsChecked+=expected.size();
        expected.forEach((n,range)->{
            var slot=slots.get(n);String line=SnapshotText.plain(NumericInputs.description(range));
            assertTrue(((List<?>)slot.get("lore")).contains(line),id+" slot "+n+" expected "+line+": "+slot);
            assertTrue(line.equals("Sin límite (hasta 10³⁰)") || line.matches("Rango: .+–.+ · límite (de Minecraft|del plugin)"),line);
            if(range.unbounded()) assertFalse(((List<?>)slot.get("lore")).stream()
                    .anyMatch(lore->lore.toString().startsWith("Decimales:")),id+" slot "+n);
        });
    }
    private Object field(Object object,String name) {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) try {
            var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
        } catch(NoSuchFieldException ignored) {} catch(IllegalAccessException failure) {throw new AssertionError(failure);}
        throw new AssertionError("Missing field "+name);
    }
}

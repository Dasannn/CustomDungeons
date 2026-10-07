package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.mob.MobKeys;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real YAML/store boundary; only Bukkit item metadata is supplied by a serializable test item. */
class EquipmentLoadCompatibilityTest {
    @TempDir Path directory;
    final DefinitionCodec codec=new DefinitionCodec();
    final List<String> console=new ArrayList<>();
    DefinitionStore store;
    @BeforeEach void setup() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        ConfigurationSerialization.registerClass(LoadItem.class,"EquipmentLoadItem");
        store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                Set.of("test"),console::add,Runnable::run);
        store.save(DefinitionCodecTest.mob()).join();store.save(DefinitionCodecTest.dungeon()).join();
    }
    @AfterEach void close() {
        store.close();ConfigurationSerialization.unregisterClass(LoadItem.class);
    }
    @ParameterizedTest
    @CsvSource({"HAND,tool","OFF_HAND,tool","HAND,key","OFF_HAND,key"})
    void reservedEquipmentLoadsWithoutOnlyTheAffectedSlotsAndKeepsDungeonActive(String slot,String marker) throws Exception {
        Path file=directory.resolve("mobs/zombie.yml");
        var yaml=new YamlConfiguration();yaml.load(file.toFile());
        var other=slot.equals("HAND")?"OFF_HAND":"HAND";
        var equipment=Map.of(slot,Map.of("item",new LoadItem(marker),"drop-chance",.5),
                other,Map.of("item",new LoadItem("ordinary"),"drop-chance",.25));
        yaml.set("equipment",equipment);
        NumericLoadCompatibilityTest.set(yaml,"phases[0].equipment",equipment);
        yaml.set("speed",4.7); // Equipment warnings must preserve the existing numeric adjustment too.
        yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);
        var raw=codec.decodeMob("zombie",yaml);
        assertThrows(CompletionException.class,()->store.save(raw).join(),"Saving remains strict");
        console.clear();store.reloadAsync(Runnable::run).join();

        var loaded=store.mobs().get("zombie");assertNotNull(loaded,console::toString);
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        assertEquals(Set.of(EquipmentSlot.valueOf(other)),loaded.equipment().keySet());
        assertEquals(Set.of(EquipmentSlot.valueOf(other)),loaded.phases().getFirst().equipment().keySet());
        for(var kept:List.of(loaded.equipment(),loaded.phases().getFirst().equipment())) {
            var entry=kept.get(EquipmentSlot.valueOf(other));
            assertEquals(.25f,entry.dropChance());assertEquals(Map.of("marker","ordinary"),entry.item().serialize());
        }
        assertEquals(raw.maxHealth(),loaded.maxHealth());assertEquals(raw.entityType(),loaded.entityType());
        assertEquals(raw.displayName(),loaded.displayName());assertEquals(raw.damage(),loaded.damage());
        assertEquals(raw.scale(),loaded.scale());assertEquals(raw.knockbackResistance(),loaded.knockbackResistance());
        assertEquals(raw.potions(),loaded.potions());assertEquals(raw.abilities(),loaded.abilities());
        assertEquals(raw.combos(),loaded.combos());assertEquals(raw.boss(),loaded.boss());
        assertEquals(raw.bossBarColor(),loaded.bossBarColor());assertEquals(raw.musicKey(),loaded.musicKey());
        assertEquals(raw.vanillaDrops(),loaded.vanillaDrops());
        var phase=loaded.phases().getFirst();var before=raw.phases().getFirst();
        assertEquals(before.healthThreshold(),phase.healthThreshold());assertEquals(before.replaceAbilities(),phase.replaceAbilities());
        assertEquals(before.abilities(),phase.abilities());assertEquals(before.combos(),phase.combos());
        assertEquals(before.potions(),phase.potions());assertEquals(before.healPercent(),phase.healPercent());
        assertEquals(before.summons(),phase.summons());assertEquals(before.title(),phase.title());
        assertEquals(before.subtitle(),phase.subtitle());assertEquals(before.soundKey(),phase.soundKey());
        assertEquals(before.musicKey(),phase.musicKey());assertEquals(before.invulnerableTicks(),phase.invulnerableTicks());
        var warnings=store.loadWarnings("mobs","zombie");assertEquals(3,warnings.size());
        for(String path:List.of("equipment."+slot,"phases[0].equipment."+slot)) {
            assertEquals(1,warnings.stream().filter(w->w.path().equals(path)
                    && w.messageKey().equals("validation.equipment-ignored")).count());
            assertEquals(1,console.stream().filter(w->w.contains(file+":"+path+" ")).count(),console::toString);
        }
        assertArrayEquals(original,Files.readAllBytes(file));
        store.save(loaded).join();assertTrue(store.loadWarnings("mobs","zombie").isEmpty());
    }
    @Test void wardenWithReservedItemsInBothHandsLoadsAvailableWithoutEitherSlot() throws Exception {
        var yaml=DefinitionCodecTest.yaml(codec.encode(DefinitionCodecTest.mob()));
        yaml.set("entity-type","WARDEN");
        yaml.set("equipment",Map.of("HAND",Map.of("item",new LoadItem("tool")),
                "OFF_HAND",Map.of("item",new LoadItem("tool"))));
        Path file=directory.resolve("mobs/warden.yml");yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);
        var dungeonFile=directory.resolve("dungeons/ejemplo.yml");
        var dungeon=new YamlConfiguration();dungeon.load(dungeonFile.toFile());
        NumericLoadCompatibilityTest.set(dungeon,"rooms[0].spawners[0].waves[0].entries[0].template-id","warden");
        NumericLoadCompatibilityTest.set(dungeon,"rooms[0].key-carrier-template-id","warden");
        dungeon.save(dungeonFile.toFile());console.clear();store.reload();
        var warden=store.mobs().get("warden");assertNotNull(warden,console::toString);
        assertEquals("WARDEN",warden.entityType());assertTrue(warden.equipment().isEmpty());
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        assertEquals(Set.of("equipment.HAND","equipment.OFF_HAND"),store.loadWarnings("mobs","warden")
                .stream().map(Validator.Warning::path).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,console.size(),console::toString);assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void unrelatedValidationErrorsStillExcludeTemplate() throws Exception {
        Path file=directory.resolve("mobs/zombie.yml");var yaml=new YamlConfiguration();yaml.load(file.toFile());
        yaml.set("equipment",Map.of("HAND",Map.of("item",new LoadItem("tool")),
                "BODY",Map.of("item",new LoadItem("ordinary"))));
        yaml.set("entity-type","WARDEN");yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);
        console.clear();store.reload();
        assertFalse(store.mobs().containsKey("zombie"));assertFalse(store.dungeons().get("ejemplo").enabled());
        assertTrue(console.stream().anyMatch(w->w.contains("equipment.BODY (validation.armor)")),console::toString);
        assertArrayEquals(original,Files.readAllBytes(file));
    }
    @SerializableAs("EquipmentLoadItem")
    public static final class LoadItem extends ItemStack {
        private final String marker;
        LoadItem(String marker) {super();this.marker=marker;}
        public static LoadItem deserialize(Map<String,Object> values) {return new LoadItem((String)values.get("marker"));}
        @Override public Map<String,Object> serialize() {return Map.of("marker",marker);}
        @Override public boolean hasItemMeta() {return true;}
        @Override public Map<org.bukkit.enchantments.Enchantment,Integer> getEnchantments() {return Map.of();}
        @Override public ItemMeta getItemMeta() {
            var meta=mock(ItemMeta.class);var pdc=mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            if(marker.equals("tool"))when(pdc.has(MobKeys.TOOL)).thenReturn(true);
            if(marker.equals("key"))when(pdc.has(MobKeys.KEY_ITEM)).thenReturn(true);
            return meta;
        }
        @Override public ItemStack clone() {return new LoadItem(marker);}
    }
}

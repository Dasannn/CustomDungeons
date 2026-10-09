package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.boss.BossRegistry;
import dev.dasan.customdungeons.gui.GuiLayout;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.WorldBossDef;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldBossChoiceMenuTest {
    private DungeonMenuFlowTest fixture;

    @BeforeEach void setup() {
        fixture = new DungeonMenuFlowTest();
        fixture.setup();
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(true);
        when(fixture.plugin.bossRegistry()).thenReturn(new BossRegistry());
        when(fixture.player.getWorld()).thenReturn(fixture.world);
    }

    @AfterEach void cleanup() throws Exception { fixture.cleanup(); }

    @Test void templatePickerUsesEachEntityEggAndSpawnerFallbackAndStillSelectsTheTemplate() {
        var warden = template("a_warden", "minecraft:warden");
        var zombie = template("b_zombie", "ZOMBIE");
        var giant = template("c_giant", "GIANT");
        when(fixture.store.mobs()).thenReturn(Map.of(warden.id(), warden, zombie.id(), zombie, giant.id(), giant));
        new BossListMenu(fixture.player, fixture.list).open();
        fixture.clickSlot(49);
        assertInstanceOf(BossChoiceMenu.class, fixture.top.getHolder());
        var expected = List.of(Material.WARDEN_SPAWN_EGG, Material.ZOMBIE_SPAWN_EGG, Material.SPAWNER);
        for (int i = 0; i < expected.size(); i++)
            assertEquals(expected.get(i), fixture.top.getItem(GuiLayout.pageSlot(i, expected.size(), 1)).getType());
        fixture.clickSlot(GuiLayout.pageSlot(0, expected.size(), 1));
        var editor = assertInstanceOf(MobMenu.class, fixture.top.getHolder());
        assertEquals(warden.id(), editor.data.id);
        assertEquals(warden.entityType(), editor.data.type);
    }

    @Test void worldPickerKeepsGrassEvenWhenWorldNameMatchesATemplateId() {
        var warden = template("world", "WARDEN");
        when(fixture.store.mobs()).thenReturn(Map.of(warden.id(), warden));
        fixture.bukkit.when(Bukkit::getWorlds).thenReturn(List.of(fixture.world));
        var draft = new MobMenu.MobDraft(warden.withWorldBoss(WorldBossDef.defaults("other")));
        var editor = new WorldBossMenu(fixture.player, draft, fixture.list);
        editor.open();
        fixture.clickSlot(37);
        assertInstanceOf(BossChoiceMenu.class, fixture.top.getHolder());
        assertEquals(Material.GRASS_BLOCK, fixture.top.getItem(GuiLayout.pageSlot(0, 1, 1)).getType());
        fixture.clickSlot(GuiLayout.pageSlot(0, 1, 1));
        assertSame(editor, fixture.top.getHolder());
        assertEquals("world", draft.worldBoss.world());
    }

    private MobTemplate template(String id, String type) {
        return new MobTemplate(id, type, id, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of(), List.of(),
                false, "PURPLE", null, List.of(), false);
    }
}

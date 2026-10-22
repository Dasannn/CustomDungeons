package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** T66: compose the existing wave and durable-loot fixtures across lifecycle boundaries. */
class LootZoneLifecycleTest {
    final OptionalZoneRuntimeTest zones=new OptionalZoneRuntimeTest();
    final LootChestServiceTest chests=new LootChestServiceTest();
    LootChestService loot=chests.loot;
    boolean inside=true;
    final SessionServices services=new SessionServices() {
        public boolean prepareStart(DungeonSession s) {return loot.prepare(s);}
        public Location spawnLocation(DungeonSession s,SpawnOrigin origin) {return zones.services.spawnLocation(s,origin);}
        public ActiveMob spawn(DungeonSession s,SpawnOrigin origin,String template,Location at) {return zones.services.spawn(s,origin,template,at);}
        public void roomEntered(DungeonSession s) {loot.trigger(s,s.def().rooms().get(s.roomIndex()).id(),LootChestDef.Trigger.ROOM_ENTER);}
        public void roomCleared(DungeonSession s) {loot.trigger(s,s.def().rooms().get(s.roomIndex()).id(),LootChestDef.Trigger.ROOM_CLEAR);s.openDoor();}
        public void finish(DungeonSession s) {loot.finish(s);}
        public boolean reset(DungeonSession s) {return loot.reset(s);}
        public boolean inside(DungeonSession s,Player p) {return inside;}
    };

    DungeonDef definition(boolean modern) throws Exception {
        var d=zones.definition(1,false).withFinish(FinishMode.NONE,10,FinishDestination.EXIT,List.of());
        if(modern) {
            var rooms=new ArrayList<>(d.rooms());
            rooms.set(0,rooms.getFirst().withLootChests(List.of(chests.chest(LootChestDef.Trigger.ROOM_ENTER))));
            return SpawnerPresets.withRooms(d,rooms);
        }
        // Go through actual YAML text with neither additive field, not just an old constructor.
        var codec=new DefinitionCodec();var yaml=new YamlConfiguration();
        codec.encode(d.withOptionalZones(List.of())).forEach(yaml::set);
        String text=yaml.saveToString();assertFalse(text.contains("optional-zones"));assertFalse(text.contains("loot-chests"));
        var loaded=new YamlConfiguration();loaded.loadFromString(text);
        var legacy=codec.decodeDungeon(d.id(),loaded);
        assertTrue(legacy.optionalZones().isEmpty());assertTrue(legacy.rooms().stream().allMatch(r->r.lootChests().isEmpty()));
        assertEquals(d.reward(),legacy.reward());return legacy;
    }
    DungeonSession start(DungeonDef d,Player p) {
        inside=true;var s=new DungeonSession(d,false,services);s.join(p);s.forceStart();return s;
    }
    void ticks(DungeonSession s,int count) {for(int i=0;i<count;i++)s.tick();}
    void activate(DungeonSession s,boolean modern) {
        ticks(s,10);assertEquals(modern?3:1,s.mobs().size());
        if(modern)assertNotNull(chests.blocks.contents[0]);
    }
    void assertFresh(DungeonDef d,boolean modern) {
        int draws=chests.draws;var next=start(d,zones.player(1));activate(next,modern);
        assertEquals(draws+(modern?1:0),chests.draws);
        assertEquals(modern?1:0,zones.zone(next,"a").size());
        assertEquals(modern?1:0,zones.zone(next,"b").size());
        next.finish(false,true);inside=false;ticks(next,15);assertEquals(SessionState.FREE,next.state().state());
    }

    @ParameterizedTest @CsvSource({"false,complete","false,fail","false,stop","false,shutdown",
            "true,complete","true,fail","true,stop","true,shutdown"})
    void oldAndNewDungeonsEndWithLiveMobsAndStartAgain(boolean modern,String end) throws Exception {
        var d=definition(modern);var p=zones.player(1);var s=start(d,p);activate(s,modern);
        var entities=s.mobs().stream().map(ActiveMob::entity).toList();
        int[] effect={0};s.scheduler().runLater(100,()->effect[0]++);
        if(end.equals("shutdown")) {s.beginShutdownReset();loot.shutdown();}
        else s.finish(end.equals("complete"),end.equals("stop"));
        assertTrue(s.mobs().isEmpty());for(var e:entities)assertNull(s.origin(e.getUniqueId()));
        if(modern && !end.equals("shutdown")) {
            assertEquals(end.equals("complete"),loot.allowed(LootChestServiceTest.POSITION,p,true));
        }
        inside=false;ticks(s,120);assertEquals(0,effect[0]);
        if(end.equals("shutdown")) {
            loot=new LootChestService(chests.store,chests.blocks,chests.store.loadLootBlocks().join(),()->{chests.draws++;return .25;},chests.failures::add);
            loot.tick();loot.tick();
        } else assertEquals(SessionState.FREE,s.state().state());
        assertEquals(LootChestServiceTest.ORIGINAL,chests.blocks.data);assertNull(chests.blocks.contents[0]);
        assertFresh(d,modern);assertTrue(chests.failures.isEmpty(),chests.failures::toString);
    }

    @ParameterizedTest @CsvSource({"false,before","false,after","false,empty","false,evacuation","false,restoring",
            "true,before","true,after","true,empty","true,evacuation","true,restoring"})
    void crashWithZonesAndLootPreservesBothBackupLayersUntilDurableRecovery(boolean placed,String stage) throws Exception {
        var d=definition(true);var chest=d.rooms().getFirst().lootChests().getFirst();
        if(placed) {var receipt=loot.place(d.id(),"first",chest);loot.tick();receipt.join();}
        var p=zones.player(1);var s=start(d,p);
        if(!stage.equals("before"))activate(s,true);
        if(stage.equals("empty"))chests.blocks.contents[0]=null; // already taken: recovery must not recreate it
        if(stage.equals("evacuation") || stage.equals("restoring"))s.finish(true);
        if(stage.equals("evacuation"))assertTrue(loot.allowed(LootChestServiceTest.POSITION,p,true));
        if(stage.equals("restoring")) {
            chests.store.next=new CompletableFuture<>();inside=false;ticks(s,15);
            assertEquals(SessionState.RESETTING,s.state().state());
        }
        var durable=chests.store.loadLootBlocks().join();assertTrue(durable.getFirst().sessionActive());
        assertEquals(placed?LootChestServiceTest.ORIGINAL:null,durable.getFirst().placementData());
        String sessionOriginal=durable.getFirst().sessionData();int draws=chests.draws;
        loot=new LootChestService(chests.store,chests.blocks,durable,()->{chests.draws++;return .25;},chests.failures::add);
        chests.blocks.ready=false;loot.tick();loot.tick();
        assertTrue(loot.blocked(d.id()));assertEquals(durable,chests.store.loadLootBlocks().join());
        chests.blocks.ready=true;var acknowledgement=new CompletableFuture<Void>();chests.store.next=acknowledgement;
        loot.chunksAvailable("world");loot.tick();
        assertTrue(loot.blocked(d.id()));assertTrue(chests.store.records.get(chest.location()).sessionActive());
        assertNull(chests.blocks.contents[0]);
        // Crash again after the block was restored but before its receipt reached storage.
        loot=new LootChestService(chests.store,chests.blocks,chests.store.loadLootBlocks().join(),()->{chests.draws++;return .25;},chests.failures::add);
        loot.tick();loot.tick();assertFalse(loot.blocked(d.id()));
        acknowledgement.complete(null);loot.tick();
        assertEquals(draws,chests.draws);assertEquals(placed?"minecraft:chest[type=single]":sessionOriginal,chests.blocks.data);
        assertFalse(chests.store.records.get(chest.location()).sessionActive());
        assertEquals(placed?LootChestServiceTest.ORIGINAL:null,chests.store.records.get(chest.location()).placementData());
        loot.reconcileAll(List.of(d));loot.tick();loot.tick();
        assertFresh(d,true);
        var removed=loot.reconcile(d.id(),List.of());for(int i=0;i<12;i++)loot.tick();removed.join();
        assertEquals(LootChestServiceTest.ORIGINAL,chests.blocks.data);assertNull(chests.blocks.contents[0]);
        assertFalse(loot.blocked(d.id()));assertTrue(chests.failures.isEmpty(),chests.failures::toString);
    }

    @ParameterizedTest @EnumSource(value=FinishMode.class,names={"DELAYED","NONE"})
    void testLootRemainsInspectableAndUntransferableAcrossCombatEvacuationAndNextRun(FinishMode mode) throws Exception {
        var d=definition(true).withFinish(mode,10,FinishDestination.EXIT,List.of());
        var p=zones.player(1);var listener=new LootChestListener(loot,point->point.equals(LootChestServiceTest.POSITION));
        for(int run=0;run<2;run++) {
            inside=true;var s=new DungeonSession(d,true,services);s.join(p);s.forceStart();activate(s,true);
            var original=chests.blocks.contents[0];
            for(boolean completed:List.of(false,true)) {
                if(completed)s.finish(true);
                var open=mock(InventoryOpenEvent.class);when(open.getInventory()).thenReturn(chests.blocks.inventory);when(open.getPlayer()).thenReturn(p);
                listener.open(open);verify(open,never()).setCancelled(true);
                assertTrue(loot.allowed(LootChestServiceTest.POSITION,p,false));assertFalse(loot.allowed(LootChestServiceTest.POSITION,p,true));
                var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(chests.blocks.inventory);
                for(var click:ClickType.values()) {
                    var event=mock(InventoryClickEvent.class);when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(p);when(event.getClick()).thenReturn(click);
                    listener.click(event);verify(event).setCancelled(true);
                }
                var drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);when(drag.getWhoClicked()).thenReturn(p);when(drag.getRawSlots()).thenReturn(Set.of(0,30));
                listener.drag(drag);verify(drag).setCancelled(true);
                for(boolean outgoing:List.of(false,true)) {
                    var transfer=mock(InventoryMoveItemEvent.class);var other=mock(Inventory.class);
                    when(transfer.getSource()).thenReturn(outgoing?chests.blocks.inventory:other);when(transfer.getDestination()).thenReturn(outgoing?other:chests.blocks.inventory);
                    listener.transfer(transfer);verify(transfer).setCancelled(true);
                }
                var pickup=mock(InventoryPickupItemEvent.class);when(pickup.getInventory()).thenReturn(chests.blocks.inventory);listener.pickup(pickup);verify(pickup).setCancelled(true);
                var block=mock(Block.class);when(block.getLocation()).thenReturn(new Location(zones.world,1,64,1));
                var breaking=new BlockBreakEvent(block,p);listener.breakBlock(breaking);assertTrue(breaking.isCancelled());
                assertSame(original,chests.blocks.contents[0]);assertEquals(run+1,chests.draws);
            }
            inside=false;ticks(s,15);assertEquals(SessionState.FREE,s.state().state());assertNull(chests.blocks.contents[0]);
            assertFalse(loot.allowed(LootChestServiceTest.POSITION,p,false));
        }
        verify(p,never()).getInventory();assertEquals(LootChestServiceTest.ORIGINAL,chests.blocks.data);
    }
}

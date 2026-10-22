package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.storage.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real manager/runtime closure; only Paper world objects and dispatch to the main thread are simulated. */
class RealRuntimeLifecycleTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @TempDir Path directory;

    @ParameterizedTest @CsvSource({"false,complete","false,fail","false,stop","false,shutdown",
            "true,complete","true,fail","true,stop","true,shutdown"})
    void runtimePhysicallyCleansAndRestoresBeforeAnotherRun(boolean modern,String end) throws Exception {
        try(var f=new Fixture(directory,modern)) {
            var s=f.start();var mobs=f.populate(s);f.placeTemporaryBlocks(s);
            assertEquals(modern?1:0,f.draws);assertEquals(2,f.storage.loadTempBlocks().join().size());
            var projectile=f.remnant(s.id());f.entities.add(projectile);
            int[] effect={0};s.scheduler().runLater(100,()->effect[0]++);
            if(end.equals("shutdown"))f.manager.shutdown();
            else if(end.equals("stop"))f.manager.stop(s.def().id());
            else s.finish(end.equals("complete"));
            for(var mob:mobs)verify(mob.entity(),atLeastOnce()).remove();verify(projectile).remove();
            assertTrue(s.mobs().isEmpty());f.assertTemporaryBlocksRestored();
            if(!end.equals("shutdown")) {
                if(modern)assertEquals(end.equals("complete"),f.manager.loot().allowed(LootChestServiceTest.POSITION,f.player,true));
                f.at.set(new Location(f.setup.world,99,64,1));
                f.until(()->s.state().state()==SessionState.FREE,()->s.tick());
                assertNull(f.manager.runtime(s));assertSame(s,f.manager.session(s.def().id()).orElseThrow());
            }
            f.barrier();assertEquals(2,f.storage.loadTempBlocks().join().size());
            assertEquals(LootChestServiceTest.ORIGINAL,f.chests.blocks.data);assertNull(f.chests.blocks.contents[0]);
            // Reopen the actual database: recovery must consume a nonempty journal without re-mutating restored blocks.
            f.reopen();var recovery=new RecoveryService(f.setup.plugin,f.storage,f.manager);
            clearInvocations(f.trap,f.gate);
            recovery.recoverOnEnable();f.pump();f.recoverLoot();recovery.recoverOnEnable();
            assertTrue(f.storage.loadTempBlocks().join().isEmpty());f.assertTemporaryBlocksRestored();
            verify(f.trap,never()).setBlockData(any(),anyBoolean());verify(f.gate,never()).setBlockData(any(),anyBoolean());
            assertFalse(f.manager.loot().blocked(s.def().id()));
            var next=f.start();assertNotEquals(s.id(),next.id());f.populate(next);f.placeTemporaryBlocks(next);
            assertEquals(modern?2:0,f.draws);assertEquals(0,effect[0]);
            f.manager.shutdown();f.assertTemporaryBlocksRestored();assertNull(f.chests.blocks.contents[0]);
            verify(f.setup.world,never()).dropItem(any(),any());verify(f.setup.world,never()).dropItemNaturally(any(),any());
            assertTrue(f.failures.isEmpty(),f.failures::toString);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void crashRecoveryRestoresNonemptySqliteJournalAndLateEntitiesWithoutPenalty(boolean modern) throws Exception {
        try(var f=new Fixture(directory,modern)) {
            var s=f.start();var mobs=f.populate(s);f.placeTemporaryBlocks(s);
            assertEquals(2,f.storage.loadTempBlocks().join().size());assertFalse(f.storage.loadActive().join().isEmpty());
            var late=f.remnant(s.id());f.entities.add(mobs.getFirst().entity());
            int draws=f.draws;f.reopen();
            var recovery=new RecoveryService(f.setup.plugin,f.storage,f.manager);recovery.recoverOnEnable();f.pump();f.recoverLoot();
            f.assertTemporaryBlocksRestored();assertTrue(f.storage.loadTempBlocks().join().isEmpty());
            assertTrue(f.storage.loadActive().join().isEmpty());assertTrue(f.storage.pendingExit(f.player.getUniqueId()).join().isPresent());
            verify(mobs.getFirst().entity()).remove();
            recovery.entitiesLoaded(new EntitiesLoadEvent(mock(Chunk.class),List.of(late)));verify(late).remove();
            assertEquals(draws,f.draws);assertEquals(LootChestServiceTest.ORIGINAL,f.chests.blocks.data);assertNull(f.chests.blocks.contents[0]);
            // Return the player through the real connection recovery, without a voluntary-disconnect penalty.
            f.manager.connected(f.player);f.until(()->f.at.get().getX()==99,()->{});
            verify(f.player,never()).setHealth(anyDouble());verify(f.player.getInventory(),never()).clear();
            assertTrue(f.storage.disconnect(f.player.getUniqueId()).join().isEmpty());
            recovery.recoverOnEnable();f.pump();f.recoverLoot();assertEquals(draws,f.draws);
            var next=f.start();f.populate(next);f.placeTemporaryBlocks(next);assertEquals(modern?draws+1:0,f.draws);
            f.manager.shutdown();f.assertTemporaryBlocksRestored();assertTrue(f.failures.isEmpty(),f.failures::toString);
        }
    }

    static final class Fixture implements AutoCloseable {
        final SessionRuntimeRegressionTest setup=new SessionRuntimeRegressionTest();
        final LootChestServiceTest chests=new LootChestServiceTest();
        final OptionalZoneRuntimeTest zones=new OptionalZoneRuntimeTest();
        final Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        final List<Throwable> failures=new ArrayList<>();
        final List<Entity> entities=new ArrayList<>();
        final AtomicReference<Location> at=new AtomicReference<>();
        final Player player=chests.player();
        final Block trap=mock(Block.class),gate=mock(Block.class);
        final BlockData air=DoorServiceTest.data("minecraft:air"),web=DoorServiceTest.data("minecraft:cobweb"),bars=DoorServiceTest.data("minecraft:iron_bars[north=true,waterlogged=true]");
        final AtomicReference<BlockData> trapData=new AtomicReference<>(air),gateData=new AtomicReference<>(bars);
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final DungeonDef def;
        final Path directory;
        SqlStorage storage;
        SessionManager manager;
        int draws;
        Fixture(Path directory,boolean modern) throws Exception {
            this.directory=directory;setup.configure();when(setup.plugin.isEnabled()).thenReturn(true);
            when(setup.plugin.getDataFolder()).thenReturn(directory.resolve("plugin").toFile());
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(setup.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of(setup.world));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(air);
            bukkit.when(()->Bukkit.createBlockData(bars.getAsString())).thenReturn(bars);
            when(setup.world.getEntities()).thenReturn(entities);
            when(setup.world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(c->{var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(setup.world);when(chunk.getX()).thenReturn(c.getArgument(0));when(chunk.getZ()).thenReturn(c.getArgument(1));return CompletableFuture.completedFuture(chunk);});
            at.set(new Location(setup.world,1,64,1));when(player.getLocation()).thenAnswer(c->at.get());when(player.getWorld()).thenReturn(setup.world);
            when(player.hasPermission(anyString())).thenReturn(true);when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
            when(player.getInventory().getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
            var pdc=mock(PersistentDataContainer.class);var tags=new HashMap<NamespacedKey,Object>();when(player.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(any(),any())).thenAnswer(c->tags.get(c.getArgument(0)));doAnswer(c->{tags.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(),any(),any());
            doAnswer(c->{at.set(((Location)c.getArgument(0)).clone());return true;}).when(player).teleport(any(Location.class));
            var base=new LootZoneLifecycleTest().definition(false);var codec=new DefinitionCodec();var yaml=new org.bukkit.configuration.file.YamlConfiguration();codec.encode(base).forEach(yaml::set);
            yaml.set("exit",Map.of("world","world","x",99,"y",64,"z",1));base=codec.decodeDungeon(base.id(),yaml);
            var rooms=base.rooms().stream().map(r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.AUTOMATIC,null,r.spawners())).toList();
            base=SpawnerPresets.withRooms(base,rooms);
            if(modern) {var changed=new ArrayList<>(rooms);changed.set(0,changed.getFirst().withLootChests(List.of(chests.chest(LootChestDef.Trigger.ROOM_ENTER))));base=SpawnerPresets.withRooms(base,changed).withOptionalZones(zones.definition(1,false).optionalZones());}
            def=base;when(setup.definitions.dungeons()).thenReturn(Map.of(def.id(),def));
            block(trap,trapData,3);block(gate,gateData,4);open();
        }
        void block(Block block,AtomicReference<BlockData> data,int x) {
            when(block.getWorld()).thenReturn(setup.world);when(block.getX()).thenReturn(x);when(block.getY()).thenReturn(64);when(block.getZ()).thenReturn(1);
            when(block.getBlockData()).thenAnswer(c->data.get());when(block.isEmpty()).thenAnswer(c->data.get()==air);
            doAnswer(c->{data.set(c.getArgument(0));return null;}).when(block).setBlockData(any(),eq(false));when(setup.world.getBlockAt(x,64,1)).thenReturn(block);
        }
        void open() throws Exception {
            storage=SqlStorage.create(new PluginConfig.DatabaseSettings("sqlite","",0,"","","",2),directory);
            manager=spy(new SessionManager(setup.plugin,setup.definitions,setup.config,storage));
            doAnswer(c->{main.add(c.getArgument(0));return null;}).when(manager).main(any());
            // Rebind the callback owner to the dispatching spy, as in DisconnectRecoveryTest.Fixture.
            SessionRuntimeRegressionTest.field(manager,"disconnects",new DisconnectService(storage,manager));
            var loot=new LootChestService(storage,chests.blocks,storage.loadLootBlocks().join(),()->{draws++;return .25;},failures::add);
            SessionRuntimeRegressionTest.field(manager,"loot",loot);
            manager.addListener(new RunRecorder(storage,manager,setup.plugin.getLogger()));
        }
        void barrier() {storage.loadLootBlocks().join();storage.loadTempBlocks().join();}
        void pump() {barrier();Runnable task;while((task=main.poll())!=null)task.run();}
        void until(java.util.function.BooleanSupplier done,Runnable step) {for(int i=0;i<100 && !done.getAsBoolean();i++){pump();step.run();}assertTrue(done.getAsBoolean(),"Lifecycle did not settle");}
        DungeonSession start() {
            at.set(new Location(setup.world,1,64,1));assertEquals(JoinResult.OK,manager.join(player,def.id()));
            var session=manager.session(def.id()).orElseThrow();until(()->session.state().state()==SessionState.RUNNING,session::forceStart);
            session.enterRoom(0);return session;
        }
        List<ActiveMob> populate(DungeonSession s) {
            var mobs=new ArrayList<ActiveMob>();var origins=new ArrayList<>(List.of(s.roomOrigin("same")));
            for(var z:def.optionalZones())origins.add(new SpawnOrigin(SpawnOrigin.Kind.ZONE,z.id(),"same"));
            for(var origin:origins) {
                var mob=zones.services.spawn(s,origin,"zombie",new Location(setup.world,1,64,1));s.track(mob,origin);mobs.add(mob);
                when(mob.entity().getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(s.id().toString());
            }
            s.maxAlive(mobs.size());for(int i=0;i<10;i++)s.tick();assertEquals(SessionState.RUNNING,s.state().state());return mobs;
        }
        void placeTemporaryBlocks(DungeonSession s) {
            var runtime=manager.runtime(s);
            var owner=s.mobs().stream().filter(m->def.optionalZones().isEmpty() || s.origin(m.entity().getUniqueId()).kind()==SpawnOrigin.Kind.ZONE).findFirst().orElseThrow();
            assertTrue(owner.session().tempBlocks().place(trap,web,1000));
            var opened=runtime.temp.openDoor(List.of(gate),air,()->true);barrier();
            until(opened::isDone,()->runtime.temp.tick(1));assertTrue(opened.join());
            assertSame(web,trapData.get());assertSame(air,gateData.get());
        }
        void assertTemporaryBlocksRestored() {assertSame(air,trapData.get());assertSame(bars,gateData.get());}
        Entity remnant(UUID id) {var entity=mock(Entity.class);var data=mock(PersistentDataContainer.class);when(entity.getPersistentDataContainer()).thenReturn(data);when(data.get(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(id.toString());return entity;}
        void recoverLoot() {until(()->!manager.loot().blocked(def.id()),()->manager.loot().tick());}
        void reopen() throws Exception {barrier();storage.close();main.clear();open();}
        public void close() {storage.close();bukkit.close();}
    }
}

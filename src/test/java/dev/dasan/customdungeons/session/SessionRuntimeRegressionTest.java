package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.storage.Storage;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRuntimeRegressionTest {
    final World world=mock(World.class);
    final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
    final PluginConfig config=mock(PluginConfig.class);
    final DefinitionStore definitions=mock(DefinitionStore.class);
    final Storage storage=mock(Storage.class);

    DungeonDef definition() {
        var point=new Point("world",1,64,1,0,0);
        var first=new RoomDef("first",Region.of("world",new BlockPos(0,60,0),new BlockPos(15,70,15)),point,null,UnlockMode.AUTOMATIC,null,List.of());
        var second=new RoomDef("second",Region.of("world",new BlockPos(16,60,0),new BlockPos(31,70,15)),new Point("world",17,64,1,0,0),null,UnlockMode.AUTOMATIC,null,List.of());
        return new DungeonDef("test","",true,point,point,1,2,0,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(first,second));
    }
    void configure() {
        when(world.getName()).thenReturn("world"); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(config.dungeonWorld()).thenReturn("world");
        when(config.limits()).thenReturn(new PluginConfig.PerformanceLimits(100,1,32));
        when(definitions.mobs()).thenReturn(Map.of());
        when(plugin.abilityRegistry()).thenReturn(mock(AbilityRegistry.class));
        var messages=mock(Messages.class);
        when(messages.get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class))).thenReturn(net.kyori.adventure.text.Component.empty());
        when(plugin.messages()).thenReturn(messages);
    }
    DungeonSessionRuntime runtime(SessionManager manager,DungeonSession session) {
        var runtime=new DungeonSessionRuntime(plugin,manager,definitions,config,storage);
        runtime.attach(session); return runtime;
    }
    static void field(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }

    @Test void mergeWithStolenDropIsCancelledInBothDirections() throws Exception {
        configure();
        var manager=mock(SessionManager.class); var session=new DungeonSession(definition(),false,new SessionServices() {});
        var runtime=runtime(manager,session); runtime.keys=mock(KeyService.class);
        when(manager.activeSessions()).thenReturn(List.of(session)); when(manager.runtime(session)).thenReturn(runtime);
        var stolen=mock(Item.class); when(stolen.getUniqueId()).thenReturn(UUID.randomUUID());
        var normal=mock(Item.class); when(normal.getUniqueId()).thenReturn(UUID.randomUUID());
        var stack=mock(ItemStack.class); when(stack.clone()).thenReturn(stack); when(stack.getAmount()).thenReturn(3);
        var entity=mock(Mob.class); when(entity.getUniqueId()).thenReturn(UUID.randomUUID()); when(entity.getWorld()).thenReturn(world);
        when(world.dropItem(any(),same(stack))).thenReturn(stolen);
        var template=new MobTemplate("zombie","ZOMBIE","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var mob=new ActiveMob(entity,template,session);
        UUID owner=UUID.randomUUID();
        runtime.stolen(owner,stack,mob); runtime.removed(session,mob,null);
        var listener=new SessionListener(manager);
        var handler=SessionListener.class.getMethod("merge",ItemMergeEvent.class);
        for (var pair : List.of(new Item[]{stolen,normal},new Item[]{normal,stolen})) {
            var event=new ItemMergeEvent(pair[0],pair[1]); handler.invoke(listener,event);
            assertTrue(event.isCancelled(),"Neither the stolen source nor target may merge with a normal stack");
        }
        var ordinary=new ItemMergeEvent(normal,normal); handler.invoke(listener,ordinary); assertFalse(ordinary.isCancelled());
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getEntity(stolen.getUniqueId())).thenReturn(stolen);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of()); bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            runtime.finish(session);
            verify(normal,never()).remove(); verify(stolen).remove();
            verify(storage).addClaims(owner,List.of(stack)); assertEquals(3,stack.getAmount());
        }
    }

    @Test void keyMovedIntoLockedRoomIsRelocatedOnPeriodicTick() throws Exception {
        configure();
        var session=mock(DungeonSession.class); when(session.id()).thenReturn(UUID.randomUUID()); when(session.def()).thenReturn(definition()); when(session.roomIndex()).thenReturn(0);
        var scheduler=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);
        when(session.scheduler()).thenReturn(scheduler); when(scheduler.currentTick()).thenReturn(20L);
        var old=mock(Item.class); when(old.isValid()).thenReturn(true); when(old.getWorld()).thenReturn(world);
        when(old.getLocation()).thenReturn(new Location(world,17,64,1));
        var replacement=mock(Item.class); when(replacement.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        // The fallback is supplied by DoorService; only the inaccessible position triggers replacement.
        var keys=new KeyService(session,mock(DoorService.class)); field(keys,"room",0); field(keys,"dropped",old);
        var fallback=new Location(world,1,64,1);
        try (var bukkit=mockStatic(Bukkit.class); var doors=mockStatic(DoorService.class); var messages=mockStatic(DungeonSessionRuntime.class);
             var stacks=mockConstruction(ItemStack.class,(stack,context)-> {
                 var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
                 when(meta.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
                 when(stack.getItemMeta()).thenReturn(meta);
             })) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            doors.when(()->DoorService.keyRespawn(any())).thenReturn(fallback);
            messages.when(DungeonSessionRuntime::messages).thenReturn(mock(Messages.class));
            // Keep the actual containment helper while mocking the message lookup.
            messages.when(()->DungeonSessionRuntime.contains(any(),any())).thenCallRealMethod();
            when(world.dropItem(same(fallback),any())).thenReturn(replacement);
            when(scheduler.currentTick()).thenReturn(19L); keys.tick(); verify(old,never()).remove();
            when(scheduler.currentTick()).thenReturn(20L); keys.tick();
            verify(old).remove(); verify(world).dropItem(same(fallback),any());
        }
    }

    @Test void repeatedCreateKeepsExistingValidKeyForTheSameRoom() throws Exception {
        configure();
        var session=mock(DungeonSession.class);
        when(session.id()).thenReturn(UUID.randomUUID()); when(session.def()).thenReturn(definition());
        when(session.roomIndex()).thenReturn(0);
        var existing=mock(Item.class); when(existing.isValid()).thenReturn(true);
        when(existing.getLocation()).thenReturn(new Location(world,1,64,1));
        var keys=new KeyService(session,mock(DoorService.class));
        field(keys,"room",0); field(keys,"dropped",existing);
        var replacement=mock(Item.class);
        when(replacement.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        try (var doors=mockStatic(DoorService.class); var messages=mockStatic(DungeonSessionRuntime.class);
             var stacks=mockConstruction(ItemStack.class,(stack,context)-> {
                 var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
                 when(meta.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
                 when(stack.getItemMeta()).thenReturn(meta);
             })) {
            doors.when(()->DoorService.keyRespawn(any())).thenReturn(new Location(world,1,64,1));
            messages.when(DungeonSessionRuntime::messages).thenReturn(mock(Messages.class));
            messages.when(()->DungeonSessionRuntime.contains(any(),any())).thenCallRealMethod();
            when(world.dropItem(any(),any())).thenReturn(replacement);
            keys.create(); keys.create();
            verify(world,never()).dropItem(any(),any()); verify(existing,never()).remove();
        }
    }

    @Test void missingTrackedEntityAdoptsExistingRoomKeyInsteadOfDuplicatingOnTicks() throws Exception {
        configure();
        var session=mock(DungeonSession.class); when(session.def()).thenReturn(definition());
        when(session.id()).thenReturn(UUID.randomUUID()); when(session.roomIndex()).thenReturn(0);
        var scheduler=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);
        when(session.scheduler()).thenReturn(scheduler); when(scheduler.currentTick()).thenReturn(20L);
        var existing=mock(Item.class); when(existing.isValid()).thenReturn(true);
        when(existing.getLocation()).thenReturn(new Location(world,1,64,1)); when(existing.getWorld()).thenReturn(world);
        when(existing.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        var stack=mock(ItemStack.class); when(existing.getItemStack()).thenReturn(stack);
        var keys=spy(new KeyService(session,mock(DoorService.class))); field(keys,"room",0);
        doReturn(true).when(keys).matches(stack);
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(existing));
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            keys.tick(); keys.tick(); keys.create();
            verify(world,never()).dropItem(any(),any()); verify(existing,never()).remove();
            var reference=KeyService.class.getDeclaredField("dropped"); reference.setAccessible(true);
            assertSame(existing,reference.get(keys));
        }
    }

    @Test void registeredKeyHandlerProcessesCancelledInteractionAndKeepsVanillaDenied() throws Exception {
        configure();
        var player=mock(Player.class); var session=mock(DungeonSession.class); var manager=mock(SessionManager.class);
        var runtime=runtime(manager,session); runtime.keys=mock(KeyService.class);
        when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session)); when(manager.runtime(session)).thenReturn(runtime);
        var block=mock(org.bukkit.block.Block.class); var key=mock(ItemStack.class);
        when(runtime.keys.use(player,block,key)).thenReturn(true);
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,key,block,org.bukkit.block.BlockFace.NORTH);
        event.setCancelled(true);
        var handler=SessionListener.class.getMethod("interact",PlayerInteractEvent.class);
        var registration=handler.getAnnotation(EventHandler.class);
        assertEquals(EventPriority.HIGHEST,registration.priority());
        // Exercise the same cancelled-event filter used by Bukkit's registered listener.
        if (!registration.ignoreCancelled() || !event.isCancelled()) handler.invoke(new SessionListener(manager),event);
        verify(runtime.keys).use(player,block,key);
        assertEquals(org.bukkit.event.Event.Result.DENY,event.useInteractedBlock());
        assertEquals(org.bukkit.event.Event.Result.DENY,event.useItemInHand());
    }

    @Test void finishingOneSessionDoesNotRemoveAnotherSessionsChunkTicket() {
        configure(); var chunk=mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world); when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk);
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of()); bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var manager=new SessionManager(plugin,definitions,config,storage);
            var session=new DungeonSession(definition(),false,new SessionServices() {});
            var first=runtime(manager,session); var second=runtime(manager,session);
            // Start through the public lifecycle, including any preparation added by the fix.
            var a=new DungeonSession(definition(),false,first); first.attach(a);
            var b=new DungeonSession(definition(),false,second); second.attach(b);
            var player=mock(Player.class); when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class)); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            a.join(player); b.join(player); a.forceStart(); b.forceStart(); a.tick(); b.tick();
            verify(chunk).addPluginChunkTicket(plugin);
            first.finish(a); verify(chunk,never()).removePluginChunkTicket(plugin);
            second.finish(b); verify(chunk).removePluginChunkTicket(plugin);
        }
    }

    @Test void startWaitsForAsyncPreloadWithoutSynchronousChunkLoads() {
        configure(); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        var pending=new CompletableFuture<Chunk>(); var chunk=mock(Chunk.class); when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk); when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(pending);
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var manager=new SessionManager(plugin,definitions,config,storage);
            var runtime=runtime(manager,mock(DungeonSession.class)); var session=new DungeonSession(definition(),false,runtime); runtime.attach(session);
            var player=mock(Player.class); when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class)); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); session.join(player); session.forceStart();
            assertEquals(SessionState.LOBBY,session.state().state(),"START must wait for the async loads");
            verify(world,never()).getChunkAt(anyInt(),anyInt()); verify(world,times(2)).getChunkAtAsync(anyInt(),anyInt());
            session.tick(); assertEquals(SessionState.LOBBY,session.state().state());
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true); pending.complete(chunk); session.tick();
            assertEquals(SessionState.RUNNING,session.state().state()); verify(world,never()).getChunkAt(anyInt(),anyInt());
        }
    }
    @Test void keyMayRemainInCurrentRoomOrAnAlreadyOpenedRoom() throws Exception {
        configure();
        var session=mock(DungeonSession.class); when(session.def()).thenReturn(definition()); when(session.roomIndex()).thenReturn(1);
        var scheduler=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);
        when(session.scheduler()).thenReturn(scheduler); when(scheduler.currentTick()).thenReturn(20L);
        var item=mock(Item.class); when(item.isValid()).thenReturn(true); when(item.getWorld()).thenReturn(world);
        var keys=new KeyService(session,mock(DoorService.class)); field(keys,"room",1); field(keys,"dropped",item);
        for (double x : new double[]{1,17}) {
            when(item.getLocation()).thenReturn(new Location(world,x,64,1)); keys.tick();
        }
        verify(item,never()).remove();
    }

    @Test void preloadCompletingAfterResetCannotAcquireTicketsOrRestartSession() {
        configure(); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        var pending=new CompletableFuture<Chunk>(); var chunk=mock(Chunk.class); when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(pending);
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of()); bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var manager=new SessionManager(plugin,definitions,config,storage);
            var runtime=runtime(manager,mock(DungeonSession.class));
            var session=new DungeonSession(definition(),false,runtime); runtime.attach(session);
            var player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
            session.join(player); session.forceStart(); session.finish(false);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true); pending.complete(chunk); session.tick();
            assertEquals(SessionState.FREE,session.state().state()); verify(chunk,never()).addPluginChunkTicket(plugin);
        }
    }

}

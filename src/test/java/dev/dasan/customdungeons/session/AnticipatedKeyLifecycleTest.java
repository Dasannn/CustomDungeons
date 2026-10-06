package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnticipatedKeyLifecycleTest {
    static ItemStack key(String token) {
        var stack=mock(ItemStack.class); var data=mock(PersistentDataContainer.class);
        when(stack.getPersistentDataContainer()).thenReturn(data);
        when(data.get(MobKeys.KEY_ITEM,PersistentDataType.STRING)).thenReturn(token);
        when(data.has(MobKeys.KEY_ITEM,PersistentDataType.STRING)).thenReturn(token!=null);
        return stack;
    }
    static class Fixture implements AutoCloseable {
        final UUID id=UUID.randomUUID();
        final DungeonSession session=mock(DungeonSession.class);
        final DoorService doors=mock(DoorService.class);
        final KeyService keys=new KeyService(session,doors);
        final SessionRuntimeRegressionTest base=new SessionRuntimeRegressionTest();
        final Player first=mock(Player.class), second=mock(Player.class);
        final List<Player> participants=new ArrayList<>(List.of(first,second));
        final List<Item> ground=new ArrayList<>();
        final Map<Player,Map<Integer,ItemStack>> slots=new HashMap<>();
        final Map<Player,AtomicReference<ItemStack>> cursors=new HashMap<>();
        final org.mockito.MockedStatic<Bukkit> bukkit;
        final org.mockito.MockedStatic<DoorService> staticDoors;
        final org.mockito.MockedStatic<DungeonSessionRuntime> runtime;
        final org.mockito.MockedConstruction<ItemStack> stacks;
        final Location at;
        Fixture(boolean keepInventory) throws Exception {
            base.configure(); at=new Location(base.world,6,65,11);
            var def=new ExternalKeyTest().puzzle();
            when(session.def()).thenReturn(new DungeonDef(def.id(),def.displayName(),true,def.lobby(),def.exit(),
                    1,2,0,3,keepInventory,0,0,false,def.scaling(),def.hooks(),def.reward(),def.rooms()));
            when(session.id()).thenReturn(id); when(session.currentRoomRegion()).thenReturn(def.rooms().getFirst().region());
            var state=new SessionStateMachine(); state.openLobby(); state.start(); when(session.state()).thenReturn(state);
            when(session.players()).thenAnswer(c->List.copyOf(participants));
            when(session.survivors()).thenAnswer(c->participants.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()));
            when(session.scheduler()).thenReturn(mock(dev.dasan.customdungeons.runtime.TickScheduler.class));
            for(var player:participants) {
                when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
                when(player.getLocation()).thenReturn(at); when(session.livesLeft(player.getUniqueId())).thenReturn(3);
                var inventory=mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
                var contents=new HashMap<Integer,ItemStack>(); slots.put(player,contents);
                when(inventory.getSize()).thenReturn(41); when(inventory.getItem(anyInt())).thenAnswer(c->contents.get(c.getArgument(0)));
                when(inventory.getContents()).thenAnswer(c->contents.values().toArray(ItemStack[]::new));
                doAnswer(c->{contents.put(c.getArgument(0),c.getArgument(1));return null;}).when(inventory).setItem(anyInt(),any());
                var cursor=new AtomicReference<ItemStack>(); cursors.put(player,cursor);
                when(player.getItemOnCursor()).thenAnswer(c->cursor.get());
                doAnswer(c->{cursor.set(c.getArgument(0));return null;}).when(player).setItemOnCursor(any());
            }
            SessionRuntimeRegressionTest.field(keys,"room",0);
            SessionRuntimeRegressionTest.field(keys,"holder",first.getUniqueId());
            when(base.world.getEntitiesByClass(Item.class)).thenAnswer(c->List.copyOf(ground));
            when(base.world.dropItem(any(Location.class),any(ItemStack.class))).thenAnswer(c->drop(c.getArgument(1)));
            when(doors.open(eq(0),any(Runnable.class))).thenAnswer(c->{((Runnable)c.getArgument(1)).run();return CompletableFuture.completedFuture(true);});
            bukkit=mockStatic(Bukkit.class); staticDoors=mockStatic(DoorService.class); runtime=mockStatic(DungeonSessionRuntime.class);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(base.world);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(base.world));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first,second));
            bukkit.when(()->Bukkit.getPlayer(first.getUniqueId())).thenReturn(first);
            bukkit.when(()->Bukkit.getPlayer(second.getUniqueId())).thenReturn(second);
            staticDoors.when(()->DoorService.keyRespawn(any())).thenReturn(at);
            runtime.when(DungeonSessionRuntime::messages).thenReturn(mock(Messages.class));
            runtime.when(()->DungeonSessionRuntime.contains(any(),any())).thenCallRealMethod();
            stacks=mockConstruction(ItemStack.class,(stack,context)->{
                var meta=mock(ItemMeta.class); var data=mock(PersistentDataContainer.class); var tokens=new HashMap<NamespacedKey,String>();
                when(stack.getItemMeta()).thenReturn(meta); when(stack.getPersistentDataContainer()).thenReturn(data); when(meta.getPersistentDataContainer()).thenReturn(data);
                doAnswer(c->{tokens.put(c.getArgument(0),c.getArgument(2));return null;}).when(data).set(any(),eq(PersistentDataType.STRING),anyString());
                when(data.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c->tokens.get(c.getArgument(0)));
            });
        }
        ItemStack currentKey() { return key(id+":first"); }
        Item drop(ItemStack stack) {
            var item=mock(Item.class); when(item.getUniqueId()).thenReturn(UUID.randomUUID());
            when(item.getItemStack()).thenReturn(stack); when(item.isValid()).thenReturn(true);
            when(item.getWorld()).thenReturn(base.world); when(item.getLocation()).thenReturn(at);
            when(item.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            ground.add(item);
            doAnswer(c->{ground.remove(item);when(item.isValid()).thenReturn(false);keys.removed(item);return null;}).when(item).remove();
            return item;
        }
        PlayerDeathEvent death(List<ItemStack> drops) {
            var manager=mock(SessionManager.class); var owner=base.runtime(manager,session); owner.keys=keys;
            when(manager.sessionOf(first.getUniqueId())).thenReturn(Optional.of(session)); when(manager.runtime(session)).thenReturn(owner);
            var event=mock(PlayerDeathEvent.class);when(event.getEntity()).thenReturn(first);when(event.getDrops()).thenReturn(drops);
            new SessionListener(manager).death(event); return event;
        }
        @Override public void close() { stacks.close();runtime.close();staticDoors.close();bukkit.close(); }
    }
    @Test void deathBeforeClearRecoversOnceAndRemovesOriginalDeathDropAndCursor() throws Exception {
        try(var f=new Fixture(false)) {
            var original=f.currentKey();var foreign=key(UUID.randomUUID()+":first");var ordinary=key(null);
            f.slots.get(f.first).put(0,original); f.cursors.get(f.first).set(f.currentKey());
            var drops=new ArrayList<>(List.of(original,foreign,ordinary)); f.death(drops);
            assertEquals(List.of(foreign,ordinary),drops);assertNull(f.slots.get(f.first).get(0));assertNull(f.cursors.get(f.first).get());
            assertEquals(1,f.ground.size());assertEquals(1,f.stacks.constructed().size());
            var recovered=f.ground.getFirst().getItemStack();assertTrue(f.keys.matches(recovered));
            f.keys.tick();f.keys.tick();assertEquals(1,f.stacks.constructed().size());
            assertFalse(f.keys.use(f.second,null,recovered));f.keys.roomCleared();assertTrue(f.keys.use(f.second,null,recovered));
            assertTrue(f.ground.isEmpty());f.keys.tick();assertEquals(1,f.stacks.constructed().size());
        }
    }
    @Test void deathWithAnotherValidCopyDoesNotCreateReplacement() throws Exception {
        try(var f=new Fixture(false)) {
            var dying=f.currentKey();var survivor=f.currentKey();f.slots.get(f.first).put(0,dying);f.slots.get(f.second).put(0,survivor);
            var drops=new ArrayList<>(List.of(dying));f.death(drops);f.keys.tick();
            assertTrue(drops.isEmpty());assertNull(f.slots.get(f.first).get(0));assertSame(survivor,f.slots.get(f.second).get(0));
            assertTrue(f.stacks.constructed().isEmpty());assertTrue(f.ground.isEmpty());
        }
    }
    @Test void keepInventoryDeathRetainsAnticipatedKeyWithoutMintingAnother() throws Exception {
        try(var f=new Fixture(true)) {
            var original=f.currentKey();f.slots.get(f.first).put(0,original);
            var drops=new ArrayList<>(List.of(original));f.death(drops);f.keys.tick();
            assertSame(original,f.slots.get(f.first).get(0));assertTrue(drops.isEmpty());assertTrue(f.stacks.constructed().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void leaveRemovesAllRoomsAndRecoversForRemainingPlayerEvenWhenHolderIsDifferent(boolean trackedHolder) throws Exception {
        try(var f=new Fixture(false)) {
            f.slots.get(f.first).put(0,f.currentKey());f.slots.get(f.first).put(1,key(f.id+":previous"));f.cursors.get(f.first).set(key(f.id+":previous"));
            if(!trackedHolder) SessionRuntimeRegressionTest.field(f.keys,"holder",f.second.getUniqueId());
            f.participants.remove(f.first);f.keys.leave(f.first);
            assertNull(f.slots.get(f.first).get(0));assertNull(f.slots.get(f.first).get(1));assertNull(f.cursors.get(f.first).get());
            assertEquals(1,f.stacks.constructed().size());f.keys.tick();assertEquals(1,f.stacks.constructed().size());
        }
    }
    @Test void lastPlayerLeaveDoesNotMintKeyAndFinishCleansEvenWithoutTrackedRoom() throws Exception {
        try(var f=new Fixture(false)) {
            f.slots.get(f.first).put(0,f.currentKey());f.participants.clear();f.keys.leave(f.first);
            assertTrue(f.stacks.constructed().isEmpty());
            SessionRuntimeRegressionTest.field(f.keys,"room",-1);
            f.slots.get(f.second).put(0,key(f.id+":previous"));f.cursors.get(f.second).set(key(f.id+":first"));
            var old=f.drop(key(f.id+":previous"));var foreign=f.drop(key(UUID.randomUUID()+":first"));var ordinary=f.drop(key(null));
            f.keys.clear();assertNull(f.slots.get(f.second).get(0));assertNull(f.cursors.get(f.second).get());
            verify(old).remove();verify(foreign,never()).remove();verify(ordinary,never()).remove();
        }
    }
    @Test void deathOnLastLifeWithNoOtherParticipantDoesNotMintReplacement() throws Exception {
        try(var f=new Fixture(false)) {
            f.participants.remove(f.second);when(f.session.livesLeft(f.first.getUniqueId())).thenReturn(1);
            var original=f.currentKey();f.slots.get(f.first).put(0,original);
            var drops=new ArrayList<>(List.of(original));f.death(drops);
            assertTrue(drops.isEmpty());assertNull(f.slots.get(f.first).get(0));
            assertTrue(f.ground.isEmpty());assertTrue(f.stacks.constructed().isEmpty());
        }
    }
    @Test void leavingHolderReusesSurvivorsCursorCopyWithoutMintingReplacement() throws Exception {
        try(var f=new Fixture(false)) {
            f.slots.get(f.first).put(0,f.currentKey());var survivor=f.currentKey();f.cursors.get(f.second).set(survivor);
            f.participants.remove(f.first);f.keys.leave(f.first);f.keys.tick();
            assertNull(f.slots.get(f.first).get(0));assertSame(survivor,f.cursors.get(f.second).get());
            assertTrue(f.ground.isEmpty());assertTrue(f.stacks.constructed().isEmpty());
        }
    }
    @Test void deathReusesValidGroundCopyAndDiscardsStaleTrackedDrop() throws Exception {
        try(var f=new Fixture(false)) {
            f.slots.get(f.first).put(0,f.currentKey());var stale=f.drop(key(f.id+":previous"));
            var valid=f.drop(f.currentKey());SessionRuntimeRegressionTest.field(f.keys,"dropped",stale);
            f.death(new ArrayList<>(List.of(f.currentKey())));f.keys.tick();
            assertEquals(List.of(valid),f.ground);assertTrue(f.stacks.constructed().isEmpty());verify(stale).remove();
        }
    }
    @Test void advancingRoomDiscardsAnticipatedCopiesWithoutRespawningOldToken() throws Exception {
        try(var f=new Fixture(false)) {
            f.slots.get(f.first).put(0,f.currentKey());var old=f.drop(f.currentKey());
            when(f.session.roomIndex()).thenReturn(1);f.keys.tick();f.keys.tick();
            assertNull(f.slots.get(f.first).get(0));assertTrue(f.ground.isEmpty());verify(old).remove();
            assertTrue(f.stacks.constructed().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void recoveryDoesNotAdoptStaleOrOtherSessionTrackedDrop(boolean sameSession) throws Exception {
        try(var f=new Fixture(false)) {
            var def=f.session.def();var r=def.rooms().getFirst();
            when(f.session.def()).thenReturn(SpawnerPresets.withRooms(def,List.of(new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),UnlockMode.KEY,"*",r.spawners()),def.rooms().getLast())));
            SessionRuntimeRegressionTest.field(f.keys,"holder",null);
            var old=f.drop(key((sameSession?f.id:UUID.randomUUID())+":previous"));SessionRuntimeRegressionTest.field(f.keys,"dropped",old);
            f.keys.create();assertEquals(1,f.stacks.constructed().size());assertTrue(f.keys.matches(f.stacks.constructed().getFirst()));
            if(sameSession) verify(old).remove();else verify(old,never()).remove();
        }
    }
}

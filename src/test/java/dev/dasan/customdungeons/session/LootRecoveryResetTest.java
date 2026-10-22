package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import dev.dasan.customdungeons.storage.LootBlockRecord;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootRecoveryResetTest {
    final LootChestServiceTest fixture=new LootChestServiceTest();
    final LootChestServiceTest.Blocks blocks=spy(fixture.blocks);
    final Point point=LootChestServiceTest.POSITION;
    final List<String> reports=new ArrayList<>();
    LootChestService ambiguous() {
        var record=new LootBlockRecord(point,"test","first","loot",null,LootChestServiceTest.ORIGINAL,false,true,blocks.expectedChest());
        fixture.store.records.put(point,record);blocks.data="minecraft:chest[type=single]";blocks.contents[0]=fixture.item();
        var service=new LootChestService(fixture.store,blocks,List.of(record),()->{fail("Recovery cannot roll loot");return 0;},fixture.failures::add);
        service.tick();assertTrue(service.blocked("test"));clearInvocations(blocks);return service;
    }
    @Test void resetDiscardIsDurablePreservesCurrentContentsAndReleasesOnlyItsDungeon() {
        var service=ambiguous();var item=blocks.contents[0];
        var other=new LootBlockRecord(new Point("other",1,64,1,0,0),"other","room","loot",null,"minecraft:stone",false,true);
        fixture.store.records.put(other.position(),other);
        var write=new CompletableFuture<Void>();fixture.store.next=write;
        var resolved=service.resolveAmbiguous("test",(p,reason)->reports.add(p+": "+reason));service.tick();
        assertFalse(resolved.isDone());assertTrue(service.blocked("test"));assertTrue(fixture.store.records.containsKey(point));assertTrue(reports.isEmpty());verifyNoInteractions(blocks);
        write.complete(null);service.tick();assertEquals(1,resolved.join());assertFalse(service.blocked("test"));
        assertEquals(Map.of(other.position(),other),fixture.store.records);assertSame(item,blocks.contents[0]);assertEquals("minecraft:chest[type=single]",blocks.data);
        assertEquals(1,reports.size());assertTrue(reports.getFirst().contains("neither original nor expected"));verify(blocks).release(point);verifyNoMoreInteractions(blocks);
        var restart=new LootChestService(fixture.store,blocks,List.of(),()->0,fixture.failures::add);restart.tick();assertFalse(restart.blocked("test"));
    }
    @Test void failedDiscardKeepsBackupAndBlockingAndCanBeRetried() {
        var service=ambiguous();var record=fixture.store.records.get(point);var item=blocks.contents[0];
        fixture.store.next=CompletableFuture.failedFuture(new IllegalStateException("disk"));
        var resolved=service.resolveAmbiguous("test",(p,reason)->reports.add(reason));service.tick();
        assertThrows(CompletionException.class,resolved::join);assertTrue(service.blocked("test"));assertEquals(record,fixture.store.records.get(point));assertSame(item,blocks.contents[0]);assertTrue(reports.isEmpty());
        resolved=service.resolveAmbiguous("test",(p,reason)->reports.add(reason));service.tick();assertEquals(1,resolved.join());assertFalse(service.blocked("test"));assertEquals(1,reports.size());assertSame(item,blocks.contents[0]);
    }
    @Test void resetDoesNotDiscardUninspectedOrOrdinaryFailedRestorations() {
        var record=new LootBlockRecord(point,"test","first","loot",null,LootChestServiceTest.ORIGINAL,false,true,blocks.expectedChest());
        blocks.chest(point,record.expectedData());blocks.failRestore=true;fixture.store.records.put(point,record);
        var service=new LootChestService(fixture.store,blocks,List.of(record),()->0,fixture.failures::add);
        assertEquals(0,service.resolveAmbiguous("test",(p,reason)->fail()).join());service.tick();
        assertEquals(0,service.resolveAmbiguous("test",(p,reason)->fail()).join());assertTrue(service.blocked("test"));assertEquals(record,fixture.store.records.get(point));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void managerResetResolvesWithoutActiveSessionAndLogsPositionAndReason(boolean retainedFreeSession) throws Exception {
        var service=ambiguous();var f=new SessionRuntimeRegressionTest();f.configure();
        var messages=new dev.dasan.customdungeons.text.Messages();
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/messages.yml")),java.nio.charset.StandardCharsets.UTF_8)) {
            messages.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader),"");
        }
        when(f.plugin.messages()).thenReturn(messages);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var manager=spy(new SessionManager(f.plugin,f.definitions,f.config,f.storage));doReturn(service).when(manager).loot();
            if(retainedFreeSession) {
                var previous=new LootChestServiceTest();previous.start(false,dev.dasan.customdungeons.model.LootChestDef.Trigger.ROOM_ENTER,dev.dasan.customdungeons.model.FinishMode.NONE,false,previous.player());
                previous.session.finish(false,true);previous.reset();assertEquals(SessionState.FREE,previous.session.state().state());
                SessionRuntimeRegressionTest.field(manager,"sessions",new HashMap<>(Map.of("test",previous.session)));
            }
            var result=manager.reset("test");service.tick();assertEquals(1,result.join());assertFalse(service.blocked("test"));
            verify(f.plugin.getLogger()).warning(argThat((String message)->message.contains("world 1 64 1") && message.contains("test") && message.contains("neither original nor expected")));manager.shutdown();
        }
    }
    @Test void managerResetWithSessionKeepsTheExistingStopBehaviorAndNeverResolves() {
        var service=ambiguous();var manager=mock(SessionManager.class);var session=mock(DungeonSession.class);
        var state=new SessionStateMachine();state.openLobby();when(session.state()).thenReturn(state);
        when(manager.session("test")).thenReturn(Optional.of(session));doCallRealMethod().when(manager).reset("test");doCallRealMethod().when(manager).stop("test");
        assertEquals(0,manager.reset("test").join());verify(session).finish(false,true);verify(manager,never()).loot();
        assertTrue(service.blocked("test"));assertTrue(fixture.store.records.containsKey(point));verifyNoInteractions(blocks);
    }
}

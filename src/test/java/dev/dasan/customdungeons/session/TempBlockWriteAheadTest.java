package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.runtime.BlockRestoration;
import dev.dasan.customdungeons.storage.TempBlockRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TempBlockWriteAheadTest {
    private void recover(TempBlockOwnershipTest.Fixture f) {
        TempBlockRecord record=f.records.getLast();
        BlockRestoration.restore(f.block,f.air,record.placedBlockData());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void pendingOrRejectedOpeningKeepsTheExistingFillRecoverable(boolean rejected) {
        var f=new TempBlockOwnershipTest.Fixture();f.place();
        var opened=f.blocks.openDoor(List.of(f.block),f.air,()->!rejected);
        if(rejected){f.blocks.tick(2);assertFalse(opened.join());}
        assertSame(f.pillar,f.state.get());
        assertEquals("minecraft:stone_bricks",f.records.getLast().placedBlockData());
        recover(f);assertSame(f.air,f.state.get());
    }
    @Test void openingAnOwnedFillIsItsRestorationAndNeverOverwritesItsJournal() {
        var f=new TempBlockOwnershipTest.Fixture();f.place();
        var opened=f.blocks.openDoor(List.of(f.block),f.air,()->true);f.blocks.tick(2);
        assertTrue(opened.join());assertSame(f.air,f.state.get());
        assertEquals(1,f.records.size());
        clearInvocations(f.block);recover(f);
        verify(f.block,never()).setBlockData(any(),anyBoolean());
        f.blocks.restoreAll();assertSame(f.air,f.state.get());
    }
    @Test void anOpeningOfAnOriginalSolidBlockWaitsForItsWriteAheadRecord() {
        var f=new TempBlockOwnershipTest.Fixture();f.state.set(f.pillar);
        var durable=new CompletableFuture<Void>();
        when(f.storage.addTempBlock(any())).thenAnswer(i->{
            assertSame(f.pillar,f.state.get());f.records.add(i.getArgument(0));return durable;
        });
        var opened=f.blocks.openDoor(List.of(f.block),f.air,()->true);f.blocks.tick(1);
        assertFalse(opened.isDone());verify(f.block,never()).setBlockData(any(),anyBoolean());
        var record=f.records.getLast();assertEquals("minecraft:stone_bricks",record.originalBlockData());
        assertEquals("minecraft:air",record.placedBlockData());
        durable.complete(null);f.blocks.tick(2);assertTrue(opened.join());assertSame(f.air,f.state.get());
        f.blocks.restoreAll();assertSame(f.pillar,f.state.get());
    }
    @Test void aPendingPillarWriteCannotTouchTheWorldAndCancellingItCannotLeaveARemnant() {
        var f=new TempBlockOwnershipTest.Fixture();var durable=new CompletableFuture<Void>();
        when(f.storage.addTempBlock(any())).thenAnswer(i->{
            assertSame(f.air,f.state.get());f.records.add(i.getArgument(0));return durable;
        });
        assertTrue(f.blocks.place(f.block,f.pillar,20));f.blocks.tick(1);
        verify(f.block,never()).setBlockData(any(),anyBoolean());
        f.blocks.restoreAll();durable.complete(null);f.blocks.tick(2);
        verify(f.block,never()).setBlockData(any(),anyBoolean());
        assertSame(f.air,f.state.get());
    }
    @Test void openingARecordedFillKeepsTheDoorReservationUntilSessionCleanup() {
        var f=new TempBlockOwnershipTest.Fixture();var journal=new SessionTempBlocks.Journal();
        var first=new SessionTempBlocks(f.storage,error->{throw new AssertionError(error);},journal);
        var second=new SessionTempBlocks(f.storage,error->{throw new AssertionError(error);},journal);
        assertTrue(first.place(f.block,f.pillar,Integer.MAX_VALUE));first.tick(1);
        var opened=first.openDoor(List.of(f.block),f.air,()->true);first.tick(2);assertTrue(opened.join());
        assertSame(f.air,f.state.get());assertFalse(second.place(f.block,f.pillar,20));
        first.restoreAll();assertTrue(second.place(f.block,f.pillar,20));second.restoreAll();
    }

}

package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntranceDoorTest {
    @Test void entranceIsDurableDoesNotAdvanceRoomAndRestoresAfterCrash() {
        var f=new DoorServiceTest();var records=new ArrayList<TempBlockRecord>();
        var d=f.session.def();var door=d.rooms().getFirst().door();
        when(f.session.def()).thenReturn(d.withStart(StartMode.PLATES,List.of(d.lobby()),3,door,false,true,false,10));
        var pending=new CompletableFuture<Void>();
        when(f.storage.addTempBlock(any())).thenAnswer(call->{records.add(call.getArgument(0));return pending;});
        when(f.storage.loadTempBlocks()).thenAnswer(call->CompletableFuture.completedFuture(List.copyOf(records)));
        when(f.storage.abortUnfinishedRuns(any())).thenReturn(CompletableFuture.completedFuture(0));
        when(f.storage.loadActive()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(f.storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            bukkit.when(()->Bukkit.createBlockData(Material.AIR)).thenReturn(f.air);
            bukkit.when(()->Bukkit.createBlockData(f.bars.getAsString())).thenReturn(f.bars);
            bukkit.when(()->Bukkit.createBlockData(f.air.getAsString())).thenReturn(f.air);
            var opened=f.doors.openEntrance();f.temp.tick(1);assertFalse(opened.isDone());assertSame(f.bars,f.contents.get(64).get());
            pending.complete(null);f.temp.tick(2);assertTrue(opened.join());assertSame(f.air,f.contents.get(64).get());
            verify(f.session,never()).openDoor();assertEquals(2,records.size());
            // Simulate a new process: recovery knows only the stored originals and world blocks.
            for(var block:f.blocks.values())when(block.getType()).thenReturn(Material.AIR);
            new RecoveryService(plugin,f.storage,mock(SessionManager.class)).recoverOnEnable();
            assertSame(f.bars,f.contents.get(64).get());assertSame(f.air,f.contents.get(65).get());
            verify(f.storage).removeTempBlock("world",0,64,0);
        }
    }
    @Test void normalResetRestoresEntrance() {
        var f=new DoorServiceTest();var d=f.session.def();
        when(f.session.def()).thenReturn(d.withStart(StartMode.AUTO,List.of(),3,d.rooms().getFirst().door(),false,true,false,10));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(()->Bukkit.createBlockData(Material.AIR)).thenReturn(f.air);
            f.doors.openEntrance();f.temp.tick(1);assertSame(f.air,f.contents.get(64).get());
            f.temp.restoreAll();assertSame(f.bars,f.contents.get(64).get());verify(f.session,never()).openDoor();
        }
    }
}

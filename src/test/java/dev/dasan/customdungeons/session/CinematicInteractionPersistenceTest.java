package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.storage.Storage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicInteractionPersistenceTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap(){PaperApiTestBootstrap.initialize();}
    CinematicRestorationTest harness() {var t=new CinematicRestorationTest();t.root=root;return t;}

    @Test void cameraCannotOpenContainersOrUseBlocksAndItems() {
        var t=harness();try(var f=t.new Fixture(false,Runnable::run)) {
            f.session.tick();var listener=new SessionListener(mock(SessionManager.class));
            var event=new PlayerInteractEvent(f.a.p,Action.RIGHT_CLICK_BLOCK,null,mock(org.bukkit.block.Block.class),org.bukkit.block.BlockFace.UP);
            event.setUseInteractedBlock(Event.Result.ALLOW);event.setUseItemInHand(Event.Result.ALLOW);
            listener.interact(event);
            assertEquals(Event.Result.DENY,event.useInteractedBlock());assertEquals(Event.Result.DENY,event.useItemInHand());
            var open=mock(InventoryOpenEvent.class);when(open.getPlayer()).thenReturn(f.a.p);dispatch(listener,open);
            verify(open).setCancelled(true);
            f.intro.skip(f.a.p.getUniqueId());f.session.tick();
            var restored=mock(InventoryOpenEvent.class);when(restored.getPlayer()).thenReturn(f.a.p);dispatch(listener,restored);
            verify(restored,never()).setCancelled(anyBoolean());assertEquals(org.bukkit.GameMode.SPECTATOR,f.b.mode.get());
        }
    }

    @Test void cameraCannotInteractWithEntitiesInventoriesDropsOrBlocks() {
        var t=harness();try(var f=t.new Fixture(false,Runnable::run)) {
            f.session.tick();var listener=new SessionListener(mock(SessionManager.class));
            var entity=mock(PlayerInteractEntityEvent.class);when(entity.getPlayer()).thenReturn(f.a.p);dispatch(listener,entity);verify(entity).setCancelled(true);
            var at=mock(PlayerInteractAtEntityEvent.class);when(at.getPlayer()).thenReturn(f.a.p);dispatch(listener,at);verify(at,atLeastOnce()).setCancelled(true);
            var click=mock(InventoryClickEvent.class);when(click.getWhoClicked()).thenReturn(f.a.p);dispatch(listener,click);verify(click).setCancelled(true);
            var drag=mock(InventoryDragEvent.class);when(drag.getWhoClicked()).thenReturn(f.a.p);dispatch(listener,drag);verify(drag).setCancelled(true);
            var drop=mock(PlayerDropItemEvent.class);when(drop.getPlayer()).thenReturn(f.a.p);dispatch(listener,drop);verify(drop).setCancelled(true);
            var swap=mock(PlayerSwapHandItemsEvent.class);when(swap.getPlayer()).thenReturn(f.a.p);dispatch(listener,swap);verify(swap).setCancelled(true);
            var broken=mock(BlockBreakEvent.class);when(broken.getPlayer()).thenReturn(f.a.p);dispatch(listener,broken);verify(broken).setCancelled(true);
        }
    }

    private void dispatch(SessionListener listener,Event event) {
        for(var method:SessionListener.class.getMethods()) {
            if(method.isAnnotationPresent(EventHandler.class) && method.getParameterCount()==1 && method.getParameterTypes()[0].isInstance(event)) {
                try {method.invoke(listener,event);} catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            }
        }
    }

    @Test void cameraFramesDoNotPersistUnchangedMembershipButDepartureDoes() {
        var t=harness();try(var f=t.new Fixture(false,Runnable::run)) {
            var storage=mock(Storage.class);when(storage.markActive(any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.startRun(anyString(),any(),any())).thenReturn(CompletableFuture.completedFuture(1L));
            var recorder=new RunRecorder(storage,mock(SessionManager.class),Logger.getAnonymousLogger());
            recorder.onStateChange(f.session,SessionState.FREE,SessionState.LOBBY);
            recorder.onStateChange(f.session,SessionState.LOBBY,SessionState.RUNNING);
            clearInvocations(storage);f.session.tick();
            var event=mock(PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(f.a.p);
            for(int i=0;i<100;i++){recorder.teleport(event);f.session.tick();}
            verify(storage,never()).markActive(any());
            f.session.disconnect(f.a.p.getUniqueId());recorder.playerDeparted(f.session).join();
            verify(storage).markActive(argThat(r->!r.players().contains(f.a.p.getUniqueId()) && r.players().contains(f.b.p.getUniqueId())));
            recorder.teleport(event);verify(storage,times(1)).markActive(any());
        }
    }

    @Test void leavingDuringIntroPersistsOnlyTheRemainingParticipants() {
        var t=harness();try(var f=t.new Fixture(false,Runnable::run)) {
            var storage=mock(Storage.class);when(storage.markActive(any())).thenReturn(CompletableFuture.completedFuture(null));
            var recorder=new RunRecorder(storage,mock(SessionManager.class),Logger.getAnonymousLogger());
            recorder.onStateChange(f.session,SessionState.FREE,SessionState.LOBBY);clearInvocations(storage);
            f.session.tick();f.session.leave(f.a.p.getUniqueId());recorder.playerDeparted(f.session).join();
            verify(storage).markActive(argThat(r->!r.players().contains(f.a.p.getUniqueId()) && r.players().contains(f.b.p.getUniqueId())));
        }
    }
}

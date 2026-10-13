package dev.dasan.customdungeons.ability.zone;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.dasan.customdungeons.ability.MobMovement;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PillarNavigationRegressionTest {
    private static List<Block> pending(ZoneServiceTest.Fixture f,Pathfinder path) {
        when(f.entity.hasAI()).thenReturn(true);when(f.entity.getTarget()).thenReturn(f.player);
        when(f.entity.getBoundingBox()).thenReturn(new BoundingBox(-.3,64,-.3,.3,65.8,.3));
        when(f.entity.getPathfinder()).thenReturn(path);
        var pending=new ArrayList<Block>();
        when(f.blocks.place(any(),any(),anyInt())).thenAnswer(i->{pending.add(i.getArgument(0));return true;});
        f.cast("falling_pillars",Map.of("count",1));f.step(42);
        assertEquals(3,pending.size());return pending;
    }
    private static void materialise(Block block) {
        when(block.getType()).thenReturn(Material.STONE_BRICKS);when(block.isEmpty()).thenReturn(false);
    }
    @Test void nativeRoutesAreRefreshedAfterTheJournalledBlocksActuallyAppear() {
        try(var f=new ZoneServiceTest.Fixture()) {
            var path=mock(Pathfinder.class);var blocks=pending(f,path);
            verify(path,never()).moveTo(f.player,1);
            materialise(blocks.getFirst());f.step(1);
            verify(path).moveTo(f.player,1);f.step(4);
            verify(path,times(1)).moveTo(f.player,1);
            blocks.forEach(PillarNavigationRegressionTest::materialise);f.step(1);
            verify(path,times(2)).moveTo(f.player,1);
            verify(f.entity,never()).setTarget(any());
        }
    }
    @Test void materialisingPillarsDoNotWriteANativeRouteWhileTheMobIsPaused() {
        try(var f=new ZoneServiceTest.Fixture();var movement=mockStatic(MobMovement.class,CALLS_REAL_METHODS)) {
            movement.when(()->MobMovement.paused(f.caster)).thenReturn(true);
            var path=mock(Pathfinder.class);var blocks=pending(f,path);
            blocks.forEach(PillarNavigationRegressionTest::materialise);f.step(2);
            verify(path,never()).moveTo(f.player,1);
            movement.when(()->MobMovement.paused(f.caster)).thenReturn(false);f.step(1);
            verify(path).moveTo(f.player,1);
        }
    }
    @Test void routesToForeignPlayersAndDisabledAiAreLeftAlone() {
        try(var f=new ZoneServiceTest.Fixture()) {
            var path=mock(Pathfinder.class);var blocks=pending(f,path);
            blocks.forEach(PillarNavigationRegressionTest::materialise);
            when(f.entity.getTarget()).thenReturn(f.foreign);f.step(2);
            verifyNoInteractions(path);
            when(f.entity.getTarget()).thenReturn(f.player);when(f.entity.hasAI()).thenReturn(false);f.step(2);
            verifyNoInteractions(path);
            when(f.entity.hasAI()).thenReturn(true);f.step(1);
            verify(path).moveTo(f.player,1);
        }
    }
    @Test void anActiveRetreatKeepsAuthorityOverTheNativeRoute() {
        try(var f=new ZoneServiceTest.Fixture()) {
            var path=mock(Pathfinder.class);var blocks=pending(f,path);
            blocks.forEach(PillarNavigationRegressionTest::materialise);
            MobMovement.retreat(f.caster,4);f.step(2);
            verifyNoInteractions(path);
            f.step(3);verify(path).moveTo(f.player,1);
        }
    }
}

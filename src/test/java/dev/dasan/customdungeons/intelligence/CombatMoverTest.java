package dev.dasan.customdungeons.intelligence;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.dasan.customdungeons.ability.MobMovement;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatMoverTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static class Fixture {
        static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
        final World world=mock(World.class);
        final Mob entity=mock(Mob.class);
        final Player player=mock(Player.class);
        final MobHost host=mock(MobHost.class);
        final Pathfinder path=mock(Pathfinder.class);
        final Pathfinder.PathResult route=mock(Pathfinder.PathResult.class);
        final Map<String,Block> blocks=new HashMap<>();
        final Location origin=new Location(world,3.5,64,.5),target=new Location(world,.5,64,.5);
        final ActiveMob mob;
        Fixture() {
            when(world.getName()).thenReturn("room");when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);
            when(border.getCenter()).thenReturn(new Location(world,0,64,0));when(border.getSize()).thenReturn(1000d);
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->block(i.getArgument(0),i.getArgument(1),i.getArgument(2)));
            when(entity.getUniqueId()).thenReturn(new UUID(0,0));when(entity.isValid()).thenReturn(true);when(entity.hasAI()).thenReturn(true);
            when(entity.getWorld()).thenReturn(world);when(entity.getLocation()).thenAnswer(i->origin.clone());when(entity.getPathfinder()).thenReturn(path);
            when(entity.getBoundingBox()).thenReturn(new BoundingBox(3.2,64,.2,3.8,65.8,.8));
            when(entity.getTarget()).thenReturn(player);when(entity.getHealth()).thenReturn(20d);
            var hp=mock(org.bukkit.attribute.AttributeInstance.class);when(hp.getValue()).thenReturn(20d);when(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(hp);
            when(player.getUniqueId()).thenReturn(new UUID(1,1));when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(i->target.clone());when(player.getEyeLocation()).thenAnswer(i->target.clone().add(0,1.6,0));
            when(entity.getEyeLocation()).thenAnswer(i->origin.clone().add(0,1.6,0));
            when(player.isOnline()).thenReturn(true);when(player.isValid()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(host.players()).thenReturn(List.of(player));when(host.scheduler()).thenReturn(mock(TickScheduler.class));
            when(host.area()).thenReturn(new MobArea("room",new BoundingBox(-24,60,-24,24,72,24)));
            var template=new MobTemplate("a","SKELETON","",20,1,.3,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false).withIntelligence(IntelligenceDef.level(4));
            mob=new ActiveMob(entity,template,host);when(host.mobs()).thenReturn(List.of(mob));
            when(route.canReachFinalPoint()).thenReturn(true);
            when(path.findPath(any(Location.class))).thenAnswer(i->{var end=(Location)i.getArgument(0);when(route.getPoints()).thenReturn(List.of(end));when(route.getFinalPoint()).thenReturn(end);return route;});
            when(path.moveTo(route,1)).thenReturn(true);
            when(path.getCurrentPath()).thenReturn(route);
        }
        Block block(int x,int y,int z) {
            return blocks.computeIfAbsent(x+":"+y+":"+z,k->{var b=mock(Block.class);
                boolean solid=y==63||y==70;when(b.getType()).thenReturn(solid?Material.STONE:Material.AIR);
                when(b.isSolid()).thenReturn(solid);when(b.isEmpty()).thenReturn(!solid);when(b.isPassable()).thenReturn(!solid);
                when(b.getX()).thenReturn(x);when(b.getY()).thenReturn(y);when(b.getZ()).thenReturn(z);return b;});
        }
        void floor(int x,int z,double height) {
            for(int y=60;y<=67;y++) {var b=block(x,y,z);when(b.getType()).thenReturn(Material.AIR);when(b.isSolid()).thenReturn(false);when(b.isEmpty()).thenReturn(true);when(b.isPassable()).thenReturn(true);}
            int y=(int)Math.ceil(height)-1;var b=block(x,y,z);var shape=mock(VoxelShape.class);
            when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,height-y,1)));
            when(b.getType()).thenReturn(Material.STONE);when(b.isEmpty()).thenReturn(false);when(b.isPassable()).thenReturn(false);when(b.getCollisionShape()).thenReturn(shape);
        }
        CombatMover mover() {return new CombatMover();}
    }
    @Test void routesWithPaperAndFacesTargetWhileBackingUpWithoutVelocityOrTargetWrites() {
        var f=new Fixture();var mover=f.mover();mover.evaluate(f.mob,f.mob.template().intelligence(),0,null);
        verify(f.path).moveTo(f.route,1);verify(f.entity).lookAt(f.player);
        verify(f.entity,never()).setTarget(any());verify(f.entity,never()).setVelocity(any());
        verify(f.world,never()).getHighestBlockYAt(anyInt(),anyInt(),any());verify(f.world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void pressureLooksAtTheTargetEvenIfNativeRouteStartsUnderTheMob() {
        var f=new Fixture();f.origin.setX(8.5);when(f.route.getNextPoint()).thenAnswer(i->f.origin.clone());
        var def=IntelligenceDef.level(2).withMovement(MovementDef.DEFAULT.withStyle(MovementDef.Style.MELEE));
        f.mover().evaluate(f.mob,def,0,null);verify(f.path).moveTo(f.route,1);
        verify(f.entity).lookAt(f.player);verify(f.entity,never()).lookAt(any(Location.class));
    }
    @Test void everyPauseAndDisabledAiCauseZeroNavigationOrOrientationWrites() {
        var f=new Fixture();var mover=f.mover();
        try(var movement=mockStatic(MobMovement.class,CALLS_REAL_METHODS)) {
            movement.when(()->MobMovement.paused(f.mob)).thenReturn(true);
            mover.evaluate(f.mob,f.mob.template().intelligence(),0,null);verifyNoInteractions(f.path);verify(f.entity,never()).lookAt(any(Entity.class));
            movement.when(()->MobMovement.paused(f.mob)).thenReturn(false);
            when(f.entity.hasAI()).thenReturn(false);mover.evaluate(f.mob,f.mob.template().intelligence(),10,null);verifyNoInteractions(f.path);
        }
    }
    @Test void navigationFailureStopsOnlyOwnedRouteAndYieldsToVanilla() {
        var f=new Fixture();var mover=f.mover();mover.evaluate(f.mob,f.mob.template().intelligence(),0,null);
        when(f.path.findPath(any(Location.class))).thenReturn(null);mover.evaluate(f.mob,f.mob.template().intelligence(),10,null);
        verify(f.path).stopPathfinding();verify(f.path,times(1)).moveTo(any(Pathfinder.PathResult.class),anyDouble());
        mover.evaluate(f.mob,IntelligenceDef.level(0),20,null);verify(f.path,times(1)).stopPathfinding();
    }
    @Test void levelsZeroAndOneAndForeignTargetsDoNoMovementWork() {
        var f=new Fixture();var mover=f.mover();
        mover.evaluate(f.mob,IntelligenceDef.level(0),0,null);mover.evaluate(f.mob,IntelligenceDef.level(1),10,null);verifyNoInteractions(f.path);
        when(f.host.players()).thenReturn(List.of());mover.evaluate(f.mob,f.mob.template().intelligence(),20,null);verifyNoInteractions(f.path);
    }
    @Test void lavaWaterVoidAndUnloadedRoutesAreRejectedEvenWithSafeEndpoint() {
        for(Material danger:List.of(Material.LAVA,Material.WATER,Material.AIR)) {
            var f=new Fixture();var mover=f.mover();
            for(int z=-4;z<=4;z++)for(int x=5;x<=7;x++) {var b=f.block(x,63,z);when(b.getType()).thenReturn(danger);when(b.isSolid()).thenReturn(false);when(b.isEmpty()).thenReturn(danger==Material.AIR);when(b.isPassable()).thenReturn(true);}
            mover.evaluate(f.mob,f.mob.template().intelligence(),0,null);verify(f.path,never()).moveTo(any(Pathfinder.PathResult.class),anyDouble());
        }
        var f=new Fixture();when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);verifyNoInteractions(f.path);
    }
    @Test void partialStepsAndStairsAreValidatedUnderTheRoof() {
        var f=new Fixture();
        for(int x=4;x<=12;x++)for(int z=-3;z<=3;z++)f.floor(x,z,64+Math.min(3,(x-3)*.5));
        when(f.path.findPath(any(Location.class))).thenAnswer(i->{var end=(Location)i.getArgument(0);var points=new ArrayList<Location>();
            for(int x=4;x<=end.getBlockX();x++)points.add(new Location(f.world,x+.5,64+Math.min(3,(x-3)*.5),.5));
            when(f.route.getPoints()).thenReturn(points);when(f.route.getFinalPoint()).thenReturn(end);return f.route;});
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);verify(f.path).moveTo(f.route,1);
    }
    @Test void pathThatCannotReachAndHugePathsAreBounded() {
        var f=new Fixture();when(f.route.canReachFinalPoint()).thenReturn(false);
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);verify(f.path,never()).moveTo(any(Pathfinder.PathResult.class),anyDouble());
        when(f.route.getPoints()).thenReturn(Collections.nCopies(65,f.origin));clearInvocations(f.world);
        assertFalse(CombatMover.safePath(f.origin,f.route,.6,1.8,null));verifyNoInteractions(f.world);
    }
    @Test void drawnBowAndCrossbowMustActuallyAimAtTheMob() {
        var f=new Fixture();when(f.player.hasActiveItem()).thenReturn(true);
        var item=mock(org.bukkit.inventory.ItemStack.class);when(f.player.getActiveItem()).thenReturn(item);
        f.target.setYaw(-90);
        for(Material weapon:List.of(Material.BOW,Material.CROSSBOW)) {
            when(item.getType()).thenReturn(weapon);assertTrue(CombatMover.aimedAt(f.player,f.entity.getEyeLocation()));
            f.target.setYaw(90);assertFalse(CombatMover.aimedAt(f.player,f.entity.getEyeLocation()));f.target.setYaw(-90);
        }
        when(f.player.hasActiveItem()).thenReturn(false);assertFalse(CombatMover.aimedAt(f.player,f.entity.getEyeLocation()));
    }
    @Test void loadedCrossbowThreatensTheMobWithoutAnActiveUseAnimation() {
        var f=new Fixture();f.target.setYaw(-90);
        var inventory=mock(org.bukkit.inventory.PlayerInventory.class);when(f.player.getInventory()).thenReturn(inventory);
        var item=mock(org.bukkit.inventory.ItemStack.class);when(item.getType()).thenReturn(Material.CROSSBOW);
        var meta=mock(org.bukkit.inventory.meta.CrossbowMeta.class);when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasChargedProjectiles()).thenReturn(true);when(inventory.getItemInMainHand()).thenReturn(item);
        assertTrue(CombatMover.aimedAt(f.player,f.entity.getEyeLocation()));
        when(meta.hasChargedProjectiles()).thenReturn(false);assertFalse(CombatMover.aimedAt(f.player,f.entity.getEyeLocation()));
    }
    @Test void dodgesAnotherHostArchersShotInsteadOfOnlyWatchingItsCurrentTarget() {
        var f=new Fixture();var archer=mock(Player.class);var at=new Location(f.world,3.5,64,4.5,180,0);
        when(archer.getWorld()).thenReturn(f.world);when(archer.getLocation()).thenReturn(at);when(archer.getEyeLocation()).thenReturn(at.clone().add(0,1.6,0));
        when(archer.isOnline()).thenReturn(true);when(archer.isValid()).thenReturn(true);when(archer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(archer.hasActiveItem()).thenReturn(true);var bow=mock(org.bukkit.inventory.ItemStack.class);when(bow.getType()).thenReturn(Material.BOW);when(archer.getActiveItem()).thenReturn(bow);
        when(f.host.players()).thenReturn(List.of(f.player,archer));
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);
        var end=org.mockito.ArgumentCaptor.forClass(Location.class);verify(f.path).findPath(end.capture());
        assertEquals(6.5,end.getValue().getX());assertEquals(.5,end.getValue().getZ(),1e-6);
    }
    @Test void backwardsRetreatFacesThePlayerAndPauseDoesNotWriteRotation() {
        var f=new Fixture();f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);
        var to=f.origin.clone().add(.2,0,0);
        assertTrue(MobMovement.face(f.entity,f.origin,to));assertEquals(90,to.getYaw());verify(f.entity).setBodyYaw(90);
        try(var movement=mockStatic(dev.dasan.customdungeons.ability.combat.CombatService.class,CALLS_REAL_METHODS)) {
            movement.when(()->dev.dasan.customdungeons.ability.combat.CombatService.paused(f.mob)).thenReturn(true);clearInvocations(f.entity);
            assertFalse(MobMovement.face(f.entity,f.origin,to));verify(f.entity,never()).setRotation(anyFloat(),anyFloat());
        }
    }
    @Test void hostTickIsStaggeredAndPhaseDowngradeAndReloadReleaseTheRoute() {
        var f=new Fixture();var engine=mock(dev.dasan.customdungeons.ability.AbilityEngine.class);
        try(var bukkit=mockStatic(Bukkit.class);var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            IntelligenceService.track(f.mob);IntelligenceService.tick(f.mob,engine,1);verifyNoInteractions(f.path);
            IntelligenceService.tick(f.mob,engine,10);verify(f.path).moveTo(f.route,1);
            IntelligenceService.phase(f.mob,IntelligenceDef.level(1),11);verify(f.path).stopPathfinding();
            clearInvocations(f.path);IntelligenceService.tick(f.mob,engine,20);verifyNoInteractions(f.path);
            IntelligenceService.phase(f.mob,IntelligenceDef.level(4),21);IntelligenceService.tick(f.mob,engine,30);verify(f.path).moveTo(f.route,1);
            IntelligenceService.reset();verify(f.path).stopPathfinding();assertNull(IntelligenceService.brain(f.mob));
        }
    }
    @Test void quarterVirtualHealthUsesRetreatAndCurrentTargetOwnsFacing() {
        var f=new Fixture();var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);when(f.entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(MobKeys.VIRTUAL_MAX_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(true);
        when(pdc.get(MobKeys.VIRTUAL_MAX_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(5000d);
        when(pdc.get(MobKeys.VIRTUAL_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(1250d);
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);
        var endpoint=org.mockito.ArgumentCaptor.forClass(Location.class);verify(f.path).findPath(endpoint.capture());assertEquals(7.5,endpoint.getValue().getX());
        when(f.entity.getTarget()).thenReturn(null);
        var to=f.origin.clone().add(.2,0,0);assertTrue(MobMovement.face(f.entity,f.origin,to));assertEquals(270,to.getYaw());
    }
    @Test void routesHaveToStayInsideHostBoundsAndRespectBodyHeightAndWaterloggedFloor() {
        var f=new Fixture();when(f.host.area()).thenReturn(new MobArea("room",new BoundingBox(-2,60,-2,5,70,2)));
        f.mover().evaluate(f.mob,f.mob.template().intelligence(),0,null);verify(f.path,never()).moveTo(any(Pathfinder.PathResult.class),anyDouble());
        var low=new Fixture();when(low.entity.getBoundingBox()).thenReturn(new BoundingBox(3.2,64,.2,3.8,72,.8));
        low.mover().evaluate(low.mob,low.mob.template().intelligence(),0,null);verify(low.path,never()).moveTo(any(Pathfinder.PathResult.class),anyDouble());
        var wet=new Fixture();var data=mock(org.bukkit.block.data.Waterlogged.class);when(data.isWaterlogged()).thenReturn(true);
        for(int x=5;x<=7;x++)for(int z=-4;z<=4;z++)when(wet.block(x,63,z).getBlockData()).thenReturn(data);
        wet.mover().evaluate(wet.mob,wet.mob.template().intelligence(),0,null);verify(wet.path,never()).moveTo(any(Pathfinder.PathResult.class),anyDouble());
    }
    @Test void failedMoveToTriesAlternateEndpointsAndCleanupLeavesANewerPathAlone() {
        var f=new Fixture();when(f.path.moveTo(f.route,1)).thenReturn(false,true);
        var mover=f.mover();mover.evaluate(f.mob,f.mob.template().intelligence(),0,null);verify(f.path,times(2)).findPath(any(Location.class));
        var newer=mock(Pathfinder.PathResult.class);when(newer.getPoints()).thenReturn(List.of(f.target));when(f.path.getCurrentPath()).thenReturn(newer);
        mover.release(f.mob);verify(f.path,never()).stopPathfinding();
    }
    @Test void phaseDowngradeDoesNotResetTwentySecondRetreatCooldown() {
        var f=new Fixture();when(f.entity.getHealth()).thenReturn(5d);
        var engine=mock(dev.dasan.customdungeons.ability.AbilityEngine.class);
        try(var bukkit=mockStatic(Bukkit.class);var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            IntelligenceService.track(f.mob);IntelligenceService.tick(f.mob,engine,0);
            IntelligenceService.phase(f.mob,IntelligenceDef.level(1),10);IntelligenceService.phase(f.mob,IntelligenceDef.level(4),20);
            clearInvocations(f.path);IntelligenceService.tick(f.mob,engine,20);
            var end=org.mockito.ArgumentCaptor.forClass(Location.class);verify(f.path).findPath(end.capture());
            assertEquals(11.5,end.getValue().getX(),"Re-enabling intelligence cannot grant another retreat during its cooldown");
        }
    }
    @Test void phaseDowngradeDuringPauseReleasesOwnedPathOnFirstUnpausedTick() {
        var f=new Fixture();var engine=mock(dev.dasan.customdungeons.ability.AbilityEngine.class);
        try(var bukkit=mockStatic(Bukkit.class);var movement=mockStatic(MobMovement.class,CALLS_REAL_METHODS);
            var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            IntelligenceService.track(f.mob);IntelligenceService.tick(f.mob,engine,0);verify(f.path).moveTo(f.route,1);
            movement.when(()->MobMovement.paused(f.mob)).thenReturn(true);
            IntelligenceService.phase(f.mob,IntelligenceDef.level(0),1);verify(f.path,never()).stopPathfinding();
            movement.when(()->MobMovement.paused(f.mob)).thenReturn(false);
            IntelligenceService.tick(f.mob,engine,2);verify(f.path).stopPathfinding();
        }
    }
}

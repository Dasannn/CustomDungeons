package dev.dasan.customdungeons.tool;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WizardParticlesTest {
    @Test void precomputedSceneIncludesAreaRoomsDoorsAndSpawnerAndIsBounded() {
        var region=Region.of("dungeons",new BlockPos(-100,0,-100),new BlockPos(100,100,100));
        var point=new Point("dungeons",0,64,0,0,0);
        var room=new RoomDef("room",region,point,region,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("spawner",point,3,List.of())));
        var d=new DungeonDef("d","d",false,point,point,1,0,30,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room),List.of(),region);
        var scene=WizardParticles.prepare(d);
        assertTrue(scene.size()<=256);assertTrue(scene.size()>0);
        assertTrue(scene.stream().anyMatch(p->p.color()==org.bukkit.Color.ORANGE));
        assertTrue(scene.stream().anyMatch(p->p.color()==org.bukkit.Color.AQUA));
    }
    @Test void emptyDraftHasNoParticlesAndPartialRoomNeverCrashes() {
        var d=new DungeonDef("d","d",false,null,null,1,0,30,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
        assertTrue(WizardParticles.prepare(d).isEmpty());
        assertDoesNotThrow(()->WizardParticles.prepare(dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,
                List.of(new RoomDef("room",null,null,null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("s",null,3,List.of())))))));
    }
}

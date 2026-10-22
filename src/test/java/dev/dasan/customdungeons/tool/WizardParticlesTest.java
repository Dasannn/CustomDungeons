package dev.dasan.customdungeons.tool;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WizardParticlesTest {
    @Test void zonesHaveDistinctRegionAndSpawnerParticlesWithinTheExistingBudget() {
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("optional-zones",List.of(Map.of("id","side","region",Map.of("world","dungeons","min",Map.of("x",10,"y",60,"z",10),"max",Map.of("x",20,"y",70,"z",20)),"spawners",List.of(Map.of("id","s","location",Map.of("world","dungeons","x",12,"y",63,"z",12))))));
        var d=new dev.dasan.customdungeons.config.DefinitionCodec().decodeDungeon("d",yaml);var dots=WizardParticles.prepare(d);
        assertTrue(dots.size()<=256);assertTrue(dots.stream().anyMatch(dot->dot.color().equals(org.bukkit.Color.TEAL)));
        assertTrue(dots.stream().anyMatch(dot->dot.color().equals(org.bukkit.Color.BLUE)&&dot.x()==12&&dot.y()==63.5));
    }
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
    @Test void constructionSceneIncludesDistinctStartExitPlatesAndEntranceWithinSharedBudget() {
        var d=new dev.dasan.customdungeons.config.DefinitionCodec().decodeDungeon("d",new org.bukkit.configuration.file.YamlConfiguration())
            .withStart(StartMode.PLATES,List.of(new Point("dungeons",1.5,64,1.5,0,0)),3,
                Region.of("dungeons",new BlockPos(5,64,5),new BlockPos(6,65,5)),false,true,false,10)
            .withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of(new Point("dungeons",2.5,64,2.5,0,0)));
        var dots=WizardParticles.prepare(d);
        assertTrue(dots.size()<=256);
        assertTrue(dots.stream().anyMatch(p->p.color().equals(org.bukkit.Color.LIME) && p.x()==1.5 && p.y()==64.3));
        assertTrue(dots.stream().anyMatch(p->p.color().equals(org.bukkit.Color.FUCHSIA) && p.x()==2.5 && p.y()==64.6));
        assertTrue(dots.stream().anyMatch(p->p.color().equals(org.bukkit.Color.ORANGE) && p.x()>=5));
    }
    @Test void emptyDraftHasNoParticlesAndPartialRoomNeverCrashes() {
        var d=new DungeonDef("d","d",false,null,null,1,0,30,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
        assertTrue(WizardParticles.prepare(d).isEmpty());
        assertDoesNotThrow(()->WizardParticles.prepare(dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,
                List.of(new RoomDef("room",null,null,null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("s",null,3,List.of())))))));
    }
}

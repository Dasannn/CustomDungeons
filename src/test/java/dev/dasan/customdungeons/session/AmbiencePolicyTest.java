package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbiencePolicyTest {
    @Test void doorSamplesAreBoundedAndCoverTheRegion() {
        var region=Region.of("world",new BlockPos(-100,0,-100),new BlockPos(100,200,100));
        var points=AmbiencePolicy.samples(region,32);
        assertEquals(32,points.size());
        assertTrue(points.stream().allMatch(p->p.x()>=-100 && p.x()<101 && p.y()>=0 && p.y()<201 && p.z()>=-100 && p.z()<101));
        assertTrue(points.stream().mapToDouble(Point::x).max().orElseThrow()>90);
        assertTrue(points.stream().mapToDouble(Point::y).max().orElseThrow()>190);
        assertTrue(points.stream().mapToDouble(Point::z).max().orElseThrow()>90);
        assertEquals(0,AmbiencePolicy.samples(region,0).size());
        assertEquals(32,AmbiencePolicy.samples(region,Integer.MAX_VALUE).size());
    }
    @Test void doorSamplesCoverTheFaceRatherThanOnlyItsDiagonal() {
        var region=Region.of("world",new BlockPos(5,64,0),new BlockPos(5,70,12));
        var samples=AmbiencePolicy.samples(region,32);
        assertTrue(samples.stream().anyMatch(p->p.y()>69.5 && p.z()<5));
        assertTrue(samples.stream().allMatch(p->p.x()==5.5));
    }
    @Test void effectsNeverReplaceExistingAndOnlyRemoveTheirOwn() {
        var port=new Effects(); var owned=new AmbiencePolicy.OwnedEffects<String>(port);
        port.current.put("speed","player");
        owned.apply("speed","dungeon");
        assertEquals("player",port.current.get("speed"));
        owned.apply("darkness","dungeon");
        assertEquals("dungeon",port.current.get("darkness"));
        owned.clear();
        assertEquals(Map.of("speed","player"),port.current);
    }
    @Test void laterExternalEffectIncludingIdenticalOneIsPreserved() {
        var port=new Effects(); var owned=new AmbiencePolicy.OwnedEffects<String>(port);
        owned.apply("darkness","dungeon");
        owned.external("darkness");
        owned.clear();
        assertEquals("dungeon",port.current.get("darkness"));
        owned.apply("darkness","other");
        assertEquals("dungeon",port.current.get("darkness"));
    }
    @Test void failedAddIsNotOwnedAndChangedEffectIsPreserved() {
        var port=new Effects(); var owned=new AmbiencePolicy.OwnedEffects<String>(port);
        port.reject=true; owned.apply("darkness","dungeon"); port.reject=false;
        port.current.put("darkness","player");owned.clear();
        assertEquals("player",port.current.get("darkness"));
        port.current.clear();owned.apply("speed","dungeon");
        port.current.put("speed","player");owned.clear();
        assertEquals("player",port.current.get("speed"));
    }
    @Test void roomMusicStopsOnExitClearAndBossPriorityThenResumes() {
        var calls=new ArrayList<String>();
        var music=new AmbiencePolicy.Music(new AmbiencePolicy.MusicPort(){
            public void play(String key){calls.add("play:"+key);}
            public void stop(String key){calls.add("stop:"+key);}
        });
        music.update("room",false,0,40);music.update("room",false,10,40);
        music.update("room",true,20,40);music.update("room",false,30,40);
        music.update(null,false,31,40);
        assertEquals(List.of("play:room","stop:room","play:room","stop:room"),calls);
    }
    @Test void musicRepeatsOnlyAtConfiguredIntervalAndStopsPreviousTrack() {
        var calls=new ArrayList<String>();
        var music=new AmbiencePolicy.Music(new AmbiencePolicy.MusicPort(){
            public void play(String key){calls.add("play:"+key);}
            public void stop(String key){calls.add("stop:"+key);}
        });
        music.update("one",false,0,40);music.update("one",false,40,40);
        music.update("two",false,41,40);music.clear();
        assertEquals(List.of("play:one","play:one","stop:one","play:two","stop:two"),calls);
    }
    @Test void failedPlaybackNeverClaimsOrStopsASoundTheDungeonDidNotStart() {
        var stopped=new ArrayList<String>();
        var music=new AmbiencePolicy.Music(new AmbiencePolicy.MusicPort(){
            public void play(String key){throw new IllegalStateException("playback rejected");}
            public void stop(String key){stopped.add(key);}
        });
        assertThrows(IllegalStateException.class,()->music.update("shared",false,0,40));
        music.clear();assertTrue(stopped.isEmpty());
    }
    @Test void clearDoesNotStopUnstartedOrPreviouslyReplacedTracks() {
        var calls=new ArrayList<String>();
        var music=new AmbiencePolicy.Music(new AmbiencePolicy.MusicPort(){
            public void play(String key){calls.add("play:"+key);}
            public void stop(String key){calls.add("stop:"+key);}
        });
        music.clear();music.update("foreign",true,0,40);music.clear();assertTrue(calls.isEmpty());
        music.update("first",false,10,40);music.update("last",false,20,40);calls.clear();
        music.clear();music.clear();assertEquals(List.of("stop:last"),calls);
    }
    private static class Effects implements AmbiencePolicy.EffectPort<String> {
        final Map<String,String> current=new HashMap<>(); boolean reject;
        public String current(String key){return current.get(key);}
        public boolean add(String key,String value){if(reject)return false;current.put(key,value);return true;}
        public void remove(String key){current.remove(key);}
        public boolean same(String expected,String actual){return Objects.equals(expected,actual);}
    }
}

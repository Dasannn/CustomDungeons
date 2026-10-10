package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpawnScheduleTest {
 static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
 @Test void lateSpawnMustRetainTheAbilityIdentityOffsets() {
  var definitions=List.of(AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,1,0),
   new AbilityInstance("second",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,1,0,Map.of()));
  var early=new AbilityEngineTest.Fixture(definitions,List.of());var late=new AbilityEngineTest.Fixture(definitions,List.of());
  var earlyEngine=early.engine();var lateEngine=late.engine();
  for(int t=0;t<100;t++){
   early.clock.advance(t);earlyEngine.tick(List.of(early.mob),t);
   late.clock.advance(1000+t);lateEngine.tick(List.of(late.mob),1000+t);
  }
  assertEquals(2,early.calls.size());assertEquals(2,late.calls.size());
  for(int i=0;i<early.calls.size();i++) {
   String[] first=early.calls.get(i).split("@"), second=late.calls.get(i).split("@");
   assertEquals(first[0],second[0]);
   assertEquals(1000+Integer.parseInt(first[1]),Integer.parseInt(second[1]),"Offsets must be relative to appearance, including late hosts");
  }
 }
}

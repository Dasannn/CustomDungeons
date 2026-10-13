package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatAnglesTest {
    @Test void threeAlliesShareAnglesOnlyForSameHostAndTargetAndScanOncePerWindow() {
        var f=new CombatMoverTest.Fixture();var allies=new ArrayList<ActiveMob>();allies.add(f.mob);
        for(int i=1;i<3;i++) {
            var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(new UUID(0,i));when(entity.isValid()).thenReturn(true);
            when(entity.getTarget()).thenReturn(f.player);when(entity.getLocation()).thenReturn(f.origin);
            allies.add(new ActiveMob(entity,f.mob.template(),f.host));
        }
        when(f.host.mobs()).thenReturn(allies);clearInvocations(f.host);
        var angles=new CombatAngles();var values=new HashSet<Double>();
        for(var ally:allies)values.add(angles.angle(ally,ally.template().intelligence(),0));
        assertEquals(Set.of(0d,Math.PI*2/3,Math.PI*4/3),values);
        verify(f.host,times(4)).mobs(); // three cheap size checks, one membership capture
        var secondHost=mock(MobHost.class);var independent=new ActiveMob(f.entity,f.mob.template(),secondHost);
        when(secondHost.mobs()).thenReturn(List.of(independent));assertNull(angles.angle(independent,independent.template().intelligence(),0));
        when(allies.getLast().entity().getTarget()).thenReturn(mock(Player.class));
        assertNull(angles.angle(allies.getLast(),allies.getLast().template().intelligence(),5),"A cached angle belongs to the original target only");
        assertNull(angles.angle(allies.getLast(),allies.getLast().template().intelligence(),10));
        assertEquals(Math.PI,angles.angle(allies.get(1),allies.get(1).template().intelligence(),10));
        angles.invalidate(f.host);when(f.host.mobs()).thenReturn(List.of(f.mob));assertNull(angles.angle(f.mob,f.mob.template().intelligence(),10));
    }
    @Test void legacyLevelsDoNotEnumerateHostMembers() {
        var f=new CombatMoverTest.Fixture();clearInvocations(f.host);
        for(int level=0;level<4;level++)assertNull(new CombatAngles().angle(f.mob,IntelligenceDef.level(level),0));
        verifyNoInteractions(f.host);
    }
}

package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugViewTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @AfterEach void reset() {DebugView.clear();}
    @Test void aHundredThousandInactiveTicksDoNotTouchBukkitMobsMessagesOrEngine() {
        @SuppressWarnings("unchecked") var mobs=(Collection<ActiveMob>)mock(Collection.class);
        var engine=mock(AbilityEngine.class);var messages=mock(Messages.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            for(int i=0;i<100_000;i++)DebugView.tick(mobs,engine,messages,i);
            bukkit.verifyNoInteractions();verifyNoInteractions(mobs,engine,messages);
        }
    }
    private Player admin() {
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);
        when(p.hasPermission("customdungeons.admin.debug")).thenReturn(true);return p;
    }
    private ActiveMob mob() {
        var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);
        when(entity.hasAI()).thenReturn(true);when(entity.name()).thenReturn(Component.text("Custodio"));
        var data=mock(org.bukkit.persistence.PersistentDataContainer.class);when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.has(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(true);
        when(data.has(MobKeys.TEMPLATE,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(true);
        var template=new MobTemplate("debug","HUSK","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
        return new ActiveMob(entity,template,mock(MobHost.class));
    }
    @Test void enginePaintsOnlyTheLookedMobFromItsHostIncludingZeroIntelligence() {
        var messages=DebugFormatTest.messages("messages.yml");var platform=mock(MobsPlatform.class);
        when(platform.messages()).thenReturn(messages);when(platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(50,1,24));
        var first=mob();var second=mob();var engine=new AbilityEngine(new AbilityRegistry(),platform);
        var admin=admin();DebugView.toggle(admin);when(admin.getTargetEntity(24,false)).thenReturn(first.entity());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(admin.getUniqueId())).thenReturn(admin);
            engine.tick(List.of(second),0);verify(admin,never()).sendActionBar(any(Component.class));
            engine.tick(List.of(first),0);verify(admin).sendActionBar(any(Component.class));verify(admin,times(2)).getTargetEntity(24,false);
            clearInvocations(admin);engine.tick(List.of(first),1);verify(admin,never()).getTargetEntity(anyInt(),anyBoolean());
        }
    }
    @Test void revokedPermissionAndDisconnectPruneTheOnlyGlobalSet() {
        var admin=admin();DebugView.toggle(admin);when(admin.hasPermission("customdungeons.admin.debug")).thenReturn(false);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(admin.getUniqueId())).thenReturn(admin);
            DebugView.tick(List.of(),mock(AbilityEngine.class),mock(Messages.class),0);
            assertFalse(DebugView.active());verify(admin,never()).getTargetEntity(anyInt(),anyBoolean());
            when(admin.hasPermission("customdungeons.admin.debug")).thenReturn(true);DebugView.toggle(admin);
            bukkit.when(()->Bukkit.getPlayer(admin.getUniqueId())).thenReturn(null);
            DebugView.tick(List.of(),mock(AbilityEngine.class),mock(Messages.class),5);assertFalse(DebugView.active());
        }
    }
    @Test void unknownEntitiesDeadMobsAndAnEmptyRayClearThePreviousLine() {
        var admin=admin();DebugView.toggle(admin);var mob=mob();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(admin.getUniqueId())).thenReturn(admin);
            var vanilla=mock(Mob.class);when(vanilla.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(admin.getTargetEntity(24,false)).thenReturn(vanilla);
            DebugView.tick(List.of(mob),mock(AbilityEngine.class),mock(Messages.class),0);
            when(admin.getTargetEntity(24,false)).thenReturn(mob.entity());when(mob.entity().isDead()).thenReturn(true);
            DebugView.tick(List.of(mob),mock(AbilityEngine.class),mock(Messages.class),5);
            when(admin.getTargetEntity(24,false)).thenReturn(null);
            DebugView.tick(List.of(mob),mock(AbilityEngine.class),mock(Messages.class),10);
            verify(admin,times(3)).sendActionBar(Component.empty());
        }
    }
    @Test void snapshotDistinguishesCombatStunPauseAndAppliedControlWithTheirAbility() {
        var mob=mob();var engine=mock(AbilityEngine.class);var messages=DebugFormatTest.messages("messages.yml");
        try(var combat=mockStatic(CombatService.class);var controls=mockStatic(ControlService.class)) {
            combat.when(()->CombatService.pauseInfo(mob)).thenReturn(new CombatService.PauseInfo("charge",true));
            var state=DebugView.snapshot(mob,engine,messages,0);assertEquals("stunned",state.state());assertEquals("collision",state.cause());
            combat.when(()->CombatService.pauseInfo(mob)).thenReturn(new CombatService.PauseInfo("interruptible_ultimate",false));
            assertEquals("paused",DebugView.snapshot(mob,engine,messages,0).state());
            combat.when(()->CombatService.pauseInfo(mob)).thenReturn(null);
            controls.when(()->ControlService.controllingAbility(mob)).thenReturn("roots");
            assertEquals("controlling",DebugView.snapshot(mob,engine,messages,0).state());
        }
    }
}

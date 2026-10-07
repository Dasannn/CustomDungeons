package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import java.time.Duration;

/** One instance per session. All work runs from existing ticks and lifecycle events. */
final class SessionAmbience {
    private final Messages messages;
    private final AmbienceSettings defaults;
    private final PluginConfig config;
    private final Map<String,MobTemplate> templates;
    private final Map<UUID,Visitor> visitors=new HashMap<>();
    private final Set<Integer> activated=new HashSet<>(),cleared=new HashSet<>();
    private final Map<Integer,AmbienceSettings.Resolved> rooms=new HashMap<>();
    private final class Visitor {
        final Player player;
        final AmbienceEffects effects;
        final AmbiencePolicy.Music music;
        int room=-1;
        Visitor(Player p){
            player=p;effects=new AmbienceEffects(p);
            music=new AmbiencePolicy.Music(new AmbiencePolicy.MusicPort(){
                public void play(String key){p.playSound(p.getLocation(),key,SoundCategory.RECORDS,1,1);}
                // Music only calls this for this player's last successfully started dungeon track.
                // Minecraft cannot distinguish other origins sharing the same key/category.
                public void stop(String key){p.stopSound(key,SoundCategory.RECORDS);}
            });
        }
        void clear(){effects.clear();music.clear();}
    }
    SessionAmbience(Messages messages,AmbienceSettings defaults,PluginConfig config,Map<String,MobTemplate> templates) {
        this.messages=messages;this.defaults=defaults;this.config=config;this.templates=templates;
    }
    static boolean bossRoom(RoomDef room,Map<String,MobTemplate> templates) {
        return room.spawners().stream().flatMap(s->s.waves().stream()).flatMap(w->w.entries().stream())
                .map(e->templates.get(e.templateId())).anyMatch(m->m!=null && m.boss());
    }
    private AmbienceSettings.Resolved settings(DungeonSession session,int room) {
        return rooms.computeIfAbsent(room,i->defaults.resolve(session.def().rooms().get(i).ambience(),bossRoom(session.def().rooms().get(i),templates)));
    }
    void tick(DungeonSession session,boolean bossMusic) {
        if(session.state().state()!=SessionState.RUNNING)return;
        long tick=session.scheduler().currentTick();
        // Arbitration is cheap and immediate; region scans, potions and particles run at 2 Hz.
        for(Visitor visitor:visitors.values())if(bossMusic)visitor.music.clear();
        if(tick%10!=0)return;
        if(session.roomStarted())activated.add(session.roomIndex());
        for(Player player:session.players()) {
            if(!player.isOnline() || player.isDead() || player.getGameMode()==GameMode.SPECTATOR){remove(player);continue;}
            int room=-1;
            // Only activated rooms can announce entry or apply effects (T38).
            for(int index:activated)if(DungeonSessionRuntime.contains(session.def().rooms().get(index).region(),player.getLocation())){room=index;break;}
            if(room<0){remove(player);continue;}
            Visitor visitor=visitors.computeIfAbsent(player.getUniqueId(),id->new Visitor(player));
            var settings=settings(session,room);var definition=session.def().rooms().get(room);
            if(visitor.room!=room) {
                visitor.clear();visitor.room=room;
                title(player,settings.text("entry-title"),settings.text("entry-subtitle"),definition,settings.number("title-seconds"));
                sound(player,player.getLocation(),settings.text("entry-sound"),1,1);
            }
            for(PotionDef effect:settings.effects()) {
                var type=AmbienceEffects.type(effect.effectKey());
                if(type!=null)visitor.effects.apply(new PotionEffect(type,settings.number("effect-ticks"),effect.amplifier(),true,effect.particles(),false));
            }
            String music=cleared.contains(room)?null:settings.text("music");
            visitor.music.update(music,bossMusic,tick,music==null?2400:config.musicLengthTicks().getOrDefault(music,2400));
            if(tick%20==0 && !cleared.contains(room)) particles(player,definition.region(),settings.text("particle"),settings.number("density"));
        }
    }
    void moved(DungeonSession session,Player player,Location to) {
        Visitor visitor=visitors.get(player.getUniqueId());
        if(visitor!=null && (visitor.room<0 || !DungeonSessionRuntime.contains(session.def().rooms().get(visitor.room).region(),to)))remove(player);
    }
    void changed(Player player,EntityPotionEffectEvent event){var visitor=visitors.get(player.getUniqueId());if(visitor!=null)visitor.effects.changed(event);}
    void remove(Player player){var visitor=visitors.remove(player.getUniqueId());if(visitor!=null)visitor.clear();}
    void pauseMusic(Player player){var visitor=visitors.get(player.getUniqueId());if(visitor!=null)visitor.music.clear();}
    void clear(){for(Visitor visitor:visitors.values())visitor.clear();visitors.clear();}
    void cleared(DungeonSession session) {
        int index=session.roomIndex();if(!cleared.add(index))return;
        var room=session.def().rooms().get(index);var settings=settings(session,index);
        for(Visitor visitor:visitors.values())if(visitor.room==index)visitor.music.clear();
        for(Player player:session.players())if(near(player,room.region())) {
            sound(player,player.getLocation(),settings.text("clear-sound"),1,1);
            title(player,settings.text("clear-title"),"",room,settings.number("title-seconds"));
        }
    }
    void door(DungeonSession session,Region region,RoomDef room) {
        if(region==null)return;
        var settings=room==null?defaults.resolve(null,false):defaults.resolve(room.ambience(),bossRoom(room,templates));
        for(Player player:session.players())if(near(player,region)) {
            var at=centre(region,player.getWorld());
            sound(player,at,settings.text("door-sound"),1,.6f);
            sound(player,at,settings.text("door-rumble"),1,.6f);
            particles(player,region,settings.text("door-particle"),settings.number("door-density"));
            if(room!=null)title(player,settings.text("door-title"),"",room,settings.number("title-seconds"));
            if(settings.flag("door-shake") && settings.number("shake-ticks")>0) {
                // Uses the same ownership boundary; never replaces an existing nausea effect.
                Visitor visitor=visitors.computeIfAbsent(player.getUniqueId(),id->new Visitor(player));
                visitor.effects.apply(new PotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA,settings.number("shake-ticks"),0,true,false,false));
            }
        }
    }
    private boolean near(Player player,Region region) {
        if(!player.isOnline() || player.isDead() || player.getGameMode()==GameMode.SPECTATOR)return false;
        var at=player.getLocation();if(at==null || at.getWorld()==null || !at.getWorld().getName().equals(region.world()))return false;
        double dx=Math.max(Math.max(region.min().x()-at.getX(),0),at.getX()-region.max().x()-1);
        double dy=Math.max(Math.max(region.min().y()-at.getY(),0),at.getY()-region.max().y()-1);
        double dz=Math.max(Math.max(region.min().z()-at.getZ(),0),at.getZ()-region.max().z()-1);
        return dx*dx+dy*dy+dz*dz<=Math.pow(config.limits().effectViewRadius(),2);
    }
    private static Location centre(Region region,World world) {
        return new Location(world,((double)region.min().x()+region.max().x()+1)/2,
                ((double)region.min().y()+region.max().y()+1)/2,((double)region.min().z()+region.max().z()+1)/2);
    }
    private void particles(Player player,Region region,String keys,int density) {
        if(keys.isBlank())return;
        int count=(int)Math.clamp(density*Math.max(0,config.limits().particleDensity()),0,NumericRanges.ambience("density").max());
        var types=Arrays.stream(keys.split(",")).map(Particle::valueOf).toList();
        var samples=AmbiencePolicy.samples(region,count);
        for(int i=0;i<samples.size();i++) {
            var p=samples.get(i);var at=new Location(player.getWorld(),p.x(),p.y(),p.z());
            if(at.distanceSquared(player.getLocation())<=Math.pow(config.limits().effectViewRadius(),2))
                player.spawnParticle(types.get(i%types.size()),at,1,.15,.15,.15,.01);
        }
    }
    private static void sound(Player player,Location at,String key,float volume,float pitch){if(!key.isBlank())player.playSound(at,key,volume,pitch);}
    private Component caption(String value,RoomDef room) {
        if(value.startsWith("@"))return messages.get(value.substring(1),Placeholder.unparsed("room",room.id()));
        return messages.get("ambience.caption",Placeholder.component("value",Text.parse(value.replace("{room}",room.id()))));
    }
    private void title(Player player,String title,String subtitle,RoomDef room,int seconds) {
        if(title.isBlank() && subtitle.isBlank())return;
        player.showTitle(Title.title(caption(title,room),caption(subtitle,room),Title.Times.times(Duration.ofMillis(250),Duration.ofSeconds(seconds),Duration.ofMillis(500))));
    }
}

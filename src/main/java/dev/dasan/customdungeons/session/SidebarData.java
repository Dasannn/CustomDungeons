package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import dev.dasan.customdungeons.text.Text;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** Presentation snapshot from in-memory session state. No IO, scheduling or gameplay changes. */
final class SidebarData {
    record Snapshot(String state,Map<String,Component> values,Set<String> conditions) {
        Snapshot {values=Map.copyOf(values);conditions=Set.copyOf(conditions);}
    }
    static Snapshot capture(DungeonSession session,Player player,Messages messages,int plates,boolean keyHeld) {
        var def=session.def();var state=session.state().state();
        var values=new HashMap<String,Component>();var conditions=new HashSet<String>();
        values.put("dungeon",Component.text(PlainTextComponentSerializer.plainText().serialize(Text.parse(def.displayName()))));
        put(values,"players",state==SessionState.LOBBY?session.players().size():session.initialPlayers());
        put(values,"alive",session.players().size());put(values,"max_players",def.maxPlayers());put(values,"min_players",def.minPlayers());
        put(values,"kills",session.sidebarKills(player.getUniqueId()));put(values,"kills_total",session.sidebarKillsTotal());
        conditions.add(def.maxPlayers()>0?"has_player_limit":"no_player_limit");
        String selected;
        if(state==SessionState.LOBBY) {
            int countdown=session.countdownSeconds();
            if(countdown>=0) {values.put("countdown",Component.text(time(countdown)));conditions.add("countdown_running");}
            else if(def.startMode()==StartMode.AUTO)conditions.add("below_minimum");
            if(def.startMode()==StartMode.PLATES) {
                put(values,"plates",plates);put(values,"plates_total",def.plates().size());
                if(plates<def.plates().size())conditions.add("plates_incomplete");
                values.put("objective",messages.get("scoreboard.objective.plates",Placeholder.unparsed("plates",Integer.toString(plates)),
                        Placeholder.unparsed("total",Integer.toString(def.plates().size()))));
            }
            selected=def.startMode()==StartMode.PLATES?"lobby-placas":"lobby-automatico";
        } else if(state==SessionState.COMPLETED || state==SessionState.FAILED) {
            selected=state==SessionState.COMPLETED?"completada":"fallida";
            values.put("time_total",Component.text(time(session.elapsedTicks()/20)));
            int countdown=session.finishCountdownSeconds();
            if(countdown>0) {
                put(values,"finish_countdown",countdown);conditions.add("finish_tp_pending");
                values.put("objective",messages.get("scoreboard.objective.exiting",Placeholder.unparsed("countdown",time(countdown))));
            } else if(state==SessionState.COMPLETED && !def.exitPlates().isEmpty())values.put("objective",messages.get("scoreboard.objective.exit-plate"));
        } else {
            var room=def.rooms().get(session.roomIndex());
            put(values,"room",session.roomIndex()+1);put(values,"rooms",def.rooms().size());
            var local=session.mobs().stream().filter(m->m.entity().isValid() && !m.entity().isDead()
                    && DungeonSessionRuntime.contains(session.currentRoomRegion(),m.entity().getLocation())).toList();
            ActiveMob boss=local.stream().filter(m->m.template().boss()).findFirst().orElse(null);
            put(values,"mobs_left",local.size());conditions.add(local.isEmpty()?"no_mobs":"has_mobs");
            boolean waves=session.roomStarted() && room.spawners().size()==1 && !room.spawners().getFirst().waves().isEmpty();
            conditions.add(waves?"has_wave_summary":"no_wave_summary");
            if(waves) {put(values,"wave",session.waveNumber());put(values,"waves",room.spawners().getFirst().waves().size());}
            if(def.timeLimitSeconds()>0) {
                long seconds=Math.max(0,((long)def.timeLimitSeconds()*20-session.elapsedTicks()+19)/20);
                values.put("time_left",Component.text(time(seconds),seconds<60?NamedTextColor.RED:null));conditions.add("has_time_limit");
            }
            values.put("lives",hearts(messages,session.livesLeft(player.getUniqueId()),def.lives()));
            if(boss!=null) {
                var max=boss.entity().getAttribute(Attribute.MAX_HEALTH);
                double fraction=max==null || max.getValue()<=0?1:boss.entity().getHealth()/max.getValue();
                put(values,"boss_health",Math.clamp((int)Math.ceil(fraction*100),1,100));
                put(values,"boss_phase",Math.min(boss.template().phases().size()+1,boss.phaseIndex()+2));
                put(values,"boss_phases",boss.template().phases().size()+1);
                conditions.add(boss.template().phases().isEmpty()?"boss_without_phases":"boss_has_phases");
            }
            String objective;
            if(session.awaitingRoomEntry()) {objective="enter-room";conditions.add("waiting_room_entry");}
            else if(boss!=null)objective="boss";
            else if(session.roomStarted())objective="clear";
            else if(room.openingMode()!=RoomDef.OpeningMode.AUTOMATIC) {
                objective=keyHeld?"use-key":room.openingMode()==RoomDef.OpeningMode.EXTERNAL_KEY?"puzzle":"collect-key";
                conditions.add("waiting_key");
            } else {objective="next-room";conditions.add("door_open");}
            values.put("objective",messages.get("scoreboard.objective."+objective));
            selected=!session.roomStarted()?"entrada-sala":boss==null?"partida-sala":"partida-jefe";
        }
        return new Snapshot(selected,values,conditions);
    }
    static Component hearts(Messages messages,int lives,int maximum) {
        var full=messages.get("scoreboard.hearts-full");var empty=messages.get("scoreboard.hearts-empty");
        var result=Component.text();
        for(int i=0;i<maximum;i++)result.append(i<lives?full:empty);
        return result.build();
    }
    static String time(long seconds) {return seconds/60+":"+String.format(Locale.ROOT,"%02d",seconds%60);}
    private static void put(Map<String,Component> data,String key,int value) {data.put(key,Component.text(value));}
}

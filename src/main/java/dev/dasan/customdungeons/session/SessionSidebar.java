package dev.dasan.customdungeons.session;

import java.util.*;
import java.util.function.*;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/** Owns private boards and restores references only while ownership is retained. Main thread only. */
final class SessionSidebar {
    private static final class Display {
        final Player player;
        final Scoreboard previous,board;
        final Objective objective;
        final List<Score> rows=new ArrayList<>();
        ScoreboardTemplates.Frame frame;
        long nextRefresh;
        Display(Player player,Scoreboard board,ScoreboardTemplates.Frame frame,long nextRefresh) {
            this.player=player;this.previous=player.getScoreboard();this.board=board;this.nextRefresh=nextRefresh;
            objective=board.registerNewObjective("cd-session",Criteria.DUMMY,frame.title());
            objective.numberFormat(NumberFormat.blank());objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        void publish(ScoreboardTemplates.Frame next) {
            if(next.equals(frame))return;
            if(frame!=null && !frame.title().equals(next.title()))objective.displayName(next.title());
            for(int i=0;i<next.lines().size();i++) {
                boolean added=i>=rows.size();
                if(added)rows.add(objective.getScore("cd-row-"+i));
                if(added || frame==null || i>=frame.lines().size())rows.get(i).setScore(15-i);
                if(frame==null || i>=frame.lines().size() || !frame.lines().get(i).equals(next.lines().get(i)))rows.get(i).customName(next.lines().get(i));
            }
            if(frame!=null)for(int i=next.lines().size();i<frame.lines().size();i++)rows.get(i).resetScore();
            frame=next;
        }
    }
    private final Supplier<Scoreboard> newBoard;
    private final long refreshTicks;
    private final Consumer<Player> conflict;
    private final Map<UUID,Display> displays=new LinkedHashMap<>();
    SessionSidebar(Supplier<Scoreboard> newBoard,long refreshTicks,Consumer<Player> conflict) {
        this.newBoard=newBoard;this.refreshTicks=Math.max(20,refreshTicks);this.conflict=conflict;
    }
    void join(Player player,ScoreboardTemplates.Frame frame,long tick) {
        if(displays.containsKey(player.getUniqueId()))return;
        var display=new Display(player,newBoard.get(),frame,tick+refreshTicks);
        display.publish(frame);displays.put(player.getUniqueId(),display);player.setScoreboard(display.board);
    }
    void refresh(long tick,Function<Player,ScoreboardTemplates.Frame> render) {
        for(var entry:List.copyOf(displays.entrySet())) {
            var display=entry.getValue();
            if(display.player.getScoreboard()!=display.board) {
                displays.remove(entry.getKey());conflict.accept(display.player);continue;
            }
            if(tick<display.nextRefresh)continue;
            display.nextRefresh=tick+refreshTicks;
            display.publish(render.apply(display.player));
        }
    }
    void remove(UUID player) {
        var display=displays.remove(player);
        if(display!=null && display.player.getScoreboard()==display.board)display.player.setScoreboard(display.previous);
    }
    void clear() {for(UUID player:List.copyOf(displays.keySet()))remove(player);}
    int size() {return displays.size();}
}

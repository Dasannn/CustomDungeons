package dev.dasan.customdungeons.session;

import java.util.*;
import java.util.function.*;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/** Main-thread private boards. Presentation failures never escape into session gameplay. */
final class SessionSidebar {
    private static final class Display {
        final Player player;
        final Scoreboard previous,board;
        final Objective objective;
        final List<Score> rows=new ArrayList<>();
        Object inputs;
        ScoreboardTemplates.Frame frame;
        long nextRefresh;
        Display(Player player,Scoreboard board,ScoreboardTemplates.Frame frame,Object inputs,long nextRefresh) {
            this.player=player;this.previous=player.getScoreboard();this.board=board;this.inputs=inputs;this.nextRefresh=nextRefresh;
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
    private final Consumer<RuntimeException> failure;
    private final Map<UUID,Display> displays=new LinkedHashMap<>();
    private long nextRefresh=Long.MAX_VALUE;
    private boolean overflowWarned;
    SessionSidebar(Supplier<Scoreboard> newBoard,long refreshTicks,Consumer<Player> conflict) {
        this(newBoard,refreshTicks,conflict,error->java.util.logging.Logger.getLogger("CustomDungeons")
                .log(java.util.logging.Level.WARNING,"Session scoreboard failed",error));
    }
    SessionSidebar(Supplier<Scoreboard> newBoard,long refreshTicks,Consumer<Player> conflict,Consumer<RuntimeException> failure) {
        this.newBoard=newBoard;this.refreshTicks=Math.max(20,refreshTicks);this.conflict=conflict;this.failure=failure;
    }
    void join(Player player,ScoreboardTemplates.Frame frame,long tick) {join(player,tick,()->frame,Function.identity());}
    <T> void join(Player player,long tick,Supplier<T> inputs,Function<T,ScoreboardTemplates.Frame> render) {
        UUID uuid=null;
        try {
            uuid=player.getUniqueId();
            if(displays.containsKey(uuid))return;
            var data=inputs.get();var frame=bounded(render.apply(data));
            var display=new Display(player,newBoard.get(),frame,data,tick+refreshTicks);
            display.publish(frame);displays.put(uuid,display);
            nextRefresh=Math.min(nextRefresh,display.nextRefresh);player.setScoreboard(display.board);
        } catch(RuntimeException error) {report(error);if(uuid!=null)remove(uuid);}
    }
    void refresh(long tick,Function<Player,ScoreboardTemplates.Frame> render) {refresh(tick,render,Function.identity());}
    <T> void refresh(long tick,Function<Player,T> inputs,Function<T,ScoreboardTemplates.Frame> render) {
        // No copies, player API calls, captures or components between deadlines.
        if(tick<nextRefresh)return;
        try {refreshDue(tick,inputs,render);}
        catch(RuntimeException error) {report(error);clear();}
    }
    private <T> void refreshDue(long tick,Function<Player,T> inputs,Function<T,ScoreboardTemplates.Frame> render) {
        nextRefresh=Long.MAX_VALUE;
        var iterator=displays.entrySet().iterator();
        while(iterator.hasNext()) {
            var display=iterator.next().getValue();
            if(tick<display.nextRefresh) {nextRefresh=Math.min(nextRefresh,display.nextRefresh);continue;}
            display.nextRefresh=tick+refreshTicks;
            try {
                if(display.player.getScoreboard()!=display.board) {
                    iterator.remove();
                    try {conflict.accept(display.player);} catch(RuntimeException error) {report(error);}
                    continue;
                }
                var data=inputs.apply(display.player);
                if(!Objects.equals(display.inputs,data)) {
                    display.publish(bounded(render.apply(data)));display.inputs=data;
                }
                nextRefresh=Math.min(nextRefresh,display.nextRefresh);
            } catch(RuntimeException error) {iterator.remove();report(error);restore(display);}
        }
    }
    private ScoreboardTemplates.Frame bounded(ScoreboardTemplates.Frame frame) {
        if(frame.lines().size()>15 && !overflowWarned) {
            overflowWarned=true;report(new IllegalStateException("Sidebar truncated to 15 rows"));
        }
        return frame.limited();
    }
    /** World changes follow session membership, not position alone during lobby/combat. */
    void worldChanged(Player player,BooleanSupplier stillInSession) {
        UUID uuid=null;
        try {uuid=player.getUniqueId();if(!stillInSession.getAsBoolean())remove(uuid);}
        catch(RuntimeException error) {report(error);if(uuid!=null)remove(uuid);}
    }
    void remove(UUID player) {
        var display=displays.remove(player);
        if(display!=null)restore(display);
        if(displays.isEmpty())nextRefresh=Long.MAX_VALUE;
    }
    private void restore(Display display) {
        try {if(display.player.getScoreboard()==display.board)display.player.setScoreboard(display.previous);}
        catch(RuntimeException error) {report(error);}
    }
    private void report(RuntimeException error) {
        // Even a failing diagnostic sink must not interrupt joining, ticking or cleanup.
        try {failure.accept(error);} catch(RuntimeException ignored) {}
    }
    void clear() {for(UUID player:List.copyOf(displays.keySet()))remove(player);}
    int size() {return displays.size();}
}

package dev.dasan.customdungeons.session;

/** Pure countdown shared by minimum-player and pressure-plate readiness. */
final class LobbyCountdown {
    private final long duration;
    private long deadline=-1;
    LobbyCountdown(int seconds) { duration=Math.max(0,seconds)*20L; }
    boolean tick(long now,boolean ready) {
        if(!ready) {deadline=-1;return false;}
        if(deadline<0) deadline=now+duration;
        return now>=deadline;
    }
    int secondsLeft(long now) {return deadline<0?-1:(int)Math.max(0,(deadline-now+19)/20);}
}

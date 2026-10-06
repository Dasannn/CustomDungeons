package dev.dasan.customdungeons.session;

/** Pure exit deadlines in session ticks. */
final class ExitPolicy {
    private ExitPolicy() {}
    static boolean due(String mode,int grace,long elapsed,boolean timeout) { return timeout || elapsed >= (mode.equals("NONE")?300:mode.equals("DELAYED")?grace:0)*20L; }
}

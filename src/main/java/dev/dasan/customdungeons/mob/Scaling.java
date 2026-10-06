package dev.dasan.customdungeons.mob;

/** Difficulty arithmetic; the session separately enforces its live-mob limit. */
public final class Scaling {
    private Scaling() {}

    public static int count(int base, int players, int minPlayers, double extraPerPlayer) {
        return (int) Math.ceil(base * healthMultiplier(players, minPlayers, extraPerPlayer));
    }

    public static double healthMultiplier(int players, int minPlayers, double extraPerPlayer) {
        return 1 + extraPerPlayer * Math.max(0, players - minPlayers);
    }
}

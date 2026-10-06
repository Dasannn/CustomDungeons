package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.PhaseDef;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Text;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** Owned by the session/live-test ticker. Does not schedule Bukkit tasks. */
public final class BossController {
    private final MobFactory factory;
    private final Map<String, MobTemplate> templates;
    private final Map<UUID, State> states = new HashMap<>();

    private static final class State {
        final ActiveMob boss;
        BossBar bar;
        String music;
        long nextMusicTick;
        final Set<Player> barViewers = new HashSet<>();
        final Set<Player> musicListeners = new HashSet<>();
        State(ActiveMob boss) { this.boss = boss; }
    }

    public BossController(MobFactory factory, Map<String, MobTemplate> templates) {
        this.factory = factory;
        this.templates = templates;
    }

    /** Returns only the next phase in definition order, or -1 when it is not crossed. */
    public static int nextPhase(List<PhaseDef> phases, int current, double healthFraction) {
        int next = current + 1;
        return next >= 0 && next < phases.size() && healthFraction <= phases.get(next).healthThreshold()
                ? next : -1;
    }

    /** Caller must pass the post-damage health, not the health before Bukkit applies damage. */
    public void onDamaged(ActiveMob boss, long tick) {
        if (!boss.template().boss() || !alive(boss)) return;
        double fraction = healthFraction(boss);
        int next;
        // A large hit can cross several thresholds. Healing must not undo that crossing.
        while ((next = nextPhase(boss.template().phases(), boss.phaseIndex(), fraction)) != -1) {
            boss.phaseIndex(next);
            applyPhase(boss, boss.template().phases().get(next), tick);
        }
        barFor(boss).progress((float) healthFraction(boss));
    }

    private void applyPhase(ActiveMob boss, PhaseDef phase, long tick) {
        if (phase.replaceAbilities()) {
            boss.abilities().clear();
            boss.combos().clear();
        }
        boss.abilities().addAll(phase.abilities());
        boss.combos().addAll(phase.combos());
        factory.applyEquipment(boss.entity(), phase.equipment());
        factory.applyPotions(boss.entity(), phase.potions());
        var max = boss.entity().getAttribute(Attribute.MAX_HEALTH);
        // healPercent is a percentage of maximum health (e.g. 25 = 25%).
        if (max != null && phase.healPercent() > 0) {
            boss.entity().setHealth(Math.min(max.getValue(),
                    boss.entity().getHealth() + max.getValue() * phase.healPercent() / 100));
        }
        for (var summon : phase.summons()) {
            if (!templates.containsKey(summon.templateId())) {
                Bukkit.getLogger().warning("[CustomDungeons] Unknown boss summon template: " + summon.templateId());
                continue;
            }
            Runnable spawn = () -> {
                if (!alive(boss)) return;
                for (int i = 0; i < summon.count(); i++) {
                    boss.session().spawnMinion(summon.templateId(), boss.entity().getLocation(), boss);
                }
            };
            if (summon.delayTicks() > 0) boss.session().scheduler().runLater(summon.delayTicks(), spawn);
            else spawn.run();
        }
        if (phase.title() != null || phase.subtitle() != null) {
            Title title = Title.title(phase.title() == null ? Component.empty() : Text.parse(phase.title()),
                    phase.subtitle() == null ? Component.empty() : Text.parse(phase.subtitle()));
            for (Player player : boss.session().players()) player.showTitle(title);
        }
        if (phase.soundKey() != null && !phase.soundKey().isBlank()) {
            for (Player player : boss.session().players()) {
                player.playSound(boss.entity().getLocation(), phase.soundKey(), SoundCategory.HOSTILE, 1, 1);
            }
        }
        if (phase.musicKey() != null) changeMusic(state(boss), phase.musicKey(), tick);
        boss.invulnerableUntil(Math.max(boss.invulnerableUntil(), tick + Math.max(0, phase.invulnerableTicks())));
    }

    public BossBar barFor(ActiveMob boss) {
        State state = state(boss);
        if (state.bar == null) {
            BossBar.Color color;
            try { color = BossBar.Color.valueOf(boss.template().bossBarColor().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { color = BossBar.Color.PURPLE; }
            state.bar = BossBar.bossBar(Text.parse(boss.template().displayName()),
                    (float) healthFraction(boss), color, BossBar.Overlay.PROGRESS);
        }
        updateViewers(state);
        state.bar.progress((float) healthFraction(boss));
        return state.bar;
    }

    public void startMusic(ActiveMob boss) {
        if (!alive(boss)) return;
        String key = boss.template().musicKey();
        for (int i = 0; i <= boss.phaseIndex(); i++) {
            String phaseKey = boss.template().phases().get(i).musicKey();
            if (phaseKey != null) key = phaseKey;
        }
        changeMusic(state(boss), key, boss.session().scheduler().currentTick());
    }

    private void changeMusic(State state, String key, long tick) {
        stopMusic(state.boss);
        state.music = key == null || key.isBlank() ? null : key;
        if (state.music != null) {
            for (Player player : state.boss.session().players()) play(state, player);
            state.nextMusicTick = tick + factory.musicLengthTicks(state.music);
        }
    }

    private void play(State state, Player player) {
        player.playSound(player.getLocation(), state.music, SoundCategory.RECORDS, 1, 1);
        state.musicListeners.add(player);
    }

    public void stopMusic(ActiveMob boss) {
        State state = states.get(boss.entity().getUniqueId());
        if (state == null) return;
        if (state.music != null) {
            for (Player player : state.musicListeners) player.stopSound(state.music, SoundCategory.RECORDS);
        }
        state.musicListeners.clear();
        state.music = null;
    }

    /** Called from the existing owner ticker; also refreshes bars and removes dead bosses. */
    public void tickMusic(long tick) {
        for (State state : List.copyOf(states.values())) {
            if (!alive(state.boss)) {
                cleanup(state.boss);
                continue;
            }
            if (state.bar != null) {
                updateViewers(state);
                state.bar.progress((float) healthFraction(state.boss));
            }
            if (state.music == null) continue;
            Set<Player> players = new HashSet<>(state.boss.session().players());
            for (var it = state.musicListeners.iterator(); it.hasNext();) {
                Player player = it.next();
                if (!players.contains(player)) {
                    player.stopSound(state.music, SoundCategory.RECORDS);
                    it.remove();
                }
            }
            boolean repeat = tick >= state.nextMusicTick;
            for (Player player : players) {
                if (repeat || !state.musicListeners.contains(player)) play(state, player);
            }
            if (repeat) state.nextMusicTick = tick + factory.musicLengthTicks(state.music);
        }
    }

    public void cleanup(ActiveMob boss) {
        State state = states.get(boss.entity().getUniqueId());
        if (state == null) return;
        stopMusic(boss);
        if (state.bar != null) {
            for (Player player : state.barViewers) player.hideBossBar(state.bar);
            state.barViewers.clear();
        }
        states.remove(boss.entity().getUniqueId());
    }

    private State state(ActiveMob boss) {
        return states.computeIfAbsent(boss.entity().getUniqueId(), ignored -> new State(boss));
    }

    private void updateViewers(State state) {
        Set<Player> players = new HashSet<>(state.boss.session().players());
        for (var it = state.barViewers.iterator(); it.hasNext();) {
            Player player = it.next();
            if (!players.contains(player)) {
                player.hideBossBar(state.bar);
                it.remove();
            }
        }
        for (Player player : players) {
            if (state.barViewers.add(player)) player.showBossBar(state.bar);
        }
    }

    private static boolean alive(ActiveMob boss) {
        return boss.entity().isValid() && !boss.entity().isDead();
    }

    private static double healthFraction(ActiveMob boss) {
        var max = boss.entity().getAttribute(Attribute.MAX_HEALTH);
        return max == null || max.getValue() <= 0 ? 0
                : Math.clamp(boss.entity().getHealth() / max.getValue(), 0, 1);
    }
}

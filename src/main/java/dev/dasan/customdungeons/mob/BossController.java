package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.ability.EffectAudience;
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
import org.bukkit.Particle;
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
        boolean transitionGlowing;
        final Set<Player> barViewers = new HashSet<>();
        final Set<Player> musicListeners = new HashSet<>();
        State(ActiveMob boss) { this.boss = boss; }
    }

    private final java.util.function.Consumer<Player> beforeMusic;
    public BossController(MobFactory factory, Map<String, MobTemplate> templates) {
        this(factory,templates,player->{});
    }
    public BossController(MobFactory factory, Map<String, MobTemplate> templates, java.util.function.Consumer<Player> beforeMusic) {
        this.beforeMusic=beforeMusic;
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
        ensureBar(boss);
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
        factory.applyAttributes(boss.entity(),phase.attributes());
        // healPercent is a percentage of maximum health (e.g. 25 = 25%).
        if (phase.healPercent()>0) MobHealth.heal(boss.entity(),MobHealth.maximum(boss.entity())*(phase.healPercent()/100));
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
            for (Player player : listeners(boss)) player.showTitle(title);
        }
        if (phase.soundKey() != null && !phase.soundKey().isBlank()) {
            for (Player player : listeners(boss)) {
                player.playSound(boss.entity().getLocation(), phase.soundKey(), SoundCategory.HOSTILE, 1, 1);
            }
        }
        if (phase.musicKey() != null) changeMusic(state(boss), phase.musicKey(), tick);
        beginTransition(boss, tick, phase.invulnerableTicks());
    }

    void beginTransition(ActiveMob boss, long tick, int duration) {
        State state = state(boss);
        updateTransition(state, tick);
        boss.invulnerableUntil(Math.max(boss.invulnerableUntil(), tick + Math.max(0, duration)));
        updateTransition(state, tick);
    }

    private void updateTransition(State state, long tick) {
        boolean active = tick < state.boss.invulnerableUntil();
        if (active == state.transitionGlowing) return;
        state.transitionGlowing = active;
        state.boss.entity().setGlowing(active);
        if (!active) return;
        var at = state.boss.entity().getLocation();
        var limits = factory.performanceLimits();
        int count = Math.max(0, (int) Math.round(30 * limits.particleDensity()));
        for (Player player : listeners(state.boss)) {
            player.playSound(at, "minecraft:item.totem.use", SoundCategory.HOSTILE, 1, 1);
        }
        if (count > 0) {
            for (Player player : EffectAudience.viewers(state.boss.session(), at, limits.effectViewRadius())) {
                player.spawnParticle(Particle.TOTEM_OF_UNDYING, at, count, 0.5, 1, 0.5, 0.1);
            }
        }
    }

    private BossBar ensureBar(ActiveMob boss) {
        State state = state(boss);
        if (state.bar == null) {
            BossBar.Color color;
            try { color = BossBar.Color.valueOf(boss.template().bossBarColor().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { color = BossBar.Color.PURPLE; }
            state.bar = BossBar.bossBar(Text.parse(boss.template().displayName()),
                    (float) healthFraction(boss), color, BossBar.Overlay.PROGRESS);
        }
        return state.bar;
    }

    public BossBar barFor(ActiveMob boss) {
        BossBar bar=ensureBar(boss);
        updateViewers(state(boss));
        bar.progress((float) healthFraction(boss));
        return bar;
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
            for (Player player : listeners(state.boss)) play(state, player);
            state.nextMusicTick = tick + factory.musicLengthTicks(state.music);
        }
    }

    public boolean musicPlaying() { return states.values().stream().anyMatch(s->s.music!=null && alive(s.boss)); }

    private void play(State state, Player player) {
        beforeMusic.accept(player);
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

    /**
     * T09 must call this every tick from the existing session ticker, including when music
     * is disabled. Also expires transition glow at invulnerableUntil (exclusive), refreshes
     * bars and removes dead bosses. Live-test tickers must use the same call.
     */
    public void tickMusic(long tick) {
        for (State state : List.copyOf(states.values())) {
            if (!alive(state.boss)) {
                cleanup(state.boss);
                continue;
            }
            updateTransition(state, tick);
            if (state.bar != null) {
                updateViewers(state);
                state.bar.progress((float) healthFraction(state.boss));
            }
            if (state.music == null) continue;
            Set<Player> players = new HashSet<>(listeners(state.boss));
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
        if (state.transitionGlowing) {
            boss.entity().setGlowing(false);
            state.transitionGlowing = false;
        }
        if (state.bar != null) {
            for (Player player : state.barViewers) player.hideBossBar(state.bar);
            state.barViewers.clear();
        }
        states.remove(boss.entity().getUniqueId());
    }

    private State state(ActiveMob boss) {
        return states.computeIfAbsent(boss.entity().getUniqueId(), ignored -> new State(boss));
    }

    private java.util.Collection<Player> listeners(ActiveMob boss) {
        return boss.session().audience(boss.entity().getLocation());
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
        return MobHealth.fraction(boss.entity());
    }
}

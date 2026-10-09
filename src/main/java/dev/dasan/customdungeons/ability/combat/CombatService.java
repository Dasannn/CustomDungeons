package dev.dasan.customdungeons.ability.combat;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** Encounter-owned continuations: no per-mob Bukkit tasks, persistent player state or world scans. */
public final class CombatService implements Listener,AutoCloseable {
    public static final NamespacedKey AUXILIARY=MobsPlatform.key("combat_auxiliary");
    public static final NamespacedKey DECOY=MobsPlatform.key("decoy");
    private static final class PositiveEffects {
        private static final Set<PotionEffectType> TYPES=Set.of(PotionEffectType.SPEED,PotionEffectType.HASTE,
            PotionEffectType.STRENGTH,PotionEffectType.INSTANT_HEALTH,PotionEffectType.JUMP_BOOST,
            PotionEffectType.REGENERATION,PotionEffectType.RESISTANCE,PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING,PotionEffectType.INVISIBILITY,PotionEffectType.NIGHT_VISION,
            PotionEffectType.HEALTH_BOOST,PotionEffectType.ABSORPTION,PotionEffectType.SATURATION,
            PotionEffectType.LUCK,PotionEffectType.SLOW_FALLING,PotionEffectType.DOLPHINS_GRACE,
            PotionEffectType.CONDUIT_POWER);
    }
    private static CombatService current;
    private final MobsPlatform platform;
    private final Map<UUID,Set<Effect>> owners=new HashMap<>();
    private final Map<UUID,Effect> auxiliaries=new HashMap<>();
    private final Map<UUID,Set<Effect>> silenced=new HashMap<>();
    private final Map<UUID,Effect> locked=new HashMap<>();
    private final Map<UUID,Budget> budgets=new HashMap<>();
    private final Map<Player,Long> fullSafety=new WeakHashMap<>();
    private Effect damageEffect;
    private boolean reflecting,closed;
    private static final class Budget {long tick=Long.MIN_VALUE;int used;}
    private static final class Effect {
        final String id;final AbilityContext ctx;final Location origin;final Vector direction;
        final Player target;final Location targetAnchor;final List<Entity> entities=new ArrayList<>();
        final Set<UUID> hit=new HashSet<>();final Set<Player> muted=new HashSet<>();
        CombatRules.Interruption interruption;CombatRules.DamageBudget link;
        boolean active=true,begun,warningFailed,ownsAI,previousAI,wall;
        int warning,left,elapsed,stun;double length,travel,halfWidth,health;
        Location totem;
        Effect(String id,AbilityContext ctx,Player target,int warning) {
            this.id=id;this.ctx=ctx;this.target=target;this.warning=warning;left=warning;
            origin=ctx.caster().entity().getLocation().clone();targetAnchor=target.getLocation().clone();
            direction=target.getLocation().toVector().subtract(origin.toVector()).setY(0);
            if(direction.lengthSquared()==0)direction.setZ(1);direction.normalize();
        }
        UUID owner() {return ctx.caster().entity().getUniqueId();}
        Mob mob() {return ctx.caster().entity();}
        double n(String key) {return ctx.params().getDouble(key);}
        int i(String key) {return ctx.params().getInt(key);}
    }
    public CombatService(MobsPlatform platform) {this.platform=Objects.requireNonNull(platform);current=this;}
    public static void execute(String id,AbilityContext ctx,int warning) {
        if(current!=null&&!current.closed)current.create(id,ctx,warning);
    }
    public static void cleanup(ActiveMob mob) {if(current!=null)current.removeOwner(mob.entity().getUniqueId());}
    public static void releasePlayer(Player p) {if(current!=null)current.forget(p);}
    public static void reset() {if(current!=null)current.clear();}
    public static boolean paused(ActiveMob mob) {return current!=null&&current.locked.containsKey(mob.entity().getUniqueId());}
    public static boolean isDecoy(Entity entity) {return entity!=null&&entity.getPersistentDataContainer()!=null&&entity.getPersistentDataContainer().has(DECOY,PersistentDataType.BYTE);}
    /** Resolve native indirect attacks too. The PDC flag suffices, including untracked recovered entities. */
    public static boolean isDecoySource(Entity entity) {
        if(isDecoy(entity))return true;
        Entity source=entity instanceof Projectile shot&&shot.getShooter() instanceof Entity shooter?shooter:
                entity instanceof EvokerFangs fangs?fangs.getOwner():entity instanceof Vex vex?vex.getOwner():
                entity instanceof AreaEffectCloud cloud&&cloud.getSource() instanceof Entity owner?owner:null;
        return isDecoy(source);
    }
    public int activeCount() {return owners.values().stream().mapToInt(Set::size).sum();}
    public int auxiliaryCount() {return auxiliaries.size();}
    public int silenceCount() {return silenced.size();}
    private static boolean allowed(Effect e,Player p) {
        return e.ctx.session().players().contains(p)&&p.isOnline()&&p.isValid()&&!p.isDead()
                &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
                &&p.getWorld().equals(e.mob().getWorld());
    }
    private boolean valid(Effect e) {return e.active&&!closed&&e.mob().isValid()&&!e.mob().isDead();}
    private static boolean loaded(Location at) {return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static boolean inside(Effect e,Location at) {return e.ctx.session().area()==null||e.ctx.session().area().contains(at);}
    private List<Player> players(Effect e) {return e.ctx.session().players().stream().filter(p->allowed(e,p)).toList();}
    private boolean near(Effect e,Player p,Location center,double radius) {
        return allowed(e,p)&&p.getLocation().distanceSquared(center)<=radius*radius;
    }
    private void create(String id,AbilityContext ctx,int configured) {
        if(!ctx.caster().entity().isValid()||ctx.caster().entity().isDead()||paused(ctx.caster()))return;
        var selected=ctx.targets().stream().filter(Player.class::isInstance).map(Player.class::cast).toList();
        if(selected.isEmpty())return;
        var set=owners.computeIfAbsent(ctx.caster().entity().getUniqueId(),k->new LinkedHashSet<>());
        if(set.size()>=9||set.stream().anyMatch(e->e.id.equals(id)))return;
        int intrinsic=id.equals("interruptible_ultimate")?ctx.params().getInt("charge-ticks"):id.equals("blink_behind")?10:20;
        var e=new Effect(id,ctx,selected.getFirst(),Math.max(intrinsic,configured));
        if(!allowed(e,e.target)||!loaded(e.origin)) {if(set.isEmpty())owners.remove(e.owner());return;}
        set.add(e);
        try {
            if(id.equals("charge")) {
                e.halfWidth=Math.max(.5,e.mob().getWidth()/2+.3);e.length=e.n("length");
                e.length=clearLength(e,e.length);lock(e);
            }
            if(id.equals("interruptible_ultimate")) {
                e.interruption=new CombatRules.Interruption(MobHealth.maximum(e.mob()),e.n("interrupt-percent"));lock(e);
            }
            if(id.equals("pain_link"))e.link=new CombatRules.DamageBudget(e.n("cap"));
            for(Player p:EffectAudience.viewers(ctx.session(),e.origin,Effects.viewRadius()))
                p.sendActionBar(platform.messages().get("combat.notice."+id));
            Effects.sound(ctx.session(),e.origin,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,.7f);
            step(e);
        } catch(RuntimeException failure) {finish(e);throw failure;}
    }
    private void later(Effect e) {e.ctx.session().scheduler().runLater(1,()->step(e));}
    private void step(Effect e) {
        try {advance(e);}catch(RuntimeException failure){finish(e);throw failure;}
    }
    private void advance(Effect e) {
        if(!e.active)return;
        if(!valid(e)||!loaded(e.origin)){finish(e);return;}
        if(e.stun>0) {e.mob().setVelocity(new Vector());if(--e.stun==0)finish(e);else later(e);return;}
        if(Set.of("pain_link","buff_steal","blink_behind").contains(e.id)&&!allowed(e,e.target)){finish(e);return;}
        if(e.left>0) {
            if(e.left==e.warning||e.left%5==0)draw(e);
            if(e.interruption!=null&&e.left%5==0)progress(e);
            if(e.ownsAI) {
                e.mob().setVelocity(new Vector());
                if(e.mob().getLocation().distanceSquared(e.origin)>.01&&!e.mob().teleport(e.origin)){finish(e);return;}
            }
            e.left--;later(e);return;
        }
        if(!e.begun) {
            e.begun=true;if(e.warningFailed){finish(e);return;}
            begin(e);if(!e.active)return;
        }
        switch(e.id) {
            case "charge" -> charge(e);
            case "boss_totem" -> totem(e);
            case "decoys" -> decoys(e);
            case "silence" -> silence(e);
            case "pain_link" -> link(e);
            default -> {finish(e);return;}
        }
        if(e.active){e.elapsed++;later(e);}
    }
    private void begin(Effect e) {
        switch(e.id) {
            case "boss_totem" -> spawnTotem(e);
            case "decoys" -> spawnDecoys(e);
            case "interruptible_ultimate" -> {
                for(Player p:players(e))if(near(e,p,e.origin,e.n("radius")))damage(e,p,e.n("damage"),e.ctx.params().getBoolean("lethal"));
                finish(e);
            }
            case "purge" -> {for(Player p:players(e))if(near(e,p,e.origin,e.n("radius")))purge(e,p,Integer.MAX_VALUE);finish(e);}
            case "buff_steal" -> {if(near(e,e.target,e.targetAnchor,1))purge(e,e.target,e.i("count"));finish(e);}
            case "silence" -> {
                for(Player p:players(e))if(near(e,p,e.origin,e.n("radius"))) {
                    silenced.computeIfAbsent(p.getUniqueId(),k->new LinkedHashSet<>()).add(e);e.muted.add(p);
                }
            }
            case "blink_behind" -> {blink(e);finish(e);}
            default -> { }
        }
    }
    private void lock(Effect e) {
        e.previousAI=e.mob().hasAI();e.ownsAI=true;locked.put(e.owner(),e);
        e.mob().setAI(false);e.mob().setVelocity(new Vector());
    }
    private void stun(Effect e,int ticks) {
        if(ticks<=0){finish(e);return;}
        if(!e.ownsAI)lock(e);e.stun=ticks;e.left=0;
        for(Player p:EffectAudience.viewers(e.ctx.session(),e.mob().getLocation(),Effects.viewRadius()))
            p.sendActionBar(platform.messages().get("combat.stunned"));
    }
    private double clearLength(Effect e,double requested) {
        double last=0;
        for(double distance=0;distance<=requested;distance+=.25) {
            Location at=e.origin.clone().add(e.direction.clone().multiply(distance));
            if(!clearVolume(e,at)){e.wall=wall(e,at);return last;}last=distance;
        }
        return requested;
    }
    private boolean clearVolume(Effect e,Location at) {
        double half=Math.max(.3,e.mob().getWidth()/2),height=Math.max(1,e.mob().getHeight());
        if(!inside(e,at)||!SafeTerrain.insideBorder(at.getWorld(),at.getX(),at.getZ(),half))return false;
        for(int x=(int)Math.floor(at.getX()-half);x<=Math.floor(at.getX()+half);x++)
            for(int z=(int)Math.floor(at.getZ()-half);z<=Math.floor(at.getZ()+half);z++) {
                if(!at.getWorld().isChunkLoaded(x>>4,z>>4))return false;
                for(int y=at.getBlockY();y<Math.ceil(at.getY()+height);y++) {
                    if(y<at.getWorld().getMinHeight()||y>=at.getWorld().getMaxHeight())return false;
                    var block=at.getWorld().getBlockAt(x,y,z);
                    if(block.isSolid()||SafeTerrain.hazardous(block)||!block.isPassable())return false;
                }
            }
        return true;
    }
    private boolean wall(Effect e,Location at) {
        double half=Math.max(.3,e.mob().getWidth()/2),height=Math.max(1,e.mob().getHeight());
        for(int x=(int)Math.floor(at.getX()-half);x<=Math.floor(at.getX()+half);x++)
            for(int z=(int)Math.floor(at.getZ()-half);z<=Math.floor(at.getZ()+half);z++) {
                if(!at.getWorld().isChunkLoaded(x>>4,z>>4))return false;
                for(int y=at.getBlockY();y<Math.ceil(at.getY()+height);y++) {
                    if(y<at.getWorld().getMinHeight()||y>=at.getWorld().getMaxHeight())return false;
                    if(at.getWorld().getBlockAt(x,y,z).isSolid())return true;
                }
            }
        return false;
    }
    private void charge(Effect e) {
        double next=Math.min(e.length,e.travel+.6);
        Location at=e.origin.clone().add(e.direction.clone().multiply(next));
        // Recheck the whole swept volume; a new wall can only shorten the warned corridor.
        for(double distance=e.travel;distance<=next+.001;distance+=.1)
            if(!clearVolume(e,e.origin.clone().add(e.direction.clone().multiply(distance)))) {
                if(wall(e,e.origin.clone().add(e.direction.clone().multiply(distance))))stun(e,e.i("stun-ticks"));else finish(e);
                return;
            }
        if(!e.mob().teleport(at)){finish(e);return;}
        for(Player p:players(e)) {
            var pos=p.getLocation();double x=pos.getX()-e.origin.getX(),z=pos.getZ()-e.origin.getZ();
            double along=x*e.direction.getX()+z*e.direction.getZ();
            if(Math.abs(pos.getY()-at.getY())<=2&&along>=e.travel-.3&&along<=next+.3
                    &&CombatRules.corridor(x,z,e.direction.getX(),e.direction.getZ(),e.length,e.halfWidth)&&e.hit.add(p.getUniqueId())) {
                damage(e,p,e.n("damage"),false);FallProtection.shared().protect(p);
                p.setVelocity(e.direction.clone().multiply(.7).setY(.45));
            }
        }
        e.travel=next;
        if(next>=e.length) {
            if(e.wall)stun(e,e.i("stun-ticks"));else finish(e);
        }
    }
    private Optional<Location> safe(Effect e,double x,double z) {
        // Validate a conservative footprint around the block center, then keep the exact
        // requested X/Z: the extra block covers the maximum half-block shift on either axis.
        return SafeTerrain.validate(e.origin.getWorld(),(int)Math.floor(x),(int)Math.floor(z),Math.max(.6,e.mob().getWidth())+1,Math.max(1,e.mob().getHeight()))
                .map(at->{at.setX(x);at.setZ(z);return at;})
                .filter(at->inside(e,at)&&Math.abs(at.getY()-e.origin.getY())<=4);
    }
    private Optional<Location> radial(Effect e,double radius,double angle) {
        return safe(e,e.origin.getX()+Math.cos(angle)*radius,e.origin.getZ()+Math.sin(angle)*radius);
    }
    private void mark(Entity entity,Effect e,boolean decoy) {
        OwnedEntities.mark(entity,e.ctx.caster());
        entity.getPersistentDataContainer().set(AUXILIARY,PersistentDataType.BYTE,(byte)1);
        if(decoy)entity.getPersistentDataContainer().set(DECOY,PersistentDataType.BYTE,(byte)1);
        entity.setPersistent(false);auxiliaries.put(entity.getUniqueId(),e);e.entities.add(entity);
    }
    private int entityCount(Effect e) {
        return owners.getOrDefault(e.owner(),Set.of()).stream().mapToInt(effect->effect.entities.size()).sum();
    }
    private void spawnTotem(Effect e) {
        for(int attempt=0;attempt<12;attempt++) {
            var at=radial(e,3+attempt%4,attempt*Math.PI/6);if(at.isEmpty())continue;
            e.totem=at.get();break;
        }
        if(e.totem==null||entityCount(e)+2>Math.min(16,platform.limits().maxAliveMobsPerSession())){finish(e);return;}
        e.health=e.n("health");
        e.totem.getWorld().spawn(e.totem,ItemDisplay.class,display->{mark(display,e,false);display.setItemStack(new ItemStack(Material.TOTEM_OF_UNDYING));});
        e.totem.getWorld().spawn(e.totem,Interaction.class,interaction->{mark(interaction,e,false);interaction.setInteractionWidth(1);interaction.setInteractionHeight(2);interaction.setResponsive(true);});
    }
    private void totem(Effect e) {
        if(e.elapsed>=e.i("ticks")||e.entities.stream().anyMatch(entity->!entity.isValid())){finish(e);return;}
        if(e.elapsed%20==0&&!e.ctx.params().getBoolean("protect"))MobHealth.heal(e.mob(),MobHealth.maximum(e.mob())*e.n("heal-percent")/100);
        if(e.elapsed%5==0)line(e,e.totem,e.mob().getLocation(),Particle.HAPPY_VILLAGER);
    }
    private void spawnDecoys(Effect e) {
        int count=Math.min(e.i("count"),Math.min(16-entityCount(e),platform.limits().maxAliveMobsPerSession()-e.ctx.session().mobs().size()-entityCount(e)));
        if(count<=0){finish(e);return;}
        List<Location> points=new ArrayList<>();
        for(int attempt=0;attempt<20&&points.size()<count+1;attempt++) {
            var at=radial(e,3+attempt%4,attempt*2.399963229728653);
            if(at.isPresent()&&points.stream().noneMatch(p->p.distanceSquared(at.get())<4))points.add(at.get());
        }
        if(points.size()<count+1){finish(e);return;}
        Collections.shuffle(points);
        // Unspawned public-API copy preserves variants, equipment, scale and the current phase appearance.
        for(int index=0;index<count;index++) {
            Mob copy=(Mob)e.mob().copy();mark(copy,e,true);copy.getPersistentDataContainer().remove(MobKeys.TEMPLATE);
            copy.getPersistentDataContainer().remove(MobKeys.VIRTUAL_ATTACK_DAMAGE);
            MobHealth.configure(copy,e.n("health"),false);
            var damage=copy.getAttribute(Attribute.ATTACK_DAMAGE);if(damage!=null)damage.setBaseValue(0);
            copy.setRemoveWhenFarAway(false);
            var reinforcements=copy.getAttribute(Attribute.SPAWN_REINFORCEMENTS);if(reinforcements!=null)reinforcements.setBaseValue(0);
            copy.setLootTable(null);
            if(copy.getEquipment()!=null)for(var slot:org.bukkit.inventory.EquipmentSlot.values())copy.getEquipment().setDropChance(slot,0);
            if(!copy.spawnAt(points.get(index),CreatureSpawnEvent.SpawnReason.CUSTOM)){finish(e);return;}
            copy.setTarget(e.target);
        }
        if(!e.mob().teleport(points.getLast()))finish(e);
    }
    private void decoys(Effect e) {
        if(e.elapsed>=e.i("ticks")){finish(e);return;}
        for(Entity entity:List.copyOf(e.entities))if(!entity.isValid()||entity.isDead()){auxiliaries.remove(entity.getUniqueId());e.entities.remove(entity);}
        if(e.entities.isEmpty())finish(e);
    }
    private void purge(Effect e,Player p,int count) {
        if(!allowed(e,p))return;
        // Explicit beneficial types only. Control-owned effects are never stripped or transferred.
        var effects=p.getActivePotionEffects().stream().filter(effect->PositiveEffects.TYPES.contains(effect.getType())&&!ControlService.ownsPotion(p,effect))
                .sorted(Comparator.comparing(effect->effect.getType().getKey().toString())).limit(count).toList();
        for(var effect:effects) {
            p.removePotionEffect(effect.getType());
            if(e.id.equals("buff_steal"))e.mob().addPotionEffect(new PotionEffect(effect.getType(),
                    CombatRules.buffTicks(effect.getDuration(),e.i("max-ticks")),effect.getAmplifier(),effect.isAmbient(),effect.hasParticles(),effect.hasIcon()));
        }
    }
    private void silence(Effect e) {
        if(e.elapsed>=e.i("ticks")){finish(e);return;}
        for(Player p:List.copyOf(e.muted))if(!allowed(e,p))unmute(e,p);
        if(e.muted.isEmpty())finish(e);
    }
    private void link(Effect e) {
        if(e.elapsed>=e.i("ticks")||!near(e,e.target,e.mob().getLocation(),e.n("distance"))||e.link.remaining()<=0){finish(e);return;}
        if(e.elapsed%5==0)line(e,e.mob().getLocation(),e.target.getLocation().add(0,1,0),Particle.SOUL);
    }
    private void blink(Effect e) {
        if(!allowed(e,e.target))return;
        var direction=e.target.getLocation().getDirection().setY(0);
        if(direction.lengthSquared()==0)return;direction.normalize();
        var target=e.target.getLocation();
        var desired=target.clone().subtract(direction.multiply(e.n("distance")));
        Optional<Location> at=Optional.empty();
        // Search only floors within two blocks of the target, including under ceilings.
        for(int offset:new int[]{0,-1,1,-2,2}) {
            at=SafeTerrain.validateAt(target.getWorld(),desired.getBlockX(),target.getBlockY()+offset,desired.getBlockZ(),
                    Math.max(.6,e.mob().getWidth())+1,Math.max(1,e.mob().getHeight()))
                    .map(point->{point.setX(desired.getX());point.setZ(desired.getZ());return point;})
                    .filter(point->inside(e,point)&&Math.abs(point.getY()-target.getY())<=2);
            if(at.isPresent())break;
        }
        if(at.isEmpty())return;
        var point=at.get();point.setDirection(e.target.getLocation().toVector().subtract(point.toVector()));
        double reach=Math.max(2.5,e.mob().getWidth()*2);
        if(e.mob().teleport(point)&&near(e,e.target,e.mob().getLocation(),reach)) {
            double amount=attackDamage(e.mob())*(1+e.n("bonus")/100);
            damage(e,e.target,amount,false);
        }
    }
    private static double attackDamage(Mob mob) {
        var virtual=mob.getPersistentDataContainer().get(MobKeys.VIRTUAL_ATTACK_DAMAGE,PersistentDataType.DOUBLE);
        var attribute=mob.getAttribute(Attribute.ATTACK_DAMAGE);
        return virtual!=null?virtual:attribute==null?0:attribute.getValue();
    }
    private void damage(Effect e,Player p,double amount,boolean lethal) {
        if(!allowed(e,p)||amount<=0)return;
        var previous=damageEffect;damageEffect=e;
        if(!lethal)guard(e,p);
        try {Effects.damage(p,amount,e.ctx.caster());}finally{damageEffect=previous;}
    }
    private void guard(Effect e,Player p) {
        var attribute=p.getAttribute(Attribute.MAX_HEALTH);double max=attribute==null?20:attribute.getValue();
        long now=Bukkit.getCurrentTick();
        if(p.getHealth()>=max&&!Objects.equals(fullSafety.get(p),now)) {
            fullSafety.put(p,now);e.ctx.session().scheduler().runLater(1,()->fullSafety.remove(p,now));
        }
    }
    private void progress(Effect e) {
        for(Player p:EffectAudience.viewers(e.ctx.session(),e.origin,Effects.viewRadius()))
            p.sendActionBar(platform.messages().get("combat.interrupt-progress",
                    Placeholder.unparsed("damage",String.format(Locale.ROOT,"%.0f",e.interruption.progress())),
                    Placeholder.unparsed("required",String.format(Locale.ROOT,"%.0f",e.interruption.required()))));
    }
    private boolean marks(Effect e,List<Location> marks,Particle particle) {
        var budget=budgets.computeIfAbsent(e.owner(),k->new Budget());long tick=e.ctx.session().scheduler().currentTick();
        if(budget.tick!=tick){budget.tick=tick;budget.used=0;}
        int maximum=dev.dasan.customdungeons.ability.zone.ZoneRules.particleBudget(platform.limits().particleDensity());
        if(marks.size()>maximum-budget.used)return false;
        budget.used+=marks.size();
        for(var at:marks)for(Player p:EffectAudience.viewers(e.ctx.session(),at,Effects.viewRadius()))p.spawnParticle(particle,at,1,0,0,0,0);
        return true;
    }
    private void draw(Effect e) {
        var points=new ArrayList<Location>();
        if(e.id.equals("charge")) {
            // Every damaged center lies inside these fixed parallel borders and end caps.
            for(double distance=0;distance<=e.length;distance+=.5)for(int side:List.of(-1,1))
                points.add(e.origin.clone().add(e.direction.clone().multiply(distance)).add(e.direction.getZ()*e.halfWidth*side,.1,-e.direction.getX()*e.halfWidth*side));
        } else {
            double radius=switch(e.id) {case "purge","silence","interruptible_ultimate"->e.n("radius");default->1;};
            var center=Set.of("buff_steal","blink_behind","pain_link").contains(e.id)?e.target.getLocation():e.origin;
            for(int index=0;index<16;index++) {
                double angle=2*Math.PI*index/16;points.add(center.clone().add(Math.cos(angle)*radius,.1,Math.sin(angle)*radius));
            }
        }
        if(!marks(e,points,e.id.equals("blink_behind")?Particle.PORTAL:Particle.ENCHANT))e.warningFailed=true;
    }
    private void line(Effect e,Location from,Location to,Particle particle) {
        if(!from.getWorld().equals(to.getWorld()))return;
        var points=new ArrayList<Location>();var delta=to.toVector().subtract(from.toVector());
        for(int index=0;index<=8;index++)points.add(from.clone().add(delta.clone().multiply(index/8.0)));
        marks(e,points,particle);
    }
    private void unmute(Effect e,Player p) {
        var effects=silenced.get(p.getUniqueId());if(effects!=null){effects.remove(e);if(effects.isEmpty())silenced.remove(p.getUniqueId());}e.muted.remove(p);
    }
    private void finish(Effect e) {
        if(!e.active)return;e.active=false;
        if(e.ownsAI) {locked.remove(e.owner(),e);e.mob().setAI(e.previousAI);e.ownsAI=false;}
        for(Player p:List.copyOf(e.muted))unmute(e,p);
        for(Entity entity:List.copyOf(e.entities)){auxiliaries.remove(entity.getUniqueId());entity.remove();}e.entities.clear();
        var set=owners.get(e.owner());if(set!=null){set.remove(e);if(set.isEmpty()){owners.remove(e.owner());budgets.remove(e.owner());}}
    }
    private void removeOwner(UUID owner) {for(Effect e:List.copyOf(owners.getOrDefault(owner,Set.of())))finish(e);}
    private void forget(Player p) {
        fullSafety.remove(p);
        for(Effect e:List.copyOf(silenced.getOrDefault(p.getUniqueId(),Set.of())))unmute(e,p);
        for(var set:List.copyOf(owners.values()))for(Effect e:List.copyOf(set))
            if(e.target.equals(p)&&Set.of("pain_link","buff_steal","blink_behind").contains(e.id))finish(e);
    }
    private void clear() {for(UUID id:List.copyOf(owners.keySet()))removeOwner(id);silenced.clear();fullSafety.clear();budgets.clear();}
    @Override public void close() {clear();closed=true;if(current==this)current=null;}
    private static boolean remnant(Entity entity) {return entity.getPersistentDataContainer().has(AUXILIARY,PersistentDataType.BYTE)||isDecoy(entity);}
    public void recoverLoaded(Collection<World> worlds) {
        for(World world:worlds)for(Chunk chunk:world.getLoadedChunks())for(Entity entity:chunk.getEntities())if(remnant(entity))entity.remove();
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {for(Entity entity:event.getEntities())if(remnant(entity))entity.remove();}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent event) {
        if(isDecoy(event.getEntity())){event.getDrops().clear();event.setDroppedExp(0);}
        removeOwner(event.getEntity().getUniqueId());
    }
    @EventHandler public void removed(EntityRemoveFromWorldEvent event) {
        removeOwner(event.getEntity().getUniqueId());var e=auxiliaries.remove(event.getEntity().getUniqueId());
        if(e!=null) {e.entities.remove(event.getEntity());if(e.id.equals("boss_totem"))finish(e);}
    }
    @EventHandler public void quit(PlayerQuitEvent event) {forget(event.getPlayer());}
    @EventHandler public void playerDeath(PlayerDeathEvent event) {forget(event.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent event) {forget(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) {
        // Distance breaking is checked on the encounter ticker; teleporting never extends a link.
        if(!silenced.isEmpty()&&event.getTo()!=null&&!event.getTo().getWorld().equals(event.getFrom().getWorld()))forget(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent event) {
        if(!silenced.isEmpty()&&silenced.containsKey(event.getPlayer().getUniqueId()))event.setCancelled(true);
    }
    private static boolean forbidden(ItemStack item) {
        if(item==null)return false;var type=item.getType();
        return type.isEdible()||type==Material.POTION||type==Material.SPLASH_POTION||type==Material.LINGERING_POTION||type==Material.ENDER_PEARL;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void use(PlayerInteractEvent event) {
        if(silenced.isEmpty()||!silenced.containsKey(event.getPlayer().getUniqueId())||!event.getAction().isRightClick()||!forbidden(event.getItem()))return;
        event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void launch(ProjectileLaunchEvent event) {
        if(isDecoySource(event.getEntity())){event.setCancelled(true);return;}
        if(!(event.getEntity().getShooter() instanceof Entity shooter))return;
        if(shooter instanceof Player p&&!silenced.isEmpty()&&silenced.containsKey(p.getUniqueId())
                &&(event.getEntity() instanceof ThrownPotion||event.getEntity() instanceof EnderPearl)){event.setCancelled(true);return;}
    }
    // One common listener enforces melee-only behavior in every host, even without an owner map entry.
    @EventHandler(priority=EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent event) {if(isDecoySource(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent event) {
        if(isDecoySource(event.getEntity())){event.blockList().clear();event.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void changeBlock(EntityChangeBlockEvent event) {if(isDecoySource(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void shoot(EntityShootBowEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void spell(EntitySpellCastEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void splash(PotionSplashEvent event) {
        if(isDecoySource(event.getPotion())) {
            for(var entity:event.getAffectedEntities())event.setIntensity(entity,0);
            event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void lingering(LingeringPotionSplashEvent event) {if(isDecoySource(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cloud(AreaEffectCloudApplyEvent event) {if(isDecoySource(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void potion(EntityPotionEffectEvent event) {if(isDecoy(event.getEntity())||isDecoySource(event.getSource()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void combust(EntityCombustByEntityEvent event) {if(isDecoySource(event.getCombuster()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void nativeSpawn(EntitySpawnEvent event) {
        Entity entity=event.getEntity();
        Entity source=entity instanceof Vex vex?vex.getOwner():entity instanceof EvokerFangs fangs?fangs.getOwner():
                entity instanceof AreaEffectCloud cloud&&cloud.getSource() instanceof Entity owner?owner:null;
        if(!isDecoy(source))return;
        // A copy's innate summons must never turn into another wave/minion or carry rewards.
        var e=auxiliaries.get(source.getUniqueId());
        if(entity instanceof Mob) {
            // Some host listeners also see cancelled spawns; keep their minion registry excluded.
            entity.getPersistentDataContainer().set(DECOY,PersistentDataType.BYTE,(byte)1);
            if(e!=null)OwnedEntities.mark(entity,e.ctx.caster());
            entity.setPersistent(false);event.setCancelled(true);return;
        }
        event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void totemAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        var e=auxiliaries.get(event.getAttacked().getUniqueId());if(e==null||!e.id.equals("boss_totem"))return;
        // Interaction is not natively damageable; validate its virtual hitbox ourselves.
        event.setCancelled(true);var p=event.getPlayer();if(!allowed(e,p)||e.totem==null)return;
        var reach=p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);var eye=p.getEyeLocation();
        if(!CombatRules.totemInReach(eye.getX()-e.totem.getX(),eye.getY()-e.totem.getY(),eye.getZ()-e.totem.getZ(),reach==null?3:reach.getValue()))return;
        var attribute=p.getAttribute(Attribute.ATTACK_DAMAGE);
        breakTotem(e,p,attribute==null?1:attribute.getValue()*p.getAttackCooldown());
    }
    private void breakTotem(Effect e,Player p,double damage) {
        if(!valid(e)||!allowed(e,p)||e.totem==null||!near(e,p,e.totem,48))return;
        e.health-=Math.max(0,damage);if(e.health<=0)finish(e);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void auxiliaryDamage(EntityDamageByEntityEvent event) {
        var e=auxiliaries.get(event.getEntity().getUniqueId());
        if(e!=null&&e.id.equals("boss_totem")) {
            event.setCancelled(true);var source=attacker(event.getDamager());if(source!=null)breakTotem(e,source,event.getFinalDamage());
        }
        Entity source=event.getDamager();if(!isDecoySource(source))return;
        if(!isDecoy(source)||source instanceof Projectile||source instanceof EvokerFangs
                ||(event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK&&event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            event.setCancelled(true);return;
        }
        var owner=auxiliaries.get(source.getUniqueId());
        if(owner==null||!valid(owner)||owner.n("damage-percent")<=0||!(event.getEntity() instanceof Player p)||!allowed(owner,p)){event.setCancelled(true);return;}
        double base=attackDamage(owner.mob());
        if(owner.mob().getAttribute(Attribute.ATTACK_DAMAGE)==null)base=owner.ctx.caster().template().attributes().values().getOrDefault("damage",owner.ctx.caster().template().damage());
        event.setDamage(base*owner.n("damage-percent")/100);guard(owner,p);cap(event,p);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protect(EntityDamageEvent event) {
        for(Effect e:owners.getOrDefault(event.getEntity().getUniqueId(),Set.of()))if(e.begun&&e.id.equals("boss_totem")&&e.ctx.params().getBoolean("protect"))
            event.setDamage(event.getDamage()*(1-e.n("reduction")/100));
        if(event.getEntity() instanceof Player p&&damageEffect!=null)cap(event,p);
    }
    private void cap(EntityDamageEvent event,Player p) {
        if(Objects.equals(fullSafety.get(p),(long)Bukkit.getCurrentTick())) {
            double maximum=Math.max(0,p.getHealth()-1);
            if(event.getFinalDamage()>maximum&&event.getFinalDamage()>0)event.setDamage(event.getDamage()*maximum/event.getFinalDamage());
        }
    }
    private static Player attacker(Entity source) {
        return source instanceof Player p?p:source instanceof Projectile shot&&shot.getShooter() instanceof Player p?p:null;
    }
    @EventHandler public void applied(MobDamageAppliedEvent event) {
        if(reflecting||isDecoy(event.entity())||event.damage().isCancelled()||event.amount()<=0||event.damage().getCause()==EntityDamageEvent.DamageCause.KILL)return;
        for(Effect e:List.copyOf(owners.getOrDefault(event.entity().getUniqueId(),Set.of()))) {
            if(e.id.equals("interruptible_ultimate")&&!e.begun&&e.stun==0&&event.damage() instanceof EntityDamageByEntityEvent hit) {
                var p=attacker(hit.getDamager());if(p!=null&&allowed(e,p)&&e.interruption.add(event.amount())) {progress(e);stun(e,40);}
            }
            if(e.id.equals("pain_link")&&e.begun&&valid(e)&&near(e,e.target,e.mob().getLocation(),e.n("distance"))) {
                double amount=e.link.take(event.amount(),e.n("percent"));if(amount<=0)continue;
                reflecting=true;var previous=damageEffect;damageEffect=e;guard(e,e.target);
                // Unattributed reflected damage cannot trigger the boss's ON_HIT or a reflected boss credit.
                try {e.target.damage(amount);}finally{damageEffect=previous;reflecting=false;}
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent event) {
        if(!isDecoy(event.getEntity()))return;var e=auxiliaries.get(event.getEntity().getUniqueId());
        if(e==null||!(event.getTarget() instanceof Player p)||!allowed(e,p))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void split(SlimeSplitEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void transform(EntityTransformEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
}

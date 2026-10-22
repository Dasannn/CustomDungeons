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
    private static final Set<String> TARGET_BOUND=Set.of("pain_link","buff_steal","blink_behind");
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
        final Map<UUID,MobCapacity.Reservation> slots=new HashMap<>();
        final Set<UUID> hit=new HashSet<>();final Set<Player> muted=new HashSet<>();
        final Map<org.bukkit.block.Block,List<org.bukkit.util.BoundingBox>> terrainShapes=new HashMap<>();
        CombatRules.Interruption interruption;CombatRules.DamageBudget link;
        boolean active=true,begun,warningFailed,ownsAI,previousAI,wall;
        int warning,left,elapsed,stun;double length,travel,halfWidth,health,floorY;
        Location totem;
        List<Location> chargeWarning,warningOutline;
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
        if(current!=null&&!current.closed)current.create(id,ctx,warning);else AbilityCasts.cancel(ctx,false);
    }
    public static void cleanup(ActiveMob mob) {if(current!=null)current.removeOwner(mob.entity().getUniqueId());}
    public static void releasePlayer(Player p) {if(current!=null)current.forget(p);}
    public static void reset() {if(current!=null)current.clear();}
    public static boolean pausedByOtherCast(AbilityContext context) {
        var effect=current==null?null:current.locked.get(context.caster().entity().getUniqueId());
        return effect!=null&&effect.ctx!=context;
    }
    public static boolean paused(ActiveMob mob) {return current!=null&&current.locked.containsKey(mob.entity().getUniqueId());}
    public record PauseInfo(String abilityId,boolean stunned) {}
    public static PauseInfo pauseInfo(ActiveMob mob) {
        var effect=current==null?null:current.locked.get(mob.entity().getUniqueId());
        return effect==null?null:new PauseInfo(effect.id,effect.stun>0);
    }
    public static boolean canStart(String id,ActiveMob mob) {
        if(current==null||current.closed)return false;
        var set=current.owners.get(mob.entity().getUniqueId());if(set==null)return true;
        if(set.size()>=9)return false;
        for(var effect:set)if(effect.id.equals(id))return false;
        return true;
    }
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
        return !e.ctx.session().excludesTarget(p)&&e.ctx.session().players().contains(p)&&p.isOnline()&&p.isValid()&&!p.isDead()
                &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
                &&p.getWorld().equals(e.mob().getWorld());
    }
    private boolean valid(Effect e) {return e.active&&!closed&&e.mob().isValid()&&!e.mob().isDead();}
    private static boolean loaded(Location at) {return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static boolean inside(Effect e,Location at) {return e.ctx.session().area()==null||e.ctx.session().area().contains(at);}
    private List<Player> players(Effect e) {return e.ctx.session().players().stream().filter(p->allowed(e,p)).toList();}
    private boolean near(Effect e,Player p,Location center,double radius) {
        return allowed(e,p)&&inside(e,p.getLocation())&&p.getLocation().distanceSquared(center)<=radius*radius;
    }
    public static int warningTicks(String id,ParamValues params,int configured) {
        int intrinsic=id.equals("interruptible_ultimate")?params.getInt("charge-ticks"):id.equals("blink_behind")?10:20;
        return Math.max(intrinsic,configured);
    }
    private void create(String id,AbilityContext ctx,int configured) {
        if(!ctx.caster().entity().isValid()||ctx.caster().entity().isDead()||paused(ctx.caster())){AbilityCasts.cancel(ctx,true);return;}
        var selected=ctx.targets().stream().filter(Player.class::isInstance).map(Player.class::cast).toList();
        if(selected.isEmpty()){AbilityCasts.cancel(ctx,false);return;}
        var set=owners.computeIfAbsent(ctx.caster().entity().getUniqueId(),k->new LinkedHashSet<>());
        if(set.size()>=9||set.stream().anyMatch(e->e.id.equals(id))){AbilityCasts.cancel(ctx,true);return;}
        var e=new Effect(id,ctx,selected.getFirst(),warningTicks(id,ctx.params(),configured));
        if(!allowed(e,e.target)||!loaded(e.origin)) {if(set.isEmpty())owners.remove(e.owner());AbilityCasts.cancel(ctx,false);return;}
        set.add(e);
        try {
            if(id.equals("charge")) {
                e.halfWidth=Math.max(.5,e.mob().getWidth()/2+.3);e.length=e.n("length");
                e.origin.setDirection(e.direction);
                e.length=clearLength(e,e.length);
                if(e.length<=0&&!e.wall){finish(e);return;}
                lock(e);
            }
            if(id.equals("interruptible_ultimate")) {
                e.interruption=new CombatRules.Interruption(MobHealth.maximum(e.mob()),e.n("interrupt-percent"));lock(e);
            }
            if(id.equals("pain_link"))e.link=new CombatRules.DamageBudget(e.n("cap"));
            var notice=platform.messages().get("combat.notice."+id);
            for(Player p:EffectAudience.viewers(ctx.session(),e.origin,Effects.viewRadius()))p.sendActionBar(notice);
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
        if(TARGET_BOUND.contains(e.id)&&!allowed(e,e.target)){finish(e);return;}
        // A host may return an escaped mob while its cast owns the pause. Its
        // old warning origin must not undo that return or relocate the warned hit.
        var area=e.ctx.session().area();
        if(e.ownsAI&&area!=null&&!area.contains(e.origin)){finish(e);return;}
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
            if(!AbilityCasts.canBegin(e.ctx)){finish(e);return;}
            e.begun=true;if(e.warningFailed){finish(e);return;}
            AbilityCasts.started(e.ctx);
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
                for(Player p:players(e))if(near(e,p,e.origin,e.n("radius"))&&withinWarning(e,p))damage(e,p,e.n("damage"),e.ctx.params().getBoolean("lethal"));
                finish(e);
            }
            case "purge" -> {for(Player p:players(e))if(near(e,p,e.origin,e.n("radius"))&&withinWarning(e,p))purge(e,p,Integer.MAX_VALUE);finish(e);}
            case "buff_steal" -> {if(near(e,e.target,e.targetAnchor,1)&&withinWarning(e,e.target))purge(e,e.target,e.i("count"));finish(e);}
            case "silence" -> {
                for(Player p:players(e))if(near(e,p,e.origin,e.n("radius"))&&withinWarning(e,p)) {
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
        var notice=platform.messages().get("combat.stunned");
        for(Player p:EffectAudience.viewers(e.ctx.session(),e.mob().getLocation(),Effects.viewRadius()))p.sendActionBar(notice);
    }
    private record Obstruction(double distance,boolean wall) {}
    private double clearLength(Effect e,double requested) {
        e.terrainShapes.clear();
        var hit=sweep(e,e.origin,requested);
        var floor=SafeTerrain.supportFloor(e.origin,e.origin.getY(),e.mob().getWidth(),e.ctx.session().area(),e.terrainShapes);
        if(floor.isEmpty()||e.origin.getY()-floor.getAsDouble()>1.5){e.wall=false;return 0;}
        e.floorY=floor.getAsDouble();
        double limit=requested;
        if(Double.isFinite(hit.distance())) {
            e.wall=hit.wall();
            limit=Math.max(0,Math.floor((hit.distance()-1e-6)/.25)*.25);
        }
        double supported=supportedLength(e,0,limit);
        if(supported<limit)e.wall=false;
        return supported;
    }
    private double supportedLength(Effect e,double from,double to) {
        double previous=from;
        for(double distance=from+.25;distance<to+.25;distance+=.25) {
            double step=Math.min(distance,to);
            var at=e.origin.clone().add(e.direction.getX()*step,0,e.direction.getZ()*step);
            var floor=SafeTerrain.supportFloor(at,e.floorY,e.mob().getWidth(),e.ctx.session().area(),e.terrainShapes);
            if(floor.isEmpty()||e.floorY-floor.getAsDouble()>1+1e-7)return previous;
            previous=step;if(step>=to)break;
        }
        return to;
    }
    private org.bukkit.util.BoundingBox nextBody(Effect e,Location at) {
        var body=e.mob().getBoundingBox();
        if(body!=null) {
            var from=e.mob().getLocation();body=body.clone().shift(at.getX()-from.getX(),at.getY()-from.getY(),at.getZ()-from.getZ());
            return body.expand(0,-1e-6,0,0,0,0);
        }
        double half=e.mob().getWidth()/2;
        return new org.bukkit.util.BoundingBox(at.getX()-half,at.getY()+1e-6,at.getZ()-half,
                at.getX()+half,at.getY()+e.mob().getHeight(),at.getZ()+half);
    }
    private boolean chargeBounds(Effect e,Location at,double half) {
        return inside(e,at)&&SafeTerrain.insideBorder(at.getWorld(),at.getX(),at.getZ(),half);
    }
    /** Scan each candidate voxel once, with exact continuous overlap rather than repeated sampled bodies.
     * Every advance reads new Paper shapes, including the protruding voxel below the feet. */
    private Obstruction sweep(Effect e,Location from,double distance) {
        if(distance<=0)return new Obstruction(Double.POSITIVE_INFINITY,false);
        var body=nextBody(e,from);var world=from.getWorld();
        double dx=e.direction.getX(),dz=e.direction.getZ(),half=Math.max(body.getWidthX(),body.getWidthZ())/2;
        double first=Double.POSITIVE_INFINITY;boolean wall=false;
        var end=from.clone().add(dx*distance,0,dz*distance);
        if(!chargeBounds(e,from,half))return new Obstruction(0,false);
        if(!chargeBounds(e,end,half)) {
            for(double d=Math.min(.1,distance);d<=distance+.001;d+=.1)
                if(!chargeBounds(e,from.clone().add(dx*d,0,dz*d),half)){first=d;break;}
            if(!Double.isFinite(first))first=distance;
        }
        int minHeight=world.getMinHeight(),maxHeight=world.getMaxHeight();
        if(body.getMinY()<minHeight||body.getMaxY()>maxHeight)return new Obstruction(0,false);
        int minX=(int)Math.floor(Math.min(body.getMinX(),body.getMinX()+dx*distance));
        int maxX=(int)Math.floor(Math.max(body.getMaxX(),body.getMaxX()+dx*distance));
        int minZ=(int)Math.floor(Math.min(body.getMinZ(),body.getMinZ()+dz*distance));
        int maxZ=(int)Math.floor(Math.max(body.getMaxZ(),body.getMaxZ()+dz*distance));
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            var column=new org.bukkit.util.BoundingBox(x,body.getMinY(),z,x+1,body.getMaxY(),z+1);
            double entry=ChargeSweep.entry(body,dx,dz,column,distance);
            if(!Double.isFinite(entry)||entry>first)continue;
            if(!world.isChunkLoaded(x>>4,z>>4)) {if(entry<first){first=entry;wall=false;}continue;}
            for(int y=Math.max(minHeight,(int)Math.floor(body.getMinY())-1);y<Math.min(maxHeight,Math.ceil(body.getMaxY()));y++) {
                var block=world.getBlockAt(x,y,z);
                if(block.isEmpty())continue; // Fresh air cannot have a collision shape.
                if(SafeTerrain.hazardous(block)) {
                    double hazard=ChargeSweep.entry(body,dx,dz,new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1),distance);
                    if(hazard<first){first=hazard;wall=false;}
                }
                for(var local:SafeTerrain.collisionBoxes(block,e.terrainShapes)) {
                    double hit=ChargeSweep.entry(body,dx,dz,local,x,y,z,distance);
                    if(Double.isFinite(hit)&&hit<=first){first=hit;wall=true;}
                }
            }
        }
        return new Obstruction(first,wall);
    }
    private void charge(Effect e) {
        e.terrainShapes.clear();
        double next=Math.min(e.length,e.travel+.6);
        var from=e.origin.clone().add(e.direction.getX()*e.travel,0,e.direction.getZ()*e.travel);
        var hit=sweep(e,from,next-e.travel);
        if(Double.isFinite(hit.distance())) {if(hit.wall())stun(e,e.i("stun-ticks"));else finish(e);return;}
        double supported=supportedLength(e,e.travel,next);
        if(supported<next){e.length=supported;e.wall=false;next=supported;}
        if(next<=e.travel){finish(e);return;}
        Location at=e.origin.clone().add(e.direction.getX()*next,0,e.direction.getZ()*next);
        if(!e.mob().teleport(at)){finish(e);return;}
        e.mob().setBodyYaw(at.getYaw());
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
            // A warned wall may have been removed during the warning or the charge.
            var endHit=e.wall?sweep(e,at,.251):new Obstruction(Double.POSITIVE_INFINITY,false);
            if(endHit.wall()&&Double.isFinite(endHit.distance()))stun(e,e.i("stun-ticks"));else finish(e);
        }
    }
    private Optional<Location> safe(Effect e,double x,double z) {
        // Validate a conservative footprint around the block center, then keep the exact
        // requested X/Z: the extra block covers the maximum half-block shift on either axis.
        return SafeTerrain.interior(e.origin.getWorld(),(int)Math.floor(x),(int)Math.floor(z),e.origin.getBlockY(),Math.max(.6,e.mob().getWidth())+1,Math.max(1,e.mob().getHeight()),e.ctx.session().area())
                .map(at->{at.setX(x);at.setZ(z);return at;})
                .filter(at->SafeTerrain.fitsArea(at,Math.max(.6,e.mob().getWidth()),Math.max(1,e.mob().getHeight()),e.ctx.session().area())&&Math.abs(at.getY()-e.origin.getY())<=4
                        &&SafeTerrain.hasWalkableExit(at,Math.max(.6,e.mob().getWidth()),Math.max(1,e.mob().getHeight()),e.ctx.session().area(),e.origin.getY()));
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
        var slots=new ArrayDeque<MobCapacity.Reservation>();
        int requested=Math.min(e.i("count"),16-entityCount(e));
        for(int i=0;i<requested;i++) {
            var slot=e.ctx.session().reserveMobSlot();if(slot==null)break;slots.addLast(slot);
        }
        int count=slots.size();
        if(count==0){finish(e);return;}
        try {
            List<Location> points=new ArrayList<>();
            for(int attempt=0;attempt<20&&points.size()<count+1;attempt++) {
                var at=radial(e,3+attempt%4,attempt*2.399963229728653);
                if(at.isPresent()&&points.stream().noneMatch(p->p.distanceSquared(at.get())<4))points.add(at.get());
            }
            if(points.size()<count+1){finish(e);return;}
            Collections.shuffle(points);
            // Unspawned public-API copy preserves variants, equipment, scale and the current phase appearance.
            for(int index=0;index<count;index++) {
                Mob copy=(Mob)e.mob().copy();mark(copy,e,true);
                e.slots.put(copy.getUniqueId(),slots.removeFirst());
                copy.getPersistentDataContainer().remove(MobKeys.TEMPLATE);
                MobFactory.inheritSpeed(e.ctx.caster(),copy);
                copy.getPersistentDataContainer().remove(MobKeys.VIRTUAL_ATTACK_DAMAGE);
                MobHealth.configure(copy,e.n("health"),false);
                var damage=copy.getAttribute(Attribute.ATTACK_DAMAGE);if(damage!=null)damage.setBaseValue(attackDamage(e.mob())*e.n("damage-percent")/100);
                copy.setAI(true);
                copy.setRemoveWhenFarAway(false);
                var reinforcements=copy.getAttribute(Attribute.SPAWN_REINFORCEMENTS);if(reinforcements!=null)reinforcements.setBaseValue(0);
                copy.setLootTable(null);
                if(copy.getEquipment()!=null)for(var slot:org.bukkit.inventory.EquipmentSlot.values())copy.getEquipment().setDropChance(slot,0);
                if(!copy.spawnAt(points.get(index),CreatureSpawnEvent.SpawnReason.CUSTOM)){finish(e);return;}
                copy.setTarget(inside(e,e.target.getLocation())?e.target:null);
                // Replace ranged/native goals with public Paper pursuit and our single melee pulse.
                var goals=Bukkit.getMobGoals();
                if(goals!=null)goals.removeAllGoals(copy);
            }
            if(!MobMovement.paused(e.ctx.caster())&&!e.mob().teleport(points.getLast()))finish(e);
        } finally {slots.forEach(MobCapacity.Reservation::close);}
    }
    private void releaseSlot(Effect e,Entity entity) {
        var slot=e.slots.remove(entity.getUniqueId());if(slot!=null)slot.close();
    }
    private void decoys(Effect e) {
        if(e.elapsed>=e.i("ticks")){finish(e);return;}
        for(Entity entity:List.copyOf(e.entities))if(!entity.isValid()||entity.isDead()){clearAuxiliarySpeed(entity);auxiliaries.remove(entity.getUniqueId());e.entities.remove(entity);releaseSlot(e,entity);}
        if(e.entities.isEmpty()){finish(e);return;}
        if(e.elapsed%5==0)for(Entity entity:e.entities) {
            Mob copy=(Mob)entity;
            MobFactory.refreshSpeed(copy);
            Player target=players(e).stream().filter(p->inside(e,p.getLocation())).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(copy.getLocation()))).orElse(null);
            copy.setTarget(target);
            if(target==null)continue;
            copy.lookAt(target);
            var path=copy.getPathfinder();if(path!=null)path.moveTo(target,1);
            if(e.elapsed%20==0&&target.getBoundingBox().clone().expand(.6).overlaps(copy.getBoundingBox()))MobCombatListener.abilityDamage(target,attackDamage(e.mob())*e.n("damage-percent")/100,copy);
        }
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
                    .filter(point->SafeTerrain.fitsArea(point,Math.max(.6,e.mob().getWidth()),Math.max(1,e.mob().getHeight()),e.ctx.session().area())&&Math.abs(point.getY()-target.getY())<=2
                            &&SafeTerrain.hasWalkableExit(point,Math.max(.6,e.mob().getWidth()),Math.max(1,e.mob().getHeight()),e.ctx.session().area(),target.getY()));
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
        if(!allowed(e,p)||!inside(e,p.getLocation())||amount<=0)return;
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
        if(marks.isEmpty())return false;
        var budget=budgets.computeIfAbsent(e.owner(),k->new Budget());long tick=e.ctx.session().scheduler().currentTick();
        if(budget.tick!=tick){budget.tick=tick;budget.used=0;}
        int maximum=dev.dasan.customdungeons.ability.zone.ZoneRules.particleBudget(platform.limits().particleDensity());
        if(marks.size()>maximum-budget.used)return false;
        budget.used+=marks.size();
        Effects.marksShared(e.ctx.session(),marks,particle);
        return true;
    }
    private boolean withinWarning(Effect e,Player player) {
        var at=player.getLocation();return e.warningOutline!=null&&AreaOutline.contains(e.warningOutline,at.getX(),at.getZ());
    }
    private void draw(Effect e) {
        var points=new ArrayList<Location>();
        if(e.id.equals("charge")) {
            if(e.chargeWarning!=null) {
                e.warningOutline=e.chargeWarning;
                if(!marks(e,e.chargeWarning,Particle.ENCHANT))e.warningFailed=true;
                return;
            }
            // Every damaged center lies inside these fixed parallel borders and end caps.
            for(int side:List.of(-1,1)) {
                var border=new ArrayList<Location>();
                for(double distance=0;distance<e.length;distance+=.5)
                    border.add(e.origin.clone().add(e.direction.clone().multiply(distance)).add(e.direction.getZ()*e.halfWidth*side,.1,-e.direction.getX()*e.halfWidth*side));
                border.add(e.origin.clone().add(e.direction.clone().multiply(e.length)).add(e.direction.getZ()*e.halfWidth*side,.1,-e.direction.getX()*e.halfWidth*side));
                if(side==1)Collections.reverse(border);
                points.addAll(border);
            }
            // Only fixed warning geometry is reused. Collision shapes are read afresh on every advance.
            e.chargeWarning=AreaOutline.clip(e.ctx.session().area(),points);
        } else {
            double radius=switch(e.id) {case "purge","silence","interruptible_ultimate"->e.n("radius");default->1;};
            var center=e.id.equals("buff_steal")?e.targetAnchor:TARGET_BOUND.contains(e.id)?e.target.getLocation():e.origin;
            for(int index=0;index<16;index++) {
                double angle=2*Math.PI*index/16;points.add(center.clone().add(Math.cos(angle)*radius,.1,Math.sin(angle)*radius));
            }
        }
        e.warningOutline=AreaOutline.clip(e.ctx.session().area(),points);
        if(!marks(e,e.warningOutline,e.id.equals("blink_behind")?Particle.PORTAL:Particle.ENCHANT))e.warningFailed=true;
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
        AbilityCasts.cancel(e.ctx,false);
        if(e.ownsAI) {locked.remove(e.owner(),e);e.mob().setAI(e.previousAI);e.ownsAI=false;dev.dasan.customdungeons.mob.MobFactory.refreshSpeed(e.ctx.caster());}
        for(Player p:List.copyOf(e.muted))unmute(e,p);
        for(Entity entity:List.copyOf(e.entities)) {
            try {clearAuxiliarySpeed(entity);auxiliaries.remove(entity.getUniqueId());entity.remove();}
            finally {releaseSlot(e,entity);}
        }
        e.entities.clear();
        var set=owners.get(e.owner());if(set!=null){set.remove(e);if(set.isEmpty()){owners.remove(e.owner());budgets.remove(e.owner());}}
    }
    private void removeOwner(UUID owner) {for(Effect e:List.copyOf(owners.getOrDefault(owner,Set.of())))finish(e);}
    private void forget(Player p) {
        fullSafety.remove(p);
        for(Effect e:List.copyOf(silenced.getOrDefault(p.getUniqueId(),Set.of())))unmute(e,p);
        for(var set:List.copyOf(owners.values()))for(Effect e:List.copyOf(set))
            if(e.target.equals(p)&&TARGET_BOUND.contains(e.id))finish(e);
    }
    private void clear() {for(UUID id:List.copyOf(owners.keySet()))removeOwner(id);silenced.clear();fullSafety.clear();budgets.clear();}
    @Override public void close() {clear();closed=true;if(current==this)current=null;}
    private static boolean remnant(Entity entity) {return entity.getPersistentDataContainer().has(AUXILIARY,PersistentDataType.BYTE)||isDecoy(entity);}
    private static void clearAuxiliarySpeed(Entity entity) {if(entity instanceof Mob mob)MobFactory.clearSpeed(mob);}
    public void recoverLoaded(Collection<World> worlds) {
        for(World world:worlds)for(Chunk chunk:world.getLoadedChunks())for(Entity entity:chunk.getEntities())if(remnant(entity)){clearAuxiliarySpeed(entity);entity.remove();}
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {for(Entity entity:event.getEntities())if(remnant(entity)){clearAuxiliarySpeed(entity);entity.remove();}}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent event) {
        if(isDecoy(event.getEntity())) {
            clearAuxiliarySpeed(event.getEntity());event.getDrops().clear();event.setDroppedExp(0);
            var e=auxiliaries.get(event.getEntity().getUniqueId());if(e!=null)releaseSlot(e,event.getEntity());
        }
        removeOwner(event.getEntity().getUniqueId());
    }
    @EventHandler public void removed(EntityRemoveFromWorldEvent event) {
        removeOwner(event.getEntity().getUniqueId());var e=auxiliaries.remove(event.getEntity().getUniqueId());
        if(e!=null) {clearAuxiliarySpeed(event.getEntity());e.entities.remove(event.getEntity());releaseSlot(e,event.getEntity());if(e.id.equals("boss_totem"))finish(e);}
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
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void movedByTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        if(event.getEntity() instanceof Mob mob&&event.getTo()!=null)MobMovement.teleported(mob,event.getFrom(),event.getTo());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void facing(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if(!(event.getEntity() instanceof Mob mob))return;
        var to=event.getTo().clone();if(MobMovement.face(mob,event.getFrom(),to))event.setTo(to);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void impulse(EntityKnockbackEvent event) {
        if(event.getEntity() instanceof Mob mob)MobMovement.impulse(mob,
                event instanceof org.bukkit.event.entity.EntityKnockbackByEntityEvent hit&&event.getForce()>1e-6
                        &&event.getKnockback().clone().setY(0).lengthSquared()>1e-6?hit.getSourceEntity():null,event.getFinalKnockback());
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
        if(owner==null||!valid(owner)||owner.n("damage-percent")<=0||!(event.getEntity() instanceof Player p)||!allowed(owner,p)||!inside(owner,p.getLocation())){event.setCancelled(true);return;}
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
        if(e==null||!(event.getTarget() instanceof Player p)||!allowed(e,p)||!inside(e,p.getLocation()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void split(SlimeSplitEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void transform(EntityTransformEvent event) {if(isDecoy(event.getEntity()))event.setCancelled(true);}
}

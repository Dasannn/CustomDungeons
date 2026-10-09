package dev.dasan.customdungeons.ability.control;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

/** One owner for warnings, controls and auxiliaries. All queued work runs in MobHost.scheduler. */
public final class ControlService implements Listener,AutoCloseable {
    public static final NamespacedKey AUXILIARY=MobsPlatform.key("control_auxiliary");
    private static final NamespacedKey IMMOBILE=MobsPlatform.key("control_immobile");
    private static final NamespacedKey MINION_OWNER=MobsPlatform.key("tactical_owner");
    private static ControlService current;
    private final MobsPlatform platform;
    private final LongSupplier clock;
    private final ControlLedger<Effect> controls=new ControlLedger<>();
    private final Map<UUID,Set<Effect>> owners=new HashMap<>();
    private final Map<UUID,Effect> auxiliaries=new HashMap<>();
    private final Set<UUID> releasing=new HashSet<>();
    // Bomb safety is UUID-only and lasts just through this tick, including concurrent damage.
    private final Map<UUID,Long> bombSafety=new HashMap<>();
    private boolean closed;
    static final class Effect {
        final String id;final AbilityContext ctx;final List<Player> players;
        final List<Entity> entities=new ArrayList<>();
        final ControlRules.Escape escape;
        Location anchor;long ends;boolean active=true,controlling,mounted,moving;Entity vehicle;
        double remaining;int hits;PotionEffect levitation;long potionApplied;boolean applyingPotion;
        BoundingBox flightBounds;
        Effect(String id,AbilityContext ctx,List<Player> players) {
            this.id=id;this.ctx=ctx;this.players=List.copyOf(players);
            anchor=players.isEmpty()?ctx.caster().entity().getLocation():players.getFirst().getLocation().clone();
            escape=new ControlRules.Escape(id.equals("drain_grab")?ctx.params().getInt("jumps"):Integer.MAX_VALUE,
                Set.of("grab_throw","drain_grab","levitation_cage").contains(id)?ctx.params().getDouble("release-damage"):Double.MAX_VALUE);
        }
    }
    public ControlService(MobsPlatform platform) {this(platform,()->Bukkit.getCurrentTick());}
    ControlService(MobsPlatform platform,LongSupplier clock) {this.platform=platform;this.clock=clock;current=this;}
    public static void execute(String id,AbilityContext ctx,int warning) {if(current!=null&&!current.closed)current.warn(id,ctx,warning);}
    public static void cleanup(ActiveMob caster) {if(current!=null)current.removeOwner(caster.entity().getUniqueId());}
    public static void releasePlayer(Player player) {if(current!=null)current.forget(player);}
    public static void reset() {if(current!=null)current.clear();}
    /** Startup only: initial worlds can load their entity chunks before listeners are enabled. */
    public void recoverLoaded(Collection<World> worlds) {
        for(World world:worlds)for(Chunk chunk:world.getLoadedChunks())for(Entity entity:chunk.getEntities())
            if(entity.getPersistentDataContainer().has(AUXILIARY,PersistentDataType.BYTE))entity.remove();
    }
    public int activeCount() {return controls.values().size();}
    public int auxiliaryCount() {return auxiliaries.size();}
    private boolean valid(Effect e) {
        return e.active&&!closed&&e.ctx.caster().entity().isValid()&&!e.ctx.caster().entity().isDead()
            &&e.players.stream().allMatch(p->allowed(e.ctx.caster(),p));
    }
    private static boolean allowed(ActiveMob caster,Player p) {
        return caster.session().players().contains(p)&&p.isOnline()&&p.isValid()&&!p.isDead()
            &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR&&p.getWorld().equals(caster.entity().getWorld());
    }
    private void warn(String id,AbilityContext ctx,int warning) {
        var selected=ctx.targets().stream().filter(t->t instanceof Player).map(t->(Player)t).filter(p->allowed(ctx.caster(),p)).toList();
        if(selected.isEmpty()&&!id.equals("tactical_summon"))return;
        if(id.equals("soul_chain")){warnOne(id,ctx,selected.stream().limit(2).toList(),warning);return;}
        if(id.equals("tactical_summon")){if(TargetSelector.tacticalSummonAllowed(ctx.caster()))warnOne(id,ctx,List.of(),warning);return;}
        for(Player player:selected)warnOne(id,ctx,List.of(player),warning);
    }
    private void warnOne(String id,AbilityContext ctx,List<Player> players,int warning) {
        // Avoid unlimited pending warnings for the same caster/ability/target.
        var set=owners.computeIfAbsent(ctx.caster().entity().getUniqueId(),k->new HashSet<>());
        if(set.size()>=64||set.stream().anyMatch(e->e.id.equals(id)&&e.players.equals(players)))return;
        var e=new Effect(id,ctx,players);set.add(e);
        for(Player p:players.isEmpty()?EffectAudience.viewers(ctx.session(),e.anchor,Effects.viewRadius()):players)p.sendActionBar(platform.messages().get("control.notice."+id));
        Effects.sound(ctx.session(),e.anchor,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,.7f);
        warning(e,warning);
    }
    private void warning(Effect e,int left) {
        if(!valid(e)){finish(e,false);return;}
        if(left<=0){begin(e);return;}
        Particle particle=e.id.equals("roots")?Particle.DUST_PLUME:e.id.equals("soul_chain")?Particle.SOUL:Particle.ENCHANT;
        for(int i=0;i<8;i++) {
            double angle=i*Math.PI/4;
            Effects.particles(e.ctx.session(),e.anchor.clone().add(Math.cos(angle),.1,Math.sin(angle)),particle,1,0);
        }
        int delay=Math.min(5,left);later(e,delay,()->warning(e,left-delay));
    }
    private void later(Effect e,int ticks,Runnable run) {
        e.ctx.session().scheduler().runLater(ticks,()->{if(!e.active)return;if(!valid(e)){finish(e,false);return;}run.run();});
    }
    private void begin(Effect e) {
        if(e.id.equals("tactical_summon")){summon(e);finish(e,false);return;}
        if(e.id.equals("anchor_spear")){projectile(e);return;}
        if(e.id.equals("bomb_mark")){e.ends=clock.getAsLong()+e.ctx.params().getInt("ticks");bomb(e);return;}
        acquire(e);
    }
    private boolean acquire(Effect e) {
        if(e.id.equals("grab_throw")&&e.players.getFirst().getLocation().distanceSquared(e.ctx.caster().entity().getLocation())>Math.pow(e.ctx.params().getDouble("reach"),2)){finish(e,false);return false;}
        if(!controls.acquire(e.players.stream().map(Player::getUniqueId).toList(),e,clock.getAsLong())){finish(e,false);return false;}
        e.controlling=true;e.ends=clock.getAsLong()+e.ctx.params().getInt("ticks");
        Player p=e.players.getFirst();e.anchor=p.getLocation().clone();
        if(e.id.equals("grab_throw")||e.id.equals("drain_grab")) {
            e.vehicle=e.id.equals("drain_grab")?auxiliary(e,front(e.ctx.caster()),ArmorStand.class,stand->{stand.setInvisible(true);stand.setMarker(true);stand.setGravity(false);stand.setInvulnerable(true);}):e.ctx.caster().entity();
            if(p.isInsideVehicle()){finish(e,false);return false;}
            boolean boarded;e.moving=true;
            try {boarded=e.vehicle.addPassenger(p);}finally {e.moving=false;}
            if(!e.active||!boarded){if(boarded)e.vehicle.removePassenger(p);finish(e,false);return false;}
            e.mounted=true;
            var input=p.getCurrentInput();if(input!=null)e.escape.held(input.isJump());
        }
        if(e.id.equals("roots")||e.id.equals("levitation_cage"))immobilize(p);
        if(e.id.equals("levitation_cage")) {
            // Do not overwrite an independent levitation; withdraw only the exact effect we installed.
            if(p.hasPotionEffect(PotionEffectType.LEVITATION)){finish(e,false);return false;}
            var potion=new PotionEffect(PotionEffectType.LEVITATION,e.ctx.params().getInt("ticks"),e.ctx.params().getInt("level")-1,false,false,true);
            boolean applied;e.applyingPotion=true;
            try {applied=p.addPotionEffect(potion);}finally {e.applyingPotion=false;}
            if(!applied){finish(e,false);return false;}
            // Store Paper's exact applied snapshot, including flags and hidden effects.
            e.levitation=p.getPotionEffect(PotionEffectType.LEVITATION);e.potionApplied=clock.getAsLong();
            FallProtection.shared().protect(p);
        }
        if(e.id.equals("roots")){e.remaining=e.ctx.params().getDouble("health");helper(e,Material.MANGROVE_ROOTS);}
        if(e.id.equals("anchor_spear")){e.hits=e.ctx.params().getInt("hits");helper(e,Material.TRIDENT);}
        pulse(e,0);return true;
    }
    private void pulse(Effect e,int age) {
        if(clock.getAsLong()>e.ends){finish(e,e.id.equals("grab_throw"));return;}
        switch(e.id) {
            case "drain_grab" -> {
                e.moving=true;
                try {e.vehicle.teleport(front(e.ctx.caster()),io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);}
                finally {e.moving=false;}
                if(age%20==0)action(e.players.getFirst(),"control.escape",e.escape.jumps(),e.ctx.params().getInt("jumps"));
                if(age>0&&age%20==0)for(Player p:e.players){double before=p.getHealth();fairDamage(e,p,e.ctx.params().getDouble("damage"));MobHealth.heal(e.ctx.caster().entity(),Math.max(0,before-p.getHealth())*e.ctx.params().getDouble("healing")/100);}
            }
            case "levitation_cage" -> {if(age%5==0)Effects.particles(e.ctx.session(),e.players.getFirst().getLocation(),Particle.ENCHANT,8,.5);if(age>0&&age%20==0)fairDamage(e,e.players.getFirst(),e.ctx.params().getDouble("damage"));}
            case "anchor_spear" -> pull(e.players.getFirst(),e.anchor,e.ctx.params().getDouble("leash"));
            case "soul_chain" -> {
                var a=e.players.getFirst();LivingEntity b=e.players.size()>1?e.players.get(1):e.ctx.caster().entity();
                line(e.ctx.session(),a.getLocation().add(0,1,0),b.getLocation().add(0,1,0));
                boolean apart=a.getLocation().distanceSquared(b.getLocation())>Math.pow(e.ctx.params().getDouble("distance"),2);
                if(apart&&e.players.size()==1)pull(a,b.getLocation(),e.ctx.params().getDouble("distance"));
                if(apart&&age>0&&age%20==0)for(Player p:e.players)fairDamage(e,p,e.ctx.params().getDouble("damage"));
            }
            default -> { }
        }
        if(clock.getAsLong()>=e.ends)finish(e,e.id.equals("grab_throw"));
        else later(e,1,()->pulse(e,age+1));
    }
    private static Location front(ActiveMob caster) {
        var at=caster.entity().getLocation();var direction=at.getDirection().setY(0);
        if(direction.lengthSquared()>0)direction.normalize().multiply(1.2);
        return at.add(direction).add(0,.2,0);
    }
    private void action(Player p,String key,int count,int max) {
        p.sendActionBar(platform.messages().get(key,Placeholder.unparsed("count",Integer.toString(count)),Placeholder.unparsed("max",Integer.toString(max))));
    }
    private static void pull(Player p,Location anchor,double leash) {
        var delta=anchor.toVector().subtract(p.getLocation().toVector());
        if(delta.lengthSquared()>leash*leash)p.setVelocity(delta.normalize().multiply(.6).setY(Math.clamp(delta.getY(),-.3,.3)));
    }
    private static void line(MobHost host,Location a,Location b) {
        var step=b.toVector().subtract(a.toVector());int count=Math.clamp((int)Math.ceil(step.length()),1,16);step.multiply(1.0/count);
        for(int i=0;i<=count;i++)Effects.particles(host,a.clone().add(step.clone().multiply(i)),Particle.SOUL,1,0);
    }
    private static void immobilize(Player p) {
        for(var type:List.of(Attribute.MOVEMENT_SPEED,Attribute.JUMP_STRENGTH)) {
            var a=p.getAttribute(type);if(a!=null&&a.getModifier(IMMOBILE)==null)a.addTransientModifier(new AttributeModifier(IMMOBILE,-1,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
    private static void restoreMovement(Player p) {
        for(var type:List.of(Attribute.MOVEMENT_SPEED,Attribute.JUMP_STRENGTH)) {var a=p.getAttribute(type);if(a!=null&&a.getModifier(IMMOBILE)!=null)a.removeModifier(IMMOBILE);}
    }
    private <T extends Entity> T auxiliary(Effect e,Location at,Class<T> type,java.util.function.Consumer<T> setup) {
        T entity=at.getWorld().spawn(at,type,created->{OwnedEntities.mark(created,e.ctx.caster());created.getPersistentDataContainer().set(AUXILIARY,PersistentDataType.BYTE,(byte)1);setup.accept(created);});
        e.entities.add(entity);auxiliaries.put(entity.getUniqueId(),e);return entity;
    }
    private void helper(Effect e,Material material) {
        auxiliary(e,e.anchor,ItemDisplay.class,d->{d.setItemStack(new ItemStack(material));d.setBillboard(Display.Billboard.FIXED);});
        auxiliary(e,e.anchor,Interaction.class,i->{i.setInteractionWidth(1.5f);i.setInteractionHeight(2);i.setResponsive(true);});
    }
    private void projectile(Effect e) {
        var delta=e.players.getFirst().getEyeLocation().toVector().subtract(e.ctx.caster().entity().getEyeLocation().toVector());
        if(delta.lengthSquared()==0){finish(e,false);return;}
        var shot=Effects.launch(e.ctx.caster(),Trident.class,delta.normalize().multiply(1.2));
        shot.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        shot.getPersistentDataContainer().set(AUXILIARY,PersistentDataType.BYTE,(byte)1);
        e.entities.add(shot);auxiliaries.put(shot.getUniqueId(),e);
        later(e,60,()->finish(e,false));
    }
    private void bomb(Effect e) {
        long left=e.ends-clock.getAsLong();
        if(left<=0) {
            Location at=e.players.getFirst().getLocation();double radius=e.ctx.params().getDouble("radius");
            Effects.particles(e.ctx.session(),at,Particle.EXPLOSION,1,0);Effects.sound(e.ctx.session(),at,Sound.ENTITY_GENERIC_EXPLODE,1,1);
            for(Player p:List.copyOf(e.ctx.session().players()))if(allowed(e.ctx.caster(),p)&&p.getLocation().distanceSquared(at)<=radius*radius) {
                boolean full=p.getHealth()>=maximum(p)-.0001;
                if(full){long tick=clock.getAsLong();UUID uuid=p.getUniqueId();bombSafety.put(uuid,tick);e.ctx.session().scheduler().runLater(1,()->bombSafety.remove(uuid,tick));}
                double amount=ControlRules.bombDamage(e.ctx.params().getDouble("damage"),p.getHealth(),full||Objects.equals(bombSafety.get(p.getUniqueId()),clock.getAsLong()));
                Effects.damage(p,amount,e.ctx.caster());
            }
            finish(e,false);return;
        }
        if(e.entities.isEmpty())auxiliary(e,e.players.getFirst().getLocation().add(0,2.5,0),TextDisplay.class,d->d.setBillboard(Display.Billboard.CENTER));
        var display=(TextDisplay)e.entities.getFirst();display.text(platform.messages().get("control.bomb-countdown",Placeholder.unparsed("seconds",Long.toString((left+19)/20))));
        display.teleport(e.players.getFirst().getLocation().add(0,2.5,0));
        later(e,5,()->bomb(e));
    }
    private static double maximum(Player p) {var a=p.getAttribute(Attribute.MAX_HEALTH);return a==null?20:a.getValue();}
    private void fairDamage(Effect e,Player p,double amount) {Effects.damage(p,Math.min(amount,Math.max(0,maximum(p)-1)),e.ctx.caster());}
    private void summon(Effect e) {
        if(!TargetSelector.tacticalSummonAllowed(e.ctx.caster()))return;
        String template=e.ctx.params().getString("template");if(template.isBlank())return;
        String owner=e.ctx.caster().entity().getUniqueId().toString();
        int alive=(int)e.ctx.session().mobs().stream().filter(m->m.entity().isValid()&&!m.entity().isDead()&&owner.equals(m.entity().getPersistentDataContainer().get(MINION_OWNER,PersistentDataType.STRING))).count();
        int total=(int)e.ctx.session().mobs().stream().filter(m->m.entity().isValid()&&!m.entity().isDead()).count();
        int count=ControlRules.summonCount(e.ctx.params().getInt("count"),alive,e.ctx.params().getInt("max-alive"),total,Effects.maxAliveMobs());
        Location center=TargetSelector.tacticalSummonAnchor(e.ctx.caster());
        for(int i=0;i<count;i++) {
            double angle=2*Math.PI*i/Math.max(1,count);double radius=e.ctx.params().getDouble("radius");
            Location at=center.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            if(e.ctx.session().area()!=null&&!e.ctx.session().area().contains(at))continue;
            ActiveMob child=e.ctx.session().spawnMinion(template,at,e.ctx.caster());
            if(child!=null)child.entity().getPersistentDataContainer().set(MINION_OWNER,PersistentDataType.STRING,owner);
        }
    }
    private void finish(Effect e,boolean throwing) {
        if(!e.active)return;e.active=false;
        if(e.controlling)controls.release(e,clock.getAsLong());
        for(Player p:e.players) {
            if(e.mounted) {
                UUID uuid=p.getUniqueId();releasing.add(uuid);
                try {e.vehicle.removePassenger(p);}finally {releasing.remove(uuid);}
            }
            if(e.controlling&&(e.id.equals("roots")||e.id.equals("levitation_cage")))restoreMovement(p);
            if(e.levitation!=null) {
                var present=p.getPotionEffect(PotionEffectType.LEVITATION);
                if(ownsPotion(e,present))p.removePotionEffect(PotionEffectType.LEVITATION);
            }
            if(e.controlling&&(e.id.equals("levitation_cage")||e.id.equals("grab_throw"))){p.setFallDistance(0);FallProtection.shared().protect(p);}
            if(e.controlling&&p.isOnline())p.sendActionBar(platform.messages().get("control.released"));
            UUID uuid=p.getUniqueId();e.ctx.session().scheduler().runLater(60,()->controls.expire(uuid,clock.getAsLong()));
        }
        removeEntities(e);
        var set=owners.get(e.ctx.caster().entity().getUniqueId());if(set!=null){set.remove(e);if(set.isEmpty())owners.remove(e.ctx.caster().entity().getUniqueId());}
        if(throwing&&allowed(e.ctx.caster(),e.players.getFirst()))launchPlayer(e);
    }
    private void removeEntities(Effect e) {for(Entity entity:List.copyOf(e.entities)){auxiliaries.remove(entity.getUniqueId());entity.remove();}e.entities.clear();}
    private void launchPlayer(Effect previous) {
        Player p=previous.players.getFirst();
        var others=previous.ctx.session().players().stream().filter(q->q!=p&&allowed(previous.ctx.caster(),q)).toList();
        var destination=others.stream().min(Comparator.comparingDouble(q->q.getLocation().distanceSquared(p.getLocation()))).orElse(null);
        Vector direction=destination==null?new Vector(0,1,0):destination.getLocation().toVector().subtract(p.getLocation().toVector());
        if(direction.lengthSquared()==0)direction=new Vector(0,1,0);
        double force=previous.ctx.params().getDouble("force");
        p.setVelocity(destination==null?new Vector(0,force,0):direction.normalize().multiply(force).setY(.7));
        var flight=new Effect("throw_flight",previous.ctx,List.of(p));flight.ends=clock.getAsLong()+60;flight.flightBounds=p.getBoundingBox().clone();
        owners.computeIfAbsent(previous.ctx.caster().entity().getUniqueId(),k->new HashSet<>()).add(flight);
        collide(flight,0);
    }
    private void collide(Effect flight,int age) {
        Player p=flight.players.getFirst();BoundingBox now=p.getBoundingBox();
        Player intercepted=null;double first=Double.POSITIVE_INFINITY;
        for(Player candidate:flight.ctx.session().players())if(candidate!=p&&allowed(flight.ctx.caster(),candidate)
                &&controls.available(candidate.getUniqueId(),clock.getAsLong())) {
            double distance=ControlRules.impactDistance(flight.flightBounds,now,candidate.getBoundingBox());
            if(distance<first){first=distance;intercepted=candidate;}
        }
        flight.flightBounds=now.clone();
        if(intercepted!=null) {
            fairDamage(flight,p,flight.ctx.params().getDouble("damage"));fairDamage(flight,intercepted,flight.ctx.params().getDouble("damage"));
            FallProtection.shared().protect(intercepted);Effects.knockback(intercepted,p.getLocation(),.7,.2);finish(flight,false);return;
        }
        if(clock.getAsLong()>=flight.ends||age>2&&p.isOnGround()){finish(flight,false);return;}
        later(flight,1,()->collide(flight,age+1));
    }
    private boolean ownsPotion(Effect e,PotionEffect present) {
        if(present==null||e.levitation==null)return false;
        int elapsed=(int)Math.max(0,clock.getAsLong()-e.potionApplied);
        // Entities may have already ticked this frame. Compare complete snapshots at either
        // natural tick phase; any successful external change explicitly relinquishes ownership.
        for(int phase=0;phase<=1;phase++) {
            if(agePotion(e.levitation,elapsed+phase).equals(present))return true;
        }
        return false;
    }
    private static PotionEffect agePotion(PotionEffect applied,int elapsed) {
        int duration=applied.isInfinite()?PotionEffect.INFINITE_DURATION:Math.max(0,applied.getDuration()-elapsed);
        // Paper's withDuration drops the hidden snapshot; preserve the complete chain instead.
        var hidden=applied.getHiddenPotionEffect();
        return new PotionEffect(applied.getType(),duration,applied.getAmplifier(),applied.isAmbient(),
            applied.hasParticles(),applied.hasIcon(),hidden==null?null:agePotion(hidden,elapsed));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void potion(EntityPotionEffectEvent event) {
        if(controls.isEmpty()||event.getModifiedType()!=PotionEffectType.LEVITATION)return;
        var control=controls.get(event.getEntity().getUniqueId());
        if(control==null||control.applyingPotion||control.levitation==null)return;
        if(event.getAction()==EntityPotionEffectEvent.Action.CHANGED&&!event.isOverride())return;
        control.levitation=null;
    }
    private void removeOwner(UUID owner) {var set=owners.get(owner);if(set!=null)for(Effect e:List.copyOf(set))finish(e,false);}
    private void forget(Player p) {
        var e=controls.get(p.getUniqueId());if(e!=null)finish(e,false);
        // Pending warnings and bombs are indexed by owner; player exits are rare, not tick work.
        for(var set:List.copyOf(owners.values()))for(var pending:List.copyOf(set))if(pending.players.contains(p))finish(pending,false);
        bombSafety.remove(p.getUniqueId());
    }
    private void clear() {for(UUID id:List.copyOf(owners.keySet()))removeOwner(id);bombSafety.clear();}
    @Override public void close(){clear();closed=true;controls.clear();if(current==this)current=null;}
    @EventHandler public void input(PlayerInputEvent e) {
        if(controls.isEmpty())return;var control=controls.get(e.getPlayer().getUniqueId());
        if(control==null||!control.id.equals("drain_grab"))return;
        if(control.escape.jump(e.getInput().isJump()))finish(control,false);
        else action(e.getPlayer(),"control.escape",control.escape.jumps(),control.ctx.params().getInt("jumps"));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dismount(EntityDismountEvent e) {
        var control=controls.get(e.getEntity().getUniqueId());
        if(control!=null&&control.mounted&&e.getDismounted().equals(control.vehicle))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if(controls.isEmpty())return;var control=controls.get(e.getPlayer().getUniqueId());if(control==null)return;
        if(!valid(control)){finish(control,false);return;}
        if(control.id.equals("roots")) {var to=e.getTo().clone();to.setX(e.getFrom().getX());to.setY(e.getFrom().getY());to.setZ(e.getFrom().getZ());e.setTo(to);}
        if(control.id.equals("levitation_cage")) {var to=e.getTo().clone();to.setX(e.getFrom().getX());to.setZ(e.getFrom().getZ());e.setTo(to);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mobDamage(MobDamageAppliedEvent e) {
        var set=owners.get(e.entity().getUniqueId());if(set==null||!(e.damage() instanceof EntityDamageByEntityEvent hit))return;
        Player attacker=hit.getDamager() instanceof Player p?p:hit.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player p?p:null;
        if(attacker==null)return;
        for(Effect control:List.copyOf(set))if(control.controlling&&!control.players.contains(attacker)&&allowed(control.ctx.caster(),attacker)&&control.escape.damage(e.amount()))finish(control,false);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void auxiliaryDamage(EntityDamageByEntityEvent e) {
        Effect control=auxiliaries.get(e.getEntity().getUniqueId());if(control==null)return;e.setCancelled(true);
        Player attacker=e.getDamager() instanceof Player p?p:e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player p?p:null;
        if(attacker!=null)breakHelper(control,attacker,e.getFinalDamage());
    }
    // Interaction reports left clicks through this Paper event rather than a native health event.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void attack(PlayerInteractEntityEvent e) {
        var control=auxiliaries.get(e.getRightClicked().getUniqueId());if(control!=null)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void preAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        var control=auxiliaries.get(e.getAttacked().getUniqueId());if(control==null)return;
        e.setCancelled(true);var a=e.getPlayer().getAttribute(Attribute.ATTACK_DAMAGE);
        breakHelper(control,e.getPlayer(),a==null?1:a.getValue());
    }
    private void breakHelper(Effect control,Player attacker,double damage) {
        if(!control.controlling)return;
        if(control.id.equals("roots")&&!allowed(control.ctx.caster(),attacker))return;
        if(!attacker.isOnline()||!attacker.isValid()||attacker.isDead()||!attacker.getWorld().equals(control.anchor.getWorld()))return;
        if(control.id.equals("roots")){control.remaining-=Math.max(0,damage);if(control.remaining<=0)finish(control,false);}
        if(control.id.equals("anchor_spear")&&--control.hits<=0)finish(control,false);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void hit(ProjectileHitEvent e) {
        var effect=auxiliaries.get(e.getEntity().getUniqueId());if(effect==null||!effect.id.equals("anchor_spear"))return;
        e.setCancelled(true);Player p=e.getHitEntity() instanceof Player player?player:null;
        removeEntities(effect);
        if(p==null||!allowed(effect.ctx.caster(),p)){finish(effect,false);return;}
        // Impact may hit a different valid participant than the aimed target.
        finish(effect,false);var impact=new Effect("anchor_spear",effect.ctx,List.of(p));
        owners.computeIfAbsent(effect.ctx.caster().entity().getUniqueId(),k->new HashSet<>()).add(impact);
        if(acquire(impact))fairDamage(impact,p,impact.ctx.params().getDouble("damage"));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void projectileDamage(EntityDamageByEntityEvent e) {
        if(auxiliaries.containsKey(e.getDamager().getUniqueId()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void nonlethal(EntityDamageEvent e) {
        Long tick=bombSafety.get(e.getEntity().getUniqueId());if(tick==null||!(e.getEntity() instanceof Player p))return;
        if(tick!=clock.getAsLong()){bombSafety.remove(p.getUniqueId());return;}
        double allowed=Math.max(0,p.getHealth()-1),finalDamage=e.getFinalDamage();
        if(finalDamage>allowed&&finalDamage>0)e.setDamage(e.getDamage()*allowed/finalDamage);
    }
    @EventHandler public void quit(PlayerQuitEvent e){forget(e.getPlayer());FallProtection.shared().finish(e.getPlayer());}
    @EventHandler public void playerDeath(PlayerDeathEvent e){forget(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){forget(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) {if(releasing.contains(e.getPlayer().getUniqueId()))return;var control=controls.get(e.getPlayer().getUniqueId());if(control==null||!control.moving)forget(e.getPlayer());}
    @EventHandler public void death(EntityDeathEvent e){removeOwner(e.getEntity().getUniqueId());}
    @EventHandler public void removed(EntityRemoveFromWorldEvent e){removeOwner(e.getEntity().getUniqueId());}
    @EventHandler public void load(EntitiesLoadEvent e) {for(Entity entity:e.getEntities())if(entity.getPersistentDataContainer().has(AUXILIARY,PersistentDataType.BYTE))entity.remove();}
}

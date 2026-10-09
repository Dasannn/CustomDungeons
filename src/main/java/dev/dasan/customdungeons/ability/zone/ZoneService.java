package dev.dasan.customdungeons.ability.zone;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** Each zone owns one queued continuation on its host ticker. No global tick scans or Bukkit tasks. */
public final class ZoneService implements Listener,AutoCloseable {
    public static final NamespacedKey ARROW=MobsPlatform.key("zone_arrow");
    public static final NamespacedKey AUXILIARY=MobsPlatform.key("zone_auxiliary");
    private static ZoneService current;
    private final MobsPlatform platform;
    private final Map<UUID,Set<Zone>> owners=new HashMap<>();
    private final Map<UUID,Shot> arrows=new HashMap<>();
    private final Map<UUID,Zone> floating=new HashMap<>();
    private final Set<UUID> damaging=new HashSet<>();
    // Weak player keys also expire when a closing host discards its one-tick continuation.
    private final Map<Player,Long> fullSafety=new WeakHashMap<>();
    private final Map<UUID,Budget> budgets=new HashMap<>();
    private boolean closed;
    private Zone damageZone;
    private static final class Budget {long tick=Long.MIN_VALUE;int used;}
    private record Shot(Arrow arrow,Zone zone,long expires) {}
    private static final class Zone {
        final String id;final AbilityContext ctx;final Location center;final Vector direction;
        final int warning,pattern;
        final Map<Player,PotionEffect> levitation=new HashMap<>();
        final List<Block> blocks=new ArrayList<>();
        final Set<Shot> shots=new HashSet<>();
        BlockDisplay falling;boolean impacted;
        List<ZoneRules.Tile> warnedTiles;
        boolean warningFailed,burstWarningFailed;
        final double burstRadius;
        long visualTick=Long.MIN_VALUE;int visualUsed;
        int left,elapsed,wave,emitted;double length;boolean active=true,started;
        Zone(String id,AbilityContext ctx,Location center,Vector direction,int warning) {
            this.id=id;this.ctx=ctx;this.center=center.clone();this.direction=direction.clone();
            this.warning=warning;left=warning;pattern=ThreadLocalRandom.current().nextInt(3);
            burstRadius=id.equals("vortex")?ctx.params().getDouble("burst-radius"):0;
        }
        double n(String key) {return ctx.params().getDouble(key);}
        int i(String key) {return ctx.params().getInt(key);}
        UUID owner() {return ctx.caster().entity().getUniqueId();}
    }
    public ZoneService(MobsPlatform platform) {this.platform=Objects.requireNonNull(platform);current=this;}
    public static void execute(String id,AbilityContext ctx,int warning) {if(current!=null&&!current.closed)current.create(id,ctx,warning);}
    public static void cleanup(ActiveMob mob) {if(current!=null)current.removeOwner(mob.entity().getUniqueId());}
    public static void reset() {if(current!=null)current.clear();}
    public int activeCount() {return owners.values().stream().mapToInt(Set::size).sum();}
    public int arrowCount() {return arrows.size();}
    private boolean valid(Zone z) {return z.active&&!closed&&z.ctx.caster().entity().isValid()&&!z.ctx.caster().entity().isDead();}
    private static boolean allowed(Zone z,Player p) {
        return z.ctx.session().players().contains(p)&&p.isOnline()&&p.isValid()&&!p.isDead()
                &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
                &&p.getWorld().equals(z.center.getWorld());
    }
    private static boolean loaded(Location at) {return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static boolean inside(AbilityContext ctx,Location at) {return ctx.session().area()==null||ctx.session().area().contains(at);}
    private void create(String id,AbilityContext ctx,int configuredWarning) {
        if(!ctx.caster().entity().isValid()||ctx.caster().entity().isDead())return;
        var targets=ctx.targets().stream().filter(Player.class::isInstance).map(Player.class::cast)
                .filter(p->p.isOnline()&&p.isValid()&&!p.isDead()&&ctx.session().players().contains(p)
                        &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR&&p.getWorld().equals(ctx.caster().entity().getWorld())).toList();
        if(targets.isEmpty())return;
        Location origin=ctx.caster().entity().getLocation();
        int count=switch(id) {case "falling_pillars","poison_pools" -> ctx.params().getInt("count");default -> 1;};
        int intrinsic=switch(id) {
            case "vortex","rift","poison_pools" -> 20;case "sweep" -> 16;
            case "cracked_floor","falling_pillars","charged_beam" -> ctx.params().getInt("warning");default -> 30;
        };
        int warning=Math.max(intrinsic,configuredWarning);
        var anchors=TargetSelector.zoneTargets(ctx.caster(),targets);
        List<Zone> added=new ArrayList<>();
        for(int index=0;index<count;index++) {
            // Pillar shadows are under distinct players; pools may surround a smaller group.
            if(id.equals("falling_pillars")&&index>=anchors.size())break;
            Player target=anchors.get(index%anchors.size());
            Location at=Set.of("vortex","sweep","cracked_floor","charged_beam").contains(id)?origin.clone():TargetSelector.zonePosition(ctx.caster(),target);
            if(id.equals("poison_pools")&&index>=anchors.size()) {
                double angle=2*Math.PI*index/count;at.add(Math.cos(angle)*ctx.params().getDouble("radius")*2,0,Math.sin(angle)*ctx.params().getDouble("radius")*2);
            }
            if(!loaded(at)||!inside(ctx,at))continue;
            var set=owners.computeIfAbsent(ctx.caster().entity().getUniqueId(),k->new LinkedHashSet<>());
            if(id.equals("poison_pools")&&set.stream().filter(zone->zone.id.equals(id)).count()>=6)break;
            if(set.size()>=ZoneRules.zoneLimit(platform.limits().maxAliveMobsPerSession()))break;
            Vector direction=id.equals("charged_beam")?TargetSelector.zonePosition(ctx.caster(),target).toVector().subtract(origin.toVector()).setY(0):origin.getDirection().setY(0);
            if(direction.lengthSquared()==0)direction=new Vector(0,0,1);else direction.normalize();
            Zone zone=new Zone(id,ctx,at,direction,warning);set.add(zone);
            if(id.equals("charged_beam"))zone.length=beamLength(zone);
            for(Player viewer:ctx.session().audience(at))viewer.sendActionBar(platform.messages().get("zone.notice."+id));
            Effects.sound(ctx.session(),at,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.7f,.7f);
            added.add(zone);
        }
        added.forEach(this::step);
    }
    private void later(Zone z) {z.ctx.session().scheduler().runLater(1,()->step(z));}
    private void step(Zone z) {
        if(!z.active)return;
        if(!valid(z)||!loaded(z.center)){finish(z);return;}
        // One continuation is shared by warning, active effect, owned arrows and obstacle lifetime.
        if(z.left>0) {if(z.left%5==0||z.left==z.warning)draw(z,true);z.left--;later(z);return;}
        if(!z.started) {z.started=true;begin(z);if(!z.active)return;if(z.left>0){later(z);return;}}
        switch(z.id) {
            case "vortex" -> vortex(z);
            case "inverted_gravity" -> gravity(z);
            case "poison_pools" -> pool(z);
            case "arrow_rain" -> rain(z);
            case "falling_pillars" -> falling(z);
            default -> {finish(z);return;}
        }
        if(!z.active)return;
        z.elapsed++;later(z);
    }
    private void begin(Zone z) {
        if(z.warningFailed){finish(z);return;}
        switch(z.id) {
            case "sweep" -> {draw(z,false);for(Player p:players(z))if(hit(z,p)) {damage(z,p,z.n("damage"));Effects.knockback(p,z.center,.7,.2);}finish(z);}
            case "charged_beam" -> {z.length=Math.min(z.length,beamLength(z));draw(z,false);for(Player p:players(z))if(hit(z,p))damage(z,p,z.n("damage"));finish(z);}
            case "cracked_floor" -> cracked(z);
            case "falling_pillars" -> fallingVisual(z);
            case "rift" -> rift(z);
            default -> { /* Persistent zones start below. */ }
        }
    }
    private List<Player> players(Zone z) {return z.ctx.session().players().stream().filter(p->allowed(z,p)).toList();}
    private double radius(Zone z) {return switch(z.id) {case "sweep" -> z.n("reach");case "falling_pillars","rift" -> 1;case "charged_beam" -> z.n("length");default -> z.n("radius");};}
    private boolean within(Zone z,Player p,double radius) {
        Location at=p.getLocation();double x=at.getX()-z.center.getX(),dz=at.getZ()-z.center.getZ();
        return x*x+dz*dz<=radius*radius && Math.abs(at.getY()-z.center.getY())<=4;
    }
    private boolean hit(Zone z,Player p) {
        Location at=p.getLocation();double x=at.getX()-z.center.getX(),dz=at.getZ()-z.center.getZ();
        if(Math.abs(at.getY()-z.center.getY())>3)return false;
        return z.id.equals("charged_beam")?ZoneRules.beam(x,dz,z.direction.getX(),z.direction.getZ(),z.length,z.n("width")):
                ZoneRules.cone(x,dz,z.direction.getX(),z.direction.getZ(),z.n("reach"),z.n("angle"));
    }
    private void damage(Zone z,Player p,double amount) {
        if(!allowed(z,p))return;
        damaging.add(p.getUniqueId());Zone previous=damageZone;damageZone=z;
        try {Effects.damage(p,amount,z.ctx.caster());}finally {damaging.remove(p.getUniqueId());damageZone=previous;}
    }
    private void vortex(Zone z) {
        if(z.elapsed>=z.i("ticks")) {
            if(!z.burstWarningFailed)for(Player p:players(z))if(within(z,p,z.burstRadius)) {damage(z,p,z.n("damage"));Effects.knockback(p,z.center,.7,.2);}
            finish(z);return;
        }
        if(z.elapsed%5==0) {
            // The attraction period is the full advance warning for the separate burst.
            // Reserve its complete fixed ring before spending particles on the spiral.
            var ring=new ArrayList<Location>();
            for(int index=0;index<8;index++) {
                double angle=2*Math.PI*index/8;
                ring.add(z.center.clone().add(Math.cos(angle)*z.burstRadius,.1,Math.sin(angle)*z.burstRadius));
            }
            if(!z.burstWarningFailed)z.burstWarningFailed=!marks(z,ring,Particle.ENCHANT);
            draw(z,false);
        }
        for(Player p:players(z))if(within(z,p,z.n("radius"))) {
            Vector pull=z.ctx.caster().entity().getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            // Default 0.3 produces a gentle acceleration; sprint speed can overcome it.
            if(pull.lengthSquared()>.25)p.setVelocity(p.getVelocity().add(pull.normalize().multiply(z.n("force")*.1)));
        }
    }
    private void gravity(Zone z) {
        if(z.elapsed>=z.i("ticks")){finish(z);return;}
        if(z.elapsed%5==0)draw(z,false);
        for(Player p:players(z))if(within(z,p,z.n("radius"))&&!z.levitation.containsKey(p)&&!floating.containsKey(p.getUniqueId())) {
            // Do not overwrite a foreign effect. The player keeps normal horizontal movement.
            if(ControlService.hasActiveControl(p)||p.hasPotionEffect(PotionEffectType.LEVITATION))continue;
            PotionEffect applied=new PotionEffect(PotionEffectType.LEVITATION,z.i("ticks")-z.elapsed,0,false,true,true);
            if(p.addPotionEffect(applied)){z.levitation.put(p,applied);floating.put(p.getUniqueId(),z);FallProtection.shared().protect(p);}
        }
        for(Player p:List.copyOf(z.levitation.keySet())) {
            if(ControlService.hasActiveControl(p)||!allowed(z,p)||!withinHorizontal(z,p,z.n("radius"))) {releaseFloat(z,p);continue;}
            if(z.elapsed%20==0)damage(z,p,z.n("damage"));
            FallProtection.shared().protect(p);
        }
    }
    private boolean withinHorizontal(Zone z,Player p,double radius) {
        double x=p.getLocation().getX()-z.center.getX(),dz=p.getLocation().getZ()-z.center.getZ();return x*x+dz*dz<=radius*radius;
    }
    private void releaseFloat(Zone z,Player p) {
        PotionEffect own=z.levitation.remove(p);floating.remove(p.getUniqueId(),z);
        var present=p.getPotionEffect(PotionEffectType.LEVITATION);
        if(own!=null&&present!=null&&present.getAmplifier()==own.getAmplifier()&&present.getDuration()<=own.getDuration()
                &&present.hasParticles()==own.hasParticles()&&present.isAmbient()==own.isAmbient()&&present.hasIcon()==own.hasIcon())p.removePotionEffect(PotionEffectType.LEVITATION);
        p.setFallDistance(0);FallProtection.shared().protect(p);
    }
    private void cracked(Zone z) {
        if(z.wave+1>=z.i("waves"))draw(z,false);
        for(Player p:players(z))if(within(z,p,z.n("radius"))&&z.warnedTiles!=null&&z.warnedTiles.contains(new ZoneRules.Tile(
                (int)Math.floor((p.getLocation().getX()-z.center.getX())/2)*2,
                (int)Math.floor((p.getLocation().getZ()-z.center.getZ())/2)*2))) {
            damage(z,p,z.n("damage"));FallProtection.shared().protect(p);p.setVelocity(p.getVelocity().setY(.35));
        }
        if(++z.wave<z.i("waves")){z.left=z.warning-1;z.started=false;z.warnedTiles=null;draw(z,true);}
        else finish(z);
    }
    private int entities(UUID owner) {
        return owners.getOrDefault(owner,Set.of()).stream().mapToInt(z->z.shots.size()+(z.falling==null?0:1)).sum();
    }
    private void fallingVisual(Zone z) {
        Location above=z.center.clone().add(0,8,0);
        if(!inside(z.ctx,above)||!loaded(above)||entities(z.owner())>=ZoneRules.arrowLimit(platform.limits().maxAliveMobsPerSession()))return;
        z.falling=above.getWorld().spawn(above,BlockDisplay.class,created->{
            OwnedEntities.mark(created,z.ctx.caster());created.getPersistentDataContainer().set(AUXILIARY,PersistentDataType.BYTE,(byte)1);
            created.setBlock(Bukkit.createBlockData(Material.STONE_BRICKS));created.setPersistent(false);
            created.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-.5f,0,-.5f),new org.joml.Quaternionf(),new org.joml.Vector3f(1,3,1),new org.joml.Quaternionf()));
            created.setTeleportDuration(1);
        });
    }
    private void falling(Zone z) {
        if(z.elapsed<10) {
            if(z.falling!=null)z.falling.teleport(z.center.clone().add(0,8*(1-(z.elapsed+1)/10.0),0));
            return;
        }
        if(!z.impacted) {
            z.impacted=true;if(z.falling!=null){z.falling.remove();z.falling=null;}
            pillar(z);if(!z.active)return;
        }
        if(z.elapsed>=10+z.i("obstacle-ticks"))finish(z);
    }
    private void pillar(Zone z) {
        draw(z,false);for(Player p:players(z))if(within(z,p,1))damage(z,p,z.n("damage"));
        if(z.i("obstacle-ticks")==0){finish(z);return;}
        // The host owns durability, pending cancellation and crash recovery. No falling block entity.
        if(!(z.ctx.session().tempBlocks() instanceof RestorableTempBlocks)){finish(z);return;}
        for(int y=0;y<3;y++) {
            Location at=z.center.clone().add(0,y,0);
            if(!loaded(at)||!inside(z.ctx,at)||at.getBlockY()<at.getWorld().getMinHeight()||at.getBlockY()>=at.getWorld().getMaxHeight())continue;
            Block block=at.getBlock();
            if(block.isEmpty()&&z.ctx.session().tempBlocks().place(block,Bukkit.createBlockData(Material.STONE_BRICKS),z.i("obstacle-ticks")))z.blocks.add(block);
        }
    }
    private void pool(Zone z) {
        if(z.elapsed>=z.i("ticks")){finish(z);return;}
        if(z.elapsed%5==0)draw(z,false);
        if(z.elapsed%20==0)for(Player p:players(z))if(within(z,p,z.n("radius")))damage(z,p,z.n("damage"));
    }
    private void rain(Zone z) {
        long now=z.ctx.session().scheduler().currentTick();
        for(Shot shot:List.copyOf(z.shots))if(!shot.arrow().isValid()||now>=shot.expires())removeShot(shot);
        if(z.elapsed<z.i("ticks")) {
            if(z.elapsed%5==0)draw(z,false);
            int due=(z.elapsed+1)*z.i("rate")/20;
            while(z.emitted<due){z.emitted++;spawnArrow(z,now);}
        } else if(z.shots.isEmpty()){finish(z);}
    }
    private void spawnArrow(Zone z,long now) {
        if(entities(z.owner())>=ZoneRules.arrowLimit(platform.limits().maxAliveMobsPerSession()))return;
        var random=ThreadLocalRandom.current();double angle=random.nextDouble(Math.PI*2),r=Math.sqrt(random.nextDouble())*z.n("radius");
        Location at=z.center.clone().add(Math.cos(angle)*r,10,Math.sin(angle)*r);
        if(!loaded(at)||at.getY()>=at.getWorld().getMaxHeight()||!inside(z.ctx,at.clone().subtract(0,10,0)))return;
        Arrow arrow=at.getWorld().spawn(at,Arrow.class,created->{
            OwnedEntities.mark(created,z.ctx.caster());created.getPersistentDataContainer().set(ARROW,PersistentDataType.BYTE,(byte)1);
            created.setShooter(z.ctx.caster().entity());created.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            created.setDamage(z.n("damage"));created.setCritical(false);created.setPierceLevel(0);created.setFireTicks(0);
            created.setVelocity(new Vector(0,-1,0));
        });
        Shot shot=new Shot(arrow,z,now+80);arrows.put(arrow.getUniqueId(),shot);z.shots.add(shot);
    }
    private double beamLength(Zone z) {
        Location eye=z.center.clone().add(0,1,0);
        double length=z.n("length");
        // Step only through loaded blocks. A wall appearing during the warning also stops damage.
        for(double distance=0;distance<=length;distance+=.25) {
            Location at=eye.clone().add(z.direction.clone().multiply(distance));
            if(!loaded(at)||!inside(z.ctx,at)||at.getBlock().isSolid())return Math.max(0,distance-.25);
        }
        return length;
    }
    private void rift(Zone z) {
        var group=players(z);
        for(Player p:group)if(within(z,p,1)) {
            var area=z.ctx.session().area();if(area==null)continue;
            Optional<Location> destination=Optional.empty();
            var random=ThreadLocalRandom.current();double minimum=z.n("min-distance"),maximum=z.n("max-distance");
            if(minimum>maximum)continue;
            for(int attempt=0;attempt<20;attempt++) {
                double angle=random.nextDouble(Math.PI*2),distance=random.nextDouble(minimum,maximum+Math.ulp(maximum));
                int x=(int)Math.floor(z.center.getX()+Math.cos(angle)*distance),dz=(int)Math.floor(z.center.getZ()+Math.sin(angle)*distance);
                if(!z.center.getWorld().isChunkLoaded(x>>4,dz>>4))continue;
                var point=SafeTerrain.validate(z.center.getWorld(),x,dz,p.getWidth(),p.getHeight());
                if(point.isEmpty()||!area.contains(point.get()))continue;
                Location at=point.get();
                if(!area.contains(at.clone().add(p.getWidth()/2,p.getHeight(),p.getWidth()/2))
                        ||!area.contains(at.clone().subtract(p.getWidth()/2,0,p.getWidth()/2)))continue;
                double nearest=group.stream().mapToDouble(q->Math.sqrt(Math.pow(q.getLocation().getX()-at.getX(),2)+Math.pow(q.getLocation().getZ()-at.getZ(),2))).min().orElse(0);
                if(!ZoneRules.riftDistance(nearest,minimum,maximum))continue;
                destination=point;break;
            }
            if(destination.isPresent()) {
                Location at=destination.get();at.setYaw(p.getYaw());at.setPitch(p.getPitch());
                // No blindness or fall state is applied if another plugin refuses the teleport.
                if(p.teleport(at)){p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,20,0));FallProtection.shared().protect(p);}
            }
        }
        finish(z);
    }
    private void draw(Zone z,boolean warning) {
        if(warning){warn(z);return;}
        Particle particle=warning?Particle.ENCHANT:z.id.equals("poison_pools")?Particle.WITCH:Particle.END_ROD;
        double radius=radius(z);
        int cost=Math.max(1,(int)Math.round(Math.clamp(platform.limits().particleDensity(),0,10)));
        int quota=Math.max(warning?64:0,ZoneRules.particleBudget(platform.limits().particleDensity()))/Math.max(1,owners.getOrDefault(z.owner(),Set.of()).size())/cost;
        if(z.id.equals("charged_beam")) {
            double length=warning?beamLength(z):z.length;
            int points=Math.max(2,Math.min(32,quota/2));
            for(int index=0;index<points;index++) {
                double distance=length*index/(points-1);
                Location at=z.center.clone().add(z.direction.clone().multiply(distance));
                double half=z.n("width")/2;
                particle(z,at.clone().add(z.direction.getZ()*half,.1,-z.direction.getX()*half),particle,warning);
                particle(z,at.clone().add(-z.direction.getZ()*half,.1,z.direction.getX()*half),particle,warning);
            }
        } else if(z.id.equals("cracked_floor")) {
            var tiles=z.warnedTiles==null?List.<ZoneRules.Tile>of():z.warnedTiles;
            int count=Math.min(tiles.size(),Math.max(1,quota/4));
            int shift=(z.warning-z.left+z.elapsed)/5*count;
            for(int index=0;index<count;index++)for(int side=0;side<4;side++) {
                var tile=tiles.get(Math.floorMod(index+shift,tiles.size()));
                particle(z,z.center.clone().add(tile.x()+(side%2)*1.8,.1,tile.z()+(side/2)*1.8),particle,warning);
            }
        } else {
            int points=Math.max(4,Math.min(24,quota));
            for(int index=0;index<points;index++) {
                double angle=z.id.equals("sweep")?Math.atan2(z.direction.getZ(),z.direction.getX())+Math.toRadians(z.n("angle"))*(index/(double)(points-1)-.5):2*Math.PI*index/points;
                double x=Math.cos(angle)*radius,dz=Math.sin(angle)*radius;
                if(z.id.equals("sweep")&&!ZoneRules.cone(x,dz,z.direction.getX(),z.direction.getZ(),radius+.001,z.n("angle")))continue;
                if(z.id.equals("vortex")) {double scale=.25+.75*index/points;x*=scale;dz*=scale;}
                particle(z,z.center.clone().add(x,.1,dz),particle,warning);
            }
            if(z.id.equals("falling_pillars")&&!warning)for(int y=8;y>=0;y--)particle(z,z.center.clone().add(0,y,0),Particle.CLOUD,warning);
        }
    }
    /** A hazard is enabled only after its entire fixed mark survives every warning frame. */
    private void warn(Zone z) {
        if(z.warningFailed)return;
        if(z.id.equals("cracked_floor")) {
            if(z.warnedTiles==null) {
                int cost=particleCost();
                int capacity=warningBudget()/Math.max(1,owners.getOrDefault(z.owner(),Set.of()).size())/cost/4;
                z.warnedTiles=ZoneRules.tiles(radius(z),z.pattern,z.wave).stream()
                        .sorted(Comparator.comparingDouble(t->Math.hypot(t.x()+1,t.z()+1))).limit(capacity).toList();
            }
            // Never rotate in a new tile halfway through the warning. Failed marks stay harmless.
            z.warnedTiles=z.warnedTiles.stream().filter(tile->marks(z,List.of(
                    z.center.clone().add(tile.x(),.1,tile.z()),z.center.clone().add(tile.x()+1.8,.1,tile.z()),
                    z.center.clone().add(tile.x(),.1,tile.z()+1.8),z.center.clone().add(tile.x()+1.8,.1,tile.z()+1.8)),Particle.ENCHANT)).toList();
            return;
        }
        var points=new ArrayList<Location>();
        if(z.id.equals("charged_beam")) {
            for(int index=0;index<4;index++) {
                Location at=z.center.clone().add(z.direction.clone().multiply(z.length*index/3));
                double half=z.n("width")/2;
                points.add(at.clone().add(z.direction.getZ()*half,.1,-z.direction.getX()*half));
                points.add(at.clone().add(-z.direction.getZ()*half,.1,z.direction.getX()*half));
            }
        } else for(int index=0;index<8;index++) {
            double angle=z.id.equals("sweep")?Math.atan2(z.direction.getZ(),z.direction.getX())+Math.toRadians(z.n("angle"))*(index/7.0-.5):2*Math.PI*index/8;
            double radius=radius(z);
            if(z.id.equals("vortex"))radius*=.25+.75*index/8;
            points.add(z.center.clone().add(Math.cos(angle)*radius,.1,Math.sin(angle)*radius));
        }
        z.warningFailed=!marks(z,points,Particle.ENCHANT);
    }
    private int particleCost() {
        double density=platform.limits().particleDensity();
        return Double.isFinite(density)?Math.max(1,(int)Math.round(Math.clamp(density,0,10))):1;
    }
    private int warningBudget() {return Math.max(64,ZoneRules.particleBudget(platform.limits().particleDensity()));}
    /** Reserve the complete outline atomically; partial outlines never authorize an effect. */
    private boolean marks(Zone z,List<Location> points,Particle type) {
        if(points.isEmpty()||points.stream().anyMatch(at->!loaded(at)||!inside(z.ctx,at)
                ||EffectAudience.viewers(z.ctx.session(),at,Effects.viewRadius()).isEmpty()))return false;
        Budget budget=budgets.computeIfAbsent(z.owner(),k->new Budget());long now=z.ctx.session().scheduler().currentTick();
        if(budget.tick!=now){budget.tick=now;budget.used=0;}
        if(z.visualTick!=now){z.visualTick=now;z.visualUsed=0;}
        int maximum=warningBudget(),cost=particleCost()*points.size();
        if(budget.used+cost>maximum||z.visualUsed+cost>maximum/Math.max(1,owners.getOrDefault(z.owner(),Set.of()).size()))return false;
        budget.used+=cost;z.visualUsed+=cost;
        for(Location at:points)Effects.particles(z.ctx.session(),at,type,1,0);
        return true;
    }
    private void particle(Zone z,Location at,Particle type,boolean warning) {
        Budget budget=budgets.computeIfAbsent(z.owner(),k->new Budget());long now=z.ctx.session().scheduler().currentTick();
        if(budget.tick!=now){budget.tick=now;budget.used=0;}
        double density=platform.limits().particleDensity();int maximum=Math.max(warning?64:0,ZoneRules.particleBudget(density));
        int cost=Math.max(1,(int)Math.round(Math.clamp(density,0,10)));
        if(z.visualTick!=now){z.visualTick=now;z.visualUsed=0;}
        // Reserve an equal slice for each possible zone so none loses its warning to another.
        if(budget.used+cost>maximum||z.visualUsed+cost>maximum/Math.max(1,owners.getOrDefault(z.owner(),Set.of()).size()))return;budget.used+=cost;z.visualUsed+=cost;
        if(loaded(at)&&inside(z.ctx,at))Effects.particles(z.ctx.session(),at,type,1,0);
    }
    private void removeShot(Shot s) {arrows.remove(s.arrow().getUniqueId());s.zone().shots.remove(s);s.arrow().remove();}
    private void finish(Zone z) {
        if(!z.active)return;z.active=false;
        if(z.falling!=null){z.falling.remove();z.falling=null;}
        for(Player p:List.copyOf(z.levitation.keySet()))releaseFloat(z,p);
        for(Shot shot:List.copyOf(z.shots))removeShot(shot);
        if(z.ctx.session().tempBlocks() instanceof RestorableTempBlocks blocks)for(Block block:z.blocks)blocks.restore(block);
        z.blocks.clear();var set=owners.get(z.owner());
        if(set!=null){set.remove(z);if(set.isEmpty()){owners.remove(z.owner());budgets.remove(z.owner());}}
    }
    private void removeOwner(UUID owner) {for(Zone z:List.copyOf(owners.getOrDefault(owner,Set.of())))finish(z);}
    private void clear() {for(UUID owner:List.copyOf(owners.keySet()))removeOwner(owner);damaging.clear();fullSafety.clear();budgets.clear();}
    @Override public void close() {clear();closed=true;if(current==this)current=null;}
    private void releasePlayer(Player player) {fullSafety.remove(player);Zone z=floating.get(player.getUniqueId());if(z!=null)releaseFloat(z,player);}
    public void recoverLoaded(Collection<World> worlds) {
        for(World world:worlds)for(Chunk chunk:world.getLoadedChunks())for(Entity entity:chunk.getEntities())if(remnant(entity))entity.remove();
    }
    private static boolean remnant(Entity entity) {return marked(entity)||entity.getPersistentDataContainer().has(AUXILIARY,PersistentDataType.BYTE);}
    private static boolean marked(Entity entity) {return entity.getPersistentDataContainer().has(ARROW,PersistentDataType.BYTE);}
    @EventHandler public void death(EntityDeathEvent event) {removeOwner(event.getEntity().getUniqueId());}
    @EventHandler public void removed(EntityRemoveFromWorldEvent event) {removeOwner(event.getEntity().getUniqueId());var shot=arrows.remove(event.getEntity().getUniqueId());if(shot!=null)shot.zone().shots.remove(shot);}
    @EventHandler public void load(EntitiesLoadEvent event) {for(Entity entity:event.getEntities())if(remnant(entity))entity.remove();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void potion(EntityPotionEffectEvent event) {
        if(event.getModifiedType()!=PotionEffectType.LEVITATION)return;
        Zone zone=floating.get(event.getEntity().getUniqueId());
        if(zone==null||!(event.getEntity() instanceof Player player))return;
        if(event.getAction()==EntityPotionEffectEvent.Action.CHANGED&&!event.isOverride())return;
        // A replacement effect belongs to its own source; cleanup must never remove it.
        zone.levitation.remove(player);floating.remove(player.getUniqueId(),zone);
        FallProtection.shared().protect(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {releasePlayer(event.getPlayer());}
    @EventHandler public void playerDeath(PlayerDeathEvent event) {releasePlayer(event.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent event) {releasePlayer(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) {releasePlayer(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST) public void hit(ProjectileHitEvent event) {
        if(!marked(event.getEntity()))return;
        Shot shot=arrows.get(event.getEntity().getUniqueId());
        if(shot==null){event.setCancelled(true);event.getEntity().remove();return;}
        if(event.getHitEntity()!=null&&(!(event.getHitEntity() instanceof Player p)||!allowed(shot.zone(),p)))event.setCancelled(true);
        // Keep native arrow collision/damage and shield handling; retire after it completes.
        shot.zone().ctx.session().scheduler().runLater(1,()->removeShot(shot));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void combust(EntityCombustByEntityEvent event) {
        if(marked(event.getCombuster()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void arrowDamage(EntityDamageByEntityEvent event) {
        if(!marked(event.getDamager()))return;Shot shot=arrows.get(event.getDamager().getUniqueId());
        if(shot==null||!valid(shot.zone())||!(event.getEntity() instanceof Player p)||!allowed(shot.zone(),p)
                ||!within(shot.zone(),p,shot.zone().n("radius"))){event.setCancelled(true);return;}
        // Damage is specified per arrow; vanilla velocity multipliers do not change the GUI value.
        event.setDamage(shot.zone().n("damage"));cap(event,p,shot.zone());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fairDamage(EntityDamageEvent event) {
        if(damaging.contains(event.getEntity().getUniqueId())&&event.getEntity() instanceof Player p)cap(event,p,damageZone);
    }
    private void cap(EntityDamageEvent event,Player p,Zone zone) {
        var maximum=p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double health=maximum==null?20:maximum.getValue();
        long now=Bukkit.getCurrentTick();
        if(p.getHealth()>=health&&!Objects.equals(fullSafety.get(p),now)) {
            fullSafety.put(p,now);
            // No global sweeper; the owning event schedules its bounded one-tick expiry.
            if(zone!=null)zone.ctx.session().scheduler().runLater(1,()->fullSafety.remove(p,now));
        }
        double capped=Objects.equals(fullSafety.get(p),now)?Math.min(event.getFinalDamage(),Math.max(0,p.getHealth()-1))
                :ZoneRules.damage(event.getFinalDamage(),p.getHealth(),health);
        if(capped<event.getFinalDamage()&&event.getFinalDamage()>0)event.setDamage(event.getDamage()*capped/event.getFinalDamage());
    }
}

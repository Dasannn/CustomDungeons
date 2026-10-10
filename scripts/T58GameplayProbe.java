import com.google.gson.Gson;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.lang.reflect.Field;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Temporary acceptance-only plugin. Reflects plugin state, never NMS/CraftBukkit; removed by runner. */
public final class T58GameplayProbe extends JavaPlugin implements Listener {
    private final Gson json=new Gson();
    private final Map<UUID,Location> previous=new HashMap<>();
    private final Map<UUID,Map<String,Long>> cooldowns=new HashMap<>();
    private final Map<UUID,Long> knocked=new HashMap<>();
    private final Map<UUID,String> knockCauses=new HashMap<>();
    private final Map<UUID,org.bukkit.util.Vector> knockVectors=new HashMap<>();
    private final Map<UUID,Location> attackers=new HashMap<>();
    private final Map<UUID,Long> legacyTicks=new HashMap<>();
    private final Map<UUID,org.bukkit.util.Vector> modernVectors=new HashMap<>();
    private final Map<UUID,Long> attackTicks=new HashMap<>();
    private record TeleportStep(int tick,Location from,Location destination,String kind) {}
    private final Map<UUID,TeleportStep> teleports=new HashMap<>();
    private final Map<UUID,TeleportStep> physicalTeleports=new HashMap<>();
    private final Map<UUID,Double> knockForces=new HashMap<>();
    private final Map<UUID,org.bukkit.util.Vector> rawKnockVectors=new HashMap<>();
    private final Map<UUID,List<Map<String,Object>>> physicalWindows=new HashMap<>();
    private final Map<UUID,Integer> lastPhysicalTick=new HashMap<>();
    private final Map<UUID,ActiveMob> observed=new HashMap<>();
    private final Map<UUID,Location> motionPrevious=new HashMap<>();
    private final Map<UUID,List<Map<String,Object>>> motionWindows=new HashMap<>();
    private record PendingSnapshot(dev.dasan.customdungeons.model.AbilityInstance instance,AbilityContext warning) {}
    private final Map<UUID,Map<String,PendingSnapshot>> pendingPrevious=new HashMap<>();
    private CustomDungeonsPlugin plugin;
    private org.bukkit.scheduler.BukkitTask observation;
    private boolean fine;
    private final Set<MobHost> orderedHosts=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Class<?>,Map<String,Field>> fields=new HashMap<>();
    private static final StackWalker stacks=StackWalker.getInstance();
    static Object field(Object owner,String name) throws ReflectiveOperationException {
        Class<?> type=owner instanceof Class<?> c?c:owner.getClass();
        var known=fields.computeIfAbsent(type,k->new HashMap<>());Field field=known.get(name);
        if(field==null){field=type.getDeclaredField(name);field.setAccessible(true);known.put(name,field);}
        return field.get(owner instanceof Class<?>?null:owner);
    }
    @Override public void onEnable() {
        plugin=(CustomDungeonsPlugin)getServer().getPluginManager().getPlugin("CustomDungeons");
        getLogger().info("T58_RUNTIME_CPUS "+Runtime.getRuntime().availableProcessors());
        for(var listener:org.bukkit.event.player.PlayerInteractEvent.getHandlerList().getRegisteredListeners())
            getLogger().info("T58_USE_HANDLER "+listener.getPriority()+" "+listener.getPlugin().getName()+" "+listener.getListener().getClass().getName());
        getServer().getPluginManager().registerEvents(this,this);
        fine="1".equals(System.getenv("T58_FINE_TRACE"));
        observation=getServer().getScheduler().runTaskTimer(this,this::observe,1,1);
    }
    private void observe() {
        captureMotion();
        if(fine||Bukkit.getCurrentTick()%5==0)sample();
    }
    private List<MobHost> hosts() throws ReflectiveOperationException {
        var hosts=new ArrayList<MobHost>();
        Object manager=field(LiveTestService.class,"manager");
        if(manager!=null)for(Object host:((Map<?,?>)field(manager,"tests")).values())hosts.add((MobHost)host);
        for(Object host:((Map<?,?>)field(plugin.sessionManager(),"sessions")).values())hosts.add((MobHost)host);
        return hosts;
    }
    private AbilityEngine engine(MobHost host) throws ReflectiveOperationException {
        if(host instanceof LiveTestService)return (AbilityEngine)field(host,"engine");
        return (AbilityEngine)field(field(host,"services"),"abilities");
    }
    private List<Map<String,Object>> effects(Object service,UUID id) throws ReflectiveOperationException {
        var result=new ArrayList<Map<String,Object>>();
        Object entries=((Map<?,?>)field(service,"owners")).get(id);
        if(entries instanceof Collection<?> all)for(Object e:all) {
            var data=new LinkedHashMap<String,Object>();data.put("id",field(e,"id"));
            if(service instanceof ControlService){data.put("applied",field(e,"applied"));int duration=0;try {duration=((AbilityContext)field(e,"ctx")).params().getInt("ticks");}catch(IllegalArgumentException ignored) {}data.put("duration",duration);data.put("ends",field(e,"ends"));data.put("players",((List<?>)field(e,"players")).stream().map(p->((Player)p).getName()).toList());}
            else {data.put("begun",field(e,"begun"));data.put("stun",field(e,"stun"));data.put("wall",field(e,"wall"));}
            result.add(data);
        }
        return result;
    }
    private Map<String,Object> position(LivingEntity e) {
        var at=e.getLocation();var data=new LinkedHashMap<String,Object>();
        data.put("uuid",e.getUniqueId().toString());data.put("x",at.getX());data.put("y",at.getY());data.put("z",at.getZ());
        data.put("yaw",at.getYaw());data.put("bodyYaw",e.getBodyYaw());data.put("pitch",at.getPitch());data.put("health",e.getHealth());
        if(e instanceof Player player){data.put("hand",player.getInventory().getItemInMainHand().getType().name());data.put("slot",player.getInventory().getHeldItemSlot());data.put("food",player.getFoodLevel());data.put("absorption",player.getAbsorptionAmount());}
        return data;
    }
    private Map<String,Object> motionStep(ActiveMob active,Mob mob,Location from,Location to,org.bukkit.util.Vector movement) {
        UUID id=mob.getUniqueId();movement=movement.clone().setY(0);
        var step=new LinkedHashMap<String,Object>();step.put("tick",Bukkit.getCurrentTick());
        step.put("x",to.getX());step.put("y",to.getY());step.put("z",to.getZ());step.put("bodyYaw",mob.getBodyYaw());
        step.put("advance",movement.length());
        var body=to.clone();body.setYaw(mob.getBodyYaw());var direction=body.getDirection().setY(0);
        step.put("facingDot",movement.lengthSquared()>.000001?direction.normalize().dot(movement.clone().normalize()):null);
        long age=Bukkit.getCurrentTick()-knocked.getOrDefault(id,-1000L);
        var impulse=knockVectors.get(id);var source=attackers.get(id);var raw=rawKnockVectors.get(id);
        double force=knockForces.getOrDefault(id,0d);
        double impulseDot=impulse==null||impulse.clone().setY(0).lengthSquared()<1e-4||movement.lengthSquared()<1e-6?0:movement.clone().normalize().dot(impulse.clone().setY(0).normalize());
        var away=source==null?null:from.toVector().subtract(source.toVector()).setY(0);
        double awayDot=away==null||away.lengthSquared()<1e-6||movement.lengthSquared()<1e-6?0:movement.clone().normalize().dot(away.normalize());
        boolean recoil=force>1e-6&&raw!=null&&raw.clone().setY(0).lengthSquared()>1e-6&&age>=0&&age<=10&&impulseDot>=.8&&awayDot>=.5&&impulse!=null&&movement.length()<=impulse.clone().setY(0).length()*1.5+.01;
        if(modernVectors.containsKey(id)){var modern=modernVectors.get(id);step.put("modernImpulse",List.of(modern.getX(),modern.getY(),modern.getZ()));}
        step.put("knockback",recoil);step.put("knockbackAge",age);step.put("impulseDot",impulseDot);step.put("awayDot",awayDot);step.put("knockbackForce",force);
        if(raw!=null)step.put("rawImpulse",List.of(raw.getX(),raw.getY(),raw.getZ()));
        if(impulse!=null)step.put("impulse",List.of(impulse.getX(),impulse.getY(),impulse.getZ()));
        if(source!=null)step.put("attacker",List.of(source.getX(),source.getY(),source.getZ()));
        step.put("retreat",MobMovement.retreating(active));return step;
    }
    /** Snapshot deltas remain diagnostics: host evaluation may already turn for the next action. */
    private void captureMotion() {
        for(var entry:observed.entrySet()) {
            ActiveMob active=entry.getValue();Mob mob=active.entity();if(!mob.isValid())continue;
            UUID id=entry.getKey();Location at=mob.getLocation(),old=motionPrevious.put(id,at);
            if(old==null||!old.getWorld().equals(at.getWorld()))continue;
            var movement=at.toVector().subtract(old.toVector());
            var step=motionStep(active,mob,old,at,movement);
            var teleport=teleports.remove(id);
            if(teleport!=null&&Bukkit.getCurrentTick()-teleport.tick()<=1)step.put("teleport",teleport.kind());
            motionWindows.computeIfAbsent(id,k->new ArrayList<>()).add(step);
            // No native move event in the preceding tick means an actual stationary interval.
            int previousTick=Bukkit.getCurrentTick()-1;
            if(lastPhysicalTick.getOrDefault(id,-1)!=previousTick) {
                var stationary=motionStep(active,mob,at,at,new org.bukkit.util.Vector());stationary.put("tick",previousTick);
                physicalWindows.computeIfAbsent(id,k->new ArrayList<>()).add(stationary);
            }
        }
    }
    /** Measure actual native movement and body orientation together, after the product's listener. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void physicalMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if(!(event.getEntity() instanceof Mob mob))return;
        var active=observed.get(mob.getUniqueId());if(active==null)return;
        var from=event.getFrom();var to=event.getTo();if(!from.getWorld().equals(to.getWorld()))return;
        var movement=to.toVector().subtract(from.toVector());double rawAdvance=movement.clone().setY(0).length();
        var teleport=physicalTeleports.remove(mob.getUniqueId());
        boolean jumped=teleport!=null&&Bukkit.getCurrentTick()-teleport.tick()>=0&&Bukkit.getCurrentTick()-teleport.tick()<=1
                &&teleport.from().getWorld().equals(from.getWorld())&&teleport.from().toVector().equals(from.toVector());
        if(jumped)movement.subtract(teleport.destination().toVector().subtract(teleport.from().toVector()));
        var step=motionStep(active,mob,to.clone().subtract(movement),to,movement);
        step.put("rawAdvance",rawAdvance);
        if(jumped){step.put("teleport",teleport.kind());step.put("teleportDelta",List.of(teleport.destination().getX()-teleport.from().getX(),teleport.destination().getY()-teleport.from().getY(),teleport.destination().getZ()-teleport.from().getZ()));}
        physicalWindows.computeIfAbsent(mob.getUniqueId(),k->new ArrayList<>()).add(step);
        lastPhysicalTick.put(mob.getUniqueId(),Bukkit.getCurrentTick());
    }
    private String discardCause(ActiveMob active,PendingSnapshot prior) {
        if(prior.instance()!=null&&!active.abilities().contains(prior.instance()))return "ability_replaced";
        AbilityContext ctx=prior.warning();if(ctx==null)return "service_cancelled_before_effect";
        double range=prior.instance()==null?Double.MAX_VALUE:prior.instance().range();
        for(String reach:List.of("reach","range","length"))try {range=Math.min(range,ctx.params().getDouble(reach));}catch(IllegalArgumentException ignored) {}
        for(LivingEntity target:ctx.targets()) {
            if(!target.isValid()||target.isDead())return "target_dead_or_invalid";
            if(!target.getWorld().equals(active.entity().getWorld()))return "target_changed_world";
            if(!ctx.session().players().contains(target))return "target_left_host";
            if(target.getLocation().distanceSquared(active.entity().getLocation())>range*range)return "target_out_of_range";
            if(target instanceof Player player&&(!player.isOnline()||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR))return "target_ineligible";
        }
        return "service_cancelled_before_effect";
    }
    @SuppressWarnings("unchecked") private void sample() {
        try {
            var hosts=hosts();boolean reorder=false;
            for(var host:hosts)if(orderedHosts.add(host))reorder=true;
            if(reorder) {
                // Hosts created after onEnable have later task IDs. Observe after their
                // existing evaluation compensates vanilla's base; never mutate product state.
                observation.cancel();observation=getServer().getScheduler().runTaskTimer(this,this::observe,1,1);return;
            }
            Set<UUID> seen=new HashSet<>();
            for(MobHost host:hosts)for(ActiveMob active:host.mobs()) {
                if(!active.template().id().startsWith("t58-")||!active.entity().isValid())continue;
                Mob mob=active.entity();UUID id=mob.getUniqueId();seen.add(id);
                if(observed.put(id,active)==null)motionPrevious.put(id,mob.getLocation());
                var data=new LinkedHashMap<String,Object>();data.put("tick",Bukkit.getCurrentTick());data.put("hostTick",host.scheduler().currentTick());
                data.put("sampleOrder","after_host_evaluation");data.put("host",host.getClass().getSimpleName());data.put("template",active.template().id());data.put("mob",position(mob));
                var speed=mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                data.put("speed",speed==null?null:speed.getValue());data.put("baseSpeed",speed==null?null:speed.getBaseValue());data.put("mobWidth",mob.getWidth());
                data.put("nativeStun",mob instanceof Ravager r?r.getStunnedTicks():0);data.put("nativeRoar",mob instanceof Ravager r?r.getRoarTicks():0);data.put("nativeAttack",mob instanceof Ravager r?r.getAttackTicks():0);
                data.put("ai",mob.hasAI());data.put("target",mob.getTarget()==null?null:mob.getTarget().getUniqueId().toString());
                var at=mob.getLocation();var old=previous.put(id,at);
                if(old!=null&&old.getWorld().equals(at.getWorld())) {
                    var movement=at.toVector().subtract(old.toVector()).setY(0);data.put("advance",movement.length());
                    var facing=at.getDirection().setY(0);data.put("rotationDot",movement.lengthSquared()>.0001?facing.normalize().dot(movement.clone().normalize()):null);
                    var body=at.clone();body.setYaw(mob.getBodyYaw());var bodyDirection=body.getDirection().setY(0);
                    data.put("facingDot",movement.lengthSquared()>.0001?bodyDirection.normalize().dot(movement.normalize()):null);
                }
                data.put("knockback",false);data.put("knockbackCause",knockCauses.get(id));var impulse=knockVectors.get(id);if(impulse!=null)data.put("knockbackVector",List.of(impulse.getX(),impulse.getY(),impulse.getZ()));
                data.put("retreat",MobMovement.retreating(active));data.put("warning",AbilityCasts.warning(active));
                data.put("paused",CombatService.paused(active));data.put("controlling",ControlService.controlling(active));
                data.put("controlEffects",effects(field(plugin,"controls"),id));data.put("combatEffects",effects(field(plugin,"combat"),id));
                var current=new HashMap<>((Map<String,Long>)field(active,"cooldowns"));var prior=cooldowns.put(id,current);
                var started=current.entrySet().stream().filter(e->prior==null||!Objects.equals(prior.get(e.getKey()),e.getValue())).map(Map.Entry::getKey).toList();
                data.put("started",started);
                data.put("comboCompleted",started.stream().filter(key->key.startsWith("combo:")).toList());
                data.put("phaseIndex",active.phaseIndex());data.put("healthFraction",MobHealth.fraction(mob));
                data.put("configuredAbilities",active.abilities().stream().map(a->a.abilityId()).toList());
                Object state=((Map<?,?>)field(engine(host),"states")).get(active);
                Map<?,?> pending=state==null?Map.of():(Map<?,?>)field(state,"pending");
                var snapshot=new HashMap<String,PendingSnapshot>();
                String casting=state==null?null:(String)field(state,"casting");
                AbilityContext warning=null;
                for(Object context:((Map<?,?>)field(AbilityCasts.class,"warnings")).keySet())if(((AbilityContext)context).caster()==active){warning=(AbilityContext)context;break;}
                for(var entry:pending.entrySet())snapshot.put((String)entry.getKey(),new PendingSnapshot((dev.dasan.customdungeons.model.AbilityInstance)field(entry.getValue(),"instance"),entry.getKey().equals(casting)?warning:null));
                var previousPending=pendingPrevious.put(id,snapshot);var discarded=new ArrayList<Map<String,Object>>();
                if(previousPending!=null)for(var entry:previousPending.entrySet())if(!pending.containsKey(entry.getKey())&&!started.contains(entry.getKey()))discarded.add(Map.of("ability",entry.getKey(),"cause",discardCause(active,entry.getValue())));
                data.put("discarded",discarded);
                data.put("pending",pending.keySet());data.put("pendingCause",AbilityCasts.warning(active)?"warning":CombatService.paused(active)?"combat_pause":ControlService.controlling(active)?"control":MobMovement.blocked(active)?"native_stun_or_roar":pending.isEmpty()?null:"waiting_for_eligible_target_or_service");
                data.put("players",host.players().stream().map(p->{var d=position(p);d.put("name",p.getName());d.put("control",ControlService.hasActiveControl(p));d.put("vehicle",p.getVehicle()==null?null:p.getVehicle().getType().name());d.put("vehicleY",p.getVehicle()==null?null:p.getVehicle().getLocation().getY());d.put("vehicleHeight",p.getVehicle()==null?null:p.getVehicle().getHeight());d.put("levitating",p.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION));return d;}).toList());
                Object effects=((Map<?,?>)field(field(plugin,"combat"),"owners")).get(id);
                var copies=new ArrayList<Map<String,Object>>();
                if(effects instanceof Collection<?> all)for(Object effect:all)for(Object entity:(List<?>)field(effect,"entities"))if(entity instanceof Mob copy&&CombatService.isDecoy(copy)){var d=position(copy);d.put("ai",copy.hasAI());d.put("target",copy.getTarget()==null?null:copy.getTarget().getUniqueId().toString());copies.add(d);}
                data.put("decoys",copies);
                data.put("motionSteps",motionWindows.remove(id));data.put("physicalSteps",physicalWindows.remove(id));
                data.put("summons",host.mobs().stream().filter(m->m.template().id().equals(active.template().id().substring(0,10)+"-ally")&&m.entity().isValid()).count());
                String quiet=AbilityCasts.warning(active)?"warning":CombatService.paused(active)?"combat_pause":ControlService.controlling(active)?"control":
                        mob instanceof Ravager ravager&&ravager.getStunnedTicks()>0?"native_stun":mob instanceof Ravager ravager&&ravager.getRoarTicks()>0?"native_roar":null;
                if(quiet==null&&mob.getTarget()!=null&&mob.getTarget().getLocation().distanceSquared(at)<=Math.pow(mob.getWidth()/2+2.2,2))quiet="melee";
                data.put("quietCause",quiet);
                getLogger().info("T58_TRACE "+json.toJson(data));
            }
            previous.keySet().retainAll(seen);cooldowns.keySet().retainAll(seen);knocked.keySet().retainAll(seen);knockCauses.keySet().retainAll(seen);knockVectors.keySet().retainAll(seen);
            observed.keySet().retainAll(seen);motionPrevious.keySet().retainAll(seen);motionWindows.keySet().retainAll(seen);
            physicalWindows.keySet().retainAll(seen);lastPhysicalTick.keySet().retainAll(seen);physicalTeleports.keySet().retainAll(seen);knockForces.keySet().retainAll(seen);rawKnockVectors.keySet().retainAll(seen);
            pendingPrevious.keySet().retainAll(seen);attackers.keySet().retainAll(seen);attackTicks.keySet().retainAll(seen);teleports.keySet().retainAll(seen);legacyTicks.keySet().retainAll(seen);modernVectors.keySet().retainAll(seen);
        } catch(Exception failure) {getLogger().severe("T58_PROBE_ERROR "+failure);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void use(org.bukkit.event.player.PlayerInteractEvent event) {
        if(!event.getPlayer().getName().startsWith("T58")||event.getItem()==null)return;
        getLogger().info("T58_USE "+json.toJson(Map.of("player",event.getPlayer().getName(),"item",event.getItem().getType().name(),"action",event.getAction().name(),"use",event.useItemInHand().name())));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void consume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if(event.getPlayer().getName().startsWith("T58"))getLogger().info("T58_CONSUME "+json.toJson(Map.of("player",event.getPlayer().getName(),"item",event.getItem().getType().name(),"cancelled",event.isCancelled())));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof Player player)||event.getDamage()<=0)return;
        var damager=event.getDamager();String template=damager.getPersistentDataContainer().get(MobKeys.TEMPLATE,org.bukkit.persistence.PersistentDataType.STRING);
        boolean decoy=CombatService.isDecoySource(damager);
        try {if(decoy) {
            Object effect=((Map<?,?>)field(field(plugin,"combat"),"auxiliaries")).get(damager.getUniqueId());
            if(effect!=null)template=((AbilityContext)field(effect,"ctx")).caster().template().id();
        }}catch(ReflectiveOperationException error){getLogger().severe("T58_PROBE_ERROR "+error);}
        if(template==null||!template.startsWith("t58-"))return;
        getLogger().info("T58_HIT "+json.toJson(Map.of("tick",Bukkit.getCurrentTick(),"template",template,"player",player.getName(),"damage",event.getFinalDamage(),"rawDamage",event.getDamage(),"cause",event.getCause().name(),"decoy",decoy)));
    }
    private static List<Number> location(Location at) {return List.of(at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void playerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if(!event.getPlayer().getName().startsWith("T58")||!ControlService.hasActiveControl(event.getPlayer()))return;
        try {
            Object correction=((Map<?,?>)field(field(plugin,"controls"),"corrections")).get(event.getPlayer().getUniqueId());
            var data=new LinkedHashMap<String,Object>();data.put("controlStack",stacks.walk(frames->frames.filter(frame->frame.getClassName().equals(ControlService.class.getName())).map(StackWalker.StackFrame::getMethodName).toList()));data.put("tick",Bukkit.getCurrentTick());data.put("cause",event.getCause().name());data.put("player",event.getPlayer().getName());data.put("from",location(event.getFrom()));data.put("to",location(event.getTo()));
            if(correction!=null){data.put("expectedFrom",location((Location)field(correction,"from")));data.put("expectedTo",location((Location)field(correction,"to")));data.put("expectedTick",field(correction,"tick"));}
            getLogger().info("T58_PLAYER_TP "+json.toJson(data));
        }catch(Exception failure){getLogger().severe("T58_PROBE_ERROR "+failure);}
    }
    /** Actual public teleport event distinguishes discontinuities from physical movement. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(EntityTeleportEvent event) {
        if(!(event.getEntity() instanceof Mob mob)||!mob.getPersistentDataContainer().has(MobKeys.TEMPLATE)||event.getTo()==null)return;
        String kind=stacks.walk(frames->{
            boolean ability=false;var iterator=frames.iterator();
            while(iterator.hasNext()) {
                var frame=iterator.next();
                if(frame.getClassName().equals("dev.dasan.customdungeons.session.DungeonSessionRuntime")&&frame.getMethodName().equals("tick"))return "host_return";
                if(frame.getClassName().equals("dev.dasan.customdungeons.ability.combat.CombatService"))ability=true;
            }
            return ability?"ability":"external";
        });
        var from=event.getFrom().clone();var prior=physicalTeleports.get(mob.getUniqueId());
        if(prior!=null&&prior.tick()==Bukkit.getCurrentTick()&&prior.destination().getWorld().equals(from.getWorld())&&prior.destination().toVector().equals(from.toVector()))from=prior.from();
        var step=new TeleportStep(Bukkit.getCurrentTick(),from,event.getTo().clone(),kind);
        teleports.put(mob.getUniqueId(),step);physicalTeleports.put(mob.getUniqueId(),step);
        getLogger().info("T58_TELEPORT "+json.toJson(Map.of("tick",Bukkit.getCurrentTick(),"mob",mob.getUniqueId().toString(),"kind",kind,"from",List.of(event.getFrom().getX(),event.getFrom().getY(),event.getFrom().getZ()),"to",List.of(event.getTo().getX(),event.getTo().getY(),event.getTo().getZ()))));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void legacyImpulse(org.bukkit.event.entity.EntityKnockbackEvent event) {
        if(event.getEntity() instanceof Mob mob&&event.getFinalKnockback().lengthSquared()>.0001&&mob.getPersistentDataContainer().has(MobKeys.TEMPLATE)) {
            knockForces.put(mob.getUniqueId(),event.getForce());rawKnockVectors.put(mob.getUniqueId(),event.getKnockback().clone());attackers.remove(mob.getUniqueId());knocked.put(mob.getUniqueId(),(long)Bukkit.getCurrentTick());knockCauses.put(mob.getUniqueId(),event.getCause().name());knockVectors.put(mob.getUniqueId(),event.getFinalKnockback().clone());legacyTicks.put(mob.getUniqueId(),(long)Bukkit.getCurrentTick());
            if(event instanceof org.bukkit.event.entity.EntityKnockbackByEntityEvent hit) {attackers.put(mob.getUniqueId(),hit.getSourceEntity().getLocation().clone());attackTicks.put(mob.getUniqueId(),(long)Bukkit.getCurrentTick());}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if(event.getKnockback().lengthSquared()>.0001&&event.getEntity() instanceof Mob mob&&mob.getPersistentDataContainer().has(MobKeys.TEMPLATE)) {
            modernVectors.put(mob.getUniqueId(),event.getKnockback().clone());
            // Generic PUSH lacks an attacker; it never grants the recoil exception.
            // Bukkit's applied final velocity and its source are the independent evidence.

        }
    }
}

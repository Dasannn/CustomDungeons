import com.google.gson.Gson;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.ability.zone.*;
import org.bukkit.persistence.PersistentDataType;
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
    private T58Arena arena;
    private final Map<String,Mob> nativeReferences=new HashMap<>();
    private final Map<UUID,Location> nativePrevious=new HashMap<>();
    private final Map<Object,Long> warningSince=new WeakHashMap<>();
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
        arena=new T58Arena(this);
        plugin=(CustomDungeonsPlugin)getServer().getPluginManager().getPlugin("CustomDungeons");
        getLogger().info("T58_RUNTIME_CPUS "+Runtime.getRuntime().availableProcessors());
        for(var listener:org.bukkit.event.player.PlayerInteractEvent.getHandlerList().getRegisteredListeners())
            getLogger().info("T58_USE_HANDLER "+listener.getPriority()+" "+listener.getPlugin().getName()+" "+listener.getListener().getClass().getName());
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getCommandMap().register("t58probe",new org.bukkit.command.Command("t58probe") {
            @Override public boolean execute(org.bukkit.command.CommandSender sender,String label,String[] args) {
                if(!(sender instanceof org.bukkit.command.ConsoleCommandSender))return false;
                if(args.length==3&&Set.of("native-husk","native-corner","native-return","native-ramp").contains(args[0])&&args[1].matches("t58b-[0-9a-f]{6}")
                        &&args[2].matches("T58"+args[1].substring(5)+"[0-4]")&&arena.ownedBy(args[1])) {
                    Player target=getServer().getPlayerExact(args[2]);
                    if(target==null||nativeReferences.containsKey(args[1]))return false;
                    var center=arena.recipe.center();
                    boolean corner=args[0].equals("native-corner"),returning=args[0].equals("native-return"),ramp=args[0].equals("native-ramp");
                    var at=new Location(Bukkit.getWorld(arena.recipe.world()),center[0]+(ramp?9.7:returning?2.773:corner?-11.452:-3.703),center[1]-(corner||returning||ramp?0:3),center[2]+(ramp?-.32:returning?.648:corner?5.3:10.528));
                    Mob reference=returning?at.getWorld().spawn(at,Skeleton.class,entity->{entity.setPersistent(false);entity.setRemoveWhenFarAway(false);}):at.getWorld().spawn(at,Husk.class,entity->{entity.setPersistent(false);entity.setRemoveWhenFarAway(false);});
                    nativeReferences.put(args[1],reference);
                    if(!returning)reference.setTarget(target);
                    var path=reference.getPathfinder().findPath(target);
                    var data=new LinkedHashMap<String,Object>();data.put("owner",args[1]);data.put("uuid",reference.getUniqueId());
                    data.put("mob",position(reference));data.put("target",position(target));data.put("pathFound",path!=null);
                    if(path!=null){data.put("pathReachesTarget",path.canReachFinalPoint());var end=path.getFinalPoint();if(end!=null)data.put("pathEnd",List.of(end.getX(),end.getY(),end.getZ()));}
                    getLogger().info("T58_NATIVE_READY "+json.toJson(data));return true;
                }
                if(args.length==3&&args[0].equals("arena")&&args[1].matches("t58b-[0-9a-f]{6}")
                        &&Set.of("room","live","warden","irregular","irregular-warden","irregular-native","restore","restore-flat").contains(args[2])) {
                    if(args[2].equals("restore-flat"))getLogger().info("T58_ARENA_RESTORED "+json.toJson(Map.of("owner",args[1],"differentBlocks",arena.restoreFlat(args[1]),"basis","legacy-flat-fixture")));
                    else if(args[2].equals("restore"))getLogger().info("T58_ARENA_RESTORED "+json.toJson(Map.of("owner",args[1],"differentBlocks",arena.restoreFor(args[1]))));
                    else {arena.build(args[1],args[2]);getLogger().info("T58_ARENA_READY "+json.toJson(Map.of("owner",args[1],"version",1,"mode",args[2],"journal",arena.original.size(),"features",arena.recipe.features())));}
                    return true;
                }
                if(args.length!=2)return false;
                if(args[0].equals("native-stop")&&args[1].matches("t58b-[0-9a-f]{6}")) {
                    Mob reference=nativeReferences.remove(args[1]);if(reference!=null){nativePrevious.remove(reference.getUniqueId());reference.remove();}
                    getLogger().info("T58_NATIVE_CLEANUP "+json.toJson(Map.of("owner",args[1],"remaining",reference!=null&&reference.isValid()?1:0)));return true;
                }
                if(args[0].equals("cleanup")&&args[1].matches("t58b?-[0-9a-f]{6}")) {cleanupEvidence(args[1]);return true;}
                if(args[0].equals("aim")&&args[1].matches("T58[0-9a-f]{6}[0-4]")) {
                    Player player=getServer().getPlayerExact(args[1]);if(player==null)return false;
                    var data=position(player);data.put("player",player.getName());
                    var hit=player.rayTraceBlocks(32);
                    if(hit!=null&&hit.getHitBlock()!=null) {var block=hit.getHitBlock();data.put("hitX",block.getX());data.put("hitY",block.getY());data.put("hitZ",block.getZ());}
                    getLogger().info("T58_AIM "+json.toJson(data));return true;
                }
                return false;
            }
        });
        fine="1".equals(System.getenv("T58_FINE_TRACE"));
        observation=getServer().getScheduler().runTaskTimer(this,this::observe,1,1);
    }
    @Override public void onDisable() {
        nativeReferences.values().forEach(Entity::remove);nativeReferences.clear();nativePrevious.clear();
        if(arena!=null&&!arena.original.isEmpty())getLogger().info("T58_ARENA_RESTORED "+json.toJson(Map.of("differentBlocks",arena.restore(true))));
    }
    private void observe() {
        captureMotion();
        if(fine||Bukkit.getCurrentTick()%5==0){sample();sampleNative();}
    }
    private void sampleNative() {
        for(var entry:nativeReferences.entrySet()) {
            Mob mob=entry.getValue();if(!mob.isValid())continue;
            var at=mob.getLocation();var old=nativePrevious.put(mob.getUniqueId(),at);
            var data=new LinkedHashMap<String,Object>();data.put("owner",entry.getKey());data.put("tick",Bukkit.getCurrentTick());data.put("mob",position(mob));
            data.put("advance",old==null?0:at.toVector().subtract(old.toVector()).setY(0).length());data.put("ai",mob.hasAI());data.put("hasPath",mob.getPathfinder().hasPath());
            data.put("marked",mob.getPersistentDataContainer().has(MobKeys.SESSION,PersistentDataType.STRING)||mob.getPersistentDataContainer().has(MobKeys.TEMPLATE,PersistentDataType.STRING));
            var followRange=mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            data.put("followRange",followRange==null?0:followRange.getValue());
            data.put("nearestPlayer",at.getWorld().getPlayers().stream().filter(p->p.getName().matches("T58[0-9a-f]{6}[0-4]")).mapToDouble(p->p.getLocation().distance(at)).min().orElse(Double.MAX_VALUE));
            if(mob.getTarget()!=null){data.put("target",position(mob.getTarget()));data.put("lineOfSight",mob.hasLineOfSight(mob.getTarget()));
                data.put("quietCause",mob.getTarget().getLocation().distanceSquared(at)<=Math.pow(mob.getWidth()/2+2.2,2)?"melee":null);}
            getLogger().info("T58_NATIVE_TRACE "+json.toJson(data));
        }
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
            else {data.put("begun",field(e,"begun"));data.put("stun",field(e,"stun"));data.put("wall",field(e,"wall"));if("silence".equals(field(e,"id")))data.put("mutedPlayers",((Collection<?>)field(e,"muted")).stream().map(p->((Player)p).getName()).toList());}
            result.add(data);
        }
        return result;
    }
    private Map<String,Object> position(LivingEntity e) {
        var at=e.getLocation();var data=new LinkedHashMap<String,Object>();
        data.put("uuid",e.getUniqueId().toString());data.put("x",at.getX());data.put("y",at.getY());data.put("z",at.getZ());
        data.put("onGround",e.isOnGround());data.put("yaw",at.getYaw());data.put("bodyYaw",e.getBodyYaw());data.put("pitch",at.getPitch());data.put("health",e.getHealth());
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
        step.put("retreat",MobMovement.retreating(active));combatFacing(step,active,mob,to);return step;
    }
    private void combatFacing(Map<String,Object> sample,ActiveMob active,Mob mob,Location at) {
        sample.put("combatFacing",MobMovement.combatFacing(active));
        var target=mob.getTarget();var body=at.clone();body.setYaw(mob.getBodyYaw());
        var toward=target==null?null:target.getLocation().toVector().subtract(at.toVector()).setY(0);
        sample.put("targetFacingDot",toward==null?null:toward.lengthSquared()<1e-6?1:body.getDirection().setY(0).normalize().dot(toward.normalize()));
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
                if(!active.template().id().matches("t58b?-[0-9a-f]{6}-.+")||!active.entity().isValid())continue;
                Mob mob=active.entity();UUID id=mob.getUniqueId();seen.add(id);
                if(observed.put(id,active)==null)motionPrevious.put(id,mob.getLocation());
                var data=new LinkedHashMap<String,Object>();data.put("tick",Bukkit.getCurrentTick());data.put("hostTick",host.scheduler().currentTick());
                data.put("sampleOrder","after_host_evaluation");data.put("host",host.getClass().getSimpleName());data.put("template",active.template().id());data.put("mob",position(mob));
                var speed=mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                data.put("speed",speed==null?null:speed.getValue());data.put("baseSpeed",speed==null?null:speed.getBaseValue());data.put("mobWidth",mob.getWidth());
                var followRange=mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
                data.put("followRange",followRange==null?null:followRange.getValue());
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
                combatFacing(data,active,mob,at);
                data.put("paused",CombatService.paused(active));data.put("controlling",ControlService.controlling(active));
                data.put("lastDiscarded",engine(host).lastDiscarded(active));
                data.put("learningVisible",dev.dasan.customdungeons.intelligence.IntelligenceService.learningVisible(active));
                var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(active);
                data.put("adaptations",brain==null?List.of():brain.active().stream().map(a->Map.of("id",a.rule().id(),"begins",a.begins(),"expires",a.expires())).toList());
                var customName=mob.customName();data.put("customName",customName==null?null:net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(customName));
                sampleZones(active);
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
                data.put("summons",host.mobs().stream().filter(m->m.template().id().equals(active.template().id().replaceFirst("^(t58b?-[0-9a-f]{6}).*","$1")+"-ally")&&m.entity().isValid()).count());
                String quiet=AbilityCasts.warning(active)?"warning":CombatService.paused(active)?"combat_pause":ControlService.controlling(active)?"control":
                        mob instanceof Ravager ravager&&ravager.getStunnedTicks()>0?"native_stun":mob instanceof Ravager ravager&&ravager.getRoarTicks()>0?"native_roar":null;
                if(quiet==null&&mob.getTarget()!=null&&mob.getTarget().getLocation().distanceSquared(at)<=Math.pow(mob.getWidth()/2+2.2,2))quiet="melee";
                data.put("quietCause",quiet);
                if(quiet==null&&(data.get("advance") instanceof Number advance)&&advance.doubleValue()<.01) {
                    var bounds=mob.getBoundingBox().clone().expand(.1);
                    var obstacles=new ArrayList<List<Object>>();
                    for(var block:temporary.getOrDefault(prefix(active.template().id()),Set.of()))if(block.getType()==Material.STONE_BRICKS
                            &&bounds.overlaps(new org.bukkit.util.BoundingBox(block.getX(),block.getY(),block.getZ(),block.getX()+1,block.getY()+1,block.getZ()+1)))
                        obstacles.add(List.of(block.getX(),block.getY(),block.getZ(),block.getType().name()));
                    data.put("touchingTemporary",obstacles);data.put("hasPath",mob.getPathfinder().hasPath());
                    var path=mob.getPathfinder().getCurrentPath();
                    if(path!=null){data.put("pathReachesTarget",path.canReachFinalPoint());data.put("pathNextIndex",path.getNextPointIndex());
                        var next=path.getNextPoint();if(next!=null)data.put("pathNext",List.of(next.getX(),next.getY(),next.getZ()));
                        data.put("pathPoints",path.getPoints().stream().map(p->List.of(p.getX(),p.getY(),p.getZ())).toList());}
                }
                if(host.area()!=null)data.put("area",List.of(host.area().bounds().getMinX(),host.area().bounds().getMinZ(),host.area().bounds().getMaxX(),host.area().bounds().getMaxZ()));
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
        if(template==null||!template.matches("t58b?-[0-9a-f]{6}-.+"))return;
        getLogger().info("T58_HIT "+json.toJson(Map.of("tick",Bukkit.getCurrentTick(),"template",template,"player",player.getName(),"damage",event.getFinalDamage(),"rawDamage",event.getDamage(),"cause",event.getCause().name(),"decoy",decoy)));
    }
    private final Map<String,Set<String>> encounters=new HashMap<>();
    private final Map<String,Set<org.bukkit.block.Block>> temporary=new HashMap<>();
    private final Set<UUID> spawnSeen=new HashSet<>();
    private final Map<String,List<Location>> buffAnchors=new HashMap<>();
    private final Map<String,List<Location>> riftAnchors=new HashMap<>();
    private String prefix(String template) {return template.replaceFirst("^(t58b?-[0-9a-f]{6}).*","$1");}
    private void emitEffect(AbilityContext ctx,String ability,String kind,Entity subject,boolean cancelled) {
        emitEffect(ctx,ability,kind,subject,cancelled,subject.getLocation());
    }
    private void emitEffect(AbilityContext ctx,String ability,String kind,Entity subject,boolean cancelled,Location at) {
        if(!ctx.caster().template().id().startsWith("t58b-"))return;
        var data=new LinkedHashMap<String,Object>();
        data.put("tick",Bukkit.getCurrentTick());data.put("template",ctx.caster().template().id());data.put("ability",ability);
        data.put("kind",kind);data.put("subject",subject.getUniqueId().toString());data.put("cancelled",cancelled);
        data.put("x",at.getX());data.put("y",at.getY());data.put("z",at.getZ());
        data.put("inside",ctx.session().area()==null||ctx.session().area().contains(at));
        int floorReferenceY=ctx.caster().entity().getLocation().getBlockY();
        if(ability.equals("arrow_rain"))try {
            Object zones=((Map<?,?>)field(field(plugin,"zones"),"owners")).get(ctx.caster().entity().getUniqueId());
            boolean found=false;
            if(zones instanceof Collection<?> all)for(Object zone:all)if(field(zone,"id").equals("arrow_rain")) {
                floorReferenceY=((Location)field(zone,"center")).getBlockY();found=true;break;
            }
            if(!found)throw new IllegalStateException("Arrow birth without its fixed rain anchor");
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        data.put("floorReferenceY",floorReferenceY);
        var floor=SafeTerrain.interior(at.getWorld(),at.getBlockX(),at.getBlockZ(),floorReferenceY,.2,1,ctx.session().area());
        data.put("safeGround",floor.isPresent()&&Math.abs(floor.get().getY()-at.getY())<1e-6
                &&SafeTerrain.clearBody(at,Math.max(.2,subject.getWidth()),Math.max(.2,subject.getHeight())));
        if(Set.of("rift","boss_totem","decoys","blink_behind").contains(ability))
            data.put("walkableExit",SafeTerrain.hasWalkableExit(at,Math.max(.6,subject.getWidth()),Math.max(1,subject.getHeight()),ctx.session().area(),arena.recipe.center()[1]));
        if(floor.isPresent()) {data.put("floorY",floor.get().getY());data.put("ceilingY",SafeTerrain.ceiling(floor.get(),.2,20));}
        getLogger().info("T58_ZONE_EFFECT "+json.toJson(data));
    }
    private void sampleZones(ActiveMob active) throws ReflectiveOperationException {
        if(!active.template().id().startsWith("t58b-"))return;
        var template=active.template().id();var run=prefix(template);
        encounters.computeIfAbsent(run,k->new HashSet<>()).add(active.session().id().toString());
        var zEntries=((Map<?,?>)field(field(plugin,"zones"),"owners")).get(active.entity().getUniqueId());
        var anchors=new IdentityHashMap<AbilityContext,Set<UUID>>();
        var portals=new ArrayList<Location>();
        if(zEntries instanceof Collection<?> zones)for(Object zone:zones) {
            var id=(String)field(zone,"id");var center=(Location)field(zone,"center");
            warningSince.putIfAbsent(zone,System.currentTimeMillis()-50L*((int)field(zone,"warning")-(int)field(zone,"left")+(int)field(zone,"elapsed"))-350);
            if(id.equals("falling_pillars")&&!(boolean)field(zone,"started"))anchors.computeIfAbsent((AbilityContext)field(zone,"ctx"),key->new HashSet<>()).add((UUID)field(zone,"anchorPlayer"));
            if(id.equals("rift"))portals.add(center);
            for(Object block:(List<?>)field(zone,"blocks"))temporary.computeIfAbsent(run,k->new HashSet<>()).add((org.bukkit.block.Block)block);
            for(Object shot:(Set<?>)field(zone,"shots")) {
                var arrow=(Arrow)field(shot,"arrow");
                if(spawnSeen.add(arrow.getUniqueId()))emitEffect((AbilityContext)field(zone,"ctx"),id,"spawn",arrow,false);
            }
        }
        if(!anchors.isEmpty()) {
            long count=anchors.values().stream().mapToInt(Set::size).max().orElse(0);
            getLogger().info("T58_PILLARS "+json.toJson(Map.of("template",template,"players",count)));
        }
        riftAnchors.put(template,portals);
        var effects=((Map<?,?>)field(field(plugin,"combat"),"owners")).get(active.entity().getUniqueId());
        var buff=new ArrayList<Location>();
        if(effects instanceof Collection<?> all)for(Object effect:all) {
            var id=(String)field(effect,"id");var ctx=(AbilityContext)field(effect,"ctx");
            warningSince.putIfAbsent(effect,System.currentTimeMillis()-50L*((int)field(effect,"warning")-(int)field(effect,"left")+(int)field(effect,"elapsed"))-350);
            if(id.equals("buff_steal"))buff.add(((Location)field(effect,"targetAnchor")).clone());
            if(id.equals("purge"))buff.add(((Location)field(effect,"origin")).clone());
            for(Object item:(List<?>)field(effect,"entities"))if(item instanceof Entity entity&&spawnSeen.add(entity.getUniqueId()))emitEffect(ctx,id,"spawn",entity,false);
        }
        buffAnchors.put(template,buff);
    }
    private void cleanupEvidence(String prefix) {
        try {
            int remaining=0;
            for(World world:getServer().getWorlds())for(Chunk chunk:world.getLoadedChunks())for(Entity entity:chunk.getEntities()) {
                String owner=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
                if(owner!=null&&encounters.getOrDefault(prefix,Set.of()).contains(owner))remaining++;
            }
            long blocks=temporary.getOrDefault(prefix,Set.of()).stream().filter(b->!b.isEmpty()).count();
            getLogger().info("T58_CLEANUP "+json.toJson(Map.of("prefix",prefix,"entities",remaining,"blocks",blocks,
                    "zones",((ZoneService)field(plugin,"zones")).activeCount(),"combat",((CombatService)field(plugin,"combat")).activeCount())));
            if(blocks==0&&remaining==0)temporary.remove(prefix);
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void zoneSpawn(EntitySpawnEvent event) {
        Entity entity=event.getEntity();String owner=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if(owner==null)return;
        try {
            for(var host:hosts())if(host.id().toString().equals(owner))for(var active:host.mobs())if(active.template().id().startsWith("t58b-")) {
                if(entity instanceof Arrow&&entity.getPersistentDataContainer().has(ZoneService.ARROW,PersistentDataType.BYTE)) {
                    if(spawnSeen.add(entity.getUniqueId()))emitEffect(new AbilityContext(active,List.of(),new ParamValues(Map.of(),List.of()),host,null),"arrow_rain","spawn",entity,false);
                } else {
                    Object auxiliary=((Map<?,?>)field(field(plugin,"combat"),"auxiliaries")).get(entity.getUniqueId());
                    if(auxiliary!=null&&spawnSeen.add(entity.getUniqueId()))emitEffect((AbilityContext)field(auxiliary,"ctx"),(String)field(auxiliary,"id"),"spawn",entity,false);
                }
            }
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
    }
    /** Actual damage boundary, distinct from melee; native shield resolution is preserved. */
    @EventHandler(priority=EventPriority.MONITOR) public void zoneDamage(EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player player)||!player.getName().startsWith("T58"))return;
        try {
            Object zones=field(plugin,"zones"),combat=field(plugin,"combat"),effect=field(zones,"damageZone");boolean zone=effect!=null;
            if(effect==null&&event instanceof EntityDamageByEntityEvent hit) {
                Object shot=((Map<?,?>)field(zones,"arrows")).get(hit.getDamager().getUniqueId());
                if(shot!=null){effect=field(shot,"zone");zone=true;}
            }
            if(effect==null)effect=field(combat,"damageEffect");
            if(effect==null&&event instanceof EntityDamageByEntityEvent hit&&CombatService.isDecoy(hit.getDamager()))effect=((Map<?,?>)field(combat,"auxiliaries")).get(hit.getDamager().getUniqueId());
            if(effect==null)return;
            var ctx=(AbilityContext)field(effect,"ctx");String template=ctx.caster().template().id();if(!template.startsWith("t58b-"))return;
            String ability=(String)field(effect,"id");var at=player.getLocation();
            boolean inside=ctx.session().area()==null||ctx.session().area().contains(at);
            List<List<Location>> polygons=new ArrayList<>();
            boolean geometryRequired=zone||Set.of("charge","interruptible_ultimate").contains(ability);
            if(zone) {
                if(ability.equals("cracked_floor")) {
                    for(var polygon:((Map<?,List<Location>>)field(effect,"tileFootprints")).values())
                        if(AreaOutline.contains(polygon,at.getX(),at.getZ()))polygons.add(polygon);
                } else polygons.add((List<Location>)field(effect,ability.equals("vortex")?"burstFootprint":"footprint"));
                inside&=polygons.stream().anyMatch(polygon->AreaOutline.contains(polygon,at.getX(),at.getZ()));
            } else {
                var polygon=(List<Location>)field(effect,"warningOutline");
                if(polygon!=null)polygons.add(polygon);
                if(geometryRequired)inside&=polygons.stream().anyMatch(p->AreaOutline.contains(p,at.getX(),at.getZ()));
            }
            long since=warningSince.getOrDefault(effect,System.currentTimeMillis()-50L*((int)field(effect,"warning")+(int)field(effect,"elapsed"))-350);
            boolean shield=event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)&&event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)<0;
            var data=new LinkedHashMap<String,Object>();data.put("template",template);data.put("ability",ability);data.put("player",player.getName());
            data.put("inside",inside);data.put("at",System.currentTimeMillis());data.put("since",since);
            data.put("x",at.getX());data.put("y",at.getY());data.put("z",at.getZ());data.put("geometryRequired",geometryRequired);
            data.put("polygons",polygons.stream().map(p->p.stream().map(l->List.of(l.getX(),l.getY(),l.getZ())).toList()).toList());
            data.put("damage",event.getFinalDamage());
            data.put("absorbed",event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)?Math.max(0,-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)):0);
            data.put("cancelled",event.isCancelled());data.put("shield",shield);
            getLogger().info("T58_ZONE_HIT "+json.toJson(data));
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void zonePotion(EntityPotionEffectEvent event) {
        if(!(event.getEntity() instanceof Player player)||!player.getName().startsWith("T58"))return;
        try {
            for(var host:hosts())for(var active:host.mobs())if(active.template().id().startsWith("t58b-")) {
                String id=active.abilities().isEmpty()?"":active.abilities().getFirst().abilityId();
                boolean relevant=id.equals("inverted_gravity")&&event.getNewEffect()!=null&&!event.isCancelled()&&event.getModifiedType()==org.bukkit.potion.PotionEffectType.LEVITATION;
                relevant|=Set.of("purge","buff_steal").contains(id)&&event.getAction()==EntityPotionEffectEvent.Action.REMOVED&&event.getCause()==EntityPotionEffectEvent.Cause.PLUGIN
                        &&buffAnchors.getOrDefault(active.template().id(),List.of()).stream().anyMatch(at->at.distanceSquared(player.getLocation())<=Math.pow(id.equals("purge")?8:1,2));
                if(relevant)emitEffect(new AbilityContext(active,List.of(player),new ParamValues(Map.of(),List.of()),host,null),id,id.equals("inverted_gravity")?"LEVITATION":"buff_removed",player,event.isCancelled());
            }
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void zonePlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if(!event.getPlayer().getName().startsWith("T58")||event.getCause()!=org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN||event.getTo()==null)return;
        try {for(var host:hosts())for(var active:host.mobs())if(active.template().id().startsWith("t58b-")&&active.abilities().stream().anyMatch(a->a.abilityId().equals("rift"))
                &&riftAnchors.getOrDefault(active.template().id(),List.of()).stream().anyMatch(at->Math.pow(at.getX()-event.getFrom().getX(),2)+Math.pow(at.getZ()-event.getFrom().getZ(),2)<=1.01&&Math.abs(at.getY()-event.getFrom().getY())<=4))emitEffect(new AbilityContext(active,List.of(event.getPlayer()),new ParamValues(Map.of(),List.of()),host,null),"rift","teleport",event.getPlayer(),event.isCancelled(),event.getTo());
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void zoneUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if(!event.getPlayer().getName().startsWith("T58")||event.getItem()==null||!event.getItem().getType().isEdible()||event.useItemInHand()!=Event.Result.DENY)return;
        try {Object effects=((Map<?,?>)field(field(plugin,"combat"),"silenced")).get(event.getPlayer().getUniqueId());
            if(effects instanceof Collection<?> all)for(Object effect:all)emitEffect((AbilityContext)field(effect,"ctx"),"silence","use_cancelled",event.getPlayer(),true);
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
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
        if(kind.equals("ability"))try {
            Object effects=((Map<?,?>)field(field(plugin,"combat"),"owners")).get(mob.getUniqueId());
            if(effects instanceof Collection<?> all)for(Object effect:all) {
                String ability=(String)field(effect,"id");
                if(Set.of("decoys","blink_behind").contains(ability))emitEffect((AbilityContext)field(effect,"ctx"),ability,"teleport",mob,false,event.getTo());
            }
        }catch(Exception error){getLogger().severe("T58_PROBE_ERROR "+error);}
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

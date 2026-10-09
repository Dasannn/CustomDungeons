import java.nio.file.*;
import java.util.*;
import me.lucko.spark.paper.proto.SparkSamplerProtos;

/** Offline analysis using the exact protobuf classes shipped by the agents' spark jar. */
class SparkZoneProfile {
    record Result(int ticks,double plugin,double zones) {}
    static double walk(int index,List<SparkSamplerProtos.StackTraceNode> nodes,String prefix) {
        var node=nodes.get(index);
        if(node.getClassName().startsWith(prefix))return node.getTimesList().stream().mapToDouble(Double::doubleValue).sum();
        return node.getChildrenRefsList().stream().mapToDouble(child->walk(child,nodes,prefix)).sum();
    }
    static Result read(Path path) throws Exception {
        var data=SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(path));
        var thread=data.getThreadsList().stream().filter(t->t.getName().equals("Server thread")).findFirst().orElseThrow();
        int ticks=data.getMetadata().getNumberOfTicks();if(ticks<=0)throw new IllegalArgumentException("Profile contains no server ticks");
        var nodes=thread.getChildrenList();
        double plugin=thread.getChildrenRefsList().stream().mapToDouble(i->walk(i,nodes,"dev.dasan.customdungeons.")).sum()/ticks;
        double zones=thread.getChildrenRefsList().stream().mapToDouble(i->walk(i,nodes,"dev.dasan.customdungeons.ability.zone.")).sum()/ticks;
        return new Result(ticks,plugin,zones);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected baseline.sparkprofile active.sparkprofile");
        Result baseline=read(Path.of(args[0])),active=read(Path.of(args[1]));
        double extra=Math.max(0,active.plugin()-baseline.plugin());
        System.out.printf(Locale.ROOT,"{\"baselineTicks\":%d,\"activeTicks\":%d,\"baselinePluginMsPerTick\":%.6f,\"activePluginMsPerTick\":%.6f,\"extraMsPerTick\":%.6f,\"zonesMsPerTick\":%.6f,\"limitMsPerTick\":0.5}%n",baseline.ticks(),active.ticks(),baseline.plugin(),active.plugin(),extra,active.zones());
        if(extra>.5||active.zones()>.5)throw new AssertionError("T57b spark budget exceeded");
    }
}

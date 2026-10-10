import java.nio.file.*;
import java.util.*;
import me.lucko.spark.paper.proto.SparkSamplerProtos;

/** Offline cold-window accounting. Includes every CustomDungeons root, including probe calls. */
class T58SparkWindows {
    static double walk(int index,List<SparkSamplerProtos.StackTraceNode> nodes) {
        var node=nodes.get(index);
        if(node.getClassName().startsWith("dev.dasan.customdungeons."))
            return node.getTimesList().stream().mapToDouble(Double::doubleValue).sum();
        return node.getChildrenRefsList().stream().mapToDouble(i->walk(i,nodes)).sum();
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Expected three cold windows: 0-30, 31-91, 91-end");
        long started=0;
        System.out.print("{\"limitMsPerTick\":0.5,\"warmupLimitMsPerTick\":1.0,\"windows\":[");
        for(int i=0;i<args.length;i++) {
            var data=SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(Path.of(args[i])));
            var thread=data.getThreadsList().stream().filter(t->t.getName().equals("Server thread")).findFirst().orElseThrow();
            var metadata=data.getMetadata();int ticks=metadata.getNumberOfTicks();
            if(ticks<=0)throw new IllegalArgumentException("Profile contains no server ticks");
            if(i==0)started=metadata.getStartTime();
            var nodes=thread.getChildrenList();
            double ms=thread.getChildrenRefsList().stream().mapToDouble(n->walk(n,nodes)).sum();
            if(i>0)System.out.print(",");
            System.out.printf(Locale.ROOT,"{\"label\":\"%s\",\"startSeconds\":%.3f,\"endSeconds\":%.3f,\"ticks\":%d,\"intervalMillis\":%.3f,\"pluginMsPerTick\":%.6f}",
                i==0?"0-30":i==1?"31-91":"91-end",(metadata.getStartTime()-started)/1000d,
                (metadata.getEndTime()-started)/1000d,ticks,metadata.getInterval()/1000d,ms/ticks);
        }
        System.out.println("]}");
    }
}

package dev.dasan.customdungeons.tool;

import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class T57bRunnerTest {
    @TempDir Path root;
    private void executable(Path file,String text) throws Exception {Files.writeString(file,"#!/usr/bin/env bash\n"+text);assertTrue(file.toFile().setExecutable(true));}
    @ParameterizedTest @ValueSource(booleans={true,false})
    void interruptionDuringStartCleansItsServerButARejectedStartNeverStopsAnotherServer(boolean acquired) throws Exception {
        var scripts=Files.createDirectories(root.resolve("scripts"));var bin=Files.createDirectories(root.resolve("bin"));
        var server=Files.createDirectories(root.resolve("server/logs")).getParent();Files.writeString(server.resolve("logs/latest.log"),"");
        String source=Files.readString(Path.of("scripts/test-t57b-bots.sh"));
        Files.writeString(scripts.resolve("test-t57b-bots.sh"),source.replace("SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes","SERVER="+server));
        executable(bin.resolve("node"),"exit 0\n");executable(bin.resolve("pgrep"),"exit 1\n");
        executable(bin.resolve("cat"),"if [[ $1 == /sys/class/thermal/thermal_zone0/temp ]]; then echo 0; else /bin/cat \"$@\"; fi\n");
        executable(scripts.resolve("test-server.sh"),"if [[ $1 != stop || -s ${CD_START_OWNER_FILE:-} ]]; then echo \"$1\" >> \"$RUNNER_LOG\"; fi\nif [[ $1 == start ]]; then "+(acquired?
                "if [[ -n ${CD_START_OWNER_FILE:-} ]]; then echo \"123.cd-agents receipt\" > \"$CD_START_OWNER_FILE\"; fi; touch \"$RUNNER_READY\"; sleep 3":
                "echo 'El puerto ya está en uso.' >&2; exit 1")+"; fi\n");
        var ready=root.resolve("ready");var log=root.resolve("operations");var output=root.resolve("output");
        var builder=new ProcessBuilder("bash",scripts.resolve("test-t57b-bots.sh").toString(),"--run").redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("PATH",bin+":"+System.getenv("PATH"));builder.environment().put("RUNNER_LOG",log.toString());builder.environment().put("RUNNER_READY",ready.toString());builder.environment().put("CD_TARGET","agents");
        var process=builder.start();
        try {
            if(acquired) {
                assertTimeoutPreemptively(Duration.ofSeconds(10),()->{while(!Files.exists(ready)){assertTrue(process.isAlive(),Files.readString(output));Thread.sleep(50);}});
                process.destroy();
            }
            assertTrue(process.waitFor(15,TimeUnit.SECONDS));
            assertEquals(acquired,Files.readAllLines(log).contains("stop"),"Cleanup must belong to its start attempt: "+Files.readString(output));
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
}

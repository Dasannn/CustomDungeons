package dev.dasan.customdungeons.tool;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real lifecycle script with simulated Screen and two distinct live process identities. */
class TestServerOwnershipTest {
    @TempDir Path root;
    private void executable(Path file,String text) throws Exception {
        Files.writeString(file,"#!/usr/bin/env bash\nset -eu\n"+text);assertTrue(file.toFile().setExecutable(true));
    }
    private record Result(int status,String output) {}
    private final class Fixture implements AutoCloseable {
        final Process own=new ProcessBuilder("sleep","60").start(),foreign=new ProcessBuilder("sleep","60").start();
        final Path scripts=Files.createDirectories(root.resolve("scripts")),bin=Files.createDirectories(root.resolve("bin"));
        final Path server=Files.createDirectories(root.resolve("s".repeat(Math.max(1,86-"/.customdungeons-screen".length()-root.toString().length()-1))).resolve("logs")).getParent();
        final Path receipt=Files.createDirectories(root.resolve(".agent")).resolve("owned"),state=root.resolve("screen-state"),stopped=root.resolve("stopped");
        final String id=own.pid()+".ca000000000001",
                otherId=foreign.pid()+".ca000000000002";
        Fixture() throws Exception {
            int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
            String source=Files.readString(Path.of("scripts/test-server.sh"));
            source=source.replace("$HOME/Desktop/Proyectos/plugins/servidor/Servidor-agentes",server.toString()).replace("PORT=25566","PORT="+port);
            executable(scripts.resolve("test-server.sh"),source.substring(source.indexOf('\n')+1));
            executable(bin.resolve("screen"),"""
                case $1 in
                  -wipe) exit 0 ;;
                  -ls) if [[ -f $SCREEN_STATE ]]; then printf '\\t%s (Detached)\\n' "$(cat "$SCREEN_STATE")"; fi ;;
                  -dmS)
                    [[ ${FAIL_SCREEN:-0} == 0 ]] || exit 1
                    launched="${OWN_ID%%.*}.$2"
                    printf '%s\\n' "$launched" > "$SCREEN_STATE"
                    touch "$SCREENDIR/$launched"
                    echo 'Done (1s)!' > "$CONSOLE"
                    echo 'Done (1s)!' > "$SERVER_LOG"
                    if [[ ${INTERRUPT_SCREEN:-0} == 1 ]]; then kill -TERM "$PPID"; fi ;;
                  -S)
                    if [[ $3 == -Q ]]; then sleep 60; exit 1; fi
                    if [[ ${4:-} == select ]]; then
                      # Socket connection is bounded and does not wait for a query reply.
                      (( ${#SCREENDIR}+1+${#2} < 108 )) || exit 1
                      [[ -f $SCREEN_STATE && $(cat "$SCREEN_STATE") == "$2" ]]; exit $?
                    fi
                    printf '%s\\n' "$2" >> "$STOPPED"; rm -f "$SCREEN_STATE" ;;
                  *) exit 2 ;;
                esac
                """);
        }
        String identity(String session) throws Exception {
            Path socket=server.resolve(".customdungeons-screen").resolve(session);
            return Files.getAttribute(socket,"unix:dev")+":"+Files.getAttribute(socket,"unix:ino");
        }
        Result run(String action,boolean fail) throws Exception {return run(action,fail,false);}
        Result run(String action,boolean fail,boolean interrupt) throws Exception {
            var builder=new ProcessBuilder("bash",scripts.resolve("test-server.sh").toString(),action).redirectErrorStream(true);
            var env=builder.environment();env.put("PATH",bin+":"+System.getenv("PATH"));env.put("CD_TARGET","agents");env.put("CD_START_OWNER_FILE",receipt.toString());
            env.put("SCREEN_STATE",state.toString());env.put("OWN_ID",id);env.put("STOPPED",stopped.toString());
            env.put("CONSOLE",root.resolve(".agent/server-console.log").toString());env.put("SERVER_LOG",server.resolve("logs/latest.log").toString());env.put("FAIL_SCREEN",fail?"1":"0");env.put("INTERRUPT_SCREEN",interrupt?"1":"0");
            var process=builder.start();
            try {assertTrue(process.waitFor(10,TimeUnit.SECONDS));return new Result(process.exitValue(),new String(process.getInputStream().readAllBytes()));}
            finally {if(process.isAlive())process.destroyForcibly();}
        }
        @Override public void close(){own.destroyForcibly();foreign.destroyForcibly();}
    }
    @ParameterizedTest @ValueSource(strings={"missing","empty","foreign","reused","reused_pid"})
    void cleanupCannotStopANewOrUnidentifiedInstance(String receiptKind) throws Exception {
        try(var f=new Fixture()) {
            Files.writeString(f.state,f.otherId+"\n");
            var sockets=Files.createDirectories(f.server.resolve(".customdungeons-screen"));
            Files.createFile(sockets.resolve(f.id));Files.createFile(sockets.resolve(f.otherId));
            switch(receiptKind) {
                case "empty" -> Files.writeString(f.receipt,"");
                case "foreign" -> Files.writeString(f.receipt,f.id+"\t"+f.identity(f.id)+"\n");
                case "reused" -> Files.writeString(f.receipt,f.otherId+"\t0:0\n");
                case "reused_pid" -> {
                    String replacement=f.own.pid()+".ca000000000003";
                    Files.writeString(f.state,replacement+"\n");Files.createFile(sockets.resolve(replacement));
                    Files.writeString(f.receipt,f.id+"\t"+f.identity(f.id)+"\n");
                }
                default -> { }
            }
            var result=f.run("stop",false);
            assertEquals(0,result.status(),result.output());
            assertFalse(Files.exists(f.stopped),"An unrelated Screen received stop: "+result.output());
            assertTrue(Files.exists(f.state));assertTrue(result.output().contains("Recibo"),result.output());
        }
    }
    @Test void receiptIsWrittenAfterStartAndBindsCleanupToThatExactLiveScreen() throws Exception {
        try(var f=new Fixture()) {
            var result=f.run("start",false);assertEquals(0,result.status(),result.output());
            String launched=Files.readString(f.state).strip();
            assertTrue(launched.matches(f.own.pid()+"[.]ca[0-9a-f]{12}"),"Every owning launch needs a compact unique session name");
            assertEquals(launched+"\t"+f.identity(launched),Files.readString(f.receipt).strip());
            result=f.run("stop",false);assertEquals(0,result.status(),result.output());
            assertEquals(launched,Files.readString(f.stopped).strip());
        }
    }
    @Test void failedScreenStartNeverCreatesAnOwnershipReceipt() throws Exception {
        try(var f=new Fixture()) {
            assertNotEquals(0,f.run("start",true).status());
            assertFalse(Files.exists(f.receipt));
            Files.writeString(f.state,f.otherId+"\n");
            assertEquals(0,f.run("stop",false).status());assertFalse(Files.exists(f.stopped));
        }
    }
    @Test void runnerStartCannotQuitAnExistingScreenEvenBeforeItsGamePortOpens() throws Exception {
        try(var f=new Fixture()) {
            Files.writeString(f.state,f.otherId+"\n");
            var sockets=Files.createDirectories(f.server.resolve(".customdungeons-screen"));Files.createFile(sockets.resolve(f.otherId));
            assertNotEquals(0,f.run("start",false).status());
            assertFalse(Files.exists(f.stopped));assertEquals(f.otherId,Files.readString(f.state).strip());
            assertFalse(Files.exists(f.receipt));
        }
    }

    @Test void interruptionBetweenScreenLaunchAndReceiptPublicationStillAllowsOwnedCleanup() throws Exception {
        try(var f=new Fixture()) {
            var result=f.run("start",false,true);assertEquals(143,result.status(),result.output());
            String launched=Files.readString(f.state).strip();
            assertTrue(launched.matches(f.own.pid()+"[.]ca[0-9a-f]{12}"),"Every owning launch needs a compact unique session name");
            assertEquals(launched+"\t"+f.identity(launched),Files.readString(f.receipt).strip());
            result=f.run("stop",false);assertEquals(0,result.status(),result.output());
            assertEquals(launched,Files.readString(f.stopped).strip());
        }
    }

}

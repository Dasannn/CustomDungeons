package dev.dasan.customdungeons.tool;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T58ColdWindowTest {
    @Test void runnerEngagesBeforeItsNormalRoomBoundaryExercise() throws Exception {
        var process=new ProcessBuilder("node","--test","scripts/t58-bot-movement.test.cjs").redirectErrorStream(true).start();
        var output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),output);
    }
    @Test void runnerRejectsOverBudgetOrIncompleteColdWindows() throws Exception {
        var process=new ProcessBuilder("node","--test","scripts/t58-profile-windows.test.cjs").redirectErrorStream(true).start();
        var output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),output);
    }
}

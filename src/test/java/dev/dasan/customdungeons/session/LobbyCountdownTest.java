package dev.dasan.customdungeons.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyCountdownTest {
    @Test void waitsForEveryPlateAndRestartsAfterSomeoneStepsOff() {
        var lobby=new LobbyCountdown(3);
        assertFalse(lobby.tick(0,false)); assertEquals(-1,lobby.secondsLeft(0));
        assertFalse(lobby.tick(10,true)); assertEquals(3,lobby.secondsLeft(10));
        assertFalse(lobby.tick(50,true)); assertEquals(1,lobby.secondsLeft(50));
        assertFalse(lobby.tick(60,false)); assertEquals(-1,lobby.secondsLeft(60));
        assertFalse(lobby.tick(70,true));
        assertFalse(lobby.tick(129,true)); assertTrue(lobby.tick(130,true));
    }
    @Test void droppingOffOnTheLastTickCancels() {
        var lobby=new LobbyCountdown(1);
        lobby.tick(0,true); assertFalse(lobby.tick(20,false));
        assertFalse(lobby.tick(21,true)); assertTrue(lobby.tick(41,true));
    }
    @Test void automaticCountdownWaitsForTheMinimumToo() {
        var lobby=new LobbyCountdown(2);
        for(int tick=0;tick<100;tick++) assertFalse(lobby.tick(tick,false));
        assertFalse(lobby.tick(100,true)); assertTrue(lobby.tick(140,true));
    }
}

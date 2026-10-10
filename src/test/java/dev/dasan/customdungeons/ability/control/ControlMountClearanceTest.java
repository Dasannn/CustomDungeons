package dev.dasan.customdungeons.ability.control;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Replay the riding attachment that put live players below the platform during drain. */
class ControlMountClearanceTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @ParameterizedTest @ValueSource(doubles={64,64.5})
    void drainSeatKeepsPassengerFeetAboveTheSurface(double floor) {
        try(var f=new ControlServiceTest.Fixture()) {
            when(f.entity.getLocation()).thenReturn(new Location(f.world,0,floor,0));
            var feet=new AtomicReference<>(new Location(f.world,1,floor,0));
            when(f.player.getLocation()).thenAnswer(i->feet.get().clone());
            when(f.world.spawn(any(Location.class),eq(ArmorStand.class),any(Consumer.class))).thenAnswer(i->{
                var stand=mock(ArmorStand.class);var at=new AtomicReference<>(((Location)i.getArgument(0)).clone());
                var height=new double[]{1.975};
                when(stand.getUniqueId()).thenReturn(UUID.randomUUID());when(stand.isValid()).thenReturn(true);
                var data=ControlServiceTest.pdc();when(stand.getPersistentDataContainer()).thenReturn(data);
                doAnswer(call->{height[0]=(boolean)call.getArgument(0)?0:1.975;return null;}).when(stand).setMarker(anyBoolean());
                // Public riding geometry, including the player's -0.6 attachment offset.
                Runnable attach=()->feet.set(at.get().clone().add(0,height[0]*.75-.6,0));
                when(stand.addPassenger(any(Player.class))).thenAnswer(call->{attach.run();return true;});
                when(stand.teleport(any(Location.class),any(io.papermc.paper.entity.TeleportFlag.class))).thenAnswer(call->{at.set(((Location)call.getArgument(0)).clone());attach.run();return true;});
                ((Consumer<ArmorStand>)i.getArgument(2)).accept(stand);f.created.add(stand);return stand;
            });
            f.start("drain_grab");assertEquals(1,f.service.activeCount());
            for(int tick=0;tick<30;tick++) {
                assertTrue(feet.get().getY()>=floor,"Mounted feet must not clip below the floor: "+feet.get());
                f.step(1);
            }
            ControlService.cleanup(f.caster);assertEquals(0,f.service.activeCount());
            assertTrue(feet.get().getY()>=floor,"Releasing the control must not leave the player in the floor");
        }
    }
}

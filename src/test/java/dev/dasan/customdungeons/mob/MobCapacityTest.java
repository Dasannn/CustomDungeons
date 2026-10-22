package dev.dasan.customdungeons.mob;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobCapacityTest {
    @Test void occupiedCreaturesAndReservationsShareOneLimitAndReleaseIsIdempotent() {
        var capacity=new MobCapacity();var slot=capacity.reserve(2,3);
        assertNotNull(slot);assertNull(capacity.reserve(2,3));
        slot.close();slot.close();assertNotNull(capacity.reserve(2,3));assertNull(capacity.reserve(3,3));
    }
    @Test void bindingTransfersItsReservationOnlyWhenThatEntityIsTracked() {
        var capacity=new MobCapacity();var slot=capacity.reserve(1,2);var id=UUID.randomUUID();slot.bind(id);
        assertTrue(capacity.owns(id));capacity.commit(UUID.randomUUID());assertNull(capacity.reserve(1,2));
        capacity.commit(id);assertFalse(capacity.owns(id));assertNull(capacity.reserve(2,2));
        slot.close();assertNotNull(capacity.reserve(1,2));
    }
}

package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.combat.CombatService;
import java.util.ArrayList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecoyCombatReceiptTest {
    @Test void decoyDamageHasNoBossReceiptForLedgerPhasesOrIntelligence() {
        var body=new VirtualHealthTest.Body(10);
        body.data.put(CombatService.DECOY,(byte)1);
        var receipts=new ArrayList<MobDamageAppliedEvent>();
        var listener=new MobCombatListener(task->{},receipts::add);
        var hit=new EntityDamageEvent(body.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class),5);
        listener.damaged(hit);
        assertTrue(receipts.isEmpty());assertFalse(hit.isCancelled());assertEquals(5,hit.getFinalDamage());
    }
}

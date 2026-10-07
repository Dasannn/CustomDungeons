package dev.dasan.customdungeons.session;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;

/** Short leases plus a player PDC journal allow cleanup after a server crash. */
final class AmbienceEffects {
    private static final NamespacedKey JOURNAL=new NamespacedKey("customdungeons","room_effects");
    private final Player player;
    private PotionEffectType changingType;
    private final AmbiencePolicy.OwnedEffects<PotionEffect> owned;
    AmbienceEffects(Player player) {
        this.player=player;
        owned=new AmbiencePolicy.OwnedEffects<>(new AmbiencePolicy.EffectPort<>() {
            public PotionEffect current(String key){var type=type(key);return type==null?null:player.getPotionEffect(type);}
            public boolean add(String key,PotionEffect effect){return mutate(effect.getType(),()->player.addPotionEffect(effect));}
            public void remove(String key){var type=type(key);if(type!=null)mutate(type,()->{player.removePotionEffect(type);return true;});}
            public boolean same(PotionEffect expected,PotionEffect actual){return matches(expected,actual);}
        });
    }
    private boolean mutate(PotionEffectType type,java.util.function.BooleanSupplier action) {
        var previous=changingType;changingType=type;
        try{return action.getAsBoolean();}finally{changingType=previous;}
    }
    void apply(PotionEffect effect){owned.apply(effect.getType().getKey().toString(),effect);journal();}
    void changed(EntityPotionEffectEvent event) {
        if(!player.equals(event.getEntity()))return;
        var effect=event.getOldEffect()!=null?event.getOldEffect():event.getNewEffect();
        if(effect!=null && !effect.getType().equals(changingType)){owned.external(effect.getType().getKey().toString());journal();}
    }
    void clear(){owned.clear();journal();}
    private void journal() {
        if(player.getPersistentDataContainer()==null)return;
        var entries=owned.snapshot().values().stream().map(AmbienceEffects::encode).sorted().toList();
        if(entries.isEmpty())player.getPersistentDataContainer().remove(JOURNAL);
        else player.getPersistentDataContainer().set(JOURNAL,PersistentDataType.STRING,String.join(";",entries));
    }
    static boolean matches(PotionEffect expected,PotionEffect actual) {
        return expected!=null && actual!=null && actual.getType().equals(expected.getType())
                && actual.getAmplifier()==expected.getAmplifier() && actual.isAmbient()==expected.isAmbient()
                && actual.hasParticles()==expected.hasParticles() && actual.hasIcon()==expected.hasIcon()
                && actual.getHiddenPotionEffect()==null && !actual.isInfinite() && actual.getDuration()<=expected.getDuration();
    }
    private static String encode(PotionEffect effect) {
        return effect.getType().getKey()+","+effect.getAmplifier()+","+effect.getDuration()+","+effect.hasParticles();
    }
    static PotionEffectType type(String key) {var id=NamespacedKey.fromString(key);return id==null?null:Registry.EFFECT.get(id);}
    static void recover(Player player) {
        if(player.getPersistentDataContainer()==null)return;
        String journal=player.getPersistentDataContainer().get(JOURNAL,PersistentDataType.STRING);
        if(journal==null)return;
        // Remove journal before invoking Paper so the existing listener cannot claim these removals.
        player.getPersistentDataContainer().remove(JOURNAL);
        for(String encoded:journal.split(";")) {
            try {
                var parts=encoded.split(",");var type=type(parts[0]);
                if(parts.length!=4 || type==null)continue;
                int amplifier=Integer.parseInt(parts[1]),duration=Integer.parseInt(parts[2]);
                if(amplifier<0 || amplifier>255 || duration<0 || duration>60)continue;
                var expected=new PotionEffect(type,duration,amplifier,true,Boolean.parseBoolean(parts[3]),false);
                if(matches(expected,player.getPotionEffect(type)))player.removePotionEffect(type);
            } catch(IllegalArgumentException invalid){ /* Corrupt local journal is discarded conservatively. */ }
        }
    }
}

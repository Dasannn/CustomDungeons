package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Pure bounded sampling, effect ownership and music arbitration. No scheduler. */
final class AmbiencePolicy {
    static List<Point> samples(Region region,int count) {
        count=Math.clamp(count,0,32);
        if(region==null || count==0)return List.of();
        var points=new ArrayList<Point>(count);
        for(int i=0;i<count;i++) {
            double edge=count==1?.5:i==0?0:i==count-1?1:-1;
            double x=edge>=0?edge:radicalInverse(i+1,2),y=edge>=0?edge:radicalInverse(i+1,3),z=edge>=0?edge:radicalInverse(i+1,5);
            // Low-discrepancy samples cover door faces/volumes without iterating any blocks.
            points.add(new Point(region.world(),region.min().x()+.5+((double)region.max().x()-region.min().x())*x,
                    region.min().y()+.5+((double)region.max().y()-region.min().y())*y,
                    region.min().z()+.5+((double)region.max().z()-region.min().z())*z,0,0));
        }
        return List.copyOf(points);
    }
    private static double radicalInverse(int n,int base) {
        double value=0,factor=1d/base;
        while(n>0){value+=(n%base)*factor;n/=base;factor/=base;}
        return value;
    }
    interface EffectPort<E> { E current(String key); boolean add(String key,E value); void remove(String key); boolean same(E expected,E actual); }
    static final class OwnedEffects<E> {
        private final EffectPort<E> port;
        private final Map<String,E> owned=new LinkedHashMap<>();
        OwnedEffects(EffectPort<E> port){this.port=port;}
        void apply(String key,E value) {
            E current=port.current(key),previous=owned.get(key);
            if(current!=null && (previous==null || !port.same(previous,current))) {owned.remove(key);return;}
            owned.remove(key);
            if(port.add(key,value))owned.put(key,value);
        }
        void external(String key){owned.remove(key);}
        Map<String,E> snapshot(){return Map.copyOf(owned);}
        void clear() {
            for(var entry:owned.entrySet())if(port.same(entry.getValue(),port.current(entry.getKey())))port.remove(entry.getKey());
            owned.clear();
        }
    }
    interface MusicPort {void play(String key);void stop(String key);}
    static final class Music {
        private final MusicPort port;
        private String playing;
        private long next;
        Music(MusicPort port){this.port=port;}
        void update(String key,boolean boss,long tick,int interval) {
            if(boss || key!=null && key.isBlank())key=null;
            if(!Objects.equals(playing,key)){clear();playing=key;next=tick;}
            if(playing!=null && tick>=next){port.play(playing);next=tick+Math.max(1,interval);}
        }
        void clear(){if(playing!=null)port.stop(playing);playing=null;}
    }
}

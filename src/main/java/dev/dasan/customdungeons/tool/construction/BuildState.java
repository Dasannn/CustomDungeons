package dev.dasan.customdungeons.tool.construction;

import dev.dasan.customdungeons.model.DungeonDef;
import java.util.*;

/** Pure authoring state; the bounded history contains definitions, never world blocks. */
public final class BuildState {
    public record Saved(DungeonDef definition, DungeonDef baseline, int room, int point, List<DungeonDef> undo) {
        public Saved {
            Objects.requireNonNull(definition,"definition");Objects.requireNonNull(baseline,"baseline");
            if(!definition.id().equals(baseline.id()) || room<0 || point<0 || point>2 || undo.size()>20
                    || undo.stream().anyMatch(d->!definition.id().equals(d.id()))) throw new IllegalArgumentException("Invalid build draft");
            room=Math.min(room,Math.max(0,definition.rooms().size()-1));undo=List.copyOf(undo);
        }
    }
    private Saved saved;
    public BuildState(Saved saved) { this.saved=saved; }
    public BuildState(DungeonDef definition) {this(new Saved(definition,definition,0,0,List.of()));}
    public Saved snapshot() {return saved;}
    public DungeonDef definition() {return saved.definition();}
    public void change(DungeonDef definition) {
        if(Objects.equals(definition,saved.definition())) return;
        var history=new ArrayList<>(saved.undo());history.add(saved.definition());
        if(history.size()>20) history.removeFirst();
        saved=new Saved(definition,saved.baseline(),saved.room(),saved.point(),history);
    }
    public boolean undo() {
        if(saved.undo().isEmpty()) return false;
        var history=new ArrayList<>(saved.undo());var previous=history.removeLast();
        saved=new Saved(previous,saved.baseline(),saved.room(),saved.point(),history);return true;
    }
    public int room() {return saved.room();}
    public void room(int room) {saved=new Saved(saved.definition(),saved.baseline(),room,saved.point(),saved.undo());}
    public void cyclePoint() {saved=new Saved(saved.definition(),saved.baseline(),saved.room(),(saved.point()+1)%3,saved.undo());}
    public boolean conflicts(DungeonDef current) {
        // A new editor/build draft need not have a published or assistant version yet.
        if(current==null) return false;
        // Publication may have reached disk before the draft's new baseline did.
        if(Objects.equals(saved.definition(),current)) published(current);
        return !Objects.equals(saved.baseline(),current);
    }
    public void published(DungeonDef definition) {saved=new Saved(definition,definition,saved.room(),saved.point(),saved.undo());}
}

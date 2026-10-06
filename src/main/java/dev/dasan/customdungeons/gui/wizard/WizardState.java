package dev.dasan.customdungeons.gui.wizard;

import java.util.function.IntPredicate;

/** Seven steps; completion is a contiguous prefix and never includes the review. */
public final class WizardState {
    private int step;
    private int completed;
    public WizardState(int step,int completed) {
        if(step<0 || step>6 || completed<0 || completed>6 || step>completed)
            throw new IllegalArgumentException("Invalid wizard progress");
        this.step=step;this.completed=completed;
    }
    public int step() {return step;}
    public int completed() {return completed;}
    public boolean next(boolean valid) {
        if(!valid || step==6) return false;
        completed=Math.max(completed,++step);return true;
    }
    public boolean back() {if(step==0) return false;step--;return true;}
    public boolean visit(int target) {
        if(target<0 || target>completed || target>6) return false;
        step=target;return true;
    }
    public void reconcile(IntPredicate valid) {
        for(int i=0;i<completed;i++) if(!valid.test(i)) {
            completed=i;step=Math.min(step,i);return;
        }
    }
}

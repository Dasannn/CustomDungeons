package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Saved items are read-only templates; deposited physical items are always returned. */
public final class RewardMenu extends DungeonEditor {
    private final Map<Integer,org.bukkit.inventory.ItemStack> templates=new HashMap<>();
    private boolean initialized;
    private List<org.bukkit.inventory.ItemStack> overflowTemplates=List.of();
    public RewardMenu(DungeonMenu root) {super("reward",root,root);}
    static boolean itemSlot(int slot) {return slot>=18&&slot<45;}
    static List<Integer> itemSlots() {return java.util.stream.IntStream.range(0,54).filter(RewardMenu::itemSlot).boxed().toList();}
    @Override public boolean allowsPlacement(int slot) {return itemSlot(slot)&&!templates.containsKey(slot)&&root.canEdit(false);}
    @Override protected void beforeInventoryReplaced() { capture(); }
    @Override protected void render() {
        if(!initialized) {
            var items=root.draft.get().reward().items();
            for(int i=0;i<Math.min(27,items.size());i++) templates.put(itemSlots().get(i),items.get(i));
            overflowTemplates=items.size()>27?items.subList(27,items.size()):List.of();
            initialized=true;
        }
        set(4,Button.of(Material.CHEST,msg("reward-zone"),List.of(msg("reward-zone-lore")),(p,c)->{}));
        // Three complete rows form a separate 27-cell placement zone.
        for(int slot:itemSlots()) {
            var template=templates.get(slot);
            if(template==null) clear(slot);
            else {
                var icon=template.clone();
                icon.editMeta(meta->{var lore=meta.lore()==null?new ArrayList<Component>():new ArrayList<>(meta.lore());lore.add(msg("reward-item-lore"));meta.lore(lore);});
                final int cell=slot;
                set(slot,new Button(icon,(p,c)->{if(root.writable()&&c.isRightClick()){capture();templates.remove(cell);sync();refresh();}}));
            }
        }
        var reward=root.draft.get().reward();
        numberReward(11,"money",reward.money(),1000000000,n->new RewardDef(root.draft.get().reward().items(),n,root.draft.get().reward().xp(),root.draft.get().reward().commands()));
        numberReward(13,"xp",reward.xp(),1000000,n->new RewardDef(root.draft.get().reward().items(),root.draft.get().reward().money(),(int)n,root.draft.get().reward().commands()));
        set(15,action("commands",Material.COMMAND_BLOCK,"",(p,c)->{capture();MenuListener.instance().later(()->{if(root.writable()) new CommandList(root,this,()->root.draft.get().reward().commands(),commands->root.change(v->v.reward=new RewardDef(v.reward.items(),v.reward.money(),v.reward.xp(),commands))).open();});}));
    }
    private void numberReward(int slot,String key,double value,double max,DoubleFunction<RewardDef> change) {
        set(slot,action(key,key.equals("money")?Material.GOLD_INGOT:Material.EXPERIENCE_BOTTLE,Inputs.formatNumber(value,key.equals("money")?2:0),(p,c)->{
            capture();MenuListener.instance().later(()->{if(root.writable()) {
                DoubleConsumer accept=n->{if(root.writable()){root.change(v->v.reward=change.apply(n));refresh();}};
                if(key.equals("money")) Inputs.decimal(p,msg(key,Placeholder.unparsed("value",Inputs.formatNumber(value,2))),0,max,inputValue(value,0,max),2,accept);
                else if(c.isRightClick()) Inputs.numberWithClicks(p,msg(key,Placeholder.unparsed("value",Inputs.formatNumber(value,0))),0,max,inputValue(value,0,max),accept,0);
                else Inputs.integer(p,msg(key,Placeholder.unparsed("value",Inputs.formatNumber(value,0))),0,(int)max,(int)inputValue(value,0,max),n->accept.accept(n));
            }});
        }));
    }
    /** Called synchronously on close, and before any refresh or input opens. */
    void capture() {
        boolean writable=root.canEdit(false);
        for(int slot:itemSlots()) if(!templates.containsKey(slot)) {
            var item=getInventory().getItem(slot);
            if(item==null||item.getType().isAir()) continue;
            if(writable) templates.put(slot,item.clone());
            getInventory().setItem(slot,null);
            var overflow=viewer.getInventory().addItem(item);
            overflow.values().forEach(extra->viewer.getWorld().dropItem(viewer.getLocation(),extra));
        }
        if(writable) sync();
    }
    private void sync() {
        // Preserve YAML rewards beyond the 27 visible cells instead of silently truncating them.
        var items=new ArrayList<org.bukkit.inventory.ItemStack>();
        for(int slot:itemSlots()) if(templates.containsKey(slot)) items.add(templates.get(slot));
        items.addAll(overflowTemplates);
        root.change(v->v.reward=new RewardDef(items,v.reward.money(),v.reward.xp(),v.reward.commands()));
    }
    @Override protected Runnable onSave() {return ()->{capture();root.saveDraft();refresh();};}
}

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
    private final MobMenu.MobDraft mob;
    private final WorldBossMenu bossRoot;
    public RewardMenu(DungeonMenu root) {super("reward",root,root);mob=null;bossRoot=null;}
    public RewardMenu(Player player,MobMenu.MobDraft mob,WorldBossMenu parent) {
        super(player,"reward",WorldBossMenu.m("rewards-title",Placeholder.component("boss",dev.dasan.customdungeons.text.Text.parse(mob.name))),null,parent);this.mob=mob;bossRoot=parent;
    }
    @Override public boolean permitted(Player player) {return super.permitted(player)&&(mob==null||WorldBossMenu.allowed(player));}
    private boolean writable() {return mob==null?root.writable():bossRoot.writable();}
    private boolean canEdit() {return mob==null?root.canEdit(false):bossRoot.writable();}
    private RewardDef reward() {return mob==null?root.draft.get().reward():mob.worldBoss.reward();}
    private void changeReward(RewardDef reward) {if(mob==null)root.change(v->v.reward=reward);else mob.worldBoss=mob.worldBoss.withReward(reward);}
    private void saveReward() {if(mob==null)root.saveDraft();else bossRoot.save();}
    @Override protected Component title() {return mob==null?super.title():WorldBossMenu.m("rewards-title",Placeholder.component("boss",dev.dasan.customdungeons.text.Text.parse(mob.name)));}
    @Override protected boolean hasUnsavedChanges() {return mob==null?super.hasUnsavedChanges():!Objects.equals(mob.snapshot(),mob.savedSnapshot);}
    @Override protected void renderHeader() {
        if(mob==null){super.renderHeader();return;}
        set(4,GuiTheme.information(Material.CHEST,WorldBossMenu.m("rewards-summary",Placeholder.component("boss",dev.dasan.customdungeons.text.Text.parse(mob.name))),
                List.of(msg("reward-zone-lore"),WorldBossMenu.m("rewards-rule",WorldBossMenu.args(mob.worldBoss,0)),WorldBossMenu.m("pending-items"))));
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->WorldBossMenu.m("rewards-help-"+i)).toList());
    }
    @Override protected void renderFooter() {
        if(mob==null)return;
        set(45,Button.of(Material.ARROW,WorldBossMenu.m("rewards-back"),List.of(Component.empty(),WorldBossMenu.m("rewards-back-lore")),(p,c)->MenuListener.instance().later(()->{capture();bossRoot.open();})));
        var lore=new ArrayList<Component>();if(hasUnsavedChanges())lore.add(MenuListener.instance().messages().get("gui.common.unsaved"));
        lore.add(Component.empty());lore.add(WorldBossMenu.m("save-context"));
        var button=Button.of(Material.LIME_CONCRETE,MenuListener.instance().messages().get("gui.common.save"),lore,(p,c)->{if(writable()){capture();saveReward();refresh();}});
        if(hasUnsavedChanges())button.icon().editMeta(meta->meta.setEnchantmentGlintOverride(true));set(49,button);
    }
    static boolean itemSlot(int slot) {return slot>=18&&slot<45;}
    static List<Integer> itemSlots() {return java.util.stream.IntStream.range(0,54).filter(RewardMenu::itemSlot).boxed().toList();}
    @Override protected Set<Integer> reservedInputSlots() {return Set.copyOf(itemSlots());}
    @Override public boolean allowsPlacement(int slot) {return itemSlot(slot)&&!templates.containsKey(slot)&&canEdit();}
    @Override protected void beforeInventoryReplaced() { capture(); }
    @Override protected void render() {
        if(!initialized) {
            var items=reward().items();
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
                set(slot,new Button(icon,(p,c)->{if(writable()&&c.isRightClick()){capture();templates.remove(cell);sync();refresh();}}));
            }
        }
        var reward=reward();
        numberReward(11,"money",reward.money(),n->new RewardDef(reward().items(),n,reward().xp(),reward().commands()));
        numberReward(13,"xp",reward.xp(),n->new RewardDef(reward().items(),reward().money(),(int)n,reward().commands()));
        set(15,action("commands",Material.COMMAND_BLOCK,"",(p,c)->{capture();MenuListener.instance().later(()->{if(writable()) if(mob==null)new CommandList(root,this,()->reward().commands(),commands->changeReward(new RewardDef(reward().items(),reward().money(),reward().xp(),commands))).open();
                else new BossRewardCommands(viewer,this,bossRoot,()->reward().commands(),commands->changeReward(new RewardDef(reward().items(),reward().money(),reward().xp(),commands))).open();});}));
        if(mob!=null)getInventory().getItem(15).editMeta(meta->{var lore=new ArrayList<>(meta.lore());
            lore.addFirst(WorldBossMenu.m("commands-summary",Placeholder.unparsed("count",Integer.toString(reward().commands().size())),Placeholder.unparsed("commands",String.join(" · ",reward().commands()))));meta.lore(lore);});
    }
    private void numberReward(int slot,String key,double value,DoubleFunction<RewardDef> change) {
        var range=key.equals("money")?NumericRanges.MONEY:NumericRanges.XP;
        set(slot,NumericInputs.decorate(action(key,key.equals("money")?Material.GOLD_INGOT:Material.EXPERIENCE_BOTTLE,Inputs.formatNumber(value,key.equals("money")?2:0),(p,c)->{
            capture();MenuListener.instance().later(()->{if(writable()) {
                DoubleConsumer accept=n->{if(writable()){changeReward(change.apply(n));refresh();}};
                var title=msg(key,Placeholder.unparsed("value",Inputs.formatNumber(value,range.decimals())));
                if(c.isRightClick() && range.decimals()==0) NumericInputs.clicks(p,title,range,value,accept);
                else NumericInputs.edit(p,title,range,value,accept);
            }});
        }),range));
        if(mob!=null)getInventory().getItem(slot).editMeta(meta->{
            var lore=new ArrayList<>(meta.lore());lore.removeIf(Component.empty()::equals);
            lore.add(MobMenuBase.label("current-value",WorldBossMenu.number(value,range.decimals(),key.equals("money"))));meta.lore(lore);
            meta.displayName(WorldBossMenu.m(key,Placeholder.unparsed("value",WorldBossMenu.number(value,range.decimals(),key.equals("money")))));
        });
    }
    /** Called synchronously on close, and before any refresh or input opens. */
    void capture() { capture(false); }
    void capture(boolean deathClose) {
        boolean writable=canEdit();
        for(int slot:itemSlots()) if(!templates.containsKey(slot)) {
            var item=getInventory().getItem(slot);
            if(item==null||item.getType().isAir()) continue;
            if(writable) templates.put(slot,item.clone());
            getInventory().setItem(slot,null);
            returnDepositedItem(item,deathClose);
        }
        if(writable) sync();
    }
    private void sync() {
        // Preserve YAML rewards beyond the 27 visible cells instead of silently truncating them.
        var items=new ArrayList<org.bukkit.inventory.ItemStack>();
        for(int slot:itemSlots()) if(templates.containsKey(slot)) items.add(templates.get(slot));
        items.addAll(overflowTemplates);
        changeReward(new RewardDef(items,reward().money(),reward().xp(),reward().commands()));
    }
    @Override protected Runnable onSave() {return ()->{capture();saveReward();refresh();};}
}

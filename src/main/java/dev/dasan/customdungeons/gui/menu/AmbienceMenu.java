package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Text;
import java.util.*;
import org.bukkit.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

/** Approved a1: four columns, sparse overrides, existing registry selectors. */
public final class AmbienceMenu extends DungeonEditor {
    private final int room;
    private final AmbienceSettings defaults;
    public AmbienceMenu(DungeonMenu root,int room,Menu parent) {
        super("ambience",m("title",Placeholder.unparsed("room",Integer.toString(room+1))),root,parent);
        this.room=room;
        defaults=AmbienceSettings.load(root.services.plugin.getConfig(),path->{});
    }
    static Component m(String key,TagResolver... args){return MenuListener.instance().messages().get("ambience."+key,args);}
    private RoomDef room(){return root.draft.get().rooms().get(room);}
    private AmbienceSettings.Resolved value() {
        var r=room();var resolved=new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),
                r.spawners().stream().map(s->s.presetId()!=null && root.services.store.spawnerPresets().containsKey(s.presetId())
                        ? SpawnerPresets.makeLocal(s,root.services.store.spawnerPresets()):s).toList(),r.openingMode(),r.ambience());
        boolean boss=resolved.spawners().stream().flatMap(s->s.waves().stream()).flatMap(w->w.entries().stream())
                .map(e->root.services.store.mobs().get(e.templateId())).anyMatch(m->m!=null && m.boss());
        return defaults.resolve(r.ambience(),boss);
    }
    private void change(String key,Object value) {
        if(!root.writable())return;
        root.room(room,r->{var settings=(r.ambience()==null?new RoomAmbience(Map.of()):r.ambience()).with(key,value);
            return r.withAmbience(settings.values().isEmpty()?null:settings);});
        refresh();
    }
    private Component caption(String text) {
        if(text.startsWith("@"))return MenuListener.instance().messages().get(text.substring(1));
        return text.isBlank()?m("none"):Text.parse(text.replace("{room}",room().id()));
    }
    private Component display(String key,Object value) {
        if(value instanceof Boolean enabled)return m(enabled?"yes":"no");
        if(key.equals("effects")) {
            var effects=this.value().effects();
            if(effects.isEmpty())return m("none");
            return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(m("separator")),effects.stream()
                    .map(e->m("effect-label",Placeholder.component("type",e.effectKey().equals("minecraft:darkness")?m("darkness"):Component.text(e.effectKey())),
                            Placeholder.component("level",e.amplifier()==0?m("level-one"):Component.text(e.amplifier()+1)))).toList());
        }
        String text=String.valueOf(value);
        if(text.isBlank())return m("none");
        if(Set.of("entry-title","entry-subtitle","clear-title").contains(key))return caption(text);
        if(key.equals("door-sound") && text.equals("minecraft:block.iron_door.open"))return m("heavy-gate");
        if(key.equals("clear-sound") && text.equals("minecraft:ui.toast.challenge_complete"))return m("fanfare");
        if(text.equals("CLOUD,SMOKE"))return m("dust-smoke");
        if(text.equals("ASH") && key.equals("particle"))return m("ash-density",Placeholder.component("density",m(this.value().number("density")<12?"low":this.value().number("density")<24?"medium":"high")));
        if(text.equals("ASH"))return m("ash");
        if(text.equals("minecraft:entity.warden.emerge"))return m("warden-emerge");
        return Component.text(text.replace("minecraft:",""));
    }
    private Button option(String key,Material icon,Object value,Button.ClickHandler click,Component... details) {
        var lore=new ArrayList<Component>();
        boolean inherited=room().ambience()==null || !room().ambience().values().containsKey(key);
        if(inherited && key.equals("door-sound"))lore.add(m("inherited"));
        lore.addAll(List.of(details));
        if(inherited && !key.equals("door-sound"))lore.add(m("inherited"));
        lore.add(Component.empty());lore.add(m(key+"-lore"));
        return Button.of(icon,m(key,Placeholder.component("value",display(key,value))),lore,(p,c)->{
            if(!root.writable())return;
            MenuListener.instance().later(()->{if(root.writable())click.handle(p,c);});
        });
    }
    @Override protected Material borderMaterial(){return Material.GREEN_STAINED_GLASS_PANE;}
    @Override protected void renderHeader(){GuiTheme.help(this,List.of(m("help-1"),m("help-2"),m("help-3")));}
    @Override protected void renderFooter(){
        for(int slot:new int[]{48,50})set(slot,GuiTheme.information(borderMaterial(),Component.empty(),List.of()));
        set(45,Button.of(Material.ARROW,m("back"),List.of(Component.empty(),m("back-lore")),(p,c)->MenuListener.instance().later(parent()::open)));
        set(49,Button.of(Material.LIME_CONCRETE,m("save"),List.of(Component.empty(),m("save-lore")),(p,c)->root.saveDraft()));
    }
    @Override protected void render() {
        var v=value();
        summary(Material.NOTE_BLOCK,m("summary",Placeholder.unparsed("room",Integer.toString(room+1)),Placeholder.component("name",caption(v.text("entry-title")))),
                m("summary-entry",Placeholder.component("title",m(v.text("entry-title").isBlank()?"no-title":"title-word")),Placeholder.component("sound",m(v.text("entry-sound").isBlank()?"no-sound":"sound-word")),Placeholder.component("music",m(v.text("music").isBlank()?"no-music":"music-word"))),m("summary-inside",Placeholder.component("effects",display("effects",v.effects())),Placeholder.component("particle",display("summary-particle",v.text("particle")))),
                m("summary-door",Placeholder.component("sound",v.text("door-sound").equals("minecraft:block.iron_door.open")?m("gate-short"):display("door-sound",v.text("door-sound"))),Placeholder.component("particle",v.text("door-particle").equals("CLOUD,SMOKE")?m("dust-short"):display("door-particle",v.text("door-particle"))),Placeholder.component("shake",m(v.flag("door-shake")?"shake-word":"no-shake"))),m("summary-clear",Placeholder.component("sound",display("clear-sound",v.text("clear-sound")))));
        for(int i=0;i<4;i++)set(10+i*2,GuiTheme.section(m("section-"+i),List.of(m("section-"+i+"-lore"))));
        textOption(19,"entry-title",Material.NAME_TAG,v);textOption(28,"entry-subtitle",Material.PAPER,v);
        soundOption(37,"entry-sound",Material.NOTE_BLOCK,v);soundOption(21,"music",Material.JUKEBOX,v);
        set(30,option("effects",Material.POTION,v.effects(),(p,c)->{if(c.isRightClick())change("effects",null);else new EffectsMenu().open();},m("effects-detail"),m("effects-preserve")));
        particleOption(39,"particle",v);soundOption(23,"door-sound",Material.IRON_DOOR,v);particleOption(32,"door-particle",v);
        set(41,option("door-shake",v.flag("door-shake")?Material.LIME_DYE:Material.GRAY_DYE,v.flag("door-shake"),(p,c)->{
            if(c.isShiftClick() && c.isLeftClick())Inputs.text(p,m("door-title"),v.text("door-title"),256,s->change("door-title",s));
            else change("door-shake",c.isRightClick()?null:!v.flag("door-shake"));
        },m("shake-detail"),m("door-title-value",Placeholder.component("value",caption(v.text("door-title"))))));
        soundOption(25,"clear-sound",Material.GOAT_HORN,v);textOption(34,"clear-title",Material.NAME_TAG,v);
        set(43,Button.of(Material.RED_DYE,m("reset"),List.of(m("reset-detail"),Component.empty(),m("reset-lore")),(p,c)->{
            if(c.isShiftClick() && root.writable()){root.room(room,r->r.withAmbience(null));refresh();}
        }));
    }
    private void textOption(int slot,String key,Material icon,AmbienceSettings.Resolved v) {
        set(slot,option(key,icon,v.text(key),(p,c)->{
            if(c.isRightClick())change(key,null);
            else Inputs.text(p,m(key,Placeholder.component("value",caption(v.text(key)))),v.text(key).startsWith("@")?
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(caption(v.text(key))):v.text(key),256,s->change(key,s));
        },key.equals("entry-title")?m("current",Placeholder.component("value",caption(v.text(key)))):Component.empty()));
    }
    private void soundOption(int slot,String key,Material icon,AmbienceSettings.Resolved v) {
        set(slot,option(key,icon,v.text(key),(p,c)->{
            if(c.isRightClick())change(key,null);
            else if(c.isShiftClick())Inputs.text(p,m("sound-key"),v.text(key),256,s->{
                var candidate=new RoomAmbience(Map.of(key,s));
                if(AmbienceSettings.errors(candidate).isEmpty())change(key,s);else MenuListener.instance().messages().send(p,"ambience.invalid-key");
            });
            else MobMenuBase.choose(p,key.equals("music")?"music":"sound",MobMenuBase.soundKeys(),this,s->change(key,s));
        },key.equals("music")?m("music-detail"):Component.empty(),Component.text(v.text(key)),m("sound-manual")));
    }
    private void particleOption(int slot,String key,AmbienceSettings.Resolved v) {
        String densityKey=key.equals("particle")?"density":"door-density";
        set(slot,NumericInputs.decorate(option(key,Material.CAMPFIRE,v.text(key),(p,c)->{
            if(c.isShiftClick() && c.isRightClick()){change(key,null);change(densityKey,null);}
            else if(c.isShiftClick())Inputs.text(p,m(key,Placeholder.component("value",display(key,v.text(key)))),v.text(key),256,s->{
                var candidate=new RoomAmbience(Map.of(key,s));
                if(AmbienceSettings.errors(candidate).isEmpty())change(key,s);else MenuListener.instance().messages().send(p,"ambience.invalid-particle");
            });
            else if(c.isRightClick())change(densityKey,v.number(densityKey)<12?12:v.number(densityKey)<24?24:4);
            else MobMenuBase.choose(p,"particle",Arrays.stream(Particle.values()).filter(part->part.getDataType()==Void.class).map(Enum::name).toList(),this,s->change(key,s));
        },m("density",Placeholder.unparsed("value",Integer.toString(v.number(densityKey)))),m("particle-reset"),m("particle-manual")),NumericRanges.ambience(densityKey)));
    }
    private final class EffectsMenu extends DungeonPage<PotionDef> {
        EffectsMenu(){super("ambience-effects",AmbienceMenu.this.root,AmbienceMenu.this);}
        @Override protected int preferredRows(){return 6;}
        @Override protected List<PotionDef> entries(){return value().effects();}
        @Override protected String createKey(){return "ambience-add-effect";}
        @Override protected void create(){MobMenuBase.choose(viewer,"potions",MobMenuBase.potionKeys(),this,key->{
            var effects=new ArrayList<>(value().effects());if(effects.size()<16 && effects.stream().noneMatch(p->p.effectKey().equals(key))){effects.add(new PotionDef(key,0,false));change("effects",effects);}
        });}
        @Override protected Button entry(PotionDef effect,int index) {
            return NumericInputs.decorate(Button.of(Material.POTION,m("effect-label",Placeholder.component("type",Component.text(effect.effectKey())),Placeholder.unparsed("level",Integer.toString(effect.amplifier()+1))),
                    List.of(m("edit-effect-lore")),(p,c)->{
                if(!root.writable())return;
                if(c.isShiftClick() && c.isRightClick()){var effects=new ArrayList<>(value().effects());effects.remove(index);change("effects",effects);refresh();}
                else MenuListener.instance().later(()->{
                    if(c.isRightClick()){replace(index,new PotionDef(effect.effectKey(),effect.amplifier(),!effect.particles()));return;}
                    NumericInputs.edit(p,m("amplifier"),NumericRanges.POTION_LEVEL,effect.amplifier()+1,
                            level->replace(index,new PotionDef(effect.effectKey(),(int)level-1,effect.particles())));
                });
            }),NumericRanges.POTION_LEVEL);
        }
        private void replace(int index,PotionDef effect){if(!root.writable())return;var effects=new ArrayList<>(value().effects());if(index<effects.size()){effects.set(index,effect);change("effects",effects);refresh();}}
        @Override protected void renderHeader(){GuiTheme.help(this,List.of(m("effects-detail"),m("effects-preserve"),m("edit-effect-lore")));}
        @Override protected void renderFooter(){
            super.renderFooter();
            int slot=37;
            for(String key:List.of("effect-ticks","title-seconds","shake-ticks","density","door-density")) {
                final String field=key;
                var range=NumericRanges.ambience(key);
                var name=m(key.equals("density")?"density-advanced":key);
                set(slot++,NumericInputs.decorate(Button.of(key.contains("density")?Material.CAMPFIRE:Material.CLOCK,name,List.of(
                        m("current",Placeholder.unparsed("value",Integer.toString(value().number(key)))),
                        m("number-lore")),(p,c)->MenuListener.instance().later(()->{
                    if(!root.writable())return;
                    if(c.isRightClick()){change(field,null);refresh();return;}
                    NumericInputs.edit(p,name,range,value().number(field),n->{change(field,(int)n);refresh();});
                })),range));
            }
            set(42,Button.of(Material.NOTE_BLOCK,m("door-rumble"),List.of(m("current",Placeholder.component("value",display("door-rumble",value().text("door-rumble")))),
                    m("rumble-lore")),(p,c)->MenuListener.instance().later(()->{
                if(!root.writable())return;
                if(c.isRightClick()){change("door-rumble",null);refresh();}
                else if(c.isShiftClick())Inputs.text(p,m("door-rumble"),value().text("door-rumble"),256,key->{
                    if(AmbienceSettings.errors(new RoomAmbience(Map.of("door-rumble",key))).isEmpty()){change("door-rumble",key);refresh();}
                    else MenuListener.instance().messages().send(p,"ambience.invalid-key");
                });
                else MobMenuBase.choose(p,"sound",MobMenuBase.soundKeys(),this,key->change("door-rumble",key));
            })));
            set(43,Button.of(Material.NAME_TAG,m("door-title"),List.of(m("current",Placeholder.component("value",caption(value().text("door-title")))),m("text-lore")),(p,c)->
                    MenuListener.instance().later(()->{
                        if(!root.writable())return;
                        if(c.isRightClick()){change("door-title",null);refresh();}
                        else Inputs.text(p,m("door-title"),value().text("door-title"),256,title->{change("door-title",title);refresh();});
                    })));

        }
    }
}

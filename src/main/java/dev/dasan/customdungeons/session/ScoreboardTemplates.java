package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.text.Text;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compiled, immutable sidebar configuration. Never reads files during a session. */
final class ScoreboardTemplates {
    static final List<String> STATES=List.of("lobby-automatico","lobby-placas","entrada-sala","partida-sala","partida-jefe","completada","fallida","intro");
    private static final Set<String> PLACEHOLDERS=Set.of("dungeon","room","rooms","wave","waves","mobs_left","kills","kills_total",
            "time_left","time_total","lives","alive","players","max_players","min_players","countdown","plates","plates_total",
            "boss_phase","boss_phases","boss_health","objective","finish_countdown");
    private static final Set<String> CONDITIONS=Set.of("always","has_time_limit","finish_tp_pending",
            "has_player_limit","no_player_limit","countdown_running","below_minimum","plates_incomplete",
            "has_wave_summary","no_wave_summary","boss_has_phases","boss_without_phases","has_mobs","no_mobs",
            "waiting_room_entry","waiting_key","door_open");
    private static final Pattern TOKEN=Pattern.compile("\\{([a-z_]+)}");
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    record Frame(Component title,List<Component> lines) {
        Frame {lines=List.copyOf(lines);}
        Frame limited() {return lines.size()<=15?this:new Frame(title,lines.subList(0,15));}
    }
    private interface CompiledText {Component render(Map<String,Component> data);}
    private record Line(Component text,Set<String> required,String when,CompiledText compiled) {}
    private record Rendered(List<Component> values,Component text,boolean blank) {}
    // One result per compiled line, bounded by the configuration and confined to the host thread.
    private final Map<Line,Rendered> rendered=new IdentityHashMap<>();
    private final Map<Component,Boolean> blanks=new IdentityHashMap<>();
    private final Consumer<String> warning;
    private final Set<String> overflowWarnings=new HashSet<>();
    private final boolean enabled;
    private final long refreshTicks;
    private final Line title,footer;
    private final Map<String,List<Line>> states;
    private ScoreboardTemplates(boolean enabled,long refreshTicks,Line title,Line footer,Map<String,List<Line>> states,Consumer<String> warning) {
        this.enabled=enabled;this.refreshTicks=refreshTicks;this.title=title;this.footer=footer;this.states=Map.copyOf(states);this.warning=warning;
    }
    boolean enabled() {return enabled;}
    long refreshTicks() {return refreshTicks;}
    static ScoreboardTemplates load(ConfigurationSection yaml,Consumer<String> warning) {
        var defaults=defaults();
        Object enabled=yaml.get("scoreboard.enabled");
        if(enabled!=null && !(enabled instanceof Boolean))warning.accept("scoreboard.enabled");
        Object refresh=yaml.get("scoreboard.refresh-seconds");
        long ticks=20;
        if(refresh instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()==n.longValue() && n.longValue()>=1 && n.longValue()<=Integer.MAX_VALUE)
            ticks=n.longValue()*20;
        else if(refresh!=null)warning.accept("scoreboard.refresh-seconds");
        var title=readText(yaml,defaults,"scoreboard.title",warning);
        var footer=readText(yaml,defaults,"scoreboard.footer",warning);
        var states=new LinkedHashMap<String,List<Line>>();
        for(String state:STATES) {
            String path="scoreboard.lines."+state;
            Object raw=yaml.get(path);if(raw==null)raw=defaults.get(path);
            try {
                var lines=compileLines(raw);
                // Conditions can overlap between events and the following tick. Count every line together.
                if(maximumRows(lines,footer)>15)throw new IllegalArgumentException(path);
                states.put(state,lines);
            } catch(IllegalArgumentException invalid) {warning.accept(path);states.put(state,compileLines(defaults.get(path)));}
        }
        return new ScoreboardTemplates(enabled instanceof Boolean b?b:true,ticks,title,footer,states,warning);
    }
    private static int maximumRows(List<Line> lines,Line footer) {
        var possible=new ArrayList<Component>();
        for(Line line:lines)appendNormalized(possible,line.text());
        if(!blank(footer.text()))appendNormalized(possible,footer.text());
        while(!possible.isEmpty() && blank(possible.getLast()))possible.removeLast();
        return possible.size();
    }
    Frame limit(Frame frame,String state) {
        if(frame.lines().size()>15 && overflowWarnings.add(state)) {
            try {warning.accept("scoreboard.lines."+state+".overflow");} catch(RuntimeException ignored) {}
        }
        return frame.limited();
    }
    private static Line readText(ConfigurationSection yaml,ConfigurationSection defaults,String path,Consumer<String> warning) {
        try {return compile(yaml.contains(path)?yaml.get(path):defaults.get(path),"always");}
        catch(IllegalArgumentException invalid) {warning.accept(path);return compile(defaults.get(path),"always");}
    }
    private static List<Line> compileLines(Object raw) {
        if(!(raw instanceof List<?> list))throw new IllegalArgumentException("Expected lines");
        var lines=new ArrayList<Line>();
        for(Object item:list) {
            if(item instanceof Map<?,?> map) {
                if(!Set.of("text","when").containsAll(map.keySet()) || !(map.getOrDefault("when",null) == null || map.get("when") instanceof String))
                    throw new IllegalArgumentException("Invalid line");
                lines.add(compile(map.get("text"),map.containsKey("when")?(String)map.get("when"):"always"));
            } else lines.add(compile(item,"always"));
        }
        return List.copyOf(lines);
    }
    private static Line compile(Object raw,String when) {
        if(!(raw instanceof String text) || !CONDITIONS.contains(when))throw new IllegalArgumentException("Invalid template");
        var required=new HashSet<String>();var matcher=TOKEN.matcher(text);
        while(matcher.find()) {
            if(!PLACEHOLDERS.contains(matcher.group(1)))throw new IllegalArgumentException("Unknown placeholder");
            required.add(matcher.group(1));
        }
        if(text.replaceAll(TOKEN.pattern(),"").matches("(?s).*[{}].*"))throw new IllegalArgumentException("Malformed placeholder");
        var component=Text.parse(text);
        return new Line(component,Set.copyOf(required),when,compileText(component));
    }
    /** Compile literal fragments once; inserted values keep their own styles and remain literal. */
    private static CompiledText compileText(Component component) {
        if(!(component instanceof TextComponent text) || component.hoverEvent()!=null)
            return data->component.replaceText(b->b.match(TOKEN).replacement((match,builder)->data.get(match.group(1))));
        var children=new ArrayList<CompiledText>();var matcher=TOKEN.matcher(text.content());int end=0;
        String prefix=text.content();boolean replaced=false;
        while(matcher.find()) {
            if(!replaced){prefix=text.content().substring(0,matcher.start());replaced=true;}
            else if(matcher.start()>end){var literal=Component.text(text.content().substring(end,matcher.start()));children.add(data->literal);}
            String key=matcher.group(1);children.add(data->data.get(key));end=matcher.end();
        }
        if(replaced && end<text.content().length()){var suffix=Component.text(text.content().substring(end));children.add(data->suffix);}
        for(var child:text.children())children.add(compileText(child));
        if(!replaced && text.children().isEmpty())return data->text;
        var base=text.content(prefix).children(List.of());var parts=List.copyOf(children);
        return data->{var rendered=new ArrayList<Component>(parts.size());for(var part:parts)rendered.add(part.render(data));return base.children(rendered);};
    }
    Frame render(String state,Map<String,Component> data,Set<String> conditions) {
        blanks.clear();
        var lines=new ArrayList<Component>();
        for(Line line:states.getOrDefault(state,List.of())) {
            if(!line.when().equals("always") && !conditions.contains(line.when()))continue;
            if(!data.keySet().containsAll(line.required()))continue;
            appendRendered(lines,substitute(line,data));
        }
        if(data.keySet().containsAll(footer.required()) && !blank(footer.text()))appendRendered(lines,substitute(footer,data));
        while(!lines.isEmpty() && renderedBlank(lines.getLast()))lines.removeLast();
        return limit(new Frame(data.keySet().containsAll(title.required())?substitute(title,data):Component.empty(),lines),state);
    }
    private static void appendNormalized(List<Component> lines,Component text) {
        if(blank(text) && (lines.isEmpty() || blank(lines.getLast())))return;
        lines.add(text);
    }
    private static boolean blank(Component text) {return PLAIN.serialize(text).isBlank();}
    private void appendRendered(List<Component> lines,Component text) {
        if(renderedBlank(text) && (lines.isEmpty() || renderedBlank(lines.getLast())))return;
        lines.add(text);
    }
    private boolean renderedBlank(Component text) {return blanks.getOrDefault(text,false);}
    private Component substitute(Line line,Map<String,Component> data) {
        var values=line.required().stream().map(data::get).toList();
        var prior=rendered.get(line);
        if(prior!=null && prior.values().equals(values)) {blanks.put(prior.text(),prior.blank());return prior.text();}
        var text=line.required().isEmpty()?line.text():line.compiled().render(data);
        boolean empty=blank(text);rendered.put(line,new Rendered(values,text,empty));blanks.put(text,empty);
        return text;
    }
    private static YamlConfiguration defaults() {
        try(var in=ScoreboardTemplates.class.getResourceAsStream("/config.yml")) {
            var yaml=new YamlConfiguration();yaml.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));return yaml;
        } catch(Exception error) {throw new IllegalStateException("Missing sidebar defaults",error);}
    }
}

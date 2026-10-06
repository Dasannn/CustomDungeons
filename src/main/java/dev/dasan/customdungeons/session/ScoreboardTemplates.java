package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.text.Text;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compiled, immutable sidebar configuration. Never reads files during a session. */
final class ScoreboardTemplates {
    static final List<String> STATES=List.of("lobby-automatico","lobby-placas","entrada-sala","partida-sala","partida-jefe","completada","fallida","intro");
    private static final Set<String> PLACEHOLDERS=Set.of("dungeon","room","rooms","wave","waves","mobs_left","kills","kills_total",
            "time_left","time_total","lives","alive","players","max_players","min_players","countdown","plates","plates_total",
            "boss_phase","boss_phases","boss_health","objective","finish_countdown");
    private static final List<Set<String>> EXCLUSIVE=List.of(Set.of("has_player_limit","no_player_limit"),
            Set.of("countdown_running","below_minimum","plates_incomplete"),Set.of("has_wave_summary","no_wave_summary"),
            Set.of("boss_has_phases","boss_without_phases"),Set.of("has_mobs","no_mobs"),
            Set.of("waiting_room_entry","waiting_key","door_open"));
    private static final Set<String> CONDITIONS=new HashSet<>(Set.of("always","has_time_limit","finish_tp_pending"));
    private static final Pattern TOKEN=Pattern.compile("\\{([a-z_]+)}");
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    static {EXCLUSIVE.forEach(CONDITIONS::addAll);}
    record Frame(Component title,List<Component> lines) {
        Frame {lines=List.copyOf(lines); if(lines.size()>9)throw new IllegalArgumentException("Sidebar exceeds ten rows including title");}
    }
    private record Line(Component text,Set<String> required,String when) {}
    private final boolean enabled;
    private final long refreshTicks;
    private final Line title,footer;
    private final Map<String,List<Line>> states;
    private ScoreboardTemplates(boolean enabled,long refreshTicks,Line title,Line footer,Map<String,List<Line>> states) {
        this.enabled=enabled;this.refreshTicks=refreshTicks;this.title=title;this.footer=footer;this.states=Map.copyOf(states);
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
                // Bound alternatives by their mutually exclusive conditions; never truncate a valid frame.
                if(maximumRows(lines)+(blank(footer.text())?0:1)>9)throw new IllegalArgumentException(path);
                states.put(state,lines);
            } catch(IllegalArgumentException invalid) {warning.accept(path);states.put(state,compileLines(defaults.get(path)));}
        }
        // A custom footer may expand a default state only by one row, already reserved in defaults.
        return new ScoreboardTemplates(enabled instanceof Boolean b?b:true,ticks,title,footer,states);
    }
    private static int maximumRows(List<Line> lines) {
        int total=0;var counts=new HashMap<String,Integer>();
        for(Line line:lines)counts.merge(line.when(),1,Integer::sum);
        for(var group:EXCLUSIVE) {
            total+=group.stream().mapToInt(key->counts.getOrDefault(key,0)).max().orElse(0);group.forEach(counts::remove);
        }
        total+=counts.values().stream().mapToInt(Integer::intValue).sum();
        // Empty edges do not survive normalization without a footer; internal blanks still count.
        return total;
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
        return new Line(Text.parse(text),Set.copyOf(required),when);
    }
    Frame render(String state,Map<String,Component> data,Set<String> conditions) {
        var lines=new ArrayList<Component>();
        for(Line line:states.getOrDefault(state,List.of())) {
            if(!line.when().equals("always") && !conditions.contains(line.when()))continue;
            if(!data.keySet().containsAll(line.required()))continue;
            appendNormalized(lines,substitute(line,data));
        }
        if(data.keySet().containsAll(footer.required()) && !blank(footer.text()))appendNormalized(lines,substitute(footer,data));
        while(!lines.isEmpty() && blank(lines.getLast()))lines.removeLast();
        return new Frame(data.keySet().containsAll(title.required())?substitute(title,data):Component.empty(),lines);
    }
    private static void appendNormalized(List<Component> lines,Component text) {
        if(blank(text) && (lines.isEmpty() || blank(lines.getLast())))return;
        lines.add(text);
    }
    private static boolean blank(Component text) {return PLAIN.serialize(text).isBlank();}
    private static Component substitute(Line line,Map<String,Component> data) {
        return line.text().replaceText(b->b.match(TOKEN).replacement((match,builder)->data.get(match.group(1))));
    }
    private static YamlConfiguration defaults() {
        try(var in=ScoreboardTemplates.class.getResourceAsStream("/config.yml")) {
            var yaml=new YamlConfiguration();yaml.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));return yaml;
        } catch(Exception error) {throw new IllegalStateException("Missing sidebar defaults",error);}
    }
}

package dev.dasan.customdungeons.session;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardTemplatesTest {
    @Test void changingPlaceholderReusesItsCompiledLiteralSuffixAndPreservesStyles() throws Exception {
        var yaml=bundled();yaml.set("scoreboard.footer","");
        yaml.set("scoreboard.lines.partida-sala",List.of("<gold>Fase {boss_phase} / fijo</gold>"));
        var templates=ScoreboardTemplates.load(yaml,p->fail(p));var values=example();
        var first=templates.render("partida-sala",values,Set.of()).lines().getFirst();
        values.put("boss_phase",Component.text("3",net.kyori.adventure.text.format.NamedTextColor.GREEN));
        var second=templates.render("partida-sala",values,Set.of()).lines().getFirst();
        assertSame(findText(first," / fijo"),findText(second," / fijo"));
        var original=dev.dasan.customdungeons.text.Text.parse("<gold>Fase {boss_phase} / fijo</gold>");
        var expected=original.replaceText(b->b.matchLiteral("{boss_phase}").replacement(values.get("boss_phase")));
        assertEquals(expected.compact(),second.compact());
    }
    private static Component findText(Component root,String content) {
        if(root instanceof net.kyori.adventure.text.TextComponent text&&text.content().equals(content))return root;
        for(var child:root.children()){var found=findText(child,content);if(found!=null)return found;}return null;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"<red>{boss_phase}</red>","<red>{boss_phase} fin</red>","<gold>Inicio <bold>{boss_phase}</bold> / {mobs_left}</gold>","<hover:show_text:'{boss_phase}'>Fase {boss_phase}</hover>","{boss_phase}{mobs_left}"})
    void compiledPlaceholdersHaveTheSameVisibleTreeAsAdventureReplacement(String source) throws Exception {
        var yaml=bundled();yaml.set("scoreboard.footer","");yaml.set("scoreboard.lines.partida-sala",List.of(source));
        var values=example();values.put("boss_phase",Component.text("<red>3",net.kyori.adventure.text.format.NamedTextColor.GREEN));
        var actual=ScoreboardTemplates.load(yaml,p->fail(p)).render("partida-sala",values,Set.of()).lines().getFirst();
        var expected=dev.dasan.customdungeons.text.Text.parse(source).replaceText(b->b.match(java.util.regex.Pattern.compile("\\{([a-z_]+)}")).replacement((m,builder)->values.get(m.group(1))));
        assertEquals(expected.compact(),actual.compact());
    }
    static YamlConfiguration bundled() throws Exception {
        var yaml=new YamlConfiguration();
        try(var in=ScoreboardTemplatesTest.class.getResourceAsStream("/config.yml")) {
            yaml.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));
        }
        return yaml;
    }
    static String plain(Component value) {return PlainTextComponentSerializer.plainText().serialize(value);}
    static Map<String,Component> example() {
        var data=new HashMap<String,Component>();
        Map.ofEntries(Map.entry("dungeon","La Guarida del Warden"),Map.entry("room","2"),Map.entry("rooms","3"),
                Map.entry("wave","1"),Map.entry("waves","2"),Map.entry("mobs_left","3"),Map.entry("kills_total","8"),Map.entry("kills","3"),
                Map.entry("time_left","14:32"),Map.entry("lives","❤❤♡"),Map.entry("alive","3"),Map.entry("players","4"),
                Map.entry("max_players","4"),Map.entry("min_players","2"),Map.entry("countdown","0:12"),Map.entry("plates","2"),
                Map.entry("plates_total","3"),Map.entry("boss_phase","2"),Map.entry("boss_phases","3"),Map.entry("boss_health","58"),
                Map.entry("time_total","21:46"),Map.entry("finish_countdown","5"),Map.entry("objective","Limpia la sala"))
                .forEach((k,v)->data.put(k,Component.text(v)));
        return data;
    }
    @Test void realDefaultsRenderEveryStateWithinTenRowsAndHideAbsentData() throws Exception {
        var templates=ScoreboardTemplates.load(bundled(),p->fail(p));
        for(String state:ScoreboardTemplates.STATES) {
            var data=example();data.remove("time_left");data.remove("boss_health");data.remove("countdown");
            var frame=templates.render(state,data,Set.of("has_player_limit","below_minimum","plates_incomplete","no_wave_summary","no_mobs"));
            assertTrue(frame.lines().size()<=9,state);
            var text=frame.lines().stream().map(ScoreboardTemplatesTest::plain).toList();
            assertFalse(String.join("\n",text).contains("{"),state);
            assertFalse(text.stream().anyMatch(s->s.startsWith("Tiempo:")),state);
            assertFalse(text.stream().anyMatch(s->s.contains("Oleada")),state);
            assertFalse(text.isEmpty()); assertFalse(text.getFirst().isBlank()); assertFalse(text.getLast().isBlank());
            for(int i=1;i<text.size();i++)assertFalse(text.get(i).isBlank()&&text.get(i-1).isBlank());
        }
    }
    @Test void conditionsAndContextChooseLobbyBossAndResultRows() throws Exception {
        var templates=ScoreboardTemplates.load(bundled(),p->fail(p));
        var lobby=templates.render("lobby-automatico",example(),Set.of("has_player_limit","countdown_running"));
        assertTrue(lobby.lines().stream().map(ScoreboardTemplatesTest::plain).anyMatch(s->s.contains("Empieza en 0:12")));
        var boss=templates.render("partida-jefe",example(),Set.of("has_wave_summary","boss_has_phases","has_mobs","has_time_limit"));
        assertTrue(boss.lines().stream().map(ScoreboardTemplatesTest::plain).anyMatch(s->s.contains("Fase 2/3")&&s.contains("58%")));
        var finalData=example();finalData.put("objective",Component.text("Saliendo en 0:05"));
        var finished=templates.render("completada",finalData,Set.of("finish_tp_pending"));
        assertTrue(finished.lines().stream().map(ScoreboardTemplatesTest::plain).anyMatch(s->s.contains("Saliendo")));
        assertFalse(finished.lines().stream().map(ScoreboardTemplatesTest::plain).anyMatch(s->s.contains("Oleada")||s.contains("Mobs")||s.contains("Jefe")));
    }
    @Test void noLimitNoFooterAndSeparatorsNormalizeWithoutDroppingZero() throws Exception {
        var yaml=bundled();yaml.set("scoreboard.footer","");
        yaml.set("scoreboard.lines.partida-sala",List.of("","{dungeon}","","{time_left}","","Mobs {mobs_left}","",""));
        var data=example();data.remove("time_left");data.put("mobs_left",Component.text("0"));
        assertEquals(List.of("La Guarida del Warden","","Mobs 0"),ScoreboardTemplates.load(yaml,p->fail(p))
                .render("partida-sala",data,Set.of()).lines().stream().map(ScoreboardTemplatesTest::plain).toList());
    }
    @Test void colorsParseButPlaceholderNamesAreInsertedAsLiteralText() throws Exception {
        var yaml=bundled();yaml.set("scoreboard.lines.partida-sala",List.of("&#C4B5FD{dungeon}","<green>{room}</green>"));
        var data=example();data.put("dungeon",Component.text("<red>&aInjected"));
        var frame=ScoreboardTemplates.load(yaml,p->fail(p)).render("partida-sala",data,Set.of());
        assertEquals("<red>&aInjected",plain(frame.lines().getFirst()));
        assertEquals(0xC4B5FD,frame.lines().getFirst().color().value());
    }
    @Test void invalidPlaceholdersConditionsTypesAndOverflowWarnAndUseStateDefaults() throws Exception {
        for(Object bad:List.of(List.of("{unknown}"),List.of(Map.of("text","x","when","arbitrary")),List.of(3),Collections.nCopies(16,"x"),"wrong type")) {
            var yaml=bundled();yaml.set("scoreboard.lines.partida-sala",bad);var warnings=new ArrayList<String>();
            var templates=ScoreboardTemplates.load(yaml,warnings::add);
            templates.render("partida-sala",example(),Set.of("has_wave_summary","has_mobs","has_time_limit"));
            assertFalse(warnings.isEmpty(),bad.toString());
        }
    }
    @Test void overlappingPlateConditionsAreValidatedTogetherAndNeverThrowAtRender() throws Exception {
        var yaml=bundled();yaml.set("scoreboard.footer","");
        var lines=new ArrayList<Object>();
        for(int i=0;i<8;i++)lines.add(Map.of("text","Countdown "+i,"when","countdown_running"));
        for(int i=0;i<8;i++)lines.add(Map.of("text","Plate "+i,"when","plates_incomplete"));
        yaml.set("scoreboard.lines.lobby-placas",lines);
        var warnings=new ArrayList<String>();var templates=ScoreboardTemplates.load(yaml,warnings::add);
        assertTrue(warnings.contains("scoreboard.lines.lobby-placas"));
        assertTrue(assertDoesNotThrow(()->templates.render("lobby-placas",example(),Set.of("countdown_running","plates_incomplete"))).lines().size()<=15);
    }
    @Test void allKnownConditionsCanCoexistWithoutOverflowInAnyDefaultState() throws Exception {
        var templates=ScoreboardTemplates.load(bundled(),p->fail(p));
        var conditions=Set.of("has_player_limit","no_player_limit","countdown_running","below_minimum","plates_incomplete",
                "has_wave_summary","no_wave_summary","boss_has_phases","boss_without_phases","has_mobs","no_mobs",
                "waiting_room_entry","waiting_key","door_open","has_time_limit","finish_tp_pending");
        for(String state:ScoreboardTemplates.STATES)assertTrue(assertDoesNotThrow(()->templates.render(state,example(),conditions)).lines().size()<=15,state);
    }

    @Test void unexpectedRuntimeOverflowTruncatesToFifteenAndWarnsOnlyOncePerState() throws Exception {
        var warnings=new ArrayList<String>();var templates=ScoreboardTemplates.load(bundled(),warnings::add);
        var oversized=new ScoreboardTemplates.Frame(Component.text("title"),Collections.nCopies(100,Component.text("row")));
        for(int i=0;i<3;i++)assertEquals(15,assertDoesNotThrow(()->templates.limit(oversized,"lobby-placas")).lines().size());
        assertEquals(List.of("scoreboard.lines.lobby-placas.overflow"),warnings);
    }

    @Test void unchangedRowsShareComponentsAcrossPersonalAndTimerUpdates() throws Exception {
        var templates=ScoreboardTemplates.load(bundled(),p->fail(p));
        var values=example();var conditions=Set.of("has_wave_summary","boss_has_phases","has_mobs","has_time_limit");
        var first=templates.render("partida-jefe",values,conditions);
        var firstBoss=first.lines().stream().filter(c->plain(c).startsWith("Fase ")).findFirst().orElseThrow();
        values.put("kills",Component.text("99"));values.put("time_left",Component.text("14:31"));
        var second=templates.render("partida-jefe",values,conditions);
        var secondBoss=second.lines().stream().filter(c->plain(c).startsWith("Fase ")).findFirst().orElseThrow();
        assertSame(firstBoss,secondBoss,"Unchanged placeholders must not rebuild the common boss row");
        assertTrue(second.lines().stream().anyMatch(c->plain(c).contains("Tuyas 99")));
        assertTrue(second.lines().stream().anyMatch(c->plain(c).contains("14:31")));
        values.put("boss_health",Component.text("1"));
        var third=templates.render("partida-jefe",values,conditions);
        assertTrue(third.lines().stream().anyMatch(c->plain(c).contains("Jefe 1%")));
    }

}

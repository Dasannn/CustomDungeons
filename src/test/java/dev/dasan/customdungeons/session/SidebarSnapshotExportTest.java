package dev.dasan.customdungeons.session;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline projection of the actual compiled templates, with the approved mockup fixtures. */
class SidebarSnapshotExportTest {
    private final Map<String,Object> snapshots=new LinkedHashMap<>();
    private final LegacyComponentSerializer legacy=LegacyComponentSerializer.builder().character('&').hexColors().build();
    @Test void exportActualTemplatesForApprovedExamplesAndContextualObjectives() throws Exception {
        var yaml=ScoreboardTemplatesTest.bundled();var templates=ScoreboardTemplates.load(yaml,p->fail(p));
        var data=ScoreboardTemplatesTest.example();
        data.put("players",Component.text(2));
        export("lobby-automatico",templates.render("lobby-automatico",data,Set.of("has_player_limit","countdown_running")));
        data.put("players",Component.text(3));data.put("min_players",Component.text(3));
        data.put("objective",SidebarDataTest.messages().get("scoreboard.objective.plates",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("plates","2"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("total","3")));
        export("lobby-placas",templates.render("lobby-placas",data,Set.of("has_player_limit","plates_incomplete")));
        data=ScoreboardTemplatesTest.example();data.put("lives",SidebarData.hearts(SidebarDataTest.messages(),2,3));
        var combat=Set.of("has_wave_summary","has_mobs","has_time_limit");
        export("partida-sala",templates.render("partida-sala",data,combat));
        var boss=new HashMap<>(data);boss.put("room",Component.text(3));boss.put("wave",Component.text(2));
        boss.put("mobs_left",Component.text(2));boss.put("kills_total",Component.text(19));boss.put("kills",Component.text(7));boss.put("time_left",Component.text("10:08"));
        export("partida-jefe",templates.render("partida-jefe",boss,Set.of("has_wave_summary","has_mobs","has_time_limit","boss_has_phases")));
        var end=new HashMap<>(data);end.put("kills_total",Component.text(21));end.put("kills",Component.text(8));
        end.put("objective",SidebarDataTest.messages().get("scoreboard.objective.exiting",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("countdown","0:05")));
        export("final",templates.render("completada",end,Set.of("finish_tp_pending")));
        export("fallida",templates.render("fallida",end,Set.of("finish_tp_pending")));
        var entry=new HashMap<>(data);entry.remove("wave");entry.remove("waves");
        for(String objective:List.of("enter-room","collect-key","use-key","puzzle","boss","exit-plate")) {
            entry.put("objective",SidebarDataTest.messages().get("scoreboard.objective."+objective));
            export("objetivo-"+objective,templates.render(objective.equals("exit-plate")?"completada":"entrada-sala",entry,Set.of("no_wave_summary","has_time_limit")));
        }
        entry.put("objective",SidebarDataTest.messages().get("scoreboard.objective.clear"));
        entry.put("time_left",Component.text("0:45",net.kyori.adventure.text.format.NamedTextColor.RED));
        export("tiempo-rojo",templates.render("partida-sala",entry,Set.of("no_wave_summary","no_mobs","has_time_limit")));
        export("intro",templates.render("intro",data,Set.of("has_time_limit")));
        yaml.set("scoreboard.title","<bold><#7dd3fc>NEXO</#7dd3fc> <white>REALMS</white></bold>");
        yaml.set("scoreboard.footer","<dark_gray>play.</dark_gray><#7dd3fc>nexorealms.net</#7dd3fc>");
        yaml.set("scoreboard.lines.partida-sala",List.of("&#C4B5FD{dungeon}","",
                "<gray>Sala <#86efac>{room}/{rooms}</#86efac> <dark_gray>·</dark_gray> Oleada <#7dd3fc>{wave}/{waves}</#7dd3fc></gray>",
                "&#94A3B8Mobs en sala: &#F8FAFC{mobs_left}","&#94A3B8Bajas grupo &#F8FAFC{kills_total} &8· &#94A3B8Tuyas &#F8FAFC{kills}",
                "&#94A3B8Tiempo: &#FDE68A{time_left}","&#94A3B8Vidas &#86EFAC{lives} &8· &#94A3B8Vivos &#86EFAC{alive}&#94A3B8/{players}",""));
        export("config-alternativa",ScoreboardTemplates.load(yaml,p->fail(p)).render("partida-sala",data,combat));
        Path output=Path.of("build/scoreboard-snapshots");Files.createDirectories(output);
        Files.writeString(output.resolve("sidebars.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshots));
        assertEquals(15,snapshots.size());
    }
    private void export(String name,ScoreboardTemplates.Frame frame) {
        assertTrue(frame.lines().size()<=9,name);
        snapshots.put(name,Map.of("title",legacy.serialize(frame.title()),"lines",frame.lines().stream().map(legacy::serialize).toList()));
    }
}

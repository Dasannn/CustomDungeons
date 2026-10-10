package dev.dasan.customdungeons.session;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import dev.dasan.customdungeons.text.Messages;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SidebarFormatCacheTest {
    Messages catalog(String full) {
        var yaml=new YamlConfiguration();yaml.set("scoreboard.hearts-full",full);yaml.set("scoreboard.hearts-empty","♡");yaml.set("scoreboard.objective.boss","Jefe");
        var messages=new Messages();messages.load(yaml,"");return messages;
    }
    SidebarData.Inputs inputs(Messages messages,int kills,int maximum) {
        return new SidebarData.Inputs("partida-jefe",Map.of("dungeon","<green>Arena","lives",2,"kills",kills,"time_left",60),Set.of(),"boss",maximum,messages.revision());
    }
    @Test void changingPersonalValuesDoesNotReparseCommonCaptions() {
        var messages=catalog("❤");var formatter=new SidebarData.Formatter();
        var first=formatter.format(inputs(messages,0,3),messages);
        var second=formatter.format(inputs(messages,1,3),messages);
        assertEquals(SidebarData.format(inputs(messages,1,3),messages),second);
        for(String key:List.of("dungeon","lives","time_left","objective"))assertSame(first.values().get(key),second.values().get(key),key);
        assertNotEquals(first.values().get("kills"),second.values().get("kills"));
    }
    @Test void reloadAndLifeLimitInvalidateTheRelevantCachedComponents() {
        var messages=catalog("❤");var formatter=new SidebarData.Formatter();
        var first=formatter.format(inputs(messages,0,3),messages);
        var second=formatter.format(inputs(messages,0,4),messages);
        assertNotEquals(first.values().get("lives"),second.values().get("lives"));
        var yaml=new YamlConfiguration();yaml.set("scoreboard.hearts-full","X");yaml.set("scoreboard.hearts-empty","O");yaml.set("scoreboard.objective.boss","Otro");messages.load(yaml,"");
        var third=formatter.format(inputs(messages,0,4),messages);
        assertEquals(SidebarData.format(inputs(messages,0,4),messages),third);
        assertNotEquals(second.values().get("lives"),third.values().get("lives"));
        assertNotEquals(second.values().get("objective"),third.values().get("objective"));
    }
}

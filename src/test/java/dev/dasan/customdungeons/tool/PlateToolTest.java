package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlateToolTest {
    @org.junit.jupiter.api.BeforeAll static void api(){dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @Test void placesRealPlateRegistersItAndRightClickRemovesIt() {
        var messages=mock(Messages.class);var tool=new PlateTool(messages);
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        var world=mock(World.class);when(world.getName()).thenReturn("world");
        var support=mock(Block.class);var block=mock(Block.class);
        var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);when(support.getType()).thenReturn(solid);when(block.getType()).thenReturn(Material.AIR);
        when(support.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(block);
        when(block.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(support);
        when(block.getWorld()).thenReturn(world);when(block.getY()).thenReturn(64);
        var d=new DefinitionCodec().decodeDungeon("demo",new YamlConfiguration());
        var points=new ArrayList<Point>();
        tool.editors=(p,id)->new PlateTool.Editor() {
            public DungeonDef definition(){return d.withStart(StartMode.PLATES,points,3,null,false,true,false,10);}
            public boolean update(List<Point> updated){points.clear();points.addAll(updated);return true;}
        };
        tool.edit(player,"demo",support,true);
        assertEquals(1,points.size());verify(block).setType(Material.STONE_PRESSURE_PLATE,false);
        when(block.getType()).thenReturn(Material.STONE_PRESSURE_PLATE);
        tool.edit(player,"demo",block,true);assertTrue(points.isEmpty());verify(block).setType(Material.AIR,false);
    }
    @Test void exitToolUsesBlackstoneAndDoesNotChangeMinimum() {
        var messages=mock(Messages.class);var tool=new PlateTool(messages);
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        var world=mock(World.class);when(world.getName()).thenReturn("world");
        var support=mock(Block.class);var block=mock(Block.class);
        var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);when(support.getType()).thenReturn(solid);when(block.getType()).thenReturn(Material.AIR);
        when(support.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(block);when(block.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(support);
        when(block.getWorld()).thenReturn(world);when(block.getY()).thenReturn(64);
        var d=new DefinitionCodec().decodeDungeon("demo",new YamlConfiguration());var points=new ArrayList<Point>();
        tool.editors=(p,id)->new PlateTool.Editor() {
            public DungeonDef definition(){return d.withFinish(FinishMode.NONE,60,FinishDestination.EXIT,points);}
            public boolean update(List<Point> updated){fail("must not edit start plates");return false;}
            public boolean updateExit(List<Point> updated){points.clear();points.addAll(updated);return true;}
        };
        tool.edit(player,"demo",support,true,true);assertEquals(1,points.size());verify(block).setType(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,false);
        verify(messages).send(eq(player),eq("tool.exit-plate-added"),any(),any());
        when(block.getType()).thenReturn(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);tool.edit(player,"demo",block,true,true);
        assertTrue(points.isEmpty());verify(block).setType(Material.AIR,false);
    }
    @Test void buildUndoValidatesAllChangedBlocksBeforeMutatingAndNeverLoadsChunksSynchronously() {
        var tool=new PlateTool(mock(Messages.class));var player=mock(Player.class);when(player.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        var base=new DefinitionCodec().decodeDungeon("demo",new YamlConfiguration());
        var d=base.withStart(StartMode.PLATES,List.of(new Point("world",1.5,64,1.5,0,0),new Point("world",2.5,64,1.5,0,0)),3,null,false,true,false,10);
        var world=mock(World.class);var first=mock(Block.class);var second=mock(Block.class);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getBlockAt(1,64,1)).thenReturn(first);when(world.getBlockAt(2,64,1)).thenReturn(second);
        when(first.getType()).thenReturn(Material.STONE_PRESSURE_PLATE);when(second.getType()).thenReturn(Material.DIAMOND_BLOCK);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            assertFalse(tool.restoreBuild(player,d,base));verify(first,never()).setType(any(),anyBoolean());verify(second,never()).setType(any(),anyBoolean());
            clearInvocations(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            assertFalse(tool.restoreBuild(player,d,base));verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        }
    }
    @Test void noWorldMutationWithoutEditPermissionOrWithRejectedDraft() {
        var tool=new PlateTool(mock(Messages.class));var player=mock(Player.class);var clicked=mock(Block.class);
        tool.edit(player,"demo",clicked,true);verify(clicked,never()).setType(any(),anyBoolean());
        when(player.hasPermission(anyString())).thenReturn(true);
        tool.edit(player,"demo",clicked,true);verify(clicked,never()).setType(any(),anyBoolean());
    }
}

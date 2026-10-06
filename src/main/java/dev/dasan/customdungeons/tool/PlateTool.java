package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.function.BiFunction;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;

/** World mutations are permitted only through the current, writable dungeon draft. */
final class PlateTool {
    interface Editor {
        DungeonDef definition();
        boolean update(List<Point> points);
        default boolean updateExit(List<Point> points) { return false; }
    }
    BiFunction<Player,String,Editor> editors=(player,id)->null;
    private final Messages messages;
    PlateTool(Messages messages) {this.messages=messages;}
    static boolean at(Point point,Block block) {
        return point.world().equals(block.getWorld().getName()) && (int)Math.floor(point.x())==block.getX()
                && (int)Math.floor(point.y())==block.getY() && (int)Math.floor(point.z())==block.getZ();
    }
    private record Cell(String world,int x,int y,int z) {
        static Cell at(Point p) {return new Cell(p.world(),(int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    }
    private static Map<Cell,Material> placements(DungeonDef d) {
        var result=new LinkedHashMap<Cell,Material>();
        d.plates().forEach(p->result.put(Cell.at(p),Material.STONE_PRESSURE_PLATE));
        d.exitPlates().forEach(p->result.put(Cell.at(p),Material.POLISHED_BLACKSTONE_PRESSURE_PLATE));return result;
    }
    boolean restoreBuild(Player player,DungeonDef before,DungeonDef after) {
        if(!player.hasPermission("customdungeons.admin.edit"))return false;
        var old=placements(before);var next=placements(after);var cells=new LinkedHashSet<>(old.keySet());cells.addAll(next.keySet());
        var changes=new LinkedHashMap<Block,Material>();
        for(var cell:cells) {
            Material expected=old.getOrDefault(cell,Material.AIR),desired=next.getOrDefault(cell,Material.AIR);
            if(expected==desired)continue;
            var world=org.bukkit.Bukkit.getWorld(cell.world());
            if(world==null || !world.isChunkLoaded(cell.x()>>4,cell.z()>>4)) {messages.send(player,"build.undo-plate-unavailable");return false;}
            var block=world.getBlockAt(cell.x(),cell.y(),cell.z());
            if(block.getType()!=expected || desired!=Material.AIR && !block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                messages.send(player,"build.undo-plate-unavailable");return false;
            }
            changes.put(block,desired);
        }
        // Validate every block before the first mutation: never partially undo or erase an admin edit.
        changes.forEach((block,material)->block.setType(material,false));return true;
    }
    void edit(Player player,String id,Block clicked,boolean right) {
        edit(player,id,clicked,right,false);
    }
    void edit(Player player,String id,Block clicked,boolean right,boolean exit) {
        if(!player.hasPermission("customdungeons.admin.tools") || !player.hasPermission("customdungeons.admin.edit")) {
            messages.send(player,"tool.plate-edit-denied");return;
        }
        Editor editor=editors.apply(player,id);
        if(editor==null){messages.send(player,"tool.plate-no-editor");return;}
        edit(player,editor,clicked,right,exit,false);
    }
    void editBuild(Player player,Editor editor,Block clicked,boolean right,boolean exit) {
        if(!player.hasPermission("customdungeons.admin.edit")) {messages.send(player,"tool.plate-edit-denied");return;}
        // Right-click removal follows the registered type, regardless of Shift.
        if(right) {
            if(editor.definition().exitPlates().stream().anyMatch(p->at(p,clicked)))exit=true;
            else if(editor.definition().plates().stream().anyMatch(p->at(p,clicked)))exit=false;
        }
        edit(player,editor,clicked,right,exit,true);
    }
    private void edit(Player player,Editor editor,Block clicked,boolean right,boolean exit,boolean building) {
        Material material=exit?Material.POLISHED_BLACKSTONE_PRESSURE_PLATE:Material.STONE_PRESSURE_PLATE;
        DungeonDef d=editor.definition();var points=new ArrayList<>(exit?d.exitPlates():d.plates());
        int index=-1;
        for(int i=0;i<points.size();i++)if(at(points.get(i),clicked)){index=i;break;}
        if(index>=0) {
            if(!right)return;
            points.remove(index);
            if(!(exit?editor.updateExit(List.copyOf(points)):editor.update(List.copyOf(points))))return;
            if(clicked.getType()==material)clicked.setType(Material.AIR,false);
            messages.send(player,exit?"tool.exit-plate-removed":"tool.plate-removed",Placeholder.unparsed("count",Integer.toString(points.size())));return;
        }
        Block plate=clicked.getType()==material?clicked:clicked.getRelative(BlockFace.UP);
        var point=new Point(plate.getWorld().getName(),plate.getX()+.5,plate.getY(),plate.getZ()+.5,0,0);
        if((building && !plate.getType().isAir()) || (!plate.getType().isAir() && plate.getType()!=material)
                || !plate.getRelative(BlockFace.DOWN).getType().isSolid()
                || d.area()!=null && !d.area().contains(point.world(),plate.getX(),plate.getY(),plate.getZ())) {
            messages.send(player,"tool.plate-invalid");return;
        }
        if(points.stream().anyMatch(p->at(p,plate)))return;
        points.add(point);
        if(!(exit?editor.updateExit(List.copyOf(points)):editor.update(List.copyOf(points))))return;
        plate.setType(material,false);
        var number=Placeholder.unparsed("number",Integer.toString(points.size()));
        var count=Placeholder.unparsed("count",Integer.toString(points.size()));
        messages.send(player,exit?"tool.exit-plate-added":"tool.plate-added",number,count);
        player.sendActionBar(messages.get(exit?"tool.exit-plate-added":"tool.plate-added",number,count));
    }
}

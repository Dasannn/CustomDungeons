package dev.dasan.customdungeons.storage;


public record TempBlockRecord(String world, int x, int y, int z, String originalBlockData,String placedBlockData) {
    /** Legacy dungeon records described an opening to air. Preserve that recovery behavior. */
    public TempBlockRecord(String world,int x,int y,int z,String originalBlockData) {
        this(world,x,y,z,originalBlockData,"minecraft:air");
    }
}

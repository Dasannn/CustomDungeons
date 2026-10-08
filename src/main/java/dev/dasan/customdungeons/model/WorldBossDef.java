package dev.dasan.customdungeons.model;

/** Optional world encounter settings; dungeon waves ignore this section. */
public record WorldBossDef(String world, int xMin, int xMax, int zMin, int zMax,
                           int maxAlive, int radius, double minimumDamage, RewardDef reward) {
    public static WorldBossDef defaults(String world) {
        return new WorldBossDef(world,-2000,2000,-2000,2000,1,48,5,new RewardDef(java.util.List.of(),0,0,java.util.List.of()));
    }
    public WorldBossDef withReward(RewardDef value) {
        return new WorldBossDef(world,xMin,xMax,zMin,zMax,maxAlive,radius,minimumDamage,value);
    }
}

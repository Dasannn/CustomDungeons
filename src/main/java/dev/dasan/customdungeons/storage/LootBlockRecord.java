package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.model.Point;

/** Placement original survives definition saves; session original survives evacuation and crashes. */
public record LootBlockRecord(Point position,String dungeon,String room,String chest,String placementData,
                              String sessionData,boolean placed,boolean sessionActive,String expectedData) {
    public LootBlockRecord(Point position,String dungeon,String room,String chest,String placementData,
                           String sessionData,boolean placed,boolean sessionActive) {
        this(position,dungeon,room,chest,placementData,sessionData,placed,sessionActive,null);
    }
    private static final String CANCELLED_PREPARATION="cancelled-preparation-v1";
    /** A durable no-op receipt; placement history remains available for an explicit administrative remove. */
    public LootBlockRecord cancelPreparation() {
        return new LootBlockRecord(position,dungeon,room,chest,placementData,CANCELLED_PREPARATION,placed,false,expectedData);
    }
    public boolean preparationCancelled() {return CANCELLED_PREPARATION.equals(sessionData);}
    public LootBlockRecord prepared(String data) {
        return new LootBlockRecord(position,dungeon,room,chest,placementData,data,placed,true,expectedData);
    }
    public LootBlockRecord restored() {
        return new LootBlockRecord(position,dungeon,room,chest,placementData,sessionData,placed,false,expectedData);
    }
    public LootBlockRecord removed() {
        return removed(sessionData!=null && !preparationCancelled()?sessionData:expectedData);
    }
    public LootBlockRecord removed(String source) {
        return new LootBlockRecord(position,dungeon,room,chest,placementData,null,false,true,source);
    }
}

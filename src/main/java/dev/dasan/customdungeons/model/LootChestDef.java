package dev.dasan.customdungeons.model;

import java.util.List;

public record LootChestDef(String id, Point location, Trigger trigger, List<LootEntryDef> entries) {
    public enum Trigger { ROOM_CLEAR, ROOM_ENTER }
    public LootChestDef {
        entries = List.copyOf(entries);
        if (location != null) location = new Point(location.world(),Math.floor(location.x()),
                Math.floor(location.y()),Math.floor(location.z()),0,0);
    }
}

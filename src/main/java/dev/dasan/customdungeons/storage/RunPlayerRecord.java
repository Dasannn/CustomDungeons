package dev.dasan.customdungeons.storage;

import java.util.UUID;

public record RunPlayerRecord(UUID player, int kills, int deaths, boolean survived, boolean rewarded) {}

package dev.dasan.customdungeons.session;

/** Stable identity of queued orders and entities, independent of roomIndex and position. */
record SpawnOrigin(Kind kind, String id, String spawner) {
    enum Kind { ROOM, ZONE }
    boolean sameRegion(SpawnOrigin other) { return other!=null && kind==other.kind && id.equals(other.id); }
    String regionKey() { return kind.name().toLowerCase(java.util.Locale.ROOT)+":"+id; }
}

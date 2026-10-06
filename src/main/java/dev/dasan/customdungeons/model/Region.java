package dev.dasan.customdungeons.model;

public record Region(String world, BlockPos min, BlockPos max) {
    public static Region of(String world, BlockPos a, BlockPos b) {
        return new Region(world,
                new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }
    public boolean contains(String world, int x, int y, int z) {
        return this.world.equals(world) && x >= min.x() && x <= max.x()
                && y >= min.y() && y <= max.y() && z >= min.z() && z <= max.z();
    }
    public long volume() {
        return ((long) max.x() - min.x() + 1) * ((long) max.y() - min.y() + 1)
                * ((long) max.z() - min.z() + 1);
    }
}

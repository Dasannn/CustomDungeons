package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.model.BlockPos;
import dev.dasan.customdungeons.model.Region;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Block-inclusive selection; endpoints may arrive in either order. */
public record Selection(String world, @Nullable BlockPos a, @Nullable BlockPos b) {
    public Selection { Objects.requireNonNull(world, "world"); }
    public boolean complete() { return a != null && b != null; }
    public Region toRegion() {
        if (!complete()) throw new IllegalStateException("Incomplete selection");
        return Region.of(world, a, b);
    }
}

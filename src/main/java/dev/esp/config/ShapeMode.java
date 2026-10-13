package dev.esp.config;

/**
 * Shape modes used by the renderer: legacy box modes plus the hitbox-based option that draws each
 * target's actual bounding box at draw time rather than the stored snapshot box.
 */
public enum ShapeMode {
    /** Legacy axis-aligned box, as stored in the snapshot. */
    BOX("Snapshot Box"),

    /** Draw the target's actual bounding box each frame (entity hitboxes, item boxes, block boxes).
     * For entities this is the real {@link net.minecraft.entity.Entity#getBoundingBox()}, so an iron
     * golem, player or armor stand gets its real shape instead of a store-sized box. */
    HITBOX("Hitbox");

    private final String displayName;

    ShapeMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean lines() {
        return this == BOX || this == HITBOX;
    }

    public boolean sides() {
        return this == BOX || this == HITBOX;
    }
}

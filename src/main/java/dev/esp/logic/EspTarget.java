package dev.esp.logic;

import dev.esp.config.EspCategory;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * One thing the renderer can draw: a world space box plus the colour context for it.
 *
 * <p>Instances are produced by the scanners and never mutated afterwards, so the list published by
 * the collector stays valid while the next scan runs.
 */
public final class EspTarget {
    private final EspCategory category;
    private final TargetKind kind;
    private final EntityGroup group;

    /** The registry entry this came from ({@code Block}, {@code Item} or {@code EntityType}), kept
     * for the per entry colour lookup in the renderer. May be {@code null}. */
    private final Object entry;

    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    private final Entity entity;
    private final boolean friend;
    private final float healthFraction;

    private EspTarget(
        EspCategory category, TargetKind kind, EntityGroup group, Object entry,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        Entity entity, boolean friend, float healthFraction
    ) {
        this.category = category;
        this.kind = kind;
        this.group = group;
        this.entry = entry;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.entity = entity;
        this.friend = friend;
        this.healthFraction = healthFraction;
    }

    public static EspTarget ofBlock(BlockPos pos, Block block) {
        return new EspTarget(
            EspCategory.BLOCK, TargetKind.BLOCK, null, block,
            pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
            null, false, -1f
        );
    }

    public static EspTarget ofItem(Box box, Entity entity, Item item) {
        return new EspTarget(
            EspCategory.ITEM, TargetKind.ITEM, null, item,
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            entity, false, -1f
        );
    }

    public static EspTarget ofEntity(
        Box box, Entity entity, EntityGroup group, boolean friend, float healthFraction,
        EntityType<?> type
    ) {
        return new EspTarget(
            EspCategory.ENTITY, TargetKind.ENTITY, group, type,
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            entity, friend, healthFraction
        );
    }

    public EspCategory category() {
        return category;
    }

    public TargetKind kind() {
        return kind;
    }

    public EntityGroup group() {
        return group;
    }

    /** @return the registry entry this target came from: a {@code Block}, {@code Item} or
     * {@code EntityType}, or {@code null} when the target has none */
    public Object entry() {
        return entry;
    }

    public Entity entity() {
        return entity;
    }

    public boolean friend() {
        return friend;
    }

    /** @return health as 0..1, or a negative value when the target has no health */
    public float healthFraction() {
        return healthFraction;
    }

    /** @return the target's box as of the last scan; entity targets may interpolate past this */
    public Box box() {
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    public double centerY() {
        return (minY + maxY) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ) / 2.0;
    }

    public double distanceSq(double x, double y, double z) {
        double dx = centerX() - x;
        double dy = centerY() - y;
        double dz = centerZ() - z;

        return dx * dx + dy * dy + dz * dz;
    }

    public static float healthFractionOf(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return -1f;

        float max = living.getMaxHealth();
        if (max <= 0f) return -1f;

        return Math.min(1f, Math.max(0f, living.getHealth() / max));
    }

    public static boolean isFriendTarget(Entity entity, Predicate<String> isFriend) {
        if (!(entity instanceof PlayerEntity player)) return false;

        return isFriend.test(player.getName().getString());
    }

    /** @return the entity's box moved to its interpolated position, so movement looks smooth */
    public static Box interpolatedBox(Entity entity, float tickDelta) {
        Box box = entity.getBoundingBox();
        if (tickDelta <= 0f) return box;

        Vec3d pos = entity.getEntityPos();
        double t = Math.min(1f, tickDelta);

        double dx = (entity.lastX + (pos.x - entity.lastX) * t) - pos.x;
        double dy = (entity.lastY + (pos.y - entity.lastY) * t) - pos.y;
        double dz = (entity.lastZ + (pos.z - entity.lastZ) * t) - pos.z;

        return box.offset(dx, dy, dz);
    }

    public Vec3d center() {
        return new Vec3d(centerX(), centerY(), centerZ());
    }
}

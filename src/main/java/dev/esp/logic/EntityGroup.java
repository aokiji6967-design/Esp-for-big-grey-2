package dev.esp.logic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;

/**
 * Sub-categories shown in the entity selector.
 *
 * <p>Groups are derived from the registry rather than from a hand written list, so every vanilla
 * entity (and anything another mod registers) lands in the right tab automatically. Mob groups come
 * from {@link EntityType#getSpawnGroup()} and projectiles from the entity's base class.
 */
public enum EntityGroup {
    ALL("All"),
    PLAYER("Players"),
    HOSTILE("Hostile"),
    PASSIVE("Passive"),
    WATER("Water"),
    AMBIENT("Ambient"),
    UTILITY("Utility"),
    PROJECTILE("Projectiles");

    private final String displayName;

    EntityGroup(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static EntityGroup of(EntityType<?> type) {
        if (type == null) return UTILITY;
        if (type == EntityType.PLAYER) return PLAYER;

        Class<? extends Entity> baseClass = type.getBaseClass();
        if (baseClass != null && ProjectileEntity.class.isAssignableFrom(baseClass)) {
            return PROJECTILE;
        }

        return switch (type.getSpawnGroup()) {
            case MONSTER -> HOSTILE;
            case CREATURE -> PASSIVE;
            case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> WATER;
            case AMBIENT -> AMBIENT;
            default -> UTILITY;
        };
    }
}

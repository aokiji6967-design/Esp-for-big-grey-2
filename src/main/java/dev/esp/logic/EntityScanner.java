package dev.esp.logic;

import dev.esp.config.EspCategory;
import dev.esp.config.EspConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Collects dropped items and entities once per client tick.
 *
 * <p>Entity iteration is cheap compared to the block sweep, so this runs every tick and produces the
 * full snapshot the renderer draws from. Player specific filters (self, friends, invisible) are
 * applied here so the renderer stays free of game logic.
 *
 * <p>The scan covers the whole effective range - at least the client's render distance - and the
 * results are published closest first, so the per frame target cap drops what is furthest away
 * instead of whatever the world's entity order happened to return first.
 */
public final class EntityScanner {
    private List<EspTarget> items = Collections.emptyList();
    private List<EspTarget> entities = Collections.emptyList();

    public List<EspTarget> itemSnapshot() {
        return items;
    }

    public List<EspTarget> entitySnapshot() {
        return entities;
    }

    public void reset() {
        items = Collections.emptyList();
        entities = Collections.emptyList();
    }

    public void tick(MinecraftClient client, EspConfig config, double range) {
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;

        if (world == null || player == null) {
            reset();
            return;
        }

        double rangeSq = range * range;
        int maxTargets = config.render().maxTargets();

        boolean itemsEnabled = config.isEnabled(EspCategory.ITEM);
        boolean entitiesEnabled = config.isEnabled(EspCategory.ENTITY);

        List<EspTarget> foundItems = new ArrayList<>();
        List<EspTarget> foundEntities = new ArrayList<>();

        for (Entity entity : world.getEntities()) {
            if (entity.squaredDistanceTo(player) > rangeSq) continue;

            if (entity instanceof ItemEntity itemEntity) {
                if (!itemsEnabled) continue;
                if (!isSelectedItem(config, itemEntity.getStack())) continue;

                foundItems.add(EspTarget.ofItem(
                    itemEntity.getBoundingBox(), itemEntity, itemEntity.getStack().getItem()
                ));
                continue;
            }

            if (!entitiesEnabled) continue;
            if (!config.entities().isEnabled(entity.getType())) continue;
            if (!passesPlayerFilters(entity, player, config)) continue;

            foundEntities.add(EspTarget.ofEntity(
                entity.getBoundingBox(),
                entity,
                EntityGroup.of(entity.getType()),
                EspTarget.isFriendTarget(entity, config::isFriend),
                EspTarget.healthFractionOf(entity),
                entity.getType()
            ));
        }

        items = nearestFirst(foundItems, player, maxTargets);
        entities = nearestFirst(foundEntities, player, maxTargets);
    }

    /** Publishes the closest targets first, capped, so the draw budget is spent on what is near. */
    private static List<EspTarget> nearestFirst(
        List<EspTarget> found, ClientPlayerEntity player, int maxTargets
    ) {
        if (found.isEmpty()) return Collections.emptyList();

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        found.sort(Comparator.<EspTarget>comparingDouble(target -> target.distanceSq(px, py, pz)));

        return List.copyOf(found.subList(0, Math.min(found.size(), maxTargets)));
    }

    private static boolean isSelectedItem(EspConfig config, ItemStack stack) {
        return !stack.isEmpty() && config.items().isEnabled(stack.getItem());
    }

    private static boolean passesPlayerFilters(Entity entity, ClientPlayerEntity self, EspConfig config) {
        if (!(entity instanceof PlayerEntity player)) return true;

        if (player == self) return config.players().renderSelf();
        if (!config.players().renderOthers()) return false;
        if (player.isSpectator()) return false;
        if (player.isInvisible() && !config.players().renderInvisible()) return false;
        if (config.players().onlyFriends() && !config.isFriend(player.getName().getString())) return false;

        return true;
    }
}

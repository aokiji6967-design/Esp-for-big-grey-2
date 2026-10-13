package dev.esp.logic;

import dev.esp.config.EspCategory;
import dev.esp.config.EspConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the scanners and publishes one combined, immutable snapshot per client tick.
 *
 * <p>Everything the renderer needs is produced here, so the render path never touches game state it
 * would have to synchronise on.
 */
public final class TargetCollector {
    private static final List<EspTarget> EMPTY = List.of();

    private final BlockScanner blockScanner = new BlockScanner();
    private final EntityScanner entityScanner = new EntityScanner();

    private List<EspTarget> snapshot = EMPTY;

    public BlockScanner blocks() {
        return blockScanner;
    }

    public EntityScanner entities() {
        return entityScanner;
    }

    public List<EspTarget> snapshot() {
        return snapshot;
    }

    /** Asks the block sweep to restart, used after bulk selection changes. */
    public void invalidateBlocks() {
        blockScanner.invalidate();
    }

    public void tick(MinecraftClient client, EspConfig config) {
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;

        if (world == null || player == null) {
            blockScanner.reset();
            entityScanner.reset();
            snapshot = EMPTY;
            return;
        }

        // One range for every scanner and for the renderer: never shorter than the render distance.
        double range = EspRange.effective(config.render(), client);

        entityScanner.tick(client, config, range);

        // A disabled category, or one with nothing selected, must not leave a stale snapshot.
        if (config.isEnabled(EspCategory.BLOCK) && config.blocks().enabledCount() > 0) {
            blockScanner.tick(world, player.getEntityPos(), config.render(), config.blocks(), range);
        } else {
            blockScanner.reset();
        }

        List<EspTarget> combined = new ArrayList<>();

        if (config.isEnabled(EspCategory.BLOCK)) combined.addAll(blockScanner.snapshot());
        if (config.isEnabled(EspCategory.ITEM)) combined.addAll(entityScanner.itemSnapshot());
        if (config.isEnabled(EspCategory.ENTITY)) combined.addAll(entityScanner.entitySnapshot());

        snapshot = combined.isEmpty() ? EMPTY : List.copyOf(combined);
    }

    public void reset() {
        blockScanner.reset();
        entityScanner.reset();
        snapshot = EMPTY;
    }
}

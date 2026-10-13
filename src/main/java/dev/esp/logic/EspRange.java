package dev.esp.logic;

import dev.esp.config.RenderSettings;
import net.minecraft.client.MinecraftClient;

/**
 * Resolves how far the ESP looks.
 *
 * <p>The configured distance is a lower bound, never an upper bound: everything the player can
 * already see inside their render distance should be trackable, so a smaller manual setting would
 * only make the overlay look broken. The scanners and the renderer all ask for the effective range
 * here, which keeps "what gets found" and "what gets drawn" in agreement.
 */
public final class EspRange {
    private EspRange() {}

    /** @return the larger of the configured distance and the client's render distance, in blocks */
    public static double effective(RenderSettings settings, MinecraftClient client) {
        return Math.max(settings.range(), renderDistanceBlocks(client));
    }

    /** @return the client's render distance in blocks, or 0 when there is no client yet */
    public static double renderDistanceBlocks(MinecraftClient client) {
        if (client == null || client.options == null) return 0.0;

        return client.options.getViewDistance().getValue() * 16.0;
    }
}

package dev.esp.render;

import dev.esp.config.CategoryStyle;
import dev.esp.config.EspColor;
import dev.esp.config.EspConfig;
import dev.esp.logic.EspTarget;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;

/**
 * Resolves the colour a target is drawn with, applying friend highlighting, health colours, gradients
 * and chroma in that order so each option can be understood independently.
 */
public final class ColorProvider {
    /** Colour used for friends when highlighting is enabled. */
    public static final EspColor FRIEND = new EspColor(80, 255, 140);

    private ColorProvider() {}

    /**
     * @param base     the configured outline or fill colour
     * @param progress 0 at the bottom of a box or the camera end of a tracer, 1 at the far end
     * @param time     seconds since the client started, used for chroma cycling
     */
    public static EspColor resolve(EspConfig config, EspTarget target, EspColor base, double progress, double time) {
        // A colour set on the individual block, item or entity type wins outright: the user asked
        // for that exact colour, so no category level effect (gradient, chroma, health, friend
        // highlight) should silently replace it.
        EspColor override = overrideFor(config, target);
        if (override != null) return override;

        CategoryStyle style = config.style(target.category());
        EspColor result = base;

        float health = target.healthFraction();

        if (style.healthColor() && health >= 0f) {
            result = fromHealth(health, base.a);
        } else if (target.friend() && config.players().highlightFriends()) {
            result = FRIEND.withAlpha(base.a);
        }

        if (style.gradient()) {
            result = result.lerp(style.gradientColor().withAlpha(result.a), progress);
        }

        if (style.chroma()) {
            result = result.withHue(result.hue() + time * style.chromaSpeed());
        }

        return result;
    }

    /** @return the colour saved for this exact entry, or {@code null} when it uses the category's */
    private static EspColor overrideFor(EspConfig config, EspTarget target) {
        Object entry = target.entry();
        if (entry == null) return null;

        return switch (target.category()) {
            case BLOCK -> config.blocks().color((Block) entry);
            case ITEM -> config.items().color((Item) entry);
            case ENTITY -> config.entities().color((EntityType<?>) entry);
        };
    }

    /** Green at full health through yellow to red at zero. */
    public static EspColor fromHealth(float fraction, int alpha) {
        double clamped = Math.min(1.0, Math.max(0.0, fraction));

        // 0.33 is green on the hue wheel, 0 is red.
        return EspColor.fromHue(0.33 * clamped, 0.9, 1.0, alpha);
    }
}

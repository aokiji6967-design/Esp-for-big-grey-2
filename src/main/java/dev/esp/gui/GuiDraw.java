package dev.esp.gui;

import dev.esp.config.EspColor;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Drawing helpers for the ESP screen.
 *
 * <p>Everything is made of rectangles, so the GUI needs no textures or sprites: a gear icon is a
 * plus with a punched out centre, and buttons are two rectangles and a border.
 */
public final class GuiDraw {
    public static final int BACKDROP = 0xB4000000;
    public static final int PANEL = 0xF0101014;
    public static final int PANEL_BORDER = 0xFF3A3A46;
    public static final int ROW = 0x30FFFFFF;
    public static final int ROW_ALT = 0x18FFFFFF;
    public static final int ROW_HOVER = 0x44FFFFFF;
    public static final int BUTTON = 0xFF262630;
    public static final int BUTTON_HOVER = 0xFF34343F;
    public static final int BUTTON_ACTIVE = 0xFF3D6E9E;
    public static final int CHECKBOX_BORDER = 0xFF6A6A78;
    public static final int ACCENT = 0xFF5AA9E6;
    public static final int TEXT = 0xFFE6E6EA;
    public static final int TEXT_DIM = 0xFF9A9AA6;
    public static final int TEXT_DISABLED = 0xFF6A6A74;

    private GuiDraw() {}

    public static boolean hovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void backdrop(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, BACKDROP);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL);
        border(context, x, y, width, height, PANEL_BORDER);
    }

    public static void border(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void button(
        DrawContext context,
        TextRenderer textRenderer,
        String label,
        int x, int y, int width, int height,
        int mouseX, int mouseY,
        boolean active
    ) {
        boolean hover = hovered(mouseX, mouseY, x, y, width, height);
        int background = active ? BUTTON_ACTIVE : (hover ? BUTTON_HOVER : BUTTON);

        context.fill(x, y, x + width, y + height, background);
        border(context, x, y, width, height, PANEL_BORDER);

        int color = active ? 0xFFFFFFFF : (hover ? TEXT : TEXT_DIM);

        // Fit the label between the borders. Centring an over wide label used to push it past both
        // edges of the button, which is what made long values look like they escaped the control.
        String fitted = fit(textRenderer, label, width - 6);
        int textWidth = textRenderer.getWidth(fitted);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;

        context.drawTextWithShadow(textRenderer, fitted, textX, textY, color);
    }

    /** Trims {@code label} with a trailing ellipsis until it fits inside {@code maxWidth} pixels. */
    public static String fit(TextRenderer textRenderer, String label, int maxWidth) {
        if (label == null || maxWidth <= 0) return "";
        if (textRenderer.getWidth(label) <= maxWidth) return label;

        String ellipsis = "...";
        if (textRenderer.getWidth(ellipsis) > maxWidth) return "";

        for (int length = label.length() - 1; length > 0; length--) {
            String candidate = label.substring(0, length) + ellipsis;

            if (textRenderer.getWidth(candidate) <= maxWidth) return candidate;
        }

        return ellipsis;
    }

    public static void checkbox(DrawContext context, int x, int y, int size, boolean checked, boolean hover) {
        int fill = checked ? ACCENT : (hover ? ROW_HOVER : ROW);

        context.fill(x, y, x + size, y + size, fill);
        border(context, x, y, size, size, checked ? ACCENT : CHECKBOX_BORDER);

        if (checked) {
            // A short tick drawn with two rectangles.
            context.fill(x + 3, y + size - 5, x + size - 3, y + size - 3, 0xFFFFFFFF);
            context.fill(x + size - 5, y + 2, x + size - 3, y + size - 3, 0xFFFFFFFF);
        }
    }

    public static void slider(
        DrawContext context,
        int x, int y, int width, int height,
        double progress,
        boolean hover
    ) {
        double clamped = Math.min(1.0, Math.max(0.0, progress));

        context.fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, hover ? ROW_HOVER : ROW);

        int filled = (int) Math.round(clamped * width);
        context.fill(x, y + height / 2 - 1, x + filled, y + height / 2 + 1, ACCENT);

        int handleX = Math.min(x + width - 2, x + filled);
        context.fill(handleX - 1, y + 1, handleX + 1, y + height - 1, 0xFFFFFFFF);
    }

    public static void swatch(DrawContext context, EspColor color, int x, int y, int width, int height, boolean hover) {
        // A checkerboard would sell the alpha better, but a flat fill keeps this cheap.
        context.fill(x, y, x + width, y + height, color.toArgb());
        border(context, x, y, width, height, hover ? ACCENT : PANEL_BORDER);
    }

    /**
     * A tiny gear icon: a plus shaped body with a hole in the middle. Clicking it opens the settings
     * panel.
     */
    public static void gear(DrawContext context, int x, int y, int size, boolean hover) {
        int color = hover ? ACCENT : TEXT_DIM;
        int centerX = x + size / 2;
        int centerY = y + size / 2;

        int half = Math.max(2, size / 2);
        int body = Math.max(1, size / 4);

        context.fill(centerX - 1, centerY - half, centerX + 1, centerY + half, color);
        context.fill(centerX - half, centerY - 1, centerX + half, centerY + 1, color);
        context.fill(centerX - body, centerY - body, centerX + body, centerY + body, color);
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, PANEL);
    }
}

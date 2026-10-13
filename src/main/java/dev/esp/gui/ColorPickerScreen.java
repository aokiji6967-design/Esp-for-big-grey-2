package dev.esp.gui;

import dev.esp.config.EspColor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A compact colour picker: hex field plus red, green, blue and alpha sliders, with a live preview.
 *
 * <p>Changes are pushed to the owning setting immediately, so the swatch behind this screen already
 * shows the new colour when it closes. When a {@code reset} action is given, a Clear button appears
 * next to Done - used by the per entry colour picker to fall back to the category colour.
 */
public final class ColorPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int ROW_HEIGHT = 18;
    private static final int SLIDER_WIDTH = 120;
    private static final int PREVIEW_Y = 22;
    private static final int HEX_Y = 42;
    private static final int SLIDERS_Y = 62;
    private static final int PANEL_HEIGHT = SLIDERS_Y + ROW_HEIGHT * 4 + 20;

    private final Screen parent;
    private final Consumer<EspColor> setter;
    private final Runnable reset;
    private final TextInput hexInput;

    private EspColor color;
    private int draggingChannel = -1;

    private int panelX;
    private int panelY;
    private int sliderX;

    public ColorPickerScreen(Screen parent, String label, EspColor initial, Consumer<EspColor> setter) {
        this(parent, label, initial, setter, null);
    }

    /**
     * @param reset optional action for the Clear button, run before closing; {@code null} hides it
     */
    public ColorPickerScreen(
        Screen parent, String label, EspColor initial, Consumer<EspColor> setter, Runnable reset
    ) {
        super(Text.literal(label));

        this.parent = parent;
        this.setter = setter;
        this.reset = reset;
        this.color = initial;

        this.hexInput = new TextInput(this::applyHex).filter(ColorPickerScreen::isHexInput).maxLength(9);
        this.hexInput.text(initial.toHex());
    }

    /** Keeps the hex field to an optional {@code #} followed by at most eight hex digits. */
    private static boolean isHexInput(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() > 8) return false;

        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) return false;
        }

        return true;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        sliderX = panelX + PANEL_WIDTH - SLIDER_WIDTH - 10;

        hexInput.focus(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        GuiDraw.backdrop(context, width, height);
        GuiDraw.panel(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        context.drawTextWithShadow(textRenderer, title.getString(), panelX + 10, panelY + 8, GuiDraw.TEXT);

        GuiDraw.swatch(context, color, panelX + 10, panelY + PREVIEW_Y, PANEL_WIDTH - 20, 14, false);

        context.fill(panelX + 10, panelY + HEX_Y, panelX + PANEL_WIDTH - 10, panelY + HEX_Y + 12, GuiDraw.ROW);
        GuiDraw.border(context, panelX + 10, panelY + HEX_Y, PANEL_WIDTH - 20, 12, GuiDraw.ACCENT);
        context.drawTextWithShadow(textRenderer, hexInput.text(), panelX + 14, panelY + HEX_Y + 2, GuiDraw.TEXT);

        String[] labels = {"R", "G", "B", "A"};
        int[] values = {color.r, color.g, color.b, color.a};

        for (int channel = 0; channel < labels.length; channel++) {
            int rowY = panelY + SLIDERS_Y + channel * ROW_HEIGHT;

            context.drawTextWithShadow(textRenderer, labels[channel], panelX + 12, rowY + 5, GuiDraw.TEXT_DIM);
            GuiDraw.slider(context, sliderX, rowY + 3, SLIDER_WIDTH, 10, values[channel] / 255.0,
                GuiDraw.hovered(mouseX, mouseY, sliderX, rowY, SLIDER_WIDTH, ROW_HEIGHT));

            String value = Integer.toString(values[channel]);
            context.drawTextWithShadow(textRenderer, value, panelX + PANEL_WIDTH - 10 - textRenderer.getWidth(value), rowY + 5, GuiDraw.TEXT);
        }

        GuiDraw.button(context, textRenderer, "Done", doneX(), doneY(), 48, 12, mouseX, mouseY, true);

        if (reset != null) {
            GuiDraw.button(context, textRenderer, "Clear", panelX + 10, doneY(), 48, 12, mouseX, mouseY, false);
        }
    }

    private int doneX() {
        return panelX + PANEL_WIDTH - 58;
    }

    private int doneY() {
        return panelY + PANEL_HEIGHT - 18;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        if (GuiDraw.hovered(mouseX, mouseY, doneX(), doneY(), 48, 12)) {
            close();
            return true;
        }

        if (reset != null && GuiDraw.hovered(mouseX, mouseY, panelX + 10, doneY(), 48, 12)) {
            reset.run();
            close();
            return true;
        }

        if (GuiDraw.hovered(mouseX, mouseY, panelX + 10, panelY + HEX_Y, PANEL_WIDTH - 20, 12)) {
            hexInput.focus(true);
            return true;
        }

        for (int channel = 0; channel < 4; channel++) {
            int rowY = panelY + SLIDERS_Y + channel * ROW_HEIGHT;

            if (GuiDraw.hovered(mouseX, mouseY, sliderX - 10, rowY, SLIDER_WIDTH + 10, ROW_HEIGHT)) {
                draggingChannel = channel;
                applySlider(mouseX);

                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingChannel < 0) return super.mouseDragged(click, offsetX, offsetY);

        applySlider((int) click.x());

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingChannel = -1;
        return super.mouseReleased(click);
    }

    private void applySlider(int mouseX) {
        if (draggingChannel < 0) return;

        double progress = Math.min(1.0, Math.max(0.0, (mouseX - sliderX) / (double) SLIDER_WIDTH));
        int value = (int) Math.round(progress * 255);

        color = switch (draggingChannel) {
            case 0 -> new EspColor(value, color.g, color.b, color.a);
            case 1 -> new EspColor(color.r, value, color.b, color.a);
            case 2 -> new EspColor(color.r, color.g, value, color.a);
            default -> new EspColor(color.r, color.g, color.b, value);
        };

        commit();
    }

    private void applyHex(String text) {
        EspColor parsed = EspColor.parseHex(text);
        if (parsed == null) return;

        // Keep the alpha the sliders set unless the hex string carries its own.
        String digits = text.startsWith("#") ? text.substring(1) : text;
        color = digits.length() == 8 ? parsed : parsed.withAlpha(color.a);

        commit();
    }

    private void commit() {
        setter.accept(color);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (hexInput.keyPressed(input)) return true;

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (hexInput.charTyped(input)) return true;

        return super.charTyped(input);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

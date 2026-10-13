package dev.esp.gui;

import dev.esp.EspClient;
import dev.esp.config.BoxMode;
import dev.esp.config.CategoryStyle;
import dev.esp.config.EspCategory;
import dev.esp.config.EspConfig;
import dev.esp.config.ShapeMode;
import dev.esp.config.PlayerFilters;
import dev.esp.config.RenderSettings;
import dev.esp.logic.EspRange;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The configuration panel behind the gear icon.
 *
 * <p>It edits one category's style at a time (picked with the category buttons at the top) plus the
 * global rendering, player and config sections. Every control writes straight into {@link EspConfig}
 * and flags the config manager, so changes are persisted without a save button.
 */
public final class SettingsPanel {
    private static final int ROW_HEIGHT = 14;
    private static final int CONTROL_WIDTH = 110;
    private static final int CATEGORY_BUTTON_WIDTH = 56;

    private final EspClient esp;
    private final List<SettingRow> rows = new ArrayList<>();
    private final TextInput friendInput = new TextInput(value -> {});

    private EspCategory styleCategory = EspCategory.BLOCK;
    private SettingRow dragging;
    private boolean friendInputFocused;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private double scroll;

    public SettingsPanel(EspClient esp) {
        this.esp = esp;
        this.friendInput.maxLength(16);
        rebuild();
    }

    public void rebuild() {
        rows.clear();

        EspConfig config = esp.config();
        CategoryStyle style = config.style(styleCategory);

        rows.add(SettingRow.header("Style - " + styleCategory.displayName()));
        rows.add(SettingRow.color("Outline Colour", style::outlineColor, value -> {
            style.outlineColor(value);
            esp.save();
        }));
        rows.add(SettingRow.color("Fill Colour", style::fillColor, value -> {
            style.fillColor(value);
            esp.save();
        }));
        rows.add(SettingRow.cycle("Box Mode (legacy)", () -> style.boxMode().displayName(), () -> {
            style.boxMode(nextBoxMode(style.boxMode()));
            esp.save();
        }));
        rows.add(SettingRow.cycle("Shape Mode", () -> style.shapeMode().displayName(), () -> {
            style.shapeMode(nextShapeMode(style.shapeMode()));
            esp.save();
        }));
        rows.add(SettingRow.toggle("Tracers", style::tracers, value -> {
            style.tracers(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Gradient", style::gradient, value -> {
            style.gradient(value);
            esp.save();
        }));
        rows.add(SettingRow.color("Gradient Colour", style::gradientColor, value -> {
            style.gradientColor(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Chroma / Rainbow", style::chroma, value -> {
            style.chroma(value);
            esp.save();
        }));
        rows.add(SettingRow.slider("Chroma Speed", 0.05, 2.0,
            style::chromaSpeed,
            value -> {
                style.chromaSpeed(value);
                esp.save();
            },
            () -> String.format("%.2f", style.chromaSpeed())));
        rows.add(SettingRow.toggle("Health Based Colours", style::healthColor, value -> {
            style.healthColor(value);
            esp.save();
        }));

        RenderSettings render = config.render();

        rows.add(SettingRow.header("Rendering"));
        rows.add(SettingRow.slider("Distance", 8, 512,
            render::range,
            value -> {
                render.range(value);
                esp.save();
            },
            () -> {
                // The ESP always covers at least the render distance; the slider only extends it.
                double renderDistance = EspRange.renderDistanceBlocks(MinecraftClient.getInstance());
                double effective = Math.max(render.range(), renderDistance);
                String suffix = render.range() < renderDistance ? " (render dist)" : "";

                return String.format("%.0f blocks%s", effective, suffix);
            }));
        rows.add(SettingRow.slider("Line Width", 1.0, 8.0,
            render::lineThickness,
            value -> {
                render.lineThickness(value);
                esp.save();
            },
            () -> Math.round(render.lineThickness()) + " px"));
        rows.add(SettingRow.slider("Max Targets (each type)", 32, 1024,
            render::maxTargets,
            value -> {
                render.maxTargets((int) Math.round(value));
                esp.save();
            },
            () -> Integer.toString(render.maxTargets())));
        rows.add(SettingRow.toggle("Wall Occlusion Check", render::occlusionCheck, value -> {
            render.occlusionCheck(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Interpolate Movement", render::interpolate, value -> {
            render.interpolate(value);
            esp.save();
        }));

        rows.add(SettingRow.header("Performance"));
        rows.add(SettingRow.slider("Block Scan / Tick", 4096, 262144,
            render::blockScanBudget,
            value -> {
                render.blockScanBudget((int) Math.round(value));
                esp.save();
            },
            () -> String.format("%,d", render.blockScanBudget())));

        PlayerFilters players = config.players();

        rows.add(SettingRow.header("Players"));
        rows.add(SettingRow.toggle("Local Player", players::renderSelf, value -> {
            players.renderSelf(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Other Players", players::renderOthers, value -> {
            players.renderOthers(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Only Friends", players::onlyFriends, value -> {
            players.onlyFriends(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Highlight Friends", players::highlightFriends, value -> {
            players.highlightFriends(value);
            esp.save();
        }));
        rows.add(SettingRow.toggle("Invisible Players", players::renderInvisible, value -> {
            players.renderInvisible(value);
            esp.save();
        }));

        rows.add(SettingRow.header("Friends"));
        rows.add(SettingRow.text("Add friend (name + Enter)"));
        rows.add(SettingRow.friends("Friend list"));

        rows.add(SettingRow.header("Config"));
        rows.add(SettingRow.button("Save to disk now", () -> esp.configManager().save()));
        rows.add(SettingRow.button("Reload from disk", () -> {
            esp.configManager().load();
            rebuild();
        }));
        rows.add(SettingRow.button("Clear all selections", () -> {
            esp.config().blocks().clear();
            esp.config().items().clear();
            esp.config().entities().clear();
            esp.collector().invalidateBlocks();
            esp.save();
        }));
    }

    private static BoxMode nextBoxMode(BoxMode mode) {
        BoxMode[] modes = BoxMode.values();
        return modes[(mode.ordinal() + 1) % modes.length];
    }

    private static ShapeMode nextShapeMode(ShapeMode mode) {
        ShapeMode[] modes = ShapeMode.values();
        return modes[(mode.ordinal() + 1) % modes.length];
    }

    /** Draws the category selector and the rows. */
    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int selectorWidth = CATEGORY_BUTTON_WIDTH * EspCategory.values().length;
        int selectorX = x + (width - selectorWidth) / 2;

        for (EspCategory category : EspCategory.values()) {
            GuiDraw.button(
                context, textRenderer, category.displayName(),
                selectorX, y, CATEGORY_BUTTON_WIDTH - 2, ROW_HEIGHT,
                mouseX, mouseY, category == styleCategory
            );

            selectorX += CATEGORY_BUTTON_WIDTH;
        }

        int headerOffset = ROW_HEIGHT + 4;
        listX = x;
        listY = y + headerOffset;
        listWidth = width;
        listHeight = height - headerOffset;

        double totalHeight = rows.size() * ROW_HEIGHT;
        double maxScroll = Math.max(0.0, totalHeight - listHeight);
        scroll = Math.min(scroll, maxScroll);

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int first = (int) (scroll / ROW_HEIGHT);
        int offset = (int) (scroll - first * ROW_HEIGHT);

        for (int i = 0; i <= listHeight / ROW_HEIGHT; i++) {
            int index = first + i;
            if (index >= rows.size()) break;

            drawRow(context, textRenderer, rows.get(index), index, listY - offset + i * ROW_HEIGHT, mouseX, mouseY);
        }

        context.disableScissor();

        if (maxScroll > 0.0) {
            int barHeight = Math.max(10, (int) (listHeight * (listHeight / totalHeight)));
            int barY = listY + (int) ((listHeight - barHeight) * (scroll / maxScroll));

            context.fill(listX + listWidth - 2, listY, listX + listWidth, listY + listHeight, GuiDraw.ROW);
            context.fill(listX + listWidth - 2, barY, listX + listWidth, barY + barHeight, GuiDraw.ACCENT);
        }
    }

    private void drawRow(
        DrawContext context, TextRenderer textRenderer, SettingRow row,
        int index, int rowY, int mouseX, int mouseY
    ) {
        boolean hover = GuiDraw.hovered(mouseX, mouseY, listX + 2, rowY, listWidth - 6, ROW_HEIGHT);

        if (row.kind() == SettingRow.Kind.HEADER) {
            context.drawTextWithShadow(textRenderer, row.label(), listX + 4, rowY + 4, GuiDraw.ACCENT);
            context.fill(listX + 4, rowY + ROW_HEIGHT - 2, listX + listWidth - 6, rowY + ROW_HEIGHT - 1, GuiDraw.PANEL_BORDER);

            return;
        }

        if (hover) {
            context.fill(listX + 2, rowY, listX + listWidth - 4, rowY + ROW_HEIGHT, index % 2 == 0 ? GuiDraw.ROW : GuiDraw.ROW_ALT);
        }

        int labelX = listX + 8;
        int centerY = rowY + (ROW_HEIGHT - 8) / 2;
        int controlX = listX + listWidth - CONTROL_WIDTH - 10;

        context.drawTextWithShadow(textRenderer, row.label(), labelX, centerY, GuiDraw.TEXT);

        switch (row.kind()) {
            case TOGGLE -> GuiDraw.checkbox(context, listX + 4, rowY + 3, 8, row.value(), hover);
            case SLIDER -> {
                GuiDraw.slider(context, controlX, rowY + 3, CONTROL_WIDTH, 8, progress(row), hover);

                String value = row.valueText();
                int width = textRenderer.getWidth(value);
                context.drawTextWithShadow(textRenderer, value, controlX - width - 4, centerY, GuiDraw.TEXT_DIM);
            }
            case COLOR -> {
                GuiDraw.swatch(context, row.color(), controlX, rowY + 3, CONTROL_WIDTH, 8, hover);

                String hex = row.color().toHex();
                int width = textRenderer.getWidth(hex);
                context.drawTextWithShadow(textRenderer, hex, controlX - width - 4, centerY, GuiDraw.TEXT_DIM);
            }
            case CYCLE -> GuiDraw.button(context, textRenderer, row.valueText(), controlX, rowY + 2, CONTROL_WIDTH, 10, mouseX, mouseY, true);
            case BUTTON -> GuiDraw.button(context, textRenderer, row.label(), controlX, rowY + 2, CONTROL_WIDTH, 10, mouseX, mouseY, false);
            case TEXT -> {
                int fieldWidth = CONTROL_WIDTH - 26;
                context.fill(controlX, rowY + 2, controlX + fieldWidth, rowY + 12, GuiDraw.ROW);
                GuiDraw.border(context, controlX, rowY + 2, fieldWidth, 10, friendInputFocused ? GuiDraw.ACCENT : GuiDraw.PANEL_BORDER);
                context.drawTextWithShadow(textRenderer, friendInput.text(), controlX + 3, rowY + 3, GuiDraw.TEXT);

                GuiDraw.button(context, textRenderer, "Add", controlX + fieldWidth + 2, rowY + 2, 22, 10, mouseX, mouseY, false);
            }
            case FRIENDS -> drawFriends(context, textRenderer, rowY, centerY, controlX, mouseX, mouseY);
            default -> {}
        }
    }

    private void drawFriends(
        DrawContext context, TextRenderer textRenderer,
        int rowY, int centerY, int controlX,
        int mouseX, int mouseY
    ) {
        var friends = esp.config().friends();

        if (friends.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "none", controlX, centerY, GuiDraw.TEXT_DIM);
            return;
        }

        int offset = 0;
        int drawn = 0;

        for (String friend : friends) {
            if (offset > CONTROL_WIDTH - 40) {
                context.drawTextWithShadow(textRenderer, "+" + (friends.size() - drawn), listX + listWidth - 24, centerY, GuiDraw.TEXT_DIM);
                return;
            }

            int nameWidth = textRenderer.getWidth(friend);
            int nameX = controlX + offset;

            context.drawTextWithShadow(textRenderer, friend, nameX, centerY, GuiDraw.TEXT_DIM);
            GuiDraw.button(context, textRenderer, "x", nameX + nameWidth + 2, rowY + 2, 10, 10, mouseX, mouseY, false);

            offset += nameWidth + 18;
            drawn++;
        }
    }

    private double progress(SettingRow row) {
        double range = row.max() - row.min();
        if (range <= 0.0) return 0.0;

        return (row.doubleValue() - row.min()) / range;
    }

    public boolean mouseClicked(Click click, int x, int y, int width, int height) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Category selector
        int selectorWidth = CATEGORY_BUTTON_WIDTH * EspCategory.values().length;
        int selectorX = x + (width - selectorWidth) / 2;

        if (GuiDraw.hovered((int) mouseX, (int) mouseY, selectorX, y, selectorWidth, ROW_HEIGHT)) {
            int index = (int) ((mouseX - selectorX) / CATEGORY_BUTTON_WIDTH);
            EspCategory[] categories = EspCategory.values();

            if (index >= 0 && index < categories.length) {
                styleCategory = categories[index];
                scroll = 0;
                rebuild();
            }

            return true;
        }

        if (!GuiDraw.hovered((int) mouseX, (int) mouseY, listX, listY, listWidth, listHeight)) {
            friendInputFocused = false;
            friendInput.focus(false);

            return false;
        }

        int index = (int) ((mouseY - listY + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) return true;

        SettingRow row = rows.get(index);
        int rowY = (int) (listY + index * ROW_HEIGHT - scroll);
        int controlX = listX + listWidth - CONTROL_WIDTH - 10;

        switch (row.kind()) {
            case TOGGLE -> {
                row.set(!row.value());
                esp.save();
            }
            case SLIDER -> {
                if (mouseX >= controlX) {
                    dragging = row;
                    applySlider(row, mouseX, controlX);
                }
            }
            case COLOR -> {
                if (mouseX >= controlX) {
                    MinecraftClient.getInstance().setScreen(new ColorPickerScreen(
                        MinecraftClient.getInstance().currentScreen,
                        row.label(),
                        row.color(),
                        row::color
                    ));
                }
            }
            case CYCLE, BUTTON -> {
                if (mouseX >= controlX) row.run();
            }
            case TEXT -> {
                int fieldWidth = CONTROL_WIDTH - 26;

                if (mouseX >= controlX + fieldWidth + 2) {
                    addFriend();
                } else {
                    friendInputFocused = true;
                    friendInput.focus(true);
                }
            }
            case FRIENDS -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    removeFriendAt((int) mouseX, (int) mouseY, rowY, controlX);
                }
            }
            default -> {}
        }

        return true;
    }

    public boolean mouseDragged(Click click, int x, int y, int width, int height) {
        if (dragging == null) return false;


        int controlX = listX + listWidth - CONTROL_WIDTH - 10;
        applySlider(dragging, click.x(), controlX);

        return true;
    }

    public boolean mouseReleased() {
        if (dragging == null) return false;

        dragging = null;
        return true;
    }

    private void applySlider(SettingRow row, double mouseX, int controlX) {
        double progress = Math.min(1.0, Math.max(0.0, (mouseX - controlX) / CONTROL_WIDTH));
        double value = row.min() + (row.max() - row.min()) * progress;

        row.setDouble(value);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!GuiDraw.hovered((int) mouseX, (int) mouseY, listX, listY, listWidth, listHeight)) return false;

        double maxScroll = Math.max(0.0, rows.size() * ROW_HEIGHT - listHeight);
        scroll = Math.min(maxScroll, Math.max(0.0, scroll - amount * ROW_HEIGHT * 2));

        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (!friendInputFocused) return false;

        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            addFriend();
            return true;
        }

        return friendInput.keyPressed(input);
    }

    public boolean charTyped(CharInput input) {
        if (!friendInputFocused) return false;

        return friendInput.charTyped(input);
    }

    private void addFriend() {
        if (esp.config().addFriend(friendInput.text())) {
            esp.save();
            rebuild();
        }

        friendInput.text("");
    }

    private void removeFriendAt(int mouseX, int mouseY, int rowY, int controlX) {
        // Walk the same layout used while drawing to find which remove button was hit.
        var friends = new ArrayList<>(esp.config().friends());
        int offset = 0;

        for (String friend : friends) {
            int nameWidth = MinecraftClient.getInstance().textRenderer.getWidth(friend);
            int removeX = controlX + offset + nameWidth + 2;

            if (GuiDraw.hovered(mouseX, mouseY, removeX, rowY + 2, 10, 10)) {
                esp.config().removeFriend(friend);
                esp.save();
                rebuild();

                return;
            }

            offset += nameWidth + 18;
        }
    }

    public void reset() {
        scroll = 0;
        dragging = null;
    }
}

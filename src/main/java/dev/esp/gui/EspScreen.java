package dev.esp.gui;

import dev.esp.EspClient;
import dev.esp.config.EspCategory;
import dev.esp.config.EspColor;
import dev.esp.config.SelectionMap;
import dev.esp.logic.Presets;
import dev.esp.logic.TargetCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * The mod's main screen: one tab per ESP category plus the settings panel, opened with the gear icon.
 *
 * <p>The list is drawn by hand and virtualised - only the visible rows are painted - so a category
 * with well over a thousand entries scrolls as cheaply as a short one. The whole UI is built from
 * rectangles, so it needs no textures or widget state to keep in sync.
 */
public final class EspScreen extends Screen {
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 250;
    private static final int TAB_WIDTH = 56;
    private static final int BAR_HEIGHT = 16;
    private static final int ROW_HEIGHT = 14;

    private final EspClient esp;
    private final SettingsPanel settings;

    private final TextInput search = new TextInput(value -> {
        scroll = 0;
        refresh();
    });

    private EspTab tab = EspTab.BLOCK;
    private String group = TargetCatalog.ALL;
    private double scroll;
    private List<TargetCatalog.Row> rows = List.of();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private int tabsY;
    private int masterY;
    private int searchY;
    private int presetY;
    private int listY;
    private int listHeight;

    public EspScreen(EspClient esp) {
        super(Text.literal("ESP"));

        this.esp = esp;
        this.settings = new SettingsPanel(esp);
        this.search.maxLength(48);
    }

    @Override
    protected void init() {
        layout();
        search.focus(true);

        settings.reset();
        refresh();
    }

    private void layout() {
        panelWidth = Math.min(width - 12, MAX_WIDTH);
        panelHeight = Math.min(height - 12, MAX_HEIGHT);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        tabsY = panelY + 5;
        masterY = tabsY + BAR_HEIGHT + 4;
        searchY = masterY + BAR_HEIGHT + 4;
        presetY = searchY + BAR_HEIGHT + 4;
        listY = presetY + BAR_HEIGHT + 6;
        listHeight = Math.max(ROW_HEIGHT, panelY + panelHeight - listY - 4);
    }

    private void refresh() {
        rows = tab.isCategory() ? esp.catalog().filter(tab.category(), query(), group) : List.of();
    }

    private String query() {
        return search.text().trim().toLowerCase(Locale.ROOT);
    }

    private int listX() {
        return panelX + 6;
    }

    private int listWidth() {
        return panelWidth - 12;
    }

    private int settingsX() {
        return panelX + 6;
    }

    private int settingsHeight() {
        return Math.max(ROW_HEIGHT + 20, panelY + panelHeight - masterY - 6);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        layout();

        GuiDraw.backdrop(context, width, height);
        GuiDraw.panel(context, panelX, panelY, panelWidth, panelHeight);

        drawTabs(context, mouseX, mouseY);

        if (tab.isCategory()) {
            drawCategoryTab(context, mouseX, mouseY);
        } else {
            settings.render(context, settingsX(), masterY, panelWidth - 12, settingsHeight(), mouseX, mouseY, deltaTicks);
        }
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + 5;

        for (EspTab candidate : EspTab.values()) {
            GuiDraw.button(context, textRenderer, candidate.displayName(), x, tabsY, TAB_WIDTH, BAR_HEIGHT, mouseX, mouseY, candidate == tab);
            x += TAB_WIDTH + 2;
        }

        int gearSize = 12;
        int gearX = panelX + panelWidth - gearSize - 6;

        GuiDraw.gear(context, gearX, tabsY + 2, gearSize, GuiDraw.hovered(mouseX, mouseY, gearX, tabsY + 2, gearSize, gearSize));
    }

    private void drawCategoryTab(DrawContext context, int mouseX, int mouseY) {
        EspCategory category = tab.category();

        GuiDraw.checkbox(context, panelX + 6, masterY + 4, 8, esp.config().isEnabled(category),
            GuiDraw.hovered(mouseX, mouseY, panelX + 6, masterY + 4, 8, 8));
        context.drawTextWithShadow(textRenderer, "Enabled", panelX + 18, masterY + 4, GuiDraw.TEXT);

        int buttonX = panelX + 60;
        GuiDraw.button(context, textRenderer, "All", buttonX, masterY, 24, BAR_HEIGHT, mouseX, mouseY, false);
        buttonX += 26;
        GuiDraw.button(context, textRenderer, "None", buttonX, masterY, 30, BAR_HEIGHT, mouseX, mouseY, false);
        buttonX += 32;

        GuiDraw.button(context, textRenderer, "Group: " + group, buttonX, masterY, groupButtonWidth(buttonX), BAR_HEIGHT, mouseX, mouseY, false);

        String counts = esp.catalog().enabledCount(category) + " / " + esp.catalog().total(category);
        context.drawTextWithShadow(textRenderer, counts, panelX + panelWidth - 6 - textRenderer.getWidth(counts), masterY + 4, GuiDraw.TEXT_DIM);

        int searchX = panelX + 6;
        int searchWidth = panelWidth - 12;

        context.fill(searchX, searchY, searchX + searchWidth, searchY + BAR_HEIGHT, GuiDraw.ROW);
        GuiDraw.border(context, searchX, searchY, searchWidth, BAR_HEIGHT,
            search.focused() ? GuiDraw.ACCENT : GuiDraw.PANEL_BORDER);

        boolean empty = search.text().isEmpty();
        context.drawTextWithShadow(textRenderer, empty ? "Search..." : search.text(), searchX + 4, searchY + 4,
            empty ? GuiDraw.TEXT_DIM : GuiDraw.TEXT);

        List<Presets.Preset> presets = Presets.forCategory(category);

        if (!presets.isEmpty()) {
            int slot = presetSlot(presets.size());
            int presetX = panelX + 6;

            for (Presets.Preset preset : presets) {
                GuiDraw.button(context, textRenderer, preset.label(), presetX, presetY, slot, BAR_HEIGHT, mouseX, mouseY, false);
                presetX += slot + 2;
            }
        }

        drawRows(context, mouseX, mouseY);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        int listX = listX();
        int listWidth = listWidth();

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        double maxScroll = Math.max(0.0, rows.size() - visibleRows);
        scroll = Math.min(scroll, maxScroll);

        int first = (int) Math.floor(scroll);
        int offset = (int) ((scroll - first) * ROW_HEIGHT);

        for (int i = 0; i <= visibleRows; i++) {
            int index = first + i;
            if (index >= rows.size()) break;

            TargetCatalog.Row row = rows.get(index);
            int rowY = listY - offset + i * ROW_HEIGHT;
            boolean hover = GuiDraw.hovered(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT);

            context.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT,
                hover ? GuiDraw.ROW_HOVER : (index % 2 == 0 ? GuiDraw.ROW : GuiDraw.ROW_ALT));

            GuiDraw.checkbox(context, listX + 2, rowY + 3, 8, row.isEnabled(), false);

            context.drawTextWithShadow(textRenderer, row.displayName(), listX + 14, rowY + 3,
                row.isEnabled() ? GuiDraw.TEXT : GuiDraw.TEXT_DISABLED);

            // Colour box: shows this entry's own colour when it has one, and opens the picker
            // when clicked, so a single block, item or entity type can differ from its category.
            int swatchX = listX + listWidth - 14;
            boolean swatchHover = GuiDraw.hovered(mouseX, mouseY, swatchX, rowY + 3, 8, 8);
            EspColor override = mapOf(tab.category()).colorOfId(row.id());

            if (override != null) {
                GuiDraw.swatch(context, override, swatchX, rowY + 3, 8, 8, swatchHover);
            } else {
                context.fill(swatchX, rowY + 3, swatchX + 8, rowY + 11, GuiDraw.BUTTON);
                GuiDraw.border(context, swatchX, rowY + 3, 8, 8,
                    swatchHover ? GuiDraw.ACCENT : GuiDraw.PANEL_BORDER);
            }

            String groupLabel = row.group();
            int groupWidth = textRenderer.getWidth(groupLabel);

            if (groupWidth + 24 < listWidth) {
                context.drawTextWithShadow(textRenderer, groupLabel, listX + listWidth - groupWidth - 16, rowY + 3, GuiDraw.TEXT_DIM);
            }
        }

        context.disableScissor();

        if (maxScroll > 0.0) {
            int barHeight = Math.max(10, (int) (listHeight * (visibleRows / (double) rows.size())));
            int barY = listY + (int) ((listHeight - barHeight) * (scroll / maxScroll));

            context.fill(listX + listWidth - 2, listY, listX + listWidth, listY + listHeight, GuiDraw.ROW);
            context.fill(listX + listWidth - 2, barY, listX + listWidth, barY + barHeight, GuiDraw.ACCENT);
        }

        String footer = rows.size() + " shown - click row to toggle, colour box to recolour";
        context.drawTextWithShadow(textRenderer, footer, listX, panelY + panelHeight - 12, GuiDraw.TEXT_DIM);
    }

    private int groupButtonWidth(int buttonX) {
        return Math.min(126, Math.max(66, panelX + panelWidth - buttonX - 74));
    }

    private int presetSlot(int count) {
        return Math.min(96, (panelWidth - 12 - (count - 1) * 2) / count);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        int x = panelX + 5;

        for (EspTab candidate : EspTab.values()) {
            if (GuiDraw.hovered(mouseX, mouseY, x, tabsY, TAB_WIDTH, BAR_HEIGHT)) {
                tab = candidate;
                scroll = 0;
                settings.reset();
                refresh();

                return true;
            }

            x += TAB_WIDTH + 2;
        }

        int gearSize = 12;
        if (GuiDraw.hovered(mouseX, mouseY, panelX + panelWidth - gearSize - 6, tabsY + 2, gearSize, gearSize)) {
            tab = EspTab.SETTINGS;
            scroll = 0;

            return true;
        }

        if (!tab.isCategory()) {
            return settings.mouseClicked(click, settingsX(), masterY, panelWidth - 12, settingsHeight());
        }

        EspCategory category = tab.category();

        if (GuiDraw.hovered(mouseX, mouseY, panelX + 6, masterY, 52, BAR_HEIGHT)) {
            esp.config().setEnabled(category, !esp.config().isEnabled(category));
            esp.save();

            if (!esp.config().isEnabled(category)) esp.collector().reset();

            return true;
        }

        int buttonX = panelX + 60;

        if (GuiDraw.hovered(mouseX, mouseY, buttonX, masterY, 24, BAR_HEIGHT)) {
            esp.catalog().setAll(category, group, true);
            applyBulkChange();

            return true;
        }

        buttonX += 26;

        if (GuiDraw.hovered(mouseX, mouseY, buttonX, masterY, 30, BAR_HEIGHT)) {
            esp.catalog().setAll(category, group, false);
            applyBulkChange();

            return true;
        }

        buttonX += 32;

        if (GuiDraw.hovered(mouseX, mouseY, buttonX, masterY, groupButtonWidth(buttonX), BAR_HEIGHT)) {
            cycleGroup(category, click.button() == 1 ? -1 : 1);

            return true;
        }

        if (GuiDraw.hovered(mouseX, mouseY, panelX + 6, searchY, panelWidth - 12, BAR_HEIGHT)) {
            search.focus(true);

            return true;
        }

        List<Presets.Preset> presets = Presets.forCategory(category);

        if (!presets.isEmpty()) {
            int slot = presetSlot(presets.size());
            int presetX = panelX + 6;

            for (Presets.Preset preset : presets) {
                if (GuiDraw.hovered(mouseX, mouseY, presetX, presetY, slot, BAR_HEIGHT)) {
                    Presets.apply(esp.catalog(), preset);
                    applyBulkChange();

                    return true;
                }

                presetX += slot + 2;
            }
        }

        if (GuiDraw.hovered(mouseX, mouseY, listX(), listY, listWidth(), listHeight)) {
            int first = (int) Math.floor(scroll);
            double pixelOffset = (scroll - first) * ROW_HEIGHT;
            int index = first + (int) ((mouseY - listY + pixelOffset) / ROW_HEIGHT);

            if (index >= 0 && index < rows.size()) {
                TargetCatalog.Row row = rows.get(index);

                int swatchX = listX() + listWidth() - 14;
                if (mouseX >= swatchX && mouseX < swatchX + 8) {
                    openColourPicker(row);
                    return true;
                }

                row.set(!row.isEnabled());
                esp.save();
            }

            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    /** @return the selection map a category's rows read and write */
    private SelectionMap<?> mapOf(EspCategory category) {
        return switch (category) {
            case BLOCK -> esp.config().blocks();
            case ITEM -> esp.config().items();
            case ENTITY -> esp.config().entities();
        };
    }

    /** Opens the colour picker for one list row; Clear falls back to the category colour. */
    private void openColourPicker(TargetCatalog.Row row) {
        EspCategory category = tab.category();
        SelectionMap<?> map = mapOf(category);

        EspColor current = map.colorOfId(row.id());
        EspColor initial = current != null ? current : esp.config().style(category).outlineColor();

        MinecraftClient.getInstance().setScreen(new ColorPickerScreen(
            this,
            row.displayName(),
            initial,
            color -> {
                map.setColorOfId(row.id(), color);
                esp.save();
            },
            () -> {
                map.setColorOfId(row.id(), null);
                esp.save();
            }
        ));
    }

    private void applyBulkChange() {
        esp.collector().invalidateBlocks();
        esp.save();
        refresh();
    }

    private void cycleGroup(EspCategory category, int direction) {
        List<String> groups = esp.catalog().groups(category);
        if (groups.isEmpty()) return;

        int index = groups.indexOf(group);
        if (index < 0) index = 0;

        group = groups.get(Math.floorMod(index + direction, groups.size()));
        scroll = 0;

        refresh();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!tab.isCategory()) {
            return settings.mouseScrolled(mouseX, mouseY, verticalAmount);
        }

        if (!GuiDraw.hovered((int) mouseX, (int) mouseY, listX(), listY, listWidth(), listHeight)) return false;

        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        double maxScroll = Math.max(0.0, rows.size() - visibleRows);

        scroll = Math.min(maxScroll, Math.max(0.0, scroll - verticalAmount * 3));

        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!tab.isCategory() && settings.mouseDragged(click, settingsX(), masterY, panelWidth - 12, settingsHeight())) {
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!tab.isCategory() && settings.mouseReleased()) return true;

        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (search.focused() && search.keyPressed(input)) return true;
        if (!tab.isCategory() && settings.keyPressed(input)) return true;

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search.focused() && search.charTyped(input)) return true;
        if (!tab.isCategory() && settings.charTyped(input)) return true;

        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

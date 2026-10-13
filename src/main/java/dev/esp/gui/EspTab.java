package dev.esp.gui;

import dev.esp.config.EspCategory;

/** The tabs across the top of the ESP screen. */
public enum EspTab {
    BLOCK("Blocks", EspCategory.BLOCK),
    ITEM("Items", EspCategory.ITEM),
    ENTITY("Entities", EspCategory.ENTITY),
    SETTINGS("Settings", null);

    private final String displayName;
    private final EspCategory category;

    EspTab(String displayName, EspCategory category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String displayName() {
        return displayName;
    }

    /** @return the category this tab edits, or {@code null} for the settings tab */
    public EspCategory category() {
        return category;
    }

    public boolean isCategory() {
        return category != null;
    }
}

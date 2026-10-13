package dev.esp.config;

/** The three top level ESP categories, each with an independent selection list and style. */
public enum EspCategory {
    BLOCK("Blocks"),
    ITEM("Items"),
    ENTITY("Entities");

    private final String displayName;

    EspCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

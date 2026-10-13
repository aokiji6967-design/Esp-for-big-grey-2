package dev.esp.config;

/** How a target's 3D shape is drawn. */
public enum BoxMode {
    /** Filled 3D box plus wireframe outline. */
    BOTH("Both"),
    /** Solid translucent 3D box. */
    FILLED("Filled Box"),
    /** Outline wireframe only. */
    OUTLINE("Wireframe");

    private final String displayName;

    BoxMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean lines() {
        return this == BOTH || this == OUTLINE;
    }

    public boolean sides() {
        return this == BOTH || this == FILLED;
    }
}

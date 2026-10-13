package dev.esp.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialisable mirror of {@link EspConfig}.
 *
 * <p>Only enabled selections are stored, as registry ids, and colours are stored as packed ARGB
 * integers, so the file is stable across sessions and easy to hand-edit.
 */
public final class ConfigData {
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;

    public boolean blocksEnabled;
    public boolean itemsEnabled;
    public boolean entitiesEnabled;

    public List<String> blocks = new ArrayList<>();
    public List<String> items = new ArrayList<>();
    public List<String> entities = new ArrayList<>();
    public List<String> friends = new ArrayList<>();

    /** Per entry colour overrides: registry id to packed ARGB, only entries with an override. */
    public Map<String, Integer> blockColors = new LinkedHashMap<>();
    public Map<String, Integer> itemColors = new LinkedHashMap<>();
    public Map<String, Integer> entityColors = new LinkedHashMap<>();

    public Map<String, StyleData> styles = new LinkedHashMap<>();
    public RenderData render = new RenderData();
    public PlayerData players = new PlayerData();

    /**
     * Replaces anything a hand edited file left as {@code null}, so loading a config can never fail
     * with a null pointer and a broken file costs the user only the settings it mentions.
     */
    public void sanitise() {
        if (blocks == null) blocks = new ArrayList<>();
        if (items == null) items = new ArrayList<>();
        if (entities == null) entities = new ArrayList<>();
        if (friends == null) friends = new ArrayList<>();

        if (blockColors == null) blockColors = new LinkedHashMap<>();
        if (itemColors == null) itemColors = new LinkedHashMap<>();
        if (entityColors == null) entityColors = new LinkedHashMap<>();

        if (styles == null) styles = new LinkedHashMap<>();
        else styles.replaceAll((key, value) -> value == null ? new StyleData() : value);

        if (render == null) render = new RenderData();
        if (players == null) players = new PlayerData();
    }

    public static final class StyleData {
        public int outlineColor = 0xFF00C8FF;
        public int fillColor = 0x3200C8FF;
        public String boxMode = BoxMode.OUTLINE.name();
        public String shapeMode = ShapeMode.HITBOX.name();
        public boolean tracers;
        public boolean gradient;
        public int gradientColor = 0xFF00C8FF;
        public boolean chroma;
        public double chromaSpeed = 0.35;
        public boolean healthColor;
    }

    public static final class RenderData {
        public double range = 48.0;
        public int maxTargets = 192;
        public double lineThickness = 2.0;
        public boolean occlusionCheck;
        public boolean interpolate = true;
        public int blockScanBudget = 65536;
        public double tracerAlpha = 0.65;
    }

    public static final class PlayerData {
        public boolean renderSelf;
        public boolean renderOthers = true;
        public boolean onlyFriends;
        public boolean highlightFriends = true;
        public boolean renderInvisible = true;
    }
}

package dev.esp.config;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The live configuration: every toggle the GUI can change and the renderer reads.
 *
 * <p>This object is never serialised directly. {@link ConfigManager} converts it to and from
 * {@link ConfigData} so the JSON on disk stays readable and independent of Minecraft types.
 */
public final class EspConfig {
    private final Map<EspCategory, CategoryStyle> styles = new EnumMap<>(EspCategory.class);
    private final RenderSettings render = new RenderSettings();
    private final PlayerFilters players = new PlayerFilters();
    private final SelectionMap<Block> blocks = new SelectionMap<>(Registries.BLOCK);
    private final SelectionMap<Item> items = new SelectionMap<>(Registries.ITEM);
    private final SelectionMap<EntityType<?>> entities = new SelectionMap<>(Registries.ENTITY_TYPE);
    private final Set<String> friends = new LinkedHashSet<>();

    private boolean blocksEnabled;
    private boolean itemsEnabled;
    private boolean entitiesEnabled;

    public EspConfig() {
        // Hitbox shapes by default for entities/items/blocks, so iron golems, creepers, players,
        // armor stands and items get their real bounding boxes instead of a flat snapshot box.
        styles.put(EspCategory.BLOCK, new CategoryStyle(new EspColor(0, 200, 255), new EspColor(0, 200, 255, 50), BoxMode.OUTLINE, ShapeMode.HITBOX));
        styles.put(EspCategory.ITEM, new CategoryStyle(new EspColor(255, 220, 0), new EspColor(255, 220, 0, 50), BoxMode.OUTLINE, ShapeMode.HITBOX));
        styles.put(EspCategory.ENTITY, new CategoryStyle(new EspColor(255, 60, 60), new EspColor(255, 60, 60, 50), BoxMode.BOTH, ShapeMode.HITBOX));
    }

    public CategoryStyle style(EspCategory category) {
        return styles.get(category);
    }

    public RenderSettings render() {
        return render;
    }

    public PlayerFilters players() {
        return players;
    }

    public SelectionMap<Block> blocks() {
        return blocks;
    }

    public SelectionMap<Item> items() {
        return items;
    }

    public SelectionMap<EntityType<?>> entities() {
        return entities;
    }

    public boolean isEnabled(EspCategory category) {
        return switch (category) {
            case BLOCK -> blocksEnabled;
            case ITEM -> itemsEnabled;
            case ENTITY -> entitiesEnabled;
        };
    }

    public void setEnabled(EspCategory category, boolean enabled) {
        switch (category) {
            case BLOCK -> blocksEnabled = enabled;
            case ITEM -> itemsEnabled = enabled;
            case ENTITY -> entitiesEnabled = enabled;
        }
    }

    public Set<String> friends() {
        return friends;
    }

    public boolean isFriend(String name) {
        return name != null && friends.contains(name.toLowerCase(Locale.ROOT));
    }

    /** @return true when the name was added */
    public boolean addFriend(String name) {
        if (name == null) return false;

        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false;

        return friends.add(trimmed.toLowerCase(Locale.ROOT));
    }

    public boolean removeFriend(String name) {
        return name != null && friends.remove(name.trim().toLowerCase(Locale.ROOT));
    }
}

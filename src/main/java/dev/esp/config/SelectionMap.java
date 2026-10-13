package dev.esp.config;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A complete, live {@code Map<registry entry, Boolean>} for one registry.
 *
 * <p>Every entry of the registry gets an entry here, so the GUI can show and toggle anything in
 * vanilla Minecraft, including content added by other mods. Only the enabled entries are written to
 * the config file (by registry id) which keeps the saved JSON small and readable while the in-memory
 * map stays authoritative.
 *
 * <p>It also holds an optional colour override per entry, so a single block, item or entity type
 * can be drawn in its own colour instead of the category's. Overrides are saved by registry id,
 * exactly like selections.
 *
 * @param <T> registry entry type, for example {@code Block}, {@code Item} or {@code EntityType<?>}
 */
public final class SelectionMap<T> {
    private final Registry<T> registry;
    private final Map<T, Boolean> values = new LinkedHashMap<>();
    private final Map<T, EspColor> colors = new HashMap<>();

    private Map<String, T> byId;

    public SelectionMap(Registry<T> registry) {
        this.registry = registry;

        for (T value : registry) {
            values.put(value, Boolean.FALSE);
        }
    }

    public Registry<T> registry() {
        return registry;
    }

    public boolean isEnabled(T key) {
        return Boolean.TRUE.equals(values.get(key));
    }

    public void set(T key, boolean enabled) {
        if (values.containsKey(key)) {
            values.put(key, enabled);
        }
    }

    public int enabledCount() {
        int count = 0;

        for (Boolean enabled : values.values()) {
            if (Boolean.TRUE.equals(enabled)) count++;
        }

        return count;
    }

    public void clear() {
        values.replaceAll((key, value) -> Boolean.FALSE);
    }

    public List<T> entries() {
        return new ArrayList<>(values.keySet());
    }

    /** @return the colour to draw for this entry instead of the category's, or {@code null} */
    public EspColor color(T key) {
        return colors.get(key);
    }

    /** Sets the per entry colour, or clears it when {@code color} is {@code null}. */
    public void color(T key, EspColor color) {
        if (!values.containsKey(key)) return;

        if (color == null) colors.remove(key);
        else colors.put(key, color);
    }

    /** @return the per entry colour of a saved registry id, or {@code null} when there is none */
    public EspColor colorOfId(String id) {
        T value = resolve(id);
        return value == null ? null : colors.get(value);
    }

    /** Sets or clears the per entry colour of a saved registry id. */
    public void setColorOfId(String id, EspColor color) {
        T value = resolve(id);
        if (value != null) color(value, color);
    }

    /** @return registry id to packed ARGB for every colour override, sorted for a stable file */
    public Map<String, Integer> colorIds() {
        Map<String, Integer> result = new TreeMap<>();

        for (Map.Entry<T, EspColor> entry : colors.entrySet()) {
            Identifier id = registry.getId(entry.getKey());
            if (id != null) result.put(id.toString(), entry.getValue().toArgb());
        }

        return result;
    }

    /** Applies saved colour overrides by registry id; ids a removed mod owned are dropped.
     *
     * @return the number of overrides that did not resolve
     */
    public int applyColorIds(Map<String, Integer> saved) {
        colors.clear();
        if (saved == null || saved.isEmpty()) return 0;

        int unknown = 0;

        for (Map.Entry<String, Integer> entry : saved.entrySet()) {
            T value = resolve(entry.getKey());
            Integer argb = entry.getValue();

            if (value == null || argb == null) {
                unknown++;
                continue;
            }

            colors.put(value, EspColor.ofArgb(argb));
        }

        return unknown;
    }

    /** @return the registry ids of every enabled entry, sorted so the config file is stable */
    public Set<String> enabledIds() {
        Set<String> ids = new TreeSet<>();

        for (Map.Entry<T, Boolean> entry : values.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;

            Identifier id = registry.getId(entry.getKey());
            if (id != null) ids.add(id.toString());
        }

        return ids;
    }

    /**
     * Enables every entry named in {@code ids} and disables everything else.
     *
     * @return the number of ids that did not resolve, usually because a mod was removed
     */
    public int applyIds(Collection<String> ids) {
        clear();

        int unknown = 0;

        for (String raw : ids) {
            T value = resolve(raw);

            if (value == null) {
                unknown++;
                continue;
            }

            values.put(value, Boolean.TRUE);
        }

        return unknown;
    }

    /**
     * Resolves a saved id by matching it against the registry's own ids. Building the lookup on
     * first use keeps loading independent of any parsing behaviour and tolerates mods being removed.
     */
    private T resolve(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        if (byId == null) {
            byId = new HashMap<>(values.size());

            for (T value : values.keySet()) {
                Identifier id = registry.getId(value);
                if (id != null) byId.put(id.toString(), value);
            }
        }

        return byId.get(raw);
    }
}

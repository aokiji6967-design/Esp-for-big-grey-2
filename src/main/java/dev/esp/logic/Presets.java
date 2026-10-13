package dev.esp.logic;

import dev.esp.config.EspCategory;

import java.util.List;
import java.util.function.Predicate;

/**
 * One-click selections for the entries people actually want, so the huge registry lists stay usable.
 *
 * <p>A preset matches either by registry id path or by the row's group label. Both forms are
 * name-based, so presets keep working when Minecraft adds new blocks, items and entities that follow
 * the existing naming conventions.
 */
public final class Presets {
    /** @param idPath the registry id without its namespace, for example {@code diamond_ore} */
    public record Preset(String label, EspCategory category, String group, Predicate<String> idMatch) {
        static Preset ids(String label, EspCategory category, Predicate<String> idMatch) {
            return new Preset(label, category, null, idMatch);
        }

        static Preset group(String label, EspCategory category, String group) {
            return new Preset(label, category, group, id -> false);
        }

        public boolean matches(TargetCatalog.Row row) {
            if (group != null) return row.group().equals(group);

            String id = row.id();
            int colon = id.indexOf(':');
            String path = colon < 0 ? id : id.substring(colon + 1);

            return idMatch.test(path);
        }
    }

    private static final List<Preset> PRESETS = List.of(
        // Blocks
        Preset.ids("Ores", EspCategory.BLOCK, path -> path.endsWith("_ore") || path.contains("ancient_debris")),
        Preset.ids("Containers", EspCategory.BLOCK, path -> path.contains("chest")
            || path.contains("barrel")
            || path.contains("shulker_box")
            || path.contains("hopper")
            || path.contains("dropper")
            || path.contains("dispenser")
            || path.contains("crafter")),
        Preset.ids("Special", EspCategory.BLOCK, path -> path.contains("spawner")
            || path.contains("obsidian")
            || path.contains("enchanting_table")
            || path.contains("anvil")
            || path.contains("beacon")
            || path.contains("end_portal_frame")
            || path.contains("respawn_anchor")),

        // Items
        Preset.ids("Valuables", EspCategory.ITEM, path -> path.equals("diamond")
            || path.equals("emerald")
            || path.contains("netherite")
            || path.equals("gold_ingot")
            || path.equals("iron_ingot")
            || path.equals("echo_shard")
            || path.startsWith("raw_")),
        Preset.ids("Gear", EspCategory.ITEM, path -> path.endsWith("_sword")
            || path.endsWith("_pickaxe")
            || path.endsWith("_axe")
            || path.endsWith("_helmet")
            || path.endsWith("_chestplate")
            || path.endsWith("_leggings")
            || path.endsWith("_boots")
            || path.equals("bow")
            || path.equals("crossbow")
            || path.equals("trident")
            || path.equals("elytra")
            || path.equals("totem_of_undying")),
        Preset.ids("Consumables", EspCategory.ITEM, path -> path.contains("apple")
            || path.contains("potion")
            || path.equals("ender_pearl")
            || path.startsWith("cooked_")
            || path.endsWith("_stew")),

        // Entities, matched through the same grouping the entity tab uses
        Preset.group("Players", EspCategory.ENTITY, EntityGroup.PLAYER.displayName()),
        Preset.group("Hostile Mobs", EspCategory.ENTITY, EntityGroup.HOSTILE.displayName()),
        Preset.group("Passive Mobs", EspCategory.ENTITY, EntityGroup.PASSIVE.displayName()),
        Preset.group("Projectiles", EspCategory.ENTITY, EntityGroup.PROJECTILE.displayName())
    );

    private Presets() {}

    public static List<Preset> forCategory(EspCategory category) {
        return PRESETS.stream().filter(preset -> preset.category() == category).toList();
    }

    /** @return how many rows the preset turned on */
    public static int apply(TargetCatalog catalog, Preset preset) {
        int changed = 0;

        for (TargetCatalog.Row row : catalog.all(preset.category())) {
            if (!preset.matches(row)) continue;

            row.set(true);
            changed++;
        }

        return changed;
    }
}

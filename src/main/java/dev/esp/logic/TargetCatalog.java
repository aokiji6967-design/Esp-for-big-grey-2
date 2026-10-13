package dev.esp.logic;

import dev.esp.config.EspCategory;
import dev.esp.config.EspConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The searchable list model behind the selection GUI.
 *
 * <p>Rows are built once from the registries, so the GUI never has to know about blocks, items or
 * entity types directly - it only deals with {@link Row} objects. Because everything comes from the
 * registry, every vanilla entry is present and modded entries appear automatically.
 */
public final class TargetCatalog {
    /** One selectable line in the GUI. */
    public record Row(
        String id,
        String displayName,
        String searchKey,
        String group,
        BooleanSupplier enabled,
        Consumer<Boolean> setter
    ) {
        public boolean isEnabled() {
            return enabled.getAsBoolean();
        }

        public void set(boolean value) {
            setter.accept(value);
        }
    }

    private static final Set<String> CONTAINER_MARKERS = Set.of(
        "chest", "barrel", "shulker_box", "hopper", "dropper", "dispenser", "crafter"
    );

    private static final Set<String> SPECIAL_BLOCKS = Set.of(
        "spawner", "obsidian", "crying_obsidian", "enchanting_table", "anvil", "chipped_anvil",
        "damaged_anvil", "beacon", "end_portal_frame", "respawn_anchor", "lodestone", "conduit",
        "budding_amethyst", "trial_spawner", "vault"
    );

    private static final Set<String> VALUABLES = Set.of(
        "diamond", "emerald", "netherite_ingot", "netherite_scrap", "gold_ingot", "iron_ingot",
        "copper_ingot", "coal", "redstone", "lapis_lazuli", "nether_quartz", "amethyst_shard",
        "raw_iron", "raw_gold", "raw_copper", "diamond_block", "emerald_block", "netherite_block",
        "gold_block", "iron_block", "echo_shard", "ancient_debris", "nether_star"
    );

    private static final Set<String> GEAR_MARKERS = Set.of(
        "sword", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "shield",
        "bow", "crossbow", "trident", "elytra", "totem_of_undying", "arrow", "fishing_rod", "shears",
        "flint_and_steel", "axe", "mace", "spear"
    );

    private static final Set<String> CONSUMABLE_MARKERS = Set.of(
        "apple", "bread", "carrot", "potato", "beef", "porkchop", "mutton", "cod", "salmon", "cookie",
        "cake", "stew", "soup", "potion", "ender_pearl", "honey_bottle", "melon_slice",
        "sweet_berries", "berries", "milk_bucket", "chorus_fruit", "kelp", "rabbit", "tropical_fish"
    );

    private final EspConfig config;
    private final Map<EspCategory, List<Row>> rows = new EnumMap<>(EspCategory.class);
    private final Map<EspCategory, List<String>> groups = new EnumMap<>(EspCategory.class);

    public TargetCatalog(EspConfig config) {
        this.config = config;

        build();
    }

    private void build() {
        List<Row> blockRows = new ArrayList<>();
        Set<String> blockGroups = new LinkedHashSet<>();

        for (Block block : config.blocks().entries()) {
            Identifier id = config.blocks().registry().getId(block);
            if (id == null) continue;

            String display = displayName(block.getName().getString(), id);
            String group = blockGroup(id.getPath());
            blockGroups.add(group);

            blockRows.add(new Row(id.toString(), display, searchKey(display, id),
                group,
                () -> config.blocks().isEnabled(block),
                value -> config.blocks().set(block, value)));
        }

        sort(blockRows);
        rows.put(EspCategory.BLOCK, blockRows);
        groups.put(EspCategory.BLOCK, orderedGroups(blockGroups));

        List<Row> itemRows = new ArrayList<>();
        Set<String> itemGroups = new LinkedHashSet<>();

        for (Item item : config.items().entries()) {
            Identifier id = config.items().registry().getId(item);
            if (id == null) continue;

            String display = displayName(item.getName().getString(), id);
            String group = itemGroup(id.getPath());
            itemGroups.add(group);

            itemRows.add(new Row(id.toString(), display, searchKey(display, id),
                group,
                () -> config.items().isEnabled(item),
                value -> config.items().set(item, value)));
        }

        sort(itemRows);
        rows.put(EspCategory.ITEM, itemRows);
        groups.put(EspCategory.ITEM, orderedGroups(itemGroups));

        List<Row> entityRows = new ArrayList<>();
        Set<String> entityGroups = new LinkedHashSet<>();

        for (EntityType<?> type : config.entities().entries()) {
            Identifier id = config.entities().registry().getId(type);
            if (id == null) continue;

            String display = displayName(type.getName().getString(), id);
            String group = EntityGroup.of(type).displayName();
            entityGroups.add(group);

            entityRows.add(new Row(id.toString(), display, searchKey(display, id),
                group,
                () -> config.entities().isEnabled(type),
                value -> config.entities().set(type, value)));
        }

        sort(entityRows);
        rows.put(EspCategory.ENTITY, entityRows);
        groups.put(EspCategory.ENTITY, orderedGroups(entityGroups));
    }

    /** @return every row of a category, in display order */
    public List<Row> all(EspCategory category) {
        return rows.get(category);
    }

    /** @return the group labels of a category, always starting with {@link #ALL}. */
    public List<String> groups(EspCategory category) {
        return groups.get(category);
    }

    public static final String ALL = "All";

    /**
     * @param query lowercase search text, may be empty
     * @param group a group label, or {@link #ALL} to ignore grouping
     * @return the matching rows, reusing the backing list when nothing is filtered
     */
    public List<Row> filter(EspCategory category, String query, String group) {
        List<Row> source = rows.get(category);
        if (source == null) return List.of();

        boolean allGroups = group == null || ALL.equals(group);
        if (query.isEmpty() && allGroups) return source;

        List<Row> result = new ArrayList<>(Math.min(source.size(), 128));

        for (Row row : source) {
            if (!allGroups && !row.group().equals(group)) continue;
            if (!query.isEmpty() && !row.searchKey().contains(query)) continue;

            result.add(row);
        }

        return result;
    }

    public int total(EspCategory category) {
        List<Row> source = rows.get(category);
        return source == null ? 0 : source.size();
    }

    public int enabledCount(EspCategory category) {
        List<Row> source = rows.get(category);
        if (source == null) return 0;

        int count = 0;
        for (Row row : source) {
            if (row.isEnabled()) count++;
        }

        return count;
    }

    /** Enables every row of a category, or every row of one group when {@code group} is given. */
    public void setAll(EspCategory category, String group, boolean enabled) {
        List<Row> source = rows.get(category);
        if (source == null) return;

        boolean allGroups = group == null || ALL.equals(group);

        for (Row row : source) {
            if (!allGroups && !row.group().equals(group)) continue;

            row.set(enabled);
        }
    }

    private static void sort(List<Row> rows) {
        rows.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
    }

    private static List<String> orderedGroups(Set<String> found) {
        List<String> preferred = List.of(
            ALL, "Ores", "Containers", "Special", "Valuables", "Gear", "Consumables",
            "Players", "Hostile", "Passive", "Water", "Ambient", "Projectiles", "Utility", "Other"
        );

        List<String> ordered = new ArrayList<>();

        for (String name : preferred) {
            if (ALL.equals(name) || found.contains(name)) ordered.add(name);
        }

        for (String name : found) {
            if (!ordered.contains(name)) ordered.add(name);
        }

        return ordered;
    }

    private static String blockGroup(String path) {
        if (path.endsWith("_ore") || path.contains("ancient_debris")) return "Ores";

        for (String marker : CONTAINER_MARKERS) {
            if (path.contains(marker)) return "Containers";
        }

        if (SPECIAL_BLOCKS.contains(path)) return "Special";

        return "Other";
    }

    private static String itemGroup(String path) {
        if (VALUABLES.contains(path)) return "Valuables";

        for (String marker : GEAR_MARKERS) {
            if (path.contains(marker)) return "Gear";
        }

        for (String marker : CONSUMABLE_MARKERS) {
            if (path.contains(marker)) return "Consumables";
        }

        return "Other";
    }

    private static String searchKey(String display, Identifier id) {
        return (display + " " + id.getPath().replace('_', ' ')).toLowerCase(Locale.ROOT);
    }

    private static String displayName(String translated, Identifier id) {
        if (translated != null && !translated.isBlank()) return translated;

        String[] parts = id.getPath().split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1));
        }

        return builder.isEmpty() ? id.toString() : builder.toString();
    }
}

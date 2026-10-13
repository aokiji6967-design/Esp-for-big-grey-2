package dev.esp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@link EspConfig} as pretty printed JSON in {@code config/esp.json}.
 *
 * <p>Writes are debounced: the GUI calls {@link #markDirty()} on every toggle and the client tick
 * calls {@link #tick()}, so dragging a slider does not hammer the disk. Each write goes to a
 * temporary file first and is then moved into place, so a crash cannot leave a truncated config.
 */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("esp");
    private static final String FILE_NAME = "esp.json";
    private static final long SAVE_DELAY_MS = 500L;

    private final EspConfig config;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private boolean dirty;
    private long dirtySince;

    public ConfigManager(EspConfig config) {
        this.config = config;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public Path file() {
        return file;
    }

    public void load() {
        if (!Files.exists(file)) {
            // Give the user a complete, commented-by-example config instead of an empty one.
            if (save()) LOG.info("Wrote default config to {}", file);
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ConfigData data = gson.fromJson(reader, ConfigData.class);

            if (data == null) {
                LOG.warn("{} is empty, keeping defaults", file);
                return;
            }

            if (data.version > ConfigData.CURRENT_VERSION) {
                LOG.warn("{} was written by a newer version of the mod (v{}), unknown fields are ignored", file, data.version);
            }

            data.sanitise();
            apply(data);
            LOG.info("Loaded config from {}", file);
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("Could not read {}, keeping defaults", file, e);
        }
    }

    /** @return true when the file was written */
    public boolean save() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            Path temp = file.resolveSibling(FILE_NAME + ".tmp");

            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot(), writer);
            }

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);

            dirty = false;
            return true;
        } catch (IOException e) {
            LOG.error("Could not write {}", file, e);
            return false;
        }
    }

    /** Flags the config as changed; the next {@link #tick()} after the debounce writes it. */
    public void markDirty() {
        dirty = true;
        dirtySince = System.currentTimeMillis();
    }

    public void tick() {
        if (!dirty) return;
        if (System.currentTimeMillis() - dirtySince < SAVE_DELAY_MS) return;

        save();
    }

    private void apply(ConfigData data) {
        config.setEnabled(EspCategory.BLOCK, data.blocksEnabled);
        config.setEnabled(EspCategory.ITEM, data.itemsEnabled);
        config.setEnabled(EspCategory.ENTITY, data.entitiesEnabled);

        int unknown = config.blocks().applyIds(emptyIfNull(data.blocks));
        unknown += config.items().applyIds(emptyIfNull(data.items));
        unknown += config.entities().applyIds(emptyIfNull(data.entities));

        if (unknown > 0) LOG.warn("{} saved selection(s) could not be resolved and were dropped", unknown);

        int unknownColors = config.blocks().applyColorIds(data.blockColors);
        unknownColors += config.items().applyColorIds(data.itemColors);
        unknownColors += config.entities().applyColorIds(data.entityColors);

        if (unknownColors > 0) {
            LOG.warn("{} saved colour override(s) could not be resolved and were dropped", unknownColors);
        }

        for (Map.Entry<String, ConfigData.StyleData> entry : data.styles.entrySet()) {
            EspCategory category = parseCategory(entry.getKey());
            if (category == null) continue;

            applyStyle(config.style(category), entry.getValue());
        }

        RenderSettings render = config.render();
        render.range(data.render.range);
        render.maxTargets(data.render.maxTargets);
        render.lineThickness(data.render.lineThickness);
        render.occlusionCheck(data.render.occlusionCheck);
        render.interpolate(data.render.interpolate);
        render.blockScanBudget(data.render.blockScanBudget);
        render.tracerAlpha(data.render.tracerAlpha);

        PlayerFilters players = config.players();
        players.renderSelf(data.players.renderSelf);
        players.renderOthers(data.players.renderOthers);
        players.onlyFriends(data.players.onlyFriends);
        players.highlightFriends(data.players.highlightFriends);
        players.renderInvisible(data.players.renderInvisible);

        config.friends().clear();
        for (String friend : emptyIfNull(data.friends)) {
            config.addFriend(friend);
        }
    }

    private ConfigData snapshot() {
        ConfigData data = new ConfigData();

        data.blocksEnabled = config.isEnabled(EspCategory.BLOCK);
        data.itemsEnabled = config.isEnabled(EspCategory.ITEM);
        data.entitiesEnabled = config.isEnabled(EspCategory.ENTITY);

        data.blocks = new ArrayList<>(config.blocks().enabledIds());
        data.items = new ArrayList<>(config.items().enabledIds());
        data.entities = new ArrayList<>(config.entities().enabledIds());
        data.friends = new ArrayList<>(config.friends());

        data.blockColors = new LinkedHashMap<>(config.blocks().colorIds());
        data.itemColors = new LinkedHashMap<>(config.items().colorIds());
        data.entityColors = new LinkedHashMap<>(config.entities().colorIds());

        for (EspCategory category : EspCategory.values()) {
            data.styles.put(category.name(), snapshotStyle(config.style(category)));
        }

        RenderSettings render = config.render();
        data.render.range = render.range();
        data.render.maxTargets = render.maxTargets();
        data.render.lineThickness = render.lineThickness();
        data.render.occlusionCheck = render.occlusionCheck();
        data.render.interpolate = render.interpolate();
        data.render.blockScanBudget = render.blockScanBudget();
        data.render.tracerAlpha = render.tracerAlpha();

        PlayerFilters players = config.players();
        data.players.renderSelf = players.renderSelf();
        data.players.renderOthers = players.renderOthers();
        data.players.onlyFriends = players.onlyFriends();
        data.players.highlightFriends = players.highlightFriends();
        data.players.renderInvisible = players.renderInvisible();

        return data;
    }

    private static ConfigData.StyleData snapshotStyle(CategoryStyle style) {
        ConfigData.StyleData data = new ConfigData.StyleData();

        data.outlineColor = style.outlineColor().toArgb();
        data.fillColor = style.fillColor().toArgb();
        data.boxMode = style.boxMode().name();
        data.shapeMode = style.shapeMode().name();
        data.tracers = style.tracers();
        data.gradient = style.gradient();
        data.gradientColor = style.gradientColor().toArgb();
        data.chroma = style.chroma();
        data.chromaSpeed = style.chromaSpeed();
        data.healthColor = style.healthColor();

        return data;
    }

    private static void applyStyle(CategoryStyle style, ConfigData.StyleData data) {
        if (data == null) return;

        style.outlineColor(EspColor.ofArgb(data.outlineColor));
        style.fillColor(EspColor.ofArgb(data.fillColor));
        style.gradientColor(EspColor.ofArgb(data.gradientColor));
        style.tracers(data.tracers);
        style.gradient(data.gradient);
        style.chroma(data.chroma);
        style.chromaSpeed(data.chromaSpeed);
        style.healthColor(data.healthColor);

        if (data.boxMode != null) {
            try {
                style.boxMode(BoxMode.valueOf(data.boxMode));
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown box mode '{}', keeping {}", data.boxMode, style.boxMode());
            }
        }

        if (data.shapeMode != null) {
            try {
                style.shapeMode(ShapeMode.valueOf(data.shapeMode));
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown shape mode '{}', keeping {}", data.shapeMode, style.shapeMode());
            }
        }
    }

    private static EspCategory parseCategory(String name) {
        try {
            return EspCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown category '{}' in config", name);
            return null;
        }
    }

    private static Collection<String> emptyIfNull(Collection<String> values) {
        return values != null ? values : List.of();
    }
}

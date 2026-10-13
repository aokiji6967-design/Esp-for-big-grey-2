package dev.esp;

import dev.esp.config.ConfigManager;
import dev.esp.config.EspConfig;
import dev.esp.logic.TargetCollector;
import dev.esp.logic.TargetCatalog;
import dev.esp.render.EspRenderer;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

/**
 * The mod's service holder: one instance, created by {@link EspMod} during client initialisation.
 *
 * <p>Keeping the pieces here (rather than in static fields scattered around) keeps the data flow
 * obvious: the tick updates the config file and the scanners, the game renderer calls
 * {@link #renderWorld(Matrix4f, float)}, and the GUI reads and writes the same {@link EspConfig}.
 */
public final class EspClient {
    private static EspClient instance;

    private final EspConfig config = new EspConfig();
    private final ConfigManager configManager = new ConfigManager(config);
    private final TargetCatalog catalog = new TargetCatalog(config);
    private final TargetCollector collector = new TargetCollector();
    private final EspRenderer renderer = new EspRenderer();

    private EspClient() {}

    static EspClient create() {
        instance = new EspClient();
        instance.configManager.load();

        return instance;
    }

    /** @return the live instance, or {@code null} before client initialisation */
    public static EspClient orNull() {
        return instance;
    }

    public EspConfig config() {
        return config;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public TargetCatalog catalog() {
        return catalog;
    }

    public TargetCollector collector() {
        return collector;
    }

    /** Called from the client tick: debounced config writes, target scanning and timers. */
    public void tick(MinecraftClient client) {
        configManager.tick();
        renderer.tick();
        collector.tick(client, config);
    }

    /** Called from the game renderer hook once per frame. */
    public void renderWorld(Matrix4f projection, float tickDelta) {
        renderer.render(MinecraftClient.getInstance(), config, collector.snapshot(), projection, tickDelta);
    }

    /** Flags the config as changed so it is written shortly after the last edit. */
    public void save() {
        configManager.markDirty();
    }
}

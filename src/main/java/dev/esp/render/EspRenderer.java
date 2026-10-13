package dev.esp.render;

import dev.esp.config.BoxMode;
import dev.esp.config.CategoryStyle;
import dev.esp.config.EspCategory;
import dev.esp.config.EspColor;
import dev.esp.config.EspConfig;
import dev.esp.config.ShapeMode;
import dev.esp.logic.EspRange;
import dev.esp.logic.EspTarget;
import dev.esp.logic.Occlusion;
import dev.esp.logic.TargetKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The render engine: turns the current target snapshot into a single mesh and submits it.
 *
 * <p>One mesh per frame means one draw call for the whole overlay, whatever the number of targets.
 * Distance culling, the target cap and the optional line of sight test all happen here, so the cost
 * of a frame is bounded by {@code maxTargets} per category rather than by the size of the selection.
 * The draw distance is the effective range, which is never shorter than the render distance, so
 * everything the scanners found within what the player can see is actually drawn.
 *
 * <p>Geometry is submitted in three passes - fills, outlines, tracers - so that outlines and tracers
 * are never washed out by a neighbouring target's translucent fill.
 */
public final class EspRenderer {
    /** Upper bound on raycasts per frame when the occlusion check is on. */
    private static final int MAX_OCCLUSION_CHECKS = 256;

    /** Tracers are drawn a little thinner than outlines. */
    private static final double TRACER_WIDTH_FACTOR = 0.75;

    private final MeshBuilder mesh = new MeshBuilder(EspPipelines.WORLD_COLORED.getVertexFormat());
    private final List<EspTarget> visible = new ArrayList<>();

    /** Seconds since the client started; drives chroma. */
    private double time;

    public void render(
        MinecraftClient client,
        EspConfig config,
        List<EspTarget> targets,
        Matrix4f projection,
        float tickDelta
    ) {
        if (targets.isEmpty()) return;

        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) return;

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

        double range = EspRange.effective(config.render(), client);
        double rangeSq = range * range;
        int maxTargets = config.render().maxTargets();
        boolean occlusion = config.render().occlusionCheck();
        boolean interpolate = config.render().interpolate();

        collectVisible(targets, world, player, camera, rangeSq, maxTargets, occlusion);

        if (visible.isEmpty()) return;

        // Line widths are configured in pixels, so they are converted with this frame's projection
        // and viewport rather than baked in as world sizes.
        float verticalScale = projection.m11();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        double thickness = config.render().lineThickness();

        Geometry boxes = new Geometry(mesh, LineStyle.of(thickness, verticalScale, framebufferHeight), camera);
        Geometry tracers = new Geometry(
            mesh,
            LineStyle.of(thickness * TRACER_WIDTH_FACTOR, verticalScale, framebufferHeight).atLeastOnePixel(),
            camera
        );

        mesh.begin(camera.x, camera.z);

        // Draw each category's requested shape. Hitbox bounds draws the target's real bounding
        // box at draw time, so entities (iron golems, creepers, players, armor stands, etc.) get
        // their actual shape instead of the stored one-shot snapshot box. Interpolated hitboxes
        // still smooth entities between ticks; blocks and items keep their snapshot box.
        for (EspTarget target : visible) drawShape(config, boxes, target, tickDelta, interpolate);
        for (EspTarget target : visible) drawOutline(config, boxes, target, tickDelta, interpolate);
        for (EspTarget target : visible) drawTracer(config, tracers, target, tickDelta, interpolate, camera);

        mesh.end();

        MeshUniforms.flip();
        MeshRenderer.draw(EspPipelines.WORLD_COLORED, mesh, projection, camera.y);
    }

    private void collectVisible(
        List<EspTarget> targets,
        ClientWorld world,
        ClientPlayerEntity player,
        Vec3d camera,
        double rangeSq,
        int maxTargets,
        boolean occlusion
    ) {
        visible.clear();

        int checks = 0;
        int[] perCategory = new int[EspCategory.values().length];

        for (EspTarget target : targets) {
            // The cap is per category so a full block sweep can never crowd dropped items or
            // entities out of the frame.
            int slot = target.category().ordinal();
            if (perCategory[slot] >= maxTargets) continue;
            if (target.distanceSq(camera.x, camera.y, camera.z) > rangeSq) continue;

            if (occlusion && checks < MAX_OCCLUSION_CHECKS) {
                checks++;

                Vec3d center = target.center();
                if (!Occlusion.isVisible(world, player, camera, center)) continue;
            }

            perCategory[slot]++;
            visible.add(target);
        }
    }

    // ---------- shape pass ----------

    /** Translucent fill pass, honouring the category's box mode. */
    private void drawShape(
        EspConfig config, Geometry geometry, EspTarget target, float tickDelta, boolean interpolate
    ) {
        CategoryStyle style = config.style(target.category());
        if (!boxMode(style).sides()) return;

        Box box = shapeBoxOf(target, shapeMode(style), tickDelta, interpolate);

        geometry.box(
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            colour(config, target, style.fillColor(), 0.0),
            colour(config, target, style.fillColor(), 1.0),
            true, false
        );
    }

    /** Wireframe pass, honouring the category's box mode. */
    private void drawOutline(
        EspConfig config, Geometry geometry, EspTarget target, float tickDelta, boolean interpolate
    ) {
        CategoryStyle style = config.style(target.category());
        if (!boxMode(style).lines()) return;

        Box box = shapeBoxOf(target, shapeMode(style), tickDelta, interpolate);

        geometry.box(
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            colour(config, target, style.outlineColor(), 0.0),
            colour(config, target, style.outlineColor(), 1.0),
            false, true
        );
    }

    private static ShapeMode shapeMode(CategoryStyle style) {
        return style.shapeMode() != null ? style.shapeMode() : ShapeMode.HITBOX;
    }

    private static BoxMode boxMode(CategoryStyle style) {
        return style.boxMode() != null ? style.boxMode() : BoxMode.OUTLINE;
    }

    private static Box shapeBoxOf(EspTarget target, ShapeMode mode, float tickDelta, boolean interpolate) {
        Entity entity = target.entity();

        // BOX keeps the stored snapshot box; HITBOX asks the entity for its real bounding box at
        // draw time, so an iron golem, creeper, player or armor stand gets its actual shape.
        if (entity != null && mode == ShapeMode.HITBOX) {
            // Use the entity's real bounding box at draw time, so entities get their actual shape
            // (iron golem, creeper, zombie, armor stand, player, etc.) instead of whatever was
            // snapshotted last tick. Items and blocks keep the snapshot box because they do not
            // have a meaningful moving entity box to ask for.
            if (interpolate && target.kind() != TargetKind.BLOCK) {
                return EspTarget.interpolatedBox(entity, tickDelta);
            }

            return entity.getBoundingBox();
        }

        return target.box();
    }

    private void drawTracer(
        EspConfig config, Geometry geometry, EspTarget target,
        float tickDelta, boolean interpolate, Vec3d camera
    ) {
        CategoryStyle style = config.style(target.category());
        if (!style.tracers()) return;

        Box box = boxOf(target, tickDelta, interpolate);

        double tracerAlpha = config.render().tracerAlpha();

        geometry.ribbon(
            camera.x, camera.y, camera.z,
            (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
            colour(config, target, style.outlineColor(), 0.0).mulAlpha(tracerAlpha),
            colour(config, target, style.outlineColor(), 1.0).mulAlpha(tracerAlpha)
        );
    }

    private EspColor colour(EspConfig config, EspTarget target, EspColor base, double progress) {
        return ColorProvider.resolve(config, target, base, progress, time);
    }

    private static Box boxOf(EspTarget target, float tickDelta, boolean interpolate) {
        // Only living, moving things need interpolating; blocks stay where they are.
        if (interpolate && target.kind() != TargetKind.BLOCK && target.entity() != null) {
            return EspTarget.interpolatedBox(target.entity(), tickDelta);
        }

        return target.box();
    }

    /** Called once per client tick to advance the chroma clock. */
    public void tick() {
        time += 1.0 / 20.0;
    }

    /**
     * Legacy helper used by older code that still calls box() directly; kept only so this class
     * continues to satisfy any callers that have not migrated to {@link #drawShape drawShape}.
     * Everything in this mod routes through the render loop, so this method is otherwise unused.
     */
    @Deprecated
    public static Box legacyBoxOf(EspTarget target, float tickDelta, boolean interpolate) {
        if (interpolate && target.kind() != TargetKind.BLOCK && target.entity() != null) {
            return EspTarget.interpolatedBox(target.entity(), tickDelta);
        }

        return target.box();
    }
}

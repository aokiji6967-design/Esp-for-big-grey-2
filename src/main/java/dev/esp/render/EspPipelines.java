package dev.esp.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The render pipeline used for all world space ESP geometry.
 *
 * <p>The depth test is disabled on purpose: that is what lets boxes and tracers stay visible through
 * terrain. The occlusion setting compensates by skipping targets without line of sight. Geometry is
 * submitted as triangles so an outline can be drawn as a camera facing ribbon, which gives real,
 * configurable line thickness instead of the fixed width of GL lines.
 *
 * <p>The shaders live in this mod's own assets, and 1.21.11 has no way to discover them on its own:
 * vanilla only resolves the shaders it ships. {@link #precompile()} therefore compiles the pipeline
 * with an explicit source provider, and {@code ShaderLoaderMixin} calls it after every resource
 * reload so the pipeline is rebuilt whenever vanilla clears its pipeline cache.
 */
public final class EspPipelines {
    private static final Logger LOG = LoggerFactory.getLogger("esp");

    private static final Identifier VERTEX_SHADER = Identifier.of("esp", "shaders/esp_box.vert");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("esp", "shaders/esp_box.frag");

    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    public static final RenderPipeline WORLD_COLORED = add(RenderPipeline.builder()
        .withLocation(Identifier.of("esp", "pipeline/esp_world_colored"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(VERTEX_SHADER)
        .withFragmentShader(FRAGMENT_SHADER)
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build());

    /**
     * Compiles every ESP pipeline from this mod's shader assets. Called once after each resource
     * reload; cheap to call again, because the device caches a compiled pipeline per definition.
     */
    public static void precompile() {
        GpuDevice device = RenderSystem.getDevice();
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();

        for (RenderPipeline pipeline : PIPELINES) {
            CompiledRenderPipeline compiled = device.precompilePipeline(
                pipeline,
                (identifier, type) -> readShaderSource(resources, identifier)
            );

            if (!compiled.isValid()) {
                LOG.error("Pipeline {} failed to compile; the ESP overlay will not draw", pipeline.getLocation());
            }
        }
    }

    private static String readShaderSource(ResourceManager resources, Identifier identifier) {
        Resource resource = resources.getResource(identifier)
            .orElseThrow(() -> new IllegalStateException("Shader source not found: " + identifier));

        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read shader source " + identifier, e);
        }
    }

    private static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);

        return pipeline;
    }

    private EspPipelines() {}
}

package dev.esp.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.OptionalInt;

/** Draws a {@link MeshBuilder} with the ESP pipeline straight into the world framebuffer. */
public final class MeshRenderer {
    private MeshRenderer() {}

    public static void draw(RenderPipeline pipeline, MeshBuilder mesh, Matrix4f projection, double cameraY) {
        if (mesh.isEmpty()) return;

        GpuBuffer vertexBuffer = mesh.vertexBuffer();
        GpuBuffer indexBuffer = mesh.indexBuffer();

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();

        try {
            // Vertical camera offset, completing the camera relative geometry the builder wrote.
            stack.translate(0.0f, (float) -cameraY, 0.0f);

            GpuBufferSlice meshData = MeshUniforms.write(projection, stack);

            RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> "ESP",
                    MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty()
                );

            try {
                pass.setPipeline(pipeline);
                pass.setUniform("MeshData", meshData);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, mesh.indexCount(), 1);
            } finally {
                pass.close();
            }
        } finally {
            stack.popMatrix();
        }
    }
}

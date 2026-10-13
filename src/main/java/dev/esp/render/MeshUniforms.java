package dev.esp.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.gl.DynamicUniformStorage;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/**
 * A single std140 uniform block holding the projection and model view matrices, matching the
 * {@code MeshData} block declared in {@code esp_box.vert}.
 */
public final class MeshUniforms {
    public static final int SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putMat4f()
        .get();

    private static final Data DATA = new Data();
    private static final DynamicUniformStorage<Data> STORAGE =
        new DynamicUniformStorage<>("ESP - Mesh UBO", SIZE, 16);

    private MeshUniforms() {}

    /** Releases this frame's slice; call once at the start of every frame that draws ESP. */
    public static void flip() {
        STORAGE.clear();
    }

    public static GpuBufferSlice write(Matrix4f projection, Matrix4f modelView) {
        DATA.projection = projection;
        DATA.modelView = modelView;

        return STORAGE.write(DATA);
    }

    private static final class Data implements DynamicUniformStorage.Uploadable {
        private Matrix4f projection;
        private Matrix4f modelView;

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putMat4f(projection)
                .putMat4f(modelView);
        }

        @Override
        public boolean equals(Object other) {
            // Always re-upload: the matrices change every frame.
            return false;
        }
    }
}

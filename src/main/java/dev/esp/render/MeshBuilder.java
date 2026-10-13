package dev.esp.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.esp.config.EspColor;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.system.MemoryUtil.memPutInt;

/**
 * Builds one vertex and index buffer per frame for the ESP pipeline.
 *
 * <p>Geometry is written camera relative on the X and Z axes, the same convention the vanilla
 * chunk renderer uses for its vertices; the vertical offset is applied on the model view stack
 * (see {@link MeshRenderer}). Buffers grow geometrically and are reused between frames, so a steady
 * scene allocates nothing after warm up.
 */
public final class MeshBuilder {
    private static final int FLOATS_PER_POSITION = 3;
    private static final int BYTES_PER_COLOR = 4;

    private final VertexFormat format;
    private final int vertexSize;

    private ByteBuffer vertices;
    private ByteBuffer indices;
    private long verticesStart;
    private long verticesPointer;
    private long indicesPointer;

    private int vertexCount;
    private int indexCount;

    private double cameraX;
    private double cameraZ;

    private boolean building;

    public MeshBuilder(VertexFormat format) {
        this.format = format;
        this.vertexSize = format.getVertexSize();

        allocate(1024, 2048);
    }

    /** Starts a frame; {@code cameraX} and {@code cameraZ} are subtracted from every position. */
    public void begin(double cameraX, double cameraZ) {
        if (building) throw new IllegalStateException("MeshBuilder.begin called while already building");

        this.cameraX = cameraX;
        this.cameraZ = cameraZ;

        verticesPointer = verticesStart;
        vertexCount = 0;
        indexCount = 0;
        building = true;
    }

    public MeshBuilder position(double x, double y, double z) {
        long pointer = verticesPointer;

        memPutFloat(pointer, (float) (x - cameraX));
        memPutFloat(pointer + 4, (float) y);
        memPutFloat(pointer + 8, (float) (z - cameraZ));

        verticesPointer += FLOATS_PER_POSITION * Float.BYTES;
        return this;
    }

    public MeshBuilder color(EspColor color) {
        long pointer = verticesPointer;

        memPutByte(pointer, (byte) color.r);
        memPutByte(pointer + 1, (byte) color.g);
        memPutByte(pointer + 2, (byte) color.b);
        memPutByte(pointer + 3, (byte) color.a);

        verticesPointer += BYTES_PER_COLOR;
        return this;
    }

    /** Marks the current vertex as complete and returns its index. */
    public int next() {
        return vertexCount++;
    }

    public int vertex(double x, double y, double z, EspColor color) {
        return position(x, y, z).color(color).next();
    }

    public void quad(int a, int b, int c, int d) {
        ensureIndices(6);

        long pointer = indicesPointer + (long) indexCount * Integer.BYTES;

        memPutInt(pointer, a);
        memPutInt(pointer + 4, b);
        memPutInt(pointer + 8, c);

        memPutInt(pointer + 12, c);
        memPutInt(pointer + 16, d);
        memPutInt(pointer + 20, a);

        indexCount += 6;
    }

    /** Reserves room for {@code vertexTotal} more vertices and {@code indexTotal} more indices. */
    public void ensure(int vertexTotal, int indexTotal) {
        if (vertices == null || (vertexCount + vertexTotal) * vertexSize >= vertices.capacity()) {
            int used = vertexCount * vertexSize;
            int needed = (vertexCount + vertexTotal) * vertexSize;
            int newSize = Math.max(vertices == null ? 0 : vertices.capacity() * 2, needed);

            ByteBuffer grown = BufferUtils.createByteBuffer(newSize);
            if (used > 0 && vertices != null) {
                memCopy(memAddress0(vertices), memAddress0(grown), used);
            }

            vertices = grown;
            verticesStart = memAddress0(vertices);
            verticesPointer = verticesStart + used;
        }

        ensureIndices(indexTotal);
    }

    private void ensureIndices(int indexTotal) {
        if (indices == null || (indexCount + indexTotal) * Integer.BYTES >= indices.capacity()) {
            int used = indexCount * Integer.BYTES;
            int needed = (indexCount + indexTotal) * Integer.BYTES;
            int newSize = Math.max(indices == null ? 0 : indices.capacity() * 2, needed);

            ByteBuffer grown = BufferUtils.createByteBuffer(newSize);
            if (used > 0 && indices != null) {
                memCopy(memAddress0(indices), memAddress0(grown), used);
            }

            indices = grown;
            indicesPointer = memAddress0(indices);
        }
    }

    private void allocate(int vertexCapacity, int indexCapacity) {
        vertices = BufferUtils.createByteBuffer(vertexSize * vertexCapacity);
        verticesStart = memAddress0(vertices);
        verticesPointer = verticesStart;

        indices = BufferUtils.createByteBuffer(Integer.BYTES * indexCapacity);
        indicesPointer = memAddress0(indices);
    }

    public void end() {
        if (!building) throw new IllegalStateException("MeshBuilder.end called while not building");

        building = false;
    }

    public int indexCount() {
        return indexCount;
    }

    public boolean isEmpty() {
        return vertexCount == 0 || indexCount == 0;
    }

    GpuBuffer vertexBuffer() {
        vertices.limit(vertexCount * vertexSize);
        return format.uploadImmediateVertexBuffer(vertices);
    }

    GpuBuffer indexBuffer() {
        indices.limit(indexCount * Integer.BYTES);
        return format.uploadImmediateIndexBuffer(indices);
    }
}

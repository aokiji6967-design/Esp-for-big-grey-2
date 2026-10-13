package dev.esp.render;

import dev.esp.config.EspColor;
import net.minecraft.util.math.Vec3d;

/**
 * Emits world space boxes and lines into a {@link MeshBuilder}.
 *
 * <p>One instance is created per frame, holding the camera position (which ribbon orientation and
 * width both depend on) and the {@link LineStyle} for that frame. Callers therefore just describe
 * the box to draw instead of threading the same camera and thickness through every call.
 *
 * <p>Filled boxes share eight corner vertices so the vertical gradient stays continuous across
 * faces. Outlines are emitted as camera facing ribbons offset perpendicular to the view ray, and
 * their width is derived from the configured pixel width, which keeps lines thin and even instead
 * of turning into bands when the camera comes close.
 */
public final class Geometry {
    private final MeshBuilder mesh;
    private final LineStyle line;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;

    public Geometry(MeshBuilder mesh, LineStyle line, Vec3d camera) {
        this.mesh = mesh;
        this.line = line;
        this.cameraX = camera.x;
        this.cameraY = camera.y;
        this.cameraZ = camera.z;
    }

    /**
     * @param sides   draw the six solid faces
     * @param outline draw the twelve wireframe edges
     * @param bottom  colour at {@code y1}
     * @param top     colour at {@code y2}
     */
    public void box(
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        EspColor bottom, EspColor top,
        boolean sides, boolean outline
    ) {
        if (sides) faces(x1, y1, z1, x2, y2, z2, bottom, top);

        if (!outline) return;

        // Verticals
        ribbon(x1, y1, z1, x1, y2, z1, bottom, top);
        ribbon(x1, y1, z2, x1, y2, z2, bottom, top);
        ribbon(x2, y1, z1, x2, y2, z1, bottom, top);
        ribbon(x2, y1, z2, x2, y2, z2, bottom, top);

        // Bottom loop
        ribbon(x1, y1, z1, x1, y1, z2, bottom, bottom);
        ribbon(x2, y1, z1, x2, y1, z2, bottom, bottom);
        ribbon(x1, y1, z1, x2, y1, z1, bottom, bottom);
        ribbon(x1, y1, z2, x2, y1, z2, bottom, bottom);

        // Top loop
        ribbon(x1, y2, z1, x1, y2, z2, top, top);
        ribbon(x2, y2, z1, x2, y2, z2, top, top);
        ribbon(x1, y2, z1, x2, y2, z1, top, top);
        ribbon(x1, y2, z2, x2, y2, z2, top, top);
    }

    private void faces(
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        EspColor bottom, EspColor top
    ) {
        mesh.ensure(8, 36);

        int blb = mesh.vertex(x1, y1, z1, bottom);
        int blf = mesh.vertex(x1, y1, z2, bottom);
        int brb = mesh.vertex(x2, y1, z1, bottom);
        int brf = mesh.vertex(x2, y1, z2, bottom);

        int tlb = mesh.vertex(x1, y2, z1, top);
        int tlf = mesh.vertex(x1, y2, z2, top);
        int trb = mesh.vertex(x2, y2, z1, top);
        int trf = mesh.vertex(x2, y2, z2, top);

        // Winding does not matter: culling is disabled on the pipeline.
        mesh.quad(blb, brb, brf, blf);
        mesh.quad(tlb, tlf, trf, trb);
        mesh.quad(blb, tlb, trb, brb);
        mesh.quad(blf, brf, trf, tlf);
        mesh.quad(blb, blf, tlf, tlb);
        mesh.quad(brb, trb, trf, brf);
    }

    /**
     * Emits a quad that faces the camera and spans {@code a} to {@code b}, as wide as this frame's
     * {@link LineStyle} asks for at that distance. Used for both box edges and tracers.
     */
    public void ribbon(
        double ax, double ay, double az,
        double bx, double by, double bz,
        EspColor colorA, EspColor colorB
    ) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) return;

        dx /= length;
        dy /= length;
        dz /= length;

        // Offset direction: perpendicular to the segment and to the view ray.
        double mx = (ax + bx) * 0.5 - cameraX;
        double my = (ay + by) * 0.5 - cameraY;
        double mz = (az + bz) * 0.5 - cameraZ;

        double distance = Math.sqrt(mx * mx + my * my + mz * mz);

        double px = dy * mz - dz * my;
        double py = dz * mx - dx * mz;
        double pz = dx * my - dy * mx;

        double normalLength = Math.sqrt(px * px + py * py + pz * pz);

        if (normalLength < 1.0E-5) {
            // The segment points straight at the camera; use an axis aligned fallback.
            px = -dz;
            py = 0.0;
            pz = dx;
            normalLength = Math.sqrt(px * px + pz * pz);

            if (normalLength < 1.0E-5) {
                px = 0.0;
                py = dz;
                pz = -dy;
                normalLength = Math.sqrt(py * py + pz * pz);

                if (normalLength < 1.0E-5) return;
            }
        }

        // Half the configured pixel width, expressed in world units at this distance.
        double half = line.halfWidth(distance) / normalLength;
        px *= half;
        py *= half;
        pz *= half;

        mesh.ensure(4, 6);

        int a1 = mesh.vertex(ax - px, ay - py, az - pz, colorA);
        int a2 = mesh.vertex(bx - px, by - py, bz - pz, colorB);
        int a3 = mesh.vertex(bx + px, by + py, bz + pz, colorB);
        int a4 = mesh.vertex(ax + px, ay + py, az + pz, colorA);

        mesh.quad(a1, a2, a3, a4);
    }
}

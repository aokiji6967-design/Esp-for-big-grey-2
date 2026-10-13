package dev.esp.logic;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Tests whether a target can be seen from the camera. */
public final class Occlusion {
    private Occlusion() {}

    public static boolean isVisible(World world, Entity viewer, Vec3d from, Vec3d to) {
        if (viewer == null) return true;

        BlockHitResult hit = world.raycast(new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            viewer
        ));

        return hit.getType() == HitResult.Type.MISS;
    }
}

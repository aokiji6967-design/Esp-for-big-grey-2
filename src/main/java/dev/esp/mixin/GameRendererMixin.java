package dev.esp.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.esp.EspClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ESP overlay inside the world render pass, once per frame.
 *
 * <p>The injection point is just before the hand is rendered, so the overlay lands on top of terrain
 * and entities. Vanilla has already placed its own camera transform on the model view stack at that
 * point, so the world matrix is pushed here and popped again afterwards, leaving the rest of the
 * frame untouched.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
            args = {"ldc=hand"}
        )
    )
    private void esp$renderWorld(
        CallbackInfo info,
        @Local(ordinal = 0) Matrix4f projection,
        @Local(ordinal = 1) Matrix4f position,
        @Local(ordinal = 0) float tickDelta
    ) {
        EspClient esp = EspClient.orNull();
        if (esp == null) return;

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix().mul(position);

        try {
            esp.renderWorld(projection, tickDelta);
        } finally {
            stack.popMatrix();
        }
    }
}

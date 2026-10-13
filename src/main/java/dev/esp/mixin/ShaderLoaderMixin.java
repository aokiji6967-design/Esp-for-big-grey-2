package dev.esp.mixin;

import dev.esp.render.EspPipelines;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compiles the ESP pipelines at the end of every shader reload.
 *
 * <p>Vanilla clears its compiled pipeline cache on reload and has no way to find the shaders of a
 * mod, so the pipeline has to be recompiled here with a source provider that can read this mod's
 * assets. Running at {@code TAIL} means the resource manager is fully populated and vanilla's own
 * pipelines are in place, so ESP shader errors surface at load time rather than mid frame.
 */
@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderMixin {
    @Inject(
        method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void esp$precompilePipelines(CallbackInfo info) {
        EspPipelines.precompile();
    }
}

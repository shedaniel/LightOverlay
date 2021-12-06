package me.shedaniel.lightoverlay.forge.mixin;

import me.shedaniel.lightoverlay.forge.LightOverlayImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "setupRender", at = @At("HEAD"))
    private void setupTerrain(Camera camera, Frustum frustum, boolean bl, boolean bl2, CallbackInfo ci) {
        LightOverlayImpl.renderer.frustum = frustum;
    }
}

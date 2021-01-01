package me.shedaniel.lightoverlay.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.lightoverlay.forge.LightOverlayImpl;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void render(PoseStack arg, MultiBufferSource.BufferSource arg2, double d, double e, double f, CallbackInfo ci) {
        LightOverlayImpl.renderWorldLast();
    }
}

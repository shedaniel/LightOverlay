package me.shedaniel.lightoverlay.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.lightoverlay.common.LightOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.function.Consumer;

public class LightOverlayImpl {
    public static Consumer<PoseStack> debugRenderer = poses -> {};
    
    public static void init() {
        LightOverlay.register();
        WorldRenderEvents.AFTER_SETUP.register(context -> LightOverlay.renderer.frustum = context.frustum());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> debugRenderer.accept(context.matrixStack()));
    }
    
    public static boolean isFrustumVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return FrustumHelper.isVisible(frustum, minX, minY, minZ, maxX, maxY, maxZ);
    }
}

package me.shedaniel.lightoverlay.fabric;

import me.shedaniel.lightoverlay.common.LightOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.culling.Frustum;

public class LightOverlayImpl {
    public static Runnable debugRenderer = () -> {};
    
    public static void init() {
        LightOverlay.register();
        WorldRenderEvents.AFTER_SETUP.register(context -> LightOverlay.frustum = context.frustum());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> debugRenderer.run());
    }
    
    public static boolean isFrustumVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return FrustumHelper.isVisible(frustum, minX, minY, minZ, maxX, maxY, maxZ);
    }
}

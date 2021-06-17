package me.shedaniel.lightoverlay.fabric;

import me.shedaniel.lightoverlay.common.LightOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class LightOverlayImpl {
    public static void init() {
        LightOverlay.register();
        LightOverlay.renderer.frustumAccess = FrustumHelper::isVisible;
        WorldRenderEvents.AFTER_SETUP.register(context -> LightOverlay.renderer.frustum = context.frustum());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> LightOverlay.renderer.accept(context.matrixStack()));
    }
}

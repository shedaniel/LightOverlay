package me.shedaniel.lightoverlay.fabric;

import me.shedaniel.cloth.api.client.events.v0.ClothClientHooks;
import me.shedaniel.lightoverlay.common.AbstractPlatform;
import me.shedaniel.lightoverlay.common.LightOverlayCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.culling.Frustum;

import java.nio.file.Path;

public class LightOverlay implements ClientModInitializer, AbstractPlatform {
    @Override
    public void onInitializeClient() {
        LightOverlayCore.register(this);
    }
    
    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
    
    @Override
    public void registerClientTick(Runnable runnable) {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> runnable.run());
    }
    
    @Override
    public void registerDebugRenderer(Runnable runnable) {
        ClothClientHooks.DEBUG_RENDER_PRE.register(runnable);
    }
    
    @Override
    public KeyMapping registerKeyBinding(KeyMapping keyMapping) {
        return KeyBindingHelper.registerKeyBinding(keyMapping);
    }
    
    @Override
    public boolean isFrustumVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return FrustumHelper.isVisible(frustum, minX, minY, minZ, maxX, maxY, maxZ);
    }
}

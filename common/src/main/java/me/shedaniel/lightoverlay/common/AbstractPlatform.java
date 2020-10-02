package me.shedaniel.lightoverlay.common;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.culling.Frustum;

import java.nio.file.Path;

public interface AbstractPlatform {
    Path getConfigDir();
    
    void registerClientTick(Runnable runnable);
    
    void registerDebugRenderer(Runnable runnable);
    
    KeyMapping registerKeyBinding(KeyMapping keyMapping);
    
    boolean isFrustumVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}

package me.shedaniel.lightoverlay.common;

import net.minecraft.client.renderer.culling.Frustum;

public interface FrustumHelperAccess {
    boolean isVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}

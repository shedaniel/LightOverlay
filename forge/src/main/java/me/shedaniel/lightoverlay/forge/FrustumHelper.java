package me.shedaniel.lightoverlay.forge;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.util.math.vector.Vector4f;

public class FrustumHelper {
    public static boolean isVisible(ClippingHelper frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float x1 = (float) (minX - frustum.cameraX);
        float y1 = (float) (minY - frustum.cameraY);
        float z1 = (float) (minZ - frustum.cameraZ);
        float x2 = (float) (maxX - frustum.cameraX);
        float y2 = (float) (maxY - frustum.cameraY);
        float z2 = (float) (maxZ - frustum.cameraZ);
        return isAnyCornerVisible(frustum, x1, y1, z1, x2, y2, z2);
    }
    
    private static boolean isAnyCornerVisible(ClippingHelper frustum, float x1, float y1, float z1, float x2, float y2, float z2) {
        Vector4f[] homogeneousCoordinates = frustum.frustum;
        for (Vector4f vector4f : homogeneousCoordinates) {
            if (dotProduct(vector4f, x1, y1, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y1, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y2, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y2, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y1, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y1, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y2, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y2, z2, 1.0F) <= 0.0F) {
                return false;
            }
        }
        
        return true;
    }
    
    private static float dotProduct(Vector4f self, float x, float y, float z, float w) {
        return self.getX() * x + self.getY() * y + self.getZ() * z + self.getW() * w;
    }
}

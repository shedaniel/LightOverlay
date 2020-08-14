package me.shedaniel.lightoverlay.fabric;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.Vector4f;

public class FrustumHelper {
    public static boolean isVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float x1 = (float) (minX - frustum.x);
        float y1 = (float) (minY - frustum.y);
        float z1 = (float) (minZ - frustum.z);
        float x2 = (float) (maxX - frustum.x);
        float y2 = (float) (maxY - frustum.y);
        float z2 = (float) (maxZ - frustum.z);
        return isAnyCornerVisible(frustum, x1, y1, z1, x2, y2, z2);
    }
    
    private static boolean isAnyCornerVisible(Frustum frustum, float x1, float y1, float z1, float x2, float y2, float z2) {
        Vector4f[] homogeneousCoordinates = frustum.homogeneousCoordinates;
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

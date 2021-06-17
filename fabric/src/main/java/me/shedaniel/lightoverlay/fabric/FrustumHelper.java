package me.shedaniel.lightoverlay.fabric;

import com.mojang.math.Vector4f;
import net.minecraft.client.renderer.culling.Frustum;

public class FrustumHelper {
    public static boolean isVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        var x1 = (float) (minX - frustum.camX);
        var y1 = (float) (minY - frustum.camY);
        var z1 = (float) (minZ - frustum.camZ);
        var x2 = (float) (maxX - frustum.camX);
        var y2 = (float) (maxY - frustum.camY);
        var z2 = (float) (maxZ - frustum.camZ);
        return isAnyCornerVisible(frustum, x1, y1, z1, x2, y2, z2);
    }
    
    private static boolean isAnyCornerVisible(Frustum frustum, float x1, float y1, float z1, float x2, float y2, float z2) {
        var homogeneousCoordinates = frustum.frustumData;
        for (var vector4f : homogeneousCoordinates) {
            if (dotProduct(vector4f, x1, y1, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y1, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y2, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y2, z1, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y1, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y1, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x1, y2, z2, 1.0F) <= 0.0F && dotProduct(vector4f, x2, y2, z2, 1.0F) <= 0.0F) {
                return false;
            }
        }
        
        return true;
    }
    
    private static float dotProduct(Vector4f self, float x, float y, float z, float w) {
        return self.x() * x + self.y() * y + self.z() * z + self.w() * w;
    }
}

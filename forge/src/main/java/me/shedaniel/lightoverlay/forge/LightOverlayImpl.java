package me.shedaniel.lightoverlay.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.lightoverlay.common.LightOverlay;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.function.Consumer;

public class LightOverlayImpl {
    public static Consumer<PoseStack> debugRenderer = poses -> {};
    
    public static void register() {
        LightOverlay.register();
        
        try {
            //noinspection Convert2MethodRef
            DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> LightOverlayCloth.register());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isFrustumVisible(Frustum clippingHelper, double v, double v1, double v2, double v3, double v4, double v5) {
        return FrustumHelper.isVisible(clippingHelper, v, v1, v2, v3, v4, v5);
    }
    
    public static void renderWorldLast(PoseStack stack) {
        debugRenderer.accept(stack);
    }
}

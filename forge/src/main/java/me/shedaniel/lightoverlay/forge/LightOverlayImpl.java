package me.shedaniel.lightoverlay.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.lightoverlay.common.LightOverlay;
import me.shedaniel.lightoverlay.common.LightOverlayRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.function.Consumer;

public class LightOverlayImpl {
    public static LightOverlayRenderer renderer = LightOverlay.renderer;
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
    
    public static void renderWorldLast(PoseStack stack) {
        debugRenderer.accept(stack);
    }
}

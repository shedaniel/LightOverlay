package me.shedaniel.lightoverlay.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import me.shedaniel.lightoverlay.common.LightOverlay;
import me.shedaniel.lightoverlay.common.LightOverlayRenderer;

import java.util.function.Consumer;

public class LightOverlayImpl {
    public static LightOverlayRenderer renderer = LightOverlay.renderer;
    public static Consumer<PoseStack> debugRenderer = poses -> {};
    
    public static void register() {
        LightOverlay.register();
        
        try {
            //noinspection Convert2MethodRef
            EnvExecutor.runInEnv(Env.CLIENT, () -> () -> LightOverlayCloth.register());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void renderWorldLast(PoseStack stack) {
        debugRenderer.accept(stack);
    }
}

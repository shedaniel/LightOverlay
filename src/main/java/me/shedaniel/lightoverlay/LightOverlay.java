package me.shedaniel.lightoverlay;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod("lightoverlay-forge")
public class LightOverlay {
    
    public LightOverlay() {
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class.forName("me.shedaniel.lightoverlay.LightOverlayClient").getDeclaredMethod("register").invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
}

package me.shedaniel.lightoverlay;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("lightoverlay-forge")
public class LightOverlay {
    
    public LightOverlay() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class.forName("me.shedaniel.lightoverlay.LightOverlayClient").getDeclaredMethod("register").invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
}

package me.shedaniel.lightoverlay;

import net.minecraft.network.IPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod("lightoverlay-forge")
public class LightOverlay {
    
    public LightOverlay() {
        //noinspection Convert2MethodRef
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> LightOverlayClient.register());
    }
    
    public static void processPacket(IPacket<?> packet) {
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> LightOverlayClient.processPacket(packet));
    }
    
}

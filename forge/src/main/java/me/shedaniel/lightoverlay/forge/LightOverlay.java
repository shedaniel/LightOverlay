package me.shedaniel.lightoverlay.forge;

import net.minecraft.network.IPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod("lightoverlay-forge")
public class LightOverlay {
    public LightOverlay() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> LightOverlayClient::register);
    }
    
    public static void processPacket(IPacket<?> packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightOverlayClient.processPacket(packet));
    }
}

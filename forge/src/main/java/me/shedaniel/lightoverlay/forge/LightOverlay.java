package me.shedaniel.lightoverlay.forge;

import net.minecraft.network.IPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;

@Mod("lightoverlay-forge")
public class LightOverlay {
    public LightOverlay() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> LightOverlayClient::register);
    }
    
    public static void processPacket(IPacket<?> packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightOverlayClient.processPacket(packet));
    }
}

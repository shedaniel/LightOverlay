package me.shedaniel.lightoverlay.forge;

import me.shedaniel.lightoverlay.common.AbstractPlatform;
import me.shedaniel.lightoverlay.common.LightOverlayCore;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SChunkDataPacket;
import net.minecraft.network.play.server.SMultiBlockChangePacket;
import net.minecraft.network.play.server.SUpdateLightPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class LightOverlayClient {
    private static Runnable debugRenderer = () -> {};
    
    public static void register() {
        LightOverlayCore.register(new AbstractPlatform() {
            @Override
            public Path getConfigDir() {
                return FMLPaths.CONFIGDIR.get();
            }
            
            @Override
            public void registerClientTick(Runnable runnable) {
                MinecraftForge.EVENT_BUS.<TickEvent.ClientTickEvent>addListener(event -> runnable.run());
            }
            
            @Override
            public void registerDebugRenderer(Runnable runnable) {
                debugRenderer = runnable;
            }
            
            @Override
            public KeyBinding registerKeyBinding(KeyBinding keyBinding) {
                ClientRegistry.registerKeyBinding(keyBinding);
                return keyBinding;
            }
            
            @Override
            public boolean isFrustumVisible(ClippingHelper clippingHelper, double v, double v1, double v2, double v3, double v4, double v5) {
                return FrustumHelper.isVisible(clippingHelper, v, v1, v2, v3, v4, v5);
            }
        });
        
        try {
            //noinspection Convert2MethodRef
            DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> LightOverlayCloth.register());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void renderWorldLast() {
        debugRenderer.run();
    }
    
    public static void processPacket(IPacket<?> packet) {
        if (packet instanceof SChangeBlockPacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((SChangeBlockPacket) packet).getPos()));
        } else if (packet instanceof SChunkDataPacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((SChunkDataPacket) packet).getX(), ((SChunkDataPacket) packet).getZ()));
        } else if (packet instanceof SMultiBlockChangePacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((SMultiBlockChangePacket) packet).sectionPos.getX(), ((SMultiBlockChangePacket) packet).sectionPos.getZ()));
        } else if (packet instanceof SUpdateLightPacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((SUpdateLightPacket) packet).getX(), ((SUpdateLightPacket) packet).getZ()));
        }
    }
}

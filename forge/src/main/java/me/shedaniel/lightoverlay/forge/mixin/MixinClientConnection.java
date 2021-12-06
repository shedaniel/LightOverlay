package me.shedaniel.lightoverlay.forge.mixin;

import me.shedaniel.lightoverlay.common.CubicChunkPos;
import me.shedaniel.lightoverlay.common.LightOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinClientConnection {
    @Inject(method = "genericsFtw", at = @At("HEAD"))
    private static void handlePacket(Packet packet, PacketListener listener, CallbackInfo ci) {
//        
//    }
//    public static void handlePacket(Packet packet, PacketListener listener) {
        try {
            if (!(listener instanceof ClientPacketListener packetListener)) return;
            var level = packetListener.getLevel();
            if (level == null) return;
            if (packet instanceof ClientboundBlockUpdatePacket p) {
                LightOverlay.queueChunkAndNear(new CubicChunkPos(p.getPos()));
            } else if (packet instanceof ClientboundSetChunkCacheCenterPacket p) {
                var height = Mth.ceil(level.getHeight() / 32.0);
                var start = Math.floorDiv(level.getMinBuildHeight(), 32);
                for (int y = start; y < start + height; y++) {
                    LightOverlay.queueChunkAndNear(new CubicChunkPos(p.getX(), y, p.getZ()));
                }
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
                LightOverlay.queueChunkAndNear(new CubicChunkPos(p.sectionPos.getX(), p.sectionPos.getY() >> 1, p.sectionPos.getZ()));
            } else if (packet instanceof ClientboundLightUpdatePacket p) {
                var height = Mth.ceil(level.getHeight() / 32.0);
                var start = Math.floorDiv(level.getMinBuildHeight(), 32);
                for (int y = start; y < start + height; y++) {
                    LightOverlay.queueChunk(new CubicChunkPos(p.getX(), y, p.getZ()));
                }
            }
        } catch (Throwable throwable) {
            new RuntimeException("Light Overlay failed to process packet", throwable).printStackTrace();
        }
    }
}

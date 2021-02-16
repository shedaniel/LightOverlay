package me.shedaniel.lightoverlay.forge.mixin;

import me.shedaniel.lightoverlay.common.CubicChunkPos;
import me.shedaniel.lightoverlay.common.LightOverlay;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinClientConnection {
    @Inject(method = "genericsFtw", at = @At("HEAD"))
    private static void handlePacket(Packet packet, PacketListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundBlockUpdatePacket) {
            LightOverlay.queueChunkAndNear(new CubicChunkPos(((ClientboundBlockUpdatePacket) packet).getPos()));
        } else if (packet instanceof ClientboundSetChunkCacheCenterPacket) {
            for (int y = 0; y <= 15; y++) {
                LightOverlay.queueChunkAndNear(new CubicChunkPos(((ClientboundSetChunkCacheCenterPacket) packet).getX(), y, ((ClientboundSetChunkCacheCenterPacket) packet).getZ()));
            }
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket) {
            for (int y = 0; y <= 15; y++) {
                LightOverlay.queueChunkAndNear(new CubicChunkPos(((ClientboundSectionBlocksUpdatePacket) packet).sectionPos.getX(), y, ((ClientboundSectionBlocksUpdatePacket) packet).sectionPos.getZ()));
            }
        } else if (packet instanceof ClientboundLightUpdatePacket) {
            for (int y = 0; y <= 15; y++) {
                LightOverlay.queueChunk(new CubicChunkPos(((ClientboundLightUpdatePacket) packet).getX(), y, ((ClientboundLightUpdatePacket) packet).getZ()));
            }
        }
    }
}

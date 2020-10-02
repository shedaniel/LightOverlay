package me.shedaniel.lightoverlay.fabric.mixin;

import me.shedaniel.lightoverlay.common.LightOverlayCore;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinClientConnection {
    @Inject(method = "genericsFtw", at = @At("HEAD"))
    private static void handlePacket(Packet packet, PacketListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundBlockUpdatePacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((ClientboundBlockUpdatePacket) packet).getPos()));
        } else if (packet instanceof ClientboundSetChunkCacheCenterPacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((ClientboundSetChunkCacheCenterPacket) packet).getX(), ((ClientboundSetChunkCacheCenterPacket) packet).getZ()));
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket) {
            LightOverlayCore.queueChunkAndNear(new ChunkPos(((ClientboundSectionBlocksUpdatePacket) packet).sectionPos.getX(), ((ClientboundSectionBlocksUpdatePacket) packet).sectionPos.getZ()));
        } else if (packet instanceof ClientboundLightUpdatePacket) {
            LightOverlayCore.queueChunk(new ChunkPos(((ClientboundLightUpdatePacket) packet).getX(), ((ClientboundLightUpdatePacket) packet).getZ()));
        }
    }
}

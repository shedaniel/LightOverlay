package me.shedaniel.lightoverlay.fabric.mixin;

import me.shedaniel.lightoverlay.fabric.LightOverlay;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Inject(method = "handlePacket", at = @At("HEAD"))
    private static void handlePacket(Packet packet, PacketListener listener, CallbackInfo ci) {
        if (packet instanceof BlockUpdateS2CPacket) {
            LightOverlay.queueChunkAndNear(new ChunkPos(((BlockUpdateS2CPacket) packet).getPos()));
        } else if (packet instanceof ChunkDataS2CPacket) {
            LightOverlay.queueChunkAndNear(new ChunkPos(((ChunkDataS2CPacket) packet).getX(), ((ChunkDataS2CPacket) packet).getZ()));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket) {
            LightOverlay.queueChunkAndNear(new ChunkPos(((ChunkDeltaUpdateS2CPacket) packet).chunkPos.x, ((ChunkDeltaUpdateS2CPacket) packet).chunkPos.z));
        } else if (packet instanceof LightUpdateS2CPacket) {
            LightOverlay.queueChunk(new ChunkPos(((LightUpdateS2CPacket) packet).getChunkX(), ((LightUpdateS2CPacket) packet).getChunkZ()));
        }
    }
}

package me.shedaniel.lightoverlay.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import me.shedaniel.lightoverlay.CrossType;
import me.shedaniel.lightoverlay.LightOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

import static me.shedaniel.lightoverlay.LightOverlay.getReach;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    
    @Shadow @Final private MinecraftClient client;
    
    @Inject(method = "renderCenter", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/debug/DebugRenderer;shouldRender()Z", ordinal = 0))
    public void renderLightOverlay(float delta, long long_1, CallbackInfo callbackInfo) {
        if (LightOverlay.isEnabled()) {
            PlayerEntity playerEntity = client.player;
            World world = client.world;
            GlStateManager.disableTexture();
            GlStateManager.disableBlend();
            BlockPos playerPos = new BlockPos(playerEntity.x, playerEntity.y, playerEntity.z);
            BlockPos.iterateBoxPositions(playerPos.add(-getReach(), -getReach(), -getReach()), playerPos.add(getReach(), getReach(), getReach())).forEach(pos -> {
                if (world.getBiome(pos).getMaxSpawnLimit() > 0) {
                    CrossType type = LightOverlay.getCrossType(pos, world, playerEntity);
                    if (type != CrossType.NONE) {
                        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
                        Color color = type == CrossType.RED ? Color.RED : Color.YELLOW;
                        LightOverlay.renderCross(pos, color, (double) delta, playerEntity);
                    }
                }
            });
            GlStateManager.enableBlend();
            GlStateManager.enableTexture();
        }
    }
    
}

package me.shedaniel.lightoverlay.common;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.function.Consumer;

public class LightOverlayRenderer implements Consumer<PoseStack> {
    private final Minecraft minecraft = Minecraft.getInstance();
    public Frustum frustum;
    public LightOverlayTicker ticker;
    public FrustumHelperAccess frustumAccess;
    
    public LightOverlayRenderer(LightOverlayTicker ticker) {
        this.ticker = ticker;
    }
    
    @Override
    public void accept(PoseStack poses) {
        if (LightOverlay.enabled) {
            ChunkData.render(poses);
        }
    }
    
    public boolean isFrustumVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        try {
            return frustum == null || frustumAccess.isVisible(frustum, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}

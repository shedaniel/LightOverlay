package me.shedaniel.lightoverlay.common;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;

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
            var playerEntity = minecraft.player;
            var playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
            var playerPosX = playerPos.getX() >> 4;
            var playerPosY = playerPos.getY() >> 5;
            var playerPosZ = playerPos.getZ() >> 4;
            var collisionContext = CollisionContext.of(playerEntity);
            var camera = minecraft.gameRenderer.getMainCamera();
            var chunkRange = LightOverlay.getChunkRange();
            
            if (LightOverlay.showNumber) {
                renderLevels(new PoseStack(), camera, playerPos, playerPosX, playerPosY, playerPosZ, chunkRange, collisionContext);
            } else {
                renderCrosses(poses, camera, playerPos, playerPosX, playerPosY, playerPosZ, chunkRange, collisionContext);
            }
        }
    }
    
    private void renderLevels(PoseStack poses, Camera camera, BlockPos playerPos, int playerPosX, int playerPosY, int playerPosZ, int chunkRange, CollisionContext collisionContext) {
        RenderSystem.enableTexture();
        RenderSystem.depthMask(true);
        var mutable = new BlockPos.MutableBlockPos();
        var downMutable = new BlockPos.MutableBlockPos();
        var source = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        for (var entry : ticker.CHUNK_MAP.entrySet()) {
            var chunkPos = entry.getKey();
            if (LightOverlay.caching && (Mth.abs(chunkPos.x - playerPosX) > chunkRange || Mth.abs(chunkPos.y - playerPosY) > Math.max(1, chunkRange >> 1) || Mth.abs(chunkPos.z - playerPosZ) > chunkRange)) {
                continue;
            }
            for (var objectEntry : entry.getValue().long2ByteEntrySet()) {
                mutable.set(objectEntry.getLongKey());
                if (mutable.closerThan(playerPos, LightOverlay.reach)) {
                    if (isFrustumVisible(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                        downMutable.set(mutable.getX(), mutable.getY() - 1, mutable.getZ());
                        renderLevel(poses, source, camera, minecraft.level, mutable, downMutable, objectEntry.getByteValue(), collisionContext);
                    }
                }
            }
        }
        RenderSystem.enableDepthTest();
        source.endBatch();
    }
    
    public void renderLevel(PoseStack poses, MultiBufferSource.BufferSource source, Camera camera, Level world, BlockPos pos, BlockPos down, byte level, CollisionContext collisionContext) {
        var text = String.valueOf(level);
        var font = minecraft.font;
        var cameraX = camera.getPosition().x;
        var cameraY = camera.getPosition().y;
        var upperOutlineShape = world.getBlockState(down).getShape(world, down, collisionContext);
        if (!upperOutlineShape.isEmpty())
            cameraY += 1 - upperOutlineShape.max(Direction.Axis.Y);
        var cameraZ = camera.getPosition().z;
        poses.pushPose();
        poses.translate(pos.getX() + 0.5 - cameraX, pos.getY() - cameraY + 0.005, pos.getZ() + 0.5 - cameraZ);
        poses.mulPose(Vector3f.XP.rotationDegrees(90));
//        poses.glNormal3f(0.0F, 1.0F, 0.0F);
        var size = 0.07F;
        poses.scale(-size, -size, size);
        var float_3 = (float) (-font.width(text)) / 2.0F + 0.4f;
        font.drawInBatch(text, float_3, -3.5f, level > LightOverlay.higherCrossLevel ? 0xff042404 : (LightOverlay.lowerCrossLevel >= 0 && level > LightOverlay.lowerCrossLevel ? 0xff0066ff : 0xff731111), false, poses.last().pose(), source, false, 0, 15728880);
        poses.popPose();
    }
    
    private void renderCrosses(PoseStack poses, Camera camera, BlockPos playerPos, int playerPosX, int playerPosY, int playerPosZ, int chunkRange, CollisionContext collisionContext) {
        RenderSystem.enableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(LightOverlay.lineWidth);
        var tesselator = Tesselator.getInstance();
        var builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        var mutable = new BlockPos.MutableBlockPos();
        
        for (var entry : ticker.CHUNK_MAP.entrySet()) {
            var chunkPos = entry.getKey();
            if (LightOverlay.caching && (Mth.abs(chunkPos.x - playerPosX) > chunkRange || Mth.abs(chunkPos.y - playerPosY) > Math.max(1, chunkRange >> 1) || Mth.abs(chunkPos.z - playerPosZ) > chunkRange)) {
                continue;
            }
            
            for (var objectEntry : entry.getValue().long2ByteEntrySet()) {
                var crossType = objectEntry.getByteValue();
                mutable.set(objectEntry.getLongKey());
                if (mutable.closerThan(playerPos, LightOverlay.reach)) {
                    if (isFrustumVisible(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                        var color = switch (crossType) {
                            case LightOverlay.CROSS_RED -> LightOverlay.redColor;
                            case LightOverlay.CROSS_YELLOW -> LightOverlay.yellowColor;
                            default -> LightOverlay.secondaryColor;
                        };
                        renderCross(poses.last().pose(), builder, camera, minecraft.level, mutable, color, collisionContext);
                    }
                }
            }
        }
        
        tesselator.end();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
    }
    
    public void renderCross(Matrix4f pose, BufferBuilder builder, Camera camera, Level world, BlockPos pos, int color, CollisionContext collisionContext) {
        var cameraX = camera.getPosition().x;
        var cameraY = camera.getPosition().y - .005D;
        double blockOffset = 0;
        var upperOutlineShape = world.getBlockState(pos).getShape(world, pos, collisionContext);
        if (!upperOutlineShape.isEmpty()) {
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        }
        var cameraZ = camera.getPosition().z;
        
        var red = (color >> 16) & 255;
        var green = (color >> 8) & 255;
        var blue = color & 255;
        var x = pos.getX() - cameraX;
        var y = pos.getY() - cameraY + blockOffset;
        var z = pos.getZ() - cameraZ;
        builder.vertex(x + .01, y, z + .01).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .99, y, z + .99).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .99, y, z + .01).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .01, y, z + .99).color(red, green, blue, 255).endVertex();
    }
    
    public boolean isFrustumVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        try {
            return frustum == null || frustumAccess.isVisible(frustum, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}

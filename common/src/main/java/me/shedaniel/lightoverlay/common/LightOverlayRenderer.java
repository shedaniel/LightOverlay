package me.shedaniel.lightoverlay.common;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;

public class LightOverlayRenderer implements Consumer<PoseStack> {
    private static final Function<Double, RenderType.CompositeRenderType> LINE = Util.memoize(
            double_ -> RenderType.create(
                    "light_overlay_lines",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.DEBUG_LINES,
                    256,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(double_)))
                            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .createCompositeState(false)
            )
    );
    
    private final Minecraft minecraft = Minecraft.getInstance();
    public Frustum frustum;
    public LightOverlayTicker ticker;
    
    public LightOverlayRenderer(LightOverlayTicker ticker) {
        this.ticker = ticker;
    }
    
    @Override
    public void accept(PoseStack poses) {
        if (LightOverlay.enabled) {
            LocalPlayer playerEntity = minecraft.player;
            BlockPos playerPos = new BlockPos(playerEntity.getBlockX(), playerEntity.getBlockY(), playerEntity.getBlockZ());
            int playerPosX = playerPos.getX() >> 4;
            int playerPosY = playerPos.getY() >> 5;
            int playerPosZ = playerPos.getZ() >> 4;
            CollisionContext collisionContext = CollisionContext.of(playerEntity);
            Camera camera = minecraft.gameRenderer.getMainCamera();
            int chunkRange = LightOverlay.getChunkRange();
            
            if (LightOverlay.showNumber) {
                renderLevels(poses, camera, playerPos, playerPosX, playerPosY, playerPosZ, chunkRange, collisionContext);
            } else {
                renderCrosses(poses, camera, playerPos, playerPosX, playerPosY, playerPosZ, chunkRange, collisionContext);
            }
            Minecraft.getInstance().renderBuffers().bufferSource().endLastBatch();
        }
    }
    
    private void renderLevels(PoseStack poses, Camera camera, BlockPos playerPos, int playerPosX, int playerPosY, int playerPosZ, int chunkRange, CollisionContext collisionContext) {
        RenderSystem.depthMask(true);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos downMutable = new BlockPos.MutableBlockPos();
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Map.Entry<CubicChunkPos, Long2ByteMap> entry : ticker.CHUNK_MAP.entrySet()) {
            CubicChunkPos chunkPos = entry.getKey();
            if (LightOverlay.caching && (Mth.abs(chunkPos.x - playerPosX) > chunkRange || Mth.abs(chunkPos.y - playerPosY) > Math.max(1, chunkRange >> 1) || Mth.abs(chunkPos.z - playerPosZ) > chunkRange)) {
                continue;
            }
            for (Long2ByteMap.Entry objectEntry : entry.getValue().long2ByteEntrySet()) {
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
    }
    
    public void renderLevel(PoseStack poses, MultiBufferSource.BufferSource source, Camera camera, Level world, BlockPos pos, BlockPos down, byte level, CollisionContext collisionContext) {
        String text = String.valueOf(level);
        Font font = minecraft.font;
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getShape(world, down, collisionContext);
        if (!upperOutlineShape.isEmpty())
            cameraY += 1 - upperOutlineShape.max(Direction.Axis.Y);
        double cameraZ = camera.getPosition().z;
        poses.pushPose();
        poses.translate(pos.getX() + 0.5 - cameraX, pos.getY() - cameraY + 0.005, pos.getZ() + 0.5 - cameraZ);
        poses.mulPose(new Quaternionf().fromAxisAngleDeg(1, 0, 0, 90));
//        poses.glNormal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        poses.scale(-size, -size, size);
        float float_3 = (float) (-font.width(text)) / 2.0F + 0.4f;
        font.drawInBatch(text, float_3, -3.5f, level > LightOverlay.higherCrossLevel ? 0xff042404 : (LightOverlay.lowerCrossLevel >= 0 && level > LightOverlay.lowerCrossLevel ? 0xff0066ff : 0xff731111),
                false, poses.last().pose(), source, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        poses.popPose();
    }
    
    private void renderCrosses(PoseStack poses, Camera camera, BlockPos playerPos, int playerPosX, int playerPosY, int playerPosZ, int chunkRange, CollisionContext collisionContext) {
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = source.getBuffer(LINE.apply((double) LightOverlay.lineWidth));
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        
        for (Map.Entry<CubicChunkPos, Long2ByteMap> entry : ticker.CHUNK_MAP.entrySet()) {
            CubicChunkPos chunkPos = entry.getKey();
            if (LightOverlay.caching && (Mth.abs(chunkPos.x - playerPosX) > chunkRange || Mth.abs(chunkPos.y - playerPosY) > Math.max(1, chunkRange >> 1) || Mth.abs(chunkPos.z - playerPosZ) > chunkRange)) {
                continue;
            }
            
            for (Long2ByteMap.Entry objectEntry : entry.getValue().long2ByteEntrySet()) {
                byte crossType = objectEntry.getByteValue();
                mutable.set(objectEntry.getLongKey());
                if (mutable.closerThan(playerPos, LightOverlay.reach)) {
                    if (isFrustumVisible(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                        int color = switch (crossType) {
                            case LightOverlay.CROSS_RED -> LightOverlay.redColor;
                            case LightOverlay.CROSS_YELLOW -> LightOverlay.yellowColor;
                            default -> LightOverlay.secondaryColor;
                        };
                        renderCross(poses.last().pose(), buffer, camera, minecraft.level, mutable, color, collisionContext);
                    }
                }
            }
        }
    }
    
    public void renderCross(Matrix4f pose, VertexConsumer builder, Camera camera, Level world, BlockPos pos, int color, CollisionContext collisionContext) {
        float cameraX = (float) camera.getPosition().x;
        float cameraY = (float) camera.getPosition().y - .005f;
        float blockOffset = 0;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, collisionContext);
        if (!upperOutlineShape.isEmpty()) {
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        }
        float cameraZ = (float) camera.getPosition().z;
        
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        float x = pos.getX() - cameraX;
        float y = pos.getY() - cameraY + blockOffset;
        float z = pos.getZ() - cameraZ;
        builder.vertex(pose, x + .01f, y, z + .01f).color(red, green, blue, 255).endVertex();
        builder.vertex(pose, x + .99f, y, z + .99f).color(red, green, blue, 255).endVertex();
        builder.vertex(pose, x + .99f, y, z + .01f).color(red, green, blue, 255).endVertex();
        builder.vertex(pose, x + .01f, y, z + .99f).color(red, green, blue, 255).endVertex();
    }
    
    public boolean isFrustumVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }
}

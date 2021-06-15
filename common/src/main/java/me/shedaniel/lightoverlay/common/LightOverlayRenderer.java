package me.shedaniel.lightoverlay.common;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import dev.architectury.injectables.targets.ArchitecturyTarget;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LightOverlayRenderer implements Consumer<PoseStack> {
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
            BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
            int playerPosX = playerPos.getX() >> 4;
            int playerPosY = playerPos.getY() >> 5;
            int playerPosZ = playerPos.getZ() >> 4;
            CollisionContext collisionContext = CollisionContext.of(playerEntity);
            Camera camera = minecraft.gameRenderer.getMainCamera();
            int chunkRange = LightOverlay.getChunkRange();
            
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
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos downMutable = new BlockPos.MutableBlockPos();
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
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
        source.endBatch();
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
        poses.mulPose(Vector3f.XP.rotationDegrees(90));
//        poses.glNormal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        poses.scale(-size, -size, size);
        float float_3 = (float) (-font.width(text)) / 2.0F + 0.4f;
        font.drawInBatch(text, float_3, -3.5f, level > LightOverlay.higherCrossLevel ? 0xff042404 : (LightOverlay.lowerCrossLevel >= 0 && level > LightOverlay.lowerCrossLevel ? 0xff0066ff : 0xff731111), false, poses.last().pose(), source, false, 0, 15728880);
        poses.popPose();
    }
    
    private void renderCrosses(PoseStack poses, Camera camera, BlockPos playerPos, int playerPosX, int playerPosY, int playerPosZ, int chunkRange, CollisionContext collisionContext) {
        RenderSystem.enableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(LightOverlay.lineWidth);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
    
        System.out.println(ticker.CHUNK_MAP.size());
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
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y - .005D;
        double blockOffset = 0;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, collisionContext);
        if (!upperOutlineShape.isEmpty()) {
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        }
        double cameraZ = camera.getPosition().z;
        
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        double x = pos.getX() - cameraX;
        double y = pos.getY() - cameraY + blockOffset;
        double z = pos.getZ() - cameraZ;
        builder.vertex(x + .01, y, z + .01).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .99, y, z + .99).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .99, y, z + .01).color(red, green, blue, 255).endVertex();
        builder.vertex(x + .01, y, z + .99).color(red, green, blue, 255).endVertex();
    }
    
    private static final Supplier<MethodHandle> IS_FRUSTUM_VISIBLE = Suppliers.memoize(() -> {
        try {
            return MethodHandles.lookup().findStatic(Class.forName("me.shedaniel.lightoverlay." + ArchitecturyTarget.getCurrentTarget() + ".LightOverlayImpl"), "isFrustumVisible",
                    MethodType.methodType(boolean.class, Frustum.class, double.class, double.class, double.class, double.class, double.class, double.class));
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    });
    
    public boolean isFrustumVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        try {
            return frustum == null || (boolean) IS_FRUSTUM_VISIBLE.get().invokeExact(frustum, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}

package me.shedaniel.lightoverlay.common;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChunkData implements Closeable {
    private static final Minecraft minecraft = Minecraft.getInstance();
    @Nullable
    public static ShaderInstance lightoverlayPositionColorShaderWithWidth;
    private final Long2ByteMap data;
    private Iterable<Pair<RenderType, VertexBuffer>> buffers;
    
    public ChunkData() {
        this(new Long2ByteOpenHashMap());
    }
    
    public ChunkData(Long2ByteMap data) {
        this.data = data;
    }
    
    public Long2ByteMap data() {
        return data;
    }
    
    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::_close);
        } else {
            _close();
        }
    }
    
    private void _close() {
        if (buffers != null) {
            for (Pair<RenderType, VertexBuffer> buffer : buffers) {
                buffer.getSecond().close();
            }
            buffers = null;
        }
    }
    
    public void put(long l, byte level) {
        this.data.put(l, level);
    }
    
    public static void render(PoseStack poses) {
        if (!LightOverlay.enabled) return;
        var playerEntity = minecraft.player;
        var playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
        var playerPosX = playerPos.getX() >> 4;
        var playerPosY = playerPos.getY() >> 5;
        var playerPosZ = playerPos.getZ() >> 4;
        var collisionContext = CollisionContext.of(playerEntity);
        var camera = minecraft.gameRenderer.getMainCamera();
        var chunkRange = LightOverlay.getChunkRange();
        var ticker = LightOverlay.ticker;
        
        RenderSystem.enableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        var p = new PoseStack();
        poses.pushPose();
        p.mulPoseMatrix(RenderSystem.getProjectionMatrix());
        poses.translate(-camera.getPosition().x(), -camera.getPosition().y(), -camera.getPosition().z());
        
        for (var entry : ticker.CHUNK_MAP.entrySet()) {
            var chunkPos = entry.getKey();
            var data = entry.getValue();
            if ((Mth.abs(chunkPos.x - playerPosX) > chunkRange || Mth.abs(chunkPos.y - playerPosY) > Math.max(1, chunkRange >> 1) || Mth.abs(chunkPos.z - playerPosZ) > chunkRange)) {
                continue;
            }
            if (!LightOverlay.renderer.isFrustumVisible(chunkPos.getMinBlockX(), chunkPos.getMinBlockY(), chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(), chunkPos.getMaxBlockY(), chunkPos.getMaxBlockZ())) {
                continue;
            }
            var buffers = data.buffer(collisionContext);
            for (Pair<RenderType, VertexBuffer> pair : buffers) {
                RenderType type = pair.getFirst();
                ShaderInstance shader = GameRenderer.getPositionColorShader();
                if (LightOverlay.showNumber) {
                    type.setupRenderState();
                    shader = RenderSystem.getShader();
                } else {
//                    RenderSystem.lineWidth(LightOverlay.lineWidth);
//                    if (shader.LINE_WIDTH != null) {
//                        shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
//                    }
                }
                VertexBuffer buffer = pair.getSecond();
                buffer.drawWithShader(poses.last().pose(), p.last().pose(), shader);
                if (LightOverlay.showNumber) {
                    type.clearRenderState();
                }
            }
            data._close();
        }
        
        poses.popPose();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
        RenderSystem.enableBlend();
    }
    
    private Iterable<Pair<RenderType, VertexBuffer>> buffer(CollisionContext collisionContext) {
        if (this.buffers == null) {
            if (!LightOverlay.showNumber) {
                var buffer = new VertexBuffer();
                var tesselator = Tesselator.getInstance();
                var builder = tesselator.getBuilder();
                this.renderCrosses(builder, collisionContext);
                builder.end();
                buffer.upload(builder);
                this.buffers = Collections.singleton(Pair.of(null, buffer));
            } else {
                var buffers = new ArrayList<Pair<RenderType, VertexBuffer>>();
                var types = new HashMap<RenderType, BufferBuilder>();
                this.renderLevels(renderType -> types.computeIfAbsent(renderType, type -> {
                    BufferBuilder builder = new BufferBuilder(256);
                    builder.begin(type.mode(), type.format());
                    return builder;
                }), collisionContext);
                for (Map.Entry<RenderType, BufferBuilder> entry : types.entrySet()) {
                    var buffer = new VertexBuffer();
                    entry.getValue().end();
                    buffer.upload(entry.getValue());
                    buffers.add(Pair.of(entry.getKey(), buffer));
                }
                this.buffers = buffers;
            }
        }
        
        return this.buffers;
    }
    
    private void renderCrosses(BufferBuilder builder, CollisionContext collisionContext) {
        float lineWidth = LightOverlay.lineWidth * 12 / Mth.sqrt(Minecraft.getInstance().getWindow().getGuiScaledWidth() * Minecraft.getInstance().getWindow().getGuiScaledHeight());
//        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var mutable = new BlockPos.MutableBlockPos();
        
        for (var objectEntry : data().long2ByteEntrySet()) {
            mutable.set(objectEntry.getLongKey());
            var color = switch (objectEntry.getByteValue()) {
                case LightOverlay.CROSS_RED -> LightOverlay.redColor;
                case LightOverlay.CROSS_YELLOW -> LightOverlay.yellowColor;
                default -> LightOverlay.secondaryColor;
            };
            renderCross(builder, minecraft.level, mutable, color, collisionContext, lineWidth);
        }
    }
    
    private void renderLevels(MultiBufferSource source, CollisionContext collisionContext) {
        var mutable = new BlockPos.MutableBlockPos();
        var mutableDown = new BlockPos.MutableBlockPos();
        var poses = new PoseStack();
        
        for (var objectEntry : data().long2ByteEntrySet()) {
            mutable.set(objectEntry.getLongKey());
            var x = mutable.getX();
            var y = mutable.getY();
            var z = mutable.getZ();
            mutableDown.set(x, y - 1, z);
            var level = objectEntry.getByteValue();
            renderLevel(poses, source, minecraft.level, mutable, mutableDown, level, collisionContext);
        }
    }
    
    public void renderCross(BufferBuilder builder, Level level, BlockPos pos, int color, CollisionContext collisionContext, double lineWidth) {
        var blockOffset = .005D;
        var upperOutlineShape = level.getBlockState(pos).getShape(level, pos, collisionContext);
        if (!upperOutlineShape.isEmpty()) {
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        }
        
        var red = (color >> 16) & 255;
        var green = (color >> 8) & 255;
        var blue = color & 255;
        var x = pos.getX();
        var y = pos.getY() + blockOffset;
        var z = pos.getZ();
        line(builder, x, z, x + 1, z + 1, y, lineWidth, lineWidth, red, green, blue, 255);
        line(builder, x + 1, z, x, z + 1, y, lineWidth, lineWidth, red, green, blue, 255);
    }
    
    public void line(VertexConsumer vertexConsumer, double x1, double z1, double x2, double z2, double y, double t1, double t2, int red, int green, int blue, int alpha) {
        float angle = (float) Mth.atan2(z2 - z1, x2 - x1);
        float sinA = Mth.sin(angle);
        float cosA = Mth.cos(angle);
        double t2sina1 = t1 / 2 * sinA;
        double t2cosa1 = t1 / 2 * cosA;
        double t2sina2 = t2 / 2 * sinA;
        double t2cosa2 = t2 / 2 * cosA;
        
        vertexConsumer.vertex(x1 + t2sina1, y, z1 - t2cosa1).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(x2 - t2sina2, y, z2 + t2cosa2).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(x2 + t2sina2, y, z2 - t2cosa2).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(x2 - t2sina2, y, z2 + t2cosa2).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(x1 + t2sina1, y, z1 - t2cosa1).color(red, green, blue, alpha).endVertex();
        vertexConsumer.vertex(x1 - t2sina1, y, z1 + t2cosa1).color(red, green, blue, alpha).endVertex();
    }
    
    public void renderLevel(PoseStack poses, MultiBufferSource source, Level level, BlockPos pos, BlockPos down, byte levelLevel, CollisionContext collisionContext) {
        var text = String.valueOf(levelLevel);
        var font = minecraft.font;
        var cameraY = -0.005;
        var upperOutlineShape = level.getBlockState(down).getShape(level, down, collisionContext);
        if (!upperOutlineShape.isEmpty())
            cameraY += 1 - upperOutlineShape.max(Direction.Axis.Y);
        poses.pushPose();
        poses.translate(pos.getX() + 0.5, pos.getY() - cameraY, pos.getZ() + 0.5);
        poses.mulPose(Vector3f.XP.rotationDegrees(90));
        var size = 0.07F;
        poses.scale(-size, -size, size);
        var x = (float) (-font.width(text)) / 2.0F + 0.4f;
        font.drawInBatch(text, x, -3.5f, levelLevel > LightOverlay.higherCrossLevel ? 0xff042404 : (LightOverlay.lowerCrossLevel >= 0 && levelLevel > LightOverlay.lowerCrossLevel ? 0xff0066ff : 0xff731111), false, poses.last().pose(), source, false, 0, 0xf000f0);
        poses.popPose();
    }
}

package me.shedaniel.lightoverlay;

import com.mojang.blaze3d.platform.GlStateManager;
import me.shedaniel.cloth.hooks.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BoundingBox;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;

import java.awt.*;

public class LightOverlay implements ClientModInitializer {
    
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static final BoundingBox TEST_BOX = new BoundingBox(0.6D / 2D, 0, 0.6D / 2D, 1D - 0.6D / 2D, 1D, 1D - 0.6D / 2D);
    private static FabricKeyBinding enableOverlay;
    private static boolean enabled = false;
    private static int reach = 12;
    
    public static CrossType getCrossType(BlockPos pos, World world, PlayerEntity playerEntity) {
        BlockState blockBelowState = world.getBlockState(pos.down());
        if (blockBelowState.getBlock() == Blocks.BEDROCK || blockBelowState.getBlock() == Blocks.BARRIER)
            return CrossType.NONE;
        if ((!blockBelowState.getMaterial().blocksLight() && blockBelowState.isTranslucent(world, pos.down())) || !SpawnHelper.isClearForSpawn(world, pos, world.getBlockState(pos), world.getFluidState(pos)))
            return CrossType.NONE;
        if (blockBelowState.isAir() || !world.getBlockState(pos).isAir() || !blockBelowState.hasSolidTopSurface(world, pos, playerEntity) || !world.getFluidState(pos.down()).isEmpty())
            return CrossType.NONE;
        if (world.getLightLevel(LightType.BLOCK, pos) >= 8)
            return CrossType.NONE;
        if (world.getLightLevel(LightType.SKY, pos) >= 8)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static void renderCross(BlockPos pos, Color color, PlayerEntity entity) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        GlStateManager.lineWidth(1.0F);
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBufferBuilder();
        double d0 = camera.getPos().x;
        double d1 = camera.getPos().y - .005D;
        double d2 = camera.getPos().z;
        
        buffer.begin(1, VertexFormats.POSITION_COLOR);
        buffer.vertex(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
    }
    
    @Override
    public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        KeyBindingRegistryImpl.INSTANCE.addCategory(KEYBIND_CATEGORY);
        KeyBindingRegistryImpl.INSTANCE.register(enableOverlay = FabricKeyBinding.Builder.create(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY).build());
        ClothClientHooks.HANDLE_INPUT.register(minecraftClient -> {
            while (enableOverlay.wasPressed())
                enabled = !enabled;
        });
        ClothClientHooks.DEBUG_RENDER_PRE.register(() -> {
            if (LightOverlay.enabled) {
                PlayerEntity playerEntity = client.player;
                World world = client.world;
                GlStateManager.disableTexture();
                GlStateManager.disableBlend();
                BlockPos playerPos = new BlockPos(playerEntity.x, playerEntity.y, playerEntity.z);
                BlockPos.iterate(playerPos.add(-reach, -reach, -reach), playerPos.add(reach, reach, reach)).forEach(pos -> {
                    if (world.getBiome(pos).getMaxSpawnLimit() > 0) {
                        CrossType type = LightOverlay.getCrossType(pos, world, playerEntity);
                        if (type != CrossType.NONE) {
                            VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
                            Color color = type == CrossType.RED ? Color.RED : Color.YELLOW;
                            LightOverlay.renderCross(pos, color, playerEntity);
                        }
                    }
                });
                GlStateManager.enableBlend();
                GlStateManager.enableTexture();
            }
        });
    }
    
    private static enum CrossType {
        YELLOW,
        RED,
        NONE
    }
    
}

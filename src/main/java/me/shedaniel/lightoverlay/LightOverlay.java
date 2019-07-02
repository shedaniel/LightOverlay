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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCategory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.awt.*;

public class LightOverlay implements ClientModInitializer {
    
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static final Identifier INCREASE_REACH_KEYBIND = new Identifier("lightoverlay", "increase_reach");
    private static final Identifier DECREASE_REACH_KEYBIND = new Identifier("lightoverlay", "decrease_reach");
    private static FabricKeyBinding enableOverlay, increaseReach, decreaseReach;
    private static boolean enabled = false;
    private static int reach = 12;
    private static EntityType<Entity> testingEntityType;
    
    public static CrossType getCrossType(BlockPos pos, World world, PlayerEntity playerEntity) {
        BlockState blockBelowState = world.getBlockState(pos.down());
        BlockState blockState = world.getBlockState(pos);
        if (blockBelowState.isAir() || !blockState.isAir())
            return CrossType.NONE;
        if (blockBelowState.getBlock() == Blocks.BEDROCK || blockBelowState.getBlock() == Blocks.BARRIER)
            return CrossType.NONE;
        if (!blockBelowState.allowsSpawning(world, pos.down(), testingEntityType))
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
        testingEntityType = EntityType.Builder.create(EntityCategory.MONSTER).setSize(0f, 0f).disableSaving().build(null);
        MinecraftClient client = MinecraftClient.getInstance();
        KeyBindingRegistryImpl.INSTANCE.addCategory(KEYBIND_CATEGORY);
        KeyBindingRegistryImpl.INSTANCE.register(enableOverlay = FabricKeyBinding.Builder.create(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(increaseReach = FabricKeyBinding.Builder.create(INCREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(decreaseReach = FabricKeyBinding.Builder.create(DECREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        ClothClientHooks.HANDLE_INPUT.register(minecraftClient -> {
            while (enableOverlay.wasPressed())
                enabled = !enabled;
            while (increaseReach.wasPressed()) {
                if (reach < 50)
                    reach++;
                client.player.addChatMessage(new TranslatableComponent("text.lightoverlay.current_reach", reach), false);
            }
            while (decreaseReach.wasPressed()) {
                if (reach > 1)
                    reach--;
                client.player.addChatMessage(new TranslatableComponent("text.lightoverlay.current_reach", reach), false);
            }
        });
        ClothClientHooks.DEBUG_RENDER_PRE.register(() -> {
            if (LightOverlay.enabled) {
                PlayerEntity playerEntity = client.player;
                World world = client.world;
                GlStateManager.disableTexture();
                GlStateManager.disableBlend();
                BlockPos playerPos = new BlockPos(playerEntity.x, playerEntity.y, playerEntity.z);
                BlockPos.iterate(playerPos.add(-reach, -reach, -reach), playerPos.add(reach, reach, reach)).forEach(pos -> {
                    Biome biome = world.getBiome(pos);
                    if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
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

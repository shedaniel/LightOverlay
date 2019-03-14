package me.shedaniel.lightoverlay;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.class_4184;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.VerticalEntityPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sortme.SpawnHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BoundingBox;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.awt.*;

public class LightOverlay implements ClientModInitializer {
    
    private static final BoundingBox TEST_BOX = new BoundingBox(0.6D / 2D, 0, 0.6D / 2D, 1D - 0.6D / 2D, 1D, 1D - 0.6D / 2D);
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static boolean enabled = false;
    private static FabricKeyBinding enableOverlay;
    private static int reach = 12;
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static void setEnabled(boolean enabled) {
        LightOverlay.enabled = enabled;
    }
    
    public static int getReach() {
        return reach;
    }
    
    public static CrossType getCrossType(BlockPos pos, World world, PlayerEntity playerEntity) {
        BlockState blockBelowState = world.getBlockState(pos.down());
        if (blockBelowState.getBlock() == Blocks.BEDROCK || blockBelowState.getBlock() == Blocks.BARRIER)
            return CrossType.NONE;
        if ((!blockBelowState.getMaterial().method_15804() && blockBelowState.isTranslucent(world, pos.down())) || !SpawnHelper.isClearForSpawn(world, pos, world.getBlockState(pos), world.getFluidState(pos)))
            return CrossType.NONE;
        if (!world.method_8628(world.getBlockState(pos), pos, VerticalEntityPosition.fromEntity(playerEntity)))
            return CrossType.NONE;
        if (blockBelowState.isAir() || !world.getBlockState(pos).isAir() || !blockBelowState.hasSolidTopSurface(world, pos) || !world.getFluidState(pos.down()).isEmpty())
            return CrossType.NONE;
        if (world.method_8312(LightType.BLOCK, pos) >= 8)
            return CrossType.NONE;
        if (world.method_8312(LightType.SKY, pos) >= 8)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static void renderCross(BlockPos pos, Color color, double delta, PlayerEntity entity) {
        class_4184 class_4184 = MinecraftClient.getInstance().gameRenderer.method_19418();
        GlStateManager.lineWidth(1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBufferBuilder();
        double d0 = class_4184.method_19326().x;
        double d1 = class_4184.method_19326().y - .005D;
        double d2 = class_4184.method_19326().z;
        
        buffer.begin(1, VertexFormats.POSITION_COLOR);
        buffer.vertex(pos.getX() - d0, pos.getY() - d1, pos.getZ() - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() + 1 - d0, pos.getY() - d1, pos.getZ() + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() + 1 - d0, pos.getY() - d1, pos.getZ() - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() - d0, pos.getY() - d1, pos.getZ() + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        tessellator.draw();
    }
    
    public static FabricKeyBinding getEnableOverlay() {
        return enableOverlay;
    }
    
    @Override
    public void onInitializeClient() {
        KeyBindingRegistryImpl.INSTANCE.addCategory(KEYBIND_CATEGORY);
        KeyBindingRegistryImpl.INSTANCE.register(enableOverlay = FabricKeyBinding.Builder.create(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEY_KEYBOARD, 296, KEYBIND_CATEGORY).build());
    }
    
}

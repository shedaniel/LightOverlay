package me.shedaniel.lightoverlay;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.awt.*;

public class LightOverlay implements ClientModInitializer {
    
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private final static VoxelShape TEST_SHAPE = VoxelShapes.cube(0.6D / 2D, 0, 0.6D / 2D, 1D - 0.6D / 2D, 1D, 1D - 0.6D / 2D);
    private static boolean enabled = false;
    private static FabricKeyBinding enableOverlay;
    private static int reach = 8;
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static void setEnabled(boolean enabled) {
        LightOverlay.enabled = enabled;
    }
    
    public static int getReach() {
        return reach;
    }
    
    public static CrossType getCrossType(BlockPos pos, World world) {
        BlockState state = world.getBlockState(pos.down());
        if (world.isAir(pos.down()) || !world.isAir(pos) || (!state.hasSolidTopSurface(world, pos) && state.isTranslucent(world, pos)))
            return CrossType.NONE;
        if (world.method_8312(LightType.BLOCK_LIGHT, pos) >= 8)
            return CrossType.NONE;
        if (world.method_8312(LightType.SKY_LIGHT, pos) >= 8)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static void renderCross(BlockPos pos, Color color, double delta, PlayerEntity entity) {
        GlStateManager.lineWidth(1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBufferBuilder();
        double d0 = MathHelper.lerp(delta, entity.prevRenderX, entity.x);
        double d1 = MathHelper.lerp(delta, entity.prevRenderY, entity.y);
        double d2 = MathHelper.lerp(delta, entity.prevRenderZ, entity.z);
        
        buffer.begin(1, VertexFormats.POSITION_COLOR);
        buffer.vertex(pos.getX() - d0, pos.getY() + .005D - d1, pos.getZ() - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() + 1 - d0, pos.getY() + .005D - d1, pos.getZ() + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() + 1 - d0, pos.getY() + .005D - d1, pos.getZ() - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
        buffer.vertex(pos.getX() - d0, pos.getY() + .005D - d1, pos.getZ() + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
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

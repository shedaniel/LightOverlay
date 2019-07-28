package me.shedaniel.lightoverlay.fabric;

import com.mojang.blaze3d.platform.GlStateManager;
import me.shedaniel.cloth.hooks.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCategory;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tag.BlockTags;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Properties;

public class LightOverlay implements ClientModInitializer {
    
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static final Identifier INCREASE_REACH_KEYBIND = new Identifier("lightoverlay", "increase_reach");
    private static final Identifier DECREASE_REACH_KEYBIND = new Identifier("lightoverlay", "decrease_reach");
    private static final Identifier INCREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "increase_line_width");
    private static final Identifier DECREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "decrease_line_width");
    private static FabricKeyBinding enableOverlay, increaseReach, decreaseReach, increaseLineWidth, decreaseLineWidth;
    private static boolean enabled = false;
    private static int reach = 12;
    private static float lineWidth = 1.0F;
    private static File configFile = new File(FabricLoader.getInstance().getConfigDirectory(), "lightoverlay.properties");
    private static EntityType<Entity> testingEntityType;
    private static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    
    public static CrossType getCrossType(BlockPos pos, World world, PlayerEntity playerEntity) {
        BlockState blockBelowState = world.getBlockState(pos.down());
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, EntityContext.of(playerEntity));
        // Check if the outline is full
        if (Block.isFaceFullSquare(upperCollisionShape, Direction.UP))
            return CrossType.NONE;
        // TODO: Not to hard code no redstone
        if (blockUpperState.emitsRedstonePower())
            return CrossType.NONE;
        // Check if the collision has a bump
        if (upperCollisionShape.getMaximum(Direction.Axis.Y) > 0)
            return CrossType.NONE;
        if (blockUpperState.getBlock().matches(BlockTags.RAILS))
            return CrossType.NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.allowsSpawning(world, pos.down(), testingEntityType))
            return CrossType.NONE;
        if (world.getLightLevel(LightType.BLOCK, pos) >= 8)
            return CrossType.NONE;
        if (world.getLightLevel(LightType.SKY, pos) >= 8)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static void renderCross(World world, BlockPos pos, Color color, PlayerEntity entity) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        GlStateManager.lineWidth(lineWidth);
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBufferBuilder();
        double d0 = camera.getPos().x;
        double d1 = camera.getPos().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getOutlineShape(world, pos, EntityContext.of(entity));
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getMaximum(Direction.Axis.Y);
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
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityCategory.MONSTER).setDimensions(0f, 0f).disableSaving().build(null);
        MinecraftClient client = MinecraftClient.getInstance();
        KeyBindingRegistryImpl.INSTANCE.addCategory(KEYBIND_CATEGORY);
        KeyBindingRegistryImpl.INSTANCE.register(enableOverlay = FabricKeyBinding.Builder.create(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(increaseReach = FabricKeyBinding.Builder.create(INCREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(decreaseReach = FabricKeyBinding.Builder.create(DECREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(increaseLineWidth = FabricKeyBinding.Builder.create(INCREASE_LINE_WIDTH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistryImpl.INSTANCE.register(decreaseLineWidth = FabricKeyBinding.Builder.create(DECREASE_LINE_WIDTH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        ClothClientHooks.HANDLE_INPUT.register(minecraftClient -> {
            while (enableOverlay.wasPressed())
                enabled = !enabled;
            while (increaseReach.wasPressed()) {
                if (reach < 50)
                    reach++;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addChatMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (decreaseReach.wasPressed()) {
                if (reach > 1)
                    reach--;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addChatMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (increaseLineWidth.wasPressed()) {
                if (lineWidth < 7)
                    lineWidth += 0.1f;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addChatMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
            }
            while (decreaseLineWidth.wasPressed()) {
                if (lineWidth > 1)
                    lineWidth -= 0.1F;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addChatMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
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
                            LightOverlay.renderCross(world, pos, color, playerEntity);
                        }
                    }
                });
                GlStateManager.enableBlend();
                GlStateManager.enableTexture();
            }
        });
    }
    
    private void loadConfig(File file) {
        try {
            if (!file.exists() || !file.canRead())
                saveConfig(file);
            FileInputStream fis = new FileInputStream(file);
            Properties properties = new Properties();
            properties.load(fis);
            fis.close();
            for(Map.Entry<Object, Object> entry : properties.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    String key = (String) entry.getKey();
                    String value = (String) entry.getValue();
                    if (key.equals("reach")) {
                        try {
                            reach = Integer.valueOf(value);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            reach = 12;
                        }
                    } else if (key.equals("lineWidth")) {
                        try {
                            lineWidth = Float.valueOf(value);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            lineWidth = 1.0F;
                        }
                    }
                }
            }
            saveConfig(file);
        } catch (Exception e) {
            e.printStackTrace();
            reach = 12;
            lineWidth = 1.0F;
        }
    }
    
    private void saveConfig(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write(("reach=" + String.valueOf(reach)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("lineWidth=" + FORMAT.format(lineWidth)).getBytes());
        fos.close();
    }
    
    private static enum CrossType {
        YELLOW,
        RED,
        NONE
    }
    
}

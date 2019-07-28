package me.shedaniel.lightoverlay;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
@Mod("lightoverlay-forge")
public class LightOverlay {
    
    private static final String KEYBIND_CATEGORY = "key.lightoverlay-forge.category";
    private static final ResourceLocation ENABLE_OVERLAY_KEYBIND = new ResourceLocation("lightoverlay-forge", "enable_overlay");
    private static final ResourceLocation INCREASE_REACH_KEYBIND = new ResourceLocation("lightoverlay-forge", "increase_reach");
    private static final ResourceLocation DECREASE_REACH_KEYBIND = new ResourceLocation("lightoverlay-forge", "decrease_reach");
    private static final ResourceLocation INCREASE_LINE_WIDTH_KEYBIND = new ResourceLocation("lightoverlay-forge", "increase_line_width");
    private static final ResourceLocation DECREASE_LINE_WIDTH_KEYBIND = new ResourceLocation("lightoverlay-forge", "decrease_line_width");
    private static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static KeyBinding enableOverlay, increaseReach, decreaseReach, increaseLineWidth, decreaseLineWidth;
    private static boolean enabled = false;
    private static int reach = 12;
    private static EntityType<Entity> testingEntityType;
    private static float lineWidth = 1.0F;
    private static File configFile = new File(new File(Minecraft.getInstance().gameDir, "config"), "lightoverlay.properties");
    
    public LightOverlay() {
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityClassification.MONSTER).size(0f, 0f).disableSerialization().build(null);
        enableOverlay = registerKeybind(ENABLE_OVERLAY_KEYBIND, InputMappings.Type.KEYSYM, 296, KEYBIND_CATEGORY);
        increaseReach = registerKeybind(INCREASE_REACH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        decreaseReach = registerKeybind(DECREASE_REACH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        increaseLineWidth = registerKeybind(INCREASE_LINE_WIDTH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        decreaseLineWidth = registerKeybind(DECREASE_LINE_WIDTH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    public static CrossType getCrossType(BlockPos pos, World world, PlayerEntity playerEntity) {
        BlockState blockBelowState = world.getBlockState(pos.down());
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, ISelectionContext.forEntity(playerEntity));
        if (!blockUpperState.getFluidState().isEmpty())
            return CrossType.NONE;
        /* WorldEntitySpawner.func_222266_a */
        // Check if the outline is full
        if (Block.doesSideFillSquare(upperCollisionShape, Direction.UP))
            return CrossType.NONE;
        // Check if there is power
        if (blockUpperState.canProvidePower())
            return CrossType.NONE;
        // Check if the collision has a bump
        if (upperCollisionShape.getEnd(Direction.Axis.Y) > 0)
            return CrossType.NONE;
        if (blockUpperState.getBlock().isIn(BlockTags.RAILS))
            return CrossType.NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.canEntitySpawn(world, pos.down(), testingEntityType))
            return CrossType.NONE;
        if (world.getLightFor(LightType.BLOCK, pos) >= 8)
            return CrossType.NONE;
        if (world.getLightFor(LightType.SKY, pos) >= 8)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static void renderCross(World world, BlockPos pos, Color color, PlayerEntity entity) {
        ActiveRenderInfo info = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();
        GlStateManager.lineWidth(lineWidth);
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        double d0 = info.getProjectedView().x;
        double d1 = info.getProjectedView().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, ISelectionContext.forEntity(entity));
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getEnd(Direction.Axis.Y);
        double d2 = info.getProjectedView().z;
        
        buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
        buffer.pos(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
        buffer.pos(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
        buffer.pos(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
    }
    
    @SubscribeEvent(receiveCanceled = true)
    public void handleInput(InputEvent.KeyInputEvent event) {
        if (enableOverlay.isPressed())
            enabled = !enabled;
        if (increaseReach.isPressed()) {
            if (reach < 50)
                reach++;
            try {
                saveConfig(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Minecraft.getInstance().player.sendStatusMessage(new TranslationTextComponent("text.lightoverlay-forge.current_reach", reach), false);
        }
        if (decreaseReach.isPressed()) {
            if (reach > 1)
                reach--;
            try {
                saveConfig(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Minecraft.getInstance().player.sendStatusMessage(new TranslationTextComponent("text.lightoverlay-forge.current_reach", reach), false);
        }
        if (increaseLineWidth.isPressed()) {
            if (lineWidth < 7)
                lineWidth += 0.1f;
            try {
                saveConfig(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Minecraft.getInstance().player.sendStatusMessage(new TranslationTextComponent("text.lightoverlay-forge.current_line_width", FORMAT.format(lineWidth)), false);
        }
        if (decreaseLineWidth.isPressed()) {
            if (lineWidth > 1)
                lineWidth -= 0.1F;
            try {
                saveConfig(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Minecraft.getInstance().player.sendStatusMessage(new TranslationTextComponent("text.lightoverlay-forge.current_line_width", FORMAT.format(lineWidth)), false);
        }
    }
    
    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        if (LightOverlay.enabled) {
            Minecraft client = Minecraft.getInstance();
            ClientPlayerEntity playerEntity = client.player;
            World world = client.world;
            GlStateManager.disableTexture();
            GlStateManager.disableBlend();
            BlockPos playerPos = new BlockPos(playerEntity.posX, playerEntity.posY, playerEntity.posZ);
            BlockPos.getAllInBox(playerPos.add(-reach, -reach, -reach), playerPos.add(reach, reach, reach)).forEach(pos -> {
                Biome biome = world.getBiome(pos);
                if (biome.getSpawningChance() > 0 && !biome.getSpawns(EntityClassification.MONSTER).isEmpty()) {
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
    }
    
    private KeyBinding registerKeybind(ResourceLocation resourceLocation, InputMappings.Type type, int keyCode, String category) {
        KeyBinding keyBinding = new KeyBinding("key." + resourceLocation.getNamespace() + "." + resourceLocation.getPath(), KeyConflictContext.IN_GAME, KeyModifier.NONE, type, keyCode, category);
        ClientRegistry.registerKeyBinding(keyBinding);
        return keyBinding;
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

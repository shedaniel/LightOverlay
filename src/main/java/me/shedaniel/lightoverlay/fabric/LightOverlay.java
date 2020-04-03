package me.shedaniel.lightoverlay.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.cloth.hooks.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.AffineTransformation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCategory;
import net.minecraft.block.ShapeContext;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Properties;

public class LightOverlay implements ClientModInitializer {
    
    static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static final Identifier INCREASE_REACH_KEYBIND = new Identifier("lightoverlay", "increase_reach");
    private static final Identifier DECREASE_REACH_KEYBIND = new Identifier("lightoverlay", "decrease_reach");
    private static final Identifier INCREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "increase_line_width");
    private static final Identifier DECREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "decrease_line_width");
    static int reach = 7;
    static int crossLevel = 7;
    static boolean showNumber = false;
    static float lineWidth = 1.0F;
    static int yellowColor = 0xFFFF00, redColor = 0xFF0000;
    static File configFile = new File(FabricLoader.getInstance().getConfigDirectory(), "lightoverlay.properties");
    private static FabricKeyBinding enableOverlay, increaseReach, decreaseReach, increaseLineWidth, decreaseLineWidth;
    private static boolean enabled = false;
    private static EntityType<Entity> testingEntityType;
    
    public static CrossType getCrossType(BlockPos pos, BlockPos down, World world, ShapeContext shapeContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, shapeContext);
        if (!blockUpperState.getFluidState().isEmpty())
            return CrossType.NONE;
        // Check if the outline is full
        if (Block.isFaceFullSquare(upperCollisionShape, Direction.UP))
            return CrossType.NONE;
        // TODO: Not to hard code no redstone
        if (blockUpperState.emitsRedstonePower())
            return CrossType.NONE;
        // Check if the collision has a bump
        if (upperCollisionShape.getMaximum(Direction.Axis.Y) > 0)
            return CrossType.NONE;
        if (blockUpperState.getBlock().isIn(BlockTags.RAILS))
            return CrossType.NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.allowsSpawning(world, down, testingEntityType))
            return CrossType.NONE;
        if (world.getLightLevel(LightType.BLOCK, pos) > crossLevel)
            return CrossType.NONE;
        if (world.getLightLevel(LightType.SKY, pos) > crossLevel)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, World world, ShapeContext shapeContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape collisionShape = blockBelowState.getCollisionShape(world, down, shapeContext);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, shapeContext);
        if (!blockUpperState.getFluidState().isEmpty())
            return -1;
        if (!blockBelowState.getFluidState().isEmpty())
            return -1;
        if (blockBelowState.isAir())
            return -1;
        if (!blockUpperState.isAir())
            return -1;
        return world.getLightLevel(LightType.BLOCK, pos);
    }
    
    public static void renderCross(Tessellator tessellator, BufferBuilder buffer, Camera camera, World world, BlockPos pos, int color, ShapeContext shapeContext) {
        double d0 = camera.getPos().x;
        double d1 = camera.getPos().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getOutlineShape(world, pos, shapeContext);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getMaximum(Direction.Axis.Y);
        double d2 = camera.getPos().z;
        
        buffer.begin(1, VertexFormats.POSITION_COLOR);
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        buffer.vertex(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(red, green, blue, 255).next();
        buffer.vertex(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(red, green, blue, 255).next();
        buffer.vertex(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(red, green, blue, 255).next();
        buffer.vertex(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(red, green, blue, 255).next();
        tessellator.draw();
    }
    
    public static void renderLevel(MinecraftClient client, Camera camera, World world, BlockPos pos, BlockPos down, int level, ShapeContext shapeContext) {
        String string_1 = String.valueOf(level);
        TextRenderer textRenderer_1 = client.textRenderer;
        double double_4 = camera.getPos().x;
        double double_5 = camera.getPos().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getOutlineShape(world, down, shapeContext);
        if (!upperOutlineShape.isEmpty())
            double_5 += 1 - upperOutlineShape.getMaximum(Direction.Axis.Y);
        double double_6 = camera.getPos().z;
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (pos.getX() + 0.5f - double_4), (float) (pos.getY() - double_5) + 0.005f, (float) (pos.getZ() + 0.5f - double_6));
        RenderSystem.rotatef(90, 1, 0, 0);
        RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        RenderSystem.scalef(-size, -size, size);
        float float_3 = (float) (-textRenderer_1.getStringWidth(string_1)) / 2.0F + 0.4f;
        RenderSystem.enableAlphaTest();
        VertexConsumerProvider.Immediate vertexConsumerProvider$Immediate_1 = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
        textRenderer_1.draw(string_1, float_3, -3.5f, level > crossLevel ? 0xff042404 : 0xff731111, false, AffineTransformation.identity().getMatrix(), vertexConsumerProvider$Immediate_1, false, 0, 15728880);
        vertexConsumerProvider$Immediate_1.draw();
        RenderSystem.popMatrix();
    }
    
    static void loadConfig(File file) {
        try {
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            if (!file.exists() || !file.canRead())
                saveConfig(file);
            FileInputStream fis = new FileInputStream(file);
            Properties properties = new Properties();
            properties.load(fis);
            fis.close();
            reach = Integer.parseInt((String) properties.computeIfAbsent("reach", a -> "7"));
            crossLevel = Integer.parseInt((String) properties.computeIfAbsent("crossLevel", a -> "7"));
            showNumber = ((String) properties.computeIfAbsent("showNumber", a -> "false")).equalsIgnoreCase("true");
            lineWidth = Float.parseFloat((String) properties.computeIfAbsent("lineWidth", a -> "1"));
            {
                int r, g, b;
                r = Integer.parseInt((String) properties.computeIfAbsent("yellowColorRed", a -> "255"));
                g = Integer.parseInt((String) properties.computeIfAbsent("yellowColorGreen", a -> "255"));
                b = Integer.parseInt((String) properties.computeIfAbsent("yellowColorBlue", a -> "0"));
                yellowColor = (r << 16) + (g << 8) + b;
            }
            {
                int r, g, b;
                r = Integer.parseInt((String) properties.computeIfAbsent("redColorRed", a -> "255"));
                g = Integer.parseInt((String) properties.computeIfAbsent("redColorGreen", a -> "0"));
                b = Integer.parseInt((String) properties.computeIfAbsent("redColorBlue", a -> "0"));
                redColor = (r << 16) + (g << 8) + b;
            }
            saveConfig(file);
        } catch (Exception e) {
            e.printStackTrace();
            reach = 7;
            lineWidth = 1.0F;
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            try {
                saveConfig(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    static void saveConfig(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write("# Light Overlay Config".getBytes());
        fos.write("\n".getBytes());
        fos.write(("reach=" + reach).getBytes());
        fos.write("\n".getBytes());
        fos.write(("crossLevel=" + crossLevel).getBytes());
        fos.write("\n".getBytes());
        fos.write(("showNumber=" + showNumber).getBytes());
        fos.write("\n".getBytes());
        fos.write(("lineWidth=" + FORMAT.format(lineWidth)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("yellowColorRed=" + ((yellowColor >> 16) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("yellowColorGreen=" + ((yellowColor >> 8) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("yellowColorBlue=" + (yellowColor & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("redColorRed=" + ((redColor >> 16) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("redColorGreen=" + ((redColor >> 8) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("redColorBlue=" + (redColor & 255)).getBytes());
        fos.close();
    }
    
    @Override
    public void onInitializeClient() {
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityCategory.MONSTER).setDimensions(0f, 0f).disableSaving().build(null);
        MinecraftClient client = MinecraftClient.getInstance();
        KeyBindingRegistry.INSTANCE.addCategory(KEYBIND_CATEGORY);
        KeyBindingRegistry.INSTANCE.register(enableOverlay = FabricKeyBinding.Builder.create(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY).build());
        KeyBindingRegistry.INSTANCE.register(increaseReach = FabricKeyBinding.Builder.create(INCREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistry.INSTANCE.register(decreaseReach = FabricKeyBinding.Builder.create(DECREASE_REACH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistry.INSTANCE.register(increaseLineWidth = FabricKeyBinding.Builder.create(INCREASE_LINE_WIDTH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
        KeyBindingRegistry.INSTANCE.register(decreaseLineWidth = FabricKeyBinding.Builder.create(DECREASE_LINE_WIDTH_KEYBIND, InputUtil.Type.KEYSYM, -1, KEYBIND_CATEGORY).build());
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
                client.player.addMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (decreaseReach.wasPressed()) {
                if (reach > 1)
                    reach--;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (increaseLineWidth.wasPressed()) {
                if (lineWidth < 7)
                    lineWidth += 0.1f;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
            }
            while (decreaseLineWidth.wasPressed()) {
                if (lineWidth > 1)
                    lineWidth -= 0.1F;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.addMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
            }
        });
        ClothClientHooks.DEBUG_RENDER_PRE.register(() -> {
            if (LightOverlay.enabled) {
                PlayerEntity playerEntity = client.player;
                ShapeContext shapeContext = ShapeContext.of(playerEntity);
                World world = client.world;
                BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
                if (showNumber) {
                    RenderSystem.enableTexture();
                    
                    RenderSystem.depthMask(true);
                    for (BlockPos pos : BlockPos.iterate(playerPos.add(-reach, -reach, -reach), playerPos.add(reach, reach, reach))) {
                        Biome biome = world.getBiome(pos);
                        if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
                            BlockPos down = pos.down();
                            int level = LightOverlay.getCrossLevel(pos, down, world, shapeContext);
                            if (level >= 0) {
                                LightOverlay.renderLevel(client, camera, world, pos, down, level, shapeContext);
                            }
                        }
                    }
                    RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.enableDepthTest();
                    RenderSystem.shadeModel(7425);
                    RenderSystem.enableAlphaTest();
                    RenderSystem.defaultAlphaFunc();
                    RenderSystem.disableTexture();
                    RenderSystem.disableBlend();
                    RenderSystem.lineWidth(lineWidth);
                    RenderSystem.depthMask(false);
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder buffer = tessellator.getBuffer();
                    for (BlockPos pos : BlockPos.iterate(playerPos.add(-reach, -reach, -reach), playerPos.add(reach, reach, reach))) {
                        Biome biome = world.getBiome(pos);
                        if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
                            BlockPos down = pos.down();
                            CrossType type = LightOverlay.getCrossType(pos, down, world, shapeContext);
                            if (type != CrossType.NONE) {
                                int color = type == CrossType.RED ? redColor : yellowColor;
                                LightOverlay.renderCross(tessellator, buffer, camera, world, pos, color, shapeContext);
                            }
                        }
                    }
                    RenderSystem.depthMask(true);
                    RenderSystem.enableBlend();
                    RenderSystem.enableTexture();
                    RenderSystem.shadeModel(7424);
                }
            }
        });
    }
    
    private enum CrossType {
        YELLOW,
        RED,
        NONE
    }
    
}

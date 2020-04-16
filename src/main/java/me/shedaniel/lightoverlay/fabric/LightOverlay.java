package me.shedaniel.lightoverlay.fabric;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.cloth.hooks.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.AffineTransformation;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCategory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tag.BlockTags;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LightOverlay implements ClientModInitializer {
    
    static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    private static final Identifier INCREASE_REACH_KEYBIND = new Identifier("lightoverlay", "increase_reach");
    private static final Identifier DECREASE_REACH_KEYBIND = new Identifier("lightoverlay", "decrease_reach");
    private static final Identifier INCREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "increase_line_width");
    private static final Identifier DECREASE_LINE_WIDTH_KEYBIND = new Identifier("lightoverlay", "decrease_line_width");
    static int reach = 12;
    static int crossLevel = 7;
    static boolean showNumber = false;
    static float lineWidth = 1.0F;
    static int yellowColor = 0xFFFF00, redColor = 0xFF0000;
    static File configFile = new File(FabricLoader.getInstance().getConfigDirectory(), "lightoverlay.properties");
    private static FabricKeyBinding enableOverlay, increaseReach, decreaseReach, increaseLineWidth, decreaseLineWidth;
    private static boolean enabled = false;
    private static EntityType<Entity> testingEntityType;
    private static int threadNumber = 0;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread thread = new Thread(r, "light-overlay-" + threadNumber++);
        thread.setDaemon(true);
        return thread;
    });
    private static final List<ChunkPos> POS = Lists.newCopyOnWriteArrayList();
    private static final Map<ChunkPos, Map<Long, Object>> CHUNK_MAP = Maps.newConcurrentMap();
    private static long ticks = 0;
    
    static {
        ClientTickCallback.EVENT.register(client -> {
            try {
                ticks++;
                if (MinecraftClient.getInstance().player == null || !enabled) {
                    POS.clear();
                } else {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    ClientWorld world = MinecraftClient.getInstance().world;
                    ShapeContext shapeContext = ShapeContext.of(player);
                    Vec3d[] playerPos = {null};
                    int playerPosX = ((int) player.getX()) >> 4;
                    int playerPosZ = ((int) player.getZ()) >> 4;
                    if (ticks % 20 == 0) {
                        for (int chunkX = playerPosX - getChunkRange(); chunkX <= playerPosX + getChunkRange(); chunkX++) {
                            for (int chunkZ = playerPosZ - getChunkRange(); chunkZ <= playerPosZ + getChunkRange(); chunkZ++) {
                                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                                if (!CHUNK_MAP.containsKey(chunkPos))
                                    queueChunk(chunkPos);
                            }
                        }
                    }
                    if (!POS.isEmpty()) {
                        if (playerPos[0] == null) {
                            playerPos[0] = player.getPos();
                        }
                        ChunkPos pos = POS.stream().min(Comparator.comparingDouble(value -> value.toBlockPos(8, 0, 8).getSquaredDistance(playerPos[0].x, 0, playerPos[0].z, false))).get();
                        EXECUTOR.submit(() -> {
                            if (MathHelper.abs(pos.x - playerPosX) <= getChunkRange() && MathHelper.abs(pos.z - playerPosZ) <= getChunkRange()) {
                                calculateChunk(world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false), world, pos, shapeContext);
                            } else {
                                CHUNK_MAP.remove(pos);
                            }
                        });
                        POS.remove(pos);
                    }
                    Iterator<Map.Entry<ChunkPos, Map<Long, Object>>> chunkMapIterator = CHUNK_MAP.entrySet().iterator();
                    while (chunkMapIterator.hasNext()) {
                        Map.Entry<ChunkPos, Map<Long, Object>> pos = chunkMapIterator.next();
                        if (MathHelper.abs(pos.getKey().x - playerPosX) > getChunkRange() * 2 || MathHelper.abs(pos.getKey().z - playerPosZ) > getChunkRange() * 2) {
                            chunkMapIterator.remove();
                        }
                    }
                }
            } catch (Exception e) {
                LogManager.getLogger().throwing(e);
            }
        });
    }
    
    public static void queueChunkAndNear(ChunkPos pos) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                queueChunk(new ChunkPos(pos.x + xOffset, pos.z + zOffset));
            }
        }
    }
    
    public static void queueChunk(ChunkPos pos) {
        if (!POS.contains(pos))
            POS.add(0, pos);
    }
    
    public static int getChunkRange() {
        return Math.max(MathHelper.ceil(reach / 16f), 1);
    }
    
    private static void calculateChunk(WorldChunk chunk, World world, ChunkPos chunkPos, ShapeContext shapeContext) {
        Map<Long, Object> map = Maps.newHashMap();
        if (chunk != null) {
            ChunkLightingView block = chunk.getLightingProvider().get(LightType.BLOCK);
            ChunkLightingView sky = showNumber ? null : chunk.getLightingProvider().get(LightType.SKY);
            for (BlockPos pos : BlockPos.iterate(chunkPos.getStartX(), 0, chunkPos.getStartZ(), chunkPos.getEndX(), 256, chunkPos.getEndZ())) {
                BlockPos down = pos.down();
                if (showNumber) {
                    int level = LightOverlay.getCrossLevel(pos, down, chunk, block, shapeContext);
                    if (level >= 0) {
                        map.put(pos.asLong(), level);
                    }
                } else {
                    Biome biome = world.getBiomeAccess().getBiome(pos);
                    if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
                        CrossType type = LightOverlay.getCrossType(pos, down, chunk, block, sky, shapeContext);
                        if (type != CrossType.NONE) {
                            map.put(pos.asLong(), type);
                        }
                    }
                }
            }
            System.out.println(CHUNK_MAP.size());
        }
        CHUNK_MAP.put(chunkPos, map);
    }
    
    public static CrossType getCrossType(BlockPos pos, BlockPos down, BlockView world, ChunkLightingView block, ChunkLightingView sky, ShapeContext shapeContext) {
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
        if (block.getLightLevel(pos) > crossLevel)
            return CrossType.NONE;
        if (sky.getLightLevel(pos) > crossLevel)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, BlockView world, ChunkLightingView view, ShapeContext shapeContext) {
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
        return view.getLightLevel(pos);
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
            reach = Integer.parseInt((String) properties.computeIfAbsent("reach", a -> "12"));
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
            reach = 12;
            crossLevel = 7;
            lineWidth = 1.0F;
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            try {
                saveConfig(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        CHUNK_MAP.clear();
        POS.clear();
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
                if (reach < 64)
                    reach++;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.sendMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (decreaseReach.wasPressed()) {
                if (reach > 1)
                    reach--;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.sendMessage(new TranslatableText("text.lightoverlay.current_reach", reach), false);
            }
            while (increaseLineWidth.wasPressed()) {
                if (lineWidth < 7)
                    lineWidth += 0.1f;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.sendMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
            }
            while (decreaseLineWidth.wasPressed()) {
                if (lineWidth > 1)
                    lineWidth -= 0.1F;
                try {
                    saveConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                client.player.sendMessage(new TranslatableText("text.lightoverlay.current_line_width", FORMAT.format(lineWidth)), false);
            }
        });
        ClothClientHooks.DEBUG_RENDER_PRE.register(() -> {
            if (LightOverlay.enabled) {
                PlayerEntity playerEntity = client.player;
                int playerPosX = ((int) playerEntity.getX()) >> 4;
                int playerPosZ = ((int) playerEntity.getZ()) >> 4;
                ShapeContext shapeContext = ShapeContext.of(playerEntity);
                World world = client.world;
                BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
                if (showNumber) {
                    RenderSystem.enableTexture();
                    RenderSystem.depthMask(true);
                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                    for (Map.Entry<ChunkPos, Map<Long, Object>> entry : CHUNK_MAP.entrySet()) {
                        if (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange()) {
                            continue;
                        }
                        for (Map.Entry<Long, Object> objectEntry : entry.getValue().entrySet()) {
                            if (objectEntry.getValue() instanceof Integer) {
                                mutable.set(BlockPos.unpackLongX(objectEntry.getKey()), BlockPos.unpackLongY(objectEntry.getKey()), BlockPos.unpackLongZ(objectEntry.getKey()));
                                if (mutable.isWithinDistance(playerPos, reach)) {
                                    BlockPos down = mutable.down();
                                    LightOverlay.renderLevel(client, camera, world, mutable, down, (Integer) objectEntry.getValue(), shapeContext);
                                }
                            }
                        }
                    }
                    RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableTexture();
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                    RenderSystem.disableLighting();
                    GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    RenderSystem.lineWidth(lineWidth);
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder buffer = tessellator.getBuffer();
                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                    for (Map.Entry<ChunkPos, Map<Long, Object>> entry : CHUNK_MAP.entrySet()) {
                        if (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange()) {
                            continue;
                        }
                        for (Map.Entry<Long, Object> objectEntry : entry.getValue().entrySet()) {
                            if (objectEntry.getValue() instanceof CrossType) {
                                mutable.set(BlockPos.unpackLongX(objectEntry.getKey()), BlockPos.unpackLongY(objectEntry.getKey()), BlockPos.unpackLongZ(objectEntry.getKey()));
                                if (mutable.isWithinDistance(playerPos, reach)) {
                                    BlockPos down = mutable.down();
                                    int color = objectEntry.getValue() == CrossType.RED ? redColor : yellowColor;
                                    LightOverlay.renderCross(tessellator, buffer, camera, world, mutable, color, shapeContext);
                                }
                            }
                        }
                    }
                    RenderSystem.disableBlend();
                    RenderSystem.enableTexture();
                    GL11.glDisable(GL11.GL_LINE_SMOOTH);
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

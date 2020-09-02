package me.shedaniel.lightoverlay.fabric;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.shedaniel.cloth.hooks.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.Rotation3;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCategory;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tag.BlockTags;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class LightOverlay implements ClientModInitializer {
    
    static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
    static int reach = 12;
    static int crossLevel = 7;
    static int secondaryLevel = -1;
    static int lowerCrossLevel = -1;
    static int higherCrossLevel = -1;
    static boolean caching = false;
    static boolean showNumber = false;
    static boolean smoothLines = true;
    static boolean underwater = false;
    static float lineWidth = 1.0F;
    static int yellowColor = 0xFFFF00, redColor = 0xFF0000, secondaryColor = 0x0000FF;
    static File configFile = new File(FabricLoader.getInstance().getConfigDirectory(), "lightoverlay.properties");
    private static final KeyBinding ENABLE_OVERLAY = createKeyBinding(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY);
    private static boolean enabled = false;
    private static EntityType<Entity> testingEntityType;
    private static int threadNumber = 0;
    private static final ThreadPoolExecutor EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread thread = new Thread(r, "light-overlay-" + threadNumber++);
        thread.setDaemon(true);
        return thread;
    });
    private static final List<ChunkPos> POS = Lists.newCopyOnWriteArrayList();
    private static final Map<ChunkPos, Long2ReferenceMap<Object>> CHUNK_MAP = Maps.newConcurrentMap();
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static long ticks = 0;
    
    static {
        ClientTickCallback.EVENT.register(client -> {
            try {
                ticks++;
                if (CLIENT.player == null || !enabled) {
                    POS.clear();
                    CHUNK_MAP.clear();
                } else {
                    if (!caching) {
                        POS.clear();
                        CHUNK_MAP.clear();
                        ClientPlayerEntity player = CLIENT.player;
                        ClientWorld world = CLIENT.world;
                        BlockPos playerPos = player.getBlockPos();
                        EntityContext entityContext = EntityContext.of(player);
                        ChunkLightingView block = world.getLightingProvider().get(LightType.BLOCK);
                        ChunkLightingView sky = showNumber ? null : world.getLightingProvider().get(LightType.SKY);
                        BlockPos.Mutable downPos = new BlockPos.Mutable();
                        Iterable<BlockPos> iterate = BlockPos.iterate(playerPos.getX() - reach, playerPos.getY() - reach, playerPos.getZ() - reach,
                                playerPos.getX() + reach, playerPos.getY() + reach, playerPos.getZ() + reach);
                        Long2ReferenceMap<Object> map = new Long2ReferenceOpenHashMap<>();
                        CHUNK_MAP.put(new ChunkPos(0, 0), map);
                        for (BlockPos blockPos : iterate) {
                            downPos.set(blockPos.getX(), blockPos.getY() - 1, blockPos.getZ());
                            if (showNumber) {
                                int level = getCrossLevel(blockPos, downPos, world, block, entityContext);
                                if (level >= 0) {
                                    map.put(blockPos.asLong(), Integer.valueOf(level));
                                }
                            } else {
                                Biome biome = world.getBiomeAccess().getBiome(blockPos);
                                if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
                                    CrossType type = getCrossType(blockPos, downPos, world, block, sky, entityContext);
                                    if (type != CrossType.NONE) {
                                        map.put(blockPos.asLong(), type);
                                    }
                                }
                            }
                        }
                    } else {
                        ClientPlayerEntity player = CLIENT.player;
                        ClientWorld world = CLIENT.world;
                        EntityContext entityContext = EntityContext.of(player);
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
                        POS.removeIf(pos -> MathHelper.abs(pos.x - playerPosX) > getChunkRange() || MathHelper.abs(pos.z - playerPosZ) > getChunkRange());
                        for (int k = 0; k < 2; k++) {
                            if (!POS.isEmpty()) {
                                if (playerPos[0] == null) {
                                    playerPos[0] = player.getPos();
                                }
                                ChunkPos pos = POS.stream().min(Comparator.comparingDouble(value -> {
                                    int i = Math.abs(value.x - playerPosX);
                                    int j = Math.abs(value.z - playerPosZ);
                                    return i * i + j * j;
                                })).get();
                                POS.remove(pos);
                                EXECUTOR.submit(() -> {
                                    try {
                                        calculateChunk(world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false), world, pos, entityContext);
                                    } catch (Throwable throwable) {
                                        LogManager.getLogger().throwing(throwable);
                                    }
                                });
                            }
                        }
                        if (ticks % 50 == 0) {
                            CHUNK_MAP.entrySet().removeIf(pos -> MathHelper.abs(pos.getKey().x - playerPosX) > getChunkRange() * 2 || MathHelper.abs(pos.getKey().z - playerPosZ) > getChunkRange() * 2);
                        }
                    }
                }
            } catch (Throwable throwable) {
                LogManager.getLogger().throwing(throwable);
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
        if (caching)
            if (!POS.contains(pos))
                POS.add(0, pos);
    }
    
    public static int getChunkRange() {
        return Math.max(MathHelper.ceil(reach / 16f), 1);
    }
    
    private static void calculateChunk(WorldChunk chunk, World world, ChunkPos chunkPos, EntityContext entityContext) {
        if (world != null && chunk != null) {
            Long2ReferenceMap<Object> map = new Long2ReferenceOpenHashMap<>();
            ChunkLightingView block = world.getLightingProvider().get(LightType.BLOCK);
            ChunkLightingView sky = showNumber ? null : world.getLightingProvider().get(LightType.SKY);
            for (BlockPos pos : BlockPos.iterate(chunkPos.getStartX(), 0, chunkPos.getStartZ(), chunkPos.getEndX(), 256, chunkPos.getEndZ())) {
                BlockPos down = pos.down();
                if (showNumber) {
                    int level = LightOverlay.getCrossLevel(pos, down, chunk, block, entityContext);
                    if (level >= 0) {
                        map.put(pos.asLong(), Integer.valueOf(level));
                    }
                } else {
                    Biome biome = world.getBiomeAccess().getBiome(pos);
                    if (biome.getMaxSpawnLimit() > 0 && !biome.getEntitySpawnList(EntityCategory.MONSTER).isEmpty()) {
                        CrossType type = LightOverlay.getCrossType(pos, down, chunk, block, sky, entityContext);
                        if (type != CrossType.NONE) {
                            map.put(pos.asLong(), type);
                        }
                    }
                }
            }
            CHUNK_MAP.put(chunkPos, map);
        } else {
            CHUNK_MAP.remove(chunkPos);
        }
    }
    
    public static CrossType getCrossType(BlockPos pos, BlockPos down, BlockView world, ChunkLightingView block, ChunkLightingView sky, EntityContext entityContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, entityContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
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
        if (blockUpperState.getBlock().matches(BlockTags.RAILS))
            return CrossType.NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.allowsSpawning(world, down, testingEntityType))
            return CrossType.NONE;
        int blockLightLevel = block.getLightLevel(pos);
        int skyLightLevel = sky.getLightLevel(pos);
        if (blockLightLevel > higherCrossLevel)
            return CrossType.NONE;
        if (skyLightLevel > higherCrossLevel)
            return CrossType.YELLOW;
        return lowerCrossLevel >= 0 && blockLightLevel > lowerCrossLevel ? CrossType.SECONDARY : CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, BlockView world, ChunkLightingView view, EntityContext entityContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape collisionShape = blockBelowState.getCollisionShape(world, down, entityContext);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, entityContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return -1;
        if (!blockBelowState.getFluidState().isEmpty())
            return -1;
        if (blockBelowState.isAir())
            return -1;
        if (Block.isFaceFullSquare(upperCollisionShape, Direction.DOWN))
            return -1;
        return view.getLightLevel(pos);
    }
    
    public static void renderCross(Camera camera, World world, BlockPos pos, int color, EntityContext entityContext) {
        double d0 = camera.getPos().x;
        double d1 = camera.getPos().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getOutlineShape(world, pos, entityContext);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getMaximum(Direction.Axis.Y);
        double d2 = camera.getPos().z;
        
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        RenderSystem.color4f(red / 255f, green / 255f, blue / 255f, 1f);
        GL11.glVertex3d(x + .01 - d0, y - d1, z + .01 - d2);
        GL11.glVertex3d(x - .01 + 1 - d0, y - d1, z - .01 + 1 - d2);
        GL11.glVertex3d(x - .01 + 1 - d0, y - d1, z + .01 - d2);
        GL11.glVertex3d(x + .01 - d0, y - d1, z - .01 + 1 - d2);
    }
    
    @SuppressWarnings("deprecation")
    public static void renderLevel(MinecraftClient client, Camera camera, World world, BlockPos pos, BlockPos down, int level, EntityContext entityContext) {
        String text = String.valueOf(level);
        TextRenderer textRenderer_1 = client.textRenderer;
        double double_4 = camera.getPos().x;
        double double_5 = camera.getPos().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getOutlineShape(world, down, entityContext);
        if (!upperOutlineShape.isEmpty())
            double_5 += 1 - upperOutlineShape.getMaximum(Direction.Axis.Y);
        double double_6 = camera.getPos().z;
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (pos.getX() + 0.5f - double_4), (float) (pos.getY() - double_5) + 0.005f, (float) (pos.getZ() + 0.5f - double_6));
        RenderSystem.rotatef(90, 1, 0, 0);
        RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        RenderSystem.scalef(-size, -size, size);
        float float_3 = (float) (-textRenderer_1.getStringWidth(text)) / 2.0F + 0.4f;
        RenderSystem.enableAlphaTest();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
        textRenderer_1.draw(text, float_3, -3.5f, level > higherCrossLevel ? 0xff042404 : (lowerCrossLevel >= 0 && level > lowerCrossLevel ? 0xff0066ff : 0xff731111), false, Rotation3.identity().getMatrix(), immediate, false, 0, 15728880);
        immediate.draw();
        RenderSystem.popMatrix();
    }
    
    static void loadConfig(File file) {
        try {
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            secondaryColor = 0x0000FF;
            if (!file.exists() || !file.canRead())
                saveConfig(file);
            FileInputStream fis = new FileInputStream(file);
            Properties properties = new Properties();
            properties.load(fis);
            fis.close();
            reach = Integer.parseInt((String) properties.computeIfAbsent("reach", a -> "12"));
            crossLevel = Integer.parseInt((String) properties.computeIfAbsent("crossLevel", a -> "7"));
            secondaryLevel = Integer.parseInt((String) properties.computeIfAbsent("secondaryLevel", a -> "-1"));
            caching = ((String) properties.computeIfAbsent("caching", a -> "false")).equalsIgnoreCase("true");
            showNumber = ((String) properties.computeIfAbsent("showNumber", a -> "false")).equalsIgnoreCase("true");
            smoothLines = ((String) properties.computeIfAbsent("smoothLines", a -> "true")).equalsIgnoreCase("true");
            underwater = ((String) properties.computeIfAbsent("underwater", a -> "false")).equalsIgnoreCase("true");
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
            {
                int r, g, b;
                r = Integer.parseInt((String) properties.computeIfAbsent("secondaryColorRed", a -> "0"));
                g = Integer.parseInt((String) properties.computeIfAbsent("secondaryColorGreen", a -> "0"));
                b = Integer.parseInt((String) properties.computeIfAbsent("secondaryColorBlue", a -> "255"));
                secondaryColor = (r << 16) + (g << 8) + b;
            }
            saveConfig(file);
        } catch (Exception e) {
            e.printStackTrace();
            reach = 12;
            crossLevel = 7;
            secondaryLevel = -1;
            lineWidth = 1.0F;
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            secondaryColor = 0x0000FF;
            caching = false;
            showNumber = false;
            smoothLines = true;
            underwater = false;
            try {
                saveConfig(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (secondaryLevel >= crossLevel) System.err.println("[Light Overlay] Secondary Level is higher than Cross Level");
        lowerCrossLevel = Math.min(crossLevel, secondaryLevel);
        higherCrossLevel = Math.max(crossLevel, secondaryLevel);
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
        fos.write(("secondaryLevel=" + secondaryLevel).getBytes());
        fos.write("\n".getBytes());
        fos.write(("caching=" + caching).getBytes());
        fos.write("\n".getBytes());
        fos.write(("showNumber=" + showNumber).getBytes());
        fos.write("\n".getBytes());
        fos.write(("smoothLines=" + smoothLines).getBytes());
        fos.write("\n".getBytes());
        fos.write(("underwater=" + underwater).getBytes());
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
        fos.write("\n".getBytes());
        fos.write(("secondaryColorRed=" + ((secondaryColor >> 16) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("secondaryColorGreen=" + ((secondaryColor >> 8) & 255)).getBytes());
        fos.write("\n".getBytes());
        fos.write(("secondaryColorBlue=" + (secondaryColor & 255)).getBytes());
        fos.close();
    }
    
    private static KeyBinding createKeyBinding(Identifier id, InputUtil.Type type, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + id.getNamespace() + "." + id.getPath(), type, code, category));
    }
    
    @Override
    public void onInitializeClient() {
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityCategory.MONSTER).setDimensions(0f, 0f).disableSaving().build(null);
        ClientTickCallback.EVENT.register(minecraftClient -> {
            while (ENABLE_OVERLAY.wasPressed())
                enabled = !enabled;
        });
        ClothClientHooks.DEBUG_RENDER_PRE.register(() -> {
            if (LightOverlay.enabled) {
                PlayerEntity playerEntity = CLIENT.player;
                int playerPosX = ((int) playerEntity.getX()) >> 4;
                int playerPosZ = ((int) playerEntity.getZ()) >> 4;
                EntityContext entityContext = EntityContext.of(playerEntity);
                World world = CLIENT.world;
                BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                Camera camera = CLIENT.gameRenderer.getCamera();
                if (showNumber) {
                    RenderSystem.enableTexture();
                    RenderSystem.depthMask(true);
                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                    for (Map.Entry<ChunkPos, Long2ReferenceMap<Object>> entry : CHUNK_MAP.entrySet()) {
                        if (caching && (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        for (Long2ReferenceMap.Entry<Object> objectEntry : entry.getValue().long2ReferenceEntrySet()) {
                            if (objectEntry.getValue() instanceof Integer) {
                                mutable.set(BlockPos.unpackLongX(objectEntry.getLongKey()), BlockPos.unpackLongY(objectEntry.getLongKey()), BlockPos.unpackLongZ(objectEntry.getLongKey()));
                                if (mutable.isWithinDistance(playerPos, reach)) {
                                    BlockPos down = mutable.down();
                                    LightOverlay.renderLevel(CLIENT, camera, world, mutable, down, (Integer) objectEntry.getValue(), entityContext);
                                }
                            }
                        }
                    }
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableTexture();
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                    if (smoothLines) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glLineWidth(lineWidth);
                    GL11.glBegin(GL11.GL_LINES);
                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                    for (Map.Entry<ChunkPos, Long2ReferenceMap<Object>> entry : CHUNK_MAP.entrySet()) {
                        if (caching && (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        for (Long2ReferenceMap.Entry<Object> objectEntry : entry.getValue().long2ReferenceEntrySet()) {
                            if (objectEntry.getValue() instanceof CrossType) {
                                mutable.set(BlockPos.unpackLongX(objectEntry.getLongKey()), BlockPos.unpackLongY(objectEntry.getLongKey()), BlockPos.unpackLongZ(objectEntry.getLongKey()));
                                if (mutable.isWithinDistance(playerPos, reach)) {
                                    int color = objectEntry.getValue() == CrossType.RED ? redColor : objectEntry.getValue() == CrossType.YELLOW ? yellowColor : secondaryColor;
                                    LightOverlay.renderCross(camera, world, mutable, color, entityContext);
                                }
                            }
                        }
                    }
                    GL11.glEnd();
                    RenderSystem.disableBlend();
                    RenderSystem.enableTexture();
                    if (smoothLines) GL11.glDisable(GL11.GL_LINE_SMOOTH);
                }
            }
        });
    }
    
    private enum CrossType {
        YELLOW,
        RED,
        SECONDARY,
        NONE
    }
}

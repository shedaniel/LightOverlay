package me.shedaniel.lightoverlay.common;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
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

public class LightOverlayCore {
    public static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final ResourceLocation ENABLE_OVERLAY_KEYBIND = new ResourceLocation("lightoverlay", "enable_overlay");
    public static int reach = 12;
    public static int crossLevel = 7;
    public static int secondaryLevel = -1;
    public static int lowerCrossLevel = -1;
    public static int higherCrossLevel = -1;
    public static boolean caching = false;
    public static boolean showNumber = false;
    public static boolean smoothLines = true;
    public static boolean underwater = false;
    public static float lineWidth = 1.0F;
    public static int yellowColor = 0xFFFF00, redColor = 0xFF0000, secondaryColor = 0x0000FF;
    public static File configFile;
    private static KeyMapping enableOverlay;
    private static boolean enabled = false;
    private static final LazyLoadedValue<EntityType<Entity>> TESTING_ENTITY_TYPE = new LazyLoadedValue<>(() ->
            EntityType.Builder.createNothing(MobCategory.MONSTER).sized(0f, 0f).noSave().build(null));
    private static int threadNumber = 0;
    public static Frustum frustum;
    private static final ThreadPoolExecutor EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread thread = new Thread(r, "light-overlay-" + threadNumber++);
        thread.setDaemon(true);
        return thread;
    });
    private static final List<ChunkPos> POS = Lists.newCopyOnWriteArrayList();
    private static final Map<ChunkPos, Long2ReferenceMap<Object>> CHUNK_MAP = Maps.newConcurrentMap();
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static long ticks = 0;
    
    public static void register(AbstractPlatform platform) {
        // Load Config
        configFile = new File(platform.getConfigDir().toFile(), "lightoverlay.properties");
        loadConfig(configFile);
        
        enableOverlay = platform.registerKeyBinding(createKeyBinding(ENABLE_OVERLAY_KEYBIND, InputConstants.Type.KEYSYM, 296, KEYBIND_CATEGORY));
        
        platform.registerDebugRenderer(() -> {
            if (enabled) {
                LocalPlayer playerEntity = CLIENT.player;
                int playerPosX = ((int) playerEntity.getX()) >> 4;
                int playerPosZ = ((int) playerEntity.getZ()) >> 4;
                CollisionContext collisionContext = CollisionContext.of(playerEntity);
                Level world = CLIENT.level;
                BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                Camera camera = CLIENT.gameRenderer.getMainCamera();
                
                if (showNumber) {
                    RenderSystem.enableTexture();
                    RenderSystem.depthMask(true);
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                    BlockPos.MutableBlockPos downMutable = new BlockPos.MutableBlockPos();
                    for (Map.Entry<ChunkPos, Long2ReferenceMap<Object>> entry : CHUNK_MAP.entrySet()) {
                        if (caching && (Mth.abs(entry.getKey().x - playerPosX) > getChunkRange() || Mth.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        for (Long2ReferenceMap.Entry<Object> objectEntry : entry.getValue().long2ReferenceEntrySet()) {
                            if (objectEntry.getValue() instanceof Byte) {
                                mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
                                if (mutable.closerThan(playerPos, reach)) {
                                    if (frustum == null || platform.isFrustumVisible(frustum, mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                                        downMutable.set(mutable.getX(), mutable.getY() - 1, mutable.getZ());
                                        renderLevel(CLIENT, camera, world, mutable, downMutable, (Byte) objectEntry.getValue(), collisionContext);
                                    }
                                }
                            }
                        }
                    }
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableTexture();
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    if (smoothLines) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glLineWidth(lineWidth);
                    GL11.glBegin(GL11.GL_LINES);
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                    for (Map.Entry<ChunkPos, Long2ReferenceMap<Object>> entry : CHUNK_MAP.entrySet()) {
                        if (caching && (Mth.abs(entry.getKey().x - playerPosX) > getChunkRange() || Mth.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        for (Long2ReferenceMap.Entry<Object> objectEntry : entry.getValue().long2ReferenceEntrySet()) {
                            if (objectEntry.getValue() instanceof CrossType) {
                                mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
                                if (mutable.closerThan(playerPos, reach)) {
                                    if (frustum == null || platform.isFrustumVisible(frustum, mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                                        int color = objectEntry.getValue() == CrossType.RED ? redColor : objectEntry.getValue() == CrossType.YELLOW ? yellowColor : secondaryColor;
                                        renderCross(camera, world, mutable, color, collisionContext);
                                    }
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
        
        platform.registerClientTick(() -> {
            while (enableOverlay.consumeClick())
                enabled = !enabled;
            
            try {
                ticks++;
                if (CLIENT.player == null || !enabled) {
                    POS.clear();
                    CHUNK_MAP.clear();
                } else {
                    LocalPlayer player = CLIENT.player;
                    ClientLevel world = CLIENT.level;
                    CollisionContext collisionContext = CollisionContext.of(player);
                    
                    if (!caching) {
                        POS.clear();
                        CHUNK_MAP.clear();
                        BlockPos playerPos = player.blockPosition();
                        LayerLightEventListener block = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
                        LayerLightEventListener sky = showNumber ? null : world.getLightEngine().getLayerListener(LightLayer.SKY);
                        BlockPos.MutableBlockPos downPos = new BlockPos.MutableBlockPos();
                        Iterable<BlockPos> iterate = BlockPos.betweenClosed(playerPos.getX() - reach, playerPos.getY() - reach, playerPos.getZ() - reach,
                                playerPos.getX() + reach, playerPos.getY() + reach, playerPos.getZ() + reach);
                        Long2ReferenceMap<Object> map = new Long2ReferenceOpenHashMap<>();
                        CHUNK_MAP.put(new ChunkPos(0, 0), map);
                        for (BlockPos blockPos : iterate) {
                            downPos.set(blockPos.getX(), blockPos.getY() - 1, blockPos.getZ());
                            if (showNumber) {
                                int level = getCrossLevel(blockPos, downPos, world, block, collisionContext);
                                if (level >= 0) {
                                    map.put(blockPos.asLong(), Byte.valueOf((byte) level));
                                }
                            } else {
                                CrossType type = getCrossType(blockPos, downPos, world, block, sky, collisionContext);
                                if (type != CrossType.NONE) {
                                    map.put(blockPos.asLong(), type);
                                }
                            }
                        }
                    } else {
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
                        POS.removeIf(pos -> Mth.abs(pos.x - playerPosX) > getChunkRange() || Mth.abs(pos.z - playerPosZ) > getChunkRange());
                        for (int k = 0; k < 2; k++) {
                            if (!POS.isEmpty()) {
                                ChunkPos pos = POS.stream().min(Comparator.comparingDouble(value -> {
                                    int i = Math.abs(value.x - playerPosX);
                                    int j = Math.abs(value.z - playerPosZ);
                                    return i * i + j * j;
                                })).get();
                                POS.remove(pos);
                                EXECUTOR.submit(() -> {
                                    try {
                                        calculateChunk(world.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false), world, pos, collisionContext);
                                    } catch (Throwable throwable) {
                                        LogManager.getLogger().throwing(throwable);
                                    }
                                });
                            }
                        }
                        if (ticks % 50 == 0) {
                            CHUNK_MAP.entrySet().removeIf(pos -> Mth.abs(pos.getKey().x - playerPosX) > getChunkRange() * 2 || Mth.abs(pos.getKey().z - playerPosZ) > getChunkRange() * 2);
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
        return Math.max(Mth.ceil(reach / 16f), 1);
    }
    
    private static void calculateChunk(LevelChunk chunk, Level world, ChunkPos chunkPos, CollisionContext entityContext) {
        if (world != null && chunk != null) {
            Long2ReferenceMap<Object> map = new Long2ReferenceOpenHashMap<>();
            LayerLightEventListener block = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
            LayerLightEventListener sky = showNumber ? null : world.getLightEngine().getLayerListener(LightLayer.SKY);
            for (BlockPos pos : BlockPos.betweenClosed(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), 256, chunkPos.getMaxBlockZ())) {
                BlockPos down = pos.below();
                if (showNumber) {
                    int level = getCrossLevel(pos, down, chunk, block, entityContext);
                    if (level >= 0) {
                        map.put(pos.asLong(), Byte.valueOf((byte) level));
                    }
                } else {
                    CrossType type = getCrossType(pos, down, chunk, block, sky, entityContext);
                    if (type != CrossType.NONE) {
                        map.put(pos.asLong(), type);
                    }
                }
            }
            CHUNK_MAP.put(chunkPos, map);
        } else {
            CHUNK_MAP.remove(chunkPos);
        }
    }
    
    public static CrossType getCrossType(BlockPos pos, BlockPos down, BlockGetter world, LayerLightEventListener block, LayerLightEventListener sky, CollisionContext entityContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, entityContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return CrossType.NONE;
        // Check if the outline is full
        if (Block.isFaceFull(upperCollisionShape, Direction.UP))
            return CrossType.NONE;
        // TODO: Not to hard code no redstone
        if (blockUpperState.isSignalSource())
            return CrossType.NONE;
        // Check if the collision has a bump
        if (upperCollisionShape.max(Direction.Axis.Y) > 0)
            return CrossType.NONE;
        if (blockUpperState.getBlock().is(BlockTags.RAILS))
            return CrossType.NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.isValidSpawn(world, down, TESTING_ENTITY_TYPE.get()))
            return CrossType.NONE;
        int blockLightLevel = block.getLightValue(pos);
        int skyLightLevel = sky.getLightValue(pos);
        if (blockLightLevel > higherCrossLevel)
            return CrossType.NONE;
        if (skyLightLevel > higherCrossLevel)
            return CrossType.YELLOW;
        return lowerCrossLevel >= 0 && blockLightLevel > lowerCrossLevel ? CrossType.SECONDARY : CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, BlockGetter world, LayerLightEventListener view, CollisionContext collisionContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape collisionShape = blockBelowState.getCollisionShape(world, down, collisionContext);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, collisionContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return -1;
        if (!blockBelowState.getFluidState().isEmpty())
            return -1;
        if (blockBelowState.isAir())
            return -1;
        if (Block.isFaceFull(upperCollisionShape, Direction.DOWN))
            return -1;
        return view.getLightValue(pos);
    }
    
    public static void renderCross(Camera camera, Level world, BlockPos pos, int color, CollisionContext collisionContext) {
        double d0 = camera.getPosition().x;
        double d1 = camera.getPosition().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, collisionContext);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.max(Direction.Axis.Y);
        double d2 = camera.getPosition().z;
        
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
    public static void renderLevel(Minecraft client, Camera camera, Level world, BlockPos pos, BlockPos down, int level, CollisionContext collisionContext) {
        String text = String.valueOf(level);
        Font textRenderer_1 = client.font;
        double double_4 = camera.getPosition().x;
        double double_5 = camera.getPosition().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getShape(world, down, collisionContext);
        if (!upperOutlineShape.isEmpty())
            double_5 += 1 - upperOutlineShape.max(Direction.Axis.Y);
        double double_6 = camera.getPosition().z;
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (pos.getX() + 0.5f - double_4), (float) (pos.getY() - double_5) + 0.005f, (float) (pos.getZ() + 0.5f - double_6));
        RenderSystem.rotatef(90, 1, 0, 0);
        RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        RenderSystem.scalef(-size, -size, size);
        float float_3 = (float) (-textRenderer_1.width(text)) / 2.0F + 0.4f;
        RenderSystem.enableAlphaTest();
        MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        textRenderer_1.drawInBatch(text, float_3, -3.5f, level > higherCrossLevel ? 0xff042404 : (lowerCrossLevel >= 0 && level > lowerCrossLevel ? 0xff0066ff : 0xff731111), false, Transformation.identity().getMatrix(), immediate, false, 0, 15728880);
        immediate.endBatch();
        RenderSystem.popMatrix();
    }
    
    public static void loadConfig(File file) {
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
    
    public static void saveConfig(File file) throws IOException {
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
    
    private static KeyMapping createKeyBinding(ResourceLocation id, InputConstants.Type type, int code, String category) {
        return new KeyMapping("key." + id.getNamespace() + "." + id.getPath(), type, code, category);
    }
    
    private enum CrossType {
        YELLOW,
        RED,
        SECONDARY,
        NONE
    }
}

package me.shedaniel.lightoverlay.common;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import me.shedaniel.architectury.event.events.GuiEvent;
import me.shedaniel.architectury.event.events.client.ClientTickEvent;
import me.shedaniel.architectury.platform.Platform;
import me.shedaniel.architectury.registry.KeyBindings;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class LightOverlay {
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
    public static boolean mushroom = false;
    public static boolean useListWhileCaching = true;
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
    private static final Set<CubicChunkPos> POS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<CubicChunkPos> CALCULATING_POS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<CubicChunkPos, ChunkData> CHUNK_MAP = Maps.newConcurrentMap();
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static long ticks = 0;
    
    public static void register() {
        // Load Config
        configFile = new File(Platform.getConfigFolder().toFile(), "lightoverlay.properties");
        loadConfig(configFile);
        
        enableOverlay = createKeyBinding(ENABLE_OVERLAY_KEYBIND, InputConstants.Type.KEYSYM, 296, KEYBIND_CATEGORY);
        KeyBindings.registerKeyBinding(enableOverlay);
        
        registerDebugRenderer(() -> {
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
                    for (Map.Entry<CubicChunkPos, ChunkData> entry : CHUNK_MAP.entrySet()) {
                        if (caching && (Mth.abs(entry.getKey().x - playerPosX) > getChunkRange() || Mth.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        for (Long2ByteMap.Entry objectEntry : entry.getValue().data().long2ByteEntrySet()) {
                            mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
                            if (mutable.closerThan(playerPos, reach)) {
                                if (frustum == null || isFrustumVisible(frustum, mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                                    downMutable.set(mutable.getX(), mutable.getY() - 1, mutable.getZ());
                                    renderLevel(CLIENT, camera, world, mutable, downMutable, objectEntry.getByteValue(), collisionContext);
                                }
                            }
                        }
                    }
                    RenderSystem.enableDepthTest();
                } else {
                    boolean useList = useListWhileCaching && caching;
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableTexture();
                    RenderSystem.enableBlend();
                    RenderSystem.enableCull();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    if (smoothLines) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glLineWidth(lineWidth);
                    if (!useList) GL11.glBegin(GL11.GL_LINES);
                    
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                    
                    if (useList) GL11.glTranslated(-camera.getPosition().x, -camera.getPosition().y + .01D, -camera.getPosition().z);
                    for (Map.Entry<CubicChunkPos, ChunkData> entry : CHUNK_MAP.entrySet()) {
                        CubicChunkPos chunkPos = entry.getKey();
                        if (caching && (Mth.abs(chunkPos.x - playerPosX) > getChunkRange() || Mth.abs(chunkPos.z - playerPosZ) > getChunkRange())) {
                            continue;
                        }
                        
                        if (useList) {
                            if (frustum == null || isFrustumVisible(frustum, chunkPos.getMinBlockX(), chunkPos.getMinBlockY(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), chunkPos.getMaxBlockY(), chunkPos.getMaxBlockZ())) {
                                entry.getValue().renderList(world, collisionContext);
                            }
                        } else {
                            for (Long2ByteMap.Entry objectEntry : entry.getValue().data().long2ByteEntrySet()) {
                                byte crossType = objectEntry.getByteValue();
                                mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
                                if (mutable.closerThan(playerPos, reach)) {
                                    if (frustum == null || isFrustumVisible(frustum, mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getX() + 1, mutable.getX() + 1)) {
                                        int color = crossType == CROSS_RED ? redColor : crossType == CROSS_YELLOW ? yellowColor : secondaryColor;
                                        renderCross(camera, world, mutable, color, collisionContext);
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!useList) GL11.glEnd();
                    RenderSystem.disableBlend();
                    RenderSystem.enableTexture();
                    if (smoothLines) GL11.glDisable(GL11.GL_LINE_SMOOTH);
                }
            }
        });
        
        GuiEvent.DEBUG_TEXT_LEFT.register(list -> {
            if (enabled) {
                if (caching) {
                    list.add(String.format("[Light Overlay] Chunks to queue: %02d", POS.size()));
                } else {
                    list.add("[Light Overlay] Enabled");
                }
            } else {
                list.add("[Light Overlay] Disabled");
            }
        });
        ClientTickEvent.CLIENT_POST.register(LightOverlay::tick);
    }
    
    private static void processChunk(CubicChunkPos pos, int playerPosX, int playerPosY, int playerPosZ, CollisionContext context) {
        CALCULATING_POS.remove(pos);
        if (Mth.abs(pos.x - playerPosX) > getChunkRange() || Mth.abs(pos.y - playerPosY) > getChunkRange() || Mth.abs(pos.z - playerPosZ) > getChunkRange() || POS.contains(pos)) {
            return;
        }
        try {
            calculateChunk(CLIENT.level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false), CLIENT.level, pos, context);
        } catch (Throwable throwable) {
            LogManager.getLogger().throwing(throwable);
        }
    }
    
    public static void queueChunkAndNear(CubicChunkPos pos) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    queueChunk(new CubicChunkPos(pos.x + xOffset, pos.y + yOffset, pos.z + zOffset));
                }
            }
        }
    }
    
    public static void queueChunk(CubicChunkPos pos) {
        if (enabled && caching && !CALCULATING_POS.contains(pos)) {
            POS.add(pos);
        }
    }
    
    public static int getChunkRange() {
        return Math.max(Mth.ceil(reach / 16f), 1);
    }
    
    private static void calculateChunk(LevelChunk chunk, Level world, CubicChunkPos chunkPos, CollisionContext collisionContext) {
        if (world != null && chunk != null) {
            ChunkData chunkData = new ChunkData();
            LayerLightEventListener block = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
            LayerLightEventListener sky = showNumber ? null : world.getLightEngine().getLayerListener(LightLayer.SKY);
            for (BlockPos pos : BlockPos.betweenClosed(chunkPos.getMinBlockX(), chunkPos.getMinBlockY(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), chunkPos.getMaxBlockY(), chunkPos.getMaxBlockZ())) {
                BlockPos down = pos.below();
                if (showNumber) {
                    int level = getCrossLevel(pos, down, chunk, block, collisionContext);
                    if (level >= 0) {
                        chunkData.data().put(pos.asLong(), (byte) level);
                    }
                } else {
                    Biome biome = !mushroom ? world.getBiome(pos) : null;
                    byte type = getCrossType(pos, biome, down, chunk, block, sky, collisionContext);
                    if (type != CROSS_NONE) {
                        chunkData.data().put(pos.asLong(), type);
                    }
                }
            }
            CHUNK_MAP.put(chunkPos, chunkData);
        } else {
            ChunkData data = CHUNK_MAP.remove(chunkPos);
            
            if (data != null) {
                data.close();
            }
        }
    }
    
    public static byte getCrossType(BlockPos pos, Biome biome, BlockPos down, BlockGetter world, LayerLightEventListener block, LayerLightEventListener sky, CollisionContext entityContext) {
        BlockState blockBelowState = world.getBlockState(down);
        BlockState blockUpperState = world.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(world, pos, entityContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return CROSS_NONE;
        // Check if the outline is full
        if (Block.isFaceFull(upperCollisionShape, Direction.UP))
            return CROSS_NONE;
        // TODO: Not to hard code no redstone
        if (blockUpperState.isSignalSource())
            return CROSS_NONE;
        // Check if the collision has a bump
        if (upperCollisionShape.max(Direction.Axis.Y) > 0)
            return CROSS_NONE;
        if (blockUpperState.getBlock().is(BlockTags.RAILS))
            return CROSS_NONE;
        // Check block state allow spawning (excludes bedrock and barriers automatically)
        if (!blockBelowState.isValidSpawn(world, down, TESTING_ENTITY_TYPE.get()))
            return CROSS_NONE;
        if (!mushroom && Biome.BiomeCategory.MUSHROOM == biome.getBiomeCategory())
            return CROSS_NONE;
        int blockLightLevel = block.getLightValue(pos);
        int skyLightLevel = sky.getLightValue(pos);
        if (blockLightLevel > higherCrossLevel)
            return CROSS_NONE;
        if (skyLightLevel > higherCrossLevel)
            return CROSS_YELLOW;
        return lowerCrossLevel >= 0 && blockLightLevel > lowerCrossLevel ? CROSS_SECONDARY : CROSS_RED;
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
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y - .005D;
        double blockOffset = 0;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, collisionContext);
        if (!upperOutlineShape.isEmpty())
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        double cameraZ = camera.getPosition().z;
        
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        RenderSystem.color4f(red / 255f, green / 255f, blue / 255f, 1f);
        GL11.glVertex3d(x + .01 - cameraX, y - cameraY + blockOffset, z + .01 - cameraZ);
        GL11.glVertex3d(x - .01 + 1 - cameraX, y - cameraY + blockOffset, z - .01 + 1 - cameraZ);
        GL11.glVertex3d(x - .01 + 1 - cameraX, y - cameraY + blockOffset, z + .01 - cameraZ);
        GL11.glVertex3d(x + .01 - cameraX, y - cameraY + blockOffset, z - .01 + 1 - cameraZ);
    }
    
    @SuppressWarnings("deprecation")
    public static void renderLevel(Minecraft client, Camera camera, Level world, BlockPos pos, BlockPos down, byte level, CollisionContext collisionContext) {
        String text = String.valueOf(level);
        Font font = client.font;
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getShape(world, down, collisionContext);
        if (!upperOutlineShape.isEmpty())
            cameraY += 1 - upperOutlineShape.max(Direction.Axis.Y);
        double cameraZ = camera.getPosition().z;
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (pos.getX() + 0.5f - cameraX), (float) (pos.getY() - cameraY) + 0.005f, (float) (pos.getZ() + 0.5f - cameraZ));
        RenderSystem.rotatef(90, 1, 0, 0);
        RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        RenderSystem.scalef(-size, -size, size);
        float float_3 = (float) (-font.width(text)) / 2.0F + 0.4f;
        RenderSystem.enableAlphaTest();
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        font.drawInBatch(text, float_3, -3.5f, level > higherCrossLevel ? 0xff042404 : (lowerCrossLevel >= 0 && level > lowerCrossLevel ? 0xff0066ff : 0xff731111), false, Transformation.identity().getMatrix(), source, false, 0, 15728880);
        source.endBatch();
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
            mushroom = ((String) properties.computeIfAbsent("mushroom", a -> "false")).equalsIgnoreCase("true");
            useListWhileCaching = ((String) properties.computeIfAbsent("useListWhileCaching", a -> "true")).equalsIgnoreCase("true");
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
            mushroom = false;
            useListWhileCaching = true;
            try {
                saveConfig(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (secondaryLevel >= crossLevel) System.err.println("[Light Overlay] Secondary Level is higher than Cross Level");
        lowerCrossLevel = Math.min(crossLevel, secondaryLevel);
        higherCrossLevel = Math.max(crossLevel, secondaryLevel);
        for (ChunkData data : CHUNK_MAP.values()) {
            data.close();
        }
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
        fos.write(("mushroom=" + mushroom).getBytes());
        fos.write("\n".getBytes());
        fos.write(("useListWhileCaching=" + useListWhileCaching).getBytes());
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
    
    private static final LazyLoadedValue<MethodHandle> IS_FRUSTUM_VISIBLE = new LazyLoadedValue<>(() -> {
        try {
            return MethodHandles.lookup().findStatic(Class.forName("me.shedaniel.lightoverlay." + Platform.getModLoader() + ".LightOverlayImpl"), "isFrustumVisible",
                    MethodType.methodType(boolean.class, Frustum.class, double.class, double.class, double.class, double.class, double.class, double.class));
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    });
    
    private static boolean isFrustumVisible(Frustum frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        try {
            return (boolean) IS_FRUSTUM_VISIBLE.get().invokeExact(frustum, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    
    private static void registerDebugRenderer(Runnable runnable) {
        try {
            Class.forName("me.shedaniel.lightoverlay." + Platform.getModLoader() + ".LightOverlayImpl").getDeclaredField("debugRenderer").set(null, runnable);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    
    private static void tick(Minecraft minecraft) {
        while (enableOverlay.consumeClick())
            enabled = !enabled;
        
        try {
            ticks++;
            if (CLIENT.player == null || !enabled) {
                POS.clear();
                CALCULATING_POS.clear();
                EXECUTOR.getQueue().clear();
                for (ChunkData data : CHUNK_MAP.values()) {
                    data.close();
                }
                CHUNK_MAP.clear();
            } else {
                LocalPlayer player = CLIENT.player;
                ClientLevel world = CLIENT.level;
                CollisionContext collisionContext = CollisionContext.of(player);
                
                if (!caching) {
                    CALCULATING_POS.clear();
                    POS.clear();
                    for (ChunkData data : CHUNK_MAP.values()) {
                        data.close();
                    }
                    CHUNK_MAP.clear();
                    BlockPos playerPos = player.blockPosition();
                    LayerLightEventListener block = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
                    LayerLightEventListener sky = showNumber ? null : world.getLightEngine().getLayerListener(LightLayer.SKY);
                    BlockPos.MutableBlockPos downPos = new BlockPos.MutableBlockPos();
                    Iterable<BlockPos> iterate = BlockPos.betweenClosed(playerPos.getX() - reach, playerPos.getY() - reach, playerPos.getZ() - reach,
                            playerPos.getX() + reach, playerPos.getY() + reach, playerPos.getZ() + reach);
                    ChunkData chunkData = new ChunkData();
                    CHUNK_MAP.put(new CubicChunkPos(0, 0, 0), chunkData);
                    for (BlockPos blockPos : iterate) {
                        downPos.set(blockPos.getX(), blockPos.getY() - 1, blockPos.getZ());
                        if (showNumber) {
                            int level = getCrossLevel(blockPos, downPos, world, block, collisionContext);
                            if (level >= 0) {
                                chunkData.data().put(blockPos.asLong(), (byte) level);
                            }
                        } else {
                            Biome biome = !mushroom ? world.getBiome(blockPos) : null;
                            byte type = getCrossType(blockPos, biome, downPos, world, block, sky, collisionContext);
                            if (type != CROSS_NONE) {
                                chunkData.data().put(blockPos.asLong(), type);
                            }
                        }
                    }
                } else {
                    int playerPosX = ((int) player.getX()) >> 4;
                    int playerPosY = ((int) player.getY()) >> 4;
                    int playerPosZ = ((int) player.getZ()) >> 4;
                    for (int chunkX = playerPosX - getChunkRange(); chunkX <= playerPosX + getChunkRange(); chunkX++) {
                        for (int chunkY = Math.max(playerPosY - getChunkRange(), 0); chunkY <= playerPosY + getChunkRange() && chunkY <= 15; chunkY++) {
                            for (int chunkZ = playerPosZ - getChunkRange(); chunkZ <= playerPosZ + getChunkRange(); chunkZ++) {
                                if (Mth.abs(chunkX - playerPosX) > getChunkRange() || Mth.abs(chunkY - playerPosY) > getChunkRange() || Mth.abs(chunkZ - playerPosZ) > getChunkRange())
                                    continue;
                                CubicChunkPos chunkPos = new CubicChunkPos(chunkX, chunkY, chunkZ);
                                if (!CHUNK_MAP.containsKey(chunkPos))
                                    queueChunk(chunkPos);
                            }
                        }
                    }
                    for (int p = 0; p < 3; p++) {
                        if (EXECUTOR.getQueue().size() >= Runtime.getRuntime().availableProcessors()) break;
                        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE, d3 = Double.MAX_VALUE;
                        CubicChunkPos c1 = null, c2 = null, c3 = null;
                        synchronized (POS) {
                            Iterator<CubicChunkPos> iterator = POS.iterator();
                            while (iterator.hasNext()) {
                                CubicChunkPos pos = iterator.next();
                                if (Mth.abs(pos.x - playerPosX) > getChunkRange() || Mth.abs(pos.y - playerPosY) > getChunkRange() || Mth.abs(pos.z - playerPosZ) > getChunkRange() || CALCULATING_POS.contains(pos)) {
                                    iterator.remove();
                                } else {
                                    if (isFrustumVisible(frustum, pos.getMinBlockX(), pos.getMinBlockY(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockY(), pos.getMaxBlockZ())) {
                                        int i = Math.abs(pos.x - playerPosX);
                                        int j = Math.abs(pos.y - playerPosY);
                                        int k = Math.abs(pos.z - playerPosZ);
                                        double distance = Math.sqrt(i * i + j * j + k * k);
                                        if (distance < d1) {
                                            d3 = d2;
                                            d2 = d1;
                                            d1 = distance;
                                            c3 = c2;
                                            c2 = c1;
                                            c1 = pos;
                                            iterator.remove();
                                        } else if (distance < d2) {
                                            d3 = d2;
                                            d2 = distance;
                                            c3 = c2;
                                            c2 = pos;
                                            iterator.remove();
                                        } else if (distance < d3) {
                                            d3 = distance;
                                            c3 = pos;
                                            iterator.remove();
                                        }
                                    }
                                }
                            }
                        }
                        CubicChunkPos finalC1 = c1;
                        CubicChunkPos finalC2 = c2;
                        CubicChunkPos finalC3 = c3;
                        if (finalC1 != null) {
                            CALCULATING_POS.add(finalC1);
                            if (finalC2 != null) {
                                CALCULATING_POS.add(finalC2);
                                if (finalC3 != null) {
                                    CALCULATING_POS.add(finalC3);
                                }
                            }
                            EXECUTOR.submit(() -> {
                                int playerPosX1 = ((int) CLIENT.player.getX()) >> 4;
                                int playerPosY1 = ((int) CLIENT.player.getY()) >> 4;
                                int playerPosZ1 = ((int) CLIENT.player.getZ()) >> 4;
                                if (finalC1 != null) processChunk(finalC1, playerPosX1, playerPosY1, playerPosZ1, collisionContext);
                                if (finalC2 != null) processChunk(finalC2, playerPosX1, playerPosY1, playerPosZ1, collisionContext);
                                if (finalC3 != null) processChunk(finalC3, playerPosX1, playerPosY1, playerPosZ1, collisionContext);
                            });
                        }
                    }
                    if (ticks % 50 == 0) {
                        Iterator<Map.Entry<CubicChunkPos, ChunkData>> iterator = CHUNK_MAP.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<CubicChunkPos, ChunkData> entry = iterator.next();
                            if (Mth.abs(entry.getKey().x - playerPosX) > getChunkRange() * 2 || Mth.abs(entry.getKey().y - playerPosY) > getChunkRange() * 2 || Mth.abs(entry.getKey().z - playerPosZ) > getChunkRange() * 2) {
                                entry.getValue().close();
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            LogManager.getLogger().throwing(throwable);
        }
    }
    
    public static final byte CROSS_YELLOW = 0;
    public static final byte CROSS_RED = 1;
    public static final byte CROSS_SECONDARY = 2;
    public static final byte CROSS_NONE = 2;
}

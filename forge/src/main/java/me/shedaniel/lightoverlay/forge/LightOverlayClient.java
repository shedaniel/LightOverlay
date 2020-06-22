package me.shedaniel.lightoverlay.forge;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.TransformationMatrix;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SChunkDataPacket;
import net.minecraft.network.play.server.SMultiBlockChangePacket;
import net.minecraft.network.play.server.SUpdateLightPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
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

public class LightOverlayClient {
    static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
    private static final ResourceLocation ENABLE_OVERLAY_KEYBIND = new ResourceLocation("lightoverlay", "enable_overlay");
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
    static File configFile = new File(new File(Minecraft.getInstance().gameDir, "config"), "lightoverlay.properties");
    private static final KeyBinding ENABLE_OVERLAY;
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
        ENABLE_OVERLAY = registerKeybind(ENABLE_OVERLAY_KEYBIND, InputMappings.Type.KEYSYM, 296, KEYBIND_CATEGORY);
    }
    
    public static void register() {
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityClassification.MONSTER).size(0f, 0f).disableSerialization().build(null);
        MinecraftForge.EVENT_BUS.register(LightOverlayClient.class);
        
        try {
            //noinspection Convert2MethodRef
            DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> LightOverlayCloth.register());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static CrossType getCrossType(BlockPos pos, BlockPos down, IBlockReader reader, IWorldLightListener block, IWorldLightListener sky, ISelectionContext selectionContext) {
        BlockState blockBelowState = reader.getBlockState(down);
        BlockState blockUpperState = reader.getBlockState(pos);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(reader, pos, selectionContext);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return CrossType.NONE;
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
        if (!blockBelowState.canEntitySpawn(reader, down, testingEntityType))
            return CrossType.NONE;
        int blockLightLevel = block.getLightFor(pos);
        int skyLightLevel = sky.getLightFor(pos);
        if (blockLightLevel > higherCrossLevel)
            return CrossType.NONE;
        if (skyLightLevel > higherCrossLevel)
            return CrossType.YELLOW;
        return lowerCrossLevel >= 0 && blockLightLevel > lowerCrossLevel ? CrossType.SECONDARY : CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, IBlockReader reader, IWorldLightListener light, ISelectionContext context) {
        BlockState blockBelowState = reader.getBlockState(down);
        BlockState blockUpperState = reader.getBlockState(pos);
        VoxelShape collisionShape = blockBelowState.getCollisionShape(reader, down, context);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(reader, pos, context);
        if (!underwater && !blockUpperState.getFluidState().isEmpty())
            return -1;
        if (!blockBelowState.getFluidState().isEmpty())
            return -1;
        if (blockBelowState.isAir(reader, down))
            return -1;
        if (Block.doesSideFillSquare(upperCollisionShape, Direction.DOWN))
            return -1;
        return light.getLightFor(pos);
    }
    
    public static void renderCross(ActiveRenderInfo info, World world, BlockPos pos, int color, ISelectionContext context) {
        double d0 = info.getProjectedView().x;
        double d1 = info.getProjectedView().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, context);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getEnd(Direction.Axis.Y);
        double d2 = info.getProjectedView().z;
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
    
    public static void renderLevel(Minecraft minecraft, ActiveRenderInfo info, World world, BlockPos pos, BlockPos down, int level, ISelectionContext context) {
        String string_1 = String.valueOf(level);
        FontRenderer fontRenderer = minecraft.fontRenderer;
        double double_4 = info.getProjectedView().x;
        double double_5 = info.getProjectedView().y;
        VoxelShape upperOutlineShape = world.getBlockState(down).getShape(world, down, context);
        if (!upperOutlineShape.isEmpty())
            double_5 += 1 - upperOutlineShape.getEnd(Direction.Axis.Y);
        double double_6 = info.getProjectedView().z;
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (pos.getX() + 0.5f - double_4), (float) (pos.getY() - double_5) + 0.005f, (float) (pos.getZ() + 0.5f - double_6));
        RenderSystem.rotatef(90, 1, 0, 0);
        RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
        float size = 0.07F;
        RenderSystem.scalef(-size, -size, size);
        float float_3 = (float) (-fontRenderer.getStringWidth(string_1)) / 2.0F + 0.4f;
        RenderSystem.enableAlphaTest();
        IRenderTypeBuffer.Impl vertexConsumerProvider$Immediate_1 = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        fontRenderer.renderString(string_1, float_3, -3.5f, level > higherCrossLevel ? 0xff042404 : (lowerCrossLevel >= 0 && level > lowerCrossLevel ? 0xff0066ff : 0xff731111), false, TransformationMatrix.identity().getMatrix(), vertexConsumerProvider$Immediate_1, false, 0, 15728880);
        vertexConsumerProvider$Immediate_1.finish();
        RenderSystem.popMatrix();
    }
    
    @SubscribeEvent(receiveCanceled = true)
    public static void handleInput(InputEvent.KeyInputEvent event) {
        if (ENABLE_OVERLAY.isPressed())
            enabled = !enabled;
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
    
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                ticks++;
                if (minecraft.player == null || !enabled) {
                    POS.clear();
                    CHUNK_MAP.clear();
                } else {
                    if (!caching) {
                        POS.clear();
                        CHUNK_MAP.clear();
                        ClientPlayerEntity player = minecraft.player;
                        ClientWorld world = minecraft.world;
                        BlockPos playerPos = player.getPosition();
                        ISelectionContext entityContext = ISelectionContext.forEntity(player);
                        IWorldLightListener block = world.getLightManager().getLightEngine(LightType.BLOCK);
                        IWorldLightListener sky = showNumber ? null : world.getLightManager().getLightEngine(LightType.SKY);
                        BlockPos.Mutable downPos = new BlockPos.Mutable();
                        Iterable<BlockPos> iterate = BlockPos.getAllInBoxMutable(playerPos.getX() - reach, playerPos.getY() - reach, playerPos.getZ() - reach,
                                playerPos.getX() + reach, playerPos.getY() + reach, playerPos.getZ() + reach);
                        HashMap<Long, Object> map = Maps.newHashMap();
                        CHUNK_MAP.put(new ChunkPos(0, 0), map);
                        for (BlockPos blockPos : iterate) {
                            downPos.setPos(blockPos.getX(), blockPos.getY() - 1, blockPos.getZ());
                            if (showNumber) {
                                int level = getCrossLevel(blockPos, downPos, world, block, entityContext);
                                if (level >= 0) {
                                    map.put(blockPos.toLong(), level);
                                }
                            } else {
                                CrossType type = getCrossType(blockPos, downPos, world, block, sky, entityContext);
                                if (type != CrossType.NONE) {
                                    map.put(blockPos.toLong(), type);
                                }
                            }
                        }
                    } else {
                        ClientPlayerEntity player = minecraft.player;
                        ClientWorld world = minecraft.world;
                        ISelectionContext selectionContext = ISelectionContext.forEntity(player);
                        Vec3d[] playerPos = {null};
                        int playerPosX = ((int) player.getPosX()) >> 4;
                        int playerPosZ = ((int) player.getPosZ()) >> 4;
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
                                playerPos[0] = player.getPositionVec();
                            }
                            ChunkPos pos = POS.stream().min(Comparator.comparingDouble(value -> value.getBlock(8, 0, 8).distanceSq(playerPos[0].x, 0, playerPos[0].z, false))).get();
                            EXECUTOR.submit(() -> {
                                if (MathHelper.abs(pos.x - playerPosX) <= getChunkRange() && MathHelper.abs(pos.z - playerPosZ) <= getChunkRange()) {
                                    calculateChunk(world.getChunkProvider().getChunk(pos.x, pos.z, ChunkStatus.FULL, false), world, pos, selectionContext);
                                } else {
                                    CHUNK_MAP.remove(pos);
                                }
                            });
                            POS.remove(pos);
                        }
                        if (ticks % 50 == 0) {
                            Iterator<Map.Entry<ChunkPos, Map<Long, Object>>> chunkMapIterator = CHUNK_MAP.entrySet().iterator();
                            while (chunkMapIterator.hasNext()) {
                                Map.Entry<ChunkPos, Map<Long, Object>> pos = chunkMapIterator.next();
                                if (MathHelper.abs(pos.getKey().x - playerPosX) > getChunkRange() * 2 || MathHelper.abs(pos.getKey().z - playerPosZ) > getChunkRange() * 2) {
                                    chunkMapIterator.remove();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogManager.getLogger().throwing(e);
            }
        }
    }
    
    private static void calculateChunk(Chunk chunk, World world, ChunkPos chunkPos, ISelectionContext selectionContext) {
        Map<Long, Object> map = Maps.newHashMap();
        if (chunk != null) {
            IWorldLightListener block = chunk.getWorldLightManager().getLightEngine(LightType.BLOCK);
            IWorldLightListener sky = showNumber ? null : chunk.getWorldLightManager().getLightEngine(LightType.SKY);
            for (BlockPos pos : BlockPos.getAllInBoxMutable(chunkPos.getXStart(), 0, chunkPos.getZStart(), chunkPos.getXEnd(), 256, chunkPos.getZEnd())) {
                BlockPos down = pos.down();
                if (showNumber) {
                    int level = LightOverlayClient.getCrossLevel(pos, down, chunk, block, selectionContext);
                    if (level >= 0) {
                        map.put(pos.toLong(), level);
                    }
                } else {
                    Biome biome = world.getBiomeManager().getBiome(pos);
                    if (biome.getSpawningChance() > 0 && !biome.getSpawns(EntityClassification.MONSTER).isEmpty()) {
                        CrossType type = LightOverlayClient.getCrossType(pos, down, chunk, block, sky, selectionContext);
                        if (type != CrossType.NONE) {
                            map.put(pos.toLong(), type);
                        }
                    }
                }
            }
        }
        CHUNK_MAP.put(chunkPos, map);
    }
    
    public static void renderWorldLast() {
        if (LightOverlayClient.enabled) {
            RenderSystem.pushMatrix();
            Minecraft client = Minecraft.getInstance();
            ClientPlayerEntity playerEntity = client.player;
            int playerPosX = ((int) playerEntity.getPosX()) >> 4;
            int playerPosZ = ((int) playerEntity.getPosZ()) >> 4;
            ISelectionContext selectionContext = ISelectionContext.forEntity(playerEntity);
            World world = client.world;
            BlockPos playerPos = playerEntity.getPosition();
            ActiveRenderInfo info = client.gameRenderer.getActiveRenderInfo();
            if (showNumber) {
                RenderSystem.enableTexture();
                RenderSystem.depthMask(true);
                BlockPos.Mutable mutable = new BlockPos.Mutable();
                for (Map.Entry<ChunkPos, Map<Long, Object>> entry : CHUNK_MAP.entrySet()) {
                    if (caching && (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                        continue;
                    }
                    for (Map.Entry<Long, Object> objectEntry : entry.getValue().entrySet()) {
                        if (objectEntry.getValue() instanceof Integer) {
                            mutable.setPos(BlockPos.unpackX(objectEntry.getKey()), BlockPos.unpackY(objectEntry.getKey()), BlockPos.unpackZ(objectEntry.getKey()));
                            if (mutable.withinDistance(playerPos, reach)) {
                                BlockPos down = mutable.down();
                                LightOverlayClient.renderLevel(client, info, world, mutable, down, (Integer) objectEntry.getValue(), selectionContext);
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
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                if (smoothLines) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(lineWidth);
                GL11.glBegin(GL11.GL_LINES);
                BlockPos.Mutable mutable = new BlockPos.Mutable();
                for (Map.Entry<ChunkPos, Map<Long, Object>> entry : CHUNK_MAP.entrySet()) {
                    if (caching && (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange())) {
                        continue;
                    }
                    for (Map.Entry<Long, Object> objectEntry : entry.getValue().entrySet()) {
                        if (objectEntry.getValue() instanceof CrossType) {
                            mutable.setPos(BlockPos.unpackX(objectEntry.getKey()), BlockPos.unpackY(objectEntry.getKey()), BlockPos.unpackZ(objectEntry.getKey()));
                            if (mutable.withinDistance(playerPos, reach)) {
                                BlockPos down = mutable.down();
                                int color = objectEntry.getValue() == CrossType.RED ? redColor : objectEntry.getValue() == CrossType.YELLOW ? yellowColor : secondaryColor;
                                renderCross(info, world, mutable, color, selectionContext);
                            }
                        }
                    }
                }
                GL11.glEnd();
                RenderSystem.disableBlend();
                RenderSystem.enableTexture();
                if (smoothLines) GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
            RenderSystem.popMatrix();
        }
    }
    
    private static KeyBinding registerKeybind(ResourceLocation resourceLocation, InputMappings.Type type, int keyCode, String category) {
        KeyBinding keyBinding = new KeyBinding("key." + resourceLocation.getNamespace() + "." + resourceLocation.getPath(), KeyConflictContext.IN_GAME, KeyModifier.NONE, type, keyCode, category);
        ClientRegistry.registerKeyBinding(keyBinding);
        return keyBinding;
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
    
    public static void processPacket(IPacket<?> packet) {
        if (packet instanceof SChangeBlockPacket) {
            LightOverlayClient.queueChunkAndNear(new ChunkPos(((SChangeBlockPacket) packet).getPos()));
        } else if (packet instanceof SChunkDataPacket) {
            LightOverlayClient.queueChunkAndNear(new ChunkPos(((SChunkDataPacket) packet).getChunkX(), ((SChunkDataPacket) packet).getChunkZ()));
        } else if (packet instanceof SMultiBlockChangePacket) {
            ChunkPos chunkPos = ObfuscationReflectionHelper.getPrivateValue(SMultiBlockChangePacket.class, (SMultiBlockChangePacket) packet, "field_148925_b");
            LightOverlayClient.queueChunkAndNear(new ChunkPos(chunkPos.x, chunkPos.z));
        } else if (packet instanceof SUpdateLightPacket) {
            LightOverlayClient.queueChunkAndNear(new ChunkPos(((SUpdateLightPacket) packet).getChunkX(), ((SUpdateLightPacket) packet).getChunkZ()));
        }
    }
    
    private enum CrossType {
        YELLOW,
        RED,
        SECONDARY,
        NONE
    }
}

package me.shedaniel.lightoverlay;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
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
    private static final String KEYBIND_CATEGORY = "key.lightoverlay-forge.category";
    private static final ResourceLocation ENABLE_OVERLAY_KEYBIND = new ResourceLocation("lightoverlay-forge", "enable_overlay");
    private static final ResourceLocation INCREASE_REACH_KEYBIND = new ResourceLocation("lightoverlay-forge", "increase_reach");
    private static final ResourceLocation DECREASE_REACH_KEYBIND = new ResourceLocation("lightoverlay-forge", "decrease_reach");
    private static final ResourceLocation INCREASE_LINE_WIDTH_KEYBIND = new ResourceLocation("lightoverlay-forge", "increase_line_width");
    private static final ResourceLocation DECREASE_LINE_WIDTH_KEYBIND = new ResourceLocation("lightoverlay-forge", "decrease_line_width");
    static int reach = 12;
    static int crossLevel = 7;
    static boolean showNumber = false;
    static EntityType<Entity> testingEntityType;
    static float lineWidth = 1.0F;
    static int yellowColor = 0xFFFF00, redColor = 0xFF0000;
    static File configFile = new File(new File(Minecraft.getInstance().gameDir, "config"), "lightoverlay.properties");
    private static KeyBinding enableOverlay, increaseReach, decreaseReach, increaseLineWidth, decreaseLineWidth;
    private static boolean enabled = false;
    private static int threadNumber = 0;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread thread = new Thread(r, "light-overlay-" + threadNumber++);
        thread.setDaemon(true);
        return thread;
    });
    private static final List<ChunkPos> POS = Lists.newCopyOnWriteArrayList();
    private static final Map<ChunkPos, Map<Long, Object>> CHUNK_MAP = Maps.newConcurrentMap();
    private static long ticks = 0;
    
    public static void register() {
        // Load Config
        loadConfig(configFile);
        
        // Setup
        testingEntityType = EntityType.Builder.create(EntityClassification.MONSTER).size(0f, 0f).disableSerialization().build(null);
        enableOverlay = registerKeybind(ENABLE_OVERLAY_KEYBIND, InputMappings.Type.KEYSYM, 296, KEYBIND_CATEGORY);
        increaseReach = registerKeybind(INCREASE_REACH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        decreaseReach = registerKeybind(DECREASE_REACH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        increaseLineWidth = registerKeybind(INCREASE_LINE_WIDTH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
        decreaseLineWidth = registerKeybind(DECREASE_LINE_WIDTH_KEYBIND, InputMappings.Type.KEYSYM, -1, KEYBIND_CATEGORY);
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
        if (!blockBelowState.canEntitySpawn(reader, down, testingEntityType))
            return CrossType.NONE;
        if (block.getLightFor(pos) > crossLevel)
            return CrossType.NONE;
        if (sky.getLightFor(pos) > crossLevel)
            return CrossType.YELLOW;
        return CrossType.RED;
    }
    
    public static int getCrossLevel(BlockPos pos, BlockPos down, IBlockReader reader, IWorldLightListener light, ISelectionContext context) {
        BlockState blockBelowState = reader.getBlockState(down);
        BlockState blockUpperState = reader.getBlockState(pos);
        VoxelShape collisionShape = blockBelowState.getCollisionShape(reader, down, context);
        VoxelShape upperCollisionShape = blockUpperState.getCollisionShape(reader, pos, context);
        if (!blockUpperState.getFluidState().isEmpty())
            return -1;
        if (!blockBelowState.getFluidState().isEmpty())
            return -1;
        if (blockBelowState.isAir(reader, down))
            return -1;
        if (!blockUpperState.isAir(reader, pos))
            return -1;
        return light.getLightFor(pos);
    }
    
    public static void renderCross(ActiveRenderInfo info, Tessellator tessellator, BufferBuilder buffer, World world, BlockPos pos, int color, ISelectionContext context) {
        double d0 = info.getProjectedView().x;
        double d1 = info.getProjectedView().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getShape(world, pos, context);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getEnd(Direction.Axis.Y);
        double d2 = info.getProjectedView().z;
        buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        buffer.pos(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(red, green, blue, 255).endVertex();
        buffer.pos(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(red, green, blue, 255).endVertex();
        buffer.pos(pos.getX() - .01 + 1 - d0, pos.getY() - d1, pos.getZ() + .01 - d2).color(red, green, blue, 255).endVertex();
        buffer.pos(pos.getX() + .01 - d0, pos.getY() - d1, pos.getZ() - .01 + 1 - d2).color(red, green, blue, 255).endVertex();
        tessellator.draw();
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
        fontRenderer.renderString(string_1, float_3, -3.5f, level > crossLevel ? 0xff042404 : 0xff731111, false, TransformationMatrix.identity().getMatrix(), vertexConsumerProvider$Immediate_1, false, 0, 15728880);
        vertexConsumerProvider$Immediate_1.finish();
        RenderSystem.popMatrix();
    }
    
    @SubscribeEvent(receiveCanceled = true)
    public static void handleInput(InputEvent.KeyInputEvent event) {
        if (enableOverlay.isPressed())
            enabled = !enabled;
        if (increaseReach.isPressed()) {
            if (reach < 64)
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
    
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                ticks++;
                if (minecraft.player == null || !enabled) {
                    POS.clear();
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
    
    @SubscribeEvent
    public static void renderWorldLast(RenderWorldLastEvent event) {
        if (LightOverlayClient.enabled) {
            RenderSystem.pushMatrix();
            RenderSystem.loadIdentity();
            RenderSystem.multMatrix(event.getMatrixStack().getLast().getMatrix());
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
                    if (MathHelper.abs(entry.getKey().x - playerPosX) > getChunkRange() || MathHelper.abs(entry.getKey().z - playerPosZ) > getChunkRange()) {
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
                            mutable.setPos(BlockPos.unpackX(objectEntry.getKey()), BlockPos.unpackY(objectEntry.getKey()), BlockPos.unpackZ(objectEntry.getKey()));
                            if (mutable.withinDistance(playerPos, reach)) {
                                BlockPos down = mutable.down();
                                int color = objectEntry.getValue() == CrossType.RED ? redColor : yellowColor;
                                LightOverlayClient.renderCross(info, tessellator, buffer, world, mutable, color, selectionContext);
                            }
                        }
                    }
                }
                RenderSystem.disableBlend();
                RenderSystem.enableTexture();
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
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
    
    public static void processPacket(IPacket<?> packet) {
        if (packet instanceof SChangeBlockPacket) {
            LightOverlayClient.queueChunkAndNear(new ChunkPos(((SChangeBlockPacket) packet).getPos()));
        } else if (packet instanceof SChunkDataPacket) {
            LightOverlayClient.queueChunkAndNear(new ChunkPos(((SChunkDataPacket) packet).getChunkX(), ((SChunkDataPacket) packet).getChunkZ()));
        } else if (packet instanceof SMultiBlockChangePacket) {
            ChunkPos chunkPos = ObfuscationReflectionHelper.getPrivateValue(SMultiBlockChangePacket.class, (SMultiBlockChangePacket) packet, "field_148925_b");
            LightOverlayClient.queueChunkAndNear(new ChunkPos(chunkPos.x, chunkPos.z));
        }
    }
    
    private enum CrossType {
        YELLOW,
        RED,
        NONE
    }
    
}

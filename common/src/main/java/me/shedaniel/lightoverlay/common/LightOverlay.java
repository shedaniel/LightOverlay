package me.shedaniel.lightoverlay.common;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Properties;

public class LightOverlay {
    public static final DecimalFormat FORMAT = new DecimalFormat("#.#");
    public static int reach = 12;
    public static int crossLevel = 7;
    public static int secondaryLevel = -1;
    public static int lowerCrossLevel = -1;
    public static int higherCrossLevel = -1;
    public static boolean showNumber = false;
    public static boolean underwater = false;
    public static float lineWidth = 1.0F;
    public static int yellowColor = 0xFFFF00, redColor = 0xFF0000, secondaryColor = 0x0000FF;
    public static File configFile;
    
    public static KeyMapping enableOverlay;
    public static boolean enabled = false;
    
    public static LightOverlayTicker ticker = new LightOverlayTicker();
    public static LightOverlayRenderer renderer = new LightOverlayRenderer(ticker);
    public static int blocksScanned = 0;
    
    public static void register() {
        // Load Config
        configFile = new File(Platform.getConfigFolder().toFile(), "lightoverlay.properties");
        loadConfig(configFile);
        
        enableOverlay = createKeyBinding(new ResourceLocation("lightoverlay", "enable_overlay"), InputConstants.Type.KEYSYM, 296, "key.lightoverlay.category");
        KeyMappingRegistry.register(enableOverlay);
        
        ClientGuiEvent.DEBUG_TEXT_LEFT.register(list -> {
            if (enabled) {
                    list.add(String.format("[Light Overlay] Chunks to queue: %02d; %d Blocks Scanned", ticker.POS.size(), blocksScanned));
            } else {
                list.add("[Light Overlay] Disabled");
            }
        });
        ClientTickEvent.CLIENT_POST.register(ticker::tick);
    }
    
    
    public static void queueChunkAndNear(CubicChunkPos pos) {
        for (var xOffset = -1; xOffset <= 1; xOffset++) {
            for (var yOffset = -1; yOffset <= 1; yOffset++) {
                for (var zOffset = -1; zOffset <= 1; zOffset++) {
                    queueChunk(new CubicChunkPos(pos.x + xOffset, pos.y + yOffset, pos.z + zOffset));
                }
            }
        }
    }
    
    public static void queueChunk(CubicChunkPos pos) {
        ticker.queueChunk(pos);
    }
    
    public static int getChunkRange() {
        return Math.max(Mth.ceil(reach / 16f), 1);
    }
    
    
    public static void loadConfig(File file) {
        try {
            redColor = 0xFF0000;
            yellowColor = 0xFFFF00;
            secondaryColor = 0x0000FF;
            if (!file.exists() || !file.canRead())
                saveConfig(file);
            var fis = new FileInputStream(file);
            var properties = new Properties();
            properties.load(fis);
            fis.close();
            reach = Integer.parseInt((String) properties.computeIfAbsent("reach", a -> "12"));
            crossLevel = Integer.parseInt((String) properties.computeIfAbsent("crossLevel", a -> "7"));
            secondaryLevel = Integer.parseInt((String) properties.computeIfAbsent("secondaryLevel", a -> "-1"));
            showNumber = ((String) properties.computeIfAbsent("showNumber", a -> "false")).equalsIgnoreCase("true");
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
            showNumber = false;
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
        ticker.CHUNK_MAP.clear();
        ticker.POS.clear();
    }
    
    public static void saveConfig(File file) throws IOException {
        var fos = new FileOutputStream(file, false);
        fos.write("# Light Overlay Config".getBytes());
        fos.write("\n".getBytes());
        fos.write(("reach=" + reach).getBytes());
        fos.write("\n".getBytes());
        fos.write(("crossLevel=" + crossLevel).getBytes());
        fos.write("\n".getBytes());
        fos.write(("secondaryLevel=" + secondaryLevel).getBytes());
        fos.write("\n".getBytes());
        fos.write(("showNumber=" + showNumber).getBytes());
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
    
    public static final byte CROSS_YELLOW = 0;
    public static final byte CROSS_RED = 1;
    public static final byte CROSS_SECONDARY = 2;
    public static final byte CROSS_NONE = 2;
}

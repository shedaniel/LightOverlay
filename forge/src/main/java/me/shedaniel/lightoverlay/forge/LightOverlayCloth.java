package me.shedaniel.lightoverlay.forge;

import me.shedaniel.forge.clothconfig2.api.ConfigBuilder;
import me.shedaniel.forge.clothconfig2.api.ConfigCategory;
import me.shedaniel.forge.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (client, parent) -> {
            ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle("key.lightoverlay.category");
            
            ConfigEntryBuilder eb = builder.getEntryBuilder();
            ConfigCategory general = builder.getOrCreateCategory("config.lightoverlay-forge.general");
            general.addEntry(eb.startIntSlider("config.lightoverlay-forge.reach", LightOverlayClient.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> "Reach: " + integer + " Blocks").setSaveConsumer(integer -> LightOverlayClient.reach = integer).build());
            general.addEntry(eb.startIntSlider("config.lightoverlay-forge.crossLevel", LightOverlayClient.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> "Cross Level: " + integer).setSaveConsumer(integer -> LightOverlayClient.crossLevel = integer).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay-forge.showNumber", LightOverlayClient.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.showNumber = bool).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay-forge.smoothLines", LightOverlayClient.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlayClient.smoothLines = bool).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay-forge.underwater", LightOverlayClient.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.underwater = bool).build());
            general.addEntry(eb.startIntSlider("config.lightoverlay-forge.lineWidth", MathHelper.floor(LightOverlayClient.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> "Light Width: " + LightOverlayClient.FORMAT.format(integer / 100d)).setSaveConsumer(integer -> LightOverlayClient.lineWidth = integer / 100f).build());
            general.addEntry(eb.startColorField("config.lightoverlay-forge.yellowColor", LightOverlayClient.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlayClient.yellowColor = color).build());
            general.addEntry(eb.startColorField("config.lightoverlay-forge.redColor", LightOverlayClient.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlayClient.redColor = color).build());
            
            return builder.setSavingRunnable(() -> {
                try {
                    LightOverlayClient.saveConfig(LightOverlayClient.configFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LightOverlayClient.loadConfig(LightOverlayClient.configFile);
            }).build();
        });
    }
}

package me.shedaniel.lightoverlay.forge;

import me.shedaniel.forge.clothconfig2.api.ConfigBuilder;
import me.shedaniel.forge.clothconfig2.api.ConfigCategory;
import me.shedaniel.forge.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.forge.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.Optional;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (client, parent) -> {
            ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle("key.lightoverlay.category");
            
            ConfigEntryBuilder eb = builder.getEntryBuilder();
            ConfigCategory general = builder.getOrCreateCategory("config.lightoverlay.general");
            general.addEntry(eb.startTextDescription("§7" + I18n.format("description.lightoverlay.caching")).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay.caching", LightOverlayClient.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.caching = bool).build());
            general.addEntry(eb.startIntSlider("config.lightoverlay.reach", LightOverlayClient.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> "Reach: " + integer + " Blocks").setSaveConsumer(integer -> LightOverlayClient.reach = integer).build());
            IntegerSliderEntry crossLevel = eb.startIntSlider("config.lightoverlay.crossLevel", LightOverlayClient.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> "Cross Level: " + integer).setSaveConsumer(integer -> LightOverlayClient.crossLevel = integer).build();
            general.addEntry(crossLevel);
            general.addEntry(eb.startIntSlider("config.lightoverlay.secondaryLevel", LightOverlayClient.secondaryLevel, -1, 15)
                    .setErrorSupplier(integer -> {
                        if (integer >= 0 && integer >= crossLevel.getValue()) return Optional.of("Secondary Level cannot be higher than Cross Level!");
                        return Optional.empty();
                    }).setDefaultValue(-1).setTextGetter(integer -> integer < 0 ? "Off" : "Level: " + integer).setSaveConsumer(integer -> LightOverlayClient.secondaryLevel = integer).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay.showNumber", LightOverlayClient.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.showNumber = bool).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay.smoothLines", LightOverlayClient.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlayClient.smoothLines = bool).build());
            general.addEntry(eb.startBooleanToggle("config.lightoverlay.underwater", LightOverlayClient.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.underwater = bool).build());
            general.addEntry(eb.startIntSlider("config.lightoverlay.lineWidth", MathHelper.floor(LightOverlayClient.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> "Light Width: " + LightOverlayClient.FORMAT.format(integer / 100d)).setSaveConsumer(integer -> LightOverlayClient.lineWidth = integer / 100f).build());
            general.addEntry(eb.startColorField("config.lightoverlay.yellowColor", LightOverlayClient.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlayClient.yellowColor = color).build());
            general.addEntry(eb.startColorField("config.lightoverlay.redColor", LightOverlayClient.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlayClient.redColor = color).build());
            general.addEntry(eb.startColorField("config.lightoverlay.secondaryColor", LightOverlayClient.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlayClient.secondaryColor = color).build());
            
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

package me.shedaniel.lightoverlay.common;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ClothScreen {
    public static Screen getConfigScreenByCloth(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.translatable("key.lightoverlay.category"));
        
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.lightoverlay.general"));
        general.addEntry(eb.startTextDescription(Component.nullToEmpty("§7" + I18n.get("description.lightoverlay.caching"))).build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("config.lightoverlay.caching"), LightOverlay.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.caching = bool).build());
        general.addEntry(eb.startIntSlider(Component.translatable("config.lightoverlay.reach"), LightOverlay.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> Component.nullToEmpty("Reach: " + integer + " Blocks")).setSaveConsumer(integer -> LightOverlay.reach = integer).build());
        IntegerSliderEntry crossLevel = eb.startIntSlider(Component.translatable("config.lightoverlay.crossLevel"), LightOverlay.crossLevel, 0, 15).setDefaultValue(0).setTextGetter(integer -> Component.nullToEmpty("Cross Level: " + integer)).setSaveConsumer(integer -> LightOverlay.crossLevel = integer).build();
        general.addEntry(crossLevel);
        general.addEntry(eb.startIntSlider(Component.translatable("config.lightoverlay.secondaryLevel"), LightOverlay.secondaryLevel, -1, 15)
              .setDefaultValue(7).setTextGetter(integer -> Component.literal(integer < 0 ? "Off" : "Level: " + integer)).setSaveConsumer(integer -> LightOverlay.secondaryLevel = integer).build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("config.lightoverlay.showNumber"), LightOverlay.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.showNumber = bool).build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("config.lightoverlay.underwater"), LightOverlay.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.underwater = bool).build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("config.lightoverlay.mushroom"), LightOverlay.mushroom).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.mushroom = bool).build());
        general.addEntry(eb.startIntSlider(Component.translatable("config.lightoverlay.lineWidth"), Mth.floor(LightOverlay.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> Component.literal("Light Width: " + LightOverlay.FORMAT.format(integer / 100d))).setSaveConsumer(integer -> LightOverlay.lineWidth = integer / 100f).build());
        general.addEntry(eb.startColorField(Component.translatable("config.lightoverlay.yellowColor"), LightOverlay.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlay.yellowColor = color).build());
        general.addEntry(eb.startColorField(Component.translatable("config.lightoverlay.redColor"), LightOverlay.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlay.redColor = color).build());
        general.addEntry(eb.startColorField(Component.translatable("config.lightoverlay.secondaryColor"), LightOverlay.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlay.secondaryColor = color).build());
        
        return builder.setSavingRunnable(() -> {
            try {
                LightOverlay.saveConfig(LightOverlay.configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LightOverlay.loadConfig(LightOverlay.configFile);
        }).build();
    }
}

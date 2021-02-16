package me.shedaniel.lightoverlay.common;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;

import java.util.Optional;

public class ClothScreen {
    public static Screen getConfigScreenByCloth(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(new TranslatableComponent("key.lightoverlay.category"));
        
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(new TranslatableComponent("config.lightoverlay.general"));
        general.addEntry(eb.startTextDescription(Component.nullToEmpty("§7" + I18n.get("description.lightoverlay.caching"))).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.caching"), LightOverlay.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.caching = bool).build());
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.reach"), LightOverlay.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> Component.nullToEmpty("Reach: " + integer + " Blocks")).setSaveConsumer(integer -> LightOverlay.reach = integer).build());
        IntegerSliderEntry crossLevel = eb.startIntSlider(new TranslatableComponent("config.lightoverlay.crossLevel"), LightOverlay.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> Component.nullToEmpty("Cross Level: " + integer)).setSaveConsumer(integer -> LightOverlay.crossLevel = integer).build();
        general.addEntry(crossLevel);
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.secondaryLevel"), LightOverlay.secondaryLevel, -1, 15)
                .setErrorSupplier(integer -> {
                    if (integer >= 0 && integer >= crossLevel.getValue())
                        return Optional.of(new TextComponent("Secondary Level cannot be higher than Cross Level!"));
                    return Optional.empty();
                }).setDefaultValue(-1).setTextGetter(integer -> new TextComponent(integer < 0 ? "Off" : "Level: " + integer)).setSaveConsumer(integer -> LightOverlay.secondaryLevel = integer).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.showNumber"), LightOverlay.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.showNumber = bool).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.smoothLines"), LightOverlay.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlay.smoothLines = bool).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.underwater"), LightOverlay.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.underwater = bool).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.mushroom"), LightOverlay.mushroom).setDefaultValue(false).setSaveConsumer(bool -> LightOverlay.mushroom = bool).build());
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.lineWidth"), Mth.floor(LightOverlay.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> new TextComponent("Light Width: " + LightOverlay.FORMAT.format(integer / 100d))).setSaveConsumer(integer -> LightOverlay.lineWidth = integer / 100f).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.yellowColor"), LightOverlay.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlay.yellowColor = color).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.redColor"), LightOverlay.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlay.redColor = color).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.secondaryColor"), LightOverlay.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlay.secondaryColor = color).build());
        
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

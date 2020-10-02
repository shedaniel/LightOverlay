package me.shedaniel.lightoverlay.fabric;

import io.github.prospector.modmenu.api.ConfigScreenFactory;
import io.github.prospector.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.lightoverlay.common.LightOverlayCore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;

import java.util.Optional;

public class LOModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::getConfigScreenByCloth;
    }
    
    public Screen getConfigScreenByCloth(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(new TranslatableComponent("key.lightoverlay.category"));
        
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(new TranslatableComponent("config.lightoverlay.general"));
        general.addEntry(eb.startTextDescription(Component.nullToEmpty("§7" + I18n.get("description.lightoverlay.caching"))).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.caching"), LightOverlayCore.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.caching = bool).build());
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.reach"), LightOverlayCore.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> Component.nullToEmpty("Reach: " + integer + " Blocks")).setSaveConsumer(integer -> LightOverlayCore.reach = integer).build());
        IntegerSliderEntry crossLevel = eb.startIntSlider(new TranslatableComponent("config.lightoverlay.crossLevel"), LightOverlayCore.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> Component.nullToEmpty("Cross Level: " + integer)).setSaveConsumer(integer -> LightOverlayCore.crossLevel = integer).build();
        general.addEntry(crossLevel);
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.secondaryLevel"), LightOverlayCore.secondaryLevel, -1, 15)
                .setErrorSupplier(integer -> {
                    if (integer >= 0 && integer >= crossLevel.getValue())
                        return Optional.of(new TextComponent("Secondary Level cannot be higher than Cross Level!"));
                    return Optional.empty();
                }).setDefaultValue(-1).setTextGetter(integer -> new TextComponent(integer < 0 ? "Off" : "Level: " + integer)).setSaveConsumer(integer -> LightOverlayCore.secondaryLevel = integer).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.showNumber"), LightOverlayCore.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.showNumber = bool).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.smoothLines"), LightOverlayCore.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlayCore.smoothLines = bool).build());
        general.addEntry(eb.startBooleanToggle(new TranslatableComponent("config.lightoverlay.underwater"), LightOverlayCore.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.underwater = bool).build());
        general.addEntry(eb.startIntSlider(new TranslatableComponent("config.lightoverlay.lineWidth"), Mth.floor(LightOverlayCore.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> new TextComponent("Light Width: " + LightOverlayCore.FORMAT.format(integer / 100d))).setSaveConsumer(integer -> LightOverlayCore.lineWidth = integer / 100f).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.yellowColor"), LightOverlayCore.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlayCore.yellowColor = color).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.redColor"), LightOverlayCore.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlayCore.redColor = color).build());
        general.addEntry(eb.startColorField(new TranslatableComponent("config.lightoverlay.secondaryColor"), LightOverlayCore.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlayCore.secondaryColor = color).build());
        
        return builder.setSavingRunnable(() -> {
            try {
                LightOverlayCore.saveConfig(LightOverlayCore.configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LightOverlayCore.loadConfig(LightOverlayCore.configFile);
        }).build();
    }
}

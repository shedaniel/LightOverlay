package me.shedaniel.lightoverlay.forge;

import me.shedaniel.clothconfig2.forge.api.ConfigBuilder;
import me.shedaniel.clothconfig2.forge.api.ConfigCategory;
import me.shedaniel.clothconfig2.forge.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.forge.gui.entries.IntegerSliderEntry;
import me.shedaniel.lightoverlay.common.LightOverlayCore;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.Optional;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (client, parent) -> {
            ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(new TranslationTextComponent("key.lightoverlay.category"));
            
            ConfigEntryBuilder eb = builder.entryBuilder();
            ConfigCategory general = builder.getOrCreateCategory(new TranslationTextComponent("config.lightoverlay.general"));
            general.addEntry(eb.startTextDescription(ITextComponent.nullToEmpty("§7" + I18n.get("description.lightoverlay.caching"))).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.caching"), LightOverlayCore.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.caching = bool).build());
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.reach"), LightOverlayCore.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> ITextComponent.nullToEmpty("Reach: " + integer + " Blocks")).setSaveConsumer(integer -> LightOverlayCore.reach = integer).build());
            IntegerSliderEntry crossLevel = eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.crossLevel"), LightOverlayCore.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> ITextComponent.nullToEmpty("Cross Level: " + integer)).setSaveConsumer(integer -> LightOverlayCore.crossLevel = integer).build();
            general.addEntry(crossLevel);
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.secondaryLevel"), LightOverlayCore.secondaryLevel, -1, 15)
                    .setErrorSupplier(integer -> {
                        if (integer >= 0 && integer >= crossLevel.getValue())
                            return Optional.of(ITextComponent.nullToEmpty("Secondary Level cannot be higher than Cross Level!"));
                        return Optional.empty();
                    }).setDefaultValue(-1).setTextGetter(integer -> ITextComponent.nullToEmpty(integer < 0 ? "Off" : "Level: " + integer)).setSaveConsumer(integer -> LightOverlayCore.secondaryLevel = integer).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.showNumber"), LightOverlayCore.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.showNumber = bool).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.smoothLines"), LightOverlayCore.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlayCore.smoothLines = bool).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.underwater"), LightOverlayCore.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayCore.underwater = bool).build());
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.lineWidth"), MathHelper.floor(LightOverlayCore.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> ITextComponent.nullToEmpty("Light Width: " + LightOverlayCore.FORMAT.format(integer / 100d))).setSaveConsumer(integer -> LightOverlayCore.lineWidth = integer / 100f).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.yellowColor"), LightOverlayCore.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlayCore.yellowColor = color).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.redColor"), LightOverlayCore.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlayCore.redColor = color).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.secondaryColor"), LightOverlayCore.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlayCore.secondaryColor = color).build());
            
            return builder.setSavingRunnable(() -> {
                try {
                    LightOverlayCore.saveConfig(LightOverlayCore.configFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LightOverlayCore.loadConfig(LightOverlayCore.configFile);
            }).build();
        });
    }
}

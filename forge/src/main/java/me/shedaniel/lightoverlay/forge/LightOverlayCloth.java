package me.shedaniel.lightoverlay.forge;

import me.shedaniel.clothconfig2.forge.api.ConfigBuilder;
import me.shedaniel.clothconfig2.forge.api.ConfigCategory;
import me.shedaniel.clothconfig2.forge.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.forge.gui.entries.IntegerSliderEntry;
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
            general.addEntry(eb.startTextDescription(ITextComponent.func_241827_a_("§7" + I18n.format("description.lightoverlay.caching"))).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.caching"), LightOverlayClient.caching).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.caching = bool).build());
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.reach"), LightOverlayClient.reach, 1, 64).setDefaultValue(12).setTextGetter(integer -> ITextComponent.func_241827_a_("Reach: " + integer + " Blocks")).setSaveConsumer(integer -> LightOverlayClient.reach = integer).build());
            IntegerSliderEntry crossLevel = eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.crossLevel"), LightOverlayClient.crossLevel, 0, 15).setDefaultValue(7).setTextGetter(integer -> ITextComponent.func_241827_a_("Cross Level: " + integer)).setSaveConsumer(integer -> LightOverlayClient.crossLevel = integer).build();
            general.addEntry(crossLevel);
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.secondaryLevel"), LightOverlayClient.secondaryLevel, -1, 15)
                    .setErrorSupplier(integer -> {
                        if (integer >= 0 && integer >= crossLevel.getValue()) return Optional.of(ITextComponent.func_241827_a_("Secondary Level cannot be higher than Cross Level!"));
                        return Optional.empty();
                    }).setDefaultValue(-1).setTextGetter(integer -> ITextComponent.func_241827_a_(integer < 0 ? "Off" : "Level: " + integer)).setSaveConsumer(integer -> LightOverlayClient.secondaryLevel = integer).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.showNumber"), LightOverlayClient.showNumber).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.showNumber = bool).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.smoothLines"), LightOverlayClient.smoothLines).setDefaultValue(true).setSaveConsumer(bool -> LightOverlayClient.smoothLines = bool).build());
            general.addEntry(eb.startBooleanToggle(new TranslationTextComponent("config.lightoverlay.underwater"), LightOverlayClient.underwater).setDefaultValue(false).setSaveConsumer(bool -> LightOverlayClient.underwater = bool).build());
            general.addEntry(eb.startIntSlider(new TranslationTextComponent("config.lightoverlay.lineWidth"), MathHelper.floor(LightOverlayClient.lineWidth * 100), 100, 700).setDefaultValue(100).setTextGetter(integer -> ITextComponent.func_241827_a_("Light Width: " + LightOverlayClient.FORMAT.format(integer / 100d))).setSaveConsumer(integer -> LightOverlayClient.lineWidth = integer / 100f).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.yellowColor"), LightOverlayClient.yellowColor).setDefaultValue(0xFFFF00).setSaveConsumer(color -> LightOverlayClient.yellowColor = color).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.redColor"), LightOverlayClient.redColor).setDefaultValue(0xFF0000).setSaveConsumer(color -> LightOverlayClient.redColor = color).build());
            general.addEntry(eb.startColorField(new TranslationTextComponent("config.lightoverlay.secondaryColor"), LightOverlayClient.secondaryColor).setDefaultValue(0x0000FF).setSaveConsumer(color -> LightOverlayClient.secondaryColor = color).build());
            
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

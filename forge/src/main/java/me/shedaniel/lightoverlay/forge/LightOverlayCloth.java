package me.shedaniel.lightoverlay.forge;

import me.shedaniel.lightoverlay.common.ClothScreen;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.ModLoadingContext;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class, () -> new ConfigGuiHandler.ConfigGuiFactory((client, parent) -> {
            return ClothScreen.getConfigScreenByCloth(parent);
        }));
    }
}

package me.shedaniel.lightoverlay.neoforge;

import me.shedaniel.lightoverlay.common.ClothScreen;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.ConfigScreenHandler;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> {
            return ClothScreen.getConfigScreenByCloth(parent);
        }));
    }
}

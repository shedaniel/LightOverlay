package me.shedaniel.lightoverlay.forge;

import me.shedaniel.lightoverlay.common.ClothScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> {
            return ClothScreen.getConfigScreenByCloth(parent);
        }));
    }
}

package me.shedaniel.lightoverlay.forge;

import me.shedaniel.lightoverlay.common.ClothScreen;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

public class LightOverlayCloth {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (client, parent) -> {
            return ClothScreen.getConfigScreenByCloth(parent);
        });
    }
}

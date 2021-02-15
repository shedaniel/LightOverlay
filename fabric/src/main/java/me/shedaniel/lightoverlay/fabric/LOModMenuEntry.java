package me.shedaniel.lightoverlay.fabric;

import io.github.prospector.modmenu.api.ConfigScreenFactory;
import io.github.prospector.modmenu.api.ModMenuApi;
import me.shedaniel.lightoverlay.common.ClothScreen;

public class LOModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClothScreen::getConfigScreenByCloth;
    }
}

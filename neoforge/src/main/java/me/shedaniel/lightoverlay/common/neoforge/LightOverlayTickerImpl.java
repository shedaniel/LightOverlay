package me.shedaniel.lightoverlay.common.neoforge;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;

public class LightOverlayTickerImpl {
    public static boolean isMushroom(Holder<Biome> biome) {
        return biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(Tags.Biomes.IS_MUSHROOM);
    }
}

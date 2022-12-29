package me.shedaniel.lightoverlay.common.forge;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class LightOverlayTickerImpl {
    private static final TagKey<Biome> MUSHROOM = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_mushroom"));
    
    public static boolean isMushroom(Holder<Biome> biome) {
        return biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(MUSHROOM);
    }
}

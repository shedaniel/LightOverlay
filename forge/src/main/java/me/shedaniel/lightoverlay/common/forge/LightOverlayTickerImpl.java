package me.shedaniel.lightoverlay.common.forge;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.data.ForgeBiomeTagsProvider;

public class LightOverlayTickerImpl {
    private static final TagKey<Biome> MUSHROOM = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation("forge", "is_mushroom"));
    
    public static boolean isMushroom(Holder<Biome> biome) {
        return biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(MUSHROOM);
    }
}

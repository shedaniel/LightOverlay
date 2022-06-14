package me.shedaniel.lightoverlay.common.fabric;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class LightOverlayTickerImpl {
    private static final TagKey<Biome> MUSHROOM = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation("c", "mushroom"));
    
    public static void populateEntityType(EntityType<Entity> type) {
        type.factory = (entityType, level) -> null;
        type.category = MobCategory.MONSTER;
        type.immuneTo = ImmutableSet.of();
        type.dimensions = EntityDimensions.fixed(0.0F, 0.0F);
    }
    
    public static boolean isMushroom(Holder<Biome> biome) {
        return biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(MUSHROOM);
    }
}

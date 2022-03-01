package me.shedaniel.lightoverlay.common.fabric;

import com.google.common.collect.ImmutableSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class LightOverlayTickerImpl {
    public static void populateEntityType(EntityType<Entity> type) {
        type.factory = (entityType, level) -> null;
        type.category = MobCategory.MONSTER;
        type.immuneTo = ImmutableSet.of();
        type.dimensions = EntityDimensions.fixed(0.0F, 0.0F);
    }
}

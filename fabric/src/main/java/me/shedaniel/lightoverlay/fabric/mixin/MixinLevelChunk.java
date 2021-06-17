package me.shedaniel.lightoverlay.fabric.mixin;

import me.shedaniel.lightoverlay.common.LevelChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelChunk.class)
public class MixinLevelChunk implements LevelChunkAccess {
    @Shadow @Final private LevelChunkSection[] sections;
    
    @Override
    public LevelChunkSection[] lightoverlay_getSections() {
        return sections;
    }
}

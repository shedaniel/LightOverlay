package me.shedaniel.lightoverlay.common;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public class CubicChunkPos {
    public final int x;
    public final int y;
    public final int z;
    
    public CubicChunkPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public CubicChunkPos(long l) {
        this.x = getX(l);
        this.y = getY(l);
        this.z = getZ(l);
    }
    
    public CubicChunkPos(BlockPos blockPos) {
        this.x = blockPos.getX() >> 4;
        this.y = blockPos.getY() >> 4;
        this.z = blockPos.getZ() >> 4;
    }
    
    public long toLong() {
        return asLong(this.x, this.y, this.z);
    }
    
    // Allocate 24 bits to x, 12 bits to y, 24 bits to z
    public static long asLong(int x, int y, int z) {
        return ((x & 0xffffffL) << 36) | ((y & 0xfffL) << 24) | (z & 0xffffffL);
    }
    
    public static int getX(long l) {
        return (int) (l >> 36 & 0xffffffL);
    }
    
    public static int getY(long l) {
        return (int) (l >> 24 & 0xfffL);
    }
    
    public static int getZ(long l) {
        return (int) (l & 0xffffffL);
    }
    
    public int getMinBlockX() {
        return this.x << 4;
    }
    
    public int getMinBlockY() {
        return this.y << 4;
    }
    
    public int getMinBlockZ() {
        return this.z << 4;
    }
    
    public int getMaxBlockX() {
        return (this.x << 4) + 15;
    }
    
    public int getMaxBlockY() {
        return (this.y << 4) + 15;
    }
    
    public int getMaxBlockZ() {
        return (this.z << 4) + 15;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CubicChunkPos that = (CubicChunkPos) o;
        return x == that.x && y == that.y && z == that.z;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}

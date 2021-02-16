package me.shedaniel.lightoverlay.common;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.opengl.GL11;

import java.io.Closeable;

import static me.shedaniel.lightoverlay.common.LightOverlay.*;

public class ChunkData implements Closeable {
    private static final IllegalStateException WRONG_TYPE = new IllegalStateException("Wrong type accessed!");
    private Long2ByteMap data;
    private int glListIndex = 0;
    private boolean generatedList = false;
    
    public ChunkData() {
        this(new Long2ByteOpenHashMap());
    }
    
    public ChunkData(Long2ByteMap data) {
        this.data = data;
    }
    
    public Long2ByteMap data() {
        return data;
    }
    
    private void compileList(Level level, CollisionContext collisionContext) {
        generatedList = true;
        
        if (data().isEmpty()) {
            glListIndex = 0;
            return;
        }
        
        glListIndex = GL11.glGenLists(3);
        GL11.glNewList(glListIndex, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_LINES);
        color(redColor);
        
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Long2ByteMap.Entry objectEntry : data().long2ByteEntrySet()) {
            byte crossType = objectEntry.getByteValue();
            mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
            if (crossType == CROSS_RED) {
                renderCross(level, mutable, collisionContext);
            }
        }
        
        GL11.glEnd();
        GL11.glEndList();
        
        GL11.glNewList(glListIndex + 1, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_LINES);
        color(yellowColor);
        
        for (Long2ByteMap.Entry objectEntry : data().long2ByteEntrySet()) {
            byte crossType = objectEntry.getByteValue();
            mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
            if (crossType == CROSS_YELLOW) {
                renderCross(level, mutable, collisionContext);
            }
        }
        
        GL11.glEnd();
        GL11.glEndList();
        
        GL11.glNewList(glListIndex + 2, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_LINES);
        color(secondaryColor);
        
        for (Long2ByteMap.Entry objectEntry : data().long2ByteEntrySet()) {
            byte crossType = objectEntry.getByteValue();
            mutable.set(BlockPos.getX(objectEntry.getLongKey()), BlockPos.getY(objectEntry.getLongKey()), BlockPos.getZ(objectEntry.getLongKey()));
            if (crossType == CROSS_SECONDARY) {
                renderCross(level, mutable, collisionContext);
            }
        }
        
        GL11.glEnd();
        GL11.glEndList();
    }
    
    public void renderList(Level level, CollisionContext collisionContext) {
        if (!generatedList) {
            compileList(level, collisionContext);
        }
        
        if (glListIndex != 0) {
            GL11.glCallList(glListIndex);
            GL11.glCallList(glListIndex + 1);
            GL11.glCallList(glListIndex + 2);
        }
    }
    
    private static void color(int color) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        GL11.glColor4f(red / 255f, green / 255f, blue / 255f, 1f);
    }
    
    public static void renderCross(Level level, BlockPos pos, CollisionContext collisionContext) {
        double blockOffset = 0;
        VoxelShape upperOutlineShape = level.getBlockState(pos).getShape(level, pos, collisionContext);
        if (!upperOutlineShape.isEmpty())
            blockOffset += upperOutlineShape.max(Direction.Axis.Y);
        
        
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        GL11.glVertex3d(x + .01, y + blockOffset, z + .01);
        GL11.glVertex3d(x - .01 + 1, y + blockOffset, z - .01 + 1);
        GL11.glVertex3d(x - .01 + 1, y + blockOffset, z + .01);
        GL11.glVertex3d(x + .01, y + blockOffset, z - .01 + 1);
    }
    
    @Override
    public void close() {
        if (glListIndex != 0) {
            GL11.glDeleteLists(glListIndex, 3);
        }
    }
}

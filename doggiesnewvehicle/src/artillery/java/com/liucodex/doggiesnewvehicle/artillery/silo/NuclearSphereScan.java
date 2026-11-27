package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;

/** Stable bounded scan, including the sphere surface; outside-cube points consume visits too. */
public final class NuclearSphereScan {
    public static final int MAX_RADIUS=52;
    // Schema 3 shape. Keep these dimensions stable for saved wave cursors.
    private static final int WAVE_DEPTH = 10;
    private static final int WAVE_HEIGHT = 20;
    private NuclearSphereScan(){ }
    public static int size(int radius){
        if(radius<1||radius>MAX_RADIUS)throw new IllegalArgumentException("Invalid scan radius");
        int width=radius*2+1;return width*width*width;
    }
    public static BlockPos offset(int radius,int cursor){
        if(cursor<0||cursor>=size(radius))throw new IllegalArgumentException("Invalid scan cursor");
        int width=radius*2+1;
        return new BlockPos(cursor/(width*width)-radius,cursor/width%width-radius,cursor%width-radius);
    }
    static int columnHeight(int radius) {
        return Math.min(radius,WAVE_DEPTH)+Math.min(radius,WAVE_HEIGHT)+1;
    }
    static int waveSize(int radius) {
        size(radius);
        int width=2*radius+1;
        return width*width*columnHeight(radius);
    }
    static double waveDistance(BlockPos offset,int radius) {
        double x=offset.getX(), y=offset.getY(), z=offset.getZ();
        int vertical=Math.min(radius,y<0?WAVE_DEPTH:WAVE_HEIGHT);
        int horizontalSquared=radius*radius, verticalSquared=vertical*vertical;
        return ((x*x+z*z)*verticalSquared+y*y*horizontalSquared)/(horizontalSquared*verticalSquared);
    }
    /** Center-first horizontal rings; each column runs bottom to top so fire sees settled support. */
    static BlockPos columnOffset(int radius, int cursor) {
        int height = columnHeight(radius);
        int column = cursor / height;
        int y = cursor % height - Math.min(radius,WAVE_DEPTH);
        if (column == 0) return new BlockPos(0, y, 0);
        int ring = ((int) Math.sqrt(column) + 1) / 2;
        int side = 2 * ring;
        int step = column - (side - 1) * (side - 1);
        int along = step % side;
        return switch (step / side) {
            case 0 -> new BlockPos(-ring + along, y, -ring);
            case 1 -> new BlockPos(ring, y, -ring + along);
            case 2 -> new BlockPos(ring - along, y, ring);
            default -> new BlockPos(-ring, y, ring - along);
        };
    }
    public static boolean contains(BlockPos offset,int radius){
        return (long)offset.getX()*offset.getX()+(long)offset.getY()*offset.getY()+(long)offset.getZ()*offset.getZ()<=(long)radius*radius;
    }
}

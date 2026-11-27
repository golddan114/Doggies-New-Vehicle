package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Bounded integer work plan: the upper footprint is 17x17, the central underground shaft is 9x9. */
public final class NuclearExcavation {
    public static final int WELL_HALF=4;
    public static final int PER_TICK=128;
    public static final int SIZE=NuclearDeploymentGeometry.FOOTPRINT;
    public static final int TOTAL=SIZE*SIZE*((int)NuclearDeploymentGeometry.WELL_DEPTH+NuclearDeploymentGeometry.ABOVE_HEIGHT);
    private NuclearExcavation(){ }
    public static BlockPos position(BlockPos anchor,int cursor){
        if(cursor<0||cursor>=TOTAL)throw new IllegalArgumentException("Excavation cursor");
        return anchor.offset(cursor%SIZE-SIZE/2,cursor/(SIZE*SIZE)-(int)NuclearDeploymentGeometry.WELL_DEPTH,
                cursor/SIZE%SIZE-SIZE/2);
    }
    public static boolean clears(BlockPos anchor,BlockPos pos){
        if(pos.equals(anchor))return false;
        return pos.getY()>=anchor.getY()||Math.abs(pos.getX()-anchor.getX())<=WELL_HALF&&Math.abs(pos.getZ()-anchor.getZ())<=WELL_HALF;
    }
    public static AABB shaft(BlockPos anchor){
        return new AABB(anchor.getX()-WELL_HALF,anchor.getY()-(int)NuclearDeploymentGeometry.WELL_DEPTH,anchor.getZ()-WELL_HALF,
                anchor.getX()+WELL_HALF+1,anchor.getY()+NuclearDeploymentGeometry.ABOVE_HEIGHT,anchor.getZ()+WELL_HALF+1);
    }
}

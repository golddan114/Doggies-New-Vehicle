package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Integer deployment boundaries share the native container's centre block. */
public final class NuclearDeploymentGeometry {
    public static final int FOOTPRINT=17;
    public static final int HALF=FOOTPRINT/2;
    public static final double WELL_DEPTH=10;
    public static final int ABOVE_HEIGHT=11;
    private NuclearDeploymentGeometry(){ }
    public static BlockPos anchorFromContainer(BlockPos container){
        return container.immutable();
    }
    public static BlockPos low(BlockPos anchor){return anchor.offset(-HALF,-(int)WELL_DEPTH,-HALF);}
    public static BlockPos high(BlockPos anchor){return anchor.offset(HALF,ABOVE_HEIGHT-1,HALF);}
    public static AABB clearance(Vec3 anchor){return new AABB(-HALF-.5,-WELL_DEPTH,-HALF-.5,HALF+.5,ABOVE_HEIGHT,HALF+.5).move(anchor);}
}

package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.world.DnvControlledFire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Keeps the nuclear registry/save identity while sharing rule-aware fire propagation with other DNV weapons. */
public final class NuclearResidualFire extends DnvControlledFire {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"doggiesnewvehicle");
    public static final RegistryObject<Block> FIRE=BLOCKS.register("nuclear_residual_fire",NuclearResidualFire::new);
    private NuclearResidualFire(){super();}
    public static boolean eligible(ServerLevel level,BlockPos pos){
        return Math.floorMod(pos.getX()*31+pos.getZ()*17,4)==0&&DnvControlledFire.eligible(level,pos);
    }
    public static void place(ServerLevel level,BlockPos pos,Entity actor){if(eligible(level,pos))DnvControlledFire.place(level,pos,actor,FIRE.get());}
}

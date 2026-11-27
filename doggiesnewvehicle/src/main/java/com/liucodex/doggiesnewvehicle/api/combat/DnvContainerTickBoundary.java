package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Optional content installs one workflow; the native adapter cannot import its implementation or state. */
public final class DnvContainerTickBoundary {
    @FunctionalInterface public interface Handler {
        boolean tick(Level level,BlockPos pos,BlockState state,Object nativeBox);
    }
    private static volatile Handler handler;
    private DnvContainerTickBoundary(){ }
    public static synchronized void install(Handler value){
        if(value==null||handler!=null)throw new IllegalStateException("Container workflow already installed or missing");
        handler=value;
    }
    public static boolean tick(Level level,BlockPos pos,BlockState state,Object box){
        Handler current=handler;return current!=null&&current.tick(level,pos,state,box);
    }
}

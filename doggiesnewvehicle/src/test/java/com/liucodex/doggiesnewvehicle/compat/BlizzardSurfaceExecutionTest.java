package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs compiled all-fluid/solid ray selection against explicit fluid/world ports. */
class BlizzardSurfaceExecutionTest {
    public record Support(double height, boolean fluid) { }
    public record Fluid(boolean empty) { public boolean isEmpty() { return empty; } }
    public record Clip(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) { }
    public record Hit(double y) {
        public HitResult.Type getType() { return Double.isFinite(y) ? HitResult.Type.BLOCK : HitResult.Type.MISS; }
        public Vec3 getLocation() { return new Vec3(0,y,0); }
        public BlockPos getBlockPos() { return BlockPos.ZERO; }
    }
    public static class World {
        public double ground = -8, surface = 0; public String fluid = "water"; public int rays;
        public Hit clip(Clip c) { rays++; return new Hit(c.fluid == ClipContext.Fluid.NONE ? ground : Math.max(ground,surface)); }
        public Fluid getFluidState(BlockPos p) { return new Fluid(fluid.isEmpty()); }
    }
    public static class Entity { public World world = new World(); public World level() { return world; } }
    @Test void waterLavaAndModdedAcidSupportHoverWhileDeploymentUsesOnlySolids() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/armored/BlizzardSurface", target = source + "Probe";
        ClassNode node = new ClassNode(); try(var in = getClass().getResourceAsStream("/"+source+".class")) { new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES); }
        node.access=Opcodes.ACC_PUBLIC; node.methods.removeIf(m->!m.name.equals("sample"));
        node.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
        Map<String,String> names = new HashMap<>(); names.put(source,target); names.put(source+"$Support",Type.getInternalName(Support.class));
        Map.of("world/entity/Entity",Entity.class,"world/level/Level",World.class,"world/level/ClipContext",Clip.class,
                "world/phys/HitResult",Hit.class,"world/phys/BlockHitResult",Hit.class,"world/level/material/FluidState",Fluid.class)
                .forEach((n,c)->names.put("net/minecraft/"+n,Type.getInternalName(c)));
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));
        var kind=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        var sample=kind.getMethod("sample",Entity.class,Vec3.class,Vec3.class,boolean.class);var tank=new Entity();
        Vec3 from=new Vec3(0,2,0),to=new Vec3(0,-12,0);
        for(String fluid:List.of("water","lava","fungal_acid","unknown_future_fluid")) {
            tank.world.fluid=fluid;assertEquals(new Support(0,true),sample.invoke(null,tank,from,to,true));
        }
        assertEquals(new Support(-8,false),sample.invoke(null,tank,from,to,false));
        tank.world.fluid="";assertEquals(new Support(-8,false),sample.invoke(null,tank,from,to,true));
        tank.world.fluid="lava";tank.world.ground=1;assertEquals(new Support(1,false),sample.invoke(null,tank,from,to,true));
        tank.world.ground=Double.NaN;tank.world.surface=Double.NaN;
        var absent=(Support)sample.invoke(null,tank,from,to,true);assertTrue(Double.isNaN(absent.height()));assertFalse(absent.fluid());
    }
}

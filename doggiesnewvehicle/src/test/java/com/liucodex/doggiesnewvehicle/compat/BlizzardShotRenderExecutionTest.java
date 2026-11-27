package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs compiled shot geometry and captures buffer ownership; GPU appearance remains a field check. */
class BlizzardShotRenderExecutionTest {
    public enum Layer { GLOW, CORE }
    public interface Buffers { VertexConsumer getBuffer(Layer layer); }
    public static class Types { public static final Layer TYPE = Layer.GLOW, CORE = Layer.CORE, BEAM_GLOW = Layer.GLOW, BEAM_CORE = Layer.CORE; }
    public static class TrailTank {
        public final com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation playback=new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation();
        public Vec3 position=new Vec3(30,0,0);
        public Vec3 getPosition(float partial){return position;}
        public com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation presentation(){return playback;}
    }
    @Test void acceptedTrailRendererUsesEachOriginalRayAfterReleaseAndFadesWithoutFollowingTheMovingHull() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",TrailTank.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/renderer/MultiBufferSource",Buffers.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/renderer/RenderType",Layer.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/client/BlizzardShotRenderType",Types.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/client/BlizzardFxRenderer",
            Set.of("continuous","laser","vertex"),names,null,false);
        var tank=new TrailTank();
        for(int tick=0;tick<=18;tick++){
            var snapshot=com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.encode(
                new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(tick,Vec3.ZERO,0,0,0,0,0,Vec3.ZERO),null);
            if(tick>=7)com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.appendLasers(snapshot,List.of(
                new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Laser(7,new Vec3(2,3,4),new Vec3(2,3,44))));
            tank.playback.accept(snapshot);tank.playback.advance();
        }
        var vertices=new ArrayList<Vec3>();var alphas=new ArrayList<Integer>();Layer[] active={null};
        Buffers buffers=layer->{active[0]=layer;return (VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(self,method,args)->{
            assertEquals(layer,active[0]);
            if(method.getName().equals("vertex")&&args.length==4)vertices.add(new Vec3(((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue(),((Number)args[3]).doubleValue()));
            if(method.getName().equals("color"))alphas.add(((Number)args[3]).intValue());
            return method.getReturnType()==VertexConsumer.class?self:null;
        });};
        var draw=type.getMethod("continuous",TrailTank.class,float.class,PoseStack.class,Buffers.class);
        draw.invoke(null,tank,1F,new PoseStack(),buffers);
        assertEquals(24,vertices.size());assertEquals(24,alphas.size());
        assertEquals(-28,vertices.stream().mapToDouble(v->v.x).average().orElseThrow(),1e-6);
        assertEquals(4,vertices.stream().mapToDouble(v->v.z).min().orElseThrow(),1e-6);
        assertEquals(44,vertices.stream().mapToDouble(v->v.z).max().orElseThrow(),1e-6);
        assertTrue(alphas.stream().allMatch(a->a>0&&a<255));
        tank.position=new Vec3(50,0,0);vertices.clear();draw.invoke(null,tank,1F,new PoseStack(),buffers);
        assertEquals(-48,vertices.stream().mapToDouble(v->v.x).average().orElseThrow(),1e-6,"Relative pose cancels later hull movement");
        tank.playback.accept(new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(100,Vec3.ZERO,0,0,0,0,0,Vec3.ZERO),null);
        vertices.clear();draw.invoke(null,tank,1F,new PoseStack(),buffers);assertTrue(vertices.isEmpty());
    }
    @Test void actualPulseHasSeparateDepthCoreAndNeverReusesConsumerAfterSwitch() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/client/BlizzardFxRenderer";
        ClassNode node = read(source); node.methods.removeIf(m -> !Set.of("coldBolt", "laser", "vertex").contains(m.name));
        node.methods.forEach(m -> m.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC); node.access = Opcodes.ACC_PUBLIC;
        String target = source + "Probe";
        Map<String,String> names = Map.of(source, target,
                "net/minecraft/client/renderer/MultiBufferSource", Type.getInternalName(Buffers.class),
                "net/minecraft/client/renderer/RenderType", Type.getInternalName(Layer.class),
                "com/liucodex/doggiesnewvehicle/client/fx/DnvJetRenderType", Type.getInternalName(Types.class),
                "com/liucodex/doggiesnewvehicle/client/BlizzardShotRenderType", Type.getInternalName(Types.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new Remapper() { @Override public String map(String name) { return names.getOrDefault(name, name); } }));
        Class<?> renderer = new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray())).loadClass(target.replace('/', '.'));
        var requests = new ArrayList<Layer>(); var counts = new EnumMap<Layer,Integer>(Layer.class); Layer[] active = {null};
        Buffers buffers = layer -> {
            requests.add(layer); active[0] = layer;
            return (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{VertexConsumer.class}, (self, method, args) -> {
                assertEquals(layer, active[0], "Consumer cannot survive a RenderType switch");
                if (method.getName().equals("endVertex")) counts.merge(layer, 1, Integer::sum);
                return method.getReturnType() == VertexConsumer.class ? self : null;
            });
        };
        var draw = renderer.getMethod("coldBolt", PoseStack.class, Buffers.class, Vec3.class, double.class);
        draw.invoke(null, new PoseStack(), buffers, new Vec3(1, 2, 7), 2d);
        assertEquals(List.of(Layer.GLOW, Layer.CORE), requests);
        assertEquals(12, counts.get(Layer.GLOW)); assertEquals(12, counts.get(Layer.CORE));
        requests.clear(); draw.invoke(null, new PoseStack(), buffers, Vec3.ZERO, 2d); assertTrue(requests.isEmpty());
    }
    @Test void coreWritesDepthAndUsesSceneOcclusionWhileTransparentGlowDoesNot() throws Exception {
        var core = read("com/liucodex/doggiesnewvehicle/client/BlizzardShotRenderType");
        var glow = read("com/liucodex/doggiesnewvehicle/client/fx/DnvJetRenderType");
        assertEquals("COLOR_DEPTH_WRITE", state(core, "setWriteMaskState"));
        assertEquals("LEQUAL_DEPTH_TEST", state(core, "setDepthTestState"));
        assertEquals("COLOR_WRITE", state(glow, "setWriteMaskState"));
        // Depth-order counterexample: a later entity at 12 may not overwrite a core at 8.
        double writtenDepth = state(core,"setWriteMaskState").equals("COLOR_DEPTH_WRITE") ? 8 : 20;
        assertFalse(12 <= writtenDepth); assertTrue(4 <= writtenDepth);
        assertTrue(12 <= 20, "The previous color-only policy reproduces the reported overwrite");
    }
    private String state(ClassNode node, String setter) {
        for (var method : node.methods) if (!method.name.equals("beam")) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(setter)) {
                var previous = instruction.getPrevious();
                while (previous.getOpcode() < 0) previous = previous.getPrevious();
                return ((FieldInsnNode) previous).name;
            }
        throw new AssertionError(setter);
    }
    private ClassNode read(String name) throws Exception {
        var node = new ClassNode(); try (var in = getClass().getResourceAsStream("/"+name+".class")) {
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
        } return node;
    }
}

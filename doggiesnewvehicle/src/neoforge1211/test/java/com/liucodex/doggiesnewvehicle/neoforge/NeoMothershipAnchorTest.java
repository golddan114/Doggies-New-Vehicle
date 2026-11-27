package com.liucodex.doggiesnewvehicle.neoforge;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.MothershipArrival;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec.Part;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** Execute the compiled stage and renderFx with entity data and an in-memory vertex sink. */
public class NeoMothershipAnchorTest {
    public enum Stage { AFTER_SKY, AFTER_PARTICLES }
    public record Event(PoseStack getPoseStack, Matrix4f getModelViewMatrix, Camera getCamera) {
        public Stage getStage() { return Stage.AFTER_PARTICLES; }
        public DeltaTracker getPartialTick() { return DeltaTracker.ONE; }
    }
    public static class Material { public static RenderType TYPE; }
    public static class World { public long getGameTime() { return 200; } }
    public static class Client {
        public static final Client INSTANCE = new Client();
        public World level = new World();
        public static Client getInstance() { return INSTANCE; }
    }
    public static class Ship {
        public double xo=22, yo=92, zo=12;
        public float yRotO=31, xRotO;
        boolean active;
        public boolean isRemoved() { return false; }
        public World level() { return Client.INSTANCE.level; }
        public boolean isWreck() { return false; }
        public float getHealth() { return 100; }
        public double getX() { return 23; }
        public double getY() { return 93; }
        public double getZ() { return 13; }
        public float getYRot() { return 34; }
        public float getXRot() { return 0; }
        public float getRoll() { return 0; }
        public float getPrevRoll() { return 0; }
        public MothershipRig.Angles renderPose(int i,float partial) { return new MothershipRig.Angles(0,0); }
        public Vec3 renderBeaconAnchor() { return new Vec3(23.5,58,13.5); }
        public int renderEntryAge() { return MothershipArrival.BEACON_TICKS; }
        public boolean entryActive() { return active; }
        public long renderShotStarted() { return -1; }
        public long renderAbortStarted() { return -1; }
        public float renderThrottle() { return 1; }
        public boolean renderImpact() { return false; }
        public double renderDropCharge(int i) { return 0; }
        public static V v(Vec3 p) { return new V(p.x,p.y,p.z); }
        public static Vec3 mc(V p) { return new Vec3(p.x(),p.y(),p.z()); }
    }
    public static class Capture extends MultiBufferSource.BufferSource {
        final List<List<Vector3f>> draws = new ArrayList<>();
        Matrix4f lightPose;
        Capture() { super(null, new LinkedHashMap<>()); }
        @Override public void endBatch() { }
        @Override public VertexConsumer getBuffer(RenderType type) {
            List<Vector3f> vertices = new ArrayList<>();
            draws.add(vertices);
            return new VertexConsumer() {
                public VertexConsumer addVertex(float x,float y,float z) { vertices.add(new Vector3f(x,y,z)); return this; }
                public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
                public VertexConsumer setUv(float u,float v) { return this; }
                public VertexConsumer setUv1(int a,int b) { return this; }
                public VertexConsumer setUv2(int a,int b) { return this; }
                public VertexConsumer setNormal(float x,float y,float z) { return this; }
            };
        }
        List<List<Vector3f>> nonEmpty() { return draws.stream().filter(d -> !d.isEmpty()).toList(); }
    }
    public static class HullPort {
        public MothershipRig rig=MothershipRig.get();
        public Map<String,Part> renderParts(Ship ship) { return rig.parts; }
        public void render(Ship ship,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) { }
        public static void draw(PoseStack pose,MultiBufferSource buffers,Part part,int light,boolean wreck,int alpha) {
            if (part==MothershipRig.get().parts.get("body_static/light"))
                ((Capture)buffers).lightPose=new Matrix4f(pose.last().pose());
        }
    }
    private static final String CLIENT = "com/liucodex/doggiesnewvehicle/boss/client/";
    private static final class StageLoader extends ClassLoader {
        final Map<String,String> names = new HashMap<>();
        StageLoader() {
            super(NeoMothershipAnchorTest.class.getClassLoader());
            names.put("net/minecraft/client/Minecraft", Type.getInternalName(Client.class));
            names.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(World.class));
            names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Ship.class));
            names.put("net/minecraft/client/renderer/entity/EntityRenderer",Type.getInternalName(HullPort.class));
            names.put("com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity", Type.getInternalName(Ship.class));
            String event = "net/neoforged/neoforge/client/event/RenderLevelStageEvent";
            names.put(event,Type.getInternalName(Event.class));
            names.put(event+"$Stage",Type.getInternalName(Stage.class));
            names.put(CLIENT+"MothershipFxRenderType",Type.getInternalName(Material.class));
            for (var name : List.of("MothershipFxPass","DnvMothershipRenderer","MothershipFxRenderer"))
                names.put(CLIENT+name, "probe/"+name);
        }
        Class<?> read(String simple, String... methods) throws Exception {
            var node = new ClassNode();
            try (InputStream in = getResourceAsStream(CLIENT+simple+".class")) {
                new ClassReader(Objects.requireNonNull(in)).accept(node,0);
            }
            if (methods.length>0) node.methods.removeIf(m -> !List.of(methods).contains(m.name));
            if (simple.equals("DnvMothershipRenderer")) {
                node.methods.removeIf(m -> (m.access & Opcodes.ACC_BRIDGE)!=0);
                node.fields.clear();
            }
            var writer = new ClassWriter(0);
            node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
            if (simple.equals("DnvMothershipRenderer")) {
                var init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
                init.visitCode(); init.visitVarInsn(Opcodes.ALOAD,0);
                init.visitMethodInsn(Opcodes.INVOKESPECIAL,Type.getInternalName(HullPort.class),"<init>","()V",false);
                init.visitInsn(Opcodes.RETURN); init.visitMaxs(1,1); init.visitEnd();
            }
            byte[] bytes = writer.toByteArray();
            return defineClass("probe."+simple,bytes,0,bytes.length);
        }
    }

    private static Vector3f middle(Vector3f a, Vector3f b) { return new Vector3f(a).lerp(b,.5f); }
    private static void anchor(Vec3 world, Vec3 camera, Matrix4f view, Vector3f actual) {
        Vector3f expected = view.transformPosition(world.subtract(camera).toVector3f());
        assertEquals(0, expected.distance(view.transformPosition(new Vector3f(actual))), 0.0001, world.toString());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void stageKeepsBeaconWarpAndAllTenExhaustRootsOnTheirWorldAnchors(boolean reverse) throws Exception {
        StageLoader loader = new StageLoader();
        loader.read("MothershipFxRenderer");
        var renderer=loader.read("DnvMothershipRenderer","renderFx","render","translate");
        var pass = loader.read("MothershipFxPass","<clinit>","enqueue","render");
        var hullRenderer=(HullPort)renderer.getConstructor().newInstance();
        var render = pass.getMethod("render",Event.class);
        var capture = new Capture();
        var field = pass.getDeclaredField("buffers");
        field.setAccessible(true);
        field.set(null,capture);
        Vec3 eye = reverse ? new Vec3(50,80,25) : new Vec3(0,85,-15);
        Matrix4f view = new Matrix4f().rotateX(reverse ? .4f : -.28f).rotateY(reverse ? 2.2f : -.6f);
        var camera = new Camera() { @Override public Vec3 getPosition() { return eye; } };
        var event = new Event(new PoseStack(),view,camera);
        Ship ship = new Ship();
        Vec3 hull = new Vec3(ship.getX(),ship.getY(),ship.getZ());
        var hullStack=new PoseStack();
        hullStack.translate(hull.x-eye.x,hull.y-eye.y,hull.z-eye.z);
        hullRenderer.render(ship,0,1,hullStack,capture,0);
        render.invoke(null,event);
        var draws = capture.nonEmpty();
        assertEquals(2,draws.size());
        var beacon = draws.getFirst();
        int column = beacon.size()-8;
        anchor(ship.renderBeaconAnchor(),eye,view,middle(beacon.get(column),beacon.get(column+1)));
        // Opposite segments of the first octagonal ground ring.
        anchor(ship.renderBeaconAnchor().add(0,.07,0),eye,view,middle(beacon.get(0),beacon.get(16)));
        var warp = draws.get(1);
        assertFalse(warp.isEmpty());
        // The last eight quads are the reveal ring about the hull origin.
        int ring = warp.size()-32;
        anchor(hull,eye,view,middle(warp.get(ring),warp.get(ring+16)));

        capture.draws.clear();
        ship.active=true;
        capture.lightPose=null;
        hullRenderer.render(ship,0,1,hullStack,capture,0);
        render.invoke(null,event);
        var flames = capture.nonEmpty();
        var rig = MothershipRig.get();
        assertEquals(10,flames.size());
        var body = Objects.requireNonNull(capture.lightPose);
        var mesh = rig.parts.get("body_static/light").stream();
        for (int i=0; i<10; i++) {
            var p = rig.engines.get(i).position();
            var rim=new HashSet<Vector3f>();
            for (int j=0;j<mesh.length;j+=8) {
                if (Math.abs(mesh[j+2]-p.z())<.1 && Math.hypot(mesh[j]-p.x(),mesh[j+1]-p.y())<1.25*rig.engines.get(i).radius())
                    rim.add(new Vector3f(mesh[j],mesh[j+1],mesh[j+2]));
            }
            assertEquals(8,rim.size());
            var center=new Vector3f();
            rim.forEach(center::add);
            center.div(8).sub(0,0,.075f);
            var vertices = flames.get(i);
            assertTrue(vertices.size()==192 || vertices.size()==48);
            int halfRing = vertices.size()==192 ? 16 : 8;
            var root=middle(vertices.get(0),vertices.get(halfRing));
            assertEquals(0,view.transformPosition(body.transformPosition(center)).distance(view.transformPosition(root)),.0001);
        }
    }
}

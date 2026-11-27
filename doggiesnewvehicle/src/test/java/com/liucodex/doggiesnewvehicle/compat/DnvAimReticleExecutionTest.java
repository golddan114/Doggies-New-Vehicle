package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

class DnvAimReticleExecutionTest {
    @Test void compiledStaticReticlesFollowThePredictedPointWhileDynamicAndUnrelatedArtworkStayUntouched()throws Exception{
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/client/overlay/RenderContext",Frame.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/client/RenderHelper",Draw.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/gui/GuiGraphics",Canvas.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/client/mixin/BlizzardCrosshairMixin",
            Set.of("dnv$fixed","dnv$placeFixedReticle","dnv$placePlainReticle"),names,Canvas.class,false);
        Canvas instance=(Canvas)type.getConstructor().newInstance();instance.dnv$screenAim=new Vec3(250,190,0);
        var precise=type.getMethod("dnv$placeFixedReticle",Canvas.class,ResourceLocation.class,
            float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class,Frame.class);
        var plain=type.getMethod("dnv$placePlainReticle",com.mojang.blaze3d.vertex.PoseStack.class,ResourceLocation.class,
            float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class,int.class,Frame.class);
        for(String path:List.of("common_cannon_zooming.png","common_cannon.png","ru_apc.png","common_cross.png","dynamic_cross.png","common_gun.png")){
            var texture=ResourceLocation.fromNamespaceAndPath("superbwarfare","textures/overlay/vehicle/crosshair/"+path);
            boolean fixed=!path.equals("dynamic_cross.png")&&!path.equals("common_gun.png");
            precise.invoke(instance,null,texture,10f,20f,0f,0f,80f,80f,80f,80f,new Frame());
            assertEquals(fixed?-60:10,Draw.x);assertEquals(fixed?30:20,Draw.y);
            plain.invoke(instance,null,texture,10f,20f,0f,0f,80f,80f,80f,80f,0xFFFFFF,new Frame());
            assertEquals(fixed?-60:10,Draw.x);assertEquals(fixed?30:20,Draw.y);
        }
        instance.dnv$screenAim=null;
        precise.invoke(instance,null,ResourceLocation.fromNamespaceAndPath("superbwarfare","textures/overlay/vehicle/crosshair/common_cannon_zooming.png"),
            10f,20f,0f,0f,80f,80f,80f,80f,new Frame());
        assertEquals(10,Draw.x);assertEquals(20,Draw.y);
    }
    @Test void nativeHandheldOriginIsEyeHeightAndVehicleHudUsesSeparateMuzzleAccessors()throws Exception{
        try(var jar=new ZipFile(SbwTestDependency.path().toFile())){
            var gun=read(jar,"item/gun/GunItem");
            var shoot=gun.methods.stream().filter(m->m.name.equals("shoot")&&m.desc.equals(
                "(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;)V")).findFirst().orElseThrow();
            var names=Arrays.stream(shoot.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).map(m->m.name).toList();
            assertTrue(names.contains("m_20188_")||names.contains("getEyeY"));
            assertFalse(names.contains("getShootPos"),"Handheld guns have no world-space animated muzzle input");
            var hud=read(jar,"client/overlay/VehicleCrosshairOverlay");
            var calls=hud.methods.stream().filter(m->m.name.equals("render")).flatMap(m->Arrays.stream(m.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).map(m->m.name).toList();
            assertTrue(calls.contains("getShootPosForHud"));assertTrue(calls.contains("getShootDirectionForHud"));
        }
    }
    @Test void compiledAimTraceUsesTheRealMuzzleDiscreteGravityWallsAndLoadedBoundary()throws Exception{
        var names=new HashMap<String,String>();String source="com/liucodex/doggiesnewvehicle/client/DnvArtilleryAimPoint";
        String target="com/liucodex/doggiesnewvehicle/compat/aimprobe/Aim";
        names.put(source,target);names.put(source+"$1",target+"$1");
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/client/overlay/RenderContext",AimFrame.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/player/LocalPlayer",Pilot.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",AimBody.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/GunData",Gun.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/GunProp",Prop.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/ProjectileInfo",Info.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Blizzard.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",AimWorld.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/ClipContext",AimClip.class);
        var definitions=new HashMap<String,byte[]>();
        for(String part:List.of(source)){
            var node=new ClassNode();try(var input=getClass().getResourceAsStream("/"+part+".class")){new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            var writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
            node.accept(new org.objectweb.asm.commons.ClassRemapper(writer,new org.objectweb.asm.commons.SimpleRemapper(names)));
            definitions.put(names.get(part),writer.toByteArray());
        }
        var type=new ExactQueuedShotFixture.Loader(definitions).loadClass(target.replace('/','.'));
        var method=type.getMethod("point",AimFrame.class,Vec3.class);var frame=new AimFrame();var vehicle=frame.player.vehicle;
        Vec3 original=vehicle.from.add(0,0,20);
        assertEquals(new Vec3(2,9.5,23),method.invoke(null,frame,original),"The static old reticle was (2,10,23)");
        assertEquals(2,vehicle.world.queries);vehicle.world.wall=18;
        assertEquals(new Vec3(2,9.75,18),method.invoke(null,frame,original));
        vehicle.world.loaded=false;assertNull(method.invoke(null,frame,original));
        vehicle.world.loaded=true;vehicle.gun.projectile="doggievehicle:apocalypse_missile";
        assertNull(method.invoke(null,frame,original),"Guided flight must not be guessed with cannon physics");
        vehicle.gun.projectile="superbwarfare:cannon_shell";vehicle.kind.namespace="superbwarfare";
        assertNull(method.invoke(null,frame,original),"No changes to upstream vehicles");
    }
    public static class AimBody{}
    public static class Kind{
        String namespace="doggievehicle";
        public static ResourceLocation getKey(Kind kind){return ResourceLocation.fromNamespaceAndPath(kind.namespace,"test");}
    }
    public static class Pilot extends AimBody{final Vehicle vehicle=new Vehicle();public AimBody getVehicle(){return vehicle;}}
    public static class AimFrame{final Pilot player=new Pilot();public Pilot getPlayer(){return player;}public float getPartialTick(){return .5f;}}
    public static class Vehicle extends AimBody{
        final Kind kind=new Kind();final Gun gun=new Gun();final AimWorld world=new AimWorld();final Vec3 from=new Vec3(2,10,3);
        public Kind getType(){return kind;}public int getSeatIndex(AimBody actor){return 0;}public Gun getGunData(int seat){return gun;}
        public Vec3 getShootPos(AimBody actor,float partial){return from;}public Vec3 getShootVec(AimBody actor,float partial){return new Vec3(0,0,1);}
        public String getGunName(int seat){return "Main";}public AimWorld level(){return world;}
    }
    public static class Blizzard extends Vehicle{public Vec3 primaryAimPoint(float partial){return from.add(0,0,256);}}
    public static class Prop{
        public static final Prop PROJECTILE=new Prop(),VELOCITY=new Prop(),GRAVITY=new Prop(),PROJECTILE_LIFE=new Prop();
    }
    public static class Info{final String id;Info(String id){this.id=id;}public String getItemId(){return id;}}
    public static class Gun{
        String projectile="superbwarfare:cannon_shell";
        public Object get(Prop prop){return prop==Prop.PROJECTILE?new Info(projectile):prop==Prop.VELOCITY?10:prop==Prop.GRAVITY?.5:200;}
    }
    public static class AimClip{
        final Vec3 from,to;
        public AimClip(Vec3 from,Vec3 to,net.minecraft.world.level.ClipContext.Block block,net.minecraft.world.level.ClipContext.Fluid fluid,AimBody source){this.from=from;this.to=to;}
    }
    public static class AimWorld{
        boolean loaded=true;double wall=Double.POSITIVE_INFINITY;int queries;
        public boolean hasChunksAt(net.minecraft.core.BlockPos a,net.minecraft.core.BlockPos b){return loaded;}
        public net.minecraft.world.phys.BlockHitResult clip(AimClip ray){
            queries++;
            if(ray.from.z<wall&&ray.to.z>=wall){var hit=ray.from.lerp(ray.to,(wall-ray.from.z)/(ray.to.z-ray.from.z));
                return new net.minecraft.world.phys.BlockHitResult(hit,net.minecraft.core.Direction.NORTH,net.minecraft.core.BlockPos.containing(hit),false);}
            return net.minecraft.world.phys.BlockHitResult.miss(ray.to,net.minecraft.core.Direction.NORTH,net.minecraft.core.BlockPos.containing(ray.to));
        }
    }
    private static ClassNode read(ZipFile jar,String name)throws Exception{
        var node=new ClassNode();try(var in=jar.getInputStream(jar.getEntry("com/atsuishio/superbwarfare/"+name+".class"))){new ClassReader(in.readAllBytes()).accept(node,0);}return node;
    }
    public static class Canvas {public Vec3 dnv$screenAim;}
    public static class Frame {public int getScreenWidth(){return 640;}public int getScreenHeight(){return 360;}}
    public static class Draw {
        static float x,y;
        public static void preciseBlit(Canvas canvas,ResourceLocation texture,float a,float b,float u,float v,float w,float h,float tw,float th){x=a;y=b;}
        public static void blit(com.mojang.blaze3d.vertex.PoseStack stack,ResourceLocation texture,float a,float b,float u,float v,float w,float h,float tw,float th,int color){x=a;y=b;}
    }
}

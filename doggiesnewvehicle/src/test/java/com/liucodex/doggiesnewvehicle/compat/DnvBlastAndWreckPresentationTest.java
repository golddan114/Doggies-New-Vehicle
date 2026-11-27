package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvWreckLifecycle;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCameraShakeSource;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Compiled opt-in projections: native damage/health remain independent from their presentation. */
public class DnvBlastAndWreckPresentationTest {
    public static class Entity {public final CompoundTag data=new CompoundTag();public CompoundTag getPersistentData(){return data;}}
    public static class Projectile extends Entity {public Entity owner;public Entity getOwner(){return owner;}}
    public static class Heaven extends Entity implements DnvCameraShakeSource {
        public boolean dnvCameraShakeAllowed(){return false;}
    }
    public static class World { }
    public static class Particles {
        static int sounds,particles,shakes;
        static java.util.function.BooleanSupplier suppressed;
        static Runnable callback;
        public static void spawnExplosionParticles(Fx type,World level,Vec3 point) {
            sounds++;particles++;
            if(!suppressed.getAsBoolean())shakes++;
            if(callback!=null){Runnable action=callback;callback=null;action.run();}
        }
    }
    public static class ShakeSender {
        int calls;
        public void sendToNearbyPlayers(World level,double x,double y,double z,double range,double duration,double intensity) {
            assertEquals(List.of(1D,2D,3D,192D,30D,12D),List.of(x,y,z,range,duration,intensity));calls++;
        }
    }
    public enum Fx {SMALL,MEDIUM,LARGE}
    public static class Builder {
        public float radius=2,damage=50;public Fx type=Fx.SMALL;
        public Builder withParticleType(Fx value){type=value;return this;}
    }
    public static class Trace {public static Entity cameraEntity;}
    public static class Vehicle extends Entity {
        public boolean wreck;public float health=20000;
        public boolean isWreck(){return wreck;}public float getHealth(){return health;}public float getMaxHealth(){return 20000;}
    }
    public static class Mother extends Vehicle implements DnvWreckLifecycle {
        public boolean active;
        public boolean dnvWreckReadyForCleanup(){return false;}public boolean dnvShowWreckHealth(){return false;}
        public boolean dnvShowVehicleMarker(){return active;}
    }
    @Test void strongerRocketAndHeEffectsDoNotChangeDamageRadiusOrOtherProjectiles()throws Exception {
        var type=blastType();
        for(int mode=0;mode<4;mode++) {
            Entity source=mode==3?new Entity():new Projectile();Builder builder=new Builder();
            if(mode>0)type.getMethod(mode==2?"cannon":"rocket",Entity.class).invoke(null,source);
            type.getMethod("apply",Builder.class,Entity.class).invoke(null,builder,source);
            assertEquals(mode==1?Fx.MEDIUM:mode==2?Fx.LARGE:Fx.SMALL,builder.type);
            assertEquals(2,builder.radius);assertEquals(50,builder.damage);
        }
    }

    @Test void heavenAndItsShotsSuppressShakeAfterOwnerUnloadWithoutChangingOtherSources()throws Exception {
        var type=blastType();Heaven heaven=new Heaven();Projectile shot=new Projectile();shot.owner=heaven;
        assertFalse(allowed(type,heaven));assertFalse(allowed(type,shot));
        type.getMethod("suppressCameraShake",Entity.class).invoke(null,shot);
        Projectile reloaded=new Projectile();reloaded.data.merge(shot.data.copy());
        assertFalse(allowed(type,reloaded),"Saved shot policy must survive an absent owner");
        assertTrue(allowed(type,new Projectile()));assertTrue(allowed(type,new Entity()));assertTrue(allowed(type,null));
    }

    @Test void nativeConstructorFeedbackFiltersOnlyMarkedSourcesAndPreservesItsParameters()throws Exception {
        var policy=blastType();
        var mixin=load("com/liucodex/doggiesnewvehicle/compat/mixin/ExplosionCameraShakeMixin",Map.of(
                "com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects",policy,
                "com/atsuishio/superbwarfare/network/message/receive/ShakeClientMessage$Companion",ShakeSender.class,
                "net/minecraft/world/entity/Entity",Entity.class,"net/minecraft/world/level/Level",World.class));
        Object subject=mixin.getConstructor().newInstance();var field=mixin.getDeclaredField("entity");field.setAccessible(true);
        var send=mixin.getDeclaredMethod("dnv$cameraShake",ShakeSender.class,World.class,
                double.class,double.class,double.class,double.class,double.class,double.class);send.setAccessible(true);
        ShakeSender sender=new ShakeSender();
        for(Entity source:new Entity[]{new Heaven(),new Entity(),null}) {
            field.set(subject,source);send.invoke(subject,sender,new World(),1D,2D,3D,192D,30D,12D);
        }
        assertEquals(2,sender.calls);
    }

    @Test void particlesKeepSoundAndVisualsAndRestoreTheirScopeAfterNestedCallsAndFailures()throws Exception {
        var type=blastType();var scope=type.getMethod("particleShakeSuppressed");
        Particles.sounds=Particles.particles=Particles.shakes=0;Particles.callback=null;
        Particles.suppressed=()->{try{return (boolean)scope.invoke(null);}catch(Exception e){throw new AssertionError(e);}};
        Particles.callback=()->{
            assertTrue(Particles.suppressed.getAsBoolean());
            try{particles(type,new Entity());}catch(Exception e){throw new AssertionError(e);}
            assertTrue(Particles.suppressed.getAsBoolean(),"Nested unrelated feedback restores the outer source");
        };
        particles(type,new Heaven());
        assertEquals(2,Particles.sounds);assertEquals(2,Particles.particles);assertEquals(1,Particles.shakes);
        assertFalse(Particles.suppressed.getAsBoolean());
        Particles.callback=()->{throw new IllegalStateException("native feedback failure");};
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->particles(type,new Heaven()));
        assertFalse(Particles.suppressed.getAsBoolean(),"A failed native callback cannot silence later explosions");
        particles(type,new Entity());assertEquals(2,Particles.shakes);
    }

    private static boolean allowed(Class<?> type,Entity source)throws Exception {
        return (boolean)type.getMethod("cameraShakeAllowed",Entity.class).invoke(null,source);
    }
    private static void particles(Class<?> type,Entity source)throws Exception {
        type.getMethod("spawnExplosionParticles",Entity.class,Fx.class,World.class,Vec3.class)
                .invoke(null,source,Fx.LARGE,new World(),Vec3.ZERO);
    }
    private static Class<?> blastType()throws Exception {
        return load("com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects",Map.of(
                "net/minecraft/world/entity/Entity",Entity.class,"net/minecraft/world/entity/projectile/Projectile",Projectile.class,
                "net/minecraft/world/level/Level",World.class,"com/atsuishio/superbwarfare/tools/ParticleTool",Particles.class,
                "com/atsuishio/superbwarfare/tools/CustomExplosion$Builder",Builder.class,
                "com/atsuishio/superbwarfare/tools/ParticleTool$ParticleType",Fx.class));
    }
    @Test void mothershipCombatBarStaysEmptyWhileNativeKirovSalvageBarAndLiveHealthStayUnchanged()throws Exception {
        var type=load("com/liucodex/doggiesnewvehicle/compat/mixin/VehicleWreckHealthOverlayMixin",Map.of(
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class,
                "net/minecraft/world/entity/Entity",Entity.class,"com/atsuishio/superbwarfare/client/overlay/OverlayTraceHandler",Trace.class));
        var projection=type.getDeclaredMethod("dnv$displayHealth",Vehicle.class);projection.setAccessible(true);
        Object instance=type.getConstructor().newInstance();
        Mother mother=new Mother();mother.health=5000;
        assertEquals(5000F,projection.invoke(instance,mother));
        mother.health=0;mother.wreck=true;
        float shown=(float)projection.invoke(instance,mother)+mother.getMaxHealth();
        assertEquals(0,shown);assertEquals(0,mother.health);
        Vehicle kirov=new Vehicle();kirov.health=0;kirov.wreck=true;
        assertEquals(20000,(float)projection.invoke(instance,kirov)+kirov.getMaxHealth());
        var target=type.getDeclaredMethod("dnv$visibleMarkerTarget");target.setAccessible(true);
        Trace.cameraEntity=mother;assertNull(target.invoke(instance));
        mother.active=true;assertSame(mother,target.invoke(instance));
        Trace.cameraEntity=kirov;assertSame(kirov,target.invoke(instance));
        Trace.cameraEntity=null;assertNull(target.invoke(instance));
    }
    private static Class<?> load(String original,Map<String,Class<?>> ports)throws Exception {
        String generated=original+"Probe";Map<String,String> types=new HashMap<>();types.put(original,generated);
        ports.forEach((name,type)->types.put(name,Type.getInternalName(type)));
        ClassNode node=new ClassNode();
        try(var input=DnvBlastAndWreckPresentationTest.class.getClassLoader().getResourceAsStream(original+".class")){new ClassReader(input).accept(node,0);}
        node.access=Opcodes.ACC_PUBLIC;node.visibleAnnotations=null;node.invisibleAnnotations=null;
        node.methods.forEach(method->{method.visibleAnnotations=null;method.invisibleAnnotations=null;});
        ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(types)));byte[] bytes=writer.toByteArray();
        ClassLoader parent=ports.getOrDefault("com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects",
                DnvBlastAndWreckPresentationTest.class).getClassLoader();
        return new ClassLoader(parent) {
            Class<?> define(){return defineClass(generated.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}

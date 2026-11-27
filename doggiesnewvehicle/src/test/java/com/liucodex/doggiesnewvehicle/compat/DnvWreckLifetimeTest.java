package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvWreckLifecycle;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the common expiry adapter, including persistence, content policy, native loot and removal ports. */
class DnvWreckLifetimeTest {
    private static final Class<?> SUBJECT=load();
    @Test void livingAndClientEntitiesDoNotAgeOrChangeNativeHealth() throws Exception {
        Vehicle live=new Vehicle();live.wreck=false;
        for(int i=0;i<2000;i++)assertFalse(tick(live));
        assertTrue(live.data.isEmpty());assertEquals(20000,live.health);assertEquals(0,live.loot);
        live.wreck=true;live.world.isClientSide=true;
        for(int i=0;i<2000;i++)assertFalse(tick(live));
        assertTrue(live.data.isEmpty());assertFalse(live.removed);
    }
    @Test void bothNativeHullAndDetachedTurretWrecksExpireAtSixtyLoadedSecondsWithOneLootSettlement() throws Exception {
        for(Body body:new Body[]{new Vehicle(),new Turret()}) {
            for(int i=0;i<600;i++)assertFalse(tick(body));
            Body loaded=body instanceof Vehicle?new Vehicle():new Turret();loaded.data=body.data.copy();
            for(int i=600;i<1199;i++)assertFalse(tick(loaded));
            assertFalse(loaded.removed);assertTrue(tick(loaded));assertTrue(loaded.removed);
            assertEquals(1,loaded.loot);assertEquals(1,loaded.ejected);
            for(int i=0;i<100;i++)assertFalse(tick(loaded));
            SUBJECT.getMethod("finish",Body.class).invoke(null,loaded);assertEquals(1,loaded.loot);
        }
    }
    @Test void mothershipFallIsProtectedThenAnySavedSettledRemnantExpiresOnTheNextTick() throws Exception {
        Mother body=new Mother();
        for(int i=0;i<500;i++)assertFalse(tick(body));
        assertTrue(body.data.isEmpty());body.ready=true;
        assertTrue(tick(body));assertEquals(1,body.loot);assertTrue(body.removed);
    }
    @Test void malformedTimerIsBoundedAndSavedSettlementCannotDuplicateLoot() throws Exception {
        Vehicle negative=new Vehicle();negative.data.putInt("DnvWreckLoadedTicks",Integer.MIN_VALUE);
        assertFalse(tick(negative));assertEquals(1,negative.data.getInt("DnvWreckLoadedTicks"));
        Vehicle huge=new Vehicle();huge.data.putInt("DnvWreckLoadedTicks",Integer.MAX_VALUE);
        assertTrue(tick(huge));assertEquals(1,huge.loot);
        Vehicle replay=new Vehicle();replay.data=huge.data.copy();assertTrue(tick(replay));assertEquals(0,replay.loot);
    }
    private static boolean tick(Body body)throws Exception{return (boolean)SUBJECT.getMethod("tick",Body.class).invoke(null,body);}
    public static class World {public boolean isClientSide;}
    public static class Body {
        public final World world=new World();public CompoundTag data=new CompoundTag();public boolean removed;public int loot,ejected;
        public World level(){return world;}public boolean isRemoved(){return removed;}public CompoundTag getPersistentData(){return data;}
        public void discard(){removed=true;}public void ejectPassengers(){ejected++;}public void generateWreckageLoot(){loot++;}
    }
    public static class Vehicle extends Body {public boolean wreck=true;public float health=20000;public boolean isWreck(){return wreck;}}
    public static class Turret extends Body { }
    public static class Mother extends Vehicle implements DnvWreckLifecycle {
        public boolean ready;public boolean dnvWreckReadyForCleanup(){return ready;}public int dnvWreckLifetimeTicks(){return 1;}
    }
    private static Class<?> load() {
        String original="com/liucodex/doggiesnewvehicle/compat/DnvWreckLifetime",generated=original+"Probe";
        Map<String,String> types=Map.of(original,generated,"net/minecraft/world/entity/Entity",Type.getInternalName(Body.class),
                "net/minecraft/world/level/Level",Type.getInternalName(World.class),
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(Vehicle.class),
                "com/atsuishio/superbwarfare/entity/vehicle/TurretWreckEntity",Type.getInternalName(Turret.class));
        try(var input=DnvWreckLifetimeTest.class.getClassLoader().getResourceAsStream(original+".class")) {
            ClassWriter writer=new ClassWriter(0);new ClassReader(input).accept(new ClassRemapper(writer,new SimpleRemapper(types)),0);
            byte[] bytes=writer.toByteArray();return new ClassLoader(DnvWreckLifetimeTest.class.getClassLoader()) {
                Class<?> define(){return defineClass(generated.replace('/','.'),bytes,0,bytes.length);}
            }.define();
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
}

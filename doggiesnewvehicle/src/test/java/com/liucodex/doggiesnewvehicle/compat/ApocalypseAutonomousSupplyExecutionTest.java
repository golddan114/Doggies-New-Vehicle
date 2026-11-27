package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import com.liucodex.doggiesnewvehicle.compat.ExactDriverlessGunFixture.*;

/** Production AI logistics plus exact SBW reloadAmmo; world, storage and NBT are bounded ports. */
class ApocalypseAutonomousSupplyExecutionTest {
    @Test void emptySpawnPrimesAllFiveGunsAndEnergyOnceThroughNativeStorage() throws Exception {
        var f=new Fixture();f.tick();
        assertEquals(20000,f.tank.energy);assertEquals(1,f.tank.energyWrites);assertEquals(5,f.tank.writes);
        for(var gun:f.tank.guns.values()){assertEquals(4,gun.ammo.get());assertEquals(12,gun.virtualAmmo.get());assertEquals(4,gun.withdrawals);}
        f.tick();f.tank.world.time++;f.tick();assertEquals(5,f.tank.writes);assertEquals(1,f.tank.energyWrites);
        assertEquals(0,f.tank.reserve);
    }
    @Test void replenishingReservesDoesNotRefillLoadedRoundsOrSkipNativeReloadAfterRestore() throws Exception {
        var f=new Fixture();f.tick();var tag=f.save();
        var restored=new Fixture();restored.load(tag);var gun=restored.tank.guns.get("MainL");
        gun.reload.setState(ReloadState.EMPTY_RELOADING);restored.tick();
        assertEquals(0,gun.ammo.get());assertEquals(16,gun.virtualAmmo.get());assertTrue(gun.reloading());
        assertEquals(0,gun.withdrawals);
        gun.reloadAmmo(restored.tank,false);assertEquals(4,gun.ammo.get());assertFalse(gun.reloading());
        restored.tank.energy=9000;restored.tank.world.time++;restored.tick();assertEquals(20000,restored.tank.energy);
    }
    @Test void legacySavePreservesAnExistingReloadAndUnavailableEntityNeverReceivesSupply() throws Exception {
        var f=new Fixture();var gun=f.tank.guns.get("MainL");gun.reload.setState(ReloadState.EMPTY_RELOADING);
        f.load(new CompoundTag());f.tick();assertEquals(0,gun.ammo.get());assertTrue(gun.reloading());
        for(int failure=0;failure<3;failure++){
            var blocked=new Fixture();if(failure==0)blocked.tank.ready=false;else if(failure==1)blocked.tank.initialized=false;else blocked.tank.guns.remove("Missiles");
            blocked.tick();assertEquals(0,blocked.tank.energyWrites);assertEquals(0,blocked.tank.writes);
        }
    }
    static class Fixture {
        final Tank tank=new Tank();final Class<?> type;final Object supply;
        Fixture()throws Exception {
            for(String name:List.of("MainL","MainR","RocketL","RocketR","Missiles"))tank.guns.put(name,new ExactDriverlessGunFixture().data);
            String source="com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousSupply",dest="com/liucodex/doggiesnewvehicle/compat/executionprobe/Supply";
            Map<String,String> names=new HashMap<>();names.put(source,dest);
            Map.of("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",Tank.class,
                "com/atsuishio/superbwarfare/data/gun/GunData",Data.class,"com/atsuishio/superbwarfare/data/gun/GunProp",Prop.class,
                "com/atsuishio/superbwarfare/data/gun/value/IntValue",IntValue.class,
                "net/minecraft/world/entity/Entity",Body.class,"net/minecraft/world/level/Level",World.class)
                .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            ClassWriter writer=new ClassWriter(0);
            try(var in=getClass().getResourceAsStream("/"+source+".class")){
                new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}),0);
            }
            type=new ExactQueuedShotFixture.Loader(Map.of(dest,writer.toByteArray())).loadClass(dest.replace('/','.'));
            var ctor=type.getDeclaredConstructor(Tank.class);ctor.setAccessible(true);supply=ctor.newInstance(tank);
        }
        Object call(String name,Class<?>[] params,Object...args)throws Exception{var method=type.getDeclaredMethod(name,params);method.setAccessible(true);return method.invoke(supply,args);}
        void tick()throws Exception{call("tick",new Class<?>[0]);}
        CompoundTag save()throws Exception{var tag=new CompoundTag();call("save",new Class<?>[]{CompoundTag.class},tag);return tag;}
        void load(CompoundTag tag)throws Exception{call("load",new Class<?>[]{CompoundTag.class},tag);}
    }
    public static class World{public long time;public long getGameTime(){return time;}}
    public static class Tank extends Vehicle {
        public final World world=new World();public final Map<String,Data> guns=new LinkedHashMap<>();
        public boolean ready=true,initialized=true;public int energy,energyWrites,writes;
        public boolean autonomousReady(){return ready;}public boolean isInitialized(){return initialized;}public World level(){return world;}
        public int getMaxEnergy(){return 20000;}public int getEnergy(){return energy;}public void setEnergy(int amount){energy=amount;energyWrites++;}
        public Data getGunData(String name){return guns.get(name);}public void modifyGunData(String name,Consumer<Data> change){change.accept(guns.get(name));writes++;}
    }
}

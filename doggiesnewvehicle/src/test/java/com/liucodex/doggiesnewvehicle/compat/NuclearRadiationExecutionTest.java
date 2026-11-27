package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Execute the production timer/maximum-health loss; health and client edges are explicit doubles. */
class NuclearRadiationExecutionTest {
    @Test void nativeNegativeEffectSchedulesTwentyMaximumHealthLossesAndNeverClientDamage()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearRadiationEffect";
        String target="com/liucodex/doggiesnewvehicle/compat/executionprobe/NuclearRadiation";
        Map<String,String> names=new HashMap<>();names.put(source,target);
        names.put("net/minecraft/world/effect/MobEffect",Type.getInternalName(Effect.class));
        names.put("net/minecraft/world/entity/LivingEntity",Type.getInternalName(Body.class));
        names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Body.class));
        names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
        names.put("net/minecraft/world/damagesource/DamageSource",Type.getInternalName(Damage.class));
        names.put("net/minecraft/resources/ResourceKey",Type.getInternalName(Key.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearDamage",Type.getInternalName(DamagePort.class));
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        try(var in=getClass().getResourceAsStream("/"+source+".class")){
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}),0);
        }
        var type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        var effect=type.getConstructor().newInstance();assertEquals(MobEffectCategory.HARMFUL,((Effect)effect).category);
        String scheduler=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m->m.getReturnType()==boolean.class&&m.getParameterCount()==2&&m.getParameterTypes()[0]==int.class).findFirst().orElseThrow().getName();
        var should=type.getMethod(scheduler,int.class,int.class);var apply=type.getMethod("applyEffectTick",Body.class,int.class);
        var body=new Body();
        for(int duration=400;duration>0;duration--){
            if((boolean)should.invoke(effect,duration,0)){
                assertEquals(0,(400-duration+1)%20,"First tick must wait a whole second");
                float before=body.health;apply.invoke(effect,body,0);assertEquals(before-210,body.health,.00001);
            }
        }
        assertEquals(20,body.writes);assertEquals(5800,body.health,.0001);
        assertEquals(0,body.hits,"Armor, absorption, invulnerability and cold multipliers cannot enter a hurt pipeline");
        body.world.isClientSide=true;apply.invoke(effect,body,0);assertEquals(20,body.writes);
        assertFalse((boolean)should.invoke(effect,0,0));
        body.world.isClientSide=false;body.health=40;apply.invoke(effect,body,5);assertEquals(0,body.health);
        body.health=200;body.max=2000;apply.invoke(effect,body,0);assertEquals(150,body.health);
        int writes=body.writes;
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){
            body.health=invalid;apply.invoke(effect,body,0);assertEquals(writes,body.writes);
        }
    }
    public static class Effect {
        final MobEffectCategory category;
        public Effect(MobEffectCategory category,int color){this.category=category;assertEquals(0xDDAA22,color);}
        public boolean isDurationEffectTick(int duration,int amplifier){return false;}
        public boolean shouldApplyEffectTickThisTick(int duration,int amplifier){return false;}
    }
    public static class World {public boolean isClientSide;}
    public static class Body {
        final World world=new World();int hits,writes;float health=10000,max=10000;
        public World level(){return world;}
        public float getHealth(){return health;}public float getMaxHealth(){return max;}public void setHealth(float value){health=value;writes++;}
        public boolean hurt(Damage source,float amount){hits++;return false;}
    }
    public static class Damage { }
    public static class Key { }
    public static class DamagePort {
        public static final Key RADIATION=new Key();
        public static Damage source(World world,Key type,Body actor){assertSame(RADIATION,type);assertNull(actor);return new Damage();}
    }
}

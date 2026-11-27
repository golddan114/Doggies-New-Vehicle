package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearFlight;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute compiled missile damage/save/load and shared armor rules; only native edges are ports. */
class NuclearDurabilityExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/",PROBE=ROOT+"compat/durabilityprobe/";
    @Test void ordinaryHitsAccumulateUnderSpecialArmorAndOnlyLethalDamageDiscards() throws Exception {
        var f=new Fixture();var body=f.create();
        assertEquals(500,f.health(body));assertEquals(.8F,(float)f.type.getMethod("ordinaryArmorReduction").invoke(body));
        assertTrue(f.hurt(body,new Source(null,Keys.IS_PROJECTILE),100));
        assertEquals(480,f.health(body),.001);assertFalse(body.removed);assertEquals(0,body.discards);
        assertTrue(f.hurt(body,new Source(null,Keys.IS_EXPLOSION),100));
        assertEquals(460,f.health(body),.001);assertFalse(body.removed);
        assertTrue(f.hurt(body,new Source(null,Keys.PROJECTILE_HIT),3000));
        assertEquals(0,f.health(body));assertEquals(1,body.discards);
        assertFalse(f.hurt(body,new Source(null,Keys.IS_PROJECTILE),1));assertEquals(1,body.discards);
        for(Key nativeTag:List.of(Keys.PROJECTILE,Keys.PROJECTILE_ABSOLUTE,Keys.CUSTOM_EXPLOSION,Keys.PROJECTILE_EXPLOSION,Keys.VEHICLE_EXPLOSION)){
            var other=f.create();assertTrue(f.hurt(other,new Source(null,nativeTag),100));assertEquals(480,f.health(other),.001);
        }
    }
    @Test void lasersSpecialDamageAndMarkedPenetrationKeepTheirOwnRules() throws Exception {
        var f=new Fixture();var body=f.create();
        assertTrue(f.hurt(body,new Source(null,Keys.LASER),100));assertEquals(400,f.health(body),.001);
        assertTrue(f.hurt(body,new Source(null,Keys.SPECIAL,Keys.IS_EXPLOSION),100));assertEquals(300,f.health(body),.001);
        var bullet=new Bullet();bullet.getPersistentData().putFloat("DnvProjectileArmorBoost",.5F);
        assertTrue(f.hurt(body,new Source(bullet,Keys.PROJECTILE_HIT),100));assertEquals(240,f.health(body),.001);
        assertTrue(f.hurt(body,new DirectSource(.5F),100));assertEquals(180,f.health(body),.001);
        assertTrue(f.hurt(body,new Source(null,Keys.LASER),200));assertEquals(0,f.health(body));assertTrue(body.removed);
    }
    @Test void rejectedDamageCannotMutateHealthOrDiscard() throws Exception {
        var f=new Fixture();var body=f.create();var source=new Source(null,Keys.IS_PROJECTILE);
        for(float amount:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,-1})assertFalse(f.hurt(body,source,amount));
        body.world.isClientSide=true;assertFalse(f.hurt(body,source,100));body.world.isClientSide=false;
        f.flag(body,"armed",false);assertFalse(f.hurt(body,source,100));f.flag(body,"armed",true);
        f.flag(body,"invalid",true);assertFalse(f.hurt(body,source,100));f.flag(body,"invalid",false);
        body.invulnerable=true;assertFalse(f.hurt(body,source,100));body.invulnerable=false;
        assertFalse(f.hurt(body,new Source(null),100));assertEquals(500,f.health(body));assertEquals(0,body.discards);
    }
    @Test void nativeTowerStaticAndHeadshotLasersAccumulateWithoutPhysicalArmorOrImmediateRemoval() throws Exception {
        var f=new Fixture();var body=f.create();
        for(Key laser:List.of(Keys.LASER_STATIC,Keys.LASER_HEADSHOT)){
            assertTrue(f.hurt(body,new Source(null,laser,Keys.IS_PROJECTILE),100));
        }
        assertEquals(300,f.health(body),.001);assertEquals(0,body.discards);
        assertTrue(f.hurt(body,new Source(null,Keys.LASER_STATIC),300));
        assertEquals(0,f.health(body));assertEquals(1,body.discards);
    }
    @Test void damagedHealthSurvivesRepeatedSaveLoadAndLegacyMigratesExactlyOnce() throws Exception {
        var f=new Fixture();var body=f.create();assertTrue(f.hurt(body,new Source(null,Keys.IS_PROJECTILE),100));
        var tag=f.save(body);assertEquals(2,tag.getInt("NuclearSchema"));assertEquals(480,tag.getFloat("Health"),.001);
        var restored=f.create();f.load(restored,tag);assertFalse(f.flag(restored,"invalid"));assertEquals(480,f.health(restored),.001);
        assertTrue(f.hurt(restored,new Source(null,Keys.LASER),30));tag=f.save(restored);
        var twice=f.create();f.load(twice,tag);assertEquals(450,f.health(twice),.001);
        var legacy=tag.copy();legacy.putInt("NuclearSchema",1);legacy.remove("Health");
        f.load(twice,legacy);assertEquals(500,f.health(twice));assertEquals(2,f.save(twice).getInt("NuclearSchema"));
        assertTrue(f.hurt(twice,new Source(null,Keys.LASER),100));f.load(restored,f.save(twice));assertEquals(400,f.health(restored));
    }
    @Test void corruptOrUnknownHealthCannotHealOrAcceptDamage() throws Exception {
        var f=new Fixture();var seed=f.save(f.create());
        for(float health:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0,501}){
            var bad=seed.copy();bad.putFloat("Health",health);var body=f.create();f.load(body,bad);
            assertTrue(f.flag(body,"invalid"));assertFalse(f.hurt(body,new Source(null,Keys.IS_PROJECTILE),100));
        }
        for(int schema:new int[]{2,3}){
            var bad=seed.copy();bad.putInt("NuclearSchema",schema);bad.remove("Health");
            var body=f.create();f.load(body,bad);assertTrue(f.flag(body,"invalid"));
        }
        var wrongType=seed.copy();wrongType.putInt("Health",400);var body=f.create();f.load(body,wrongType);assertTrue(f.flag(body,"invalid"));
    }
    public record Key(String id) { }
    public static class Keys {
        public static final Key SPECIAL=new Key("special"),LASER=new Key("laser"),LASER_STATIC=new Key("laser_static"),
            LASER_HEADSHOT=new Key("laser_headshot"),IS_PROJECTILE=new Key("is_projectile"),
            IS_EXPLOSION=new Key("is_explosion"),CUSTOM_EXPLOSION=new Key("custom_explosion"),
            PROJECTILE_EXPLOSION=new Key("projectile_explosion"),VEHICLE_EXPLOSION=new Key("vehicle_explosion"),
            PROJECTILE_HIT=new Key("projectile_hit"),PROJECTILE_HIT_HEADSHOT=new Key("projectile_hit_headshot"),
            GRAPESHOT_HIT=new Key("grapeshot_hit"),PROJECTILE=new Key("projectile"),PROJECTILE_ABSOLUTE=new Key("projectile_absolute"),
            GUN_FIRE=new Key("gunfire"),GUN_FIRE_HEADSHOT=new Key("gunfire_headshot"),
            GUN_FIRE_ABSOLUTE=new Key("gunfire_absolute"),GUN_FIRE_HEADSHOT_ABSOLUTE=new Key("gunfire_headshot_absolute");
    }
    public static class Source {
        final Body direct;final Set<Key> keys;
        public Source(Body direct,Key...keys){this.direct=direct;this.keys=Set.of(keys);}
        public boolean is(Key key){return keys.contains(key);}public Body getDirectEntity(){return direct;}
    }
    public static class DirectSource extends Source {
        final float fraction;public DirectSource(float fraction){super(null);this.fraction=fraction;}
        public float fraction(){return fraction;}
    }
    public static class World {public boolean isClientSide;}
    public static class Body {
        public final World world=new World();public final Data entityData=new Data();public boolean removed,invulnerable;public int discards;
        final CompoundTag persistent=new CompoundTag();
        public World level(){return world;}public boolean isRemoved(){return removed;}
        public boolean isInvulnerableTo(Source source){return invulnerable;}public void discard(){removed=true;discards++;}
        public CompoundTag getPersistentData(){return persistent;}
    }
    public static class Bullet extends Body { }
    public record DataKey(int id) { }
    public static class Serializers {public static final Object BOOLEAN=new Object(),INT=new Object(),FLOAT=new Object();}
    public static class Data {
        private static int next;final Map<DataKey,Object> values=new HashMap<>();
        public static DataKey defineId(Class<?> owner,Object serializer){return new DataKey(next++);}
        public void define(DataKey key,Object value){assertNull(values.put(key,value));}
        public Object get(DataKey key){return values.get(key);}public void set(DataKey key,Object value){values.put(key,value);}
    }
    public static class Builder {
        final Data data;public Builder(Data data){this.data=data;}
        public Builder define(DataKey key,Object value){data.define(key,value);return this;}
    }
    static class Fixture extends ClassLoader {
        final Map<String,String> names=new HashMap<>();final Class<?> type;
        Fixture() throws Exception {
            super(NuclearDurabilityExecutionTest.class.getClassLoader());
            for(String p:List.of("artillery/silo/NuclearMissileEntity","core/DnvArmorRules","core/DnvTaggedProjectileArmor"))
                names.put(ROOT+p,PROBE+p.substring(p.lastIndexOf('/')+1));
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity",Body.class),
                Map.entry("net/minecraft/world/entity/projectile/Projectile",Bullet.class),Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/world/damagesource/DamageSource",Source.class),Map.entry("net/minecraft/resources/ResourceKey",Key.class),
                Map.entry("net/minecraft/tags/TagKey",Key.class),Map.entry("net/minecraft/tags/DamageTypeTags",Keys.class),
                Map.entry("com/atsuishio/superbwarfare/init/ModDamageTypes",Keys.class),Map.entry("com/atsuishio/superbwarfare/init/ModTags$DamageTypes",Keys.class),
                Map.entry(ROOT+"core/DnvArmorDamageTags",Keys.class),Map.entry(ROOT+"core/DnvDirectArmorPenetration",DirectSource.class),
                Map.entry("net/minecraft/network/syncher/EntityDataAccessor",DataKey.class),
                Map.entry("net/minecraft/network/syncher/EntityDataSerializer",Object.class),
                Map.entry("net/minecraft/network/syncher/EntityDataSerializers",Serializers.class),
                Map.entry("net/minecraft/network/syncher/SynchedEntityData",Data.class),
                Map.entry("net/minecraft/network/syncher/SynchedEntityData$Builder",Builder.class))
                .forEach((a,b)->names.put(a,Type.getInternalName(b)));
            type=loadClass((PROBE+"NuclearMissileEntity").replace('/','.'));
        }
        Body create() throws Exception {
            var b=(Body)type.getConstructor().newInstance();
            var define=Arrays.stream(type.getMethods()).filter(m->m.getName().equals("defineSynchedData")).findFirst().orElseThrow();
            if(define.getParameterCount()==0)define.invoke(b);else define.invoke(b,new Builder(b.entityData));
            flag(b,"armed",true);type.getField("actor").set(b,UUID.randomUUID());type.getField("silo").set(b,UUID.randomUUID());
            type.getField("target").set(b,new BlockPos(100,64,200));type.getField("stage").set(b,NuclearFlight.Stage.TRANSFER);return b;
        }
        void flag(Body b,String name,boolean v)throws Exception{type.getField(name).setBoolean(b,v);}
        boolean flag(Body b,String name)throws Exception{return type.getField(name).getBoolean(b);}
        boolean hurt(Body b,Source s,float amount)throws Exception{return (boolean)type.getMethod("hurt",Source.class,float.class).invoke(b,s,amount);}
        float health(Body b)throws Exception{return (float)type.getMethod("getHealth").invoke(b);}
        CompoundTag save(Body b)throws Exception{var tag=new CompoundTag();type.getMethod("addAdditionalSaveData",CompoundTag.class).invoke(b,tag);return tag;}
        void load(Body b,CompoundTag tag)throws Exception{type.getMethod("readAdditionalSaveData",CompoundTag.class).invoke(b,tag);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            String original=names.entrySet().stream().filter(e->e.getValue().equals(name.replace('.','/'))).map(Map.Entry::getKey)
                .findFirst().orElseThrow(()->new ClassNotFoundException(name));
            try(var in=getParent().getResourceAsStream(original+".class")){
                var node=new ClassNode();new ClassReader(in.readAllBytes()).accept(node,0);node.access=Opcodes.ACC_PUBLIC;
                if(original.endsWith("NuclearMissileEntity")){
                    var selected=Set.of("<clinit>","defineSynchedData","getHealth","ordinaryArmorReduction","hurt","addAdditionalSaveData","readAdditionalSaveData");
                    node.methods.removeIf(m->!selected.contains(m.name));node.fields.removeIf(f->f.name.equals("clock"));
                    node.superName=Type.getInternalName(Body.class);node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
                    var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
                    ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
                    ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxStack=1;ctor.maxLocals=1;node.methods.add(ctor);
                }
                node.fields.forEach(f->f.access=(f.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
                node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
                var writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
                byte[] bytes=writer.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception ex){throw new ClassNotFoundException(name,ex);}
        }
    }
}

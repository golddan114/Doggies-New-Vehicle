package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled proxy admission plus the exact upstream skip-before-exposure control-flow contract. */
class CombatProxyExplosionExecutionTest {
    @Test void explosionsNeverResolveOrDamageTheRepresentedHullWhileMeleeAndArrowsStillRelay() throws Exception {
        ProxyPort proxy=create();
        for(Key key:List.of(Keys.IS_EXPLOSION,Keys.CUSTOM_EXPLOSION,Keys.PROJECTILE_EXPLOSION,Keys.VEHICLE_EXPLOSION))
            assertFalse(proxy.hurt(new Source(key,new Actor()),200));
        assertEquals(0,proxy.targetReads);assertEquals(0,proxy.target.hits);
        for(String kind:List.of("mob_attack","arrow")) {
            assertTrue(proxy.hurt(new Source(new Key(kind),new Actor()),7));
        }
        assertEquals(2,proxy.target.hits);assertEquals(14,proxy.target.damage);
        proxy.world.isClientSide=true;
        assertFalse(proxy.hurt(new Source(new Key("arrow"),new Actor()),7));
        proxy.world.isClientSide=false;
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            assertFalse(proxy.hurt(new Source(new Key("arrow"),new Actor()),invalid));
        assertEquals(2,proxy.target.hits);
    }

    @Test void exactCustomExplosionBranchesPastExposureForTheCompiledProxy() throws Exception {
        ProxyPort proxy=create();
        var guard=Arrays.stream(proxy.getClass().getMethods()).filter(m->m.getName().equals("ignoreExplosion")).findFirst().orElseThrow();
        assertEquals(true,guard.invoke(proxy,guard.getParameterCount()==0?new Object[0]:new Object[]{new ExplosionPort()}));
        try(var zip=new ZipFile(java.nio.file.Path.of(System.getProperty("dnv.sbw.jar")).toFile())) {
            var node=new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/tools/CustomExplosion.class"))).accept(node,0);
            var method=node.methods.stream().filter(m->m.name.equals("m_46061_")||m.name.equals("explode")).findFirst().orElseThrow();
            // 0.8.9.2 extracts the entity loop; require the real explosion entry to call it.
            var extracted=node.methods.stream().filter(m->m.name.equals("damageEntities")&&m.desc.equals("()V")).findFirst();
            if(extracted.isPresent()) {
                assertTrue(Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).anyMatch(call->call.owner.equals(node.name)
                        &&call.name.equals("damageEntities")&&call.desc.equals("()V")));
                method=extracted.get();
            }
            MethodInsnNode skip=null,exposure=null;JumpInsnNode vehicleSkip=null;
            for(var insn:method.instructions) {
                if(insn instanceof MethodInsnNode call && (call.name.equals("m_6128_")||call.name.equals("ignoreExplosion")))skip=call;
                if(insn instanceof MethodInsnNode call && (call.name.equals("m_46064_")||call.name.equals("getSeenPercent")))exposure=call;
                if(insn instanceof TypeInsnNode type && type.getOpcode()==Opcodes.INSTANCEOF
                        && type.desc.equals("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity"))
                    vehicleSkip=(JumpInsnNode)nextCode(type);
            }
            assertNotNull(skip);assertNotNull(exposure);assertNotNull(vehicleSkip);
            var ignored=(JumpInsnNode)nextCode(skip);
            assertEquals(Opcodes.IFNE,ignored.getOpcode());
            assertSame(vehicleSkip.label,ignored.label,"Ignoring the proxy must skip to the same loop continuation as vehicles");
            assertTrue(method.instructions.indexOf(ignored.label)<method.instructions.indexOf(skip));
            assertTrue(method.instructions.indexOf(skip)<method.instructions.indexOf(exposure));
        }
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode insn) {
        do {insn=insn.getNext();}while(insn.getOpcode()<0);return insn;
    }
    public record Key(String id) { }
    public static class Keys {
        public static final Key IS_EXPLOSION=new Key("tag_explosion"),CUSTOM_EXPLOSION=new Key("custom_explosion"),
                PROJECTILE_EXPLOSION=new Key("projectile_explosion"),VEHICLE_EXPLOSION=new Key("vehicle_explosion");
    }
    public static class Source {
        private final Key key;private final Actor attacker;
        Source(Key key,Actor attacker){this.key=key;this.attacker=attacker;}
        public boolean is(Key expected){return key.equals(expected);}
        public Actor getEntity(){return attacker;}
    }
    public static class World {public boolean isClientSide;}
    public static class ExplosionPort { }
    public static class Actor {
        public int hits;public float damage;
        public boolean hurt(Source source,float amount){hits++;damage+=amount;return true;}
    }
    public abstract static class ProxyPort extends Actor {
        public World world=new World();public Actor target=new Actor();public int targetReads;
        public World level(){return world;}
        public Actor realTarget(){targetReads++;return target;}
    }
    private static ProxyPort create()throws Exception {
        String original="com/liucodex/doggiesnewvehicle/entity/DnvCombatProxyEntity";
        var node=new ClassNode();
        try(var in=CombatProxyExplosionExecutionTest.class.getClassLoader().getResourceAsStream(original+".class")) {
            assertNotNull(in);new ClassReader(in).accept(node,0);
        }
        node.methods.removeIf(m->!Set.of("hurt","ignoreExplosion").contains(m.name));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        node.superName=Type.getInternalName(ProxyPort.class);node.access=Opcodes.ACC_PUBLIC;
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxStack=1;ctor.maxLocals=1;node.methods.add(ctor);
        Map<String,String> names=new HashMap<>();String name="com/liucodex/doggiesnewvehicle/compat/ExecutedCombatProxy";
        names.put(original,name);
        for(var e:Map.ofEntries(
                Map.entry("net/minecraft/world/entity/Entity",Actor.class),
                Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/world/level/Explosion",ExplosionPort.class),
                Map.entry("net/minecraft/world/damagesource/DamageSource",Source.class),
                Map.entry("net/minecraft/resources/ResourceKey",Key.class),Map.entry("net/minecraft/tags/TagKey",Key.class),
                Map.entry("net/minecraft/tags/DamageTypeTags",Keys.class),
                Map.entry("com/atsuishio/superbwarfare/init/ModDamageTypes",Keys.class)).entrySet())names.put(e.getKey(),Type.getInternalName(e.getValue()));
        var out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(names)));byte[] bytes=out.toByteArray();
        Class<?> type=new ClassLoader(CombatProxyExplosionExecutionTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}
        }.define();
        return (ProxyPort)type.getConstructor().newInstance();
    }
}

package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute the exact selected SBW hurt armor branch, preserving its own bypass tag and native compute. */
class NuclearVehicleDamageBoundaryTest {
    @Test void nativeVehicleBypassSkipsArmorWhileRadiationKeepsOrdinaryNativeReduction()throws Exception {
        String vehicle="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity";
        String modifier="com/atsuishio/superbwarfare/entity/vehicle/damage/DamageModifier";
        ClassNode nativeNode=new ClassNode();
        try(var jar=new JarFile(SbwTestDependency.path().toFile());var in=jar.getInputStream(jar.getJarEntry(vehicle+".class"))){new ClassReader(in.readAllBytes()).accept(nativeNode,0);}
        MethodNode hurt=nativeNode.methods.stream().filter(m->m.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")).findFirst().orElseThrow();
        FieldInsnNode tag=Arrays.stream(hurt.instructions.toArray()).filter(n->n instanceof FieldInsnNode f&&f.name.equals("BYPASSES_VEHICLE")).map(n->(FieldInsnNode)n).findFirst().orElseThrow();
        AbstractInsnNode start=tag;
        while(!(start instanceof VarInsnNode v&&v.getOpcode()==Opcodes.FSTORE))start=start.getPrevious();
        int resultSlot=((VarInsnNode)start).var;start=start.getPrevious();
        JumpInsnNode branch=(JumpInsnNode)tag.getNext().getNext();assertEquals(Opcodes.IFNE,branch.getOpcode());
        Map<LabelNode,LabelNode> labels=new HashMap<>();for(var insn:hurt.instructions.toArray())if(insn instanceof LabelNode l)labels.put(l,new LabelNode());
        MethodNode slice=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"apply","(L"+vehicle+";Lnet/minecraft/world/damagesource/DamageSource;F)F",null,null);
        boolean compute=false;
        for(var n=start;;n=n.getNext()){
            if(!(n instanceof FrameNode||n instanceof LineNumberNode)){
                var copy=n.clone(labels);
                if(copy instanceof MethodInsnNode m){
                    if(m.owner.equals("net/minecraft/world/damagesource/DamageSource")&&m.desc.startsWith("(Lnet/minecraft/tags/TagKey;"))m.name="is";
                    if(m.owner.equals(modifier)&&m.name.equals("compute"))compute=true;
                }
                slice.instructions.add(copy);
            }
            if(n==branch.label)break;
        }
        assertTrue(compute);slice.instructions.add(new VarInsnNode(Opcodes.FLOAD,resultSlot));slice.instructions.add(new InsnNode(Opcodes.FRETURN));
        Map<String,String> names=Map.of(vehicle,Type.getInternalName(Vehicle.class),modifier,Type.getInternalName(Modifier.class),
            "net/minecraft/world/entity/Entity",Type.getInternalName(Body.class),"net/minecraft/world/damagesource/DamageSource",Type.getInternalName(Source.class),
            "net/minecraft/tags/TagKey",Type.getInternalName(Tag.class),"com/atsuishio/superbwarfare/init/ModTags$DamageTypes",Type.getInternalName(Tags.class));
        String target="com/liucodex/doggiesnewvehicle/compat/executionprobe/NuclearVehicleArmor";
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,target,null,"java/lang/Object",null);
        slice.accept(new MethodRemapper(writer.visitMethod(slice.access,slice.name,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}.mapMethodDesc(slice.desc),null,null),new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
        writer.visitEnd();
        var type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        var method=type.getMethod("apply",Vehicle.class,Source.class,float.class);var body=new Vehicle();
        assertEquals(1000F,(float)method.invoke(null,body,new Source(true),1000F));assertEquals(0,body.modifier.calls);
        assertEquals(100F,(float)method.invoke(null,body,new Source(false),1000F));assertEquals(1,body.modifier.calls);
        assertEquals(.75F,(float)method.invoke(null,body,new Source(false),7.5F));assertEquals(2,body.modifier.calls);
        // Verify the selected upstream tag name against the packaged JSON path.
        ClassNode tags=new ClassNode();try(var jar=new JarFile(SbwTestDependency.path().toFile());var in=jar.getInputStream(jar.getJarEntry("com/atsuishio/superbwarfare/init/ModTags$DamageTypes.class"))){new ClassReader(in.readAllBytes()).accept(tags,0);}
        var init=tags.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        var field=Arrays.stream(init.instructions.toArray()).filter(n->n instanceof FieldInsnNode f&&f.name.equals("BYPASSES_VEHICLE")).findFirst().orElseThrow();
        assertEquals("bypasses_vehicle",((LdcInsnNode)field.getPrevious().getPrevious()).cst);
    }
    public static class Body { }
    public static class Vehicle extends Body {final Modifier modifier=new Modifier();public Modifier getDamageModifier(){return modifier;}}
    public static class Modifier {int calls;public float compute(Body body,Source source,float amount){calls++;return amount*.1F;}}
    public static class Tag { }
    public static class Tags {public static final Tag BYPASSES_VEHICLE=new Tag();}
    public static class Source {final boolean bypass;Source(boolean bypass){this.bypass=bypass;}public boolean is(Tag tag){assertSame(Tags.BYPASSES_VEHICLE,tag);return bypass;}}
}

package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

class BlizzardItemColorExecutionTest {
    @Test void actualCryoItemTintRemainsOpaqueUnderArgbItemRendering() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/client/BlizzardRenderer",target=source+"TintProbe";
        var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){
            new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);
        }
        node.superName="java/lang/Object";node.fields.clear();node.innerClasses.clear();node.nestMembers=null;
        node.methods.removeIf(m->!m.name.startsWith("lambda$itemColor$"));
        assertEquals(1,node.methods.size());var method=node.methods.get(0);method.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String name){
            if(name.equals(source))return target;
            return name.equals("net/minecraft/world/item/ItemStack")?"java/lang/Object":name;
        }}));
        Class<?> probe=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        int color=(int)probe.getMethod(method.name,Object.class,int.class).invoke(null,null,0);
        assertEquals(255,color>>>24,"Neo 1.21.1 ItemRenderer uses tint alpha; RGB-only colors are invisible");
        assertEquals(0xB0DEF0,color&0xFFFFFF,"Keep the approved ice-blue tint");
        assertEquals(0,0xB0DEF0>>>24,"Old tint reproduces transparency");
    }
}

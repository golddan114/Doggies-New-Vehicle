package com.liucodex.doggiesnewvehicle.artillery.walker;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import static org.junit.jupiter.api.Assertions.*;

/** Data-flow contract: vanilla block renderers, especially shulkers, must retain their texture routing. */
class HexbotRenderContractTest {
    @Test void everyModelDrawReceivesTheOriginalNativeBufferSource()throws Exception{
        String owner="com/liucodex/doggiesnewvehicle/client/walker/HexbotRenderer";
        ClassNode node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+owner+".class")){
            assertNotNull(in);new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);
        }
        var render=node.methods.stream().filter(m->m.name.equals("render")&&m.desc.startsWith("(Lcom/liucodex/doggiesnewvehicle/artillery/walker/DnvHexbotEntity;")).findFirst().orElseThrow();
        BasicValue original=new BasicValue(Type.getObjectType("net/minecraft/client/renderer/MultiBufferSource"));
        int[] draws={0};
        var interpreter=new BasicInterpreter(Opcodes.ASM9){
            @Override public BasicValue newParameterValue(boolean instance,int local,Type type){
                return local==5?original:super.newParameterValue(instance,local,type);
            }
            @Override public BasicValue naryOperation(AbstractInsnNode instruction,List<? extends BasicValue> values)throws AnalyzerException{
                if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals("draw")){
                    assertSame(original,values.get(2),"A global buffer wrapper replaces the shulker atlas with the block atlas");draws[0]++;
                }
                return super.naryOperation(instruction,values);
            }
        };
        new Analyzer<>(interpreter).analyze(owner,render);assertTrue(draws[0]>=2,"Torso and leg model calls must both preserve native routing");
    }
}

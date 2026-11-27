package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real Neo Camera and SBW mixins: the caller finalizes TV after any setup return. */
class BlizzardNeoCameraOrderTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void tvRunsAfterNativeVehicleEarlyReturnInEitherConfigOrder(boolean dnvFirst) throws Exception {
        var fixture=new DnvMuzzleMixinApplicationTest();fixture.temp=temp;
        try(var loader=fixture.loader()) {
            var service=loader.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService");
            byte[][] transformed=(byte[][])service.getMethod("transformVehicleCamera",String.class,boolean.class)
                    .invoke(null,System.getProperty("dnv.sbw.jar"),dnvFirst);
            ClassNode node=new ClassNode();new ClassReader(transformed[1]).accept(node,0);
            var setup=node.methods.stream().filter(m->m.name.equals("setup")).findFirst().orElseThrow();
            ClassNode renderer=new ClassNode();new ClassReader(transformed[3]).accept(renderer,0);
            var after=renderer.methods.stream().filter(m->Arrays.stream(m.instructions.toArray())
                    .anyMatch(i->i instanceof MethodInsnNode call&&call.owner.endsWith("/BlizzardTvCamera")&&call.name.equals("afterSetup")))
                    .findFirst().orElseThrow();
            assertTrue(node.methods.stream().anyMatch(m->m.name.contains("onSetup")),"Native SBW callback must be applied too");
            assertTrue(node.interfaces.contains("com/liucodex/doggiesnewvehicle/client/mixin/BlizzardCameraAccess"));
            assertTrue(node.interfaces.contains("com/liucodex/doggiesnewvehicle/client/BlizzardCameraFrame"));
            assertTrue(Arrays.stream(setup.instructions.toArray()).filter(i->i.getOpcode()==Opcodes.RETURN).count()>=2,
                    "Check native cancellation and normal return");
            var calls=Arrays.stream(after.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).toList();
            assertTrue(calls.stream().anyMatch(c->c.name.equals("dnv$setupPartialTick")),
                    "TV must consume the effective camera input, including third-party argument edits");
            for (String owner : List.of("BlizzardTvCamera", "DnvM1RemoteCrewView"))
                assertEquals(1,calls.stream().filter(c->c.owner.endsWith("/" + owner)&&c.name.equals("afterSetup")).count(), owner);
            assertFalse(Arrays.stream(after.instructions.toArray()).anyMatch(i->i instanceof JumpInsnNode),
                    "Every normal or native-cancelled setup return must reach finalization");
            var render=renderer.methods.stream().filter(m->m.name.equals("renderLevel")).findFirst().orElseThrow();
            assertEquals(1,Arrays.stream(render.instructions.toArray()).filter(i->i instanceof MethodInsnNode c&&c.name.equals(after.name)).count());
            var renderCalls=Arrays.stream(render.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).toList();
            var original=renderCalls.stream().filter(c->c.owner.equals("net/minecraft/client/Camera")&&c.name.equals("setup")).toList();
            assertEquals(1,original.size(),"Retain the original setup invocation for other Mixins");
            var callback=renderCalls.stream().filter(c->c.name.equals(after.name)).findFirst().orElseThrow();
            assertTrue(renderCalls.indexOf(original.get(0))<renderCalls.indexOf(callback));
            for(var insn=original.get(0).getNext();insn!=callback;insn=insn.getNext())
                assertFalse(insn instanceof JumpInsnNode,"No branch may skip TV finalization after setup returns");
            var setupCalls=Arrays.stream(setup.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).toList();
            int capture=-1,nativeSetup=-1;
            for(int i=0;i<setupCalls.size();i++){
                if(setupCalls.get(i).name.contains("dnv$captureSetupPartial"))capture=i;
                if(setupCalls.get(i).name.contains("onSetup"))nativeSetup=i;
            }
            assertTrue(capture>=0&&nativeSetup>capture,"Capture the current frame before native cancellation");
        }
    }
}

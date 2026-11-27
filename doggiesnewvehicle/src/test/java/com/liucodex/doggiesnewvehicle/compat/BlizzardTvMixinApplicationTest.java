package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Applies real Mixins to development classes and, on Forge, the SRG runtime using generated refmaps. */
class BlizzardTvMixinApplicationTest {
    @TempDir Path temp;
    @ParameterizedTest @CsvSource({"world/entity/Entity,BlizzardTvMouseMixin,turn", "client/Camera,BlizzardCameraAccess,dnv$blizzardPosition", "client/Camera,BlizzardCameraFrameMixin,dnv$setupPartialTick", "client/renderer/GameRenderer,BlizzardCameraSetupMixin,afterSetup"})
    void inputAndCameraHooksApplyToTheActualRuntime(String target,String mixin,String member)throws Exception {
        var fixture=new DnvMuzzleMixinApplicationTest();fixture.temp=temp;
        String runtime=System.getProperty("dnv.minecraft.srg");
        for(boolean release:new boolean[]{false,true}) {
            if(release&&runtime==null)continue; // Neo uses official runtime names; the development pass is its actual mapping.
            try(var loader=fixture.loader()) {
                var service=loader.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService");
                byte[][] result=release?(byte[][])service.getMethod("transformReleaseOnSide",String.class,String.class,String.class,String.class,String.class,boolean.class)
                    .invoke(null,runtime,System.getProperty("dnv.artifact",""),"net/minecraft/"+target,mixin,"CLIENT",false)
                    :(byte[][])service.getMethod("transform",String.class,String.class,String.class,boolean.class)
                    .invoke(null,"net/minecraft/"+target,mixin,"CLIENT",false);
                assertFalse(Arrays.equals(result[0],result[1]));
                ClassNode node=new ClassNode();new ClassReader(result[1]).accept(node,0);
                if(mixin.equals("BlizzardCameraAccess") || mixin.equals("BlizzardCameraFrameMixin"))assertTrue(node.methods.stream().anyMatch(m->m.name.equals(member)));
                else assertEquals(1,node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                    .filter(call->call.owner.endsWith("/BlizzardTvCamera")&&call.name.equals(member)).count());
            }
        }
    }
}

package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Apply the production Mixin and execute the exact native energy/health draw section. */
class BlizzardEnergyHudExecutionTest {
    @TempDir Path temp;
    @Test void nativeBarsRemainVisibleAndBoundedWithoutChangingStoredEnergyOrOtherVehicles() throws Exception {
        var fixture=new DnvMuzzleMixinApplicationTest();fixture.temp=temp;
        try(var engine=fixture.loader()){
            String source="com/atsuishio/superbwarfare/client/overlay/VehicleHudOverlay";
            var service=engine.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService");
            String runtime=System.getProperty("dnv.minecraft.srg"),artifact=System.getProperty("dnv.artifact","");
            byte[][] result=runtime!=null&&!artifact.isBlank()
                    ?(byte[][])service.getMethod("transformReleaseOnSide",String.class,String.class,String.class,String.class,String.class,boolean.class)
                        .invoke(null,runtime,artifact,source,"BlizzardEnergyHudMixin","CLIENT",false)
                    :(byte[][])service.getMethod("transform",String.class,String.class,String.class,boolean.class)
                        .invoke(null,source,"BlizzardEnergyHudMixin","CLIENT",false);
            Object renderer=renderer(source,result[1]);
            var render=renderer.getClass().getMethod("renderBars",Context.class,Vehicle.class,Object.class,int.class);
            for(boolean mode2:List.of(false,true))for(int[] sample:new int[][]{
                    {0,2_000_000,0},{1_000_000,2_000_000,30},{2_000_000,2_000_000,60},
                    {Integer.MAX_VALUE,2_000_000,60},{-1,2_000_000,0},{100,0,0},{100,-1,0}}){
                var vehicle=new Blizzard();vehicle.mode2=mode2;vehicle.energy=sample[0];vehicle.capacity=sample[1];
                var context=new Context();render.invoke(renderer,context,vehicle,0,7);
                assertEquals(6,context.canvas.draws.size(),"Both native bars must remain visible");
                Draw energy=context.canvas.draws.get(2),health=context.canvas.draws.get(5);
                assertEquals("ENERGY",context.canvas.draws.get(0).sprite());
                assertEquals("ARMOR",context.canvas.draws.get(3).sprite());
                assertEquals(20F,energy.x());assertEquals(272F,energy.y());assertEquals(sample[2],energy.width());
                assertEquals(20F,health.x());assertEquals(281F,health.y());assertEquals(30F,health.width());
                assertEquals(sample[0],vehicle.energy,"HUD must never alter authoritative FE");
                assertEquals(sample[1],vehicle.capacity);assertEquals(750F,vehicle.health);
            }
            var ordinary=new Vehicle();ordinary.energy=3_000_000;
            var context=new Context();render.invoke(renderer,context,ordinary,0,0);
            assertEquals(90F,context.canvas.draws.get(2).width(),"Other vehicles keep their native projection");
            ordinary.storage=false;context=new Context();render.invoke(renderer,context,ordinary,0,0);
            assertEquals(3,context.canvas.draws.size());assertEquals("ARMOR",context.canvas.draws.get(0).sprite());
            assertTrue(context.canvas.rectangles.isEmpty(), "Other vehicles do not receive the jump HUD");
            var speedRow=Arrays.stream(renderer.getClass().getDeclaredMethods())
                    .filter(m->m.getName().contains("dnv$jumpSpeedRow")).findFirst().orElseThrow();
            speedRow.setAccessible(true);
            for (int height : new int[] {180, 300, 540}) for (int offset : new int[] {0, 7, 9, 17})
                for (int[] charge : new int[][] {{1200,0},{600,18},{1,35},{0,36},{-1,36},{Integer.MAX_VALUE,0}}) {
                    var vehicle=new Blizzard();vehicle.energy=100_000;vehicle.remaining=charge[0];
                    context=new Context();context.height=height;render.invoke(renderer,context,vehicle,0,offset);
                    var rectangles=context.canvas.rectangles;
                    assertEquals(charge[1]==0?6:7, rectangles.size());
                    Rect frame=rectangles.get(4),background=rectangles.get(5);
                    Draw nativeEnergy=context.canvas.draws.get(2),nativeHealth=context.canvas.draws.get(5);
                    assertEquals(nativeEnergy.y(),frame.y1());
                    assertTrue(frame.x1()>nativeEnergy.x()+60);
                    assertTrue(frame.y2()<nativeHealth.y());
                    int speedOffset=(int)speedRow.invoke(renderer,context.canvas,vehicle,320,height,0F,offset);
                    assertTrue(frame.y2()<height-22-speedOffset,"Native speed text must clear the charge bar");
                    assertEquals(nativeHealth.y()-1,height-22-speedOffset);
                    assertEquals(offset,speedRow.invoke(renderer,context.canvas,ordinary,320,height,0F,offset));
                    assertEquals(38,frame.x2()-frame.x1());assertEquals(6,frame.y2()-frame.y1());
                    assertTrue(frame.x2()<320 && frame.y1()>=0 && frame.y2()<=height);
                    if(charge[1]>0) {
                        Rect fill=rectangles.get(6);
                        assertEquals(charge[1],fill.x2()-fill.x1());
                        assertTrue(fill.x1()>=background.x1()&&fill.x2()<=background.x2());
                        assertEquals(charge[0]<=0?0xFF8EEAFF:0xFF409CB5,fill.color());
                    }
                    assertEquals(charge[0],vehicle.remaining);assertEquals(100_000,vehicle.energy);
                }
            for(int reason=0;reason<5;reason++) {
                var vehicle=new Blizzard();vehicle.energy=100_000;
                if(reason==0)vehicle.mode2=true;if(reason==1)vehicle.tv=true;
                if(reason==2)vehicle.energy=99_999;if(reason==3)vehicle.alive=false;if(reason==4)vehicle.wreck=true;
                context=new Context();render.invoke(renderer,context,vehicle,0,0);
                assertEquals(0xFF74828B,context.canvas.rectangles.get(6).color());
                assertEquals(36,context.canvas.rectangles.get(6).x2()-context.canvas.rectangles.get(6).x1());
            }
        }
    }
    private static Object renderer(String source,byte[] bytes)throws Exception {
        var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_FRAMES);
        var render=node.methods.stream().filter(m->m.name.equals("render")).findFirst().orElseThrow();
        AbstractInsnNode begin=null,end=null;int blits=0;
        for(var instruction:render.instructions){
            if(instruction instanceof MethodInsnNode call&&call.name.equals("getArmorPlateCompatHeight")){
                var store=instruction.getNext();while(store.getOpcode()<0)store=store.getNext();
                assertEquals(Opcodes.ISTORE,store.getOpcode());begin=store.getNext();
            }
            if(instruction instanceof MethodInsnNode call&&call.name.equals("preciseBlit")&&++blits==6){end=instruction;break;}
        }
        assertNotNull(begin);assertNotNull(end);
        while(render.instructions.getFirst()!=begin)render.instructions.remove(render.instructions.getFirst());
        while(render.instructions.getLast()!=end)render.instructions.remove(render.instructions.getLast());
        render.instructions.add(new InsnNode(Opcodes.RETURN));render.name="renderBars";
        render.desc="(Lcom/atsuishio/superbwarfare/client/overlay/RenderContext;Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;Lcom/mojang/blaze3d/vertex/PoseStack;I)V";
        render.signature=null;render.localVariables=null;render.tryCatchBlocks.clear();
        node.methods.removeIf(m->m!=render&&!m.name.contains("dnv$"));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
        node.access=Opcodes.ACC_PUBLIC;node.superName="java/lang/Object";
        for(var method:node.methods)for(var instruction:method.instructions.toArray())
            if(instruction instanceof FieldInsnNode field&&field.owner.equals(source)){
                assertEquals("Lnet/minecraft/resources/ResourceLocation;",field.desc);
                method.instructions.set(field,new LdcInsnNode(field.name));
            }
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        String target="com/liucodex/doggiesnewvehicle/compat/hudprobe/NativeBars";
        var names=new HashMap<String,String>();names.put(source,target);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Vehicle.class);
        NuclearRecoveryExecutionTest.map(names,"com/mojang/blaze3d/vertex/PoseStack",Object.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Blizzard.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/client/overlay/RenderContext",Context.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/client/RenderHelper",Recorder.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/gui/GuiGraphics",Canvas.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/resources/ResourceLocation",String.class);
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){
            @Override public String map(String name){return names.getOrDefault(name,name);}
            @Override public String mapMethodName(String owner,String name,String descriptor){
                // Exact Forge 1.20.1 SRG aliases used by the packaged HUD; source/Neo names pass through.
                if(owner.equals("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity")
                        &&name.equals("m_6084_")&&descriptor.equals("()Z"))return "isAlive";
                if(owner.equals("net/minecraft/client/gui/GuiGraphics")
                        &&name.equals("m_280046_")&&descriptor.equals("(IIIIII)V"))return "fill";
                return name;
            }
        }));
        return new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.')).getConstructor().newInstance();
    }
    public static class Vehicle {
        int energy,capacity=2_000_000;float health=750;boolean storage=true;
        public boolean hasEnergyStorage(){return storage;}public int getEnergy(){return energy;}public int getMaxEnergy(){return capacity;}
        public float getHealth(){return health;}public float getMaxHealth(){return 1500;}
    }
    public static class Blizzard extends Vehicle {
        boolean mode2,tv,wreck,alive=true;int remaining;
        public int jumpCooldownTicks(){return remaining;}public boolean hovering(){return !mode2;}
        public boolean hasTv(){return tv;}public boolean isWreck(){return wreck;}public boolean isAlive(){return alive;}
    }
    public static class Context {
        int height=300;final Canvas canvas=new Canvas();public Canvas getGuiGraphics(){return canvas;}public int getScreenHeight(){return height;}
    }
    public record Draw(String sprite,float x,float y,float width) { }
    public record Rect(int x1,int y1,int x2,int y2,int color) { }
    public static class Canvas {
        final List<Draw> draws=new ArrayList<>();final List<Rect> rectangles=new ArrayList<>();
        public void fill(int x1,int y1,int x2,int y2,int z,int color){assertEquals(100,z);rectangles.add(new Rect(x1,y1,x2,y2,color));}
    }
    public static class Recorder {
        public static void preciseBlit(Canvas canvas,String sprite,float x,float y,float z,float u,float v,float width,float height,float tw,float th){
            canvas.draws.add(new Draw(sprite,x,y,width));
        }
    }
}

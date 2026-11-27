package com.liucodex.libertad.ship;

import com.liucodex.libertad.naval.AirDefenseRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute the registered production sound factory without initializing Forge registries. */
public final class RuntimeSoundCheck {
    public static void main(String[] args) throws Exception {
        RuntimePhysicsCheck.bootstrapStandalone();
        String source="com/liucodex/libertad/Libertad", target=source+"SoundExecution";
        var node=new ClassNode();
        String archive=System.getProperty("libertad.sound.archive");
        if(archive==null) {
            try(var in=RuntimeSoundCheck.class.getResourceAsStream("/"+source+".class")) {new ClassReader(in.readAllBytes()).accept(node,0);}
        } else {
            try(var zip=new java.util.zip.ZipFile(archive);var in=zip.getInputStream(zip.getEntry(source+".class"))) {
                new ClassReader(in.readAllBytes()).accept(node,0);
            }
        }
        node.methods.removeIf(m->!m.name.equals("mainCannonSound")&&!m.name.equals("airDefenseSound"));node.fields.clear();node.innerClasses.clear();node.nestMembers=null;
        node.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
        var out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(source,target) {
            @Override public String mapMethodName(String owner,String name,String descriptor) {
                return owner.equals("net/minecraft/sounds/SoundEvent")&&name.equals("m_262856_")
                        ? "createFixedRangeEvent" : name;
            }
        }));
        Class<?> factory=new ClassLoader(RuntimeSoundCheck.class.getClassLoader()) {
            Class<?> define(){return defineClass(target.replace('/','.'),out.toByteArray(),0,out.toByteArray().length);}
        }.define();
        var sound=(SoundEvent)factory.getMethod("mainCannonSound").invoke(null);
        float previous=SoundEvent.createVariableRangeEvent(new ResourceLocation("libertad","main_cannon_fire")).getRange(6);
        if(previous!=96 || sound.getRange(6)<240)throw new AssertionError("main cannon dispatch cannot cover ship and existing audible falloff");
        System.out.println("PASS: native SoundEvent range 96 -> "+sound.getRange(6)+" blocks at unchanged volume 6; covers 150-block hull and 240-block falloff");
        var aa=(SoundEvent)factory.getMethod("airDefenseSound").invoke(null);
        byte[] soundJson;
        byte[] burstHeader;
        String burstPath="assets/libertad/sounds/air_defense_burst.ogg";
        if(archive==null) {
            try(var in=RuntimeSoundCheck.class.getResourceAsStream("/assets/libertad/sounds.json")) { soundJson=in.readAllBytes(); }
            try(var in=RuntimeSoundCheck.class.getResourceAsStream("/"+burstPath)) { burstHeader=in.readNBytes(4); }
        } else {
            try(var zip=new java.util.zip.ZipFile(archive);
                var in=zip.getInputStream(zip.getEntry("assets/libertad/sounds.json"));
                var burst=zip.getInputStream(zip.getEntry(burstPath))) {
                soundJson=in.readAllBytes();
                burstHeader=burst.readNBytes(4);
            }
        }
        var entries=com.google.gson.JsonParser.parseString(new String(soundJson,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("air_defense_fire").getAsJsonArray("sounds");
        var clip=entries.get(0).getAsJsonObject();
        int distance=clip.get("attenuation_distance").getAsInt();
        float volume=AirDefenseRules.FIRE_VOLUME;
        if(entries.size()!=1||!clip.get("name").getAsString().equals("libertad:air_defense_burst")
                ||!java.util.Arrays.equals(burstHeader,new byte[]{'O','g','g','S'})
                ||volume<=0||volume>=1||aa.getRange(volume)!=200||distance*Math.max(1,volume)!=200)
            throw new AssertionError("AA needs one burst sample with reduced gain and 200-block falloff and dispatch");
        System.out.println("PASS: one AA burst clip, volume " + volume + ", falloff and dispatch 200 blocks");
    }
}

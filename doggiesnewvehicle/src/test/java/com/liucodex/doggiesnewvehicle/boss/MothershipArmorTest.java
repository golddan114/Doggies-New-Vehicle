package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Exact native string parsing, source matching and mitigation. Serialization and entity registries are ports. */
class MothershipArmorTest {
    @Test void mothershipUsesOneEntityOwnedArmorPresetWithoutStackingLegacyJsonModifiers() throws Exception {
        var data=JsonParser.parseString(Files.readString(Path.of("src/boss/resources/data/doggievehicle/sbw/vehicles/ses_executor_of_truth.json"))).getAsJsonObject();
        Loader loader=new Loader();Class<?> rule=loader.loadClass((Loader.GENERATED+"DamageModify").replace('/','.'));
        List<Object> modifiers=new ArrayList<>();
        for(var value:data.getAsJsonArray("DamageModifiers")) {
            Object modifier=rule.getConstructor().newInstance();rule.getMethod("deserializeFromString",String.class).invoke(modifier,value.getAsString());modifiers.add(modifier);
        }
        assertTrue(modifiers.isEmpty(),"The former shell-specific JSON rules must not stack with superheavy armor");
        for(String id:List.of("superbwarfare:javelin_missile","superbwarfare:rpg_rocket_standard","superbwarfare:projectile","dnv_noise:laser_bolt"))
            assertEquals(1000,apply(rule,modifiers,id,1000),id);
        assertEquals(8000,data.get("MaxHealth").getAsInt());
        assertEquals(200,com.liucodex.doggiesnewvehicle.core.DnvArmorRules.apply(
                com.liucodex.doggiesnewvehicle.core.DnvArmorRules.Profile.SUPERHEAVY,1000),.001);
        try(var input=getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity.class")) {
            ClassNode mother=new ClassNode();new ClassReader(input).accept(mother,0);
            assertTrue(mother.methods.stream().anyMatch(m->m.name.equals("getDamageModifier")),
                    "Entity-owned armor must pass once through the native vehicle path");
        }
    }
    private static float apply(Class<?> rule,List<Object> rules,String projectile,float damage)throws Exception {
        Source source=new Source(new Actor(projectile));
        for(Object modifier:rules)if((boolean)rule.getMethod("match",Source.class).invoke(modifier,source))
            damage=(float)rule.getMethod("compute",Actor.class,Source.class,float.class).invoke(modifier,new Actor("doggievehicle:ses_executor_of_truth"),source,damage);
        return damage;
    }
    public enum ModifyType { IMMUNITY,REDUCE,MULTIPLY,CUSTOM,INVALID }
    public enum SourceType { TAG_KEY,RESOURCE_KEY,FUNCTION,ENTITY_ID,ENTITY_TAG,ALL }
    public interface Custom {float compute(Actor entity,Source source,float damage);}
    public record Kind(String id) {public static ResourceLocation getKey(Kind kind){return new ResourceLocation(kind.id);}}
    public record Actor(String id) {public Kind getType(){return new Kind(id);}}
    public record Source(Actor direct) {public Actor getDirectEntity(){return direct;}public Actor getEntity(){return new Actor("minecraft:player");}}
    private static final class Loader extends ClassLoader {
        private static final String NATIVE="com/atsuishio/superbwarfare/entity/vehicle/damage/";
        private static final String GENERATED="com/liucodex/doggiesnewvehicle/boss/armorprobe/";
        private final Map<String,String> types=new HashMap<>(),methods=new HashMap<>();
        Loader()throws Exception {
            types.put(NATIVE+"DamageModify",GENERATED+"DamageModify");types.put(NATIVE+"DamageModify$WhenMappings",GENERATED+"DamageModify$WhenMappings");
            for(var entry:Map.of(NATIVE+"DamageModify$ModifyType",ModifyType.class,NATIVE+"DamageModify$SourceType",SourceType.class,
                    NATIVE+"DamageModifier$CustomDamageModifier",Custom.class,"net/minecraft/world/entity/Entity",Actor.class,
                    "net/minecraft/world/entity/EntityType",Kind.class,"net/minecraft/world/damagesource/DamageSource",Source.class).entrySet())
                types.put(entry.getKey(),Type.getInternalName(entry.getValue()));
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] split=line.split(" ");if(split[0].equals("MD:"))methods.put(shortName(split[1]),shortName(split[3]));
            }
        }
        private static String shortName(String name){return name.substring(name.lastIndexOf('/')+1);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            if(!name.replace('.','/').startsWith(GENERATED))throw new ClassNotFoundException(name);
            String simple=name.substring(name.lastIndexOf('.')+1);
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())) {
                ClassNode node=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(NATIVE+simple+".class"))).accept(node,0);
                if(simple.equals("DamageModify")) {
                    MethodNode clinit=node.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
                    AbstractInsnNode start=null;
                    for(var ins:clinit.instructions)if(ins instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.startsWith("^(?<prefix>")){start=ins;break;}
                    assertNotNull(start);
                    while(clinit.instructions.getFirst()!=start)clinit.instructions.remove(clinit.instructions.getFirst());
                    clinit.localVariables=null;clinit.tryCatchBlocks.clear();
                    node.methods.removeIf(m->!Set.of("<init>","<clinit>","deserializeFromString","generateSourceType","match","compute").contains(m.name)
                            ||m.name.equals("<init>")&&!m.desc.equals("()V"));
                    node.interfaces.clear();node.innerClasses.clear();node.visibleAnnotations=null;
                }
                ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(new ClassRemapper(out,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String owner,String value,String desc){return methods.getOrDefault(value,value);}
                }));
                byte[] bytes=out.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception failure){throw new ClassNotFoundException(name,failure);}
        }
    }
}

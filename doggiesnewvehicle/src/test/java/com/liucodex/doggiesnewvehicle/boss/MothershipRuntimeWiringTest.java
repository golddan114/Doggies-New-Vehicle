package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Compiled Forge/SBW adapter and resource contracts, separate from gameplay simulation. */
class MothershipRuntimeWiringTest {
    private static final String ENTITY="com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity";
    private static final String BOSS="com/liucodex/doggiesnewvehicle/boss/";
    @Test void vehicleConfigurationUsesExactDependencyEnumSpellingAndTwentyThousandHealth() throws Exception {
        var data=JsonParser.parseString(Files.readString(Path.of("src/boss/resources/data/doggievehicle/sbw/vehicles/ses_executor_of_truth.json"))).getAsJsonObject();
        assertEquals(8000,data.get("MaxHealth").getAsInt());assertFalse(data.get("KeepChunkLoaded").getAsBoolean());
        var types=serializedEnumNames("VehicleType");
        var engines=serializedEnumNames("EngineType");
        assertTrue(types.contains(data.get("Type").getAsString()));assertTrue(engines.contains(data.get("EngineType").getAsString()));
        assertFalse(types.contains("Airship"),"Case-sensitive negative control");
        assertTrue(data.getAsJsonArray("Seats").isEmpty());
        assertTrue(data.getAsJsonArray("OBB").isEmpty(),"The mesh rig supplies the compound collision shape");
        assertEquals(90,com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.get().dimensions.z(),.00001);
    }
    @Test void actualModelRendererHasNoCombatWritesAndUsesOneEmissionAndOneBaseMaterial() throws Exception {
        var node=compiled(BOSS+"client/DnvMothershipRenderer");
        for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call) {
            assertFalse(List.of("hurt","doDamage","addFreshEntity","setBlock","getEntities","keepChunkLoaded","setHealth").contains(call.name));
        }
        assertTrue(calls(method(node,"draw"),"eyes"));assertTrue(calls(method(node,"draw"),"entityCutoutNoCull"));
        assertTrue(calls(method(node,"render"),"enqueue"));
        assertFalse(calls(method(node,"render"),"renderAt"));
        assertTrue(calls(method(node,"renderFx"),"renderAt"));
        assertTrue(calls(method(node,"renderFx"),"renderBeamEnd"));
        assertFalse(calls(method(node,"renderFx"),"renderBeamOffset"));
        assertTrue(calls(method(node,"render"),"renderPose"));
    }
    @Test void fxBlendUsesVertexAlphaAndSceneDepthWithoutWritingInvisibleSurfaceDepth()throws Exception {
        var fields=new java.util.HashSet<String>();
        var type=compiled("com/liucodex/doggiesnewvehicle/client/fx/DnvJetRenderType");
        assertTrue(calls(method(type,"<clinit>"),"textured"),"Default jets must use the same textured state factory");
        for(var instruction:method(type,"textured").instructions)
            if(instruction instanceof FieldInsnNode field)fields.add(field.name);
        assertTrue(fields.containsAll(List.of("LIGHTNING_TRANSPARENCY","LEQUAL_DEPTH_TEST","COLOR_WRITE",
                "NO_CULL","RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER")));
        assertFalse(fields.contains("ADDITIVE_TRANSPARENCY"));assertFalse(fields.contains("NO_DEPTH_TEST"));
        assertFalse(fields.contains("COLOR_DEPTH_WRITE"));
        var renderer=compiled(BOSS+"client/MothershipFxRenderer");
        for(var method:renderer.methods)for(var instruction:method.instructions)if(instruction instanceof FieldInsnNode field)
            assertNotEquals("com/atsuishio/superbwarfare/client/renderer/ModRenderTypes",field.owner);
    }
    @Test void baseVehicleCannotLoadChunksOrTakeOverTheServerFlightWriter() throws Exception {
        var node=compiled(ENTITY);
        for(String method:List.of("travel","keepChunkLoaded","towedTick","towingTick","push","pushNew","preventStacking"))
            assertFalse(java.util.stream.StreamSupport.stream(method(node,method).instructions.spliterator(),false).anyMatch(MethodInsnNode.class::isInstance),method);
        assertTrue(calls(method(node,"moveShip"),"safeStep"));assertTrue(calls(method(node,"tick"),"moveShip"));
        assertTrue(node.methods.stream().filter(m->m.name.startsWith("lambda$tickStations$")).anyMatch(m->calls(m,"obstructed")));
    }
    @Test void fireUsesForgeGriefingAndPlacementVetoAndSpawnReservesThroughTheController() throws Exception {
        var node=compiled(ENTITY);var blast=method(node,"blast");var fire=method(node,"igniteGround");
        assertTrue(calls(blast,"begin"));assertFalse(calls(blast,"setBlock"));
        assertTrue(calls(method(node,"tick"),"igniteGround"));
        assertTrue(calls(fire,"allowed"));assertTrue(calls(fire,"place"));
        assertFalse(calls(fire,"setBlock"));assertTrue(calls(method(node,"tickDrops"),"deploy"));
        assertTrue(calls(method(node,"prepareDrop"),"refreshBoundingBoxFromOBBs"));
        assertTrue(calls(method(node,"addAdditionalSaveData"),"m_7380_"));assertTrue(calls(method(node,"readAdditionalSaveData"),"m_7378_"));
    }
    private static ClassNode compiled(String name) throws Exception {
        var node=new ClassNode();try(var in=MothershipRuntimeWiringTest.class.getClassLoader().getResourceAsStream(name+".class")){assertNotNull(in);new ClassReader(in).accept(node,0);}return node;
    }
    private static java.util.Set<String> serializedEnumNames(String type) throws Exception {
        try(var zip=new java.util.zip.ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            var node=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/data/vehicle/subdata/"+type+".class"))).accept(node,0);
            var serializer=node.methods.stream().filter(m->m.name.startsWith("_init_")).findFirst().orElseThrow();
            assertTrue(calls(serializer,"createAnnotatedEnumSerializer"));
            var values=new java.util.HashSet<String>();
            for(var instruction:serializer.instructions)if(instruction instanceof LdcInsnNode literal && literal.cst instanceof String text)values.add(text);
            return values;
        }
    }
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static boolean calls(MethodNode method,String name){for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals(name))return true;return false;}
}

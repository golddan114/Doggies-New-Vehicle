package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact upstream control flow plus actual DNV compiled call sites/resources; not an in-game damage test. */
class MothershipDamageContractTest {
    private static final Path RES = Path.of("src/boss/resources");
    private static final Set<String> PIERCING = Set.of("doggiesnewvehicle:boss_piercing_laser", "doggiesnewvehicle:mothership_doomsday_blast");
    private static final String PREFIX = "com/atsuishio/superbwarfare/";

    @Test
    void onlyOurDamageTypesBypassArmorVehicleMitigationAndSameTickCooldown() throws Exception {
        for (String tag : List.of("minecraft/bypasses_armor", "minecraft/bypasses_cooldown", "superbwarfare/bypasses_vehicle")) {
            String[] parts = tag.split("/");
            var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources").resolve("data/" + parts[0] + "/tags/damage_type/" + parts[1] + ".json")))
                    .getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean());
            assertEquals(Set.of("doggiesnewvehicle:boss_piercing_laser", "doggiesnewvehicle:mothership_doomsday_blast", "doggiesnewvehicle:blizzard_cold"),
                    StreamSupport.stream(json.getAsJsonArray("values").spliterator(), false)
                    .map(e -> e.getAsJsonObject().get("id").getAsString()).collect(Collectors.toSet()));
            json.getAsJsonArray("values").forEach(e -> assertFalse(e.getAsJsonObject().get("required").getAsBoolean()));
        }
        for (String id : PIERCING) {
            var data = JsonParser.parseString(Files.readString(RES.resolve("data/" + id.replace(":", "/damage_type/") + ".json")))
                    .getAsJsonObject();
            assertEquals("never", data.get("scaling").getAsString());
        }
        assertFalse(Files.exists(RES.resolve("data/minecraft/tags/damage_type/bypasses_invulnerability.json")));
    }

    @Test
    void upstreamTagTrueBranchActuallyJumpsPastTheVehicleDamageModifier() throws Exception {
        MethodNode hurt = upstream("entity/vehicle/base/VehicleEntity").methods.stream()
                .filter(m -> m.name.equals("m_6469_")).findFirst().orElseThrow();
        var instructions = hurt.instructions;
        FieldInsnNode tag = StreamSupport.stream(instructions.spliterator(), false)
                .filter(n -> n instanceof FieldInsnNode field && field.name.equals("BYPASSES_VEHICLE"))
                .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
        var next = tag.getNext();
        while (!(next instanceof JumpInsnNode)) next = next.getNext();
        JumpInsnNode branch = (JumpInsnNode) next;
        assertEquals(Opcodes.IFNE, branch.getOpcode());
        MethodInsnNode compute = StreamSupport.stream(instructions.spliterator(), false)
                .filter(n -> n instanceof MethodInsnNode method && method.name.equals("compute") && method.owner.endsWith("DamageModifier"))
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertTrue(instructions.indexOf(branch) < instructions.indexOf(compute));
        assertTrue(instructions.indexOf(compute) < instructions.indexOf(branch.label));
    }

    @Test
    void annihilatorLaserAndExplosionRetainTheVehicleAsTheirDirectSource() throws Exception {
        var annihilator = upstream("entity/vehicle/AnnihilatorEntity");
        var laser = annihilator.methods.stream().filter(m -> m.name.equals("laserLengthEntity")).findFirst().orElseThrow();
        var laserSource = StreamSupport.stream(laser.instructions.spliterator(), false)
                .filter(n -> n instanceof MethodInsnNode m && m.name.equals("causeLaserDamage"))
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        // The pilot can be absent; the second argument remains the Annihilator itself.
        var pilot = (VarInsnNode) previous(laserSource.getPrevious());
        assertEquals(Opcodes.ALOAD, pilot.getOpcode());
        assertEquals(14, pilot.var);
        var direct = (VarInsnNode) previous(previous(pilot.getPrevious()).getPrevious());
        assertEquals(Opcodes.ALOAD, direct.getOpcode());
        assertEquals(0, direct.var);

        var blast = annihilator.methods.stream().filter(m -> m.name.equals("causeLaserExplode")).findFirst().orElseThrow();
        assertTrue(calls(blast).contains(PREFIX + "entity/vehicle/AnnihilatorEntity.createCustomExplosion"));
        assertTrue(calls(blast).contains(PREFIX + "tools/CustomExplosion$Builder.attacker"));
        assertFalse(calls(blast).stream().anyMatch(c -> c.endsWith(".directSource") || c.endsWith(".source")));

        var create = upstream("entity/vehicle/base/VehicleEntity").methods.stream()
                .filter(m -> m.name.equals("createCustomExplosion")).findFirst().orElseThrow();
        var constructor = StreamSupport.stream(create.instructions.spliterator(), false)
                .filter(n -> n instanceof MethodInsnNode m && m.name.equals("<init>")
                        && m.owner.equals(PREFIX + "tools/CustomExplosion$Builder"))
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        var vehicle = (VarInsnNode) previous(previous(constructor.getPrevious()).getPrevious());
        assertEquals(Opcodes.ALOAD, vehicle.getOpcode());
        assertEquals(0, vehicle.var);
        var builder = upstream("tools/CustomExplosion$Builder");
        var init = builder.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        assertTrue(StreamSupport.stream(init.instructions.spliterator(), false)
                .anyMatch(n -> n instanceof FieldInsnNode field && field.name.equals("sourceEntity")
                        && field.getOpcode() == Opcodes.PUTFIELD
                        && previous(field.getPrevious()) instanceof FieldInsnNode source
                        && source.name.equals("directSource")));
    }

    @Test
    void realNoiseFireUsesOurSourceWhileKeepingTheExistingDamageCadenceOwner() throws Exception {
        var noise = compiled("com/liucodex/noise/entity/AbstractNoiseVehicleWeapons");
        var fire = noise.methods.stream().filter(m -> m.name.equals("fireMainGun")).findFirst().orElseThrow();
        assertTrue(calls(fire).contains("com/liucodex/doggiesnewvehicle/combat/DnvDamageTypes.piercingLaser"));
        String laserHit = "com/liucodex/doggiesnewvehicle/boss/DnvBossLaserHit";
        assertTrue(calls(fire).contains(laserHit + ".apply"));
        var impact = compiled(laserHit).methods.stream().filter(m -> m.name.equals("apply")).findFirst().orElseThrow();
        assertTrue(calls(impact).contains(PREFIX + "tools/DamageHandler.doDamage"));
        assertFalse(calls(fire).stream().anyMatch(c -> c.endsWith(".setHealth")));
        var state = compiled("com/liucodex/noise/entity/AbstractNoiseVehicleState");
        for (var expected : java.util.Map.of("MAIN_DAMAGE", 60.0F, "MAIN_RELOAD", 160,
                "MAIN_INTERVAL", 20, "MAIN_MAGAZINE", 6).entrySet()) {
            var value = state.fields.stream().filter(f -> f.name.equals(expected.getKey())).findFirst().orElseThrow().value;
            assertEquals(((Number) expected.getValue()).doubleValue(), ((Number) value).doubleValue());
        }
    }

    @Test
    void nativeSecondaryProjectilesEnableTerrainDestructionAndTheExactDependencyUsesTheFlag() throws Exception {
        var fire = compiled("com/liucodex/doggiesnewvehicle/boss/MothershipWeapons").methods.stream()
                .filter(m -> m.name.equals("fireSecondary")).findFirst().orElseThrow();
        var flags = StreamSupport.stream(fire.instructions.spliterator(), false)
                .filter(n -> n instanceof MethodInsnNode m && m.name.equals("setExplosionDestroy"))
                .map(MethodInsnNode.class::cast).toList();
        assertEquals(2, flags.size());
        for (var flag : flags) {
            var argument = flag.getPrevious();
            while (argument.getOpcode() < 0) argument = argument.getPrevious();
            assertEquals(Opcodes.ICONST_1, argument.getOpcode());
        }
        var projectile = upstream("entity/projectile/FastThrowableProjectile");
        for (String name : List.of("m_7378_", "m_7380_")) {
            var method = projectile.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            assertTrue(StreamSupport.stream(method.instructions.spliterator(), false)
                    .anyMatch(n -> n instanceof org.objectweb.asm.tree.LdcInsnNode literal && "ExplosionDestroy".equals(literal.cst)));
        }
        var build = projectile.methods.stream().filter(m -> m.name.equals("buildExplosion")).findFirst().orElseThrow();
        assertTrue(calls(build).stream().anyMatch(c -> c.endsWith(".destroyBlock")));
    }

    @Test
    void fieldMixRetainsRequestedSourceIdentityAndIndependentBossAssets() throws Exception {
        Path source = Path.of("src/artillery/resources/assets/doggiesnewvehicle/sounds");
        var mix=JsonParser.parseString(Files.readString(Path.of("../tools/dnv-mothership-fx-preview/field-audio-mix.json")))
                .getAsJsonObject().getAsJsonObject("samples");
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        assertEquals(mix.getAsJsonObject("charge").get("input_sha256").getAsString(),
                java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(source.resolve("jingzhe_activate.ogg")))));
        for(var entry:mix.entrySet()) {
            var data=entry.getValue().getAsJsonObject();
            assertEquals(data.get("output_sha256").getAsString(),java.util.HexFormat.of().formatHex(digest.digest(
                    Files.readAllBytes(RES.resolve("assets/dnv_mothership/sounds/"+entry.getKey()+".ogg")))));
            assertTrue(data.get("peak").getAsDouble()<1);assertTrue(data.get("waveform_correlation").getAsDouble()>.9);
            assertEquals(1,data.get("channels").getAsInt());
        }
        assertArrayEquals(Files.readAllBytes(source.resolve("jingzhe_attack.ogg")),
                Files.readAllBytes(RES.resolve("assets/dnv_mothership/sounds/fire.ogg")));
    }

    @Test void selectedMachineryAndAllThreeStationEventsResolveToTheirExactResources()throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        assertEquals("e8a0a57209b49dc847f9bab33160cff5e3fde946fef77b82a9008686d7cf75a0",java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(RES.resolve("assets/dnv_mothership/sounds/idle.ogg")))));
        assertEquals("8f8af3d562b060b236024313b6e8c6f023b423edebf54086a16b66c241f72947",java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(RES.resolve("assets/dnv_mothership/sounds/propulsion.ogg")))));
        var sounds=JsonParser.parseString(Files.readString(RES.resolve("assets/dnv_mothership/sounds.json"))).getAsJsonObject();
        assertFalse(sounds.has("thrust"));
        assertTrue(sounds.has("propulsion"));
        try(var zip=new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            var nativeEvents=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/superbwarfare/sounds.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:java.util.Map.of("autocannon_fire","bmp_2_cannon_fire_3p","rocket_fire","rpg_fire_3p","laser_fire","waveforce_tower_fire").entrySet()) {
                var samples=sounds.getAsJsonObject(entry.getKey()).getAsJsonArray("sounds");
                var nativeSamples=nativeEvents.getAsJsonObject(entry.getValue()).getAsJsonArray("sounds");
                assertEquals(nativeSamples.size(),samples.size());
                for(int i=0;i<samples.size();i++) {
                    var sample=samples.get(i).getAsJsonObject();
                    assertFalse(sample.has("type"),"Direct file references retain the explicit attenuation distance");
                    assertEquals(nativeSamples.get(i).getAsJsonObject().get("name"),sample.get("name"));
                    assertEquals(192,sample.get("attenuation_distance").getAsInt());
                    assertNotNull(zip.getEntry("assets/superbwarfare/sounds/"+sample.get("name").getAsString().split(":")[1]+".ogg"));
                }
            }
        }
        for(String name:List.of("lock","charge","fire","cool","blast"))for(var sample:sounds.getAsJsonObject(name).getAsJsonArray("sounds"))
            assertEquals(name.equals("lock")?256:255,
                    (name.equals("lock")?1.6F:3F)*sample.getAsJsonObject().get("attenuation_distance").getAsInt(),0.001);
        assertEquals(96,sounds.getAsJsonObject("idle").getAsJsonArray("sounds").get(0).getAsJsonObject().get("attenuation_distance").getAsInt());
    }

    private static ClassNode upstream(String name) throws Exception {
        try (ZipFile zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            var node = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(PREFIX + name + ".class")).readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var stream = MothershipDamageContractTest.class.getResourceAsStream("/" + name + ".class")) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static List<String> calls(MethodNode method) {
        return StreamSupport.stream(method.instructions.spliterator(), false).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).map(c -> c.owner + "." + c.name).toList();
    }

    private static org.objectweb.asm.tree.AbstractInsnNode previous(org.objectweb.asm.tree.AbstractInsnNode instruction) {
        while (instruction.getOpcode() < 0) instruction = instruction.getPrevious();
        return instruction;
    }
}

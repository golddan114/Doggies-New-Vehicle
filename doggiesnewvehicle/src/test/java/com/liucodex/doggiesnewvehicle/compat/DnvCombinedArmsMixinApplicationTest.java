package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Accurate Forge 0.1.6a damage redirect + DNV bridge, without executing third-party bootstrap. */
class DnvCombinedArmsMixinApplicationTest {
    private static final String SERVICE = "com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService";
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({"CLIENT,true", "CLIENT,false", "SERVER,true", "SERVER,false"})
    void bothRequiredMixinsApplyTogetherInEitherRegistrationOrder(String side, boolean dnvFirst) throws Exception {
        Path artifact = Path.of(System.getProperty("dnv.combinedArms.reference",
                "../outputs/research/2026-10-06-combined-arms-crash/tacz_sewv-0.1.6a.jar"));
        assertEquals("36ba8a3740a7b092802ba070187e14290c5d9d29b8bb922955da3a00346c11da",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));
        var fixture = new DnvMuzzleMixinApplicationTest(); fixture.temp = temp;
        try (var loader = fixture.loader()) {
            byte[][] result = (byte[][]) loader.loadClass(SERVICE)
                    .getMethod("transformCombinedArms", String.class, String.class, boolean.class)
                    .invoke(null, artifact.toAbsolutePath().toString(), side, dnvFirst);
            var vehicle = node(result[1]);
            var armor = node(result[3]);
            assertEquals(1, calls(vehicle, "tacz_sewv$redirectDamageCompute"));
            assertEquals(0, calls(vehicle, "dnv$vehicleArmor"));
            assertEquals(3, calls(armor, "dnv$vehicleArmor"));
            // The third-party redirect retains its native compute call and translation/nearby melee branches.
            assertTrue(vehicle.methods.stream().filter(m -> m.name.contains("tacz_sewv$redirectDamageCompute"))
                    .flatMap(m -> Arrays.stream(m.instructions.toArray())).anyMatch(i -> i instanceof MethodInsnNode call
                            && call.owner.equals("com/atsuishio/superbwarfare/entity/vehicle/damage/DamageModifier")
                            && call.name.equals("compute")));
            assertEquals(1, callsTo(vehicle, "tryTranslate"));
            assertEquals(1, callsTo(vehicle, "evaluate"));
            // Both direct-hit and AoE translations still enter the same adjusted armor result boundary.
            try (var zip = new ZipFile(artifact.toFile())) {
                var translation = node(zip.getInputStream(zip.getEntry(
                        "com/neoalive/tacz_sewv/ballistics/VehicleGunfireTranslation.class")).readAllBytes());
                long computes = translation.methods.stream().filter(m -> m.name.equals("tryTranslate"))
                        .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                        .filter(i -> i instanceof MethodInsnNode call && call.name.equals("compute")
                                && call.owner.equals("com/atsuishio/superbwarfare/entity/vehicle/damage/DamageModifier")).count();
                assertEquals(2, computes);
            }
        }
    }

    private static ClassNode node(byte[] bytes) {
        var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }
    private static long calls(ClassNode node, String handler) {
        return node.methods.stream().filter(m -> !m.name.contains(handler))
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof MethodInsnNode call && call.name.contains(handler)).count();
    }
    private static long callsTo(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.contains("tacz_sewv$redirectDamageCompute"))
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof MethodInsnNode call && call.name.equals(name)).count();
    }
}

package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact dependency route from GunData through the projectile to the impact explosion. */
class DnvNativeExplosionPayloadTest {
    private static final String PREFIX = "com/atsuishio/superbwarfare/";

    @Test void bothForgeProfilesTransferConfiguredPayloadIntoNativeImpactAndTerrainBlast() throws Exception {
        for (String jar : List.of("superbwarfare-0.8.9.1-hotfix-993063bed.jar",
                "superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar")) {
            try (var zip = new ZipFile(Path.of("libs", jar).toFile())) {
                var gun = read(zip, "item/gun/GunItem");
                var spawn = gun.methods.stream().filter(m -> m.name.startsWith("shootBullet$lambda$")
                        && methods(m).contains("setExplosionDestroy")).findFirst().orElseThrow();
                var fields = StreamSupport.stream(spawn.instructions.spliterator(), false)
                        .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                        .filter(f -> f.owner.equals(PREFIX + "data/gun/GunProp"))
                        .map(f -> f.name).collect(Collectors.toSet());
                assertTrue(fields.containsAll(Set.of("EXPLOSION_DAMAGE", "EXPLOSION_RADIUS", "EXPLOSION_DESTROY")), jar);
                assertTrue(gun.methods.stream().filter(m -> m.name.equals("shootBullet"))
                        .flatMap(m -> StreamSupport.stream(m.instructions.spliterator(), false))
                        .anyMatch(n -> n instanceof FieldInsnNode field
                                && field.owner.equals(PREFIX + "data/gun/GunProp") && field.name.equals("DAMAGE")), jar);
                assertTrue(methods(spawn).containsAll(Set.of("setDamage", "setExplosionDamage",
                        "setExplosionRadius", "setExplosionDestroy")), jar);

                var projectile = read(zip, "entity/projectile/FastThrowableProjectile");
                var impact = method(projectile, "buildExplosion");
                assertTrue(methods(impact).containsAll(Set.of("damage", "radius", "destroyBlock")), jar);
                var impactFields = StreamSupport.stream(impact.instructions.spliterator(), false)
                        .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                        .map(f -> f.name).collect(Collectors.toSet());
                assertTrue(impactFields.containsAll(Set.of("explosionDamageValue", "explosionRadiusValue",
                        "explosionDestroyValue")), jar);
                assertTrue(methods(method(projectile, "causeExplode")).containsAll(Set.of("buildExplosion", "explode")), jar);
            }
        }
    }

    private static Set<String> methods(MethodNode method) {
        return StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .map(m -> m.name).collect(Collectors.toSet());
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(PREFIX + name + ".class");
        assertNotNull(entry);
        var node = new ClassNode();
        new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, 0);
        return node;
    }
}

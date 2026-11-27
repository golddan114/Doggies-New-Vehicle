package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Catches development-name accessors that cannot apply to the Forge SRG runtime. */
class DnvReleaseMixinRefmapTest {
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({"server/level/ServerLevel,DnvServerLevelEntityManagerAccess,f_143244_,dnv$getEntityManager",
        "world/level/entity/PersistentEntitySectionManager,DnvPersistentEntitySectionsAccess,f_157495_,dnv$getSectionStorage",
        "world/level/entity/EntitySection,DnvEntitySectionEntitiesAccess,f_156827_,dnv$getStorage",
        "world/level/entity/EntitySectionStorage,DnvEntitySectionKeysAccess,f_156853_,dnv$getSectionIds"})
    void actualSrgRuntimeAcceptsRefmapAndRejectsMissingRefmap(
            String target, String mixin, String field, String accessor) throws Exception {
        var fixture = new DnvMuzzleMixinApplicationTest(); fixture.temp = temp;
        for (boolean omit : new boolean[] {false, true}) try (var loader = fixture.loader()) {
            var method = loader.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService")
                .getMethod("transformRelease", String.class, String.class, String.class, String.class, boolean.class);
            var runtime = Path.of(System.getProperty("dnv.minecraft.srg"));
            assertTrue(Files.isRegularFile(runtime), runtime::toString);
            String artifact = System.getProperty("dnv.artifact", "");
            if (omit) {
                var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null,
                    runtime.toString(), artifact, "net/minecraft/" + target, mixin, true));
                StringBuilder causes = new StringBuilder();
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) causes.append(cause);
                assertTrue(causes.toString().contains("InvalidAccessorException"), causes::toString);
            } else {
                byte[][] result = (byte[][]) method.invoke(null, runtime.toString(), artifact,
                    "net/minecraft/" + target, mixin, false);
                ClassNode after = new ClassNode(); new ClassReader(result[1]).accept(after, 0);
                var applied = after.methods.stream().filter(m -> m.name.equals(accessor)).findFirst().orElseThrow();
                assertEquals(1, Arrays.stream(applied.instructions.toArray()).filter(FieldInsnNode.class::isInstance)
                    .map(FieldInsnNode.class::cast).filter(read -> read.name.equals(field)).count());
            }
        }
    }

    @Test
    void productionConfigsNameGeneratedRefmaps() throws Exception {
        assertRefmap("main", "compileJava", "mixins.doggiesnewvehicle");
        assertRefmap("artillery", "compileArtilleryJava", "mixins.dnvartillery");
        assertRefmap("platform", "compilePlatformJava", "mixins.dnvplatform");
    }

    @Test
    void dynamicLightAppliesToPatchedForgeSrgAndFailsWithoutItsGeneratedRefmap() throws Exception {
        Path runtime = Path.of(System.getProperty("dnv.minecraft.forgeSrg"));
        assertTrue(Files.isRegularFile(runtime), runtime::toString);
        var fixture = new DnvMuzzleMixinApplicationTest(); fixture.temp = temp;
        for (boolean omit : new boolean[] {false, true}) try (var loader = fixture.loader()) {
            var method = loader.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService")
                .getMethod("transformReleaseOnSide", String.class, String.class, String.class, String.class, String.class, boolean.class);
            if (omit) {
                var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null,
                    runtime.toString(), "", "net/minecraft/world/level/lighting/BlockLightEngine", "BlizzardDynamicLightMixin", "CLIENT", true));
                StringBuilder causes = new StringBuilder();
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) causes.append(cause);
                assertTrue(causes.toString().contains("InvalidInjectionException"), causes::toString);
            } else {
                byte[][] result = (byte[][]) method.invoke(null, runtime.toString(), "",
                    "net/minecraft/world/level/lighting/BlockLightEngine", "BlizzardDynamicLightMixin", "CLIENT", false);
                ClassNode after = new ClassNode(); new ClassReader(result[1]).accept(after, 0);
                var emission = after.methods.stream().filter(m -> m.name.equals("m_284436_")).findFirst().orElseThrow();
                var calls = Arrays.stream(emission.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast).toList();
                assertEquals(1, calls.stream().filter(c -> c.name.contains("dnv$sourceEmission")).count());
                assertEquals(1, calls.stream().filter(c -> c.name.equals("m_284382_")).count(), "Native enabled-section guard remains");
            }
        }
    }

    @Test
    void sectionPagerAccessorsMapToTheActualForgeRuntimeFields() throws Exception {
        Path file = Path.of("build/tmp/compileJava/mixins.doggiesnewvehicle.refmap.json");
        JsonObject mappings = JsonParser.parseString(Files.readString(file))
                .getAsJsonObject().getAsJsonObject("data").getAsJsonObject("searge");
        assertField(mappings, "DnvServerLevelEntityManagerAccess", "entityManager",
                "f_143244_:Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;");
        assertField(mappings, "DnvPersistentEntitySectionsAccess", "sectionStorage",
                "f_157495_:Lnet/minecraft/world/level/entity/EntitySectionStorage;");
        assertField(mappings, "DnvEntitySectionEntitiesAccess", "storage",
                "f_156827_:Lnet/minecraft/util/ClassInstanceMultiMap;");
        assertField(mappings, "DnvEntitySectionKeysAccess", "sectionIds",
                "f_156853_:Lit/unimi/dsi/fastutil/longs/LongSortedSet;");
    }

    private static void assertRefmap(String sourceSet, String compileTask, String configName)
            throws Exception {
        Path config = Path.of("src", sourceSet, "resources", configName + ".json");
        Path mapping = Path.of("build", "tmp", compileTask, configName + ".refmap.json");
        JsonObject definition = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        assertEquals(configName + ".refmap.json", definition.get("refmap").getAsString());
        assertTrue(Files.isRegularFile(mapping), mapping::toString);
        assertTrue(JsonParser.parseString(Files.readString(mapping)).getAsJsonObject().has("data"));
    }

    private static void assertField(JsonObject mappings, String mixin, String field,
                                    String runtimeField) {
        JsonObject owner = mappings.getAsJsonObject(
                "com/liucodex/doggiesnewvehicle/compat/mixin/" + mixin);
        assertEquals(runtimeField, owner.get(field).getAsString(), mixin + '.' + field);
    }
}

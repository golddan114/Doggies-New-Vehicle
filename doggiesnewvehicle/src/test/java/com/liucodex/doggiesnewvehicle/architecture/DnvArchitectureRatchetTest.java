package com.liucodex.doggiesnewvehicle.architecture;

import com.liucodex.dchsuite.architecture.ArchitectureRatchet;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class DnvArchitectureRatchetTest {
    @Test
    void compiledBaseAndContentOwnersRemainIndependent() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.liucodex.doggiesnewvehicle", "com.liucodex.noise",
                        "com.liucodex.silentheaven");
        noClasses().that(compiledIn("main"))
                .should().dependOnClassesThat(compiledIn("artillery", "platform", "boss"))
                .because("base composition uses only three fixed optional registration names")
                .check(production);
        for (String owner : java.util.List.of("artillery", "platform", "boss")) {
            String[] others = java.util.stream.Stream.of("artillery", "platform", "boss")
                    .filter(module -> !module.equals(owner)).toArray(String[]::new);
            noClasses().that(compiledIn(owner)).should().dependOnClassesThat(compiledIn(others))
                    .because("content owners share base contracts without importing one another")
                    .check(production);
        }
    }

    private static DescribedPredicate<JavaClass> compiledIn(String... owners) {
        return new DescribedPredicate<>("compiled in " + String.join(",", owners)) {
            @Override public boolean test(JavaClass type) {
                return type.getSource().map(source -> {
                    String uri = source.getUri().toString();
                    return java.util.Arrays.stream(owners).anyMatch(owner ->
                            uri.contains("/doggiesnewvehicle/build/classes/java/" + owner + "/"));
                }).orElse(false);
            }
        };
    }

    @Test
    void logicalModuleDebtCannotIncrease() throws IOException {
        ArchitectureRatchet.verify(
                "dnv",
                "com.liucodex.doggiesnewvehicle",
                "com.liucodex.noise",
                "com.liucodex.silentheaven");
    }

    @Test
    void publicContractsAndPureCoreDoNotReachAdaptersOrClients() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.liucodex.doggiesnewvehicle",
                        "com.liucodex.noise",
                        "com.liucodex.silentheaven");

        noClasses()
                .that().resideInAnyPackage(
                        "com.liucodex.doggiesnewvehicle.api..",
                        "com.liucodex.doggiesnewvehicle.core.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.liucodex.doggiesnewvehicle.client..",
                        "com.liucodex.doggiesnewvehicle.platform.client..",
                        "com.liucodex.doggiesnewvehicle.compat..",
                        "com.liucodex.doggiesnewvehicle.entity..",
                        "com.liucodex.doggiesnewvehicle.mission..",
                        "com.liucodex.noise..",
                        "com.liucodex.silentheaven..",
                        "net.minecraft.client..",
                        "net.minecraftforge.client.."
                )
                .because(
                        "DNV contracts and pure core must remain usable without DCH, SBW adapters, entities, or client classes"
                )
                .check(production);
    }
}

package com.liucodex.doggiesnewvehicle.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class DnvClientAdapterBoundaryTest {
    @Test
    void adaptersDoNotLoadDnvScreensOrHudImplementations() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.liucodex.doggiesnewvehicle");
        noClasses().that().resideInAPackage("com.liucodex.doggiesnewvehicle.compat..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.liucodex.doggiesnewvehicle.client..",
                        "com.liucodex.doggiesnewvehicle.platform.client..",
                        "com.liucodex.noise.client..",
                        "com.liucodex.silentheaven.client..")
                .because("client projections are installed by the client; protocols must not own screens")
                .check(production);
    }
}

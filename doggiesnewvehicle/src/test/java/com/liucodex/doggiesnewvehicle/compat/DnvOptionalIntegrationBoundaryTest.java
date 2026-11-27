package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvOptionalIntegrationBoundaryTest {
    private static final Path MAIN = Path.of("src", "main");

    @Test
    void dnvProductionContainsNoDchRuntimeOrContentIdentity() throws IOException {
        try (var files = Files.walk(MAIN)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = MAIN.relativize(file).toString();
                if (!relative.endsWith(".java")
                        && !relative.endsWith(".json")
                        && !relative.endsWith(".toml")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("com.liucodex.casterhud"), relative);
                assertFalse(source.contains("dch_customnpcs_entity_marker"), relative);
                assertFalse(source.contains("ENGAGE_DCH_ENEMIES"), relative);
                assertFalse(source.contains("engage_dch_enemies"), relative);
                assertFalse(source.contains("DCH-AIR"), relative);
            }
        }
    }
}

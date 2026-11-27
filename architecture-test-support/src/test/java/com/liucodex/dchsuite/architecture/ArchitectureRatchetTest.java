package com.liucodex.dchsuite.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArchitectureRatchetTest {
    @TempDir
    Path temporary;

    @Test
    void forbiddenReverseEdgesAndBothCycleDirectionsRemainReported() throws Exception {
        var classes = new ClassFileImporter().importClasses(First.class, Second.class);
        Path mapping = writeMapping(bytecodeRoot(), "second", "");
        var analysis = ArchitectureRatchet.analyze(classes,
                ArchitectureRatchet.ScopeMapping.load(mapping, "fixture", temporary));
        assertEquals(3, analysis.exceptions.size());
        assertTrue(analysis.exceptions.containsKey(
                new ArchitectureRatchet.ExceptionKey("FORBIDDEN_DEPENDENCY", "second", "first")));
        assertTrue(analysis.exceptions.containsKey(
                new ArchitectureRatchet.ExceptionKey("CYCLE_EDGE", "first", "second")));
        assertTrue(analysis.exceptions.containsKey(
                new ArchitectureRatchet.ExceptionKey("CYCLE_EDGE", "second", "first")));
    }

    @Test
    void samePackageCannotHideAnUnknownCompiledOwner() throws Exception {
        var classes = new ClassFileImporter().importClasses(First.class, Second.class);
        Path mapping = writeMapping(temporary.resolve("unrecognized-output"), "second", "first");
        AssertionError failure = assertThrows(AssertionError.class, () -> ArchitectureRatchet.analyze(
                classes, ArchitectureRatchet.ScopeMapping.load(mapping, "fixture", temporary)));
        assertTrue(failure.getMessage().contains("unassigned production classes"));
        assertTrue(failure.getMessage().contains(First.class.getName()));
    }

    private Path writeMapping(Path bytecodeRoot, String firstTargets, String secondTargets) throws Exception {
        Path mapping = temporary.resolve("mapping.tsv");
        Files.writeString(mapping, row("first", firstTargets, First.class, bytecodeRoot)
                + row("second", secondTargets, Second.class, bytecodeRoot));
        return mapping;
    }

    private static String row(String module, String targets, Class<?> type, Path bytecodeRoot) {
        return String.join("\t", "fixture", module, "true", targets,
                java.util.regex.Pattern.quote(type.getName()), "true", bytecodeRoot.toString()) + "\n";
    }

    private static Path bytecodeRoot() throws Exception {
        return Path.of(First.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    static final class First {
        Second second;
    }

    static final class Second {
        First first;
    }
}

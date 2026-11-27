package com.liucodex.doggiesnewvehicle.compat;

import java.nio.file.Path;

/** The same exact artifact used by javac, runtime fixtures and Mixin probes. */
public final class SbwTestDependency {
    private SbwTestDependency() {}

    public static Path path() {
        return Path.of(System.getProperty("dnv.sbw.jar",
                "libs/superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar"));
    }

    public static String expectedSha256() {
        if (!System.getProperty("dnv.sbw.profile", "0.8.9.2").equals("0.8.9.2")) {
            throw new IllegalStateException("Unsupported SBW test profile");
        }
        return System.getProperty("dnv.sbw.sha256",
                "559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12");
    }
}

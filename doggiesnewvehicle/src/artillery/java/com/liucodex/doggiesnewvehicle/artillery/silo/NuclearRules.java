package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Packaged defaults; confirmed economics and native coordinate-missile reference are explicit data. */
public record NuclearRules(int shotFE, int reloadTicks, int openTicks, int departureTicks,
        int closeTicks, int exitTicks, double exitRise, double coordinateRange, int lifeTicks,
        double cruiseAltitude, double terminalDistance, double maxSpeed, double accelerationRate) {
    public static final NuclearRules DEFAULT = load();
    public NuclearRules {
        if (shotFE <= 0 || reloadTicks <= 0 || openTicks <= 0 || departureTicks < exitTicks
                || closeTicks <= 0 || exitTicks <= 0 || lifeTicks <= departureTicks
                || !finitePositive(exitRise) || !finitePositive(coordinateRange)
                || !finitePositive(cruiseAltitude) || !finitePositive(terminalDistance)
                || !finitePositive(maxSpeed) || !finitePositive(accelerationRate)
                || accelerationRate*exitTicks>100) throw new IllegalArgumentException("Invalid nuclear defaults");
    }
    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    private static NuclearRules load() {
        var stream = NuclearRules.class.getResourceAsStream("/data/doggiesnewvehicle/nuclear/defaults.json");
        if (stream == null) throw new IllegalStateException("Missing nuclear defaults");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var j = JsonParser.parseReader(reader).getAsJsonObject();
            return new NuclearRules(j.get("shotFE").getAsInt(), j.get("reloadTicks").getAsInt(),
                    j.get("openTicks").getAsInt(), j.get("departureTicks").getAsInt(),
                    j.get("closeTicks").getAsInt(), j.get("exitTicks").getAsInt(),
                    j.get("exitRise").getAsDouble(), j.get("coordinateRange").getAsDouble(),
                    j.get("lifeTicks").getAsInt(), j.get("cruiseAltitude").getAsDouble(),
                    j.get("terminalDistance").getAsDouble(), j.get("maxSpeed").getAsDouble(),j.get("accelerationRate").getAsDouble());
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
}

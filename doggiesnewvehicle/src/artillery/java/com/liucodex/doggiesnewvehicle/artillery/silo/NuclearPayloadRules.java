package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Packaged user-approved radii; not a second runtime configuration owner. */
public record NuclearPayloadRules(int blastRadius, int fluidRadius, int terrainRadius,
        int radiationRadius, int scentRadius, int centerKillRadius, float blastFraction, float radiationDamage, float radiationFraction,
        int radiationTicks, int radiationInterval, int visitsPerTick, int blocksPerTick) {
    /** Entity queries do not use the persistent block-scan cursor or its radius limit. */
    public static final int MAX_ENTITY_RADIUS = 112;
    public static final NuclearPayloadRules DEFAULT = load();
    public NuclearPayloadRules {
        for (int radius : new int[]{blastRadius,radiationRadius,scentRadius})
            if (radius < 1 || radius > MAX_ENTITY_RADIUS) throw new IllegalArgumentException("Invalid entity payload radius");
        for (int radius : new int[]{fluidRadius,terrainRadius})
            if (radius < 1 || radius > NuclearSphereScan.MAX_RADIUS) throw new IllegalArgumentException("Invalid payload radius");
        if (centerKillRadius < 0 || centerKillRadius > blastRadius
                || !Float.isFinite(blastFraction) || blastFraction <= 0 || blastFraction > 1
                || !Float.isFinite(radiationDamage) || radiationDamage < 0
                || !Float.isFinite(radiationFraction) || radiationFraction <= 0 || radiationFraction >= 1
                || radiationTicks <= 0 || radiationInterval <= 0 || radiationInterval > radiationTicks
                // Reserve the tallest composite column (37 cells) through all three phases.
                || visitsPerTick < 111 || visitsPerTick > 16384 || blocksPerTick < 111 || blocksPerTick > 2048)
            throw new IllegalArgumentException("Invalid payload budget");
    }
    private static NuclearPayloadRules load() {
        var stream=NuclearPayloadRules.class.getResourceAsStream("/data/doggiesnewvehicle/nuclear/payload.json");
        if(stream==null)throw new IllegalStateException("Missing nuclear payload defaults");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var j=JsonParser.parseReader(reader).getAsJsonObject();
            return new NuclearPayloadRules(j.get("blastRadius").getAsInt(),j.get("fluidRadius").getAsInt(),
                j.get("terrainRadius").getAsInt(),j.get("radiationRadius").getAsInt(),j.get("scentRadius").getAsInt(),
                j.get("centerKillRadius").getAsInt(),j.get("blastFraction").getAsFloat(),j.get("radiationDamage").getAsFloat(),j.get("radiationFraction").getAsFloat(),
                j.get("radiationTicks").getAsInt(),j.get("radiationInterval").getAsInt(),
                j.get("visitsPerTick").getAsInt(),j.get("blocksPerTick").getAsInt());
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
}

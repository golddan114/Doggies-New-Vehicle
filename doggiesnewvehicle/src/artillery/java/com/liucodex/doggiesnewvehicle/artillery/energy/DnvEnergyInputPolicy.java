package com.liucodex.doggiesnewvehicle.artillery.energy;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Packaged artillery data; economic values are separate from native vehicle identities. */
public final class DnvEnergyInputPolicy {
    private static final Map<String, Integer> LIMITS = load();
    private DnvEnergyInputPolicy() { }
    public static DnvEnergyInputBudget forVehicle(String id) {
        Integer limit = LIMITS.get(id);
        return limit == null ? null : new DnvEnergyInputBudget(limit);
    }
    private static Map<String, Integer> load() {
        var stream = DnvEnergyInputPolicy.class.getResourceAsStream("/data/doggiesnewvehicle/energy/input_limits.json");
        if (stream == null) throw new IllegalStateException("Missing artillery energy policy");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var values = new HashMap<String, Integer>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                int value = entry.getValue().getAsInt();
                if (value < 0) throw new IllegalArgumentException("Invalid energy input: " + entry.getKey());
                values.put(entry.getKey(), value);
            });
            return Map.copyOf(values);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read artillery energy policy", failure); }
    }
}

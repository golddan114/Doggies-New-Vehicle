package com.liucodex.doggiesnewvehicle.combat;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Packaged balance data, loaded once; no tick parsing or separate world configuration. */
public final class BossPercentDamage {
    private static final Map<String, Float> VALUES = load();
    private BossPercentDamage() { }
    public static float fraction(String weapon) {
        Float value = VALUES.get(weapon);
        if (value == null) throw new IllegalArgumentException("Missing percent profile: " + weapon);
        return value;
    }
    private static Map<String, Float> load() {
        var stream = BossPercentDamage.class.getResourceAsStream("/data/doggiesnewvehicle/boss/direct_percent.json");
        if (stream == null) throw new IllegalStateException("Missing direct percent defaults");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> {
                        float value = entry.getValue().getAsFloat();
                        if (!Float.isFinite(value) || value <= 0 || value > 1) throw new IllegalArgumentException(entry.getKey());
                        return value;
                    }));
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}

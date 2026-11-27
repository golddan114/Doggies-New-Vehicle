package com.liucodex.doggiesnewvehicle.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Instance input only. SBW still owns computed vehicle data, GunData and its server synchronization. */
public final class DnvArtillerySettings {
    public static final String FILE = "dnv-artillery-turrets-v1.properties";
    private static final String[] MODELS = {"dragon_bofors_twin", "dragon_bofors_quad", "dragon_flak38",
            "dragon_flak88", "m1_yunlong", "dragon_flak40", "50_changfeng_cannon", "storm_turret", "jingzhe_turret"};
    private final Map<String, Double> values;
    private DnvArtillerySettings(Map<String, Double> values) { this.values = Map.copyOf(values); }

    public static Map<String, Double> defaults() {
        var values = new LinkedHashMap<String, Double>();
        for (String model : MODELS) {
            try (var in = DnvArtillerySettings.class.getResourceAsStream("/data/doggievehicle/sbw/vehicles/" + model + ".json")) {
                if (in == null) throw new IllegalStateException("Missing turret defaults: " + model);
                var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                var speed = json.getAsJsonArray("TurretTurnSpeed");
                values.put(model + ".pitch-degrees-per-second", speed.get(0).getAsDouble() * 20);
                values.put(model + ".yaw-degrees-per-second", speed.get(1).getAsDouble() * 20);
                if (light(model)) values.put(model + ".he-radius-blocks", 1D);
            } catch (IOException failure) { throw new IllegalStateException(model, failure); }
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    public static DnvArtillerySettings load(Path directory) throws IOException {
        Path file = directory.resolve(FILE);
        var defaults = defaults();
        if (!Files.exists(file)) {
            Files.createDirectories(directory);
            Files.writeString(file, template(defaults), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        if (Files.size(file) > 32768) throw new IllegalArgumentException(FILE + "：文件超过32KiB");
        return parse(Files.readString(file, StandardCharsets.UTF_8), defaults);
    }

    public static DnvArtillerySettings packaged() { return new DnvArtillerySettings(defaults()); }

    public static DnvArtillerySettings parse(String text, Map<String, Double> defaults) throws IOException {
        if (text.length() > 32768) throw new IllegalArgumentException(FILE + "：文件过长");
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException(FILE + "：重复参数 " + key);
                return super.put(key, value);
            }
        };
        properties.load(new java.io.StringReader(text));
        var expected = new java.util.HashSet<>(defaults.keySet()); expected.add("schema");
        if (!properties.stringPropertyNames().equals(expected) || !"1".equals(properties.getProperty("schema")))
            throw new IllegalArgumentException(FILE + "：参数缺失、未知参数或schema不为1；请与默认文件核对");
        var parsed = new LinkedHashMap<String, Double>();
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey();
            double value;
            try { value = Double.parseDouble(properties.getProperty(key)); }
            catch (NumberFormatException failure) { throw new IllegalArgumentException(FILE + "：" + key + " 必须为数字", failure); }
            double maximum = key.endsWith("he-radius-blocks") ? 16 : 360;
            if (!Double.isFinite(value) || value < 0 || value > maximum)
                throw new IllegalArgumentException(FILE + "：" + key + " 必须在0～" + maximum + "之间且为有限数");
            parsed.put(key, value);
        }
        return new DnvArtillerySettings(parsed);
    }

    public static String template(Map<String, Double> defaults) {
        var text = new StringBuilder("# DNV炮塔参数。UTF-8；schema是格式版本，与模组版本无关。\n"
                + "# 单人由本人修改；多人只读取服务端/服主文件，客户端文件不能提高服务器炮速。\n"
                + "# 首次使用需要包含此功能的新模组；之后修改本文件无需改JAR。\n"
                + "# 优先级：包内/数据包默认 < 本文件。保存后完全重启游戏或专服；不支持热重载。\n"
                + "# 对已有及新部署炮塔的原生数据均生效；不改库存、弹药数量、RPM或世界存档。\n"
                + "# 错误时整份回退包内默认并输出中文错误；不会部分生效。无运行中重载，无last-valid替换。\n"
                + "# 恢复：停服，另存本文件作备份，移走本文件后重启生成默认；备份放在本目录外。\n"
                + "schema=1\n");
        defaults.forEach((key, value) -> {
            boolean radius = key.endsWith("he-radius-blocks");
            text.append("\n# ").append(key.substring(0, key.indexOf('.'))).append(radius
                    ? " HE爆炸半径；单位格；范围0～16，0关闭爆炸范围；不附点火。增大会扩大伤害/破块范围和扫描成本。"
                    : key.contains(".yaw-") ? " 水平回转上限；单位度/秒；范围0～360，0停止水平转向。"
                    : " 俯仰转速上限；单位度/秒；范围0～360，0停止俯仰转向。");
            if (!radius) text.append(" 20TPS时除20传入原生度/tick，近目标受原生插值/射界和FE限制；不改变射速或FE单价。");
            text.append(" 默认=").append(value).append("。\n").append(key).append('=').append(value).append('\n');
        });
        return text.toString();
    }

    /** Applied before exact SBW parses vehicle weapons and broadcasts its server data. No client-local mutation. */
    public JsonObject apply(String id, JsonObject input) {
        if (!id.startsWith("doggievehicle:") || !input.has("Weapons")) return input;
        String model = id.substring("doggievehicle:".length());
        if (!values.containsKey(model + ".yaw-degrees-per-second")) return input;
        JsonObject result = input.deepCopy();
        var speed = new JsonArray();
        speed.add(values.get(model + ".pitch-degrees-per-second") / 20);
        speed.add(values.get(model + ".yaw-degrees-per-second") / 20);
        result.add("TurretTurnSpeed", speed);
        if (light(model)) {
            var main = result.getAsJsonObject("Weapons").getAsJsonObject("Main");
            var ammo = main == null ? null : main.get("AmmoType");
            // Single-ammo strings and metadata-only overrides are valid native data-pack shapes.
            if (ammo == null || !ammo.isJsonArray()) return result;
            for (var value : ammo.getAsJsonArray()) if (value.isJsonObject()) {
                var overrideValue = value.getAsJsonObject().get("Override");
                if (overrideValue == null || !overrideValue.isJsonObject()) continue;
                var override = overrideValue.getAsJsonObject();
                var shell = override.get("ShellType");
                if (shell != null && shell.isJsonPrimitive() && "HE".equals(shell.getAsString()))
                    override.addProperty("ExplosionRadius", values.get(model + ".he-radius-blocks"));
            }
        }
        return result;
    }

    private static boolean light(String model) {
        return model.equals("dragon_bofors_twin") || model.equals("dragon_bofors_quad") || model.equals("dragon_flak38");
    }
}

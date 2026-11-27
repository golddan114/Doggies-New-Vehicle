package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

class DnvLocalizationTest {
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern FORMAT = Pattern.compile("%(?:%|(?:[0-9]+\\$)?[a-zA-Z])");

    @Test void blizzardUsesTheApprovedShortNameInBothPackagedLanguages() throws Exception {
        assertEquals("暴风雪",language("doggiesnewvehicle","zh_cn").get("entity.doggievehicle.blizzard").getAsString());
        assertEquals("Blizzard",language("doggiesnewvehicle","en_us").get("entity.doggievehicle.blizzard").getAsString());
    }

    @Test void shippedLanguageNamespacesHaveMatchingKeysAndFormatArguments() throws Exception {
        for (String namespace : List.of("doggiesnewvehicle", "noise", "silent_heaven", "dnv_mothership")) {
            var english = language(namespace, "en_us");
            var chinese = language(namespace, "zh_cn");
            assertEquals(chinese.keySet(), english.keySet(), namespace);
            for (String key : english.keySet()) {
                String value = english.get(key).getAsString();
                assertFalse(value.isBlank(), key);
                assertFalse(HAN.matcher(value).find(), key);
                assertEquals(formats(chinese.get(key).getAsString()), formats(value), key);
            }
        }
    }

    @Test void previouslyHardcodedRuntimeMessagesUsePackagedTranslationKeys() throws Exception {
        var english = language("doggiesnewvehicle", "en_us");
        for (String name : List.of("init/DnvM1Content", "entity/DnvM1YunlongEntity",
                "compat/DnvRemoteWeaponCommands", "boss/client/MothershipFxPreview")) {
            var node = new ClassNode();
            try (var input = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/" + name + ".class")) {
                assertNotNull(input, name);
                new ClassReader(input).accept(node, 0);
            }
            var literals = node.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                    .filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                    .filter(value -> value.cst instanceof String).map(value -> (String) value.cst).toList();
            assertFalse(literals.stream().anyMatch(value -> HAN.matcher(value).find()), name);
            for (String key : literals) {
                if ((key.startsWith("message.doggiesnewvehicle.") || key.startsWith("entity.doggievehicle."))
                        && !key.endsWith(".")) assertTrue(english.has(key), key);
            }
        }
        for (var reason : com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Reason.values())
            assertTrue(english.has("message.doggiesnewvehicle.remote_station.reason." + reason.name()));
        for (var action : com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Action.values())
            assertTrue(english.has("message.doggiesnewvehicle.rws.action." + action.name()));
    }

    @Test void nativeComponentFormatterRendersEnglishPercentAndNestedStatusText() throws Exception {
        var english = language("doggiesnewvehicle", "en_us");
        String start = format(english, "message.doggiesnewvehicle.mothership_preview.start");
        assertTrue(start.contains("80% of maximum health"), start);
        String pulse = format(english, "message.doggiesnewvehicle.mothership_preview.pulse",
                1, 250, 8, 125, 16, Component.literal(" / final blast ignites ground"));
        assertTrue(pulse.contains("1/4") && pulse.contains("250 + 8%") && pulse.contains("radius 16"), pulse);
        assertFalse(pulse.contains("%s"));
        String result = format(english, "message.doggiesnewvehicle.rws.result", Component.literal("Completed"),
                Component.literal("Success / target available"), "sample-operation", 4, Component.empty());
        assertEquals("Completed: Success / target available · operation sample-operation · revision 4", result);
    }

    @Test void everyShippedTextRendersWithNativeFormattingInBothLocales() throws Exception {
        for (String namespace : List.of("doggiesnewvehicle", "noise", "silent_heaven", "dnv_mothership")) {
            for (String locale : List.of("en_us", "zh_cn")) {
                var language = language(namespace, locale);
                for (String key : language.keySet()) {
                    String value = language.get(key).getAsString();
                    int implicit = 0, largest = 0;
                    for (String token : formats(value)) {
                        assertTrue(token.endsWith("s"), key + ": unsupported native format " + token);
                        int dollar = token.indexOf('$');
                        if (dollar > 0) largest = Math.max(largest, Integer.parseInt(token.substring(1, dollar)));
                        else implicit++;
                    }
                    Object[] args = new Object[Math.max(implicit, largest)];
                    for (int i = 0; i < args.length; i++) args[i] = Component.literal("DNV_ARGUMENT_" + i);
                    String rendered = format(language, key, args);
                    assertFalse(rendered.isBlank(), key);
                    assertFalse(FORMAT.matcher(rendered).find(), key + ": unresolved format " + rendered);
                    assertFalse(rendered.contains("\uFFFD"), key);
                }
                if (namespace.equals("doggiesnewvehicle") && locale.equals("en_us")) {
                    assertEquals("M1 Heavy Cannon", format(language, "entity.doggievehicle.m1_yunlong"));
                    assertEquals("M1 Heavy Cannon Blueprint", format(language, "item.doggiesnewvehicle.m1_yunlong_blueprint"));
                }
            }
        }
    }

    private static List<String> formats(String value) {
        return FORMAT.matcher(value).results().map(match -> match.group()).filter(token -> !token.equals("%%")).toList();
    }
    private static String format(JsonObject language, String key, Object... args) {
        // An unused key selects the supplied locale while exercising Minecraft's real text formatter.
        return Component.translatableWithFallback("test.dnv.localization." + key, language.get(key).getAsString(), args).getString();
    }
    private static JsonObject language(String namespace, String locale) throws Exception {
        try (var input = DnvLocalizationTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + locale + ".json")) {
            assertNotNull(input, namespace + "/" + locale);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}

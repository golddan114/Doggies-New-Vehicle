package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MothershipNativeDamageBaselineTest {
    @Test void exactDependencyCarriesGunIdentityAndIncludesBothGunAndStarSegmentsInPhysicalTags() throws Exception {
        try(var zip=new java.util.zip.ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            for(var entry:java.util.Map.of("projectile",java.util.List.of("gunfire","super_star_hit","super_star_slash"),
                    "projectile_absolute",java.util.List.of("gunfire_absolute","gunfire_headshot_absolute")).entrySet()) {
                var values=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("data/superbwarfare/tags/damage_type/"+entry.getKey()+".json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
                for(String id:entry.getValue())assertTrue(values.asList().stream().anyMatch(v->v.isJsonPrimitive()&&v.getAsString().equals("superbwarfare:"+id)));
            }
            var item=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/item/gun/GunItem.class"))).accept(item,0);
            assertTrue(item.methods.stream().anyMatch(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false)
                    .anyMatch(n->n instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("setGunItemId"))));
            var entities=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/init/ModEntities.class"))).accept(entities,0);
            var ids=entities.methods.stream().flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                    .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance).map(n->((org.objectweb.asm.tree.LdcInsnNode)n).cst).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.containsAll(java.util.List.of("small_cannon_shell","cannon_shell","gun_grenade","grapeshot","mortar_shell","small_rocket","medium_rocket")));
        }
    }
    @Test void exactNativeMinigunDamageSplitsOrdinaryAndAbsolutePartsBeforeAdmission() throws Exception {
        try(var zip=new java.util.zip.ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            var data=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("data/superbwarfare/sbw/guns/minigun.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var target=new ExactSbwDamageProbe.Target();
            ExactSbwDamageProbe.bullet(data.get("BypassesArmor").getAsFloat(),data.get("Headshot").getAsFloat())
                    .performDamage(target,data.get("Damage").getAsFloat(),false);
            assertEquals(2,target.calls);assertEquals(8,target.input,.0001);
            assertEquals("gunfire_absolute",target.submitted.get(0).source().id());assertEquals(2.4,target.submitted.get(0).amount(),.0001);
            assertEquals("gunfire",target.submitted.get(1).source().id());assertEquals(5.6,target.submitted.get(1).amount(),.0001);
        }
    }
    @Test void successfulNativeDispatchNeverRepairsACappedPercentageEvenWithForceModeEnabled() {
        for (boolean force : new boolean[]{false, true}) {
            ExactSbwDamageProbe.Options.FORCE_DAMAGE_MODE.enabled = force;
            var target = new ExactSbwDamageProbe.LivingTarget(); target.cap = 100;
            float input = new MothershipDoomsday().hit(1).directDamageAgainst(10000);
            assertEquals(1050, input);
            assertTrue(ExactSbwDamageProbe.damage(target, new ExactSbwDamageProbe.Source("doomsday"), input));
            assertEquals(9900, target.health); assertEquals(1, target.calls); assertEquals(1050, target.input);
        }
        ExactSbwDamageProbe.Options.FORCE_DAMAGE_MODE.enabled = false;
    }

    @Test void vehicleNativeHealthWriteCanPrecedeFalseAndMustNotTriggerAnotherDamageAttempt() {
        var target = new ExactSbwDamageProbe.VehicleTarget(8000);
        assertFalse(ExactSbwDamageProbe.damage(target, new ExactSbwDamageProbe.Source("projectile_hit"), 200));
        assertEquals(7800, target.nativeHealth.getHealth()); assertEquals(1, target.calls);
        target.nativeHealth.onHurt(9000, target, true);
        assertEquals(-201, target.nativeHealth.getHealth(), .001,
                "Native debit caps one input at max+1, rather than promising arbitrary absolute removal");
        target.nativeHealth.setHealth(-1_000_000);
        assertEquals(-8010, target.nativeHealth.getHealth());
        target.nativeHealth.setHealth(1_000_000);
        assertEquals(8000, target.nativeHealth.getHealth());
    }

    @Test void rejectedLivingTargetRemainsUntouchedWithoutForceFallback() {
        ExactSbwDamageProbe.Options.FORCE_DAMAGE_MODE.enabled = false;
        var target = new ExactSbwDamageProbe.LivingTarget(); target.accepted = false;
        assertFalse(ExactSbwDamageProbe.damage(target, new ExactSbwDamageProbe.Source("doomsday"), 10000));
        assertEquals(10000, target.health); assertEquals(1, target.calls);
    }
}

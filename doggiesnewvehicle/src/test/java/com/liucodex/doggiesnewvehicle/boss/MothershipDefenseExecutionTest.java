package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.core.MothershipDefense;

import static com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Compiled mothership hurt + compiled identity classifier + exact native health debit; registry/events are ports. */
class MothershipDefenseExecutionTest {
    @Test void nativeItemStackIdentityAndNativeArmorSplitReachTheIntendedMachineGunTiers() throws Exception {
        for(var entry:java.util.Map.of("m_60",.2F,"m_2_hb",4F).entrySet()) {
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())) {
                var gun=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("data/superbwarfare/sbw/guns/"+entry.getKey()+".json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                var nativeBullet=ExactSbwDamageProbe.bullet(gun.get("BypassesArmor").getAsFloat(),gun.get("Headshot").getAsFloat());
                nativeBullet.identify(new ExactSbwDamageProbe.Stack("item.superbwarfare."+entry.getKey()));
                assertEquals("item.superbwarfare."+entry.getKey(),nativeBullet.getGunItemId());
                var target=new ExactSbwDamageProbe.Target();
                nativeBullet.performDamage(target,gun.get("Damage").getAsFloat(),false);
                Base ship=create();ship.health=8000;
                for(var hit:target.submitted)hurt(ship,new Damage(new Actor(),new Bullet(nativeBullet.getGunItemId()),hit.source().id()),hit.amount());
                assertEquals(entry.getValue(),8000-ship.health,.002,entry.getKey());
            }
        }
        assertEquals(20,loss(new Bullet("item.othermod.m_60"),"gunfire",100,0),.002);
    }

    @Test void repeatedNativeStarContactKeepsOnlyOneHitAndOneDelayedSlashPerProjectileAndShip() throws Exception {
        Base ship=create();ship.health=8000;Actor star=projectile("super_star_projectile");
        for(int tick=0;tick<11;tick++) {
            ship.world.now=tick;hurt(ship,new Damage(new Actor(),star,"super_star_hit"),60);
            ship.world.now=tick+2;hurt(ship,new Damage(new Actor(),star,"super_star_slash"),45);
        }
        assertEquals(7994.75,ship.health,.002);assertEquals(2,ship.nativeHurtCalls);
        // Another projectile and another ship have independent pairs.
        Actor other=projectile("super_star_projectile");
        hurt(ship,new Damage(new Actor(),other,"super_star_hit"),60);
        Base second=create();second.health=8000;
        hurt(second,new Damage(new Actor(),star,"super_star_hit"),60);
        assertEquals(7997,second.health,.002);
        assertEquals(7991.75,ship.health,.002);
        // Clearing combat/loading the same ship must not refund a projectile's already committed stage.
        clear(ship);hurt(ship,new Damage(new Actor(),star,"super_star_hit"),60);
        assertEquals(7991.75,ship.health,.002);
    }
    @Test void starReceiptsSurviveProjectileNbtAndBoundTargetsWithoutSpendingRejectedHits() throws Exception {
        Base ship=create();Actor star=projectile("super_star_projectile");
        Damage hit=new Damage(new Actor(),star,"super_star_hit");
        ship.entryAge=0;hurt(ship,hit,60);assertEquals(8000,ship.health);
        ship.entryAge=160;hurt(ship,hit,60);assertEquals(7997,ship.health,.002);
        Actor restored=projectile("super_star_projectile");restored.persistent.merge(star.persistent.copy());
        hurt(ship,new Damage(new Actor(),restored,"super_star_hit"),60);assertEquals(7997,ship.health,.002);
        hurt(ship,new Damage(new Actor(),restored,"super_star_slash"),45);assertEquals(7994.75,ship.health,.002);
        var first=ship.uuid;
        for(int i=1;i<128;i++){ship.uuid=java.util.UUID.randomUUID();hurt(ship,hit,60);}
        float before=ship.health;ship.uuid=java.util.UUID.randomUUID();hurt(ship,hit,60);
        assertEquals(before,ship.health);assertEquals(128,star.persistent.getCompound("DnvBossStarHits").size());
        ship.uuid=first;hurt(ship,new Damage(new Actor(),star,"super_star_slash"),45);
        assertEquals(before-2.25F,ship.health,.002,"existing targets retain their second stage at the cap");
    }
    @Test void bothActualMinigunSubHitsPassArmorOnceAndEachKeepsOneNativeHealthWrite() throws Exception {
        var nativeTarget=new ExactSbwDamageProbe.Target();
        try(var zip=new ZipFile(SbwTestDependency.path().toFile())) {
            var gun=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("data/superbwarfare/sbw/guns/minigun.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            ExactSbwDamageProbe.bullet(gun.get("BypassesArmor").getAsFloat(),gun.get("Headshot").getAsFloat())
                    .performDamage(nativeTarget,gun.get("Damage").getAsFloat(),false);
        }
        Base ship=create();ship.health=8000;float before=ship.health;
        Bullet bullet=new Bullet("superbwarfare:minigun");
        for(var submitted:nativeTarget.submitted)hurt(ship,new Damage(new Actor(),bullet,submitted.source().id()),submitted.amount());
        assertEquals(.16,before-ship.health,.002);assertEquals(2,ship.nativeHurtCalls);
    }

    @Test void starHitAndDelayedSlashBothUseTheStarTierWithoutAnnihilatorBudget() throws Exception {
        Base ship=create();ship.health=8000;Actor star=projectile("super_star_projectile");
        hurt(ship,new Damage(new Actor(),star,"super_star_hit"),60);
        ship.world.now=2;hurt(ship,new Damage(new Actor(),star,"super_star_slash"),45);
        assertEquals(7994.75,ship.health,.002);assertEquals(2,ship.nativeHurtCalls);
        assertTrue(ship.annihilatorDamageBySource.isEmpty());
        // Delayed source still retains its damage key even when no direct entity is available.
        assertEquals(2.25,loss(null,"super_star_slash",45,0),.002);
    }

    @Test void shellsAndRocketsUseConfirmedIdentityWhileHeavyAndUnknownSourcesRetainBaseline() throws Exception {
        for(String id:List.of("small_cannon_shell","gun_grenade","grapeshot"))
            assertEquals(5,loss(projectile(id),"projectile_hit",100,0),.002,id);
        for(String id:List.of("cannon_shell","mortar_shell","small_rocket"))
            assertEquals(10,loss(projectile(id),"projectile_hit",100,0),.002,id);
        assertEquals(15,loss(projectile("medium_rocket"),"projectile_hit",100,0),.002);
        for(String id:List.of("javelin_missile","rpg_rocket_standard","agm_65","unknown_shell"))
            assertEquals(20,loss(projectile(id),"projectile_hit",100,0),.002,id);
        assertEquals(20,loss(new Bullet(null),"gunfire",100,0),.002,"generic bullets also carry vehicle cannon shots");
        assertEquals(20,loss(new Bullet("othermod:minigun"),"gunfire",100,0),.002);
        Actor other=projectile("small_cannon_shell");other.kind=new Kind("othermod:small_cannon_shell");
        assertEquals(20,loss(other,"projectile_hit",100,0),.002);
        assertEquals(10,loss(new Bullet("superbwarfare:ntw_20"),"gunfire",100,0),.002);
        assertEquals(10,loss(new Bullet("superbwarfare:m_2_hb"),"gunfire",100,0),.002);
    }

    @Test void explicitDirectPenetrationStillWorksOnceAndIsNotCopiedIntoBlast() throws Exception {
        Actor shell=projectile("cannon_shell");
        assertEquals(64,loss(shell,"projectile_hit",100,.60F),.002);
        assertEquals(10,loss(shell,"projectile_explosion",100,.60F),.002);
        assertEquals(80,loss(projectile("javelin_missile"),"projectile_hit",100,.75F),.002);
        assertEquals(100,loss(shell,"projectile_hit",100,1),.002);
    }

    @Test void allKnownLightGunIdsExistInTheExactDependencyAndUnknownGunKeepsItsDamage() throws Exception {
        try(var zip=new ZipFile(SbwTestDependency.path().toFile())) {
            var field=MothershipDefense.class.getDeclaredField("LIGHT_GUNS");field.setAccessible(true);
            @SuppressWarnings("unchecked") var ids=(java.util.Set<String>)field.get(null);
            for(String id:ids) {
                assertNotNull(zip.getEntry("data/superbwarfare/sbw/guns/"+id.split(":")[1]+".json"),id);
                assertEquals(2,loss(new Bullet(id),"gunfire_absolute",100,0),.002,id);
            }
            assertEquals(20,loss(new Bullet("superbwarfare:ql_1031"),"gunfire",100,0),.002);
        }
    }

    @Test void specialLaserDeadAndInvalidInputsDoNotEnterTheOrdinaryClassificationOrExtraCallback() throws Exception {
        Base ship=create();float before=ship.health;
        hurt(ship,new Damage(new Actor(),projectile("small_cannon_shell"),true,false),100);
        assertEquals(2,before-ship.health,.002);assertEquals(1,ship.nativeHurtCalls);
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})hurt(ship,new Damage(new Actor()),invalid);
        assertEquals(1,ship.nativeHurtCalls);
        ship.health=0;hurt(ship,new Damage(new Actor(),projectile("small_cannon_shell"),"projectile_hit"),100);
        assertEquals(1,ship.nativeHurtCalls);
    }

    private static Actor projectile(String path) {Actor direct=new Actor();direct.kind=new Kind("superbwarfare:"+path);return direct;}
    private static float loss(Actor direct,String key,float amount,float penetration)throws Exception {
        Base ship=create();ship.health=8000;Damage source=new Damage(new Actor(),direct,key);source.penetration=penetration;
        hurt(ship,source,amount);assertEquals(1,ship.nativeHurtCalls);return 8000-ship.health;
    }
    private static void hurt(Base ship,Damage source,float amount)throws Exception {
        ship.getClass().getMethod("hurt",Damage.class,float.class).invoke(ship,source,amount);
    }
}

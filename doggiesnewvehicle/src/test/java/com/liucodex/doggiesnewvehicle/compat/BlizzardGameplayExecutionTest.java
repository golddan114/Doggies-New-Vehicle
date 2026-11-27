package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor;
import com.liucodex.doggiesnewvehicle.armored.BlizzardRules;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs compiled hover, held-fire and NBT methods; geometry/world/native stores are explicit ports. */
class BlizzardGameplayExecutionTest {
    @Test void nativeFastReloadPausesWithoutPowerRestoresLegacyCooldownAndSeparatesModeActivation() throws Exception {
        var f=fixture();f.tank.fire=true;f.beamTick();assertEquals(1,f.tank.bolts);
        f.tank.fire=false;for(int i=0;i<3;i++)f.beamTick();assertEquals(.3F,f.tank.getChargeProgress(),.00001F);
        int energy=f.tank.energy;f.tank.energy=0;for(int i=0;i<20;i++)f.beamTick();assertEquals(.3F,f.tank.getChargeProgress(),.00001F);
        f.tank.energy=energy;var legacy=f.save();legacy.remove("BlizzardReloadTicks");legacy.putFloat("ChargeProgress",1);
        f.load(legacy);assertEquals(.3F,f.tank.getChargeProgress(),.00001F,"Legacy remaining cooldown owns the migration");
        f.tank.fire=true;for(int i=0;i<6;i++)f.beamTick();assertEquals(1,f.tank.bolts);f.beamTick();assertEquals(2,f.tank.bolts);
        f.tank.fire=false;f.tank.y=0;f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        assertEquals(0,f.tank.getChargeProgress());f.deployTicks(20);assertEquals(0,f.tank.getChargeProgress());
        f.tank.fire=true;f.chargeShot();assertEquals(1,f.tank.world.target.hurts);assertEquals(2,f.tank.bolts);
    }
    @Test void holdingFireDuringDeploymentStartsSixtyTickChargeOnlyAfterHatchOpens() throws Exception {
        var f=fixture();f.tank.y=0;
        f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        f.tank.fire=true;
        var admission=f.tank.getClass().getMethod("canShoot",Actor.class);
        for(int i=0;i<19;i++){
            assertTrue((boolean)admission.invoke(f.tank,f.tank.pilot));
            f.travel();f.beamTick();assertEquals(0,f.tank.world.target.hurts);
        }
        f.travel();f.beamTick();assertEquals(0,f.tank.getChargeProgress());
        for(int i=0;i<59;i++){f.beamTick();assertEquals(0,f.tank.world.target.hurts);}
        f.beamTick();assertEquals(1,f.tank.world.target.hurts);assertEquals(1,f.tank.energyWrites);
        for(int i=0;i<99;i++){f.beamTick();assertEquals(1,f.tank.world.target.hurts);}
        f.beamTick();assertEquals(2,f.tank.world.target.hurts);
    }

    @Test void nativeReleaseStopsDamageAndEnergyEvenWhenTheBarrelTurnsToANewTarget() throws Exception {
        var f=fixture();f.tank.y=0;f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        for(int i=0;i<20;i++){f.travel();f.beamTick();}f.tank.fire=true;f.chargeShot();
        var release=f.tank.getClass().getMethod("releaseNativeFire",Entity.class);
        release.invoke(f.tank,new Actor());assertTrue(f.tank.fire);
        release.invoke(f.tank,f.tank.pilot);assertFalse(f.tank.fire);assertFalse(f.tank.firing());
        assertFalse(((CompoundTag)f.tank.data.get(f.key("LASER"))).contains("Tick"));
        int spent=f.tank.energyWrites;var untouched=new Target();f.tank.world.extra.add(untouched);f.tank.turretYaw=90;
        for(int i=0;i<40;i++)f.beamTick();
        assertEquals(spent,f.tank.energyWrites);assertEquals(0,untouched.hurts);assertEquals(1,f.tank.world.target.hurts);
        f.tank.world.isClientSide=true;f.tank.fire=true;release.invoke(f.tank,f.tank.pilot);assertTrue(f.tank.fire);
    }
    @Test void jumpHudUsesAuthoritativeSnapshotAcrossCooldownSaveAndClientPause() throws Exception {
        var server=fixture();var client=fixture();client.tank.world.isClientSide=true;
        var publish=server.tank.getClass().getMethod("presentationSnapshot");
        var read=client.tank.getClass().getMethod("jumpCooldownTicks");
        assertEquals(1200,read.invoke(client.tank),"Unknown initial snapshot is not ready");
        client.tank.data.set(client.key("PRESENTATION"),publish.invoke(server.tank));
        assertEquals(0,read.invoke(client.tank));
        server.tank.jump=true;server.travel();
        client.tank.data.set(client.key("PRESENTATION"),publish.invoke(server.tank));
        assertEquals(1200,read.invoke(client.tank));
        server.tank.jump=false;
        for(int i=0;i<600;i++)server.travel();
        var saved=server.save();server.load(saved);
        client.tank.data.set(client.key("PRESENTATION"),publish.invoke(server.tank));
        assertEquals(600,read.invoke(client.tank));
        for(int i=0;i<100;i++)client.travel();
        assertEquals(600,read.invoke(client.tank),"No client time drift or prediction");
        assertEquals(0,client.tank.energyWrites);
        server.tank.data.set(server.key("MODE2"),true);server.travel();
        client.tank.data.set(client.key("PRESENTATION"),publish.invoke(server.tank));
        assertEquals(600,read.invoke(client.tank),"Existing deployed-mode cooldown pause is preserved");
        for(int[] sample:new int[][]{{-2,0},{0,0},{1,1},{1200,1200},{Integer.MAX_VALUE,1200}}) {
            var tag=new CompoundTag();tag.putInt("JumpCooldown",sample[0]);
            client.tank.data.set(client.key("PRESENTATION"),tag);
            assertEquals(sample[1],read.invoke(client.tank));
        }
        server=fixture();server.tank.energy=99_999;server.tank.jump=true;server.travel();
        assertEquals(0,server.integer("jumpCooldown"),"Rejected jump does not start charging");
    }
    @Test void compiledHudEndpointMatchesBeamWithoutWritingDamageResourcesOrSnapshot() throws Exception {
        var f=fixture();f.tank.world.target.pickable=false;
        var aim=f.tank.getClass().getMethod("laserAimPoint",float.class);
        assertEquals(new Vec3(0,1,80),aim.invoke(f.tank,.5F));
        f.tank.world.blocked=true;assertEquals(new Vec3(0,1,12),aim.invoke(f.tank,.5F));
        f.tank.world.blocked=false;f.tank.world.target.pickable=true;
        assertEquals(new Vec3(0,1,80),aim.invoke(f.tank,.5F));
        f.tank.world.clear=false;assertEquals(new Vec3(0,1,80),aim.invoke(f.tank,.5F));
        assertEquals(0,f.tank.energyWrites);assertEquals(0,f.tank.world.target.hurts);
        assertFalse(((CompoundTag)f.tank.data.get(f.key("LASER"))).contains("Tick"));
    }
    @Test void compiledImpactCallbackUpdatesTheMatchingAcceptedRoundInsteadOfOnlyTheLatest()throws Exception{
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",
            Set.of("endPulse"),new HashMap<>(),PulseHistoryBody.class,false);
        var body=(PulseHistoryBody)type.getConstructor().newInstance();
        var first=new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse(1,Vec3.ZERO,new Vec3(0,0,8),256);
        var second=new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse(11,new Vec3(2,0,0),new Vec3(0,0,8),256);
        body.recentPulses.put(1L,first);body.recentPulses.put(11L,second);body.latestPulse=second;
        var end=type.getMethod("endPulse",long.class,double.class);
        end.invoke(body,1L,192d);assertEquals(192,body.recentPulses.get(1L).distance());assertSame(second,body.latestPulse);
        end.invoke(body,11L,24d);assertEquals(24,body.latestPulse.distance());assertEquals(body.latestPulse,body.recentPulses.get(11L));
        end.invoke(body,1L,256d);assertEquals(192,body.recentPulses.get(1L).distance());
        end.invoke(body,99L,10d);assertEquals(2,body.recentPulses.size());
        end.invoke(body,11L,Double.NaN);assertEquals(24,body.latestPulse.distance());
    }
    public static class PulseHistoryBody{
        public java.util.Map<Long,com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse> recentPulses=new java.util.LinkedHashMap<>();
        public com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse latestPulse;
    }
    @Test void primaryHudUsesFinal256RangeForPulseAnd80ForPiercingWithWallsAndTargets()throws Exception{
        var f=fixture();f.tank.world.target.pickable=false;
        var aim=f.tank.getClass().getMethod("primaryAimPoint",float.class);
        assertEquals(new Vec3(0,1,256),aim.invoke(f.tank,.5F));
        f.tank.world.target.pickable=true;assertEquals(new Vec3(0,1,12),aim.invoke(f.tank,.5F));
        f.tank.world.target.pickable=false;f.tank.world.blocked=true;assertEquals(new Vec3(0,1,12),aim.invoke(f.tank,.5F));
        f.tank.world.blocked=false;f.tank.world.clear=false;assertEquals(new Vec3(0,1,256),aim.invoke(f.tank,.5F));
        f.tank.world.clear=true;f.tank.data.set(f.key("MODE2"),true);
        assertEquals(new Vec3(0,1,80),aim.invoke(f.tank,.5F));
        assertEquals(0,f.tank.energyWrites);assertEquals(0,f.tank.world.target.hurts);
    }
    @Test void primaryHudDoesNotChasePickableColdRoundsAfterFiring() throws Exception {
        var f=fixture();var aim=f.tank.getClass().getMethod("primaryAimPoint",float.class);
        f.tank.world.target.pickable=false;
        f.tank.world.extra.add(new ColdBolt());
        assertEquals(new Vec3(0,1,256),aim.invoke(f.tank,.5F),"A live native destroyable missile cannot attract the HUD");
        f.tank.world.loadedLimit=160;
        assertEquals(new Vec3(0,1,160),aim.invoke(f.tank,.5F),"Short render distance keeps the display on the loaded ray instead of falling back to a projectile");
        f.tank.world.target.pickable=true;
        assertEquals(new Vec3(0,1,12),aim.invoke(f.tank,.5F),"Real targets still bound the aim point");
        assertEquals(0,f.tank.energyWrites);assertEquals(0,f.tank.world.target.hurts);
    }
    @Test void compiledMode2TracePublishesEightyAndRetainsWallAndUnloadedVeto() throws Exception {
        var f=fixture();f.tank.world.target.pickable=false;
        var trace=f.tank.getClass().getMethod("traceLaser",boolean.class);
        assertTrue((boolean)trace.invoke(f.tank,true));
        var tag=(CompoundTag)f.tank.data.get(f.key("LASER"));
        var origin=new Vec3(tag.getDouble("Ox"),tag.getDouble("Oy"),tag.getDouble("Oz"));
        var end=new Vec3(tag.getDouble("Ex"),tag.getDouble("Ey"),tag.getDouble("Ez"));
        assertEquals(80,origin.distanceTo(end),1e-8);
        f.tank.world.blocked=true;assertTrue((boolean)trace.invoke(f.tank,true));
        tag=(CompoundTag)f.tank.data.get(f.key("LASER"));assertEquals(12,tag.getDouble("Ez"));
        f.tank.world.clear=false;assertFalse((boolean)trace.invoke(f.tank,true));
        assertEquals(0,f.tank.world.target.hurts);assertEquals(0,f.tank.energyWrites);
    }
    @Test void onePaidPulseHurtsAllTraceTargetsAndVisualTicksNeitherQueryNorHurtAgain() throws Exception {
        var f=fixture();f.tank.y=0;
        f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        f.deployTicks(20);
        var medium = new ArmoredTarget(DnvColdArmor.Tier.MEDIUM);
        var heavy = new ArmoredTarget(DnvColdArmor.Tier.HEAVY);
        f.tank.world.extra.addAll(List.of(medium, heavy));f.tank.fire=true;
        int before=f.tank.energy;f.chargeShot();
        assertEquals(75_000,before-f.tank.energy);assertEquals(1,f.tank.energyWrites);
        assertEquals(1,f.tank.world.target.hurts);assertEquals(1,medium.hurts);assertEquals(1,heavy.hurts);
        assertEquals(666F,f.tank.world.target.damage,.001F);
        assertEquals(399.6F,medium.damage,.001F);assertEquals(199.8F,heavy.damage,.001F);
        assertEquals(80,((CompoundTag)f.tank.data.get(f.key("LASER"))).getDouble("Ez"));
        for(int i=0;i<40;i++)f.tickOnly();
        assertEquals(1,f.tank.world.traceQueries);assertEquals(1,medium.hurts);assertEquals(1,heavy.hurts);
        assertEquals(1,f.tank.energyWrites);
    }
    @Test void mode2UsesSameEnergyThresholdAndCannotFireWithoutAnAuthenticatedCharge() throws Exception {
        var f=deployed();f.tank.fire=true;f.tank.energy=74_999;
        var admission=f.tank.getClass().getMethod("canShoot",Actor.class);
        assertFalse((boolean)admission.invoke(f.tank,f.tank.pilot));f.requestShot();
        for(int i=0;i<120;i++)f.tickOnly();assertEquals(0,f.tank.energyWrites);
        assertEquals(0,f.tank.getChargeProgress());
        f.tank.energy=75_000;assertTrue((boolean)admission.invoke(f.tank,f.tank.pilot));
        for(int i=0;i<120;i++)f.tickOnly();assertEquals(0,f.tank.world.target.hurts);
        f.chargeShot();assertEquals(0,f.tank.energy);assertEquals(1,f.tank.energyWrites);
        for(int i=0;i<200;i++)f.beamTick();assertEquals(1,f.tank.world.target.hurts);
    }

    @Test void staleMovementFireBitNeverEmitsAfterReleaseOrTurningWithoutAnotherNativeShotRequest() throws Exception {
        var f=fixture();f.tank.y=0;
        f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        f.deployTicks(20);f.tank.fire=true;f.chargeShot();
        assertEquals(1,f.tank.world.target.hurts);
        f.tank.getClass().getMethod("releaseNativeFire",Entity.class).invoke(f.tank,f.tank.pilot);
        f.tank.fire=true; // Reproduce a late movement packet reasserting the old held bit.
        var target=new Target();f.tank.world.extra.add(target);f.tank.turretYaw=90;
        for(int tick=0;tick<200;tick++) f.tickOnly();
        assertEquals(0,target.hurts);assertEquals(1,f.tank.energyWrites);assertEquals(1,f.tank.world.traceQueries);
        f.chargeShot();assertEquals(1,target.hurts);assertEquals(2,f.tank.energyWrites);
    }
    @Test void paidAscentHoldsForOneHundredTicksThenFallsWithoutAnExtraJumpDebit() throws Exception {
        var f=fixture();f.tank.energy=100_000;f.tank.jump=true;
        int elapsed=0;
        do {f.travel();f.tank.y+=f.tank.velocity.y;assertTrue(++elapsed<65);} while(f.integer("jumpHoldTicks")==0);
        assertEquals(100,f.integer("jumpHoldTicks"));assertEquals(0,f.integer("ascentTicks"));
        assertEquals(0,f.tank.energy);assertEquals(1,f.tank.energyWrites);
        assertFalse((boolean)f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot));
        for(int i=1;i<100;i++) {
            f.travel();f.tank.y+=f.tank.velocity.y;
            assertEquals(100-i,f.integer("jumpHoldTicks"));assertEquals(10,f.tank.y,.08);
        }
        f.travel();assertEquals(0,f.integer("jumpHoldTicks"));assertTrue(f.tank.velocity.y<0);
        assertEquals(1,f.tank.energyWrites);
    }
    @Test void ceilingDeathAndReloadCannotRetainPrepaidHold() throws Exception {
        for(int reason=0;reason<3;reason++) {
            var f=fixture();f.tank.jump=true;f.travel();
            f.tank.y=10;
            if(reason==0)f.tank.verticalCollision=true;
            if(reason==1)f.tank.wreck=true;
            if(reason==2) {f.travel();assertEquals(100,f.integer("jumpHoldTicks"));f.load(f.save());}
            f.travel();assertEquals(0,f.integer("jumpHoldTicks"));assertEquals(0,f.integer("ascentTicks"));
            assertTrue(f.tank.velocity.y<=0);
        }
    }
    @Test void laserSoundUsesIncreasedVolumeWithoutChangingPulseSound() throws Exception {
        var f=fixture();f.tank.world.isClientSide=true;
        var shot=new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse(10,new Vec3(1,2,3),Vec3.ZERO,0);
        for(int tick=0;tick<24;tick++) {
            var pose=new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(tick,Vec3.ZERO,0,0,0,0,0,Vec3.ZERO);
            f.tank.data.set(f.key("PRESENTATION"),com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.encode(pose,tick>=10?shot:null));
            f.tank.getClass().getMethod("handleClientSync").invoke(f.tank);
        }
        assertEquals(1,f.tank.world.fireSounds);assertEquals(2F,f.tank.world.soundVolume);
    }
    @Test void lostSupportPowerTvWeaponPilotOrUnloadedPathCannotCompleteChargedDamage() throws Exception {
        for(int reason=0;reason<7;reason++) {
            var f=deployed();f.tank.fire=true;f.requestShot();
            for(int i=0;i<59;i++)f.tickOnly();
            if(reason==0){f.tank.ground=Double.NaN;f.travel();}
            if(reason==1)f.tank.energy=0;
            if(reason==2)f.tank.data.set(f.key("TV_ACTIVE"),true);
            if(reason==3)f.tank.world.clear=false;
            if(reason==4)f.tank.selected=1;
            if(reason==5)f.tank.pilot=new Actor();
            if(reason==6)f.tank.world.blocked=true;
            f.tickOnly();assertEquals(0,f.tank.energyWrites,"cause "+reason);assertEquals(0,f.tank.world.target.hurts);
        }
    }

    @Test void leavingDeploymentClosesDoorsBeforeHoverAndCannotReplayAnExtraPulse() throws Exception {
        var f = fixture(); f.tank.y = 0;
        var toggle = f.tank.getClass().getMethod("requestMode", Actor.class);
        assertTrue((boolean) toggle.invoke(f.tank, f.tank.pilot));
        f.deployTicks(20);
        f.tank.fire = true; f.chargeShot();
        assertTrue((boolean) toggle.invoke(f.tank, f.tank.pilot));
        for (int i = 0; i < 19; i++) {
            f.tank.forward = true; f.travel(); f.beamTick();
            assertFalse((boolean) f.tank.getClass().getMethod("hovering").invoke(f.tank));
            assertEquals(0, f.tank.velocity.horizontalDistance());
            assertEquals(1, f.tank.energyWrites);
        }
        f.travel(); assertTrue((boolean) f.tank.getClass().getMethod("hovering").invoke(f.tank));
        assertEquals(1, f.tank.world.target.hurts);
    }
    @Test void fluidSupportKeepsHalfBlockHoverAndRejectsDeploymentAndMode2NeverJumps() throws Exception {
        var f=fixture(); f.tank.y=.5; f.tank.getClass().getField("fluidSupported").setBoolean(f.tank,true);
        assertFalse((boolean)f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot));
        for(int i=0;i<50;i++){f.travel();f.tank.y+=f.tank.velocity.y;}
        assertEquals(.5,f.tank.y,1e-9);
        f.tank.getClass().getField("fluidSupported").setBoolean(f.tank,false);f.tank.y=0;
        assertTrue((boolean)f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot));
        f.tank.jump=true;
        for(int i=0;i<25;i++)f.travel();
        assertEquals(0,f.integer("ascentTicks"));assertEquals(50,f.tank.energyWrites);assertEquals(Vec3.ZERO,f.tank.velocity);
    }
    @Test void releaseDecaysSlowlyAndResumeNeedsRequestWhileTurningNeverHurts() throws Exception {
        var f=deployed();f.tank.fire=true;f.requestShot();
        for(int i=0;i<30;i++)f.tickOnly();assertEquals(.5F,f.tank.charge,.00001F);
        f.tank.getClass().getMethod("releaseNativeFire",Entity.class).invoke(f.tank,f.tank.pilot);
        f.tank.turretYaw=90;for(int i=0;i<30;i++)f.tickOnly();
        assertEquals(.25F,f.tank.charge,.00001F);assertEquals(0,f.tank.world.target.hurts);
        f.tank.fire=true;f.tickOnly();assertTrue(f.tank.charge<.25F,"late movement cannot rearm");
        f.requestShot();for(int i=0;i<45;i++)f.tickOnly();assertEquals(0,f.tank.world.target.hurts);
        f.tickOnly();assertEquals(1,f.tank.world.target.hurts);
        assertEquals(1,f.tank.energyWrites);assertEquals(2,f.phase());
    }

    @Test void savedCoolingSurvivesReleaseModeSwitchAndLoadButChargingNeverResumesOnLoad() throws Exception {
        var f=deployed();f.tank.fire=true;f.chargeShot();
        for(int i=0;i<7;i++)f.tickOnly();var saved=f.save();float remaining=f.tank.charge;
        f.load(saved);assertEquals(2,f.phase());assertEquals(remaining,f.tank.charge);
        for(int i=0;i<20;i++)f.travel();
        for(int i=0;i<32;i++){f.tank.fire=true;f.requestShot();f.tickOnly();assertEquals(1,f.tank.energyWrites);}
        f.tickOnly();assertEquals(1,f.phase());assertEquals(0,f.tank.charge);
        for(int i=0;i<59;i++)f.tickOnly();assertEquals(1,f.tank.energyWrites);f.tickOnly();assertEquals(2,f.tank.energyWrites);
        f=deployed();f.tank.fire=true;f.requestShot();for(int i=0;i<59;i++)f.tickOnly();
        f.load(f.save());assertEquals(0,f.phase());assertEquals(0,f.tank.charge);
        for(int i=0;i<100;i++){f.travel();f.tickOnly();}assertEquals(0,f.tank.energyWrites);
        f=deployed();f.tank.fire=true;f.chargeShot();
        f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        for(int i=0;i<39;i++){f.travel();f.beamTick();assertEquals(0,f.tank.bolts);}
        f.beamTick();assertEquals(1,f.tank.bolts,"mode one cannot bypass the remaining cooling");
    }

    @Test void chargedCadenceIsTwelveShotsPerMinuteRegardlessOfDuplicateRequests() throws Exception {
        var f=deployed();var ticks=new ArrayList<Integer>();f.tank.fire=true;int energy=f.tank.energy;f.requestShot();
        for(int tick=1;tick<=1200;tick++) {
            f.tank.world.time=tick;int before=f.tank.energyWrites;
            f.requestShot();f.requestShot();f.tickOnly();
            if(f.tank.energyWrites>before)ticks.add(tick);
        }
        assertEquals(12,ticks.size());assertEquals(List.of(60,160,260),ticks.subList(0,3));
        assertEquals(7992,f.tank.world.target.damage,.01F);assertEquals(900_000,energy-f.tank.energy);
        assertEquals(12,f.tank.world.traceQueries);assertEquals(0,f.tank.bolts);
    }

    @Test void deploymentDescendsAndCannotFireBeforeLandingOrAcrossRestoreWithoutReopening() throws Exception {
        var f = fixture(); f.tank.y = 4;
        var toggle = f.tank.getClass().getMethod("requestMode", Actor.class);
        assertFalse((boolean) toggle.invoke(f.tank, new Actor()));
        assertTrue((boolean) toggle.invoke(f.tank, f.tank.pilot));
        assertFalse((boolean) toggle.invoke(f.tank, f.tank.pilot), "request cooldown rejects repeats");
        f.tank.fire = true; f.beamTick(); assertEquals(0, f.tank.energyWrites);
        for (int i = 0; i < 80; i++) { f.travel(); f.tank.y += f.tank.velocity.y; }
        assertTrue(f.tank.y >= 0 && f.tank.y <= .08); assertEquals(0, f.tank.velocity.horizontalDistance());
        assertTrue((boolean) f.tank.getClass().getMethod("mode2Ready").invoke(f.tank));
        var tag = f.save(); assertTrue(tag.getBoolean("BlizzardMode2")); f.load(tag);
        assertFalse((boolean) f.tank.getClass().getMethod("mode2Ready").invoke(f.tank));
        assertFalse(f.tank.firing()); f.tank.fire = true; f.beamTick(); assertEquals(0, f.tank.energyWrites);
        f.tank.fire=false;f.deployTicks(20);f.tank.fire=true;
        f.chargeShot(); assertEquals(1, f.tank.world.target.hurts);
        f.tank.pilot = null; f.beamTick(); assertFalse(f.tank.firing());
    }
    @Test void actualClientSyncPlaysAcceptedFireOnPresentationClockAtOriginalWorldMuzzleOnce() throws Exception {
        var f = fixture(); f.tank.world.isClientSide = true;
        var origin = new Vec3(8, 2, -4);
        var shot = new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pulse(10, origin, new Vec3(0,0,8), 48);
        for (int tick = 0; tick < 24; tick++) {
            var pose = new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(tick,
                    new Vec3(tick, 1, 2), 0, 0, 0, 0, 0, new Vec3(1,0,0));
            f.tank.data.set(f.key("PRESENTATION"), com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.encode(pose, tick >= 10 ? shot : null));
            f.tank.getClass().getMethod("handleClientSync").invoke(f.tank);
            if (tick <= 12) assertEquals(0, f.tank.world.fireSounds);
        }
        assertEquals(1, f.tank.world.fireSounds);
        assertEquals(origin, f.tank.world.soundOrigin);
        ClassNode source = read("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity");
        var emit = source.methods.stream().filter(m -> m.name.equals("emitColdBolt")).findFirst().orElseThrow();
        for (var instruction : emit.instructions) {
            if (instruction instanceof MethodInsnNode call) assertFalse(call.name.equals("playSound"), "No second undelayed server fire sound");
        }
    }
    @Test void hoverAttitudeHasWeightSettlesAndKeepsTheSameWorldBarrelRay() throws Exception {
        var f=fixture();f.tank.y=.5;f.tank.forward=true;f.tank.left=true;
        f.tank.turretYaw=40;f.tank.turretPitch=-20;
        Vec3 initial=f.tank.getShootVec("ColdBeam",1);
        double maximumPitch=0,maximumRoll=0;
        for(int i=0;i<70;i++) {
            f.travel();maximumPitch=Math.max(maximumPitch,Math.abs(f.tank.pitch));maximumRoll=Math.max(maximumRoll,Math.abs(f.tank.roll));
            assertEquals(0,initial.distanceTo(f.tank.getShootVec("ColdBeam",1)),2e-6);
        }
        assertTrue(maximumPitch>1);assertTrue(maximumRoll>1);
        f.tank.forward=f.tank.left=false;
        for(int i=0;i<100;i++)f.travel();
        assertEquals(0,f.tank.pitch,1e-5);assertEquals(0,f.tank.roll,1e-5);
        assertEquals(0,f.tank.velocity.horizontalDistance(),1e-10);
        double min=1,max=0;
        for(int i=0;i<400;i++){f.tank.tickCount++;f.travel();f.tank.y+=f.tank.velocity.y;min=Math.min(min,f.tank.y);max=Math.max(max,f.tank.y);}
        assertTrue(min>.47&&max<.53);assertTrue(max-min>.02,"idle lift is a small server-owned movement");
    }
    @Test void localPilotConsumesServerMotionInsteadOfDiscardingInterpolation() throws Exception {
        var f = fixture(); f.tank.world.isClientSide = true;
        assertFalse(f.tank.isControlledByLocalInstance());
        var pose = new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(20,
                new Vec3(10, 1, 2), 35, 3, -2, 90, -10, new Vec3(.6, 0, .2), true);
        f.tank.data.set(f.key("PRESENTATION"), com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.encode(pose, null));
        f.tank.getClass().getMethod("handleClientSync").invoke(f.tank);
        assertEquals(10, f.tank.x); assertEquals(35, f.tank.yaw);
        assertEquals(90, f.tank.turretYaw); assertEquals(-10, f.tank.turretPitch);
        assertEquals(Vec3.ZERO, f.tank.velocity, "native move must not add presentation velocity again");
        assertEquals(0, f.tank.turretSounds, "joining must not sound like turning from zero");
        pose = new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.Pose(23,
                new Vec3(10, 1, 2), 35, 3, -2, 93, -9, new Vec3(.6, 0, .2), true);
        f.tank.data.set(f.key("PRESENTATION"), com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation.encode(pose, null));
        f.tank.getClass().getMethod("handleClientSync").invoke(f.tank);
        assertEquals(1, f.tank.turretSounds);
        assertEquals(1F, f.tank.soundYaw, 1e-6);
        assertEquals(1F/3F, f.tank.soundPitch, 1e-6);
        f.tank.world.isClientSide = false;
        assertTrue(f.tank.isControlledByLocalInstance());
    }
    @Test void heldPulsesPayFixedPriceAndStopsWithoutSideEffectsOnReleaseOrInvalidPilot() throws Exception {
        var f = fixture(); f.tank.energy = 540_000; f.tank.fire = true;
        for (int tick = 0; tick < 1800; tick++) f.beamTick();
        assertEquals(0, f.tank.energy); assertEquals(180, f.tank.energyWrites);
        assertEquals(180, f.tank.bolts);
        f.beamTick(); assertEquals(180, f.tank.energyWrites); assertFalse(f.tank.firing());
        for (int reason = 0; reason < 7; reason++) {
            f = fixture(); f.tank.fire = true;
            if (reason == 0) f.tank.fire = false;
            if (reason == 1) f.tank.pilot = null;
            if (reason == 2) f.tank.pilot.alive = false;
            if (reason == 3) f.tank.pilot.spectator = true;
            if (reason == 4) f.tank.selected = 1;
            if (reason == 5) f.tank.world.clear = false;
            if (reason == 6) f.tank.spawnAllowed = false;
            f.beamTick(); assertEquals(0, f.tank.energyWrites); assertEquals(0, f.tank.world.target.hurts);
            assertFalse(f.tank.firing());
        }
    }
    @Test void pulseReleaseAndSaveRestoreKeepCooldownAndClearOldResidueAndHeldFire() throws Exception {
        var f = fixture(); f.tank.energy = 540_000; f.tank.fire = true;
        for (int i = 0; i < 7; i++) f.beamTick();
        int before = f.tank.energy;
        var tag = f.save(); assertEquals(0, tag.getInt("BlizzardBeamRemainder"));
        tag.putInt("BlizzardBeamRemainder", 179);
        f.tank.fire = false; f.beamTick(); assertEquals(before, f.tank.energy);
        f.load(tag);
        assertEquals(0, f.save().getInt("BlizzardBeamRemainder"));
        assertFalse(f.tank.fire); assertFalse(f.tank.firing());
        f.tank.fire = true;
        for (int i = 0; i < 1793; i++) f.beamTick();
        assertEquals(0, f.tank.energy); assertEquals(180, f.tank.energyWrites);
    }
    @Test void jumpUsesRisingEdgeAndPaysSeparateHoverWithoutReplayingAcrossSave() throws Exception {
        var f = fixture(); f.tank.jump = true; f.travel();
        assertEquals(1_899_984, f.tank.energy); assertEquals(2, f.tank.energyWrites);
        assertEquals(1200, f.integer("jumpCooldown"));
        double highest = f.tank.y;
        for (int i = 0; i < 80; i++) { f.tank.y += f.tank.velocity.y; highest = Math.max(highest, f.tank.y); f.travel(); }
        assertTrue(highest > 9.5 && highest <= 10);
        assertEquals(1_899_984 - 80 * 16, f.tank.energy);
        f.tank.jump = false; f.travel(); f.tank.jump = true; f.travel();
        assertEquals(1_899_984 - 82 * 16, f.tank.energy);
        var tag = f.save(); int remaining = tag.getInt("BlizzardJumpCooldown");
        f.load(tag); assertEquals(remaining, f.integer("jumpCooldown")); assertEquals(0, f.integer("ascentTicks"));
        int before = f.tank.energy;
        f.tank.y = 1; f.tank.jump = true; f.travel(); assertEquals(before - 16, f.tank.energy);
        tag.putInt("BlizzardJumpCooldown", -1); f.load(tag); f.tank.energy = 99_999;
        f.tank.jump = true; f.travel(); assertEquals(99_983, f.tank.energy);
        assertEquals(0, f.integer("ascentTicks"));
    }
    @Test void lowEnergyCannotProduceUnpaidHoverThrustOrTurnAndExactHoverPaysOnce() throws Exception {
        var f = fixture(); f.tank.energy = 15; f.tank.forward = f.tank.left = true;
        f.travel(); assertEquals(15, f.tank.energy); assertEquals(0, f.tank.energyWrites);
        assertEquals(0, f.tank.yaw); assertEquals(0, f.tank.velocity.horizontalDistance());
        assertTrue(f.tank.velocity.y < 0);
        f = fixture(); f.tank.energy = 16; f.travel();
        assertEquals(0, f.tank.energy); assertEquals(1, f.tank.energyWrites);
        f.travel(); assertEquals(1, f.tank.energyWrites);
        f = fixture(); f.tank.energy = 100_000; f.tank.jump = true; f.travel();
        assertEquals(0, f.tank.energy); assertEquals(1, f.tank.energyWrites);
        assertTrue(f.tank.velocity.y > 0, "Paid ascent survives an empty battery");
    }
    @Test void hoverRunsOnlyOnServerAndMissingSupportPowerOrPilotCannotAuthorizeDrive() throws Exception {
        var f = fixture(); f.tank.forward = true;
        for (int i = 0; i < 100; i++) f.travel();
        assertEquals(BlizzardRules.FORWARD_SPEED, f.tank.velocity.z, 1e-12);
        f.tank.pilot = null; f.travel(); assertFalse(f.tank.forward);
        for (int i = 0; i < 60; i++) f.travel(); assertEquals(0, f.tank.velocity.z, 1e-12);
        f.tank.ground = Double.NaN; f.travel(); assertTrue(f.tank.velocity.y < 0);
        f.tank.ground = 0; f.tank.energy = 0; f.tank.forward = true; f.travel();
        assertFalse(f.tank.forward); assertTrue(f.tank.velocity.y < 0);
        int writes = f.tank.energyWrites; Vec3 previous = f.tank.velocity; f.tank.world.isClientSide = true; f.tank.forward = true; f.travel();
        assertSame(previous, f.tank.velocity); assertEquals(writes, f.tank.energyWrites);
    }

    @Test void immediatePulsePaysOnceAndReleaseOrSaveCannotBypassHalfSecondCooldown() throws Exception {
        var f = fixture(); f.tank.fire = true;
        f.beamTick(); assertEquals(1, f.tank.bolts); assertEquals(1, f.tank.energyWrites); assertTrue(f.tank.firing());
        assertEquals(1.6F, f.tank.getTurretTurnXSpeed()); assertEquals(3.2F, f.tank.getTurretTurnYSpeed());
        for (int i = 0; i < 9; i++) {
            f.tank.fire = i % 2 == 0; f.beamTick();
            assertEquals(1, f.tank.bolts); assertEquals(1, f.tank.energyWrites);
        }
        var tag = f.save(); f.load(tag);
        f.tank.fire = true; f.beamTick(); assertEquals(2, f.tank.bolts);
        f.tank.fire = false; f.beamTick(); assertFalse(f.tank.firing());
        assertEquals(2F, f.tank.getTurretTurnXSpeed());
        f = fixture(); f.tank.fire = true; f.tank.spawnAllowed = false;
        f.beamTick(); assertEquals(0, f.tank.energyWrites); assertEquals(0, f.tank.bolts);
    }
    @Test void actualTravelStartsExponentiallyAndStopsGroundLateralSlipInOneTick() throws Exception {
        var f = fixture(); f.tank.y = .5; f.tank.forward = true;
        f.tank.velocity = new Vec3(.5, 0, 0); f.travel();
        assertEquals(.225, f.tank.velocity.x, 1e-12);
        double first = f.tank.velocity.z;
        for (int i = 1; i < 10; i++) f.travel();
        assertTrue(first < .01); assertTrue(f.tank.velocity.z < .07);
        for (int i = 10; i < 65; i++) f.travel();
        assertEquals(BlizzardRules.FORWARD_SPEED, f.tank.velocity.z, 1e-12);
        f.tank.forward = false; double travelled = 0;
        for (int i = 0; i < 10; i++) { f.travel(); travelled += f.tank.velocity.z; }
        assertEquals(0, f.tank.velocity.z); assertTrue(travelled < .85);
    }
    @Test void realTravelUsesResponsiveSteeringAndReleaseRampWithoutASecondMoveWriter() throws Exception {
        var f = fixture(); f.tank.left = true;
        for (int i = 0; i < 8; i++) f.travel();
        assertTrue(f.tank.yaw < -15, "Low-speed pivot must respond substantially faster than the braking ramp");
        assertTrue(Math.abs(f.tank.getClass().getField("steeringRate").getFloat(f.tank)) <= 3.2F);
        f.tank.left = false;
        for (int i = 0; i < 8; i++) f.travel();
        float settled = f.tank.yaw; f.travel(); assertEquals(settled, f.tank.yaw);
        f.tank.left = true; f.tank.energy = 0;
        for (int i = 0; i < 8; i++) f.travel();
        assertEquals(settled, f.tank.yaw);
    }
    @Test void changingDirectionOrLeavingAWallRestartsAccelerationInsteadOfJumpingToTopSpeed() throws Exception {
        var f = fixture(); f.tank.y = .5; f.tank.forward = true;
        for (int i = 0; i < 40; i++) f.travel();
        f.tank.forward = false; f.tank.back = true;
        for (int i = 0; i < 10; i++) f.travel();
        assertTrue(Math.abs(f.tank.velocity.z) < .02, "brake before reversing");
        f.tank.back = false; f.tank.forward = true; f.tank.velocity = Vec3.ZERO;
        f.tank.horizontalCollision = true;
        for (int i = 0; i < 45; i++) f.travel();
        f.tank.horizontalCollision = false; f.travel();
        assertTrue(f.tank.velocity.z < .01, "wall contact cannot accumulate full throttle");
    }

    private Fixture deployed() throws Exception {
        var f=fixture();f.tank.y=0;
        f.tank.getClass().getMethod("requestMode",Actor.class).invoke(f.tank,f.tank.pilot);
        f.deployTicks(20);return f;
    }
    record Fixture(Tank tank, Object beam) {
        int phase() throws Exception { return (int)beam.getClass().getMethod("phase").invoke(beam); }
        void chargeShot() throws Exception { requestShot();for(int i=0;i<60;i++)tickOnly(); }
        void deployTicks(int ticks) throws Exception { for(int i=0;i<ticks;i++){travel();beamTick();} }
        void travel() throws Exception { tank.getClass().getMethod("travel").invoke(tank); }
        void tickOnly() throws Exception { beam.getClass().getMethod("tick").invoke(beam); }
        void requestShot() throws Exception { beam.getClass().getMethod("fire").invoke(beam); }
        void beamTick() throws Exception { tickOnly(); if(tank.fire) requestShot(); }
        int integer(String name) throws Exception { return tank.getClass().getField(name).getInt(tank); }
        Key key(String name) throws Exception { return (Key) tank.getClass().getField(name).get(null); }
        CompoundTag save() throws Exception {
            var tag = new CompoundTag(); tank.getClass().getMethod("addAdditionalSaveData", CompoundTag.class).invoke(tank, tag); return tag;
        }
        void load(CompoundTag tag) throws Exception { tank.getClass().getMethod("readAdditionalSaveData", CompoundTag.class).invoke(tank, tag); }
    }
    private Fixture fixture() throws Exception {
        String root = "com/liucodex/doggiesnewvehicle/", source = root + "armored/DnvBlizzardEntity";
        String beam = root + "armored/BlizzardColdBeam", target = root + "compat/gameplayprobe/Tank", beamTarget = target + "Beam";
        String damage = root + "armored/BlizzardColdDamage", damageTarget = target + "Damage";
        Map<String, String> names = new HashMap<>(); names.put(source, target); names.put(beam, beamTarget);
        names.put("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Type.getInternalName(Tank.class));
        names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Entity.class));
        names.put("net/minecraft/world/entity/player/Player", Type.getInternalName(Actor.class));
        names.put("net/minecraft/world/entity/LivingEntity", Type.getInternalName(Actor.class));
        names.put("com/atsuishio/superbwarfare/tools/DamageHandlerKt", Type.getInternalName(ForcedDamage.class));
        names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
        names.put("net/minecraft/network/syncher/SynchedEntityData", Type.getInternalName(Data.class));
        names.put("net/minecraft/network/syncher/EntityDataAccessor", Type.getInternalName(Key.class));
        names.put("net/minecraft/resources/ResourceKey", Type.getInternalName(Key.class));
        names.put("net/minecraft/resources/ResourceLocation", Type.getInternalName(Location.class));
        names.put("net/minecraft/core/registries/Registries", Type.getInternalName(Keys.class));
        names.put("net/minecraft/core/RegistryAccess", Type.getInternalName(Registry.class));
        names.put("net/minecraft/core/Registry", Type.getInternalName(Registry.class));
        names.put("net/minecraft/core/Holder$Reference", Type.getInternalName(Holder.class));
        names.put("net/minecraft/core/Holder", Type.getInternalName(Holder.class));
        names.put("net/minecraft/world/damagesource/DamageSource", Type.getInternalName(Cold.class));
        names.put("net/minecraft/world/level/ClipContext", Type.getInternalName(Clip.class));
        names.put("net/minecraft/world/phys/BlockHitResult", Type.getInternalName(Hit.class));
        names.put("net/minecraft/world/phys/HitResult", Type.getInternalName(Hit.class));
        names.put(root + "armored/BlizzardLoadedPath", Type.getInternalName(Loaded.class));
        names.put(root + "armored/BlizzardTrace", Type.getInternalName(Trace.class));
        names.put(root + "armored/BlizzardTrace$Hit", Type.getInternalName(Trace.Hit.class));
        names.put(root + "armored/DnvBlizzardColdBolt", Type.getInternalName(ColdBolt.class));
        names.put(root + "armored/BlizzardRig", Type.getInternalName(Rig.class));
        names.put(damage, damageTarget);
        names.put(root + "artillery/DnvArtillerySounds", Type.getInternalName(Sounds.class));
        names.put("net/minecraftforge/registries/RegistryObject", Type.getInternalName(SoundRef.class));
        names.put("net/neoforged/neoforge/registries/DeferredHolder", Type.getInternalName(SoundRef.class));
        names.put("net/minecraft/sounds/SoundEvent", Type.getInternalName(Sound.class));
        Map<String, byte[]> definitions = new HashMap<>();
        ClassNode vehicle = read(source);
        vehicle.interfaces.clear();
        vehicle.methods.removeIf(m -> !Set.of("powered", "hasTv", "driverReady", "travel", "setFiring", "firing", "getTurretTurnXSpeed", "getTurretTurnYSpeed", "isControlledByLocalInstance", "handleClientSync",
                "addAdditionalSaveData", "readAdditionalSaveData", "mode2", "mode2Ready", "hovering", "clearLaser", "traceLaser", "laserAimPoint", "primaryAimPoint", "deploymentTravel", "requestMode", "jumpCooldownTicks", "presentationSnapshot", "canShoot", "releaseNativeFire").contains(m.name) && !m.name.startsWith("lambda$traceLaser$") && !m.name.startsWith("lambda$laserAimPoint$") && !m.name.startsWith("lambda$primaryAimPoint$") && !m.name.startsWith("lambda$presentationSnapshot$"));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, vehicle.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); vehicle.methods.add(constructor);
        definitions.put(target, mapped(vehicle, names));
        ClassNode cold = read(beam); cold.methods.removeIf(m -> m.name.equals("<clinit>"));
        definitions.put(beamTarget, mapped(cold, names));
        definitions.put(damageTarget, mapped(read(damage), names));
        var loader = new ExactQueuedShotFixture.Loader(definitions);
        Class<?> kind = loader.loadClass(target.replace('/', '.'));
        var tank = (Tank) kind.getConstructor().newInstance();
        Object coldBeam = loader.loadClass(beamTarget.replace('/', '.')).getConstructor(kind).newInstance(tank);
        kind.getField("beam").set(tank, coldBeam);
        for (String key : List.of("FIRING", "NEXT_PORT", "PRESENTATION", "TV_ACTIVE", "MODE2", "DEPLOYED", "HATCH", "LASER")) kind.getField(key).set(null, new Key(key));
        tank.data.set((Key) kind.getField("FIRING").get(null), false);
        tank.data.set((Key) kind.getField("NEXT_PORT").get(null), 0);
        kind.getField("presentation").set(tank, new com.liucodex.doggiesnewvehicle.armored.BlizzardPresentation());
        tank.data.set((Key) kind.getField("PRESENTATION").get(null), new CompoundTag());
        tank.data.set((Key) kind.getField("TV_ACTIVE").get(null), false);
        tank.data.set((Key) kind.getField("MODE2").get(null), false);
        tank.data.set((Key) kind.getField("DEPLOYED").get(null), false);
        tank.data.set((Key) kind.getField("HATCH").get(null), 0);
        tank.data.set((Key) kind.getField("LASER").get(null), new CompoundTag());
        return new Fixture(tank, coldBeam);
    }
    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var in = BlizzardGameplayExecutionTest.class.getResourceAsStream("/" + name + ".class")) {
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    private static byte[] mapped(ClassNode node, Map<String, String> names) {
        node.access = Opcodes.ACC_PUBLIC;
        // Metadata is not executed; mapped Holder/Reference share one explicit registry port.
        node.innerClasses.clear();
        node.fields.forEach(f -> f.access = (f.access & ~(Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
        node.methods.forEach(m -> m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        return writer.toByteArray();
    }

    public record Key(String name) {
        public static Key create(Key type, Location location) { return new Key(location.name); }
    }
    public record Location(String name) { public static Location fromNamespaceAndPath(String namespace, String path) { return new Location(namespace+":"+path); } }
    public static class Keys { public static Key DAMAGE_TYPE = new Key("damage"); }
    public static class Data {
        public Map<Key, Object> values = new HashMap<>();
        public Object get(Key key) { return values.get(key); }
        public void set(Key key, Object value) { values.put(key, value); }
    }
    public static class Holder { }
    public interface Registry {
        default Registry registryOrThrow(Key key) { return this; }
        default Holder getHolderOrThrow(Key key) { return new Holder(); }
    }
    public static class Cold { public Cold(Holder holder, Entity direct, Entity owner) { } }
    public static class Entity {
        public int invulnerableTime;
        public boolean alive = true, spectator, pickable=true;
        public boolean isAlive() { return alive; } public boolean isSpectator() { return spectator; }
        public Entity getRootVehicle() { return this; } public boolean isPickable() { return pickable; }
        public boolean hurt(Cold source, float damage) { return true; }
    }
    public static class Actor extends Entity { public float getMaxHealth() { return 4_000; } }
    public static class ColdBolt extends Entity { }
    public static class Target extends Actor {
        public int hurts; public float damage;
        @Override public boolean hurt(Cold source, float amount) {
            if(invulnerableTime>10)return false;
            hurts++; damage += amount;invulnerableTime=20;return true;
        }
    }
    public static class ArmoredTarget extends Target implements DnvColdArmor {
        private final Tier tier;
        ArmoredTarget(Tier tier) { this.tier = tier; }
        @Override public Tier coldArmorTier() { return tier; }
    }
    public static class ForcedDamage { public static boolean forceHurt(Entity target,Cold source,float amount){return target.hurt(source,amount);} }
    public static class World {
        public int fireSounds,traceQueries; public Vec3 soundOrigin; public float soundVolume;
        public final List<Entity> extra=new ArrayList<>();
        public void playLocalSound(double x, double y, double z, Sound sound, net.minecraft.sounds.SoundSource source,
                float volume, float pitch, boolean delay) {
            fireSounds++; soundOrigin = new Vec3(x, y, z); soundVolume=volume;
            assertEquals(sound==Sounds.BLIZZARD_LASER.value?2F:.8F, volume); assertEquals(1F, pitch); assertFalse(delay);
        }
        public boolean isClientSide, blocked, clear = true; public Target target = new Target();
        public double loadedLimit=Double.POSITIVE_INFINITY;
        public long time; public long getGameTime() { return time; }
        public Hit clip(Clip clip) { return new Hit(clear&&!blocked); } public Registry registryAccess() { return new Registry() { }; }
    }
    public static class Sound { }
    public static class SoundRef { public final Sound value=new Sound();public Object get() { return value; } }
    public static class Sounds { public static final SoundRef BLIZZARD_FIRE = new SoundRef(), BLIZZARD_LASER = new SoundRef(); }
    public static class Clip { public Clip(Vec3 from, Vec3 to, net.minecraft.world.level.ClipContext.Block b,
            net.minecraft.world.level.ClipContext.Fluid f, Entity entity) { } }
    public static class Hit {
        public boolean clear; public Hit(boolean clear) { this.clear = clear; }
        public net.minecraft.world.phys.HitResult.Type getType() {
            return clear ? net.minecraft.world.phys.HitResult.Type.MISS : net.minecraft.world.phys.HitResult.Type.BLOCK;
        }
        public Vec3 getLocation() { return new Vec3(0, 1, 12); }
    }
    public static class Loaded { public static boolean loaded(Entity tank, Vec3 from, Vec3 to) { return ((Tank) tank).world.clear&&to.z<=((Tank)tank).world.loadedLimit; } }
    public static class Trace {
        public record Hit(Entity entity, Vec3 position) { }
        public static List<Hit> piercing(World world, Vec3 from, Vec3 to, java.util.function.Predicate<Entity> accept) {
            world.traceQueries++;
            var targets=new ArrayList<Entity>();targets.add(world.target);targets.addAll(world.extra);
            return targets.stream().filter(accept).map(e->new Hit(e,new Vec3(0,1,12))).toList();
        }
        public static Hit nearest(World world, Vec3 from, Vec3 to, java.util.function.Predicate<Entity> accept) {
            var targets=new ArrayList<Entity>();targets.addAll(world.extra);targets.add(world.target);
            return targets.stream().filter(accept).map(e->new Hit(e,new Vec3(0,1,12))).findFirst().orElse(null);
        }
    }
    public static class Rig {
        public static Rig INSTANCE = new Rig(); public Vec3 barrel = Vec3.ZERO;
        public static Vec3 point(org.joml.Matrix4d matrix, Vec3 position) { return position; }
        public static Vec3 direction(org.joml.Matrix4d matrix, Vec3 direction) { return com.liucodex.doggiesnewvehicle.armored.BlizzardRig.direction(matrix, direction); }
    }
    /** Fixed damage port for BlizzardProjectileExecutionTest, which only fires regular cold bolts. */
    public static class Damage {
        public static float amount(Object target, boolean mode2) {
            assertFalse(mode2);
            return 10;
        }
    }
    public static class Tank extends Entity {
        public Data entityData = new Data(), data = entityData; public World world = new World();
        public Actor pilot = new Actor(); public int energy = 2_000_000, energyWrites, selected, tickCount;
        public boolean wreck, fire, jump, forward, back, left, right, verticalCollision, horizontalCollision; public double y = 1, ground;
        public float yaw, pitch, roll, turretYaw, turretPitch, soundYaw, soundPitch, charge;
        public int turretSounds; public Vec3 velocity = Vec3.ZERO;
        public int interpolationSteps; public double x, z, xO, yO, zO; public float serverYaw, serverPitch;
        public World level() { return world; } public boolean isWreck() { return wreck; }
        public boolean isControlledByLocalInstance() { return world.isClientSide; }
        public int getEnergy() { return energy; } public void consumeEnergy(int cost) { energy -= cost; energyWrites++; }
        public float getMaxHealth() { return 600; }
        public float getChargeProgress() { return charge; } public void setChargeProgress(float value) { charge=value; }
        public void setFireInputDown(boolean value) { fire=value; }
        public Entity getFirstPassenger() { return pilot; } public int getWeaponIndex(int seat) { return selected; }
        public boolean fireInputDown() { return fire; } public boolean decoyInputDown() { return jump; }
        public boolean forwardInputDown() { return forward; } public boolean backInputDown() { return back; }
        public boolean leftInputDown() { return left; } public boolean rightInputDown() { return right; }
        public void processInput(short keys) { if (keys == 0) fire = jump = forward = back = left = right = false; }
        public double supportHeight() { return ground; } public double getY() { return y; }
        public double getX() { return x; } public double getZ() { return z; }
        public Vec3 position() { return new Vec3(x,y,z); }
        public float getXRot() { return pitch; }
        public float getRoll() { return roll; }
        public void setTurretYRot(float value) { turretYaw = value; }
        public void setTurretXRot(float value) { turretPitch = value; }
        public float getTurretYRot() { return turretYaw; }
        public float getTurretXRot() { return turretPitch; }
        public void turretTurnSound(float pitch, float yaw, float volume) { turretSounds++; soundPitch=pitch; soundYaw=yaw; }
        public float getTurretMinPitch() { return -7.7F; }
        public float getTurretMaxPitch() { return 50.8F; }
        public void handleClientSync() { }
        public void setPos(Vec3 v) { setPos(v.x, v.y, v.z); }
        public org.joml.Matrix4d getVehicleTransform(float partial) { return new org.joml.Matrix4d().rotateY(Math.toRadians(-yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll)); } public float getServerYaw() { return serverYaw; } public float getServerPitch() { return serverPitch; }
        public void setServerYaw(float value) { serverYaw = value; } public void setServerPitch(float value) { serverPitch = value; }
        public void syncPacketPositionCodec(double x, double y, double z) { }
        public void setPos(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public float getYRot() { return yaw; } public void setYRot(float value) { yaw = value; }
        public void setXRot(float value) { pitch = value; } public void setZRot(float value) { roll = value; } public void setPower(float value) { }
        public Vec3 getDeltaMovement() { return velocity; } public void setDeltaMovement(Vec3 value) { velocity = value; }
        public Vec3 getShootPos(String weapon, float partial) { return new Vec3(0, 1, 0); }
        public Vec3 getShootVec(String weapon, float partial) {
            return com.liucodex.doggiesnewvehicle.armored.BlizzardRig.direction(getVehicleTransform(partial)
                    .rotateY(Math.toRadians(turretYaw)).rotateX(Math.toRadians(turretPitch)), new Vec3(0, 0, 1));
        }
        public org.joml.Matrix4d groupTransform(String group, float partial) { return new org.joml.Matrix4d(); }
        public float getTurretTurnXSpeed() { return 2; } public float getTurretTurnYSpeed() { return 4; }
        public boolean firing() { return (boolean) data.values.entrySet().stream().filter(e -> e.getKey().name().equals("FIRING")).findFirst().orElseThrow().getValue(); }
        public int bolts; public boolean spawnAllowed = true;
        public boolean emitColdBolt() { if (!spawnAllowed) return false; bolts++; return true; }
        public void m_7380_(CompoundTag tag) { tag.putFloat("ChargeProgress",charge); }
        public void m_7378_(CompoundTag tag) { charge=tag.getFloat("ChargeProgress"); }
    }
}

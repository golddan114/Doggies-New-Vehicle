package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import static com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MothershipEntityExecutionTest {
    @Test void scentIsAssignedOnlyToLaserStationsAndCannotStartPursuitOrDoomsday() throws Exception {
        Base ship=create();Actor scent=enemy(ship);scent.scent=true;scent.position=new Vec3(0,64,60);
        assertNull(resolve(ship,0));
        execute(ship,"tickStations",null,0);
        boolean laserAssigned=false;
        for(int i=0;i<ship.stationTargets.length;i++) {
            if(MothershipArmament.Station.values()[i].kind==MothershipArmament.Kind.TWIN_LASER)
                laserAssigned|=scent.uuid.equals(ship.stationTargets[i]);
            else assertNull(ship.stationTargets[i]);
        }
        assertTrue(laserAssigned);assertNull(ship.combatTarget);
        scent.valid=false;execute(ship,"tickStations",null,1);
        assertTrue(java.util.Arrays.stream(ship.stationTargets).allMatch(java.util.Objects::isNull));
    }
    @Test void percentFollowsMothershipArmorBeforeTheSingleNativeHealthDebit() throws Exception {
        Base ship=create(); Actor attacker=enemy(ship);
        var source=new Damage(attacker,new Actor(),"projectile_hit");source.percent=.06F;
        var hurt=ship.getClass().getMethod("hurt",Damage.class,float.class);
        hurt.invoke(ship,source,100F);
        assertEquals(7884,ship.health,.001);assertEquals(1,ship.nativeHurtCalls);
        hurt.invoke(ship,source,100F);
        assertEquals(7864,ship.health,.001);
    }
    @Test void hiddenTargetsCannotStartPursuitTurnStationsOrConsumeDropBays() throws Exception {
        Base ship=create();Actor hidden=enemy(ship);hidden.visible=false;
        ship.arrivalTicks=MothershipDrops.SECOND_CHARGE_TICKS;
        var before=ship.poses.clone();
        assertNull(resolve(ship,0));execute(ship,"tickStations",null,0);execute(ship,"tickDrops",null,0);
        assertNull(ship.combatTarget);assertTrue(java.util.Arrays.stream(ship.stationTargets).allMatch(java.util.Objects::isNull));
        assertArrayEquals(before,ship.poses);assertEquals(0,ship.world.secondaryShots);
        assertEquals(0,ship.prepared);assertEquals(2,ship.drops.remaining());
        hidden.visible=true;assertSame(hidden,resolve(ship,1));
        hidden.visible=false;hidden.position=hidden.position.add(30,0,0);
        assertNull(resolve(ship,2));assertNull(ship.combatTarget);
    }
    @Test void deferredStationObservationRetainsIdentityWithoutUpdatingPoseOrFiring() throws Exception {
        Base ship=create();Actor target=enemy(ship);target.position=new Vec3(0,64,60);
        execute(ship,"tickStations",null,0);
        assertTrue(java.util.Arrays.stream(ship.stationTargets).anyMatch(java.util.Objects::nonNull));
        var ids=ship.stationTargets.clone();var poses=ship.poses.clone();int shots=ship.world.secondaryShots;
        ship.world.sightDeferred=true;execute(ship,"tickStations",null,1);
        assertArrayEquals(ids,ship.stationTargets);assertArrayEquals(poses,ship.poses);
        assertEquals(shots,ship.world.secondaryShots);
    }
    @Test void deferredSightSuspendsTrackingAndVisibleAlternativeStillWinsOverHiddenNearest() throws Exception {
        Base ship=create();Actor hidden=enemy(ship),visible=enemy(ship);
        hidden.visible=false;visible.position=visible.position.add(0,0,20);
        assertSame(visible,resolve(ship,0));
        ship.world.sightDeferred=true;assertNull(resolve(ship,1));assertEquals(visible.uuid,ship.combatTarget);
        ship.world.sightDeferred=false;assertSame(visible,resolve(ship,2));
        clear(ship);assertTrue(ship.pursuitSight.clears>0);assertTrue(ship.dropSight.clears>0);
        assertTrue(java.util.Arrays.stream(ship.stationSights).allMatch(sight->sight.clears>0));
    }
    @Test void nativeVehicleMarkerWaitsForCompletedEntryIncludingClientSynchronizedAge() throws Exception {
        Base ship=create();var shown=ship.getClass().getMethod("dnvShowVehicleMarker");
        for(int age:new int[]{0,1,119,120,159}){ship.entryAge=age;assertEquals(false,shown.invoke(ship));}
        ship.entryAge=160;assertEquals(true,shown.invoke(ship));
        ship.world.isClientSide=true;
        for(int age:new int[]{0,1,119,120,159}){ship.entityData.set(Base.ENTRY_AGE,age);assertEquals(false,shown.invoke(ship));}
        ship.entryAge=0;ship.entityData.set(Base.ENTRY_AGE,160);assertEquals(true,shown.invoke(ship));
    }
    @Test void candidateDiscoveryIncludesRearMuzzleCoverageWithoutExtendingMainPursuit() throws Exception {
        Base ship=create();int i=5;
        java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[i]=0;
        var pose=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(145,-10);
        ship.poses[i]=pose;
        Actor target=enemy(ship);
        target.position=ship.world(ship.rig.muzzle(i,0,pose).add(ship.rig.direction(pose).scale(155))).add(-.5,-1,-.5);
        assertFalse((boolean)ship.getClass().getMethod("legalTarget",Actor.class,double.class)
                .invoke(ship,target,MothershipTactics.TARGET_RANGE));
        ship.candidates.scanned.add(target);
        ship.getClass().getMethod("refreshCandidates").invoke(ship);
        assertEquals(List.of(target),ship.candidates.targets);
        execute(ship,"tickStations",null,0);
        assertEquals(target.uuid,ship.stationTargets[i]);
        assertNull(resolve(ship,1),"main pursuit keeps the original hull-centred range");
        target.team=ship.team;
        ship.getClass().getMethod("refreshCandidates").invoke(ship);
        assertTrue(ship.candidates.targets.isEmpty());
    }

    @Test void muzzleRangeGraceNeverFiresOutsideAndPersistentEscapeSelectsNearbyEnemy() throws Exception {
        Base ship=create();int i=0;
        java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[i]=0;
        var pose=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(ship.rig.stations.get(i).centerYaw(),-10);
        ship.poses[i]=pose;
        Vec3 muzzle=ship.world(ship.rig.muzzle(i,0,pose));
        Vec3 forward=ship.world(ship.rig.muzzle(i,0,pose).add(ship.rig.direction(pose))).subtract(muzzle);
        Vec3 horizontal=new Vec3(forward.x,0,forward.z).normalize();
        Actor escaping=enemy(ship);
        escaping.position=muzzle.add(horizontal.scale(94)).add(-.5,-11,-.5);
        execute(ship,"tickStations",null,0);
        assertEquals(escaping.uuid,ship.stationTargets[i]);
        int shots=ship.world.secondaryShots;
        escaping.position=muzzle.add(horizontal.scale(100)).add(-.5,-11,-.5);
        execute(ship,"tickStations",null,1);
        assertEquals(escaping.uuid,ship.stationTargets[i]);assertEquals(shots,ship.world.secondaryShots);
        escaping.position=muzzle.add(horizontal.scale(94)).add(-.5,-11,-.5);
        execute(ship,"tickStations",null,2);
        assertEquals(escaping.uuid,ship.stationTargets[i]);assertEquals(0,ship.stationOutOfRangeSince[i]);
        shots=ship.world.secondaryShots;
        escaping.position=muzzle.add(horizontal.scale(100)).add(-.5,-11,-.5);
        execute(ship,"tickStations",null,3);
        execute(ship,"tickStations",null,42);
        assertEquals(escaping.uuid,ship.stationTargets[i]);assertEquals(shots,ship.world.secondaryShots);
        Actor nearby=enemy(ship);nearby.position=muzzle.add(horizontal.scale(55)).add(-.5,-11,-.5);
        execute(ship,"tickStations",null,43);
        assertEquals(nearby.uuid,ship.stationTargets[i]);
        assertNull(ship.combatTarget,"station target never writes the main pursuit target");
    }

    @Test void discoveryCandidateReaches256WithoutExtendingPursuitOrTakingSlotsFromLocalEnemies() throws Exception {
        for (double x : new double[] {159, 160, 161, 255, 256, 257}) {
            Base ship = create();
            var refresh = ship.getClass().getMethod("refreshCandidates");
            Actor target = enemy(ship);
            target.position = ship.position().add(x, 0, 0);
            ship.candidates.scanned.add(target);
            refresh.invoke(ship);
            assertEquals(x <= 256, ship.candidates.targets.contains(target), "distance " + x);
            assertEquals(x <= 160 ? target : null, resolve(ship, (long) x), "pursuit " + x);
        }
        Base ship = create();
        var refresh = ship.getClass().getMethod("refreshCandidates");
        for (int i = 0; i < 40; i++) {
            Actor remote = enemy(ship);
            remote.position = ship.position().add(230, 0, i * .2);
            ship.candidates.scanned.add(remote);
        }
        Actor local = enemy(ship);
        local.position = ship.position().add(140, 60, 0);
        ship.candidates.scanned.add(local);
        Actor tooHigh = enemy(ship);
        tooHigh.position = ship.position().add(240, 65, 0);
        ship.candidates.scanned.add(tooHigh);
        refresh.invoke(ship);
        assertEquals(32, ship.candidates.targets.size());
        assertSame(local, ship.candidates.targets.get(0));
        assertFalse(ship.candidates.targets.contains(tooHigh));
    }

    @Test void normalHundredTickReloadIsNotAnAimingFailure() throws Exception {
        Base ship=create();int i=2;
        java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[i]=0;
        var pose=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(ship.rig.stations.get(i).centerYaw(),-10);
        Actor target=enemy(ship);
        target.position=ship.world(ship.rig.muzzle(i,0,pose).add(ship.rig.direction(pose).scale(55))).add(-.5,-1,-.5);
        ship.stationTargets[i]=target.uuid;
        ship.armament.tryFire(MothershipArmament.Station.values()[i],0,(station,barrel)->true);
        for(int tick=1;tick<100;tick++) {
            execute(ship,"tickStations",null,tick);
            assertEquals(target.uuid,ship.stationTargets[i]);assertEquals(0,ship.stationStalledTicks[i]);
        }
        assertEquals(0,ship.world.secondaryShots);
    }

    @Test void continuouslyUnreachableAlignmentReleasesOldTargetWithoutWaitingForShotFailures() throws Exception {
        Base ship=create();int i=0;
        java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[i]=0;
        var desired=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(ship.rig.stations.get(i).centerYaw(),-10);
        Actor target=enemy(ship);
        target.position=ship.world(ship.rig.muzzle(i,0,desired).add(ship.rig.direction(desired).scale(55))).add(-.5,-1,-.5);
        ship.stationTargets[i]=target.uuid;
        for(int tick=0;tick<60;tick++) {
            ship.poses[i]=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(desired.yaw()+45,20);
            execute(ship,"tickStations",null,tick);
        }
        assertEquals(60,ship.stationStalledTicks[i]);assertEquals(0,ship.blockedAttempts[i]);
        Actor replacement=enemy(ship);replacement.position=target.position.add(0,0,2);
        execute(ship,"tickStations",null,60);
        assertEquals(replacement.uuid,ship.stationTargets[i]);
    }

    @Test void emptySummonNbtStartsBeaconAndKeepsFirstShotReadyWithoutSpendingCooldown() throws Exception {
        Base ship=create();restore(ship,new net.minecraft.nbt.CompoundTag(),500);
        assertEquals(0,ship.entryAge);assertFalse(enter(ship,501));assertEquals(1,ship.entryAge);
        assertTrue(ship.doomsday.ready(501));
        arrival(ship,660);assertTrue(ship.doomsday.ready(660));
        assertEquals(0,ship.doomsday.cooldownToSave(660));
    }
    @Test void compiledHurtLimitsEightCompleteAnnihilatorVolleysIncludingBlasts() throws Exception {
        Base ship=create();Annihilator vehicle=new Annihilator();Actor pilot=new Actor();pilot.vehicle=vehicle;
        var hurt=ship.getClass().getMethod("hurt",Damage.class,float.class);
        for(int volley=0;volley<8;volley++)for(int pulse=0;pulse<10;pulse++) {
            ship.world.now=volley*20L+pulse;
            for(int ray=0;ray<3;ray++)hurt.invoke(ship,new Damage(pilot,vehicle,true,false),1000F);
            for(int blast=0;blast<6;blast++)hurt.invoke(ship,new Damage(pilot,vehicle,false,true),72F);
        }
        assertEquals(1600,ship.health);
        assertFalse(ship.wreck);
        assertEquals(MothershipCrash.ALIVE,ship.crashSequence.state());
    }
    @Test void twoAnnihilatorsHaveIndependentBudgetsAndTntIsNeverChargedToEither() throws Exception {
        Base ship=create();Annihilator first=new Annihilator(),second=new Annihilator();
        Actor pilot=new Actor(),tnt=new Actor();pilot.vehicle=first;
        var hurt=ship.getClass().getMethod("hurt",Damage.class,float.class);ship.world.now=10;
        for(int i=0;i<3;i++)hurt.invoke(ship,new Damage(pilot,first,true,false),1000F);
        hurt.invoke(ship,new Damage(pilot,first,false,true),72F);
        hurt.invoke(ship,new Damage(tnt,tnt,false,true),72F);
        for(int i=0;i<3;i++)hurt.invoke(ship,new Damage(null,second,true,false),1000F);
        hurt.invoke(ship,new Damage(null,second,false,true),72F);
        assertEquals(8000F-74.4F-14.4F-74.4F,ship.health,.001F);
        assertEquals(74.4F,ship.annihilatorDamageBySource.get(first.uuid),.001F);
        assertEquals(74.4F,ship.annihilatorDamageBySource.get(second.uuid),.001F);
        ship.world.now=11;hurt.invoke(ship,new Damage(null,first,false,true),72F);
        hurt.invoke(ship,new Damage(null,first,false,true),72F);
        assertEquals(8000F-74.4F-14.4F-74.4F-28.8F,ship.health,.001F);
        assertEquals(1,ship.annihilatorDamageBySource.size());
        ship.world.now=10;hurt.invoke(ship,new Damage(null,first,true,false),1000F);
        assertEquals(8000F-74.4F-14.4F-74.4F-28.8F-20F,ship.health,.001F,
                "Clock rollback opens a fresh pulse ledger");
        clear(ship);assertTrue(ship.annihilatorDamageBySource.isEmpty());
        assertEquals(Long.MIN_VALUE,ship.annihilatorBudgetTick);
    }
    @Test void unpilotedExplosionWithoutLaserIsCappedAndOtherExplosionIsNot() throws Exception {
        Base ship=create();Annihilator vehicle=new Annihilator();Actor tnt=new Actor();
        var hurt=ship.getClass().getMethod("hurt",Damage.class,float.class);ship.world.now=20;
        hurt.invoke(ship,new Damage(null,vehicle,false,true),72F);
        hurt.invoke(ship,new Damage(null,vehicle,false,true),72F);
        hurt.invoke(ship,new Damage(tnt,tnt,false,true),72F);
        assertEquals(8000F-14.4F*3,ship.health,.001F);
    }
    @Test void bothNativeAndDnvPiercingLaserKeepTheMothershipIntrinsicResistance() throws Exception {
        Base ship=create();Actor other=new Actor();var hurt=ship.getClass().getMethod("hurt",Damage.class,float.class);
        ship.world.now=30;hurt.invoke(ship,new Damage(other,other,true,false),1000F);
        hurt.invoke(ship,new Damage(other,other,false,false,true),1000F);
        assertEquals(7960,ship.health);
        assertTrue(ship.annihilatorDamageBySource.isEmpty(),"Other laser owners do not consume Annihilator budgets");
    }

    @Test void entryPausesWhenUnloadedResumesAtExactWarpAndRevealAgesAndSurvivesSave() throws Exception {
        Base ship=create();ship.entryAge=0;ship.beaconAnchor=null;ship.world.loaded=false;
        assertFalse(enter(ship,0));assertEquals(0,ship.entryAge);assertTrue(ship.world.sounds.isEmpty());
        ship.world.loaded=true;
        for(int tick=1;tick<=119;tick++)assertFalse(enter(ship,tick));
        assertEquals(new Vec3(0,64,0),ship.beaconAnchor);
        assertEquals(List.of("beacon@1"),ship.world.sounds);
        assertFalse(enter(ship,120));assertEquals(120,ship.entryAge);
        assertEquals(List.of("beacon@1","warp@120"),ship.world.sounds);
        for(int tick=121;tick<=137;tick++)assertFalse(enter(ship,tick));
        Base restored=create();restored.entryAge=0;
        restore(restored,save(ship,137),1000);
        assertEquals(137,restored.entryAge);assertEquals(ship.beaconAnchor,restored.beaconAnchor);
        restored.world.loaded=false;assertFalse(enter(restored,1001));assertEquals(137,restored.entryAge);
        restored.world.loaded=true;
        for(int tick=138;tick<160;tick++)assertFalse(enter(restored,1000+tick));
        assertTrue(enter(restored,1160));assertEquals(160,restored.entryAge);
        assertTrue(enter(restored,1161));assertEquals(160,restored.entryAge);
        assertTrue(restored.world.sounds.isEmpty(),"Reloading reveal must not replay entry audio");
    }

    @Test void legacyMothershipSaveOpensCombatWithoutReplayingEntry() throws Exception {
        Base ship=create();var saved=save(ship,0);
        saved.getCompound("DnvMothership").putInt("Schema",4);
        saved.getCompound("DnvMothership").remove("EntryAge");
        Base restored=create();restored.entryAge=0;restore(restored,saved,300);
        assertEquals(160,restored.entryAge);assertTrue(enter(restored,301));
        assertTrue(restored.world.sounds.isEmpty());
    }
    @Test void fourFootfallExplosionsStayAtTheLockedTargetWhileBeamEndpointStaysAtTheSurface()throws Exception {
        Base ship=create();Actor target=enemy(ship);Vec3 impact=new Vec3(3,64,150);ship.world.blockHit=impact;
        for(int tick=0;tick<360;tick++)execute(ship,"tickDoomsday",target,tick);
        assertEquals(List.of(target.position,target.position,target.position),ship.world.impactEvents);
        for(int i=0;i<3;i++)assertEquals(List.of(target.position),ship.terrainBlasts[i].centers);
        assertTrue(ship.terrainBlasts[3].centers.isEmpty());
        execute(ship,"tickDoomsday",target,360);
        assertEquals(List.of(target.position,target.position,target.position,target.position),ship.world.blastCenters);
        assertEquals(List.of(target.position),ship.terrainBlasts[3].centers);
        ship.getClass().getMethod("syncFx").invoke(ship);
        assertEquals(target.position,ship.getClass().getMethod("renderGroundPoint").invoke(ship));
        ship.position=ship.position.add(10,3,-8);ship.yaw=70;
        assertEquals(impact,ship.getClass().getMethod("renderBeamEnd").invoke(ship));
        for(int tick:new int[]{360,359,361,380})execute(ship,"tickDoomsday",target,tick);
        assertEquals(impact,ship.getClass().getMethod("renderBeamEnd").invoke(ship));
        assertEquals(4,ship.world.impactEvents.size());assertEquals(4,ship.blasts);
    }
    @Test void rayWithoutASurfaceHitStillCreatesFourTargetFootfallBlasts()throws Exception {
        Base ship=create();Actor target=enemy(ship);ship.world.surfaceImpact=false;
        for(int tick=0;tick<=361;tick++)execute(ship,"tickDoomsday",target,tick);
        assertEquals(2500,ship.world.directDamage);assertEquals(1250,ship.world.areaDamage);
        assertEquals(4,ship.world.impactEvents.size());assertEquals(4,ship.blasts);
    }
    @Test void unloadedFootfallCannotStartAnAreaOrTerrainEffect()throws Exception {
        Base ship=create();Actor target=enemy(ship);
        for(int tick=0;tick<260;tick++)execute(ship,"tickDoomsday",target,tick);
        ship.world.loaded=false;
        execute(ship,"tickDoomsday",target,260);
        assertEquals(250,ship.world.directDamage);
        assertEquals(0,ship.world.areaDamage);
        assertTrue(ship.world.blastCenters.isEmpty());
        for(var terrain:ship.terrainBlasts)assertTrue(terrain.centers.isEmpty());
    }
    @Test void actualAcquisitionReaches160ButRejectsHeightAlliesProtectedAndChildren()throws Exception {
        Base ship=create();Actor target=enemy(ship);target.position=ship.position.add(0,0,160);
        assertSame(target,resolve(ship,0));target.position=ship.position.add(0,0,160.01);assertNull(resolve(ship,1));
        target.position=ship.position.add(0,64,100);assertSame(target,resolve(ship,2));
        target.position=ship.position.add(0,64.01,100);assertNull(resolve(ship,3));
        target.position=ship.position.add(0,0,100);target.protectedPlayer=true;assertNull(resolve(ship,4));
        target.protectedPlayer=false;target.persistent.putUUID("DnvMothershipParent",ship.uuid);assertNull(resolve(ship,5));
        target.persistent.remove("DnvMothershipParent");target.team=ship.team;assertNull(resolve(ship,6));
        target.team=null;ship.arrivalTicks=1800;execute(ship,"tickDrops",null,40);assertEquals(1,ship.world.spawned.size());
    }
    @Test void eachAcceptedStationBurstSoundsOnceAndBlockedOrCoolingMountsStaySilent()throws Exception {
        for(var station:MothershipArmament.Station.values()) {
            Base ship=create();Actor target=enemy(ship);int i=station.ordinal();
            java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[i]=0;
            var info=ship.rig.stations.get(i);var pose=new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles(info.centerYaw(),-30);
            var center=ship.rig.pitchPoint(i,new com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V(0,0,0),pose)
                    .add(ship.rig.direction(pose).scale(55));
            target.position=ship.world(center).subtract(new Vec3(.5,1,.5));ship.poses[i]=ship.rig.aim(i,ship.model(target.getBoundingBox().getCenter()));
            ship.world.fireAccepted=false;execute(ship,"tickStations",null,0);assertEquals(0,ship.world.stationSounds);
            int aimBeforeFire=ship.world.aimCalls;
            ship.world.fireAccepted=true;execute(ship,"tickStations",null,10);
            assertEquals(aimBeforeFire+1,ship.world.aimCalls,"A firing station solves aim once per update");
            assertEquals(station.kind.barrels,ship.world.secondaryShots,station.name());assertEquals(1,ship.world.stationSounds,station.name());
            execute(ship,"tickStations",null,11);assertEquals(1,ship.world.stationSounds);
        }
    }
    @Test void compiledSixStationTickSharesOneFrameAndSplitsOverlappingFrontTargets() throws Exception {
        Base ship=create();Actor near=enemy(ship),far=enemy(ship);
        near.position=new Vec3(0,64,45);far.position=new Vec3(1,64,48);
        java.util.Arrays.fill(ship.nextAttempt,10000);
        ship.nextAttempt[0]=ship.nextAttempt[1]=0;
        for(int station:new int[]{0,1})for(Actor target:List.of(near,far))
            assertTrue(ship.rig.permits(station,ship.rig.aim(station,ship.model(target.getBoundingBox().getCenter()))));
        execute(ship,"tickStations",null,0);
        assertEquals(1,ship.candidates.frameReads);
        assertNotNull(ship.stationTargets[0]);assertNotNull(ship.stationTargets[1]);
        assertNotEquals(ship.stationTargets[0],ship.stationTargets[1]);
        assertTrue(List.of(near.uuid,far.uuid).contains(ship.stationTargets[0]));
        assertTrue(List.of(near.uuid,far.uuid).contains(ship.stationTargets[1]));
    }
    @Test void compiledStationTickCanRedistributeAfterHoldWithoutBreakingOneTargetFocus() throws Exception {
        Base ship=create();Actor near=enemy(ship);near.position=new Vec3(0,64,45);
        java.util.Arrays.fill(ship.nextAttempt,10000);
        ship.nextAttempt[0]=ship.nextAttempt[1]=0;
        execute(ship,"tickStations",null,0);
        assertEquals(near.uuid,ship.stationTargets[0]);assertEquals(near.uuid,ship.stationTargets[1]);
        Actor far=enemy(ship);far.position=new Vec3(1,64,48);
        execute(ship,"tickStations",null,39);
        assertEquals(near.uuid,ship.stationTargets[0]);assertEquals(near.uuid,ship.stationTargets[1]);
        assertEquals(1,ship.candidates.frameReads,"stable locks do not resolve candidates each tick");
        execute(ship,"tickStations",null,40);
        assertNotEquals(ship.stationTargets[0],ship.stationTargets[1]);
        assertEquals(2,ship.candidates.frameReads);
        execute(ship,"tickStations",null,41);
        assertEquals(2,ship.candidates.frameReads);
    }
    @Test void compiledStationTickDropsAFormerEnemyAndReacquiresWithoutWaitingForBalance() throws Exception {
        Base ship=create();Actor former=enemy(ship),replacement=enemy(ship);
        former.position=new Vec3(0,64,45);replacement.position=new Vec3(1,64,48);
        java.util.Arrays.fill(ship.nextAttempt,10000);ship.nextAttempt[0]=0;
        execute(ship,"tickStations",null,0);
        assertEquals(former.uuid,ship.stationTargets[0]);
        former.team=ship.team;
        execute(ship,"tickStations",null,1);
        assertEquals(replacement.uuid,ship.stationTargets[0]);
        assertEquals(2,ship.candidates.frameReads);
    }
    @Test void arrivalKeepsFirstDoomShotReadyAndSavedBayChargingContinuesWithoutOfflineCatchup() throws Exception {
        Base ship=create();ship.home=null;
        arrival(ship,700);assertEquals(0,ship.doomsday.cooldownToSave(700));assertTrue(ship.doomsday.ready(700));assertEquals(1,ship.arrivalTicks);
        for(int i=1;i<600;i++)arrival(ship,700+i);
        var saved=save(ship,1299);Base restored=create();restore(restored,saved,50_000);
        assertEquals(600,restored.arrivalTicks);assertTrue(restored.arrivalStarted);
        arrival(restored,50_001);assertEquals(601,restored.arrivalTicks);
        assertEquals(0,restored.doomsday.cooldownToSave(50_001));
    }
    @Test void bossBarAppearsOnEntryTracksDamageAndRemovesOutOfRangeDisconnectedAndUnloadedViewers() throws Exception {
        Base ship=create();ship.home=null;ship.tickCount=1;
        Actor viewer=enemy(ship);viewer.position=ship.position.add(50,0,0);
        Actor spectator=enemy(ship);spectator.spectator=true;
        ship.world.server.players.players.addAll(List.of(viewer,spectator));
        bar(ship);assertTrue(ship.bossEvent.visible);assertEquals(java.util.Set.of(viewer),ship.bossEvent.players);
        assertEquals(8000F/8000F,ship.bossEvent.progress);assertEquals("SES Executor of Truth",ship.bossEvent.name.getString());
        ship.health=2000;ship.tickCount=2;bar(ship);assertEquals(2000F/8000F,ship.bossEvent.progress);
        viewer.position=ship.position.add(193,0,0);ship.tickCount=20;bar(ship);assertTrue(ship.bossEvent.players.isEmpty());
        viewer.position=ship.position;bar(ship);assertEquals(1,ship.bossEvent.players.size());
        ship.world.server.players.players.clear();bar(ship);assertTrue(ship.bossEvent.players.isEmpty());
        ship.world.server.players.players.add(viewer);bar(ship);clear(ship);
        assertFalse(ship.bossEvent.visible);assertTrue(ship.bossEvent.players.isEmpty());
    }
    @Test void baysChargeImmediatelyAndBecomeReadyIndependentlyAtSixtyAndNinetySeconds() throws Exception {
        Base ship=create();enemy(ship);
        assertEquals(.5,ship.drops.charge(0,600));assertEquals(1.0/3,ship.drops.charge(1,600),1e-8);
        ship.arrivalTicks=1199;execute(ship,"tickDrops",null,1199);assertTrue(ship.world.spawned.isEmpty());
        ship.arrivalTicks=1200;ship.nextDrop=0;execute(ship,"tickDrops",null,1200);
        assertEquals(1,ship.world.spawned.size());assertEquals(1,ship.drops.remaining());
        ship.arrivalTicks=1799;execute(ship,"tickDrops",null,1799);assertEquals(1,ship.world.spawned.size());
        ship.arrivalTicks=1800;ship.nextDrop=0;execute(ship,"tickDrops",null,1800);assertEquals(2,ship.world.spawned.size());
        assertEquals(0,ship.drops.charge(0,1800));assertEquals(0,ship.drops.charge(1,1800));
    }

    @Test void fourDoomsdayPulsesDamageEveryIntersectedEnemyWithoutShorteningToTheFirstTarget() throws Exception {
        Base ship=create();Actor first=enemy(ship),second=enemy(ship);second.position=first.position.add(0,0,25);
        for(int tick=0;tick<=361;tick++)execute(ship,"tickDoomsday",first,tick);
        assertEquals(5000,ship.world.directDamage);
        assertEquals(512,ship.fixedBeamEnd.distanceTo(ship.world(ship.rig.doomsday)),1e-7);
    }
    private static Actor enemy(Base ship) {
        Actor target=new Actor();target.world=ship.world;ship.world.actors.put(target.uuid,target);ship.candidates.targets.add(target);return target;
    }
    @Test void actualEntityOrchestrationEmitsFourGrowingHitsFourAudioCuesAndFourBlasts() throws Exception {
        Base ship=create();Actor target=enemy(ship);
        for(int tick=0;tick<=361;tick++){ship.tickCount=tick;execute(ship,"tickDoomsday",target,tick);}
        assertEquals(2500,ship.world.directDamage);assertEquals(1250,ship.world.areaDamage);assertEquals(4,ship.blasts);
        assertEquals(List.of(target.position,target.position,target.position,target.position),ship.world.blastCenters);
        assertEquals(4,ship.world.impactEvents.size());
        assertEquals(List.of("lock@0","charge@100","fire@260","cool@360"),ship.world.sounds);
        assertEquals(List.of(1.6F,3F,3F,3F),ship.world.soundVolumes);
        assertEquals(1,target.effectApplications);
        assertEquals(400,target.slow.duration());assertEquals(2,target.slow.amplifier());
        assertFalse(Glow.OWNERS.containsKey(target.uuid));
        execute(ship,"tickDoomsday",target,362);assertEquals(2500,ship.world.directDamage);
    }
    @Test void markedTargetDiesDuringLockAndDoomsdayWaitsTenSecondsBeforeReacquiring() throws Exception {
        Base ship=create();Actor first=enemy(ship),replacement=enemy(ship);
        execute(ship,"tickDoomsday",first,0);
        assertTrue(Glow.OWNERS.containsKey(first.uuid));
        assertEquals(400,first.slow.duration());assertEquals(2,first.slow.amplifier());
        first.valid=false;
        execute(ship,"tickDoomsday",replacement,40);
        assertNull(ship.doomTarget);assertFalse(Glow.OWNERS.containsKey(first.uuid));
        for(int tick=41;tick<240;tick++)execute(ship,"tickDoomsday",replacement,tick);
        assertNull(ship.doomTarget);
        execute(ship,"tickDoomsday",replacement,240);
        assertEquals(replacement.uuid,ship.doomTarget);
        assertEquals(1,replacement.effectApplications);
    }
    @Test void actualEntityCancelsBlockedChargeAndReleasesGlowWithoutLateDamage() throws Exception {
        Base ship=create();Actor target=enemy(ship);
        for(int tick=0;tick<=50;tick++)execute(ship,"tickDoomsday",target,tick);
        assertTrue(Glow.OWNERS.containsKey(target.uuid));ship.world.lineClear=false;
        execute(ship,"tickDoomsday",target,51);assertFalse(Glow.OWNERS.containsKey(target.uuid));
        execute(ship,"tickDoomsday",target,360);assertEquals(0,ship.world.directDamage);assertEquals(0,ship.blasts);
    }
    @Test void failureSnapshotCannotBeMistakenForFireAndCancellationStopsTheTail() throws Exception {
        Base ship=create();Actor target=enemy(ship);
        for(int tick=0;tick<=200;tick++)execute(ship,"tickDoomsday",target,tick);
        ship.world.lineClear=false;
        execute(ship,"tickDoomsday",target,201);
        assertEquals(MothershipDoomsday.Phase.ABORTING,ship.weapon.phase());
        assertEquals(-1,ship.entityData.fx.getLong("Start"));
        assertEquals(201,ship.entityData.fx.getLong("Abort"));
        assertEquals(201,ship.entityData.fx.getLong("AbortElapsed"));
        assertEquals(1,ship.world.stoppedWeaponSounds);
        for(int tick=202;tick<231;tick++)execute(ship,"tickDoomsday",target,tick);
        assertEquals(0,ship.world.directDamage+ship.world.areaDamage);assertEquals(0,ship.blasts);
        assertTrue(ship.world.impactEvents.isEmpty());
        Base restored=create();restore(restored,save(ship,225),1000);
        assertEquals(-1,restored.doomsday.abortStarted());assertNull(restored.doomTarget);
        cancel(ship);ship.getClass().getMethod("syncFx").invoke(ship);
        assertEquals(2,ship.world.stoppedWeaponSounds);assertEquals(-1,ship.entityData.fx.getLong("Abort"));
    }
    @Test void firingKeepsTheExactShipPoseEvenWhenTargetMovesBehindAndFurtherMovementWasRequested() throws Exception {
        Base ship=create();Actor target=enemy(ship);
        for(int tick=0;tick<=260;tick++)execute(ship,"tickDoomsday",target,tick);
        Vec3 fixed=ship.position;float yaw=ship.yaw;target.position=new Vec3(0,64,-80);
        ship.position=ship.position.add(2,2,2);ship.yaw=120;ship.course=new Vec3(1,0,0);
        execute(ship,"moveShip",target,261);assertEquals(fixed,ship.position);assertEquals(yaw,ship.yaw);assertEquals(Vec3.ZERO,ship.velocity);
        cancel(ship);assertFalse(Glow.OWNERS.containsKey(target.uuid));assertEquals(3339,ship.doomsday.cooldownToSave(261));
    }
    @Test void actualDropInsertionRefundsRejectionReservesExceptionsAndInheritsTheCurrentTeam() throws Exception {
        Base ship=create();enemy(ship);ship.world.accept=false;ship.arrivalTicks=1800;
        execute(ship,"tickDrops",null,0);assertEquals(2,ship.drops.remaining());assertTrue(ship.world.spawned.isEmpty());
        ship.team=new Team("admin-changed");ship.world.accept=true;ship.world.overhead=false;
        execute(ship,"tickDrops",null,40);assertEquals(1,ship.drops.remaining());assertEquals(1,ship.world.spawned.size());
        execute(ship,"tickDrops",null,80);assertEquals(0,ship.drops.remaining());assertEquals(2,ship.world.spawned.size());
        assertTrue(ship.world.spawned.stream().allMatch(noise->noise.team==ship.team));assertEquals(List.of(true,true,false,false),ship.around);
        execute(ship,"tickDrops",null,1000);assertEquals(2,ship.world.spawned.size());
        Base ambiguous=create();enemy(ambiguous);ambiguous.world.throwInsert=true;ambiguous.arrivalTicks=1800;
        assertThrows(IllegalStateException.class,()->execute(ambiguous,"tickDrops",null,0));assertEquals(1,ambiguous.drops.remaining());
    }
    @Test void noLegalLandingSiteOrNoHostileTargetSpendsNeitherBay() throws Exception {
        Base ship=create();Actor target=enemy(ship);ship.prepareAccepted=false;ship.arrivalTicks=1800;
        execute(ship,"tickDrops",null,0);assertEquals(2,ship.drops.remaining());
        target.team=ship.team;ship.prepareAccepted=true;
        execute(ship,"tickDrops",null,40);assertEquals(2,ship.drops.remaining());assertTrue(ship.world.spawned.isEmpty());
    }
    @Test void actualFlightAcceleratesWithinCapAndStopsAtUnloadedSpace() throws Exception {
        Base ship=create();Actor target=enemy(ship);target.position=new Vec3(0,64,128);
        double previousSpeed=0;
        for(int tick=0;tick<=120;tick++) {
            ship.tickCount=tick;Vec3 previous=ship.position;execute(ship,"moveShip",target,tick);
            double speed=ship.position.distanceTo(previous);assertTrue(speed<=.05000001);assertTrue(speed+1e-9>=previousSpeed);previousSpeed=speed;
        }
        assertEquals(.05,previousSpeed,1e-8);
        ship.world.loaded=false;Vec3 stopped=ship.position;
        execute(ship,"moveShip",target,121);assertEquals(stopped,ship.position);assertEquals(Vec3.ZERO,ship.velocity);
        ship.world.loaded=true;execute(ship,"moveShip",target,132);assertEquals(stopped,ship.position,"Restart must begin the slow ramp again");
    }
    @Test void closedRoutesHoverAndTallRoofsNeverRaiseTheEncounterDatum() throws Exception {
        Base ship=create();Actor target=enemy(ship);target.position=new Vec3(0,64,128);ship.world.obstructed=true;ship.world.ground=200;
        Vec3 stopped=ship.position;for(int tick=0;tick<120;tick++){ship.tickCount=tick;execute(ship,"moveShip",target,tick);}
        assertEquals(stopped,ship.position);assertEquals(64,ship.encounterGround);
    }
    @Test void actualNbtRoundTripPreservesSpentBaysClocksPursuitAndRestingRocketPose() throws Exception {
        Base before=create();Actor target=enemy(before);
        before.drops.deploy(0,()->true);before.armament.tryFire(MothershipArmament.Station.REAR_AFT,300,(s,b)->true);
        before.tactics.onAttacked(target.uuid,290,80,true);before.pursuitOrigin=before.position;
        before.doomsday.begin(0);before.doomsday.advance(260,new MothershipDoomsday.Pose(0,84,0,0,-30));
        before.health=7000;before.rocketTube[2]=1;
        before.arrivalStarted=true;before.arrivalTicks=600;
        Base after=create();restore(after,save(before,300),1000);
        assertEquals(7000,after.health);assertEquals(1,after.drops.remaining());assertEquals(1,after.rocketTube[2]);
        assertTrue(after.arrivalStarted);assertEquals(600,after.arrivalTicks);
        assertEquals(0,after.poses[2].elevation());assertEquals(before.home,after.home);assertEquals(before.pursuitOrigin,after.pursuitOrigin);
        assertEquals(target.uuid,after.tactics.target());assertEquals(390,after.tactics.remaining(1000));
        assertFalse(after.armament.ready(MothershipArmament.Station.REAR_AFT,1099));assertTrue(after.armament.ready(MothershipArmament.Station.REAR_AFT,1100));
        assertNull(after.doomTarget);assertNull(after.fixedBeamEnd);assertEquals(-1,after.shotStarted);
        assertFalse(after.doomsday.begin(1000));
        cancel(after);Base twice=create();restore(twice,save(after,1010),2000);assertEquals(3290,twice.doomsday.cooldownToSave(2000));assertEquals(1,twice.drops.remaining());
    }
    @Test void unknownOrIncompleteNbtCannotRefillBaysOrImmediatelyFire() throws Exception {
        Base before=create();var saved=save(before,0);saved.getCompound("DnvMothership").putInt("Schema",999);
        Base after=create();restore(after,saved,100);assertEquals(0,after.drops.remaining());assertFalse(after.doomsday.ready(100));
        saved.getCompound("DnvMothership").putInt("Schema",1);saved.getCompound("DnvMothership").remove("Drops");
        restore(after,saved,200);assertEquals(0,after.drops.remaining());
    }
    @Test void expiredPursuitReturnsToItsCapturedStartAndThenReleasesReturnMode() throws Exception {
        Base ship=create();Actor attacker=enemy(ship);ship.position=new Vec3(30,84,0);ship.pursuitOrigin=new Vec3(28,84,0);
        ship.tactics.onAttacked(attacker.uuid,0,50,true);assertNull(resolve(ship,400));assertTrue(ship.returning);
        ship.position=new Vec3(28.5,84,0);resolve(ship,401);assertFalse(ship.returning);assertNull(ship.pursuitOrigin);
        assertEquals(new Vec3(0,99,0),ship.home,"Encounter home must not be overwritten by retaliation");
    }
    @Test void blockedReturnCannotSuppressTargetAcquisitionIndefinitely() throws Exception {
        Base ship=create();Actor attacker=enemy(ship);ship.position=new Vec3(30,84,0);ship.pursuitOrigin=new Vec3(0,84,0);
        ship.tactics.onAttacked(attacker.uuid,0,50,true);
        assertNull(resolve(ship,400));assertTrue(ship.returning);
        assertNull(resolve(ship,599));assertTrue(ship.returning);
        assertSame(attacker,resolve(ship,600));assertFalse(ship.returning);assertNull(ship.pursuitOrigin);
    }
}

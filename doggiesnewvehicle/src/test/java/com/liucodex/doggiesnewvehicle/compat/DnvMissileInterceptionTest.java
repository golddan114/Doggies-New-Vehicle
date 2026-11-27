package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Executes the actual transformed native flight through impact, not just the first launch frames. */
class DnvMissileInterceptionTest {
    private static final AABB TARGET = new AABB(-.5, 0, -.5, .5, 1, .5);

    @Test void oldSixDegreeAdapterMissesARepeatedClosePassWhileTheNewAdapterImpacts() throws Exception {
        var old = new ExactApocalypseMissileFixture(false, true);
        old.target.position = new Vec3(0, 0, 12);
        assertEquals(-1, impact(old, false), "The archived negative must reproduce the missed target");
        var fixed = new ExactApocalypseMissileFixture();
        fixed.target.position = new Vec3(0, 0, 12);
        assertTrue(impact(fixed, false) > 0);
    }

    @Test void threeLaunchersHitCloseDistantHighLowAndMovingTargetsBeforeNativeExpiry() throws Exception {
        var missed = new java.util.ArrayList<String>();
        for (boolean boss : new boolean[] {false, true}) {
            for (double distance : new double[] {3, 20, 50, 150, 400}) {
                for (double height : new double[] {-20, 0, 30, 100}) {
                    for (Vec3 movement : new Vec3[] {Vec3.ZERO, new Vec3(.7, .1, .3), new Vec3(-1.2, 0, 0)}) {
                        for(boolean targetFirst:new boolean[]{false,true}) {
                            var f = new ExactApocalypseMissileFixture(boss);
                            f.target.position = new Vec3(0, height, distance);f.target.velocity = movement;
                            if (boss) { f.missile.position = new Vec3(0, 4, 0);f.missile.setXRot(-90); }
                            int tick = impact(f, boss,targetFirst);
                            if (tick <= 0 || tick >= 350) missed.add(
                                    "boss=" + boss + " distance=" + distance + " height=" + height + " movement=" + movement+" targetFirst="+targetFirst);
                        }
                    }
                }
            }
        }
        assertTrue(missed.isEmpty(), missed.toString());
    }

    @Test void countermeasureKeepsItsNativeRetargetAndStopsDnvCorrection() throws Exception {
        var f = new ExactApocalypseMissileFixture();
        for (int tick=0;tick<10;tick++) f.tick();
        int guided = f.missile.data.getInt("DnvVlsFlightTicks");
        f.missile.distracted = true;f.target.eligible = false;
        f.tick();
        assertEquals(f.target.id.toString(), f.missile.targetId);
        assertEquals(guided, f.missile.data.getInt("DnvVlsFlightTicks"));
    }
    @Test void savedCruiseResumesWithoutLaunchResetAndMalformedTurnAgeStaysBounded()throws Exception {
        var original=new ExactApocalypseMissileFixture(true);original.missile.position=new Vec3(0,16,0);
        original.arm(6);original.missile.data.putInt("DnvVlsClimb",3);original.nativeTick();
        for(int i=0;i<30;i++)original.nativeTick();
        var restored=new ExactApocalypseMissileFixture(true);
        restored.missile.position=original.missile.position;restored.missile.velocity=original.missile.velocity;
        restored.missile.setXRot(original.missile.pitch);restored.missile.setYRot(original.missile.yaw);
        restored.missile.data.putBoolean(DnvVlsFlight.ACTIVE,original.missile.data.getBoolean(DnvVlsFlight.ACTIVE));
        restored.missile.data.putBoolean(DnvVlsFlight.OWNED,original.missile.data.getBoolean(DnvVlsFlight.OWNED));
        restored.missile.data.putInt("DnvVlsFlightTicks",Integer.MAX_VALUE);
        boolean hit=false;
        for(int i=0;i<250&&!hit;i++) {
            Vec3 before=restored.missile.position.subtract(restored.target.position);restored.nativeTick();
            Vec3 after=restored.missile.position.subtract(restored.target.position);
            hit=TARGET.contains(after)||TARGET.clip(before,after).isPresent();
        }
        assertTrue(hit);assertEquals(40,restored.missile.data.getInt("DnvVlsFlightTicks"));
        assertTrue(restored.missile.data.getBoolean(DnvVlsFlight.OWNED));
    }

    private static int impact(ExactApocalypseMissileFixture f, boolean boss) throws Exception {
        return impact(f,boss,false);
    }
    private static int impact(ExactApocalypseMissileFixture f, boolean boss,boolean targetFirst) throws Exception {
        if (boss) f.arm(6);
        for (int tick=0;tick<350;tick++) {
            if(targetFirst)f.target.position=f.target.position.add(f.target.velocity);
            // FastThrowableProjectile.tick sweeps against the target's current box BEFORE projectileMove.
            // The target box must remain at this observed position throughout that ray, for either tick order.
            Vec3 observedTarget=f.target.position;
            Vec3 before = f.missile.position.subtract(observedTarget);
            if (boss) f.nativeTick();else f.tick();
            if (f.missile.explosions > 0) {
                assertEquals(1,f.missile.explosions);
                assertTrue(TARGET.inflate(2).contains(f.missile.explosionPoint.subtract(observedTarget)));
                return tick + 1;
            }
            Vec3 after = f.missile.position.subtract(observedTarget);
            assertTrue(Double.isFinite(f.missile.position.lengthSqr()));
            assertTrue(f.missile.velocity.length() <= 4.001);
            if (TARGET.contains(before) || TARGET.contains(after) || TARGET.clip(before, after).isPresent()) return tick + 1;
            if(!targetFirst)f.target.position = f.target.position.add(f.target.velocity);
        }
        return -1;
    }
}

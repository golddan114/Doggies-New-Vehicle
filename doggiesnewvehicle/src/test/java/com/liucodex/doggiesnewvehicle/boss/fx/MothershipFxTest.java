package com.liucodex.doggiesnewvehicle.boss.fx;

import com.liucodex.doggiesnewvehicle.boss.MothershipDoomsday;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class MothershipFxTest {
    @Test void exhaustAnchorsMeetTheTenEmissiveNozzleRims() {
        var rig = MothershipRig.get();
        float[] vertices = rig.parts.get("body_static/light").stream();
        assertEquals(10, rig.engines.size());
        for (var engine : rig.engines) {
            var anchor = engine.position();
            assertEquals(new MothershipRig.V(0, 0, -1), engine.direction());
            var rim = new HashSet<MothershipRig.V>();
            for (int i = 0; i < vertices.length; i += DnvMeshCodec.STRIDE) {
                var vertex = new MothershipRig.V(vertices[i], vertices[i + 1], vertices[i + 2]);
                var delta = vertex.sub(anchor);
                if (Math.abs(delta.z()) < 0.1 && Math.hypot(delta.x(), delta.y()) < 1.25 * engine.radius()) {
                    rim.add(vertex);
                }
            }
            assertEquals(8, rim.size(), "Expected the octagonal lip at " + anchor);
            var center = rim.stream().reduce(new MothershipRig.V(0, 0, 0), MothershipRig.V::add).scale(1.0 / rim.size());
            assertEquals(anchor.x(), center.x(), 1e-4);
            assertEquals(anchor.y(), center.y(), 1e-4);
            assertEquals(0.075, center.z() - anchor.z(), 1e-4);
        }
    }

    @Test void abortedChargeShrinksAndFadesWithoutBeamOrEndpointGeometry() {
        for(double interrupted:new double[]{20,180,259,260}) {
            double previous=Double.POSITIVE_INFINITY;
            for(int age=0;age<30;age++) {
                var sample=MothershipFx.sampleAbort(interrupted,age,0);
                assertEquals(MothershipFx.Phase.ABORT,sample.phase());assertEquals(0,sample.beam());
                assertTrue(sample.heat()<previous);previous=sample.heat();
                int[] vertices={0};
                MothershipFxMesh.lance((x,y,z,r,g,b,a)->{
                    assertTrue(Math.abs(z)<=6,"No failed beam or terminal explosion at the distant endpoint");
                    assertTrue(a>=0&&a<=1);vertices[0]++;
                },sample,100,MothershipFx.Detail.NEAR,interrupted,true);
                assertTrue(vertices[0]>0&&vertices[0]<=256);
            }
            MothershipFxMesh.lance((x,y,z,r,g,b,a)->fail("Expired failure must be invisible"),
                    MothershipFx.sampleAbort(interrupted,30,0),100,MothershipFx.Detail.NEAR,interrupted,true);
        }
    }
    @Test void arrivalEffectsStayWithinTheirSixSecondAndTwoSecondWindows() {
        for(var detail:List.of(MothershipFx.Detail.NEAR,MothershipFx.Detail.FAR)) {
            for(double tick:new double[]{0,60,119,120,131,140,159,160}) {
                int[] count={0};
                MothershipFxMesh.Sink bounded=(x,y,z,r,g,b,a)->{
                    assertTrue(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z));
                    assertTrue(a>=0&&a<=1);
                    count[0]++;
                };
                MothershipFxMesh.arrivalBeacon(bounded,tick,35,detail);
                MothershipFxMesh.arrivalWarp(bounded,tick,detail);
                assertTrue(count[0]<=256,"Arrival vertices at tick "+tick);
                assertTrue(count[0]>0 || tick==160);
            }
        }
        MothershipFxMesh.Sink reject=(x,y,z,r,g,b,a)->fail("Outside arrival window");
        MothershipFxMesh.arrivalWarp(reject,119,MothershipFx.Detail.NEAR);
        MothershipFxMesh.arrivalBeacon(reject,132,35,MothershipFx.Detail.NEAR);
        MothershipFxMesh.arrivalWarp(reject,160,MothershipFx.Detail.NEAR);
    }

    @Test void eachTargetFootRingPrecedesItsExplosionAndTheFinalHaloReachesSeventyPercentOfKirov() {
        MothershipFxMesh.Sink reject=(x,y,z,r,g,b,a)->fail("Pulse before warning window");
        MothershipFxMesh.groundPulse(reject,252,MothershipFx.Detail.NEAR);
        double last=0;
        for(int ordinal=1;ordinal<=4;ordinal++) {
            double[] preMax={0};
            MothershipFxMesh.groundPulse((x,y,z,r,g,b,a)->{
                assertEquals(.06,y,1e-9);preMax[0]=Math.max(preMax[0],Math.hypot(x,z));
            },MothershipFx.damageTick(ordinal)-1,MothershipFx.Detail.NEAR);
            assertTrue(preMax[0]>last,"Increasing target-foot warning ring "+ordinal);
            last=preMax[0];
            int[] blastVertices={0};
            MothershipFxMesh.groundPulse((x,y,z,r,g,b,a)->blastVertices[0]++,
                    MothershipFx.damageTick(ordinal),MothershipFx.Detail.NEAR);
            assertTrue(blastVertices[0]>0,"Explosion follows each ring");
        }
        double[] finalRadius={0};
        MothershipFxMesh.groundPulse((x,y,z,r,g,b,a)->{
            if(Math.abs(y-.08)<1e-9)finalRadius[0]=Math.max(finalRadius[0],Math.hypot(x,z));
        },384,MothershipFx.Detail.NEAR);
        assertEquals(MothershipDoomsday.FINAL_VISUAL_RADIUS+2.2,finalRadius[0],1e-6);
        assertEquals(16,new MothershipDoomsday().hit(4).blastRadius());
    }
    @Test void hullHaloBrightensDuringChargeAndFadesAfterFiring() {
        double previous=0;
        for(double tick:new double[]{120,160,200,240,260}) {
            double[] brightest={0};
            MothershipFxMesh.hullCharge((x,y,z,r,g,b,a)->brightest[0]=Math.max(brightest[0],a),
                    MothershipFx.sample(tick,0),MothershipFx.Detail.NEAR,tick);
            assertTrue(brightest[0]>previous);previous=brightest[0];
        }
        double[] after={0};
        MothershipFxMesh.hullCharge((x,y,z,r,g,b,a)->after[0]=Math.max(after[0],a),
                MothershipFx.sample(361,0),MothershipFx.Detail.NEAR,361);
        assertTrue(after[0]>0);
        MothershipFxMesh.hullCharge((x,y,z,r,g,b,a)->fail("Halo must end with cooling"),
                MothershipFx.sample(460,0),MothershipFx.Detail.NEAR,460);
    }
    @Test void enlargedChargeOrbStaysForEveryFiringFrameAndVanishesAtTheEndWithoutMovingTheImpact() {
        for(double tick:new double[]{259.99,260,260.5,290,320,359.99,360}) {
            double[] back={0},radius={0};
            MothershipFxMesh.lance((x,y,z,r,g,b,a)->{if(z<0)back[0]=Math.min(back[0],z);
                if(Math.abs(z)<1e-9)radius[0]=Math.max(radius[0],Math.hypot(x,y));},
                    MothershipFx.sample(tick,0),100,MothershipFx.Detail.NEAR,tick,false);
            assertEquals(-6,back[0],.000001);assertEquals(6,radius[0],.000001);
        }
        for(double tick:new double[]{360.01,361,380,459}) {
            MothershipFxMesh.lance((x,y,z,r,g,b,a)->fail("No post-fire muzzle orb"),
                    MothershipFx.sample(tick,0),100,MothershipFx.Detail.NEAR,tick,false);
        }
        MothershipFxMesh.lance((x,y,z,r,g,b,a)->assertTrue(z>80,"Only the endpoint explosion survives"),
                MothershipFx.sample(370,0),100,MothershipFx.Detail.NEAR,370,true);
    }
    @Test void idleNeverProducesExhaustInAnyWeaponPhase() {
        for(double tick:new double[]{-1,0,150,270,361,380,459,500}) {
            var sample=MothershipFx.sample(tick,0);
            assertEquals(0,sample.thrust());
            MothershipFxMesh.exhaust((x,y,z,r,g,b,a)->fail("Stationary exhaust"),sample.thrust(),1,tick,MothershipFx.Detail.NEAR);
        }
    }
    @Test
    void continuousBeamFitsFiveSecondFireWindow() {
        assertEquals(0, MothershipFx.sample(259.99, 1).beam());
        for (double tick = 260; tick <= 360; tick += 0.125) assertTrue(MothershipFx.sample(tick, 1).beam() > 0);
        assertEquals(0, MothershipFx.sample(360.01, 1).beam());
    }

    @Test
    void phasesAndThrustAreBoundedAtEveryBoundary() {
        assertEquals(MothershipFx.Phase.IDLE, MothershipFx.sample(-1, 0).phase());
        assertEquals(MothershipFx.Phase.LOCK, MothershipFx.sample(0, 0).phase());
        assertEquals(MothershipFx.Phase.CHARGE, MothershipFx.sample(100, 0).phase());
        assertEquals(MothershipFx.Phase.FIRE, MothershipFx.sample(260, 0).phase());
        assertEquals(MothershipFx.Phase.COOL, MothershipFx.sample(361, 0).phase());
        assertEquals(MothershipFx.Phase.DONE, MothershipFx.sample(460, 0).phase());
        for (double tick = -1; tick < 460; tick += 0.25) {
            var sample = MothershipFx.sample(tick, 2);
            for (double value : new double[]{sample.charge(), sample.beam(), sample.heat(), sample.thrust()}) {
                assertTrue(Double.isFinite(value) && value >= 0 && value <= 1);
            }
        }
    }

    @Test
    void lanceStartsAtOnePointAndStopsAtTheSuppliedSurface() {
        for (double length : new double[]{0.02, 0.2, 10, 256, 10000}) {
            var vertices = new ArrayList<double[]>();
            // Isolate the beam layers; the muzzle orb now persists during FIRE and is tested above.
            var beamOnly = new MothershipFx.Sample(MothershipFx.Phase.IDLE, 0, MothershipFx.sample(270, 1).beam(), 0, 0);
            MothershipFxMesh.lance((x, y, z, r, g, b, a) -> vertices.add(new double[]{x, y, z}),
                    beamOnly, length, MothershipFx.Detail.NEAR, 270, false);
            assertFalse(vertices.isEmpty());
            int originCount = 0;
            for (double[] v : vertices) {
                assertTrue(v[2] >= 0 && v[2] <= MothershipFx.length(length));
                if (v[2] == 0) { assertEquals(0, v[0]); assertEquals(0, v[1]); originCount++; }
            }
            assertEquals(48, originCount, "All eight sides of three beam layers share the same muzzle point");
        }
    }

    @Test
    void invisibleAndInvalidEffectsDoNotSubmitVertices() {
        MothershipFxMesh.Sink reject = (x, y, z, r, g, b, a) -> fail("Unexpected vertex");
        for (double length : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            MothershipFxMesh.lance(reject, MothershipFx.sample(110, 1), length, MothershipFx.Detail.NEAR, 110, true);
        }
        MothershipFxMesh.exhaust(reject, 1, Double.NaN, 1, MothershipFx.Detail.NEAR);
        MothershipFxMesh.exhaust(reject, 1, 1, 1, MothershipFx.Detail.HIDDEN);
        MothershipFxMesh.lance(reject, MothershipFx.sample(210, 1), 200, MothershipFx.Detail.HIDDEN, 210, true);
        assertEquals(MothershipFx.Detail.NEAR, MothershipFx.detail(0));
        assertEquals(MothershipFx.Detail.FAR, MothershipFx.detail(96 * 96));
        assertEquals(MothershipFx.Detail.HIDDEN, MothershipFx.detail(256 * 256));
        assertEquals(MothershipFx.Detail.HIDDEN, MothershipFx.detail(Double.NaN));
    }

    @Test
    void allFramesStayInsideBudgetAndExportTheActualGeometryForPreview() throws IOException {
        Path folder = Path.of("build/reports/mothership-fx");
        Files.createDirectories(folder);
        int nearMax = 0;
        int farMax = 0;
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(folder.resolve("frames.bin.gz")))) {
            for (var detail : List.of(MothershipFx.Detail.NEAR, MothershipFx.Detail.FAR)) {
                for (int tick = 0; tick < MothershipFx.TOTAL_TICKS; tick++) {
                    ByteArrayOutputStream frame = new ByteArrayOutputStream();
                    ByteBuffer vertex = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
                    int[] count = {0};
                    MothershipFxMesh.Sink sink = (x, y, z, r, g, b, a) -> {
                        assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
                        assertTrue(a >= 0 && a <= 1);
                        vertex.clear();
                        vertex.putFloat((float) x).putFloat((float) y).putFloat((float) z);
                        vertex.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
                        frame.writeBytes(vertex.array());
                        count[0]++;
                    };
                    var sample = MothershipFx.sample(tick, 0.65);
                    MothershipFxMesh.lance(sink, sample, 100, detail, tick, true);
                    MothershipFxMesh.groundPulse((x,y,z,r,g,b,a)->sink.vertex(x,y,z+100,r,g,b,a),tick,detail);
                    MothershipFxMesh.groundFirePreview((x, y, z, r, g, b, a) -> sink.vertex(x, y, z + 100, r, g, b, a), tick, detail);
                    MothershipFxMesh.dropCharge(sink,.99,tick);
                    MothershipFxMesh.dropCharge(sink,.99,tick);
                    var rig = com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.get();
                    var hull=rig.doomsday;
                    MothershipFxMesh.hullCharge((x,y,z,r,g,b,a)->sink.vertex(x-hull.x(),y-hull.y(),z-hull.z(),r,g,b,a),sample,detail,tick);
                    for (int i = 0; i < MothershipFx.MAX_NOZZLES; i++) {
                        var engine = rig.engines.get(i); var p = engine.position().sub(rig.doomsday);
                        MothershipFxMesh.Sink translated = (vx, vy, vz, r, g, b, a) -> sink.vertex(vx+p.x(),vy+p.y(),vz+p.z(),r,g,b,a);
                        MothershipFxMesh.gather(translated, sample, -p.x(), -p.y(), -p.z(), i, tick, detail);
                        MothershipFxMesh.exhaust((vx, vy, vz, r, g, b, a) -> translated.vertex(vx,vy,-vz,r,g,b,a),
                                sample.thrust(), engine.radius(), tick + i * 7, detail);
                    }
                    assertEquals(0, count[0] % 4);
                    assertTrue(count[0] <= MothershipFx.MAX_VERTICES, "Geometry budget at tick " + tick);
                    if (detail == MothershipFx.Detail.NEAR) nearMax = Math.max(nearMax, count[0]);
                    else farMax = Math.max(farMax, count[0]);
                    output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(count[0]).array());
                    frame.writeTo(output);
                }
            }
        }
        assertTrue(farMax < nearMax * 0.4);
        var weapon = new MothershipDoomsday();
        weapon.begin(0);
        var pose = new MothershipDoomsday.Pose(0, 0, 0, 0, 0);
        StringBuilder timeline = new StringBuilder("[");
        int readyTick = MothershipDoomsday.REFIRE_INTERVAL_TICKS;
        for (int tick = 0; tick <= readyTick; tick++) {
            var state = weapon.advance(tick, pose);
            if (tick > 0) timeline.append(',');
            timeline.append('[').append(state.phase().ordinal()).append(',').append(weapon.consumedHits()).append(',')
                    .append(state.movementLocked() ? 1 : 0).append(',').append(state.cooldownRemaining()).append(',')
                    .append(state.hitMask()).append(',').append(state.soundMask()).append(',')
                    .append(state.targetGlowing() ? 1 : 0).append(']');
        }
        timeline.append(']');
        String hits = java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(i -> "" + MothershipDoomsday.damageTick(i))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String pulses = java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(i -> {
            var hit = weapon.hit(i);
            // Export the actual calculation's maximum-health fraction, not a second formula in JavaScript.
            float percent = hit.directDamageAgainst(100) - hit.directDamage();
            return "[" + hit.directDamage() + "," + percent + "," + hit.blastDamage() + "," + hit.blastRadius() + "]";
        }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        var rig = com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.get();
        String nozzles = rig.engines.stream().map(engine -> {
            var p = engine.position().sub(rig.doomsday);
            return "[" + p.x() + "," + p.y() + "," + p.z() + "," + engine.radius() + "]";
        }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String json = "{\"schemaVersion\":2,\"frames\":" + MothershipFx.TOTAL_TICKS
                + ",\"nearMaxVertices\":" + nearMax + ",\"farMaxVertices\":" + farMax
                + ",\"nearMaxTriangles\":" + nearMax / 2 + ",\"farMaxTriangles\":" + farMax / 2
                + ",\"nozzles\":10,\"nozzleOffsets\":" + nozzles + ",\"beamLength\":100,\"aftZ\":-90,\"circularSides\":8,\"hitTicks\":" + hits + ",\"particleEntities\":0"
                + ",\"lockTicks\":" + MothershipDoomsday.LOCK_TICKS + ",\"chargeTicks\":" + MothershipDoomsday.CHARGE_TICKS
                + ",\"fireTicks\":" + MothershipDoomsday.FIRE_TICKS
                + ",\"afterglowTicks\":" + MothershipFx.COOL_TICKS + ",\"readyTick\":" + readyTick
                + ",\"refireIntervalTicks\":" + MothershipDoomsday.REFIRE_INTERVAL_TICKS + ",\"pulses\":" + pulses
                + ",\"finalVisualRadius\":" + MothershipDoomsday.FINAL_VISUAL_RADIUS
                + ",\"blastRadius\":" + weapon.blastRadius() + ",\"fireSites\":" + weapon.fireSites().size()
                + ",\"maxVertices\":" + MothershipFx.MAX_VERTICES + ",\"timeline\":" + timeline + "}";
        Files.writeString(folder.resolve("budget.json"), json);
    }
}

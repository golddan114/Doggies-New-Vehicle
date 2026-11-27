package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ApocalypseRunningGearTest {
    private static DnvTurretMesh.MeshData mesh() throws Exception {
        try (var in = ApocalypseRunningGearTest.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/apocalypse.etm")) {
            assertNotNull(in);
            return DnvTurretMesh.APOCALYPSE.parse(in.readAllBytes());
        }
    }

    @Test void exactVerticesNormalsUvsAndAllLinksSurviveEveryTrackSeamAndReversePhase() throws Exception {
        var mesh = mesh(); var cache = new ApocalypseRunningGear(); Object entity = new Object();
        for (float phase : new float[] {0, .00001F, .249F, .5F, .99999F, 1, -.2F}) {
            float wl = phase * 360, wr = -phase * 235;
            var gear = cache.sample(entity, mesh, wl, wr, phase, -phase);
            List<Float> wheels = new ArrayList<>(), tracks = new ArrayList<>();
            for (var wheel : ApocalypseRig.INSTANCE.wheels) {
                PoseStack pose = new PoseStack(); Vec3 p = wheel.position();
                pose.translate(p.x, p.y, p.z); pose.mulPose(Axis.XP.rotationDegrees(p.x < 0 ? wl : wr));
                original(wheels, mesh.part(wheel.part()).main(), pose);
            }
            for (var track : ApocalypseRig.INSTANCE.tracks) {
                for (int i = 0; i < track.count(); i++) {
                    var link = ApocalypseRig.link(track, (track.x() < 0 ? phase : -phase) * track.length()
                            + track.length() * i / track.count());
                    PoseStack pose = new PoseStack(); Vec3 p = link.position();
                    pose.translate(p.x, p.y, p.z); pose.mulPose(Axis.XP.rotation((float) link.angle()));
                    original(tracks, mesh.part("track_link").main(), pose);
                }
            }
            equivalent(wheels, gear.wheels()); equivalent(tracks, gear.tracks());
        }
    }

    @Test void idleAndRepeatedShadowPassesBakeOnceAndExactChangesRebuildOnlyChangedGroup() throws Exception {
        var mesh = mesh(); var cache = new ApocalypseRunningGear(); Object key = new Object();
        var gear = cache.sample(key, mesh, 20, -50, .2F, .8F);
        for (int frame = 0; frame < 7200; frame++) {
            assertSame(gear, cache.sample(key, mesh, 20, -50, .2F, .8F));
        }
        assertEquals(1, gear.wheelBuilds()); assertEquals(1, gear.trackBuilds());
        float[] oldTrack = gear.tracks().clone();
        cache.sample(key, mesh, 20.00001F, -50, .2F, .8F);
        assertEquals(2, gear.wheelBuilds()); assertEquals(1, gear.trackBuilds()); assertArrayEquals(oldTrack, gear.tracks());
        cache.sample(key, mesh, 20.00001F, -50, .20001F, .8F);
        assertEquals(2, gear.wheelBuilds()); assertEquals(2, gear.trackBuilds());
        assertFalse(java.util.Arrays.equals(oldTrack, gear.tracks()), "No visible animation quantization");
    }

    @Test void entityIdentityResourceReloadAndCapacityCannotLeakStaleGeometry() throws Exception {
        var mesh = mesh(); var cache = new ApocalypseRunningGear(); Object first = new Object();
        var original = cache.sample(first, mesh, 0, 0, 0, 0);
        Object[] retain = new Object[80];
        for (int i = 0; i < retain.length; i++) {
            retain[i] = new Object();
            assertNotSame(original, cache.sample(retain[i], mesh, 0, 0, 0, 0));
            assertTrue(cache.retainedEntities() <= 32);
        }
        assertNotSame(original, cache.sample(first, mesh(), 0, 0, 0, 0));
        assertEquals(1, cache.retainedEntities());
    }

    private static void original(List<Float> out, float[] triangles, PoseStack pose) {
        float[] q = DnvQuadStream.quads(triangles);
        for (int i = 0; i < q.length; i += 8) {
            Vector3f p = pose.last().pose().transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2]));
            Vector3f n = pose.last().normal().transform(new Vector3f(q[i + 3], q[i + 4], q[i + 5]));
            for (float value : new float[] {p.x, p.y, p.z, n.x, n.y, n.z, q[i + 6], q[i + 7]}) out.add(value);
        }
    }
    private static void equivalent(List<Float> expected, float[] actual) {
        assertEquals(expected.size(), actual.length);
        for (int i = 0; i < actual.length; i++) assertEquals(expected.get(i), actual[i], i % 8 >= 6 ? 0 : 2e-6, "vertex field " + i);
    }
}

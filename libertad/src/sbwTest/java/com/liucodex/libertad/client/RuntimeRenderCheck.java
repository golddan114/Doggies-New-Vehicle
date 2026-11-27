package com.liucodex.libertad.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.ship.MainBatteryView;
import com.liucodex.libertad.ship.ShipModel;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** Same-input A/B for the pose preparation used before vertex submission. */
public final class RuntimeRenderCheck {
    private static final ShipModel MODEL = ShipModel.get();
    private static final int RENDERS = 2400;
    private static volatile float checksum;

    public static void main(String[] args) {
        WatchCameraCheck.run(args);
        checkHelmChase();
        checkShellTracer();
        checkBatteryProjection();
        checkSearchlightSubmission();
        var batches = MODEL.batches;
        var interleaved = new ArrayList<>(batches);
        interleaved.sort(java.util.Comparator.comparing(batch -> batch.texture().toString()));
        var original = new Matrix4f[ShipModel.VISUAL_PARTS];
        var normals = new Matrix3f[ShipModel.VISUAL_PARTS];
        var scratch = new ShipRenderPose();
        for (int i = 0; i < 4; i++) {
            var input = new Frame(i);
            var bodyBefore = new Matrix4d(input.body);
            var baseBefore = new Matrix4f(input.base);
            var normalsBefore = new Matrix3f(input.normal);
            for (var order : List.of(batches, interleaved)) {
                int last = -1;
                for (var batch : order) {
                    int part = batch.part();
                    original(input, original, normals, part);
                    if (part != last) input.update(scratch, part);
                    require(scratch.position.equals(original[part]) && scratch.normal.equals(normals[part]),
                            "changed position/normal matrix for part " + part + " in frame " + i);
                    last = part;
                }
                Arrays.fill(original, null);
                Arrays.fill(normals, null);
            }
            require(input.body.equals(bodyBefore) && input.base.equals(baseBefore) && input.normal.equals(normalsBefore),
                    "pose update mutated the base matrix");
        }
        System.out.println("PASS: all part matrices match the reference renderer across 4 poses and 2 batch orders");

        var mx = ManagementFactory.getThreadMXBean();
        if (!(mx instanceof ThreadMXBean allocations) || !allocations.isThreadAllocatedMemorySupported()) return;
        if (!allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        Frame[] frames = {new Frame(0), new Frame(1), new Frame(2), new Frame(3)};
        for (int i = 0; i < 3; i++) {
            sample(false, frames, allocations);
            sample(true, frames, allocations);
        }
        var oldBytes = new long[7];
        var newBytes = new long[7];
        var oldTime = new long[7];
        var newTime = new long[7];
        for (int i = 0; i < oldBytes.length; i++) {
            boolean first = (i & 1) == 0;
            Sample a = sample(first, frames, allocations), b = sample(!first, frames, allocations);
            Sample old = first ? b : a, current = first ? a : b;
            oldBytes[i] = old.bytes;
            newBytes[i] = current.bytes;
            oldTime[i] = old.nanos;
            newTime[i] = current.nanos;
            require(old.checksum == current.checksum, "A/B pose checksum differs");
        }
        System.out.printf("POSE_AB %d submissions/sample, median bytes/ship %d -> %d, median ns/ship %d -> %d%n",
                RENDERS, median(oldBytes), median(newBytes), median(oldTime), median(newTime));
    }

    private static void checkHelmChase() {
        class Orbit extends net.minecraft.client.Camera {
            void prepare(int elevation) {
                setRotation(37, -elevation);
                setPosition(25,72,10);
                move(-4,0,0);
            }
            void apply(Vec3 offset) { move((float)-offset.x,(float)offset.y,(float)offset.z); }
        }
        var offset = new Vec3(108,29,0);
        for (int elevation : new int[]{0,15,30,60,85}) {
            var camera = new Orbit();
            camera.prepare(elevation);
            var look = new org.joml.Vector3f(camera.getLookVector());
            if (HelmChaseCamera.clip(camera,offset,(start,end)->end) != offset) throw new AssertionError("dry orbit");
            Vec3 limited = HelmChaseCamera.clip(camera,offset,(start,end)->
                    end.y >= 63 ? end : start.lerp(end,(start.y-63)/(start.y-end.y)));
            camera.apply(limited);
            double height = camera.getPosition().y;
            if (height < 63.349 || (elevation >= 30 && height > 63.351)
                    || (elevation < 30 && !limited.equals(offset)) || !look.equals(camera.getLookVector()))
                throw new AssertionError("helm orbit elevation=" + elevation + " height=" + height);
            System.out.printf("helm %d degrees: distance %.3f, camera Y %.4f, surface Y 63%n", elevation,limited.x,height);
        }
    }

    private static void checkShellTracer() {
        var from = new Vec3(10, 9, -8);
        var to = new Vec3(25, 8.5, -8);
        var segment = new ShellTracer.Segment(20, from, to);
        require(segment.visibleTip(19, .9F).equals(from) && segment.visibleTip(20, 0).equals(from),
                "tracer started before visible shell movement");
        require(segment.visibleTip(20, .25F).equals(from.lerp(to, .25)) && segment.visibleTip(21, 0).equals(to),
                "tracer outran the interpolated shell or missed the completed movement");
        require(segment.visibleTip(25, 0).equals(to) && segment.opacity(24, 0) == 0
                        && segment.opacity(22, 0) < segment.opacity(21, 0),
                "tracer extended beyond its segment or failed to fade out");
        System.out.println("PASS: naval shell tracer stays behind the frame's shell tip and fades in four ticks");
    }

    private static void checkSearchlightSubmission() {
        float[] stream = MODEL.batches.stream().filter(b -> b.texture().equals(LibertadRenderer.SEARCHLIGHT_FACE))
                .findFirst().orElseThrow().vertices();
        List<Vec3> positions = new ArrayList<>();
        List<Integer> lights = new ArrayList<>();
        var consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vertex")) {
                        positions.add(new Vec3((float) args[1], (float) args[2], (float) args[3]));
                    } else if (method.getName().equals("uv2")) {
                        lights.add((int) args[0]);
                    }
                    return method.getReturnType() == Void.TYPE ? null : proxy;
                });
        LibertadRenderer.emitSearchlightGlow(stream, new ShipRenderPose(), consumer);
        int exposedTriangles = stream.length / 24;
        require(positions.size() == exposedTriangles * 4 && lights.size() == exposedTriangles * 4
                        && lights.stream().allMatch(v -> v == LightTexture.FULL_BRIGHT),
                "eyes pass did not submit every exposed triangle at full brightness");
        for (int triangle = 0; triangle < stream.length / 24; triangle++) {
            for (int corner = 0; corner < 4; corner++) {
                int i = triangle * 24 + Math.min(corner, 2) * 8;
                require(positions.get(triangle * 4 + corner).equals(new Vec3(stream[i], stream[i+1], stream[i+2])),
                        "quad adapter changed a searchlight triangle");
            }
        }
        System.out.println("PASS: eyes pass submits " + exposedTriangles + " exposed triangles at full brightness");
    }

    private static void checkBatteryProjection() {
        Vec3 eye = new Vec3(200, 80, -320);
        var normal = new MainBatteryHud.Projection(23, eye,
                new Matrix4f().perspective((float) Math.toRadians(70), 1.6f, .05f, 512));
        var zoom = new MainBatteryHud.Projection(23, eye,
                new Matrix4f().perspective((float) Math.toRadians(20), 1.6f, .05f, 512));
        Vec3 point = eye.add(2, 1, -20);
        var standard = normal.project(point, 400, 250);
        var magnified = zoom.project(point, 400, 250);
        require(standard.x() > 200 && standard.y() < 125 && magnified.x() > standard.x() && magnified.y() < standard.y(),
                "prediction must consume the frame projection including zoom");
        require(normal.project(eye.add(0, 0, 20), 400, 250) == null, "point behind camera appeared in the HUD");
        var edge = normal.project(eye.add(-200, 0, -20), 400, 250);
        require(edge.edge() && edge.x() > 0 && edge.x() < 35, "out-of-view bearing must stay within the viewport");
        var loaded = new MainBatteryView.Load(0, 0, true);
        var blocked = new MainBatteryView.Marker(point, MainBatteryView.Endpoint.BLOCKED);
        var contact = new MainBatteryView.Marker(point.add(2, 0, 0), MainBatteryView.Endpoint.CONTACT);
        var mount = new MainBatteryView(MainBatteryView.Aim.BLOCKED, loaded, loaded, blocked, contact);
        require(mount.marker().endpoint() == MainBatteryView.Endpoint.BLOCKED, "merged reticle hid a blocked tube");
        var loading = new MainBatteryView(MainBatteryView.Aim.CLEAR, loaded, new MainBatteryView.Load(200, 400, false), contact, contact);
        require(loading.progress() == .5f, "one bar must show the slower tube");
        require(loading.marker().equals(contact), "twin mount must share one marker");
        var empty = new MainBatteryView(MainBatteryView.Aim.CLEAR, loaded, new MainBatteryView.Load(0, 0, false), null, contact);
        require(empty.progress() == 0 && empty.marker().equals(contact), "empty tube or missing path sample lost");
        var cleared = new MainBatteryView(MainBatteryView.Aim.CLEAR, loaded, loaded, blocked, contact);
        require(MainBatteryHud.reloadOpacity(true, cleared.progress()) == 0xFF,
                "confirmed ready must be opaque even with a prior blocked path sample");
        int lowOpacity = MainBatteryHud.reloadOpacity(false, mount.progress());
        require(mount.progress() == 1 && lowOpacity > 0 && lowOpacity < 128,
                "blocked but loaded must show a full, dimmed bar");
        require(MainBatteryHud.reloadOpacity(false, loading.progress()) == lowOpacity
                && MainBatteryHud.reloadOpacity(false, empty.progress()) == lowOpacity,
                "loading and empty must use the same low opacity as an obstructed mount");
        var completing = new MainBatteryView.Load(0, 400, true);
        require(MainBatteryHud.reloadOpacity(true, completing.progress()) == lowOpacity,
                "an active reload timer must stay dim even if the ready bit came from before the shot");
        int bar = MainBatteryHud.barTop(250, 49);
        require(MainBatteryHud.hintTop(bar) > bar + 2 && MainBatteryHud.hintTop(bar) + 9 < 250 - 39,
                "one secondary hint must fit below the reload strip and above health");
        int stacked = MainBatteryHud.barTop(250, 69);
        require(bar - stacked == 20 && MainBatteryHud.hintTop(stacked) + 9 < 250 - 59,
                "stacked vanilla bars must push the strip and hint up");
        require(MainBatteryHud.secondaryHintKey(0).endsWith(".enable")
                && MainBatteryHud.secondaryHintKey(1).endsWith(".disable")
                && MainBatteryHud.secondaryHintKey(2).endsWith(".returning"), "hint must follow the confirmed secondary state");
        require(LibertadClient.helmYaw(175, 0, true) == 90 && LibertadClient.helmYaw(-175, 0, true) == -90
                && LibertadClient.helmYaw(-175, 175, true) == -175, "first-person yaw must be bow-relative and wrap correctly");
        require(LibertadClient.helmYaw(175, 0, false) == 175 && LibertadClient.helmYaw(360, 0, false) == 360,
                "third-person rotation and rear aim must be unrestricted");
        System.out.println("PASS: zoom-aware projection, reload opacity, secondary hint and view-specific helm yaw");
    }

    private static void original(Frame input, Matrix4f[] positions, Matrix3f[] normals, int part) {
        if (positions[part] != null) return;
        Matrix4d matrix = input.body;
        if (part >= ShipModel.PARTS) {
            var mount = MODEL.aaMounts.get((part - ShipModel.PARTS) / 2);
            var pivot = mount.pivot();
            var local = new Matrix4d().translate(pivot.x, pivot.y, pivot.z)
                    .rotateY(Math.toRadians(input.angles(part).yaw()) - mount.restYaw())
                    .translate(-pivot.x, -pivot.y, -pivot.z);
            if ((part - ShipModel.PARTS) % 2 == 1) {
                var hinge = mount.pitchPivot();
                local.translate(hinge.x, hinge.y, hinge.z)
                        .rotate(Math.toRadians(input.angles(part).pitch()), -Math.cos(mount.restYaw()), 0, Math.sin(mount.restYaw()))
                        .translate(-hinge.x, -hinge.y, -hinge.z);
            }
            matrix = new Matrix4d(input.body).mul(local);
        } else if (part > 0) {
            matrix = new Matrix4d(input.body).mul(part >= ShipModel.FIRST_PROPELLER
                    ? MODEL.propellerTransform(part, input.spin) : MODEL.transform(part, input.angles(part)));
        }
        positions[part] = new Matrix4f(input.base).mul(new Matrix4f(matrix));
        normals[part] = new Matrix3f(input.normal).mul(new Matrix3f(new Matrix4f(matrix)).invert().transpose());
    }

    private static Sample sample(boolean current, Frame[] frames, ThreadMXBean allocations) {
        long thread = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        float value = 0;
        for (int i = 0; i < RENDERS; i++) {
            Frame input = frames[i % frames.length];
            if (current) {
                var pose = new ShipRenderPose();
                int last = -1;
                for (var batch : MODEL.batches) {
                    int part = batch.part();
                    if (part != last) input.update(pose, part);
                    value += pose.position.m30() + pose.normal.m01();
                    last = part;
                }
            } else {
                var poses = new Matrix4f[ShipModel.VISUAL_PARTS];
                var normals = new Matrix3f[ShipModel.VISUAL_PARTS];
                for (var batch : MODEL.batches) {
                    int part = batch.part();
                    original(input, poses, normals, part);
                    value += poses[part].m30() + normals[part].m01();
                }
            }
        }
        checksum = value;
        return new Sample(allocations.getThreadAllocatedBytes(thread) - before, System.nanoTime() - start, checksum);
    }

    private static long median(long[] samples) {
        Arrays.sort(samples);
        return samples[samples.length / 2] / RENDERS;
    }

    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private record Sample(long bytes, long nanos, float checksum) { }

    private static final class Frame {
        private final Matrix4d body;
        private final Matrix4f base;
        private final Matrix3f normal;
        private final double spin;
        private final Angles[] poses = new Angles[24];

        private Frame(int index) {
            body = new Matrix4d().translation(index * 13.2, -1.62 * index, 11.7 * index)
                    .rotateY(index * 2.1).rotateX(index * .12).rotateZ(index * -.08);
            base = new Matrix4f().rotateY(index * 1.3f).translate(-3f * index, index, 6f * index);
            normal = new Matrix3f(base).invert().transpose();
            spin = index * 127.0;
            for (int i = 0; i < poses.length; i++) poses[i] = new Angles((i - 12) * index * 3.125, (i - 4) * index * .41);
        }

        private Angles angles(int part) {
            if (part >= ShipModel.PARTS) return new Angles(spin - part * 4, Math.sin(spin) * 50);
            return part > 0 && part < ShipModel.FIRST_PROPELLER ? poses[(part - 1) / 2] : null;
        }

        private void update(ShipRenderPose pose, int part) {
            pose.update(MODEL, body, base, normal, part, angles(part), spin);
        }
    }
}

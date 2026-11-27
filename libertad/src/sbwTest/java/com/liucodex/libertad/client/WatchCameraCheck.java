package com.liucodex.libertad.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Exercise the production camera poses; optionally export the very same frames for the visual preview. */
final class WatchCameraCheck {
    static void run(String[] args) {
        var trace = new StringBuilder("seat,frame,bow,shipX,shipZ,eyeX,eyeY,eyeZ,yaw,pitch\n");
        for (int seat : new int[]{1, 2}) {
            var camera = new WatchOrbit(seat);
            Vec3 start = camera.position(Vec3.ZERO, 0);
            require(seat == 1 ? start.x > 0 : start.x < 0, "wrong starting side");
            Vec3 origin = new Vec3(40, 0, 85);
            for (int frame = 0; frame < 96; frame++) {
                // Two seconds straight, followed by a six-second turn crossing the yaw wrap.
                float bow = (float)(170 + Math.max(0,frame - 24) * 6.0 / 12);
                if (bow > 180) bow -= 360;
                origin = origin.add(new Vec3(0, 0, 32 * 1852.0 / 3600 / 12).yRot((float)-Math.toRadians(bow)));
                if (frame == 24) camera.lock();
                if (camera.locked()) {
                    camera.orbit(30, 10);
                    camera.scroll(3, false);
                    camera.scroll(-2, true);
                }
                Vec3 eye = camera.position(origin, bow);
                Vec2 angles = camera.rotation(bow);
                Vec3 target = origin.add(new Vec3(0, 8, 20).yRot((float)-Math.toRadians(bow)));
                Vec3 aim = Vec3.directionFromRotation(angles.y, angles.x);
                require(aim.normalize().dot(target.subtract(eye).normalize()) > .99999, "lens lost deck target seat=" + seat + " frame=" + frame + " dot=" + aim.normalize().dot(target.subtract(eye).normalize()));
                require(eye.subtract(origin).distanceTo(start.yRot((float)-Math.toRadians(bow))) < .025,
                        "locked camera drifted off its side of the ship");
                trace.append(String.format(Locale.ROOT,"%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        seat,frame,bow,origin.x,origin.z,eye.x,eye.y,eye.z,angles.x,angles.y));
            }
        }
        var camera = new WatchOrbit(1);
        Vec3 original = camera.position(Vec3.ZERO, 0);
        camera.orbit(45, 15);
        require(original.distanceTo(camera.position(Vec3.ZERO, 0)) > 20, "mouse orbit ignored");
        Vec3 beforeZoom = camera.position(Vec3.ZERO, 0);
        camera.scroll(2, false);
        require(beforeZoom.distanceTo(camera.position(Vec3.ZERO, 0)) > 8, "wheel zoom ignored");
        Vec3 beforePan = camera.position(Vec3.ZERO, 0);
        camera.scroll(2, true);
        require(Math.abs(camera.position(Vec3.ZERO, 0).z - beforePan.z - 8) < .001, "deck framing ignored");
        if (args.length == 1) {
            try { Files.writeString(Path.of(args[0]), trace); }
            catch (IOException e) { throw new IllegalStateException("Writing camera preview frames", e); }
        }
        System.out.println("PASS: both observer seats, 192 straight/turn poses, yaw wrap, orbit, zoom, deck framing and lock");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

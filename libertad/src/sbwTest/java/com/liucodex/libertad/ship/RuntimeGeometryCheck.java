package com.liucodex.libertad.ship;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.liucodex.libertad.compat.sbw.LoadedPath;
import com.liucodex.libertad.compat.sbw.NativeShells;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.MountGeometry.Point;
import com.liucodex.libertad.naval.ShellRange;
import com.liucodex.libertad.naval.SecondaryDirector;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

public final class RuntimeGeometryCheck {
    public static void main(String[] args) throws Exception {
        checkTracerSpawn();
        var model = ShipModel.get();
        checkHelmOptic(model);
        var lightState = new CompoundTag();
        require(!ShipFireControl.searchlightsOn(lightState), "lamps lit at startup");
        lightState.putInt("secondary", SecondaryDirector.Mode.ACTIVE.ordinal());
        require(ShipFireControl.searchlightsOn(lightState), "automatic battery did not light the lamps");
        lightState.putBoolean("multicrew", true);
        require(!ShipFireControl.searchlightsOn(lightState), "lamps lit in manual crew mode");
        lightState.putBoolean("multicrew", false);
        lightState.putInt("secondary", SecondaryDirector.Mode.RETURNING.ordinal());
        require(!ShipFireControl.searchlightsOn(lightState), "lamps stayed lit while turrets returned home");
        var lights = model.batches.stream().filter(b -> b.texture().getPath().equals("textures/entity/searchlight-face.png")).toList();
        require(lights.size() == 1 && lights.get(0).part() == 0 && lights.get(0).vertices().length == 180 * 24,
                "the static lamp batch must contain eighteen lights with five exposed quads each");
        for (int index = 0; index < model.aaMounts.size(); index++) {
            var mount = model.aaMounts.get(index);
            var angles = new Angles(Math.toDegrees(mount.restYaw()) + 40, 55);
            var turn = model.aaTransform(mount, angles, false);
            var elevated = model.aaTransform(mount, angles, true);
            require(ShipModel.point(turn, mount.pivot()).distanceToSqr(mount.pivot()) < 1e-12, "AA support pivot drifted");
            require(ShipModel.point(turn, mount.muzzle()).distanceToSqr(mount.muzzle()) > .01, "AA muzzle did not turn");
            require(ShipModel.point(elevated, mount.pitchPivot()).distanceToSqr(ShipModel.point(turn, mount.pitchPivot())) < 1e-12,
                    "barrel pitch detached from its yaw support");
            require(ShipModel.point(elevated, mount.muzzle()).y > ShipModel.point(turn, mount.muzzle()).y + .1,
                    "upward target did not raise the actual AA muzzle");
            var lowered = model.aaTransform(mount, new Angles(angles.yaw(), -20), true);
            require(ShipModel.point(lowered, mount.muzzle()).y < ShipModel.point(turn, mount.muzzle()).y,
                    "AA barrel cannot depress toward surrounding targets");
            final int part = ShipModel.PARTS + 2 * index;
            require(model.batches.stream().anyMatch(batch -> batch.part() == part), "AA motion part missing");
            require(model.batches.stream().anyMatch(batch -> batch.part() == part + 1), "AA pitch part missing");
        }
        Angles[] home = new Angles[24];
        Arrays.fill(home, new Angles(0, 0));
        var staticInverse = model.inverses(home);
        for (int i = 0; i < 4; i++) {
            final int zone = i;
            var guns = model.aaMounts.stream().filter(mount -> mount.zone() == zone).toList();
            require(guns.size() == 3, "three authored guns per AA zone");
            double heading = (i < 2 ? 90 : 130) * ((i & 1) == 0 ? 1 : -1);
            double yaw = Math.toRadians(heading);
            Vec3 direction = new Vec3(Math.sin(yaw) * 50, 8, Math.cos(yaw) * 50);
            require(guns.stream().anyMatch(gun -> {
                var muzzle = ShipModel.point(model.aaTransform(gun, new Angles(heading, 0), true), gun.muzzle());
                return !model.obstructed(muzzle, muzzle.add(direction), -1, staticInverse);
            }), "no clear outward shot in AA zone " + zone);
        }
        for (int i = 0; i < model.mounts.size(); i++) {
            var mount = model.mounts.get(i);
            double yawStep = i < 6 ? .5 : i < 16 ? .8 : 1.2;
            double pitchStep = i < 6 ? .4 : i < 16 ? .6 : .9;
            var arc = ShipFireControl.mountArc(i, mount);
            var yaw = arc.yawSectors().get(0);
            double side = yaw.to() > 90 ? 1 : -1;
            var first = ShipFireControl.advance(i, home[i], new Angles(120 * side, 35));
            require(first.yaw() == yawStep * side && first.pitch() == pitchStep, mount.id() + " exceeded slew");
            var wrapped = ShipFireControl.advance(i, new Angles(yaw.to(), 4), new Angles(yaw.from(), 0));
            require(Math.abs(wrapped.yaw() - (yaw.to() - yawStep)) < 1e-9 && arc.allows(wrapped)
                    && Math.abs(wrapped.pitch() - (4 - pitchStep)) < 1e-9, mount.id() + " crossed the excluded sector");
            require(!arc.allows(new Angles(-179.9, 4)) && !arc.allows(new Angles(179.9, 4)), mount.id() + " missing rear stops");
            Angles angles = new Angles(37, 15);
            var housing = model.transform(i * 2 + 1, angles);
            var transformed = model.transform(i * 2 + 2, angles);
            Vec3 base = ShipModel.vec(mount.yawCenter()), hinge = ShipModel.vec(mount.pitchCenter());
            require(ShipModel.point(housing, base).distanceToSqr(base) < 1e-12, mount.id() + " yaw center drifted");
            require(ShipModel.point(housing, hinge).distanceToSqr(ShipModel.point(transformed, hinge)) < 1e-12,
                    mount.id() + " pitch center drifted");
            var barrels = mount.at(angles);
            for (int j = 0; j < barrels.size(); j++) {
                Vec3 rendered = ShipModel.point(transformed, ShipModel.vec(mount.barrels().get(j).muzzle()));
                require(rendered.distanceToSqr(ShipModel.vec(barrels.get(j).muzzle())) < 1e-12, "render and fire matrices diverged");
                Vec3 bore = ShipModel.vector(transformed, ShipModel.vec(mount.barrels().get(j).forward()));
                require(bore.distanceToSqr(ShipModel.vec(barrels.get(j).forward())) < 1e-12, "render and fire bore diverged");
            }
            int clear = 0, blocked = 0;
            for (double azimuth = yaw.from(); azimuth <= yaw.to(); azimuth += 15) {
                for (double pitch = arc.minPitch(); pitch <= arc.maxPitch(); pitch += 5) {
                    boolean pairClear = true;
                    for (var barrel : mount.at(new Angles(azimuth, pitch))) {
                        Vec3 muzzle = ShipModel.vec(barrel.muzzle()), direction = ShipModel.vec(barrel.forward());
                        Vec3 back = muzzle.subtract(direction.scale(muzzle.subtract(ShipModel.vec(barrel.pitchCenter())).dot(direction)));
                        pairClear &= !model.obstructed(back, muzzle.add(direction.scale(1.9)), i, staticInverse);
                    }
                    if (pairClear) clear++; else blocked++;
                }
            }
            require(clear > 0, mount.id() + " has no clear firing samples");
            System.out.printf("%s yaw %.0f..%.0f pitch %.0f..%.0f deg, %.0f/%.0f deg/s, %d clear and %d blocked samples%n",
                    mount.id(), yaw.from(), yaw.to(), arc.minPitch(), arc.maxPitch(), yawStep * 20, pitchStep * 20, clear, blocked);
        }
        for (var batch : model.batches) {
            float[] vertices = batch.vertices();
            for (int i = 0; i < vertices.length; i += 24) {
                Vec3 a = vertex(vertices, i);
                Vec3 face = vertex(vertices, i + 8).subtract(a).cross(vertex(vertices, i + 16).subtract(a));
                for (int corner = i + 3; corner < i + 24; corner += 8) {
                    Vec3 normal = vertex(vertices, corner);
                    require(Math.abs(normal.lengthSqr() - 1) < 1e-4 && face.dot(normal) / Math.sqrt(face.lengthSqr()) > .7,
                            "exported face winding and normal disagree at part " + batch.part() + ", vertex " + corner);
                }
            }
        }
        // A texture batch can be a single flat panel. Check winding on each complete main-gun part.
        for (int part = 1; part <= 12; part++) {
            final int id = part;
            var batches = model.batches.stream().filter(batch -> batch.part() == id).toList();
            Vec3 center = Vec3.ZERO;
            int count = 0;
            for (var batch : batches) {
                float[] vertices = batch.vertices();
                for (int i = 0; i < vertices.length; i += 8) center = center.add(vertex(vertices, i));
                count += vertices.length / 8;
            }
            require(count > 0, "missing main-gun part " + part);
            center = center.scale(1.0 / count);
            double volume = 0;
            for (var batch : batches) {
                float[] vertices = batch.vertices();
                for (int i = 0; i < vertices.length; i += 24) {
                    Vec3 a = vertex(vertices, i).subtract(center);
                    Vec3 b = vertex(vertices, i + 8).subtract(center);
                    Vec3 c = vertex(vertices, i + 16).subtract(center);
                    volume += a.dot(b.cross(c)) / 6;
                }
            }
            require(volume > .01, "main-gun exterior points inward at part " + part);
        }
        for (int part = ShipModel.FIRST_PROPELLER; part < ShipModel.PARTS; part++) {
            var matrix = model.propellerTransform(part, 127);
            var sample = new Vec3(5, -3, 70);
            var moved = ShipModel.point(matrix, sample);
            require(moved.distanceToSqr(sample) > .01, "propeller did not move");
            require(ShipModel.point(model.propellerTransform(part, -127), moved).distanceToSqr(sample) < 1e-15,
                    "propeller reverse did not restore the original location");
        }
        var third = model.mounts.get(2);
        var barrel = third.at(new Angles(0, 15)).get(0);
        Vec3 tip = ShipModel.vec(barrel.muzzle()), direction = ShipModel.vec(barrel.forward());
        Vec3 rear = tip.subtract(direction.scale(tip.subtract(ShipModel.vec(barrel.pitchCenter())).dot(direction)));
        require(model.obstructed(rear, tip.add(direction.scale(1.9)), 2, staticInverse), "third turret must see upper deck obstruction");
        var high = third.at(new Angles(0, 35)).get(0);
        Vec3 clearTip = ShipModel.vec(high.muzzle());
        Vec3 highDirection = ShipModel.vec(high.forward());
        Vec3 highRear = clearTip.subtract(highDirection.scale(clearTip.subtract(ShipModel.vec(high.pitchCenter())).dot(highDirection)));
        require(!model.obstructed(highRear, clearTip.add(highDirection.scale(1.9)), 2, staticInverse), "third turret clears deck at high elevation");
        try (var manifest = RuntimeGeometryCheck.class.getResourceAsStream("/assets/libertad/model-sources.json")) {
            require(manifest != null, "model provenance is missing");
            var reader = new InputStreamReader(manifest, StandardCharsets.UTF_8);
            var witness = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("collision_only_witness");
            Vec3 start = vector(witness.getAsJsonArray("start")), end = vector(witness.getAsJsonArray("end"));
            require(model.obstructed(start, end, -1, staticInverse), "pretrim fire-control surface was lost");
            for (var batch : model.batches) {
                float[] vertices = batch.vertices();
                for (int i = 0; i < vertices.length; i += 24) {
                    require(!intersects(start, end, vertex(vertices, i), vertex(vertices, i + 8), vertex(vertices, i + 16)),
                            "preserved fire-control surface also remains in the visual stream");
                }
            }
        }

        double east = LoadedPath.limit(new Vec3(15, 0, 3), new Vec3(25, 0, 3), (x, z) -> x == 0 && z == 0);
        require(east > .0999 && east < .1, "east entry must stop inside the active chunk");
        double west = LoadedPath.limit(new Vec3(-15, 0, -2), new Vec3(-25, 0, -2), (x, z) -> x == -1 && z == -1);
        require(west > .0999 && west < .1, "negative-coordinate entry must stop at -16");
        require(LoadedPath.limit(new Vec3(16, 0, 16), new Vec3(16, 4, 16), (x, z) -> x == 1 && z == 1) == 1,
                "vertical path stays in its active chunk");
        require(LoadedPath.limit(Vec3.ZERO, new Vec3(5, 0, 5), (x, z) -> false) == 0,
                "inactive origin may not trace");
        System.out.println("PASS: production mesh, 40 render/fire transforms, independent obstruction and chunk clipping");
        checkMainBatteryView();
        checkShellFlightBoundaries();
        checkLongRangeSearchAndTeams();
        checkTravelCalibration();
    }

    private static void checkHelmOptic(ShipModel model) throws IOException {
        try (var stream = RuntimeGeometryCheck.class.getResourceAsStream("/data/libertad/sbw/vehicles/libertad.json")) {
            var vehicle = JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(stream),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            var seat = vehicle.getAsJsonArray("Seats").get(0).getAsJsonObject();
            var camera = seat.getAsJsonObject("CameraPos");
            var normal = vector(camera.getAsJsonArray("Position"));
            var optic = vector(camera.getAsJsonArray("ZoomPosition"));
            var primary = vehicle.getAsJsonObject("Weapons").getAsJsonObject(seat.getAsJsonArray("Weapons").get(0).getAsString());
            require(normal.equals(new Vec3(0, 10.25, 25.1)) && "Vehicle".equals(camera.get("Transform").getAsString())
                    && "Passenger".equals(camera.get("ZoomDirection").getAsString())
                    && !primary.getAsJsonObject("ShootPos").has("ViewPosition") && primary.get("DefaultZoom").getAsDouble() == 5,
                    "helm bridge, native zoom origin, passenger direction or magnification changed");
            Vec3 aft = normal.add(0, 0, -100);
            boolean oldViewBlocked = false;
            double highestElevation = -90;
            for (var batch : model.batches) {
                float[] points = batch.vertices();
                for (int i = 0; i < points.length; i += 24) {
                    oldViewBlocked |= intersects(normal, aft, vertex(points, i), vertex(points, i+8), vertex(points, i+16));
                    for (int corner = i; corner < i + 24; corner += 8) {
                        Vec3 relative = vertex(points, corner).subtract(optic);
                        highestElevation = Math.max(highestElevation,
                                Math.toDegrees(Math.atan2(relative.y, Math.hypot(relative.x, relative.z))));
                    }
                }
            }
            require(oldViewBlocked, "released bridge eye must reproduce the aft sight obstruction");
            require(highestElevation < -10, "ship enters the helm optic's horizon band: " + highestElevation);
            System.out.printf("PASS: helm optic %s, all neutral visual triangles below %.2f degrees across all bearings%n",
                    optic, highestElevation);
        }
    }

    private static void checkTracerSpawn() throws Exception {
        String source = Type.getInternalName(NativeShells.class);
        var node = new ClassNode();
        try (var in = NativeShells.class.getResourceAsStream("NativeShells.class")) {
            new ClassReader(in).accept(node, 0);
        }
        var methods = Set.of("managed", "writeTracerSpawn", "readTracerSpawn", "hasClientTracer");
        node.methods.removeIf(method -> !methods.contains(method.name));
        node.fields.clear();
        node.innerClasses.clear();
        node.nestMembers = null;
        var writer = new ClassWriter(0);
        String projectile = "com/atsuishio/superbwarfare/entity/projectile/";
        node.accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(
                source, "com/liucodex/libertad/ship/TracerSpawnProbe",
                projectile + "FastThrowableProjectile", Type.getInternalName(ProjectileStub.class),
                projectile + "CannonShellEntity", Type.getInternalName(CannonStub.class),
                "net/minecraft/world/entity/Entity", Type.getInternalName(ProjectileStub.class)))));
        var probe = MethodHandles.lookup().defineClass(writer.toByteArray());
        var write = probe.getMethod("writeTracerSpawn", ProjectileStub.class, FriendlyByteBuf.class);
        var read = probe.getMethod("readTracerSpawn", ProjectileStub.class, FriendlyByteBuf.class);
        var active = probe.getMethod("hasClientTracer", ProjectileStub.class);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var nativeShell = new CannonStub();
            write.invoke(null, nativeShell, buffer);
            require(!buffer.isReadable(), "ordinary SBW cannon spawn must stay byte-identical");
            var shipShell = new CannonStub();
            shipShell.getPersistentData().put("LibertadShot", new CompoundTag());
            shipShell.setCustomGravity(.06F);
            write.invoke(null, shipShell, buffer);
            require(buffer.readableBytes() == 8, "Libertad must append one bounded spawn suffix");
            var otherProjectile = new ProjectileStub();
            read.invoke(null, otherProjectile, buffer);
            require(buffer.readerIndex() == 0 && !(boolean) active.invoke(null, otherProjectile),
                    "tracer suffix affected a different projectile type");
            var clientShell = new CannonStub();
            read.invoke(null, clientShell, buffer);
            require((boolean) active.invoke(null, clientShell) && clientShell.getCustomGravity() == .06F && !buffer.isReadable(),
                    "Libertad spawn must enable the client tracer and preserve actual gravity");
            buffer.clear().writeLong(123);
            read.invoke(null, nativeShell, buffer);
            require(buffer.readerIndex() == 0 && !(boolean) active.invoke(null, nativeShell),
                    "unrecognized spawn suffix was consumed or enabled the tracer");
        } finally {
            buffer.release();
        }
        System.out.println("PASS: scoped 8-byte tracer spawn suffix and unchanged ordinary SBW projectiles");
    }

    public static class ProjectileStub {
        private final CompoundTag data = new CompoundTag();
        private float gravity;
        public CompoundTag getPersistentData() { return data; }
        public float getCustomGravity() { return gravity; }
        public void setCustomGravity(float gravity) { this.gravity = gravity; }
    }

    public static final class CannonStub extends ProjectileStub {}

    private static void checkMainBatteryView() {
        var origin = new Vec3(15, 20, 3);
        var direction = new Vec3(15, 0, 0);
        var positions = new java.util.ArrayList<Vec3>();
        var range = MainGunPrediction.trace(origin, direction, .06f, (start, end) -> {
            positions.add(end);
            return null;
        });
        require(positions.get(0).equals(new Vec3(30, 20, 3)), "shell fell before the native first move");
        require(Math.abs(positions.get(18).y - (20 - (double) .06f * 19 * 18 / 2)) < 1e-8,
                "incorrect air drop after nineteen untruncated steps");
        require(range.endpoint() == MainBatteryView.Endpoint.RANGE
                        && Math.abs(range.point().distanceTo(origin) - ShellRange.MAIN) < 1e-8,
                "prediction did not stop on the same sphere as the shell");
        var diagonal = new Vec3(1, 0, 1).normalize().scale(15);
        var full = MainGunPrediction.trace(origin, diagonal, 0, (from, to) -> null);
        require(full.endpoint() == MainBatteryView.Endpoint.RANGE && Math.abs(full.point().distanceTo(origin) - 720) < 1e-8,
                "diagonal prediction must reach 720");
        var boundary = MainGunPrediction.trace(origin, direction, .06f, (start, end) -> {
            double limit = LoadedPath.limit(start, end, (x, z) -> x == 0 && z == 0);
            return new MainBatteryView.Marker(start.lerp(end, limit), MainBatteryView.Endpoint.BOUNDARY);
        });
        require(boundary.point().x < 16 && boundary.point().x > 15.9999,
                "prediction passed the inactive chunk boundary");
        var hit = new MainBatteryView.Marker(new Vec3(37, 19, 3), MainBatteryView.Endpoint.CONTACT);
        var intercepted = MainGunPrediction.trace(origin, direction, .06f, (start, end) -> end.x >= 37 ? hit : null);
        require(intercepted.equals(hit), "prediction must preserve its first contact");

        var partial = new MainBatteryView(MainBatteryView.Aim.BLOCKED,
                new MainBatteryView.Load(175, 400, false), new MainBatteryView.Load(0, 0, true), hit, boundary);
        require(Math.abs(partial.left().progress() - .5625f) < 1e-6 && partial.right().progress() == 1,
                "each tube must retain its own native reload state");
        require(new MainBatteryView.Load(0, 400, false).progress() < 1,
                "an active native timer cannot display completed readiness");
        var mounts = new MainBatteryView[MainBatteryView.MOUNTS];
        Arrays.fill(mounts, MainBatteryView.EMPTY);
        mounts[4] = partial;
        var tag = new CompoundTag();
        MainBatteryView.write(tag, mounts);
        require(MainBatteryView.read(tag).equals(Arrays.asList(mounts)), "six mount snapshots must round trip");
        var raw = tag.getList("MainBattery", Tag.TAG_COMPOUND);
        raw.getCompound(4).getCompound("left").putDouble("x", Double.NaN);
        require(MainBatteryView.read(tag).get(4).leftMarker() == null, "nonfinite prediction escaped validation");
        raw.remove(0);
        require(MainBatteryView.read(tag).isEmpty() && MainBatteryView.read(new CompoundTag()).isEmpty(),
                "partial or legacy snapshots must fail closed");
        System.out.println("PASS: native-order air steps, bounded prediction and six independent twin reload snapshots");
    }

    private static void checkShellFlightBoundaries() {
        var muzzle = new Point(.5, 70, .5);
        for (int radius : new int[]{ShellRange.SECONDARY, ShellRange.MAIN}) {
            var range = new ShellRange(muzzle, radius, 200);
            // Secondary continuation from a first contact uses the original muzzle and expiry.
            Vec3 hit = new Vec3(radius - 2, 70, .5);
            Vec3 inside = hit.add(1, 0, 0), outside = hit.add(10, 0, 0);
            var allowed = NativeShells.flight(range, hit, inside, 199, (a, b) -> 1, (a, b) -> 1);
            require(!allowed.terminal() && allowed.end().equals(inside), "loaded penetration lost in-range hits");
            var boundary = NativeShells.flight(range, hit, outside, 199, (a, b) -> 1, (a, b) -> 1);
            require(boundary.terminal() && Math.abs(boundary.end().x - (radius + .5)) < 1e-8,
                    "penetration exceeded the original muzzle range");
            require(NativeShells.flight(range, hit, outside, 200, (a, b) -> 1, (a, b) -> 1) == null,
                    "penetration reset the original expiry");
        }
        var range = new ShellRange(muzzle, ShellRange.MAIN, 200);
        var start = new Vec3(15, 70, .5);
        var end = new Vec3(30, 70, .5);
        var step = NativeShells.flight(range, start, end, 0,
                (a, b) -> LoadedPath.limit(a, b, (x, z) -> x == 0), (a, b) -> {
                    require(b.x < 16, "water ray entered an inactive column");
                    return .5;
                });
        require(step.terminal() && step.end().x > 15.49 && step.end().x < 15.5,
                "first water entry must win before the inactive column");
        require(LoadedPath.limit(new Vec3(15, 70, 15), new Vec3(17, 70, 17), (x, z) -> x == z) < .5,
                "corner trace must not enter an inactive side column");
        Vec3 from = new Vec3(15.99, 70, 15.8);
        Vec3 to = from.add(new Vec3(1, 0, 1).normalize().scale(ShellRange.MAIN));
        require(LoadedPath.limit(from, to, (x, z) -> true) == 1, "720 diagonal must not truncate after 64 columns");
        require(LoadedPath.limit(from, to, (x, z) -> x < 32 || z < 31) < 1, "final diagonal column must be checked");
        System.out.println("PASS: 600/720 flight and continuation bounds, chunk corners, water priority, expiry");
    }

    private static void checkLongRangeSearchAndTeams() {
        var origin = new Vec3(-.01, 70, -15.9);
        var scan = ShipFireControl.searchColumns(origin, ShellRange.SECONDARY);
        var center = new BlockPos(-1, 0, -1);
        require(scan.next().equals(center), "the commander's column comes first");
        var covered = new HashSet<BlockPos>();
        covered.add(center);
        int previousRing = 0;
        while (scan.hasNext()) {
            BlockPos next = scan.next().immutable();
            int ring = Math.max(Math.abs(next.getX() + 1), Math.abs(next.getZ() + 1));
            require(ring >= previousRing && covered.add(next), "search visits each near-to-far column once");
            previousRing = ring;
        }
        require(previousRing == 38 && covered.size() == 77 * 77, "search covers the 600-block columns");
        require(ShipFireControl.lead(new Vec3(590, 0, 0), new Vec3(0, 0, 1)).z > 39,
                "moving targets at 590 blocks need more than 25 ticks of lead");
        var board = new Scoreboard();
        var blue = board.addPlayerTeam("blue");
        var red = board.addPlayerTeam("red");
        var hostile = SecondaryDirector.Relation.HOSTILE;
        var friendly = SecondaryDirector.Relation.FRIENDLY;
        board.addPlayerToTeam("driver", red);
        require(ShipFireControl.playerRelation(blue, board.getPlayersTeam("driver"), true) == hostile, "blue engages red");
        board.addPlayerToTeam("driver", blue);
        require(ShipFireControl.playerRelation(blue, board.getPlayersTeam("driver"), true) == friendly, "team switch protects immediately");
        board.removePlayerFromTeam("driver");
        require(ShipFireControl.playerRelation(blue, board.getPlayersTeam("driver"), true) == friendly, "no team protects immediately");
        require(ShipFireControl.playerRelation(null, red, true) == friendly
                && ShipFireControl.playerRelation(blue, red, false) == friendly, "teamless commander and exempt players");
    }

    private static void checkTravelCalibration() throws IOException {
        try (var input = RuntimeGeometryCheck.class.getResourceAsStream("/data/libertad/sbw/vehicles/libertad.json")) {
            require(input != null, "native ship data is missing");
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var engine = data.getAsJsonObject("EngineInfo");
            double verticalBeforeDamping = data.get("Gravity").getAsDouble() / .85;
            double depth = verticalBeforeDamping / engine.get("Buoyancy").getAsDouble();
            float rate = engine.get("MaxForwardSpeedRate").getAsFloat();
            float increment = engine.get("Increment").getAsFloat();
            double knot = 1852.0 / (3600 * 20);
            require(Math.abs(ShipTravelLimits.WATER_FORWARD / knot - 32) < 1e-10, "water cap must be 32 knots");
            double speed = 0;
            float power = 0;
            int fiveKnotsTick = 0, tenKnotsTick = 0;
            for (int tick = 0; tick < 2400; tick++) {
                // Exact horizontal branch for a straight, level, ungrounded ship with full input.
                float alignment = (float) (speed / Math.hypot(speed, verticalBeforeDamping));
                double damp = .835f - .04f * depth + .005f * alignment;
                power = Math.min(power + increment * (1 - power / 1.02f), 1);
                speed = (speed + speed * .04) * damp + .11 * rate * power * power;
                if (fiveKnotsTick == 0 && speed >= 5 * knot) fiveKnotsTick = tick + 1;
                if (tenKnotsTick == 0 && speed >= 10 * knot) tenKnotsTick = tick + 1;
            }
            require(fiveKnotsTick >= 60 && fiveKnotsTick <= 70 && tenKnotsTick >= 100 && tenKnotsTick <= 110,
                    "low-speed forward build-up must stay within the 32-knot ramp");
            require(Math.abs(speed - ShipTravelLimits.WATER_FORWARD) < .0002,
                    "candidate engine thrust misses the level-water target: " + speed);
            require(ShipTravelLimits.limit(new Vec3(0, -.125, speed), 0, true)
                    .distanceToSqr(new Vec3(0, -.125, ShipTravelLimits.WATER_FORWARD)) < 1e-14,
                    "water cap or vertical motion changed");
            require(ShipTravelLimits.limit(new Vec3(0, 0, 1), 0, false).equals(new Vec3(0, 0, .4)), "dry cap changed");
            require(ShipTravelLimits.limit(new Vec3(0, 0, -1), 0, true).equals(new Vec3(0, 0, -.1)), "reverse cap changed");
            var turning = ShipTravelLimits.limit(new Vec3(2, .1, 1), -90, true);
            require(Math.abs(turning.horizontalDistance() - ShipTravelLimits.WATER_FORWARD) < 1e-10 && turning.y == .1,
                    "heading-relative speed limit changed the yaw or vertical branch");
            System.out.printf("PASS: native straight-water equation %.6f blocks/tick, limit %.6f, 5 kn %.2fs, 10 kn %.2fs, dry and reverse caps%n",
                    speed, ShipTravelLimits.WATER_FORWARD, fiveKnotsTick / 20.0, tenKnotsTick / 20.0);
        }
    }

    private static boolean intersects(Vec3 start, Vec3 end, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a), ac = c.subtract(a), normal = ab.cross(ac), delta = end.subtract(start);
        double denominator = normal.dot(delta);
        if (Math.abs(denominator) < 1e-10) return false;
        double time = normal.dot(a.subtract(start)) / denominator;
        if (time < 0 || time > 1) return false;
        Vec3 hit = start.add(delta.scale(time));
        return ab.cross(hit.subtract(a)).dot(normal) >= 0
                && c.subtract(b).cross(hit.subtract(b)).dot(normal) >= 0
                && a.subtract(c).cross(hit.subtract(c)).dot(normal) >= 0;
    }

    private static Vec3 vector(JsonArray data) {
        return new Vec3(data.get(0).getAsDouble(), data.get(1).getAsDouble(), data.get(2).getAsDouble());
    }

    private static Vec3 vertex(float[] data, int index) {
        return new Vec3(data[index], data[index + 1], data[index + 2]);
    }

    private static void require(boolean pass, String reason) {
        if (!pass) throw new AssertionError(reason);
    }
}

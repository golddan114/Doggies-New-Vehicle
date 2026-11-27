package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.compat.sbw.LoadedPath;
import com.liucodex.libertad.compat.sbw.NativeShells;
import com.liucodex.libertad.compat.sbw.SbwGunPort;
import com.liucodex.libertad.compat.sbw.WaterEntry;
import com.liucodex.libertad.naval.MountGeometry;
import com.liucodex.libertad.naval.CrewControl;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.AirDefenseRules;
import com.liucodex.libertad.naval.MountGeometry.BarrelPose;
import com.liucodex.libertad.naval.SalvoControl;
import com.liucodex.libertad.naval.SalvoControl.Arc;
import com.liucodex.libertad.naval.SalvoControl.Control;
import com.liucodex.libertad.naval.SalvoControl.YawSector;
import com.liucodex.libertad.naval.SecondaryDirector;
import com.liucodex.libertad.naval.SecondaryDirector.Relation;
import com.liucodex.libertad.naval.ShellRange;
import com.liucodex.libertad.naval.MountGeometry.Point;
import com.liucodex.libertad.ship.MainBatteryView.Aim;
import com.liucodex.libertad.ship.MainBatteryView.Endpoint;
import com.liucodex.libertad.ship.MainBatteryView.Load;
import com.liucodex.libertad.ship.MainBatteryView.Marker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.joml.Matrix4d;

/** Server owns turret poses, consent to fire and one shared secondary target scan. */
final class ShipFireControl {
    static final Angles HOME = new Angles(0, 0);
    static final float MAIN_CANNON_VOLUME = 6;
    private static final double[] MAIN_STOPS = {150, 150, 135, 150, 150, 135};
    private static final Set<ResourceLocation> DNV_BOSSES = Set.of(
            new ResourceLocation("doggievehicle", "ses_executor_of_truth"),
            new ResourceLocation("doggievehicle", "noise"),
            new ResourceLocation("doggievehicle", "silent_heaven"),
            new ResourceLocation("doggievehicle", "hover_cube_boss"));
    private static final EntityTypeTest<Entity, Entity> ENTITY = EntityTypeTest.forClass(Entity.class);
    private static final double SPEED = 15, GRAVITY = .06;
    private final LibertadEntity ship;
    private final ShipModel model = ShipModel.get();
    private final Map<String, Integer> indexByGun = new HashMap<>();
    private final Arc[] arcs = new Arc[24];
    private final double searchRadius;
    private final Angles[] poses = new Angles[24];
    private final long[] holdUntil = new long[24];
    private final MainBatteryView[] mainView = new MainBatteryView[MainBatteryView.MOUNTS];
    private final MainBatteryView[][] sideView = {new MainBatteryView[9], new MainBatteryView[9]};
    private final int[] sideReady = new int[2];
    private final boolean[] highView = new boolean[3];
    private final CrewControl crew = new CrewControl();
    private final SecondaryDirector secondary = new SecondaryDirector();
    private final ShipAirDefense airDefense;
    private Control control;
    private String aaCommander = "";
    private long lastToggle = -1;
    private ScanWork scan;
    private Matrix4d body, inverse;
    private Matrix4d[] hullInverse;
    private int mainReady, mainBlocked, mainEmpty, mainReload, ammoType;
    private int requestedAmmo = -1;
    private boolean switchingAmmo;

    ShipFireControl(LibertadEntity ship) {
        this.ship = ship;
        airDefense = new ShipAirDefense(ship, model);
        Arrays.fill(poses, HOME);
        Arrays.fill(mainView, MainBatteryView.EMPTY);
        double muzzleOffset = 0;
        for (int i = 0; i < poses.length; i++) {
            var mount = model.mounts.get(i);
            arcs[i] = mountArc(i, mount);
            for (var barrel : mount.barrels()) {
                indexByGun.put(barrel.gunId(), i);
                if (i >= 6) {
                    double offset = Math.sqrt(mount.yawCenter().lengthSquared())
                            + Math.sqrt(mount.pitchCenter().minus(mount.yawCenter()).lengthSquared())
                            + Math.sqrt(barrel.muzzle().minus(mount.pitchCenter()).lengthSquared());
                    muzzleOffset = Math.max(muzzleOffset, offset);
                }
            }
        }
        searchRadius = ShellRange.SECONDARY + muzzleOffset;
    }

    Angles pose(int i) { return poses[i]; }

    CompoundTag savePoses() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Multicrew", crew.multicrew());
        tag.putString("AaCommander", aaCommander);
        for (int i = 0; i < poses.length; i++) {
            CompoundTag pose = new CompoundTag();
            pose.putDouble("Yaw", poses[i].yaw());
            pose.putDouble("Pitch", poses[i].pitch());
            tag.put(model.mounts.get(i).id(), pose);
        }
        return tag;
    }

    void loadPoses(CompoundTag tag) {
        boolean returning = false;
        for (int i = 0; i < poses.length; i++) {
            CompoundTag pose = tag.getCompound(model.mounts.get(i).id());
            double yaw = pose.getDouble("Yaw"), pitch = pose.getDouble("Pitch");
            if (pose.contains("Yaw", Tag.TAG_ANY_NUMERIC) && pose.contains("Pitch", Tag.TAG_ANY_NUMERIC)
                    && Double.isFinite(yaw) && Double.isFinite(pitch)) {
                poses[i] = visualGoal(new Angles(yaw, pitch), arc(i));
            } else {
                poses[i] = HOME;
            }
            returning |= i >= 6 && !poses[i].equals(HOME);
        }
        crew.restore(tag.getBoolean("Multicrew"));
        aaCommander = tag.getString("AaCommander");
        syncCrew();
        secondary.restoreIdle(returning);
    }

    void vacate() { syncCrew(); }

    boolean multicrew() { return crew.multicrew(); }

    BarrelPose barrel(String id, float partial) {
        Integer i = indexByGun.get(id);
        if (i == null) return null;
        return model.mounts.get(i).at(ship.angles(i, partial)).stream().filter(b -> b.gunId().equals(id)).findFirst().orElse(null);
    }

    void request(LivingEntity who) {
        if (!(who instanceof ServerPlayer player) || !ship.master(who)) return;
        syncCrew();
        crew.request(0, player.getUUID(), ship.level().getGameTime());
    }

    void requestSide(ServerPlayer player, long generation, boolean high, boolean fire) {
        syncCrew();
        int seat = ship.getSeatIndex(player);
        Control operator = crew.controller(seat);
        if (seat < 1 || operator == null || operator.seatEpoch() != generation
                || !operator.player().equals(player.getUUID())) return;
        highView[seat] = high;
        if (fire) crew.request(seat, player.getUUID(), ship.level().getGameTime());
    }

    void helmOptic(ServerPlayer player, long generation, boolean zoom) {
        syncCrew();
        if (control != null && control.seatEpoch() == generation && control.player().equals(player.getUUID())) {
            highView[0] = zoom;
        }
    }

    void toggleCrewMode(ServerPlayer player) {
        syncCrew();
        if (crew.toggle(player.getUUID(), ship.level().getGameTime())) {
            syncCrew();
            Arrays.fill(highView, false);
            Arrays.fill(sideReady, 0);
            for (var view : sideView) Arrays.fill(view, MainBatteryView.EMPTY);
            feedback(player, crew.multicrew() ? "crew_on" : "crew_off");
        } else feedback(player, "mode_cooldown");
    }

    void toggle(ServerPlayer player) {
        syncCrew();
        if (crew.multicrew()) {
            feedback(player, "secondary_mode");
            return;
        }
        long now = ship.level().getGameTime();
        if (now == lastToggle) return;
        lastToggle = now;
        boolean enable = secondary.mode() != SecondaryDirector.Mode.ACTIVE;
        if (!secondary.setEnabled(control, enable)) {
            feedback(player, "helm");
            return;
        }
        scan = null;
        if (enable && ship.getEnergy() <= 0) feedback(player, "secondary_no_energy");
        else feedback(player, enable ? "secondary_on" : "secondary_off");
    }

    private static void feedback(ServerPlayer player, String reason) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.libertad.control." + reason), true);
    }

    private ServerPlayer occupant(int seat) {
        return ship.getNthEntity(seat) instanceof ServerPlayer player && ship.crewMember(player) ? player : null;
    }

    private void syncCrew() {
        for (int seat = 0; seat < 3; seat++) {
            ServerPlayer player = occupant(seat);
            Control previous = crew.controller(seat);
            crew.assign(seat, player == null ? null : player.getUUID());
            if (seat > 0 && !java.util.Objects.equals(previous, crew.controller(seat))) {
                highView[seat] = false;
                sideReady[seat - 1] = 0;
                Arrays.fill(sideView[seat - 1], MainBatteryView.EMPTY);
            }
        }
        if (!java.util.Objects.equals(control, crew.controller(0))) {
            control = crew.controller(0);
            highView[0] = false;
            secondary.updateController(control);
            scan = null;
            requestedAmmo = -1;
            Arrays.fill(mainView, MainBatteryView.EMPTY);
        }
    }

    void tick(ServerLevel level) {
        syncCrew();
        ServerPlayer player = occupant(0);
        if (player != null) aaCommander = player.getScoreboardName();
        boolean aaEnabled = AirDefenseRules.enabled(crew.multicrew(), secondary.mode() == SecondaryDirector.Mode.ACTIVE,
                !ship.isWreck() && ship.getEnergy() > 0);
        ServerPlayer operator = player != null || !aaEnabled || aaCommander.isEmpty() ? player
                : level.getServer().getPlayerList().getPlayerByName(aaCommander);
        Team team = operator != null ? operator.getTeam() : level.getScoreboard().getPlayersTeam(aaCommander);
        airDefense.tick(level, aaEnabled, operator, target -> relation(target, operator, team), poses);
        if (player == null && !crew.multicrew()) {
            returnHome(0);
            mainReady = mainBlocked = mainEmpty = mainReload = 0;
            return;
        }
        body = ship.getVehicleTransform(1);
        inverse = new Matrix4d(body).invert();
        if (player != null) tickMain(level, player);
        else {
            mainReady = mainBlocked = mainEmpty = mainReload = 0;
            for (int i = 0; i < 6; i++) poses[i] = advance(i, poses[i], HOME);
        }
        if (crew.multicrew()) {
            tickSides(level);
        } else if (player != null && secondary.mode() == SecondaryDirector.Mode.ACTIVE) {
            updateScan(level, player);
            track(level, player);
        } else returnHome(6);
    }

    private void tickMain(ServerLevel level, ServerPlayer player) {
        int previousAmmo = ammoType;
        syncAmmo();
        if (ammoType != previousAmmo) Arrays.fill(mainView, MainBatteryView.EMPTY);
        Vec3 aim = worldAim(level, player);
        Angles[] goals = new Angles[6];
        for (int i = 0; i < 6; i++) {
            goals[i] = aim == null ? null : solve(i, aim, Vec3.ZERO, false);
            if (goals[i] != null) {
                Arc arc = arc(i);
                Angles goal = goals[i];
                // At a target behind the stops, hold the current side of the excluded sector.
                if (Math.abs(goal.yaw()) > MAIN_STOPS[i] && poses[i].yaw() != 0) {
                    goal = new Angles(Math.copySign(Math.abs(goal.yaw()), poses[i].yaw()), goal.pitch());
                }
                poses[i] = advance(i, poses[i], visualGoal(goal, arc));
            }
        }
        hullInverse = model.inverses(poses);
        boolean shoot = crew.consume(0, level.getGameTime());
        mainReady = mainBlocked = mainEmpty = mainReload = 0;
        int sample = Math.floorMod(ship.tickCount, mainView.length);
        for (int i = 0; i < mainView.length; i++) {
            MountGeometry mount = model.mounts.get(i);
            Arc arc = arc(i);
            SbwGunPort port = port(level, player, i);
            Angles goal = goals[i];
            boolean legal = goal != null && arc.allows(goal) && arc.allows(poses[i]);
            boolean aligned = legal && near(poses[i], goal);
            boolean loaded = legal;
            for (var barrel : mount.barrels()) {
                GunData gun = ship.getGunData(barrel.gunId());
                if (gun == null || gun.selectedAmmoType.get() != ammoType) {
                    loaded = false;
                    mainEmpty |= 1 << i;
                } else if (gun.reloading()) {
                    loaded = false;
                    mainReload |= 1 << i;
                } else if (legal && !port.ready(barrel.gunId())) {
                    loaded = false;
                    mainEmpty |= 1 << i;
                }
            }
            var barrels = mount.at(poses[i]);
            boolean checkBores = aligned || i == sample;
            boolean leftClear = checkBores && port.clear(barrels.get(0));
            boolean rightClear = checkBores && (leftClear || i == sample) && port.clear(barrels.get(1));
            boolean clear = aligned && leftClear && rightClear;
            if (!clear) mainBlocked |= 1 << i;
            boolean ready = loaded && clear && NativeShells.room(level, 2);
            if (ready) mainReady |= 1 << i;
            if (shoot && ready) {
                SalvoControl.executeTwin(control, mount, arc, poses[i], goal, .7, port);
            }
            Aim status = goal == null ? Aim.UNKNOWN : !legal ? Aim.LIMIT : !aligned ? Aim.TURNING : clear ? Aim.CLEAR : Aim.BLOCKED;
            var old = mainView[i];
            var left = barrels.get(0);
            var right = barrels.get(1);
            mainView[i] = new MainBatteryView(status, observedLoad(left.gunId()), observedLoad(right.gunId()),
                    i == sample ? preview(level, player, left, leftClear) : old.leftMarker(),
                    i == sample ? preview(level, player, right, rightClear) : old.rightMarker());
        }

    }

    private void tickSides(ServerLevel level) {
        var players = new ServerPlayer[] {null, occupant(1), occupant(2)};
        var aims = new Vec3[3];
        var goals = new Angles[poses.length];
        for (int seat = 1; seat <= 2; seat++) {
            var player = players[seat];
            if (player == null) continue;
            float yaw = CrewControl.viewYaw(player.getYRot(), ship.getYRot(), seat, !highView[seat]);
            Vec3 direction = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -80, 80), yaw);
            aims[seat] = aimPoint(level, ship, ship.sideCamera(seat, 1, highView[seat]), direction, ShellRange.SECONDARY);
        }
        for (int i = 6; i < poses.length; i++) {
            int seat = sideSeat(i);
            if (aims[seat] != null) goals[i] = solve(i, aims[seat], Vec3.ZERO, false);
            poses[i] = advance(i, poses[i], goals[i] == null ? HOME : visualGoal(goals[i], arc(i)));
        }
        hullInverse = model.inverses(poses);
        for (int seat = 1; seat <= 2; seat++) {
            var player = players[seat];
            if (player == null) continue;
            boolean shoot = crew.consume(seat, level.getGameTime());
            int slot = 0, ready = 0;
            var views = sideView[seat - 1];
            int sample = Math.floorMod(ship.tickCount, views.length);
            for (int i = 6; i < poses.length; i++) {
                if (sideSeat(i) != seat) continue;
                if (tickSideMount(level, player, seat, i, goals[i], shoot, views, slot, sample == slot)) ready |= 1 << slot;
                slot++;
            }
            sideReady[seat - 1] = ready;
        }
    }

    private int sideSeat(int i) { return CrewControl.sideSeat(model.mounts.get(i).yawCenter().x()); }

    private boolean tickSideMount(ServerLevel level, ServerPlayer player, int seat, int i, Angles goal,
                                  boolean shoot, MainBatteryView[] views, int slot, boolean sample) {
        var mount = model.mounts.get(i);
        var arc = arc(i);
        var port = port(level, player, i, seat);
        var barrels = mount.at(poses[i]);
        var left = barrels.get(0);
        var right = barrels.size() == 2 ? barrels.get(1) : null;
        boolean legal = goal != null && arc.allows(goal) && arc.allows(poses[i]);
        boolean aligned = legal && near(poses[i], goal);
        boolean leftClear = (aligned || sample) && port.clear(left);
        boolean rightClear = right == null || ((aligned || sample) && port.clear(right));
        boolean clear = aligned && leftClear && rightClear;
        boolean ready = clear && port.ready(left.gunId()) && (right == null || port.ready(right.gunId()))
                && NativeShells.room(level, barrels.size());
        if (shoot && ready) {
            Control operator = crew.controller(seat);
            if (right != null) SalvoControl.executeTwin(operator, mount, arc, poses[i], goal, .7, port);
            else if (port.isCurrentMaster(operator)) port.tryFire(left);
        }
        Aim status = goal == null ? Aim.UNKNOWN : !legal ? Aim.LIMIT : !aligned ? Aim.TURNING : clear ? Aim.CLEAR : Aim.BLOCKED;
        var old = views[slot];
        Load load = observedLoad(left.gunId());
        views[slot] = new MainBatteryView(status, load, right == null ? load : observedLoad(right.gunId()),
                sample ? preview(level, player, left, leftClear) : old.leftMarker(),
                right == null ? null : sample ? preview(level, player, right, rightClear) : old.rightMarker());
        return ready;
    }

    private void syncAmmo() {
        GunData primary = ship.getGunData("main_01_l");
        if (primary == null) return;
        if (requestedAmmo >= 0 && !mainReloading()) {
            switchingAmmo = true;
            try {
                primary.changeAmmoConsumer(requestedAmmo, ship.getAmmoSupplier());
                requestedAmmo = -1;
            } finally { switchingAmmo = false; }
        }
        ammoType = Mth.clamp(primary.selectedAmmoType.get(), 0, 1);
        for (int i = 0; i < 6; i++) for (var barrel : model.mounts.get(i).barrels()) {
            GunData gun = ship.getGunData(barrel.gunId());
            if (gun != null && !gun.reloading() && gun.selectedAmmoType.get() != ammoType) {
                gun.changeAmmoConsumer(ammoType, ship.getAmmoSupplier());
            }
        }
    }

    boolean deferAmmoChange(GunData gun, int index) {
        if (switchingAmmo || gun != ship.getGunData("main_01_l") || !mainReloading()) return false;
        if (ship.level() instanceof ServerLevel && ship.master(ship.getNthEntity(0))) requestedAmmo = Mth.clamp(index, 0, 1);
        return true;
    }

    private boolean mainReloading() {
        return ship.getGunDataMap().entrySet().stream().anyMatch(e -> e.getKey().startsWith("main_") && e.getValue().reloading());
    }

    private Vec3 worldAim(ServerLevel level, ServerPlayer player) {
        Vec3 eye = highView[0] ? ship.getZoomPos(player, 1) : ship.getCameraPos(player, 1);
        return aimPoint(level, ship, eye, player.getViewVector(1));
    }

    private Load observedLoad(String id) {
        GunData gun = ship.getGunData(id);
        if (gun == null || (id.startsWith("main_") && gun.selectedAmmoType.get() != ammoType)) return Load.EMPTY;
        if (!gun.reloading()) return new Load(0, 0, gun.ammo.get() > 0);
        int remaining = gun.reload.time();
        int total = gun.get(gun.reload.empty() ? GunProp.EMPTY_RELOAD_TIME : GunProp.NORMAL_RELOAD_TIME);
        return new Load(remaining, Math.max(1, Math.max(total, remaining)), false);
    }

    private Marker preview(ServerLevel level, ServerPlayer player, BarrelPose barrel, boolean clear) {
        Vec3 muzzle = ShipModel.point(body, ShipModel.vec(barrel.muzzle()));
        if (!clear) return new Marker(muzzle, Endpoint.BLOCKED);
        GunData gun = ship.getGunData(barrel.gunId());
        if (gun == null || gun.get(GunProp.ADD_SHOOTER_DELTA_MOVEMENT)) return null;
        float speed = gun.get(GunProp.VELOCITY).floatValue(), gravity = gun.get(GunProp.GRAVITY).floatValue();
        if (!(speed > 0 && speed <= 64 && gravity >= 0 && gravity <= 2)) return null;
        Vec3 velocity = ShipModel.vector(body, ShipModel.vec(barrel.forward())).normalize().scale(speed);
        boolean main = barrel.gunId().startsWith("main_");
        return MainGunPrediction.trace(muzzle, velocity, gravity, main ? ShellRange.MAIN : ShellRange.SECONDARY,
                (from, to) -> previewSegment(level, player, from, to, main && ammoType == 0));
    }

    private Marker previewSegment(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 end, boolean armorPiercing) {
        double loaded = LoadedPath.limit(level, from, end);
        if (loaded <= 0) return new Marker(from, Endpoint.BOUNDARY);
        Vec3 to = from.lerp(end, loaded);
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(to))) return new Marker(from, Endpoint.BOUNDARY);
        if (!level.getFluidState(BlockPos.containing(from)).isEmpty()) return new Marker(from, Endpoint.WATER);
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, ship));
        boolean contact = hit.getType() != HitResult.Type.MISS;
        Vec3 nearest = contact ? hit.getLocation() : to;
        Endpoint endpoint = contact ? Endpoint.CONTACT : loaded < 1 ? Endpoint.BOUNDARY : null;
        if (contact && armorPiercing && !level.getFluidState(hit.getBlockPos()).isEmpty()) {
            var solid = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship));
            if (solid.getType() == HitResult.Type.MISS || solid.getLocation().distanceToSqr(nearest) > 1e-8) endpoint = Endpoint.WATER;
        }
        List<Entity> candidates = new ArrayList<>();
        level.getEntities(ENTITY, new AABB(from, nearest).inflate(.3), e -> e != ship && e != player
                && e.isAlive() && !e.isSpectator() && e.getRootVehicle() != ship
                && !(e instanceof Projectile) && (e.isPickable() || e instanceof OBBEntity), candidates, 24);
        if (candidates.size() == 24) return new Marker(from, Endpoint.BOUNDARY);
        Vec3 stop = nearest;
        for (var entity : candidates) {
            Vec3 point = entityAim(entity, from, stop);
            if (point != null && from.distanceToSqr(point) < from.distanceToSqr(nearest)) {
                nearest = point;
                endpoint = Endpoint.CONTACT;
            }
        }
        return endpoint == null ? null : new Marker(nearest, endpoint);
    }

    static Vec3 aimPoint(ServerLevel level, Entity ship, Vec3 eye, Vec3 view) {
        return aimPoint(level, ship, eye, view, ShellRange.MAIN);
    }

    private static Vec3 aimPoint(ServerLevel level, Entity ship, Vec3 eye, Vec3 view, double range) {
        Vec3 far = eye.add(view.scale(range));
        double loaded = LoadedPath.limit(level, eye, far);
        if (loaded <= 0) return null;
        Vec3 traced = eye.lerp(far, loaded);
        var hit = level.clip(new ClipContext(eye, traced, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship));
        Vec3 stop = hit.getType() == HitResult.Type.MISS ? traced : hit.getLocation();
        int segments = Math.max(1, (int) Math.ceil(eye.distanceTo(stop) / 32));
        List<Entity> candidates = new ArrayList<>();
        Vec3 from = eye;
        for (int i = 1; i <= segments; i++) {
            Vec3 end = eye.lerp(stop, i / (double) segments);
            candidates.clear();
            level.getEntities(ENTITY, new AABB(from, end).inflate(.3),
                    target -> target != ship && target.isAlive() && !target.isSpectator() && target.getRootVehicle() != ship
                            && !(target instanceof Projectile) && (target.isPickable() || target instanceof OBBEntity),
                    candidates, 24);
            if (candidates.size() == 24) return null;
            Vec3 nearest = end;
            for (Entity entity : candidates) {
                Vec3 point = entityAim(entity, from, end);
                if (point != null && from.distanceToSqr(point) < from.distanceToSqr(nearest)) nearest = point;
            }
            if (nearest != end) return nearest;
            from = end;
        }
        return stop;
    }

    private static Vec3 entityAim(Entity entity, Vec3 from, Vec3 to) {
        if (entity instanceof OBBEntity obb && !obb.enableAABB() && !obb.getOBBs().isEmpty()) {
            Vec3 nearest = null;
            var start = OBB.vec3ToVector3d(from);
            var end = OBB.vec3ToVector3d(to);
            for (OBB part : obb.getOBBs()) {
                if (part.part == OBB.Part.COLLISION || part.part == OBB.Part.INTERACTIVE) continue;
                var hit = part.clip(start, end);
                if (hit.isEmpty()) continue;
                Vec3 point = OBB.vector3dToVec3(hit.get());
                if (nearest == null || from.distanceToSqr(point) < from.distanceToSqr(nearest)) nearest = point;
            }
            return nearest;
        }
        AABB bounds = entity.getBoundingBox();
        return bounds.contains(from) ? from : bounds.clip(from, to).orElse(null);
    }

    private Angles solve(int i, Vec3 target, Vec3 targetMotion, boolean limited) {
        MountGeometry mount = model.mounts.get(i);
        List<BarrelPose> barrels = mount.at(poses[i]);
        Vec3 origin = ShipModel.vec(barrels.get(0).muzzle());
        if (barrels.size() == 2) origin = origin.lerp(ShipModel.vec(barrels.get(1).muzzle()), .5);
        Vec3 delta = target.subtract(ShipModel.point(body, origin));
        if (delta.lengthSqr() < 4 || (limited && delta.lengthSqr() > ShellRange.SECONDARY * ShellRange.SECONDARY)) return null;
        Vec3 desired = lead(delta, targetMotion);
        Vec3 local = ShipModel.vector(inverse, desired);
        Vec3 forward = ShipModel.vec(mount.forward());
        Vec3 tangent = new Vec3(forward.z, 0, -forward.x);
        return new Angles(Math.toDegrees(Math.atan2(local.dot(tangent), local.dot(forward))),
                Math.toDegrees(Math.atan2(local.y, local.horizontalDistance())));
    }

    static Vec3 lead(Vec3 delta, Vec3 motion) {
        double time = delta.length() / SPEED;
        Vec3 desired = delta;
        for (int step = 0; step < 4; step++) {
            desired = delta.add(motion.scale(Math.min(time, ShellRange.SECONDARY / SPEED)))
                    .add(0, .5 * GRAVITY * time * Math.max(0, time - 1), 0);
            time = desired.length() / SPEED;
        }
        return desired;
    }

    private SbwGunPort port(ServerLevel level, ServerPlayer player, int i) {
        return port(level, player, i, 0);
    }

    private SbwGunPort port(ServerLevel level, ServerPlayer player, int i, int seat) {
        return port(level, player, i, seat, Map.of());
    }

    private SbwGunPort port(ServerLevel level, ServerPlayer player, int i, int seat, Map<String, Vec3> checkedDirections) {
        Control consent = crew.controller(seat);
        return new SbwGunPort(Libertad.SHIP.get(), ship, player, seat,
                () -> CrewControl.controls(crew.multicrew(), seat, i, model.mounts.get(i).yawCenter().x())
                        && crew.current(seat, consent) ? consent : null, new SbwGunPort.WorldAdmission() {
            private boolean primarySoundPlayed;

            @Override public boolean clear(VehicleEntity host, BarrelPose barrel) {
                return clearBore(level, i, barrel);
            }

            @Override public boolean admit(VehicleEntity host, ServerPlayer actor, GunData gun, BarrelPose barrel) {
                if (i < 6 && gun.selectedAmmoType.get() != ammoType) return false;
                Vec3 start = ShipModel.point(body, ShipModel.vec(barrel.muzzle()));
                Vec3 direction = checkedDirections.get(barrel.gunId());
                if (direction == null) {
                    direction = shotDirection(level, barrel, gun);
                    if (!clearBore(level, i, barrel, ShipModel.vector(inverse, direction))) return false;
                }
                // Use the checked direction. Applying spread again would invalidate the fire-time trace.
                var shot = new ShootParameters(ship.getAmmoSupplier(), actor, level, start, direction, gun, 0, true, null, null);
                if (!NativeShells.fire(gun, shot, i >= 6)) return false;
                ship.afterShoot(gun, direction);
                var effect = i < 6 ? ShipMuzzleEffects.MAIN : i < 16 ? ShipMuzzleEffects.HEAVY_SECONDARY
                        : ShipMuzzleEffects.LIGHT_SECONDARY;
                effect.flash(level, start, ShipModel.vector(body, ShipModel.vec(barrel.forward())).normalize());
                if (i >= 6) {
                    ship.playShootSound3p(actor, gun, start);
                } else if (!primarySoundPlayed) {
                    primarySoundPlayed = true;
                    level.playSound(null, start.x, start.y, start.z, Libertad.MAIN_CANNON_FIRE.get(),
                            SoundSource.PLAYERS, MAIN_CANNON_VOLUME, 1);
                }
                return true;
            }
        });
    }

    private Vec3 shotDirection(ServerLevel level, BarrelPose barrel, GunData gun) {
        Vec3 direction = ShipModel.vector(body, ShipModel.vec(barrel.forward())).normalize();
        return VectorTool.INSTANCE.randomSpreadVec(level.getRandom(), direction, gun.get(GunProp.SPREAD)).normalize();
    }

    private boolean clearBore(ServerLevel level, int i, BarrelPose barrel) {
        return clearBore(level, i, barrel, ShipModel.vec(barrel.forward()));
    }

    private boolean clearBore(ServerLevel level, int i, BarrelPose barrel, Vec3 direction) {
        Vec3 muzzle = ShipModel.vec(barrel.muzzle());
        Vec3 rear = muzzle.subtract(direction.scale(muzzle.subtract(ShipModel.vec(barrel.pitchCenter())).dot(direction)));
        Vec3 exit = muzzle.add(direction.scale(1.9));
        if (model.obstructed(rear, exit, i, hullInverse)) return false;
        if (i < 6) {
            // The exit test alone misses the bridge and other mounts further down the bore.
            Vec3 position = exit;
            for (int tick = 1; tick <= 10; tick++) {
                Vec3 worldDrop = ShipModel.vector(inverse, new Vec3(0, -.5 * GRAVITY * tick * (tick - 1), 0));
                Vec3 next = muzzle.add(direction.scale(SPEED * tick)).add(worldDrop);
                if (model.obstructed(position, next, i, hullInverse)) return false;
                if (!ship.getBoundingBoxForCulling().contains(ShipModel.point(body, next))) break;
                position = next;
            }
        }
        return blockClear(level, ShipModel.point(body, rear), ShipModel.point(body, exit));
    }

    private boolean blockClear(ServerLevel level, Vec3 from, Vec3 to) {
        return LoadedPath.limit(level, from, to) == 1 && level.getWorldBorder().isWithinBounds(BlockPos.containing(to))
                && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship)).getType() == HitResult.Type.MISS;
    }

    private void returnHome(int first) {
        boolean home = true;
        for (int i = first; i < poses.length; i++) {
            poses[i] = advance(i, poses[i], HOME);
            if (i >= 6) home &= poses[i].equals(HOME);
        }
        if (home) secondary.reachedHome();
    }

    private Arc arc(int i) { return arcs[i]; }

    static Arc mountArc(int i, MountGeometry mount) {
        if (i < 6) return new Arc(List.of(new YawSector(-MAIN_STOPS[i], MAIN_STOPS[i])), -5, 35);
        boolean medium = i < 16;
        double stop = medium ? 165 : 175;
        boolean positive = mount.yawCenter().x() * mount.forward().z() > 0;
        return new Arc(List.of(positive ? new YawSector(-15, stop) : new YawSector(-stop, 15)),
                medium ? -5 : -10, medium ? 45 : 75);
    }

    private static Angles visualGoal(Angles goal, Arc arc) {
        YawSector yaw = arc.yawSectors().get(0);
        return new Angles(Mth.clamp(goal.yaw(), yaw.from(), yaw.to()), Mth.clamp(goal.pitch(), arc.minPitch(), arc.maxPitch()));
    }
    private static boolean near(Angles actual, Angles goal) {
        return Math.abs(Math.IEEEremainder(actual.yaw() - goal.yaw(), 360)) <= .7 && Math.abs(actual.pitch() - goal.pitch()) <= .7;
    }
    static Angles advance(int mount, Angles current, Angles goal) {
        // Blocks run at 20 ticks/s. Rates in deg/s reflect the three different mounting weights.
        double yawStep = mount < 6 ? .5 : mount < 16 ? .8 : 1.2;
        double pitchStep = mount < 6 ? .4 : mount < 16 ? .6 : .9;
        // Every mount has one contiguous non-wrapping travel interval. Stay inside it when changing sides.
        return new Angles(current.yaw() + Mth.clamp(goal.yaw() - current.yaw(), -yawStep, yawStep),
                current.pitch() + Mth.clamp(goal.pitch() - current.pitch(), -pitchStep, pitchStep));
    }

    private Relation relation(Entity target, ServerPlayer player) {
        return relation(target, player, player.getTeam());
    }

    private Relation relation(Entity target, ServerPlayer player, Team commander) {
        if (target == null || target == ship || target == player || target.isRemoved() || !target.isAlive()
                || target.level() != ship.level() || target.getRootVehicle() == ship) return Relation.NEUTRAL;
        if (target instanceof VehicleEntity vehicle && vehicle.isWreck()) return Relation.NEUTRAL;
        if (target instanceof Player other) {
            Entity vehicle = other.getRootVehicle();
            return vehicle == other ? playerRelation(commander, other) : relation(vehicle, player, commander);
        }
        Entity driver = target instanceof VehicleEntity vehicle ? vehicle.getNthEntity(0) : target.getControllingPassenger();
        if (driver instanceof Player pilot) {
            Team team = pilot.getTeam();
            if (playerRelation(commander, pilot) != Relation.HOSTILE
                    || StreamSupport.stream(target.getIndirectPassengers().spliterator(), false).anyMatch(occupant -> occupant instanceof Player crewMember
                            && (crewMember.getTeam() != team || playerRelation(commander, crewMember) != Relation.HOSTILE)
                            || player != null && player.isAlliedTo(occupant))) return Relation.FRIENDLY;
            return Relation.HOSTILE;
        }
        if (target.isSpectator()) return Relation.NEUTRAL;
        if (StreamSupport.stream(target.getIndirectPassengers().spliterator(), false).anyMatch(Player.class::isInstance)) return Relation.FRIENDLY;
        if ((player != null && (target.isAlliedTo(player) || player.isAlliedTo(target) || SeekTool.IS_FRIENDLY.test(player, target)))
                || (commander != null && target.getTeam() != null && commander.isAlliedTo(target.getTeam()))
                || SeekTool.IS_FRIENDLY.test(ship, target)) return Relation.FRIENDLY;
        if (target instanceof VehicleEntity vehicle) {
            if (player != null && vehicle.getPassengers().stream().anyMatch(e -> e == player || player.isAlliedTo(e))) return Relation.FRIENDLY;
            if (vehicle.getPassengers().stream().anyMatch(e -> e instanceof Enemy || e.getTags().contains("libertad_hostile"))) return Relation.HOSTILE;
        }
        var team = target.getTeam();
        if (team != null && ("dnv_boss".equals(team.getName()) || "dnv_guardians".equals(team.getName()))
                && DNV_BOSSES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()))) return Relation.HOSTILE;
        if (target instanceof Enemy || target.getTags().contains("libertad_hostile")
                || player != null && target instanceof Mob mob && mob.getTarget() == player) return Relation.HOSTILE;
        return Relation.NEUTRAL;
    }

    static Relation playerRelation(Team commander, Player target) {
        return playerRelation(commander, target.getTeam(), target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !target.isCreative());
    }

    static Relation playerRelation(Team commander, Team target, boolean canBeTargeted) {
        return canBeTargeted && commander != null && target != null && !commander.isAlliedTo(target)
                ? Relation.HOSTILE : Relation.FRIENDLY;
    }

    private void updateScan(ServerLevel level, ServerPlayer player) {
        if (scan == null) {
            SecondaryDirector.Scan token = secondary.beginScan(level.getGameTime());
            if (token == null) return;
            scan = new ScanWork(token);
            for (UUID target : secondary.assignments().values()) scan.consider(level.getEntity(target), player);
        }
        if (!scan.columns(level, player)) return;
        if (scan.geometry(level, player)) {
            boolean finished = !scan.columns.hasNext();
            secondary.publishScan(scan.token, scan.contacts, scan.stations, finished);
            if (finished) scan = null;
            else scan.reset(player, level);
        }
    }

    private void track(ServerLevel level, ServerPlayer player) {
        int admitted = 0;
        int pathBudget = 8 * 24;
        int mounts = poses.length - 6;
        for (int offset = 0; offset < mounts; offset++) {
            int i = 6 + Math.floorMod(ship.tickCount + offset, mounts);
            MountGeometry mount = model.mounts.get(i);
            UUID id = secondary.assignments().get(mount.id());
            if (id == null) {
                if (level.getGameTime() >= holdUntil[i]) poses[i] = advance(i, poses[i], HOME);
                continue;
            }
            Entity target = level.getEntity(id);
            Relation relation = target != null && level.isPositionEntityTicking(target.blockPosition()) && target.getRootVehicle() == target
                    ? relation(target, player) : Relation.NEUTRAL;
            Vec3 position = relation == Relation.HOSTILE ? target.getBoundingBox().getCenter() : Vec3.ZERO;
            Angles goal = relation == Relation.HOSTILE ? solve(i, position, target.getDeltaMovement(), true) : null;
            boolean visible = goal != null && arc(i).allows(goal) && visibleFrom(level, i, goal, position);
            if (!secondary.retain(mount.id(), id, relation, arc(i), goal, visible)) {
                holdUntil[i] = level.getGameTime() + 10;
                continue;
            }
            poses[i] = advance(i, poses[i], goal);
            if (admitted >= 8 || !near(poses[i], goal) || !NativeShells.room(level, mount.barrels().size())) continue;
            SbwGunPort port = port(level, player, i);
            List<BarrelPose> barrels = mount.at(poses[i]);
            if (barrels.stream().anyMatch(barrel -> !port.ready(barrel.gunId()))) continue;
            int blast = i < 16 ? 3 : 1;
            Vec3 motion = target.getDeltaMovement();
            Vec3 muzzle = ShipModel.point(body, ShipModel.vec(barrels.get(0).muzzle()));
            double duration = lead(position.subtract(muzzle), motion).length() / SPEED + 1;
            int steps = (int) Math.ceil(duration);
            if (steps > ShellRange.LIFETIME || steps * barrels.size() > pathBudget) continue;
            pathBudget -= steps * barrels.size();
            if (!safeImpact(level, position.add(motion.scale(duration - 1)), player, target, blast)) continue;
            var directions = new HashMap<String, Vec3>();
            for (var barrel : barrels) {
                Vec3 direction = shotDirection(level, barrel, ship.getGunData(barrel.gunId()));
                if (!clearBore(level, i, barrel, ShipModel.vector(inverse, direction))
                        || !trajectoryClear(level, barrel, direction, target, player, blast, duration)) break;
                directions.put(barrel.gunId(), direction);
            }
            if (directions.size() != barrels.size() || relation(target, player) != Relation.HOSTILE) continue;
            admitted += barrels.size();
            SbwGunPort checked = port(level, player, i, 0, directions);
            if (barrels.size() == 2) SalvoControl.executeTwin(control, mount, arc(i), poses[i], goal, .7, checked);
            else if (checked.isCurrentMaster(control) && checked.clear(barrels.get(0))) checked.tryFire(barrels.get(0));
        }
    }

    private boolean trajectoryClear(ServerLevel level, BarrelPose barrel, Vec3 direction, Entity target,
                                    ServerPlayer player, int blast, double duration) {
        Vec3 muzzle = ShipModel.point(body, ShipModel.vec(barrel.muzzle()));
        Vec3 position = muzzle;
        Vec3 speed = direction.scale(SPEED);
        var limit = new ShellRange(new Point(muzzle.x, muzzle.y, muzzle.z), ShellRange.SECONDARY, ShellRange.LIFETIME);
        List<Entity> nearby = new ArrayList<>();
        for (int tick = 0; tick < duration; tick++) {
            double step = Math.min(1, duration - tick);
            Vec3 movement = speed.scale(step);
            var clipped = limit.next(new Point(position.x, position.y, position.z), new Point(movement.x, movement.y, movement.z), tick);
            Vec3 next = ShipModel.vec(clipped.endpoint());
            if (!clipped.trace() || !blockClear(level, position, next)
                    || WaterEntry.limit(level, ship, position, next) < 1) return false;
            nearby.clear();
            level.getEntities(ENTITY, new AABB(position, next).inflate(.65), e -> true, nearby, 24);
            if (nearby.size() >= 24) return false;
            for (Entity entity : nearby) {
                if (entity == ship || entity == player || entity == target || entity.getRootVehicle() == ship) continue;
                if (relation(entity, player) == Relation.FRIENDLY) {
                    AABB box = entity.getBoundingBox().inflate(.3);
                    if (box.contains(position) || box.clip(position, next).isPresent()) return false;
                }
            }
            position = next;
            speed = speed.add(0, -GRAVITY, 0);
            if (clipped.discardIfMiss()) break;
        }
        return safeImpact(level, position, player, target, blast);
    }

    private boolean visibleFrom(ServerLevel level, int i, Angles angles, Vec3 target) {
        for (var barrel : model.mounts.get(i).at(angles)) {
            Vec3 local = ShipModel.vec(barrel.muzzle());
            Vec3 muzzle = ShipModel.point(body, local);
            if (muzzle.distanceToSqr(target) > ShellRange.SECONDARY * ShellRange.SECONDARY || !clearBore(level, i, barrel)
                    || !blockClear(level, muzzle, target)
                    || model.obstructed(local, ShipModel.point(inverse, target), i, hullInverse)) return false;
        }
        return true;
    }

    private boolean safeImpact(ServerLevel level, Vec3 point, ServerPlayer player, Entity target, int radius) {
        Vec3 min = point.subtract(radius, radius, radius), max = point.add(radius, radius, radius);
        if (LoadedPath.limit(level, min, max) < 1 || LoadedPath.limit(level,
                new Vec3(min.x, min.y, max.z), new Vec3(max.x, max.y, min.z)) < 1) return false;
        List<Entity> nearby = new ArrayList<>();
        level.getEntities(ENTITY, new AABB(min, max), e -> true, nearby, 32);
        return nearby.size() < 32 && nearby.stream().noneMatch(e -> e != target && (e == ship || e == player
                || e.getRootVehicle() == ship || relation(e, player) == Relation.FRIENDLY));
    }

    static boolean searchlightsOn(CompoundTag state) {
        return !state.getBoolean("multicrew") && state.getInt("secondary") == SecondaryDirector.Mode.ACTIVE.ordinal();
    }

    CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < poses.length; i++) {
            tag.putFloat("y" + i, (float) poses[i].yaw());
            tag.putFloat("p" + i, (float) poses[i].pitch());
        }
        for (int i = 0; i < AirDefenseRules.ZONES; i++) {
            tag.putFloat("aa" + i, airDefense.heading(i));
            tag.putFloat("aap" + i, airDefense.pitch(i));
        }
        tag.putInt("secondary", secondary.mode().ordinal());
        tag.putInt("ready", mainReady);
        tag.putInt("blocked", mainBlocked);
        tag.putInt("empty", mainEmpty);
        tag.putInt("reload", mainReload);
        tag.putInt("ammo", ammoType);
        tag.putBoolean("multicrew", crew.multicrew());
        for (int seat = 1; seat <= 2; seat++) {
            Control operator = crew.controller(seat);
            if (operator == null) continue;
            tag.putLong("operator" + seat, operator.seatEpoch());
            tag.putInt("ready" + seat, sideReady[seat - 1]);
            MainBatteryView.write(tag, "SideBattery" + seat, sideView[seat - 1]);
        }
        if (control != null) {
            tag.putLong("operator0", control.seatEpoch());
            MainBatteryView.write(tag, mainView);
        }
        return tag;
    }

    private final class ScanWork {
        final SecondaryDirector.Scan token;
        final Vec3 origin = ShipModel.point(body, Vec3.ZERO);
        final Iterator<BlockPos.MutableBlockPos> columns;
        final Map<UUID, Double> candidates = new HashMap<>();
        int station = 6, cursor, discoveryTicks;
        boolean discovered;
        List<SecondaryDirector.Contact> contacts;
        final List<SecondaryDirector.Station> stations = new ArrayList<>();
        Map<UUID, Angles> solutions = new LinkedHashMap<>();

        ScanWork(SecondaryDirector.Scan token) {
            this.token = token;
            columns = searchColumns(origin, searchRadius);
        }

        void reset(ServerPlayer player, ServerLevel level) {
            var previous = List.copyOf(candidates.keySet());
            candidates.clear();
            stations.clear();
            contacts = null;
            station = 6;
            cursor = discoveryTicks = 0;
            discovered = false;
            for (UUID id : secondary.assignments().values()) consider(level.getEntity(id), player);
            for (UUID id : previous) consider(level.getEntity(id), player);
        }

        boolean columns(ServerLevel level, ServerPlayer player) {
            if (contacts != null) return true;
            int queries = 0;
            for (int i = 0; i < 64 && columns.hasNext() && queries < 6; i++) {
                BlockPos column = columns.next();
                int a = column.getX() << 4, b = column.getZ() << 4;
                double dx = origin.x - Mth.clamp(origin.x, a, a + 16);
                double dz = origin.z - Mth.clamp(origin.z, b, b + 16);
                if (dx * dx + dz * dz > searchRadius * searchRadius) continue;
                if (level.isPositionEntityTicking(new BlockPos(a, ship.getBlockY(), b))) {
                    List<Entity> found = new ArrayList<>();
                    level.getEntities(ENTITY, new AABB(a, origin.y - searchRadius, b, a + 16, origin.y + searchRadius, b + 16),
                            e -> e.getRootVehicle() == e && !(e instanceof Projectile) && e != ship, found, 64);
                    for (Entity entity : found) consider(entity, player);
                    queries++;
                }
            }
            discoveryTicks = Math.min(discoveryTicks + 1, 8);
            if (columns.hasNext() && (!discovered || discoveryTicks < 8)) return false;
            contacts = candidates.entrySet().stream().sorted(Map.Entry.comparingByValue())
                    .map(e -> new SecondaryDirector.Contact(e.getKey(), Relation.HOSTILE, e.getValue())).toList();
            return true;
        }

        void consider(Entity entity, ServerPlayer player) {
            if (entity == null || candidates.containsKey(entity.getUUID()) || relation(entity, player) != Relation.HOSTILE) return;
            double distance = origin.distanceToSqr(entity.getBoundingBox().getCenter());
            boolean assigned = secondary.assignments().containsValue(entity.getUUID());
            if (!assigned && distance > searchRadius * searchRadius) return;
            if (assigned) distance = 0;
            if (candidates.size() >= 32) {
                var worst = candidates.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
                if (worst.getValue() <= distance) return;
                candidates.remove(worst.getKey());
            }
            candidates.put(entity.getUUID(), distance);
            discovered |= !assigned;
        }

        boolean geometry(ServerLevel level, ServerPlayer player) {
            int budget = 24;
            while (station < poses.length && budget > 0) {
                if (cursor >= contacts.size()) {
                    stations.add(new SecondaryDirector.Station(model.mounts.get(station).id(), arc(station), solutions));
                    solutions = new LinkedHashMap<>();
                    station++;
                    cursor = 0;
                    continue;
                }
                UUID id = contacts.get(cursor++).id();
                budget--;
                Entity target = level.getEntity(id);
                if (target == null || target.getRootVehicle() != target || !level.isPositionEntityTicking(target.blockPosition())
                        || relation(target, player) != Relation.HOSTILE) continue;
                Vec3 pos = target.getBoundingBox().getCenter();
                Angles angles = solve(station, pos, target.getDeltaMovement(), true);
                if (angles != null && arc(station).allows(angles) && visibleFrom(level, station, angles, pos)) solutions.put(id, angles);
            }
            return station == poses.length;
        }
    }

    static Iterator<BlockPos.MutableBlockPos> searchColumns(Vec3 origin, double range) {
        var center = new BlockPos(Mth.floor(origin.x) >> 4, 0, Mth.floor(origin.z) >> 4);
        return BlockPos.spiralAround(center, Mth.ceil(range / 16), Direction.EAST, Direction.SOUTH).iterator();
    }
}

package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModItems;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.compat.sbw.LoadedPath;
import com.liucodex.libertad.naval.AirDefenseRules;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.SecondaryDirector.Relation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

/** Ship-owned contact assignment and four staggered virtual muzzles. */
final class ShipAirDefense {
    private static final int MAX_CONTACTS_PER_KIND = 24, CONTACT_TTL = 160;
    private static final int BLOCKED_TICKS = 30, LAST_CLOSE_AIM = 5;
    // Clear the deck and nearby fittings while keeping the plotted horizontal zone centers.
    private static final double MUZZLE_HEIGHT_ABOVE_CENTER = 2;
    private static final EntityTypeTest<Entity, Entity> ENTITY = EntityTypeTest.forClass(Entity.class);

    private final LibertadEntity ship;
    private final ShipModel model;
    private final Zone[] zones = new Zone[AirDefenseRules.ZONES];
    private final Map<UUID, Contact> aircraft = new HashMap<>(), surface = new HashMap<>();
    private final List<Entity> found = new ArrayList<>();
    private final double searchRadius;
    private Iterator<BlockPos.MutableBlockPos> columns;
    private Vec3 scanOrigin;
    private int ammoSlot = -1;
    private long nextAmmoCheck;

    ShipAirDefense(LibertadEntity ship, ShipModel model) {
        this.ship = ship;
        this.model = model;
        double offset = 0;
        for (int i = 0; i < zones.length; i++) {
            final int zone = i;
            var guns = model.aaMounts.stream().filter(mount -> mount.zone() == zone).toList();
            var center = guns.stream().map(ShipModel.AaMount::pivot).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / guns.size());
            zones[i] = new Zone(guns, center);
            offset = Math.max(offset, zones[i].muzzle.length());
        }
        searchRadius = AirDefenseRules.RANGE + offset;
    }

    float heading(int zone) { return zones[zone].heading; }
    float pitch(int zone) { return zones[zone].pitch; }

    void reset() {
        if (columns != null) {
            columns = null;
            aircraft.clear();
            surface.clear();
        }
        for (Zone zone : zones) {
            zone.target = null;
            zone.blockedUntil.clear();
            zone.aimSample = 0;
            zone.surfaceNext = false;
            zone.goal = (float) Math.toDegrees(Math.atan2(zone.center.x, zone.center.z));
            zone.pitchGoal = 0;
        }
    }

    void tick(ServerLevel level, boolean enabled, ServerPlayer operator, Function<Entity, Relation> relation, Angles[] poses) {
        long now = level.getGameTime();
        boolean creative = enabled && ship.hasCreativeAmmoBoxCached();
        int slot = enabled && !creative ? ammo(now) : -1;
        if (!enabled || (!creative && slot < 0)) reset();
        else {
            if (now % AirDefenseRules.PULSE_TICKS == 0) {
                aircraft.values().removeIf(contact -> contact.expires() < now);
                surface.values().removeIf(contact -> contact.expires() < now);
            }
            discover(level, relation, now);
            for (int i = 0; i < zones.length; i++) {
                if (Math.floorMod(now - 2L * i, AirDefenseRules.PULSE_TICKS) == 0)
                    pulse(level, i, slot, operator, relation, poses);
            }
        }
        for (Zone zone : zones) {
            float step = Mth.clamp(Mth.wrapDegrees(zone.goal - zone.heading), -9, 9);
            zone.heading = Mth.wrapDegrees(zone.heading + step);
            zone.pitch += Mth.clamp(zone.pitchGoal - zone.pitch, -6, 6);
        }
    }

    private int ammo(long now) {
        var inventory = ship.getInventory();
        if (ammoSlot >= 0 && inventory.getStackInSlot(ammoSlot).is(ModItems.SMALL_SHELL_AP.get())) return ammoSlot;
        ammoSlot = -1;
        if (now < nextAmmoCheck) return -1;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(ModItems.SMALL_SHELL_AP.get())) return ammoSlot = slot;
        }
        nextAmmoCheck = now + AirDefenseRules.PULSE_TICKS;
        return -1;
    }

    private void discover(ServerLevel level, Function<Entity, Relation> relation, long now) {
        if (columns == null || !columns.hasNext() || scanOrigin.distanceToSqr(ship.position()) > 256) {
            scanOrigin = ship.position();
            columns = ShipFireControl.searchColumns(scanOrigin, searchRadius);
        }
        int queries = 0;
        for (int checked = 0; checked < 64 && queries < 8 && columns.hasNext(); checked++) {
            var column = columns.next();
            int x = column.getX() << 4, z = column.getZ() << 4;
            double dx = scanOrigin.x - Mth.clamp(scanOrigin.x, x, x + 16);
            double dz = scanOrigin.z - Mth.clamp(scanOrigin.z, z, z + 16);
            if (dx * dx + dz * dz > searchRadius * searchRadius
                    || !level.isPositionEntityTicking(new BlockPos(x, ship.getBlockY(), z))) continue;
            found.clear();
            level.getEntities(ENTITY, new AABB(x, scanOrigin.y - searchRadius, z, x + 16, scanOrigin.y + searchRadius, z + 16),
                    e -> e != ship && !(e instanceof Projectile) && e.getRootVehicle() == e, found, 48);
            for (Entity target : found) {
                double distance = target.getBoundingBox().getCenter().distanceToSqr(ship.position());
                if (distance <= searchRadius * searchRadius && relation.apply(target) == Relation.HOSTILE
                        && !underwater(level, aimPoint(target))) {
                    boolean flying = isAircraft(target);
                    (flying ? surface : aircraft).remove(target.getUUID());
                    remember(flying ? aircraft : surface, target.getUUID(), new Contact(distance, now + CONTACT_TTL));
                } else {
                    aircraft.remove(target.getUUID());
                    surface.remove(target.getUUID());
                }
            }
            queries++;
        }
        found.clear();
    }

    private static void remember(Map<UUID, Contact> contacts, UUID id, Contact contact) {
        if (!contacts.containsKey(id) && contacts.size() >= MAX_CONTACTS_PER_KIND) {
            var farthest = contacts.entrySet().stream().max(Map.Entry.comparingByValue(Contact.ORDER)).orElseThrow();
            if (farthest.getValue().distance() <= contact.distance()) return;
            contacts.remove(farthest.getKey());
        }
        contacts.put(id, contact);
    }

    private void pulse(ServerLevel level, int index, int slot, ServerPlayer operator,
                       Function<Entity, Relation> relation, Angles[] poses) {
        Zone zone = zones[index];
        long now = level.getGameTime();
        zone.blockedUntil.values().removeIf(until -> now >= until);
        if (aircraft.isEmpty() && surface.isEmpty()) {
            zone.target = null;
            return;
        }

        var body = ship.getVehicleTransform(1);
        var inverse = new Matrix4d(body).invert();
        Solution shot = assign(level, index, zone, inverse, relation);
        if (shot == null) {
            zone.target = null;
            return;
        }
        UUID targetId = shot.target().getUUID();
        if (!targetId.equals(zone.target) || !shot.closeDefense()) zone.aimSample = 0;
        zone.target = targetId;
        if (zone.aimSample > 0) {
            Solution alternate = solutionAt(index, zone, shot.target(), upperAim(shot.target(), zone.aimSample), inverse);
            if (alternate == null || !alternate.closeDefense() || underwater(level, alternate.aim())) {
                rejectLine(zone, shot, now);
                return;
            }
            shot = alternate;
        }

        Vec3 direction = shot.local().subtract(zone.muzzle);
        zone.goal = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        zone.pitchGoal = (float) Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        if (Math.abs(Mth.wrapDegrees(zone.goal - zone.heading)) > 12 || Math.abs(zone.pitchGoal - zone.pitch) > 12) return;

        // This zone gets one virtual line per slot. Try another exposed point on a later slot.
        Vec3 muzzle = ShipModel.point(body, zone.muzzle);
        if (model.obstructed(zone.muzzle, shot.local(), -1, model.inverses(poses)) || !clearPath(level, muzzle, shot.aim())) {
            rejectLine(zone, shot, now);
            return;
        }

        if (ship.getEnergy() <= 0 || relation.apply(shot.target()) != Relation.HOSTILE
                || (!ship.hasCreativeAmmoBoxCached() && (slot < 0
                || !ship.getInventory().extractItem(slot, 1, false).is(ModItems.SMALL_SHELL_AP.get())))) return;
        zone.surfaceNext = false;
        shot.target().hurt(ModDamageTypes.causeProjectileHitDamage(level.registryAccess(), ship,
                operator == null ? ship : operator), AirDefenseRules.PULSE_DAMAGE);

        Angles angles = new Angles(zone.heading, zone.pitch);
        for (var gun : zone.guns) {
            var pose = model.aaTransform(gun, angles, true);
            Vec3 local = ShipModel.point(pose, gun.muzzle());
            Vec3 forward = new Vec3(Math.sin(gun.restYaw()), 0, Math.cos(gun.restYaw()));
            ShipMuzzleEffects.AIR_DEFENSE.flash(level, ShipModel.point(body, local),
                    ShipModel.vector(body, ShipModel.vector(pose, forward)).normalize());
        }
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, Libertad.AIR_DEFENSE_FIRE.get(), SoundSource.BLOCKS,
                AirDefenseRules.FIRE_VOLUME, 1);
    }

    private static void rejectLine(Zone zone, Solution shot, long now) {
        if (shot.closeDefense() && zone.aimSample < LAST_CLOSE_AIM) {
            zone.aimSample++;
            return;
        }
        zone.blockedUntil.put(shot.target().getUUID(), now + BLOCKED_TICKS);
        zone.target = null;
        zone.aimSample = 0;
        zone.surfaceNext = isAircraft(shot.target());
    }

    private Solution assign(ServerLevel level, int index, Zone zone, Matrix4d inverse, Function<Entity, Relation> relation) {
        if (zone.surfaceNext) {
            Solution ground = select(level, index, zone, inverse, relation, surface);
            if (ground != null) return ground;
            zone.surfaceNext = false;
        }
        Solution air = select(level, index, zone, inverse, relation, aircraft);
        return air != null ? air : select(level, index, zone, inverse, relation, surface);
    }

    private Solution select(ServerLevel level, int index, Zone zone, Matrix4d inverse,
                            Function<Entity, Relation> relation, Map<UUID, Contact> contacts) {
        Solution best = null;
        for (UUID id : contacts.keySet()) {
            if (zone.blockedUntil.containsKey(id)) continue;
            Entity target = level.getEntity(id);
            if (target == null || target.getRootVehicle() != target || !level.isPositionEntityTicking(target.blockPosition())) continue;

            Solution candidate = solutionAt(index, zone, target, aimPoint(target), inverse);
            if (candidate == null) continue;
            boolean incumbent = id.equals(zone.target);
            if (!incumbent && best != null) {
                int distance = Double.compare(candidate.distance(), best.distance());
                if (distance > 0 || (distance == 0 && id.compareTo(best.target().getUUID()) >= 0)) continue;
            }
            if (relation.apply(target) != Relation.HOSTILE || underwater(level, candidate.aim())) continue;
            if (incumbent) return candidate;
            best = candidate;
        }
        return best;
    }

    private static Solution solutionAt(int index, Zone zone, Entity target, Vec3 aim, Matrix4d inverse) {
        Vec3 local = ShipModel.point(inverse, aim);
        Vec3 delta = local.subtract(zone.muzzle);
        boolean closeDefense = AirDefenseRules.nearSuperstructure(local.x, local.y, local.z);
        if (!AirDefenseRules.covers(index, local.x, delta.x, delta.y, delta.z, closeDefense)) return null;
        return new Solution(target, aim, local, delta.lengthSqr(), closeDefense);
    }

    private static Vec3 upperAim(Entity target, int sample) {
        AABB bounds = target.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double top = bounds.maxY - Math.min(.05, bounds.getYsize() * .1);
        double x = bounds.getXsize() * .45;
        double z = bounds.getZsize() * .45;
        return switch (sample) {
            case 2 -> new Vec3(center.x + x, top, center.z);
            case 3 -> new Vec3(center.x - x, top, center.z);
            case 4 -> new Vec3(center.x, top, center.z + z);
            case 5 -> new Vec3(center.x, top, center.z - z);
            default -> new Vec3(center.x, top, center.z);
        };
    }

    private static boolean isAircraft(Entity target) {
        if (target instanceof VehicleEntity vehicle) {
            var engine = vehicle.computed().getEngineType();
            return !vehicle.onGround() && (engine == EngineType.HELICOPTER || engine == EngineType.AIRCRAFT || engine == EngineType.AIRSHIP);
        }
        return !target.onGround() && (target instanceof FlyingMob || target instanceof FlyingAnimal
                || target instanceof Player player && player.isFallFlying());
    }

    private static boolean underwater(ServerLevel level, Vec3 point) {
        var pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) return true;
        var fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && point.y < pos.getY() + fluid.getHeight(level, pos);
    }

    private static Vec3 aimPoint(Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        return new Vec3(center.x, Math.max(center.y, target.getEyeY()), center.z);
    }

    private boolean clearPath(ServerLevel level, Vec3 from, Vec3 to) {
        return LoadedPath.limit(level, from, to) == 1
                && level.getWorldBorder().isWithinBounds(BlockPos.containing(to))
                && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, ship)).getType() == HitResult.Type.MISS;
    }

    private static final class Zone {
        final List<ShipModel.AaMount> guns;
        final Vec3 center, muzzle;
        final Map<UUID, Long> blockedUntil = new HashMap<>();
        UUID target;
        int aimSample;
        boolean surfaceNext;
        float heading, goal, pitch, pitchGoal;

        Zone(List<ShipModel.AaMount> guns, Vec3 center) {
            this.guns = guns;
            this.center = center;
            this.muzzle = center.add(0, MUZZLE_HEIGHT_ABOVE_CENTER, 0);
            this.heading = this.goal = (float) Math.toDegrees(Math.atan2(center.x, center.z));
        }
    }

    private record Contact(double distance, long expires) {
        static final Comparator<Contact> ORDER = Comparator.comparingDouble(Contact::distance);
    }

    private record Solution(Entity target, Vec3 aim, Vec3 local, double distance, boolean closeDefense) {}
}

package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;

/** Player-operated heavy tank. Native SBW owns chassis physics, crew, inventory and GunData persistence. */
public class DnvApocalypseEntity extends VehicleEntity implements DnvNativeShotAdmission, DnvNativeRemoteShotGuard,
        com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget,
        com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor {
    @Override public Tier coldArmorTier() { return Tier.HEAVY; }
    public static final String TYPE_ID = "doggievehicle:apocalypse";
    private static final EntityDataAccessor<Integer> NEXT_MAIN = integer();
    private static final EntityDataAccessor<Integer> NEXT_ROCKET = integer();
    private static final EntityDataAccessor<Integer> PREFERRED_AMMO = integer();
    private static final EntityDataAccessor<Float> ROCKET_ELEVATION = scalar();
    private static final EntityDataAccessor<Float> TRACK_L = scalar();
    private static final EntityDataAccessor<Float> TRACK_R = scalar();
    private static final EntityDataAccessor<Float> WHEEL_L = scalar();
    private static final EntityDataAccessor<Float> WHEEL_R = scalar();
    private final ApocalypseArmament armament = new ApocalypseArmament(this);
    private final ApocalypseTargeting targeting = new ApocalypseTargeting(this);
    private final ApocalypseCrushing crushing = new ApocalypseCrushing();
    private float oldRocket;
    private float oldTrackL;
    private float oldTrackR;
    private float oldWheelL;
    private float oldWheelR;
    private List<OBB> nativeBoxes;
    private List<OBB> damageBoxes;

    public DnvApocalypseEntity(EntityType<? extends DnvApocalypseEntity> type, Level level) {
        super(type, level);
    }

    private static EntityDataAccessor<Integer> integer() {
        return SynchedEntityData.defineId(DnvApocalypseEntity.class, EntityDataSerializers.INT);
    }
    private static EntityDataAccessor<Float> scalar() {
        return SynchedEntityData.defineId(DnvApocalypseEntity.class, EntityDataSerializers.FLOAT);
    }

    @Override protected void defineSynchedData() {
        super.m_8097_();
        entityData.define(NEXT_MAIN, 0);
        entityData.define(NEXT_ROCKET, 0);
        entityData.define(PREFERRED_AMMO, 0);
        entityData.define(ROCKET_ELEVATION, 0F);
        entityData.define(TRACK_L, 0F);
        entityData.define(TRACK_R, 0F);
        entityData.define(WHEEL_L, 0F);
        entityData.define(WHEEL_R, 0F);
    }

    @Override public GunData getGunData(int seat, int index) {
        String name = getGunName(seat, index);
        return name == null ? null : super.getGunData(name);
    }
    @Override public String getGunName(int seat, int index) {
        if (seat != 0) return null;
        return switch (index) {
            case 0 -> entityData.get(NEXT_MAIN) == 0 ? "MainL" : "MainR";
            case 1 -> entityData.get(NEXT_ROCKET) == 0 ? "RocketL" : "RocketR";
            case 2 -> "Missiles";
            default -> null;
        };
    }
    void advanceMain() { entityData.set(NEXT_MAIN, 1 - entityData.get(NEXT_MAIN)); }
    void advanceRocket() { entityData.set(NEXT_ROCKET, 1 - entityData.get(NEXT_ROCKET)); }
    public int preferredAmmo() { return entityData.get(PREFERRED_AMMO); }
    void setPreferredAmmo(int value) { entityData.set(PREFERRED_AMMO, value & 1); }
    public boolean requestAmmoSelection(GunData gun, int index) { return armament.requestAmmo(gun, index); }
    public ApocalypseTargeting targeting() { return targeting; }
    boolean muzzleClear(String name, Vec3 muzzle) { return armament.muzzleClear(name, muzzle); }

    @Override public boolean canShoot(LivingEntity actor) {
        if (actor != getFirstPassenger() || actor == null || !actor.isAlive() || actor.isSpectator() || isWreck()) return false;
        return level().isClientSide ? super.canShoot(actor) : armament.ready(actor);
    }
    @Override public boolean permitsNativeClientFire(Entity actor, Entity lockedTarget, boolean locked, GunData gun) {
        if (!(actor instanceof Player player) || player != getFirstPassenger() || !player.isAlive() || player.isSpectator()
                || isRemoved() || isWreck() || gun == null || gun != getGunData(0) || !poseClear()) return false;
        int group = getWeaponIndex(0);
        String name = getGunName(0);
        return group >= 0 && group < 3 && name != null && muzzleClear(name, getShootPos(name, 1))
                && (group != 2 || locked && targeting.valid(lockedTarget, player));
    }
    @Override public void vehicleShoot(LivingEntity actor, UUID target, Vec3 point) { armament.fire(actor, null, target); }
    @Override public void vehicleShoot(LivingEntity actor, String name, Vec3 point) { armament.fire(actor, name, null); }
    @Override public void vehicleShoot(LivingEntity actor, String name, UUID target, Vec3 point) { armament.fire(actor, name, target); }
    void submitNativeShot(LivingEntity actor, String name, UUID target, Vec3 point) { super.vehicleShoot(actor, name, target, point); }
    @Override public Runnable guardNativeShot(Runnable nativeWork) { return armament.guard(nativeWork); }
    @Override public boolean deferNativeShotEffects() { return true; }
    @Override public boolean deferNativeClientSound() { return true; }
    @Override public void onNativeShotQueueResult(boolean accepted) { armament.queueResult(accepted); }
    @Override public void afterShoot(GunData gun, Vec3 direction) {
        super.afterShoot(gun, direction);
        if (level() instanceof ServerLevel server && (gun == getGunData("MainL") || gun == getGunData("MainR"))) {
            String name = gun == getGunData("MainL") ? "MainL" : "MainR";
            ParticleTool.spawnBigCannonMuzzleParticles(direction, getShootPos(name, 1), server, this);
        }
    }
    @Override public boolean admitNativeProjectile(ServerLevel level, Entity projectile, ShootParameters parameters) {
        return armament.admit(level, projectile, parameters);
    }
    @Override public boolean nativeProjectileAccepted(ShootParameters parameters) { return armament.accepted(parameters); }

    @Override public void travel() {
        float beforeYaw = getYRot();
        // Native tracked power approaches its bound exponentially. Sprint cannot turn this heavy chassis into a light tank.
        setSprintInputDown(false);
        super.travel();
        float turn = Mth.clamp(ApocalypseMotion.degrees(getYRot() - beforeYaw), -hullTurnLimit(), hullTurnLimit());
        setYRot(beforeYaw + turn);
        Vec3 velocity = getDeltaMovement();
        double cap = velocity.dot(getViewVector(1)) < 0 ? reverseSpeedLimit() : forwardSpeedLimit();
        double horizontal = velocity.horizontalDistance();
        if (onGround() && horizontal > cap) setDeltaMovement(velocity.x * cap / horizontal, velocity.y, velocity.z * cap / horizontal);
    }

    @Override public float getTurretTurnXSpeed() { return ApocalypseMotion.ELEVATION_SPEED; }
    protected double forwardSpeedLimit() { return ApocalypseMotion.FORWARD_SPEED; }
    protected double reverseSpeedLimit() { return ApocalypseMotion.REVERSE_SPEED; }
    protected float hullTurnLimit() { return ApocalypseMotion.HULL_TURN; }
    @Override public void crushEntities() {
        // Keep upstream high-speed collision physics at .3 blocks/tick; lower-speed contact uses DNV's hull sweep.
        if (getDeltaMovement().horizontalDistanceSqr() >= .09) super.crushEntities();
        else crushing.tick(this);
    }
    @Override public float getTurretTurnYSpeed() { return ApocalypseMotion.TURRET_TURN; }
    @Override public float getEngineSoundVolume() {
        // Keep the native engine loop audible at idle; native EngineSound still owns pitch, distance and stop conditions.
        return engineRunning() ? Math.max(.16F, super.getEngineSoundVolume()) : 0;
    }
    @Override public void turretTurnSound(float diffX, float diffY, float pitch) {
        super.turretTurnSound(diffX * 2, diffY * 2, .65F);
    }
    @Override public float getTurretMinPitch() { return ApocalypseMotion.floor(getTurretYRot(), false); }
    @Override public float getTurretMaxPitch() { return 25; }
    @Override public void adjustTurretAngle() {
        if (isWreck()) return;
        Entity driver = getNthEntity(getTurretControllerIndex());
        Vec3 barrel = getBarrelPosition();
        if (driver == null || barrel == null) return;
        Vec3 origin = ApocalypseRig.transformPoint(getTurretTransform(1), barrel);
        Vec3 aim = getBoundingBox().getCenter().add(driver.getViewVector(1).scale(512));
        // The SBW static adjust helper bypasses this subclass's joint-aware virtual aim method.
        turretAutoAimFromVector(aim.subtract(origin));
    }
    @Override public void turretAutoAimFromVector(Vec3 direction) {
        float priorYaw = getTurretYRot(), priorMain = -getTurretXRot(), priorRocket = entityData.get(ROCKET_ELEVATION);
        super.turretAutoAimFromVector(direction);
        float wantedYaw = getTurretYRot();
        float main = ApocalypseMotion.approach(priorMain,
                ApocalypseMotion.elevation(-getTurretXRot(), wantedYaw, false), getTurretTurnXSpeed());
        float rocket = ApocalypseMotion.approach(priorRocket,
                ApocalypseMotion.elevation(-getTurretXRot(), wantedYaw, true), getTurretTurnXSpeed());
        if (!ApocalypseMotion.cleared(wantedYaw, main, rocket)) {
            setTurretYRot(priorYaw); // Raise first; no instant pitch snap through the rear exhaust/deck.
            setTurretYRotLock(0);
        }
        setTurretXRot(-ApocalypseMotion.elevation(main, getTurretYRot(), false));
        entityData.set(ROCKET_ELEVATION, ApocalypseMotion.elevation(rocket, getTurretYRot(), true));
    }
    public boolean poseClear() {
        return ApocalypseMotion.cleared(getTurretYRot(), -getTurretXRot(), entityData.get(ROCKET_ELEVATION));
    }
    public float rocketElevation(float partial) { return Mth.lerp(partial, oldRocket, entityData.get(ROCKET_ELEVATION)); }
    public float recoil(String weapon, float partial) {
        GunData gun = getGunData(weapon);
        if (gun == null) return 0;
        float value = Mth.clamp((gun.shootAnimationTimer.get() - partial) / 14F, 0, 1);
        return value * value * (3 - 2 * value);
    }

    public Matrix4d groupTransform(String group, float partial) {
        double recoil = group.equals("MainL") || group.equals("MainR") ? recoil(group, partial) : 0;
        return getVehicleTransform(partial).mul(ApocalypseRig.INSTANCE.local(group, getTurretYaw(partial),
                -getTurretPitch(partial), rocketElevation(partial), recoil));
    }
    @Override public Matrix4d getTransformFromString(String name, float partial) {
        return name != null && name.startsWith("Apocalypse") ? groupTransform(name.substring(10), partial)
                : super.getTransformFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name, float partial) {
        return name != null && name.startsWith("Apocalypse") ? groupTransform(name.substring(10), partial).getNormalizedRotation(new Quaterniond())
                : super.getRotationFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name) {
        return name != null && name.startsWith("Apocalypse") ? getRotationFromString(name, 1) : super.getRotationFromString(name);
    }

    private String muzzleName(String weapon) {
        GunData gun = getGunData(weapon);
        int index = gun == null ? 0 : Math.floorMod(gun.fireIndex.get(), 4);
        return switch (weapon) {
            case "MainL" -> "muzzle_main_l";
            case "MainR" -> "muzzle_main_r";
            case "RocketL" -> "muzzle_rocket_l_0" + (index + 1);
            case "RocketR" -> "muzzle_rocket_r_0" + (index + 1);
            case "Missiles" -> "muzzle_missile_" + (index < 2 ? "l" : "r") + "_0" + (index % 2 + 1);
            default -> throw new IllegalArgumentException("Unknown Apocalypse weapon " + weapon);
        };
    }
    private static String weaponGroup(String weapon) {
        return weapon.startsWith("Main") ? weapon : weapon.startsWith("Rocket") ? "Rocket" : "Turret";
    }
    @Override public Vec3 getShootPos(String weapon, float partial) {
        var muzzle = ApocalypseRig.INSTANCE.muzzles.get(muzzleName(weapon));
        return ApocalypseRig.transformPoint(groupTransform(weaponGroup(weapon), partial), muzzle.position());
    }
    @Override public Vec3 getShootPos(Entity actor, float partial) {
        String name = getGunName(getSeatIndex(actor));
        return name == null ? getEyePosition(partial) : getShootPos(name, partial);
    }
    @Override public Vec3 getShootPosForHud(Entity actor, float partial) { return getShootPos(actor, partial); }
    @Override public Vec3 getShootVec(String weapon, float partial) {
        var muzzle = ApocalypseRig.INSTANCE.muzzles.get(muzzleName(weapon));
        return ApocalypseRig.transformDirection(groupTransform(weaponGroup(weapon), partial), muzzle.direction());
    }
    @Override public Vec3 getShootVec(Entity actor, float partial) {
        String name = getGunName(getSeatIndex(actor));
        return name == null ? getViewVector(partial) : getShootVec(name, partial);
    }
    @Override public Vec3 getShootDirectionForHud(Entity actor, float partial) {
        return getWeaponIndex(0) == 2 && actor != null ? actor.getViewVector(partial) : getShootVec(actor, partial);
    }
    @Override public Vec3 getSeekVec(Entity actor, float partial) {
        return actor == null ? getViewVector(partial) : actor.getViewVector(partial);
    }
    public Vec3 lockOrigin() {
        return ApocalypseRig.transformPoint(groupTransform("Turret", 1), ApocalypseRig.INSTANCE.yaw.add(0, 1.4, 0));
    }
    Vec3 firingPivot(String name) {
        var rig = ApocalypseRig.INSTANCE;
        Vec3 pivot = name.startsWith("Main") ? rig.main : name.startsWith("Rocket") ? rig.rocket : rig.yaw.add(0, 1.4, 0);
        if (name.endsWith("R")) pivot = new Vec3(-pivot.x, pivot.y, pivot.z);
        return ApocalypseRig.transformPoint(groupTransform(weaponGroup(name), 1), pivot);
    }

    /** Physics-only OBB remains available to native vMove but cannot fill damage/picking gaps. */
    @Override public List<OBB> getOBBs() {
        List<OBB> source = super.getOBBs();
        if (source != nativeBoxes) {
            nativeBoxes = source;
            damageBoxes = source.stream().filter(box -> box.part != OBB.Part.COLLISION).toList();
        }
        return damageBoxes;
    }
    @Override public OBB getCollisionOBB() {
        var info = getCollisionOBBInfo();
        return info == null ? null : info.getOBB();
    }

    @Override public void tick() {
        oldRocket = entityData.get(ROCKET_ELEVATION);
        oldTrackL = entityData.get(TRACK_L);
        oldTrackR = entityData.get(TRACK_R);
        oldWheelL = entityData.get(WHEEL_L);
        oldWheelR = entityData.get(WHEEL_R);
        Vec3 previous = position();
        float previousYaw = getYRot();
        armament.prepareReloads();
        super.tick();
        if (isRemoved()) return;
        if (!level().isClientSide) {
            double yawDelta = Math.toRadians(ApocalypseMotion.degrees(getYRot() - previousYaw));
            double midYaw = Math.toRadians(previousYaw) + yawDelta * .5;
            Vec3 delta = position().subtract(previous);
            double distance = -Math.sin(midYaw) * delta.x + Math.cos(midYaw) * delta.z;
            if (delta.lengthSqr() < 16 && Math.abs(yawDelta) < .5) {
                gear(TRACK_L, WHEEL_L, distance - 2.075 * yawDelta);
                gear(TRACK_R, WHEEL_R, distance + 2.075 * yawDelta);
            }
        }
        updateOBB();
        refreshBoundingBoxFromOBBs();
        emitExhaust(position().subtract(previous));
    }
    private void emitExhaust(Vec3 travelled) {
        // Client projection only: two particles every third tick; no scans, packets or idle/wreck emission.
        if (!level().isClientSide || isWreck() || !engineRunning() || tickCount % 3 != 0
                || travelled.horizontalDistanceSqr() < .000225 || travelled.lengthSqr() > 4) return;
        Matrix4d body = getVehicleTransform(1);
        for (var outlet : ApocalypseRig.INSTANCE.exhausts) {
            Vec3 at = ApocalypseRig.transformPoint(body, outlet.position());
            Vec3 velocity = ApocalypseRig.transformDirection(body, outlet.direction()).scale(.06).add(0, .025, 0);
            level().addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
        }
    }
    private void gear(EntityDataAccessor<Float> track, EntityDataAccessor<Float> wheel, double distance) {
        var rig = ApocalypseRig.INSTANCE;
        entityData.set(track, (float) ApocalypseMotion.wrap(entityData.get(track) + distance / rig.tracks.get(0).length(), 1));
        entityData.set(wheel, (float) ApocalypseMotion.wrap(entityData.get(wheel) + Math.toDegrees(distance / rig.wheels.get(0).radius()), 360));
    }
    public float trackPhase(boolean left, float partial) {
        return ApocalypseMotion.interpolateCycle(left ? oldTrackL : oldTrackR, entityData.get(left ? TRACK_L : TRACK_R), partial, 1);
    }
    public float wheelAngle(boolean left, float partial) {
        return ApocalypseMotion.interpolateCycle(left ? oldWheelL : oldWheelR, entityData.get(left ? WHEEL_L : WHEEL_R), partial, 360);
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty() && super.m_7310_(passenger);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.m_7380_(tag);
        CompoundTag custom = new CompoundTag();
        custom.putInt("Schema", 1);
        custom.putInt("NextMain", entityData.get(NEXT_MAIN));
        custom.putInt("NextRocket", entityData.get(NEXT_ROCKET));
        custom.putInt("PreferredAmmo", preferredAmmo());
        custom.putFloat("RocketElevation", entityData.get(ROCKET_ELEVATION));
        armament.save(custom);
        tag.put("DnvApocalypse", custom);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag);
        CompoundTag custom = tag.getCompound("DnvApocalypse");
        entityData.set(NEXT_MAIN, custom.getInt("NextMain") & 1);
        entityData.set(NEXT_ROCKET, custom.getInt("NextRocket") & 1);
        setPreferredAmmo(custom.getInt("PreferredAmmo"));
        entityData.set(ROCKET_ELEVATION, ApocalypseMotion.elevation(custom.getFloat("RocketElevation"), getTurretYRot(), true));
        oldRocket = entityData.get(ROCKET_ELEVATION);
        armament.load(custom);
    }
}

package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;

/** Server-owned hover velocity; native SBW owns movement, collision, inventory and crew. */
public final class DnvBlizzardEntity extends VehicleEntity implements com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget,
        com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission,
        com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard,
        com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor,
        com.liucodex.doggiesnewvehicle.api.DnvVehicleModeSwitch {
    @Override public float ordinaryArmorReduction() { return .55F; }
    @Override public Tier coldArmorTier() { return Tier.MEDIUM; }
    private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.BOOLEAN);
    private final BlizzardColdBeam beam = new BlizzardColdBeam(this);
    private final BlizzardMissileArmament missiles = new BlizzardMissileArmament(this);
    private static final EntityDataAccessor<Integer> NEXT_PORT = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TV_ACTIVE = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID tvMissile;
    private net.minecraft.server.level.ServerPlayer tvOperator;
    private static final EntityDataAccessor<CompoundTag> PRESENTATION = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final BlizzardPresentation presentation = new BlizzardPresentation();
    private boolean motionActive;
    private BlizzardPresentation.Pulse latestPulse;
    private java.util.Map<Long, BlizzardPresentation.Laser> recentLasers;
    private java.util.Map<Long,BlizzardPresentation.Pulse> recentPulses;
    private List<OBB> nativeBoxes, damageBoxes;
    private int jumpCooldown, ascentTicks, jumpHoldTicks;
    private double jumpTarget;
    private boolean previousJumpKey;
    private boolean fluidSupported;
    private float steeringRate;
    private static final EntityDataAccessor<Boolean> MODE2 = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEPLOYED = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HATCH = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LASER = SynchedEntityData.defineId(DnvBlizzardEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private int modeCooldown, previousHatch;
    private float pitchRate, rollRate;
    private double groundPitch, groundRoll;
    private int driveTicks, previousDrive;

    public DnvBlizzardEntity(EntityType<? extends DnvBlizzardEntity> type, Level level) { super(type, level); }
    @Override public void handlePartHealth() {
        // Native hull repair does not finish the separate 95%-threshold part repair.
        // Reconcile the same native slots, including a previously saved full-hull repair.
        float maximum = getMaxHealth();
        if (!level().isClientSide && !isRemoved() && !isWreck() && Float.isFinite(maximum) && maximum > 0
                && Float.isFinite(getHealth()) && getHealth() >= maximum) {
            setTurretHealth(getTurretMaxHealth()); setTurretDamaged(false);
            setLeftWheelHealth(getLeftWheelMaxHealth()); setLeftWheelDamaged(false);
            setRightWheelHealth(getRightWheelMaxHealth()); setRightWheelDamaged(false);
            setMainEngineHealth(getMainEngineMaxHealth()); setMainEngineDamaged(false);
            setSubEngineHealth(getSubEngineMaxHealth()); setSubEngineDamaged(false);
            setTurretBurned(false); setTurretBurnTimer(0);
        }
        super.handlePartHealth();
    }
    @Override protected void defineSynchedData() {
        super.m_8097_();
        entityData.define(FIRING, false);
        entityData.define(NEXT_PORT, 0);
        entityData.define(TV_ACTIVE, false);
        entityData.define(PRESENTATION, new CompoundTag());
        entityData.define(MODE2, false);
        entityData.define(DEPLOYED, false);
        entityData.define(HATCH, 0);
        entityData.define(LASER, new CompoundTag());
    }
    public boolean mode2() { return entityData.get(MODE2); }
    public boolean mode2Ready() { return mode2() && entityData.get(DEPLOYED) && entityData.get(HATCH) == 20; }
    public boolean hovering() { return !mode2() && entityData.get(HATCH) == 0; }
    public float hatchOpening(float partial) { return Mth.lerp(partial, previousHatch, entityData.get(HATCH)) / 20F; }
    public CompoundTag laserSnapshot() { return entityData.get(LASER); }
    void clearLaser() { entityData.set(LASER, new CompoundTag()); }
    @Override public boolean requestMode(Player actor) {
        if (level().isClientSide || actor != getFirstPassenger() || !driverReady() || modeCooldown > 0 || ascentTicks > 0 || jumpHoldTicks > 0) return false;
        if (!mode2()) { supportHeight(); if (fluidSupported) return false; }
        entityData.set(MODE2, !mode2());
        beam.modeChanged();
        entityData.set(DEPLOYED, false);
        modeCooldown = 10; setFiring(false); clearLaser();
        return true;
    }
    private void deploymentTravel(double ground) {
        boolean support = Double.isFinite(ground);
        boolean landed = mode2() && support && getY() >= ground - .1 && getY() <= ground + .08;
        entityData.set(DEPLOYED, landed);
        int hatch = entityData.get(HATCH);
        entityData.set(HATCH, Mth.clamp(hatch + (landed && powered() ? 1 : -1), 0, 20));
        steeringRate = pitchRate = rollRate = 0; driveTicks = previousDrive = 0;
        // Native baseTick performs the sole collision move. No teleport or forced chunk load.
        double vertical = support ? Mth.clamp((ground - getY()) * .35, -.22, .06)
                : Math.max(-.35, getDeltaMovement().y - .04);
        setDeltaMovement(new Vec3(0, landed ? 0 : vertical, 0)); setPower(0);
    }
    boolean traceLaser(boolean damage) {
        Vec3 from = getShootPos("ColdBeam", 1), end = from.add(getShootVec("ColdBeam", 1).scale(BlizzardRules.MODE2_RANGE));
        if (!BlizzardLoadedPath.loaded(this, from, end)) return false;
        var wall = level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        if (!damage) return true; // Validate held fire without rewriting an accepted shot's world-space trail.
        Entity owner = getFirstPassenger();
        if (damage) {
            var key = net.minecraft.resources.ResourceKey.<net.minecraft.world.damagesource.DamageType>create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "blizzard_cold"));
            var source = new net.minecraft.world.damagesource.DamageSource(level().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key), this, owner);
            for (var hit : BlizzardTrace.piercing(level(), from, end, target -> target.isAlive() && target.isPickable()
                    && !target.isSpectator() && target != this && target != owner && target.getRootVehicle() != this)) {
                com.atsuishio.superbwarfare.tools.DamageHandlerKt.forceHurt(hit.entity(), source, BlizzardColdDamage.amount(hit.entity(), true));
                hit.entity().invulnerableTime = 0; // Same accepted-ray policy as SBW's laser defense tower.
            }
        }
        var tag = new CompoundTag(); tag.putLong("Tick", level().getGameTime());
        tag.putDouble("Ox", from.x); tag.putDouble("Oy", from.y); tag.putDouble("Oz", from.z);
        tag.putDouble("Ex", end.x); tag.putDouble("Ey", end.y); tag.putDouble("Ez", end.z);
        entityData.set(LASER, tag);
        if (recentLasers == null) recentLasers = new java.util.LinkedHashMap<>();
        recentLasers.put(level().getGameTime(), new BlizzardPresentation.Laser(level().getGameTime(), from, end));
        while (recentLasers.size() > 8) recentLasers.remove(recentLasers.keySet().iterator().next());
        if (damage) latestPulse = new BlizzardPresentation.Pulse(level().getGameTime(), from, Vec3.ZERO, 0);
        return true;
    }
    public boolean firing() { return entityData.get(FIRING); }
    void setFiring(boolean value) { entityData.set(FIRING, value); }
    /** Existing SBW release intent also stops this custom continuous-input consumer, including empty hands. */
    @Override public void releaseNativeFire(Entity actor) {
        if (level().isClientSide || actor == null || actor != getFirstPassenger()) return;
        setFireInputDown(false); beam.release();
    }
    boolean emitColdBolt() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return false;
        var bolt = BlizzardContent.BOLT.get().create(server);
        if (bolt == null) return false;
        bolt.setOwner(getFirstPassenger());
        bolt.setPos(getShootPos("ColdBeam", 1));
        bolt.setDeltaMovement(getShootVec("ColdBeam", 1).scale(BlizzardRules.BOLT_SPEED));
        if (!server.addFreshEntity(bolt)) return false;
        long shotTick = level().getGameTime();
        latestPulse = new BlizzardPresentation.Pulse(shotTick, bolt.position(), bolt.getDeltaMovement(), BlizzardRules.RANGE);
        if(recentPulses==null)recentPulses=new java.util.LinkedHashMap<>();
        recentPulses.put(shotTick,latestPulse);
        while(recentPulses.size()>8)recentPulses.remove(recentPulses.keySet().iterator().next());
        bolt.presentationEnd = distance -> endPulse(shotTick,distance);
        return true;
    }
    /** Impact updates its own accepted shot, even after another round has left the muzzle. */
    private void endPulse(long shotTick,double distance){
        if(recentPulses==null||!Double.isFinite(distance)||distance<0)return;
        var pulse=recentPulses.get(shotTick);if(pulse==null)return;
        var ended=new BlizzardPresentation.Pulse(shotTick,pulse.origin(),pulse.velocity(),Math.min(distance,pulse.distance()));
        recentPulses.put(shotTick,ended);
        if(latestPulse!=null&&latestPulse.tick()==shotTick)latestPulse=ended;
    }
    public boolean powered() { return isAlive() && !isWreck() && (getEnergy() > 0 || ascentTicks > 0 || jumpHoldTicks > 0); }
    public boolean presentationPowered() {
        var pose=presentation.sample(1F);
        return isAlive()&&!isWreck()&&(level().isClientSide?pose!=null&&pose.motionPowered():motionActive);
    }
    public boolean hasTv() { return entityData.get(TV_ACTIVE); }
    boolean driverReady() {
        return powered() && !hasTv() && getFirstPassenger() instanceof Player player && player.isAlive() && !player.isSpectator();
    }

    // SBW inputs still authenticate the passenger; the server owns motion and every client consumes interpolation.
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }

    @Override public void adjustTurretAngle() { if (!level().isClientSide && !hasTv()) super.adjustTurretAngle(); }
    @Override public void processInput(short keys) { super.processInput(hasTv() ? (short) 0 : keys); }
    @Override public void handleClientSync() {
        if (!level().isClientSide) { super.handleClientSync(); return; }
        var previousPose = presentation.sample(1F);
        presentation.accept(entityData.get(PRESENTATION));
        presentation.acceptLaser(entityData.get(LASER));
        previousHatch = entityData.get(HATCH);
        var pose = presentation.advance();
        if (pose != null) {
            float previousYaw=getTurretYRot(), previousPitch=getTurretXRot();
            setPos(pose.position()); setYRot(pose.yaw()); setXRot(pose.pitch()); setZRot(pose.roll());
            setTurretYRot(pose.turretYaw()); setTurretXRot(pose.turretPitch());
            if(previousPose!=null&&pose.tick()-previousPose.tick()<=20
                    &&pose.position().distanceToSqr(previousPose.position())<=64*64
                    &&(pose.motionPowered()||mode2Ready()))turretTurnSound(Mth.wrapDegrees(pose.turretPitch()-previousPitch),
                    Mth.wrapDegrees(pose.turretYaw()-previousYaw),.95F);
        }
        var sound = presentation.pollSound();
        if (sound != null) {
            Vec3 muzzle = sound.origin();
            level().playLocalSound(muzzle.x, muzzle.y, muzzle.z,
                    sound.velocity().lengthSqr() == 0 ? DnvArtillerySounds.BLIZZARD_LASER.get() : DnvArtillerySounds.BLIZZARD_FIRE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, sound.velocity().lengthSqr() == 0 ? 2F : .8F, 1F, false);
        }
        // Native baseTick still owns its single move; presentation must not move the hull twice.
        setDeltaMovement(Vec3.ZERO);
    }
    public BlizzardPresentation presentation() { return presentation; }
    /** Read-only HUD projection; no client countdown or second cooldown owner. */
    public int jumpCooldownTicks() {
        if (!level().isClientSide) return jumpCooldown;
        CompoundTag snapshot = entityData.get(PRESENTATION);
        return snapshot.contains("JumpCooldown", 3)
                ? Mth.clamp(snapshot.getInt("JumpCooldown"), 0, BlizzardRules.JUMP_COOLDOWN)
                : BlizzardRules.JUMP_COOLDOWN;
    }
    public int coldPhase() { return level().isClientSide ? entityData.get(PRESENTATION).getInt("ColdPhase") : beam.phase(); }
    public float coldProgress() {
        float value = level().isClientSide ? entityData.get(PRESENTATION).getFloat("ColdProgress") : getChargeProgress();
        return Float.isFinite(value) ? Mth.clamp(value, 0, 1) : 0;
    }
    private CompoundTag presentationSnapshot() {
        CompoundTag snapshot = BlizzardPresentation.encode(new BlizzardPresentation.Pose(level().getGameTime(),
                position(), getYRot(), getXRot(), getRoll(), getTurretYRot(), getTurretXRot(), getDeltaMovement(), motionActive), latestPulse);
        if(recentPulses!=null){
            recentPulses.values().removeIf(pulse->level().getGameTime()-pulse.tick()>BlizzardRules.BOLT_LIFE+4);
            BlizzardPresentation.appendPulses(snapshot,recentPulses.values());
        }
        if (recentLasers != null) {
            recentLasers.values().removeIf(shot -> level().getGameTime() - shot.tick() > BlizzardRules.TRAIL_TICKS);
            BlizzardPresentation.appendLasers(snapshot, recentLasers.values());
        }
        snapshot.putInt("JumpCooldown", jumpCooldown);
        snapshot.putInt("ColdPhase", beam.phase());
        snapshot.putFloat("ColdProgress", getChargeProgress());
        return snapshot;
    }
    public Vec3 presentationVelocity() {
        var pose = presentation.sample(1F);
        return level().isClientSide && pose != null ? pose.velocity() : getDeltaMovement();
    }

    // Native aim clamps before its >360 wrap. Normalize at the write seam so full turns never reach that clamp.
    @Override public void setTurretYRot(float value) {
        float wrapped = Mth.wrapDegrees(value);
        setTurretYRotO(getTurretYRotO() + wrapped - value);
        super.setTurretYRot(wrapped);
    }
    @Override public float getTurretYaw(float partial) { return Mth.rotLerp(partial, getTurretYRotO(), getTurretYRot()); }

    @Override public void travel() {
        if (level().isClientSide) return;
        motionActive=false;
        if ((!isAlive() || isWreck()) && (ascentTicks > 0 || jumpHoldTicks > 0)) {
            ascentTicks = jumpHoldTicks = 0;
            Vec3 stopped = getDeltaMovement();
            setDeltaMovement(new Vec3(stopped.x, Math.min(0, stopped.y), stopped.z));
        }
        boolean driven = driverReady();
        if (!driven) processInput((short) 0);
        boolean powered = powered();
        double ground = supportHeight();
        if (modeCooldown > 0) modeCooldown--;
        previousHatch = entityData.get(HATCH);
        if (!powered) entityData.set(MODE2, false);
        if (mode2() || entityData.get(HATCH) > 0) { deploymentTravel(ground); return; }
        entityData.set(DEPLOYED, false);
        if (jumpCooldown > 0) jumpCooldown--;
        boolean jumpKey = driven && decoyInputDown();
        if (jumpKey && !previousJumpKey && jumpCooldown == 0 && Double.isFinite(ground)
                && getY() <= ground + 1.5 && getEnergy() >= BlizzardRules.JUMP_FE) {
            consumeEnergy(BlizzardRules.JUMP_FE);
            jumpTarget = ground + 10;
            ascentTicks = 60;
            jumpHoldTicks = 0;
            jumpCooldown = BlizzardRules.JUMP_COOLDOWN;
        }
        previousJumpKey = jumpKey;
        if (jumpHoldTicks > 0) jumpHoldTicks--;
        boolean completedAscent = false;
        if (ascentTicks > 0) {
            boolean ceiling = verticalCollision && getDeltaMovement().y > 0;
            boolean arrived = getY() >= jumpTarget - .08;
            completedAscent = ceiling || arrived || --ascentTicks == 0;
            if (completedAscent) {
                ascentTicks = 0;
                if (arrived && !ceiling) jumpHoldTicks = BlizzardRules.JUMP_HOLD_TICKS;
            }
        }
        int drive = driven ? (forwardInputDown() ? 1 : 0) - (backInputDown() ? 1 : 0) : 0;
        int turn = driven ? (leftInputDown() ? 1 : 0) - (rightInputDown() ? 1 : 0) : 0;
        // Pay before turning or producing thrust. The jump prepays ascent and the five-second hold.
        int nextDriveTicks = drive != 0 && driven && drive == previousDrive && !horizontalCollision
                ? Math.min(BlizzardRules.ACCELERATION_TICKS, driveTicks + 1) : drive != 0 && driven ? 1 : 0;
        int motionCost = BlizzardRules.motionCost(turn != 0 ? BlizzardRules.ACCELERATION_TICKS : nextDriveTicks,
                drive != 0 || turn != 0);
        boolean motionPowered = isAlive() && !isWreck() && getEnergy() >= motionCost;
        if (motionPowered) consumeEnergy(motionCost);
        else { processInput((short) 0); driven = false; drive = turn = 0; }
        motionActive=motionPowered||ascentTicks>0||jumpHoldTicks>0;
        powered = motionPowered;
        // No native track engine or second move call; baseTick performs the sole collision move.
        steeringRate = BlizzardRules.steering(steeringRate, turn, getDeltaMovement().horizontalDistance(), driven);
        Vec3 stabilized = getShootVec("ColdBeam", 1);
        setYRot(getYRot() - steeringRate);
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(getYRot())), 0, Math.cos(Math.toRadians(getYRot())));
        Vec3 old = getDeltaMovement();
        if (completedAscent) old = new Vec3(old.x, Math.min(0, old.y), old.z);
        boolean supported = Double.isFinite(ground) && getY() - ground <= .8 && getY() >= ground - .15;
        if (drive == 0 || !driven || drive != previousDrive || horizontalCollision) driveTicks = 0;
        if (drive != 0 && driven && old.dot(forward) * drive >= -.002) driveTicks = Math.min(BlizzardRules.ACCELERATION_TICKS, driveTicks + 1);
        previousDrive = drive;
        double speed = BlizzardRules.horizontal(old.dot(forward), drive, powered, driveTicks, supported);
        double acceleration = speed - old.dot(forward);
        double pitchTarget = Mth.clamp(groundPitch, -12, 12) - Mth.clamp(acceleration * 110, -3.5, 3.5);
        double rollTarget = Mth.clamp(groundRoll, -10, 10) - Mth.clamp(steeringRate * speed * 2.5, -5, 5);
        pitchRate = BlizzardRules.attitudeRate(getXRot(), pitchRate, powered ? pitchTarget : 0);
        rollRate = BlizzardRules.attitudeRate(getRoll(), rollRate, powered ? rollTarget : 0);
        setXRot(getXRot() + pitchRate); setZRot(getRoll() + rollRate);
        // Hull suspension/steering must not masquerade as a player turret command.
        Vec3 localAim = BlizzardRig.direction(new Matrix4d(getVehicleTransform(1)).invert(), stabilized);
        setTurretYRot((float) Math.toDegrees(Math.atan2(localAim.x, localAim.z)));
        setTurretXRot(Mth.clamp((float) -Math.toDegrees(Math.asin(Mth.clamp(localAim.y, -1, 1))),
                -getTurretMaxPitch(), -getTurretMinPitch()));
        double grip = BlizzardRules.lateralRetention(supported, powered);
        Vec3 lateral = old.subtract(forward.scale(old.dot(forward))).multiply(grip, 0, grip);
        double hoverGround = powered && supported && drive == 0
                ? ground + .015 * Math.sin(tickCount / 18.0) : ground;
        double vertical = jumpHoldTicks > 0 ? Mth.clamp((jumpTarget - getY()) * .35, -.12, .12) : ascentTicks > 0
                ? BlizzardRules.vertical(old.y, getY(), jumpTarget - 10, true, 10)
                : BlizzardRules.vertical(old.y, getY(), hoverGround, powered);
        if (ascentTicks > 0) vertical = Math.min(vertical, Math.max(0, jumpTarget - getY()));
        setDeltaMovement(forward.scale(speed).add(lateral).add(0, vertical, 0));
        setPower(powered ? (float) Math.max(.08, Math.abs(speed) / BlizzardRules.FORWARD_SPEED) : 0);
    }

    private double supportHeight() {
        double highest = Double.NaN;
        fluidSupported = false;
        double[] heights = new double[5];
        java.util.Arrays.fill(heights, Double.NaN);
        int index = 0;
        for (double[] offset : new double[][] {{0, 0}, {-1.5, 2.4}, {1.5, 2.4}, {-1.5, -2.4}, {1.5, -2.4}}) {
            int probe = index++;
            Vec3 local = new Vec3(offset[0], 0, offset[1]).yRot((float) Math.toRadians(-getYRot()));
            Vec3 from = position().add(local).add(0, 1, 0);
            Vec3 to = new Vec3(from.x, Math.max(level().getMinBuildHeight(), getY() - 14), from.z);
            if (!BlizzardLoadedPath.loaded(this, from, to)) continue;
            var support = BlizzardSurface.sample(this, from, to, hovering());
            if (Double.isFinite(support.height())) {
                heights[probe] = support.height();
                if (Double.isNaN(highest) || heights[probe] > highest) {
                    highest = heights[probe]; fluidSupported = support.fluid();
                }
            }
        }
        groundPitch = -BlizzardRules.terrainAngle((heights[1] + heights[2]) / 2, (heights[3] + heights[4]) / 2, 4.8);
        groundRoll = BlizzardRules.terrainAngle((heights[2] + heights[4]) / 2, (heights[1] + heights[3]) / 2, 3);
        return highest;
    }

    @Override public void tick() {
        super.tick();
        if (isRemoved()) return;
        updateOBB();
        refreshBoundingBoxFromOBBs();
        if (!level().isClientSide) {
            beam.tick();
            entityData.set(PRESENTATION, presentationSnapshot());
        }
    }
    @Override public List<OBB> getOBBs() {
        List<OBB> boxes = super.getOBBs();
        if (boxes != nativeBoxes) {
            nativeBoxes = boxes;
            damageBoxes = boxes.stream().filter(b -> b.part != OBB.Part.COLLISION).toList();
        }
        return damageBoxes;
    }
    @Override public OBB getCollisionOBB() {
        var info = getCollisionOBBInfo();
        return info == null ? null : info.getOBB();
    }
    @Override public boolean canShoot(LivingEntity actor) {
        if (actor == null || actor != getFirstPassenger() || !driverReady()) return false;
        if (getWeaponIndex(0) == 1) return level().isClientSide ? super.canShoot(actor) : missiles.ready(actor);
        // Native canShoot also gates the client's held-key latch. Deployment/recharge are server shot gates,
        // so rejecting the key here would require another click after the hatch finished opening.
        return getWeaponIndex(0) == 0 && getEnergy() >= BlizzardRules.pulseCost(mode2());
    }
    @Override public int vehicleWeaponRpm(String weapon) {
        return "ColdBeam".equals(weapon) ? 120 : super.vehicleWeaponRpm(weapon);
    }
    @Override public int vehicleWeaponRpm(LivingEntity actor) {
        return actor == getFirstPassenger() && getWeaponIndex(0) == 0 ? 120 : super.vehicleWeaponRpm(actor);
    }
    // Native requests arm the server-owned charge. The 120 RPM request heartbeat is not mode-two damage cadence.
    @Override public boolean permitsNativeClientFire(Entity actor, Entity lockedTarget, boolean locked,
            com.atsuishio.superbwarfare.data.gun.GunData gun) {
        if (getWeaponIndex(0) != 1) return true;
        return actor != null && actor == getFirstPassenger() && !isRemoved() && driverReady()
                && gun != null && gun == getGunData("ClearingMissile") && missiles.clear();
    }
    @Override public void vehicleShoot(LivingEntity actor, UUID target, Vec3 point) { vehicleShoot(actor, null, target, point); }
    @Override public void vehicleShoot(LivingEntity actor, String name, Vec3 point) { vehicleShoot(actor, name, null, point); }
    @Override public void vehicleShoot(LivingEntity actor, String name, UUID target, Vec3 point) {
        if (level().isClientSide || actor == null || actor != getFirstPassenger() || !driverReady()) return;
        if (getWeaponIndex(0) == 0 && (name == null || "ColdBeam".equals(name))) beam.fire();
        else missiles.fire(actor, name, target);
    }
    void submitMissile(LivingEntity actor, UUID target) { super.vehicleShoot(actor, "ClearingMissile", target, null); }
    @Override public Runnable guardNativeShot(Runnable work) { return missiles.guard(work); }
    @Override public boolean deferNativeShotEffects() { return true; }
    @Override public boolean deferNativeClientSound() { return true; }
    @Override public void onNativeShotQueueResult(boolean enqueued) { missiles.queueResult(enqueued); }
    @Override public boolean admitNativeProjectile(net.minecraft.server.level.ServerLevel level, Entity projectile,
            com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) {
        return missiles.admit(level, projectile, parameters);
    }
    @Override public boolean nativeProjectileAccepted(com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) { return missiles.accepted(parameters); }
    void advanceMissilePort() {
        entityData.set(NEXT_PORT, 1 - entityData.get(NEXT_PORT));
    }
    boolean ownsTv(DnvBlizzardMissile missile) { return hasTv() && missile.getUUID().equals(tvMissile); }
    void beginTv(net.minecraft.server.level.ServerPlayer player, DnvBlizzardMissile missile) {
        tvMissile = missile.getUUID(); tvOperator = player; entityData.set(TV_ACTIVE, true);
        processInput((short) 0); setFiring(false);
        BlizzardTvNetwork.sendState(player, missile.state(true));
    }
    void endTv(DnvBlizzardMissile missile) {
        if (!missile.getUUID().equals(tvMissile)) return;
        if (tvOperator != null && tvOperator.serverLevel().getServer().getPlayerList().getPlayer(tvOperator.getUUID()) == tvOperator)
            BlizzardTvNetwork.sendState(tvOperator, missile.state(false));
        tvOperator = null; tvMissile = null; entityData.set(TV_ACTIVE, false); processInput((short) 0);
    }
    @Override public float getTurretTurnXSpeed() { return super.getTurretTurnXSpeed() * (firing() ? mode2() ? .60F : .80F : 1F); }
    @Override public float getTurretTurnYSpeed() { return super.getTurretTurnYSpeed() * (firing() ? mode2() ? .60F : .80F : 1F); }
    @Override public float getTurretMinPitch() { return (float) BlizzardRig.INSTANCE.minElevation(getTurretYaw(1)); }
    @Override public float getTurretMaxPitch() { return (float) BlizzardRig.INSTANCE.maxElevation; }
    @Override public boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty() && super.m_7310_(passenger);
    }
    // Native Track starts a second tank track loop and repitches jets as a piston engine.
    // Client effects own the selected fixed-pitch Blizzard idle and the movement jet loop.
    @Override public boolean engineRunning() { return false; }
    @Override public void releaseDecoy() { /* V is consumed by the authenticated hover input above. */ }
    public Matrix4d groupTransform(String group, float partial) {
        if (level().isClientSide) {
            var pose = presentation.sample(partial);
            if (pose != null) return pose.transform().mul(BlizzardRig.INSTANCE.local(group, pose.turretYaw(), -pose.turretPitch(), hatchOpening(partial)));
        }
        return getVehicleTransform(partial).mul(BlizzardRig.INSTANCE.local(group, getTurretYaw(partial), -getTurretPitch(partial), hatchOpening(partial)));
    }
    @Override public Matrix4d getTransformFromString(String name, float partial) {
        return name != null && name.startsWith("Blizzard") ? groupTransform(name.substring(8), partial) : super.getTransformFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name, float partial) {
        return name != null && name.startsWith("Blizzard") ? groupTransform(name.substring(8), partial).getNormalizedRotation(new Quaterniond())
                : super.getRotationFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name) { return getRotationFromString(name, 1); }
    public Vec3 anchorPoint(String name, float partial) {
        var anchor = BlizzardRig.INSTANCE.anchors.get(name);
        return BlizzardRig.point(groupTransform(anchor.group(), partial), anchor.position());
    }
    public Vec3 anchorDirection(String name, float partial) {
        var anchor = BlizzardRig.INSTANCE.anchors.get(name);
        return BlizzardRig.direction(groupTransform(anchor.group(), partial), anchor.direction());
    }
    /** Same muzzle and wall-bounded 80-block endpoint as the piercing server beam. Read-only. */
    public Vec3 laserAimPoint(float partial) {
        Vec3 from=getShootPos("ColdBeam",partial),end=from.add(getShootVec("ColdBeam",partial).scale(BlizzardRules.MODE2_RANGE));
        if(!BlizzardLoadedPath.loaded(this,from,end))return end;
        var wall=level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        return end;
    }
    /** The pulse and piercing mode have different ranges; both leave the same real muzzle. */
    public Vec3 primaryAimPoint(float partial){
        Vec3 from=getShootPos("ColdBeam",partial);
        Vec3 direction=getShootVec("ColdBeam",partial);
        double range=mode2()?BlizzardRules.MODE2_RANGE:BlizzardRules.RANGE;
        Vec3 end=from;
        // A shorter client view distance must not fall back to the native ray that can select our own rounds.
        // Query only the loaded portion; an unknown first segment still gives a stable direction projection.
        for(double distance=0;distance<range;distance+=BlizzardRules.BOLT_SPEED){
            Vec3 next=from.add(direction.scale(Math.min(range,distance+BlizzardRules.BOLT_SPEED)));
            if(!BlizzardLoadedPath.loaded(this,end,next))break;
            end=next;
        }
        if(end.equals(from))return from.add(direction.scale(range));
        var wall=level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        if(!mode2()){
            var hit=BlizzardTrace.nearest(level(),from,end,target->target.isAlive()&&target.isPickable()
                &&!target.isSpectator()&&target!=this&&target.getRootVehicle()!=this
                &&!(target instanceof DnvBlizzardColdBolt));
            if(hit!=null)end=hit.position();
        }
        return end;
    }
    private String shootAnchor(String weapon) {
        return "ClearingMissile".equals(weapon)
                ? entityData.get(NEXT_PORT) == 0 ? "missile_launch_L" : "missile_launch_R" : "main_gun_muzzle";
    }
    @Override public Vec3 getShootPos(String weapon, float partial) { return anchorPoint(shootAnchor(weapon), partial); }
    @Override public Vec3 getShootPos(Entity actor, float partial) { return getShootPos(getGunName(getSeatIndex(actor)), partial); }
    @Override public Vec3 getShootPosForHud(Entity actor, float partial) { return getShootPos(actor, partial); }
    @Override public Vec3 getShootVec(String weapon, float partial) { return anchorDirection(shootAnchor(weapon), partial); }
    @Override public Vec3 getShootVec(Entity actor, float partial) { return getShootVec(getGunName(getSeatIndex(actor)), partial); }
    @Override public Vec3 getShootDirectionForHud(Entity actor, float partial) { return getShootVec(actor, partial); }
    @Override public Vec3 getSeekVec(Entity actor, float partial) { return actor == null ? getViewVector(partial) : actor.getViewVector(partial); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.m_7380_(tag);
        tag.putInt("BlizzardBeamRemainder", 0); // Legacy fractional costs are no longer used.
        tag.putInt("BlizzardCannonCooldown", beam.cooldown());
        tag.putInt("BlizzardReloadTicks", beam.reloadTicks());
        tag.putInt("BlizzardCadencePhase", beam.cadencePhase());
        tag.putInt("BlizzardColdPhase", beam.phase());
        tag.putInt("BlizzardJumpCooldown", jumpCooldown);
        tag.putInt("BlizzardNextPort", entityData.get(NEXT_PORT));
        tag.putBoolean("BlizzardMode2", mode2());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag);
        if (tag.contains("BlizzardReloadTicks", 3)) {
            beam.restoreReloadTicks(tag.getInt("BlizzardReloadTicks"));
            float charge = getChargeProgress();
            setChargeProgress(Float.isFinite(charge) ? Mth.clamp(charge, 0, 1) : 0);
        } else beam.restoreCooldown(tag.getInt("BlizzardCannonCooldown"));
        beam.restoreCadencePhase(tag.getInt("BlizzardCadencePhase"));
        setFiring(false);
        entityData.set(TV_ACTIVE, false); tvMissile = null; tvOperator = null;
        processInput((short) 0);
        jumpCooldown = Mth.clamp(tag.getInt("BlizzardJumpCooldown"), 0, BlizzardRules.JUMP_COOLDOWN);
        ascentTicks = jumpHoldTicks = 0;
        previousJumpKey = false;
        steeringRate = 0;
        pitchRate = rollRate = 0;
        driveTicks = previousDrive = 0;
        entityData.set(NEXT_PORT, tag.getInt("BlizzardNextPort") & 1);
        entityData.set(MODE2, tag.getBoolean("BlizzardMode2"));
        beam.restorePhase(tag.getInt("BlizzardColdPhase"));
        entityData.set(DEPLOYED, false); entityData.set(HATCH, 0); clearLaser();
        modeCooldown = previousHatch = 0; fluidSupported = false; latestPulse = null; recentPulses = null; recentLasers = null;
    }
}

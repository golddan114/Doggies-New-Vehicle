package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.tools.OBB;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Independent AI identity; native SBW remains the sole chassis, inventory and health owner. */
public final class DnvAlliedApocalypseEntity extends DnvApocalypseEntity
        implements com.liucodex.doggiesnewvehicle.artillery.energy.DnvAutonomousEnergy,
        com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
    public static final String TYPE_ID = "doggievehicle:apocalypse_ai";
    private static final EntityDataAccessor<Float> AI_YAW = SynchedEntityData.defineId(DnvAlliedApocalypseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AI_PITCH = SynchedEntityData.defineId(DnvAlliedApocalypseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AI_POWER = SynchedEntityData.defineId(DnvAlliedApocalypseEntity.class, EntityDataSerializers.FLOAT);
    private final ApocalypseAlliedMembership membership = new ApocalypseAlliedMembership();
    private final ApocalypseAutonomousWeapons weapons = new ApocalypseAutonomousWeapons(this);
    private final ApocalypseAutonomy autonomy = new ApocalypseAutonomy(this, weapons);
    private final ApocalypseAutonomousSupply supply = new ApocalypseAutonomousSupply(this);
    private UUID deployer;
    private boolean driving;
    private boolean nativeMotionActive;
    private boolean nativeEnergyMutation;
    @Override public boolean nativeEnergyMutation() { return nativeEnergyMutation; }
    @Override public void setEnergy(int energy) {
        boolean previous = nativeEnergyMutation;
        nativeEnergyMutation = true;
        try { super.setEnergy(energy); } finally { nativeEnergyMutation = previous; }
    }
    @Override public void consumeEnergy(int energy) {
        boolean previous = nativeEnergyMutation;
        nativeEnergyMutation = true;
        try { super.consumeEnergy(energy); } finally { nativeEnergyMutation = previous; }
    }

    public DnvAlliedApocalypseEntity(EntityType<? extends DnvAlliedApocalypseEntity> type, Level level) {
        super(type, level);
    }

    public void deployedBy(Player player) {
        deployer = player.getUUID();
        membership.request(player.getTeam() == null ? null : player.getTeam().getName());
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AI_YAW, 0f);
        entityData.define(AI_PITCH, 0f);
        entityData.define(AI_POWER, 0f);
    }

    @Override public void onAddedToWorld() {
        super.onAddedToWorld();
        membership.added(this);
    }

    public boolean autonomousReady() {
        return level() instanceof ServerLevel server && membership.initialized() && !isRemoved() && !isWreck()
                && isAlive() && getPassengers().isEmpty() && server.getEntity(getUUID()) == this;
    }

    /** This permit exists only on the stack of the native tracked travel call. */
    public boolean nativeDriveAuthorized() { return driving && autonomousReady(); }

    @Override public boolean engineRunning() {
        // Native sound owns looping/attenuation/removal; an autonomous tank has no passenger to start it.
        return isAlive() && !isRemoved() && !isWreck() && getEnergy() > 0;
    }

    @Override public void tick() {
        if (!level().isClientSide) { supply.tick(); autonomy.beforeNativeTick(); }
        super.tick();
        if (!level().isClientSide) { supply.tick(); autonomy.afterNativeTick(); }
        synchronizeAim();
    }

    @Override public void handleClientSync() {
        // SBW restarts its ten-tick buffer on every movement update. This type sends every two ticks.
        int steps = Math.min(getInterpolationSteps(), 2);
        if (!level().isClientSide || steps == 0) {
            super.handleClientSync();
            return;
        }
        float yaw = getYRot(), pitch = getXRot();
        setInterpolationSteps(steps);
        super.handleClientSync();
        setYRot(yaw + Mth.wrapDegrees(getServerYaw() - yaw) / steps);
        setXRot(pitch + Mth.wrapDegrees(getServerPitch() - pitch) / steps);
    }

    @Override public float getTurretYaw(float partial) {
        return getTurretYRotO() + Mth.wrapDegrees(getTurretYRot() - getTurretYRotO()) * partial;
    }

    private void synchronizeAim() {
        if (level().isClientSide) {
            setTurretYRot(entityData.get(AI_YAW));
            setTurretXRot(entityData.get(AI_PITCH));
            setPower(entityData.get(AI_POWER));
        } else {
            entityData.set(AI_YAW, getTurretYRot());
            entityData.set(AI_PITCH, getTurretXRot());
            entityData.set(AI_POWER, getPower());
        }
    }

    boolean activeEnemy(Entity enemy) { return autonomy.activeEnemy(enemy); }

    @Override public boolean admitNativeProjectile(ServerLevel level, Entity projectile,
            com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) {
        return weapons.admit(level, projectile, parameters);
    }

    @Override public boolean nativeProjectileAccepted(com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) {
        return weapons.accepted(parameters);
    }

    @Override protected double forwardSpeedLimit() { return .40; }
    @Override protected double reverseSpeedLimit() { return .20; }
    @Override protected float hullTurnLimit() { return 1F; }
    @Override public float getTurretTurnXSpeed() { return 2.25F; }
    @Override public float getTurretTurnYSpeed() { return 2.25F; }

    @Override public void travel() {
        driving = autonomousReady();
        try { super.travel(); }
        finally { driving = false; }
    }

    @Override public void vMove(MoverType type, Vec3 motion) {
        boolean previous = nativeMotionActive;
        nativeMotionActive = true;
        try { super.vMove(type, motion); }
        finally { nativeMotionActive = previous; }
    }

    @Override public void setPos(double x, double y, double z) {
        // Native support can push an already deployed OBB vehicle down without resolving terrain.
        // Native vMove already resolves its own motion; spawning and client interpolation remain available.
        if (!nativeMotionActive && y < getY() - 1E-7 && level() instanceof ServerLevel server
                && server.getEntity(getUUID()) == this && !supportDestinationClear(server, x, y, z)) return;
        super.setPos(x, y, z);
    }

    private boolean supportDestinationClear(ServerLevel server, double x, double y, double z) {
        var physics = getCollisionOBB();
        if (physics == null) return false;
        var shifted = physics.move(new Vec3(x - getX(), y - getY(), z - getZ()));
        var bounds = OBB.getWorldAABB(shifted);
        if (!server.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) return false;
        for (var shape : server.getBlockCollisions(this, bounds))
            for (var part : shape.toAabbs()) if (OBB.isColliding(shifted, part)) return false;
        return true;
    }

    @Override protected boolean canAddPassenger(Entity passenger) { return false; }

    private boolean mayService(Player player) {
        return player != null && !player.isSpectator() && (player.isCreative() || player.hasPermissions(2)
                || deployer == null || deployer.equals(player.getUUID()) || isAlliedTo(player));
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        return mayService(player) ? super.m_6096_(player, hand) : InteractionResult.FAIL;
    }

    @Override public void openMenu(Player player) { if (mayService(player)) super.openMenu(player); }

    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
        autonomy.clear();
        membership.removed(this, reason);
        driving = false;
    }


    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag ai = new CompoundTag();
        ai.putInt("Schema", 1);
        if (deployer != null) ai.putUUID("Deployer", deployer);
        membership.save(ai);
        autonomy.save(ai);
        weapons.save(ai);
        supply.save(ai);
        tag.put("DnvAlliedApocalypse", ai);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        var ai = tag.getCompound("DnvAlliedApocalypse");
        deployer = ai.hasUUID("Deployer") ? ai.getUUID("Deployer") : null;
        membership.load(ai);
        autonomy.load(ai);
        weapons.load(ai);
        supply.load(ai);
        driving = false;
    }
}

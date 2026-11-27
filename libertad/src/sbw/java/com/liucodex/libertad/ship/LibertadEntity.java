package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.libertad.client.LibertadClient;
import com.liucodex.libertad.naval.CrewControl;
import com.liucodex.libertad.naval.AirDefenseRules;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** SBW's Ship engine, native seat, GunData and inventory are the underlying vehicle. */
public final class LibertadEntity extends VehicleEntity {
    private static final EntityDataAccessor<CompoundTag> FIRE_STATE = SynchedEntityData.defineId(LibertadEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final ShipFireControl fire = new ShipFireControl(this);
    private final ShipTickets tickets = new ShipTickets();
    private final Angles[] shown = new Angles[24], previous = new Angles[24];
    private final float[] aaShown = new float[AirDefenseRules.ZONES], aaPrevious = new float[AirDefenseRules.ZONES];
    private final float[] aaPitchShown = new float[AirDefenseRules.ZONES], aaPitchPrevious = new float[AirDefenseRules.ZONES];
    private long leaseUntil;
    private boolean receivedFireState;
    private CompoundTag displayedBatteryTag;
    private List<MainBatteryView> displayedBattery = List.of();
    private int displayedSeat = -1;
    private double propellerAngle, propellerStep;

    public LibertadEntity(EntityType<? extends LibertadEntity> type, Level level) {
        super(type, level);
        java.util.Arrays.fill(shown, ShipFireControl.HOME);
        java.util.Arrays.fill(previous, ShipFireControl.HOME);
    }

    @Override protected void defineSynchedData() {
        super.m_8097_();
        entityData.define(FIRE_STATE, new CompoundTag());
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.m_7380_(tag);
        tag.put("LibertadTurrets", fire.savePoses());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag);
        fire.loadPoses(tag.getCompound("LibertadTurrets"));
        if (!level().isClientSide) entityData.set(FIRE_STATE, fire.snapshot());
    }

    @Override public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level() instanceof ServerLevel server) ShipPresence.get(server).accept(getUUID());
    }

    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            tickets.update(server, this, false);
            if (reason.shouldDestroy()) ShipPresence.get(server).release(getUUID());
        }
        super.m_142687_(reason);
    }

    @Override public boolean canChangeDimensions() { return false; }

    @Override public void onPassengerTurned(Entity rider) {
        super.m_7340_(rider);
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LibertadClient.constrainHelmYaw(this, rider));
        }
    }

    @Override protected boolean canAddPassenger(Entity actor) {
        return actor instanceof Player && getPassengers().size() < 3 && !isWreck() && super.m_7310_(actor);
    }

    @Override protected void removePassenger(Entity actor) {
        super.m_20351_(actor);
        if (fire != null && !level().isClientSide) fire.vacate();
    }

    @Override public boolean changeSeat(Entity actor, int seat) {
        boolean changed = super.changeSeat(actor, seat);
        if (changed && !level().isClientSide) fire.vacate();
        return changed;
    }

    boolean master(Entity player) {
        return player instanceof Player && player.getVehicle() == this
                && player.isAlive() && !player.isSpectator() && getSeatIndex(player) == 0 && !isWreck() && !isRemoved();
    }

    boolean crewMember(Entity actor) {
        return actor instanceof Player && actor.getVehicle() == this && actor.isAlive() && !actor.isSpectator()
                && !isWreck() && !isRemoved() && getSeatIndex(actor) >= 0 && getSeatIndex(actor) < 3;
    }

    public boolean multicrew() { return level().isClientSide ? fireSnapshot().getBoolean("multicrew") : fire.multicrew(); }

    @Override public double getDefaultZoom(Entity actor) {
        int seat = getSeatIndex(actor);
        // Side batteries use our authenticated fire channel, so their SBW Weapons list stays empty.
        // Supply the same 5x optic as the main battery to SBW's FOV and camera path.
        return seat == 1 || seat == 2 ? (multicrew() ? 5 : 1) : super.getDefaultZoom(actor);
    }

    @Override public boolean canShoot(LivingEntity actor) { return master(actor); }

    @Override public void vehicleShoot(LivingEntity actor, UUID target, Vec3 point) { fire.request(actor); }
    @Override public void vehicleShoot(LivingEntity actor, String name, Vec3 point) { fire.request(actor); }
    @Override public void vehicleShoot(LivingEntity actor, String name, UUID target, Vec3 point) { fire.request(actor); }
    @Override public void releaseDecoy() { /* V is a scoped Libertad control. */ }

    public void toggleSecondaries(ServerPlayer player) {
        if (mayChangeFireControl(player)) fire.toggle(player);
    }

    public void toggleCrewMode(ServerPlayer player) {
        if (mayChangeFireControl(player)) fire.toggleCrewMode(player);
    }

    private boolean mayChangeFireControl(ServerPlayer player) {
        if (master(player)) return true;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                isWreck() || isRemoved() ? "message.libertad.control.wreck" : "message.libertad.control.helm"), true);
        return false;
    }

    public void sideInput(ServerPlayer player, long generation, boolean high, boolean shoot) {
        if (crewMember(player) && getSeatIndex(player) > 0) fire.requestSide(player, generation, high, shoot);
    }

    public void helmOptic(ServerPlayer player, long generation, boolean zoom) {
        if (master(player)) fire.helmOptic(player, generation, zoom);
    }

    // Measured against the fore bridge's central observation platform (deck Y = 10.73).
    Vec3 sideCamera(int seat, float partial, boolean high) {
        double side = seat == 1 ? 1 : -1;
        return ShipModel.point(getVehicleTransform(partial), new Vec3(side * (high ? 3.8 : 1.62), high ? 17.5 : 12.35, 19.4));
    }

    @Override public Vec3 getThirdPersonCameraPosition() {
        Vec3 offset = super.getThirdPersonCameraPosition();
        return level().isClientSide ? com.liucodex.libertad.client.HelmChaseCamera.limit(this, offset) : offset;
    }

    @Override public Vec3 getCameraPosition(float partial, Player player, boolean zoom, boolean firstPerson) {
        int seat = getSeatIndex(player);
        if (level().isClientSide && seat > 0) {
            Vec3 exterior = com.liucodex.libertad.client.WatchCamera.position(this, player, partial);
            if (exterior != null) return exterior;
        }
        return seat == 1 || seat == 2 ? sideCamera(seat, partial, !firstPerson)
                : super.getCameraPosition(partial, player, zoom, firstPerson);
    }

    @Override public Vec2 getCameraRotation(float partial, Player player, boolean zoom, boolean firstPerson) {
        int seat = getSeatIndex(player);
        if (seat < 1 || seat > 2) return super.getCameraRotation(partial, player, zoom, firstPerson);
        if (level().isClientSide) {
            Vec2 exterior = com.liucodex.libertad.client.WatchCamera.rotation(this, player, partial);
            if (exterior != null) return exterior;
        }
        return new Vec2(CrewControl.viewYaw(player.getViewYRot(partial), getViewYRot(partial), seat, firstPerson),
                Mth.clamp(player.getViewXRot(partial), -80, 80));
    }

    public boolean deferAmmoChange(com.atsuishio.superbwarfare.data.gun.GunData gun, int index) {
        return fire != null && fire.deferAmmoChange(gun, index);
    }

    @Override public void destroy() {
        if (level().isClientSide || isRemoved()) return;
        setWreck(true);
        setHealth(0);
        setSympatheticDetonated(true);
        ejectPassengers();
    }

    @Override public void vehicleExplosion(com.atsuishio.superbwarfare.data.vehicle.subdata.DestroyInfo info) {
        // Shipwreck owns the water-bottom impact.
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel wreckLevel && (isWreck() || getHealth() <= 0)) {
            destroy();
            com.liucodex.doggiesnewvehicle.naval.Shipwreck.tick(this, wreckLevel, true);
            return;
        }
        if (level() instanceof ServerLevel level) {
            if (getPassengers().stream().anyMatch(this::crewMember)) leaseUntil = level.getGameTime() + 100;
            if (tickCount % 4 == 0) tickets.update(level, this, level.getGameTime() < leaseUntil && !isWreck());
        }
        super.tick();
        if (isRemoved()) return;
        if (level() instanceof ServerLevel level) {
            fire.tick(level);
            entityData.set(FIRE_STATE, fire.snapshot());
        } else {
            CompoundTag snapshot = entityData.get(FIRE_STATE);
            if (!snapshot.isEmpty()) {
                for (int i = 0; i < shown.length; i++) {
                    var pose = new Angles(snapshot.getFloat("y" + i), snapshot.getFloat("p" + i));
                    previous[i] = receivedFireState ? shown[i] : pose;
                    shown[i] = receivedFireState ? ShipFireControl.advance(i, shown[i], pose) : pose;
                }
                for (int i = 0; i < aaShown.length; i++) {
                    float heading = snapshot.getFloat("aa" + i);
                    aaPrevious[i] = receivedFireState ? aaShown[i] : heading;
                    aaShown[i] = heading;
                    float pitch = snapshot.getFloat("aap" + i);
                    aaPitchPrevious[i] = receivedFireState ? aaPitchShown[i] : pitch;
                    aaPitchShown[i] = pitch;
                }
                receivedFireState = true;
            }
            propellerAngle = Math.IEEEremainder(propellerAngle + propellerStep, 360);
            double yaw = Math.toRadians(getYRot());
            double speed = getDeltaMovement().x * -Math.sin(yaw) + getDeltaMovement().z * Math.cos(yaw);
            double target = isWreck() || Math.abs(speed) < .002 ? 0 : Mth.clamp(speed * 180, -60, 60);
            propellerStep = Math.abs(target - propellerStep) < .1 ? target : Mth.lerp(.25, propellerStep, target);
            FunnelExhaust.tick(this);
        }
        refreshBoundingBoxFromOBBs();
    }

    @Override public void travel() {
        float yaw = getYRot();
        super.travel();
        float turn = Mth.clamp(Mth.wrapDegrees(getYRot() - yaw), -.35F, .35F);
        if (Math.abs(turn) > 1e-4 && master(getNthEntity(0))) {
            setYRot(yaw);
            updateOBB();
            double angle = -Math.toRadians(turn);
            for (OBB hull : getOBBs()) {
                if (!ShipHull.canTurn(level(), this, hull, angle)) {
                    turn = 0;
                    break;
                }
            }
        }
        setYRot(yaw + turn);
        Vec3 movement = getDeltaMovement();
        Vec3 bounded = ShipTravelLimits.limit(movement, getYRot(), isInFluidType() && !onGround());
        if (bounded != movement) setDeltaMovement(bounded);
        if (level() instanceof ServerLevel level && master(getNthEntity(0))) {
            for (long key : ShipTickets.footprint(this, 0)) {
                if (!level.isPositionEntityTicking(new BlockPos(ChunkPos.getX(key) << 4, getBlockY(), ChunkPos.getZ(key) << 4))) {
                    setYRot(yaw);
                    setDeltaMovement(0, movement.y, 0);
                    break;
                }
            }
        }
        updateOBB();
    }

    @Override public Vec3 vCollide(Vec3 movement) {
        var hull = getOBBs().stream().filter(box -> box.part == OBB.Part.COLLISION).toList();
        return hull.isEmpty() ? super.vCollide(movement) : ShipCollisionQuery.resolve(this, movement, hull);
    }

    @Override public void updateFluidHeightAndDoFluidPushing(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> predicate) {
        if (!ShipFluidAccess.update(this, predicate)) super.updateFluidHeightAndDoFluidPushing(predicate);
    }

    void waterHeight(double height) { setFluidTypeHeight(net.minecraftforge.common.ForgeMod.WATER_TYPE.get(), height); }

    public Angles angles(int index, float partial) {
        if (!level().isClientSide) return fire.pose(index);
        if (shown[index] == null || previous[index] == null) return ShipFireControl.HOME;
        return new Angles(previous[index].yaw() + Math.IEEEremainder(shown[index].yaw() - previous[index].yaw(), 360) * partial,
                Mth.lerp(partial, previous[index].pitch(), shown[index].pitch()));
    }

    public CompoundTag fireSnapshot() { return entityData.get(FIRE_STATE); }

    public float aaHeading(int zone, float partial) {
        return aaPrevious[zone] + Mth.wrapDegrees(aaShown[zone] - aaPrevious[zone]) * partial;
    }

    public float aaPitch(int zone, float partial) {
        return Mth.lerp(partial, aaPitchPrevious[zone], aaPitchShown[zone]);
    }

    public boolean searchlightsOn() {
        return !isWreck() && ShipFireControl.searchlightsOn(fireSnapshot());
    }

    public List<MainBatteryView> mainBatteryDisplay() {
        return batteryDisplay(0);
    }

    public List<MainBatteryView> batteryDisplay(int seat) {
        CompoundTag state = fireSnapshot();
        if (displayedBatteryTag != state || displayedSeat != seat) {
            displayedBattery = seat == 0 ? MainBatteryView.read(state)
                    : multicrew() ? MainBatteryView.read(state, "SideBattery" + seat, 9) : List.of();
            displayedBatteryTag = state;
            displayedSeat = seat;
        }
        return displayedBattery;
    }

    public double propellerAngle(float partial) { return propellerAngle + propellerStep * partial; }

    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(6); }

    @Override public Vec3 getShootPos(String name, float partial) {
        var barrel = fire.barrel(name, partial);
        return barrel == null ? super.getShootPos(name, partial)
                : ShipModel.point(getVehicleTransform(partial), ShipModel.vec(barrel.muzzle()));
    }

    @Override public Vec3 getShootVec(String name, float partial) {
        var barrel = fire.barrel(name, partial);
        return barrel == null ? super.getShootVec(name, partial)
                : ShipModel.vector(getVehicleTransform(partial), ShipModel.vec(barrel.forward())).normalize();
    }

    @Override public Vec3 getShootPos(Entity actor, float partial) { return getShootPos("main_01_l", partial); }
    @Override public Vec3 getShootVec(Entity actor, float partial) { return getShootVec("main_01_l", partial); }
    @Override public Vec3 getShootPosForHud(Entity actor, float partial) { return getShootPos(actor, partial); }
    @Override public Vec3 getShootDirectionForHud(Entity actor, float partial) { return getShootVec(actor, partial); }
}

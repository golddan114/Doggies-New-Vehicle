package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.init.ModTags;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.IVehicleInteract;
import com.atsuishio.superbwarfare.tools.TrajectoryCalculator;
import com.atsuishio.superbwarfare.tools.VectorToolKt;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.liucodex.doggiesnewvehicle.compat.DnvM1RemoteCrew;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteManualTarget;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Native SBW defense turret. Inventory, GunData, reload/fire, health, activation and HUD
 * belong to SBW. M1 retains its identity; five authored derivatives opt into mounted or remote crew.
 */
public class DnvM1YunlongEntity extends AutoAimableEntity implements DnvRemoteManualTarget,
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission,
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent,
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeCrewTarget,
    com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard,
    DnvPlayerArtilleryTarget, com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  @Override public float ordinaryArmorReduction() { return 0.20F; }
  @Override public float directPercent(String weapon) {
    return "Main".equals(weapon)
        ? com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction("yunlong") : 0;
  }
  public static final String TYPE_ID = "doggievehicle:m1_yunlong";
  public static final float MODEL_SCALE = 1.0F;
  public static final float MAX_HEALTH = 450.0F;
  public static final float DAMAGED_HEALTH = 270.0F;
  private static final float RECOIL_TICKS = 42.0F;
  private static final String NATIVE_SCHEMA = "DnvM1YunlongNativeSchema";
  private static final EntityDataAccessor<Optional<UUID>> REMOTE_ACTOR =
      SynchedEntityData.defineId(DnvM1YunlongEntity.class, EntityDataSerializers.OPTIONAL_UUID);
  private static final EntityDataAccessor<BlockPos> REMOTE_BODY =
      SynchedEntityData.defineId(DnvM1YunlongEntity.class, EntityDataSerializers.BLOCK_POS);
  private static final EntityDataAccessor<Rotations> SYNCED_TURRET_POSE =
      SynchedEntityData.defineId(DnvM1YunlongEntity.class, EntityDataSerializers.ROTATIONS);
  private static final DnvTurretDefinition DEFINITION = new DnvTurretDefinition(
      TYPE_ID, "doggiesnewvehicle:m1_yunlong_manual", Map.of("vehicle", Set.of("Main")), true, Optional.empty());
  private UUID remoteStation;
  private BlockPos remoteStationPosition;
  private long remoteStationRevision;
  private long remoteCrewGeneration;
  private boolean nativeStateInvalid;
  private boolean endingRemote;
  // A player's indicator order is transient M1 state, never another ammo or station owner.
  private DnvM1IndicatorAim indicatorAim;
  private DnvM1IndicatorAim indicatorPending;
  private DnvM1IndicatorAim indicatorSubmitting;
  private boolean indicatorRequested;
  protected final com.liucodex.doggiesnewvehicle.artillery.energy.M1ServoEnergy servoEnergy =
      new com.liucodex.doggiesnewvehicle.artillery.energy.M1ServoEnergy();
  private final DnvTurretPoseInterpolation clientTurretPose = new DnvTurretPoseInterpolation();
  private final DnvBarrelRecoil visualRecoil = new DnvBarrelRecoil();
  private com.atsuishio.superbwarfare.data.gun.ShootParameters admittedParameters;
  private boolean admittedProjectile, admittingProjectile;
  private long mountedEpoch;
  private ServerPlayer crowbarActor;
  private long lastCrowbarTick = Long.MIN_VALUE;
  public DnvDragonTurretProfile profile() { return DnvDragonTurretProfile.M1; }
  protected boolean mountedCrew() { return false; }
  /** Fixed turrets have no client-owned body motion; retain native server-position interpolation. */
  @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }

  public DnvM1YunlongEntity(EntityType<? extends DnvM1YunlongEntity> type, Level level) {
    super(type, level);
    setNoGravity(true);
    setCustomName(Component.translatable("entity.doggievehicle.m1_yunlong"));
    setCustomNameVisible(false);
  }

  @Override protected void defineSynchedData() {
    super.m_8097_();
    entityData.define(REMOTE_ACTOR, Optional.empty());
    entityData.define(REMOTE_BODY, BlockPos.ZERO);
    entityData.define(SYNCED_TURRET_POSE, new Rotations(0, 0, 0));
  }

  public boolean initializeOwner(UUID owner) {
    if (!(level() instanceof ServerLevel) || owner == null || getOptionalOwnerUUID().isPresent() || nativeStateInvalid) return false;
    setOwnerUUID(owner);
    return true;
  }

  @Override public DnvTurretDefinition remoteManualDefinition() { return DEFINITION; }
  @Override public UUID remoteManualOwner() { return getOptionalOwnerUUID().orElse(null); }
  @Override public Entity remoteCrewEntity() { return this; }
  // There is no second custom fire lease: the native occupied crew seat is the authority.
  @Override public void autoAim() { /* M1 remains a manually operated turret. */ }

  public boolean canBindIndicator(Player player) {
    return player != null && !nativeStateInvalid && !isDestroyed() && !getLocked()
        && getOptionalOwnerUUID().filter(player.getUUID()::equals).isPresent();
  }

  /** Called only by the exact SBW indicator's server-side target command. */
  public boolean setIndicatorTarget(ServerPlayer player, BlockPos target, int radius, boolean depressed) {
    if (!canBindIndicator(player) || player.level() != level()) return false;
    indicatorAim = null;
    indicatorPending = null;
    indicatorRequested = false;
    if (target == null
        || radius < 0 || radius > 256 || level().isOutsideBuildHeight(target)
        || !level().getWorldBorder().isWithinBounds(target) || getGunData("Main") == null) return false;
    Vec3 origin = getShootPos("Main", 1.0F);
    Vec3 point = VectorToolKt.randomPos(target.getCenter(), radius);
    double distance = origin.distanceTo(target.getCenter());
    point = point.add(0, -0.5D - 0.0015D * distance, 0);
    Vec3 launch = TrajectoryCalculator.calculateLaunchVector(origin, point,
        getProjectileVelocity("Main"), getProjectileGravity("Main"), !depressed);
    if (launch == null || launch.lengthSqr() < 1.0E-8D
        || !Double.isFinite(launch.x) || !Double.isFinite(launch.y) || !Double.isFinite(launch.z)) return false;
    float pitch = -(float) VehicleVecUtils.getXRotFromVector(launch);
    if (pitch < -getTurretMaxPitch() || pitch > -getTurretMinPitch()) return false;
    if (!withinYawRange(launch)) return false;
    indicatorAim = new DnvM1IndicatorAim(player.getUUID(), target.immutable(), radius, depressed,
        launch.normalize(), level().getGameTime() + 1200);
    return true;
  }

  public boolean requestIndicatorFire(ServerPlayer player, BlockPos target, int radius, boolean depressed) {
    DnvM1IndicatorAim aim = indicatorAim;
    if (aim == null || !aim.matches(player.getUUID(), target, radius, depressed) || indicatorPending != null
        || !indicatorValid(aim, player) || !getPassengers().isEmpty()) return false;
    GunData gun = getGunData("Main");
    if (gun == null || !gun.canShoot(getAmmoSupplier())) return false;
    indicatorRequested = true;
    return true;
  }

  public void clearIndicatorTarget(ServerPlayer player) {
    if (player != null && indicatorAim != null && indicatorAim.actor().equals(player.getUUID())) {
      indicatorAim = null;
      indicatorPending = null;
      indicatorRequested = false;
    }
  }

  private boolean indicatorValid(DnvM1IndicatorAim aim, ServerPlayer player) {
    return aim == indicatorAim && hasOperationalPower() && withinYawRange(aim.launch())
        && canBindIndicator(player) && !isRemoved() && player.isAlive()
        && !player.isSpectator() && player.level() == level() && level() instanceof ServerLevel server
        && server.getEntity(getUUID()) == this && player.server.getPlayerList().getPlayer(aim.actor()) == player
        && !aim.expired(level().getGameTime()) && !level().isOutsideBuildHeight(aim.target())
        && level().getWorldBorder().isWithinBounds(aim.target());
  }

  private boolean indicatorAligned(DnvM1IndicatorAim aim) { return aim.aligned(getShootVec("Main", 1.0F)); }

  private boolean withinYawRange(Vec3 launch) {
    float relative=Mth.wrapDegrees((float)VehicleVecUtils.getYRotFromVector(launch)+getYRot());
    return relative>=getTurretMinYaw()-.001F && relative<=getTurretMaxYaw()+.001F;
  }

  @Override public InteractionResult interact(Player player, InteractionHand hand) {
    if (nativeStateInvalid) return InteractionResult.FAIL;
    ItemStack stack = player.getMainHandItem();
    if (mountedCrew()) {
      // Mounted guns use the inventory gesture rather than the autonomous-tower activation toggle.
      if (player.isShiftKeyDown() && !getLocked() && !isDestroyed() && stack.isEmpty()) {
        openMenu(player);
        return InteractionResult.sidedSuccess(level().isClientSide);
      }
      return super.m_6096_(player, hand);
    }
    // Native locks, repair/key tools, naming, explosives, activation and packing keep their original dispatch.
    // Only ordinary entry becomes inventory access; native gunner entry is supplied by the remote station.
    if (getLocked() || player.isShiftKeyDown() || stack.is(ModTags.Items.TOOLS_CROWBAR)
        || stack.getItem() instanceof IVehicleInteract || stack.getItem() instanceof NameTagItem
        || stack.is(ModItems.C4_BOMB.get()) || stack.is(ModItems.DETONATOR.get()))
      return super.m_6096_(player, hand);
    if (isDestroyed()) return InteractionResult.FAIL;
    if (getOptionalOwnerUUID().isPresent() && !getOptionalOwnerUUID().get().equals(player.getUUID())) return InteractionResult.FAIL;
    if (hand == InteractionHand.MAIN_HAND) openMenu(player);
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override public InteractionResult onCrowbarInteract(ItemStack stack, Player player, InteractionHand hand) {
    if (!getPassengers().isEmpty() || nativeStateInvalid) return InteractionResult.FAIL;
    if (profile().entity.equals("m1_yunlong") && !player.isShiftKeyDown()) {
      if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
      if (level().isClientSide) return InteractionResult.SUCCESS;
      if (!(player instanceof ServerPlayer actor) || !crowbarReady(actor)) return InteractionResult.FAIL;
      long now = level().getGameTime();
      if (lastCrowbarTick == now) return InteractionResult.FAIL;
      lastCrowbarTick = now;
      withCrowbarActor(actor, () -> super.vehicleShoot(actor, "Main", null, null));
      return InteractionResult.SUCCESS;
    }
    return super.onCrowbarInteract(stack, player, hand);
  }

  private boolean crowbarReady(ServerPlayer actor) {
    var gun = getGunData("Main");
    return profile().entity.equals("m1_yunlong") && canBindIndicator(actor) && hasOperationalPower()
        && actor.isAlive() && !actor.isSpectator() && actor.level() == level() && actor.distanceToSqr(this) <= 64
        && actor.getMainHandItem().is(ModTags.Items.TOOLS_CROWBAR) && getPassengers().isEmpty()
        && level() instanceof ServerLevel server && server.getEntity(getUUID()) == this
        && gun != null && gun.canShoot(getAmmoSupplier());
  }

  private void withCrowbarActor(ServerPlayer actor, Runnable work) {
    ServerPlayer previousActor = crowbarActor;
    crowbarActor = actor;
    try { work.run(); } finally { crowbarActor = previousActor; }
  }

  @Override public void openMenu(Player player) {
    if (!nativeStateInvalid && !isDestroyed() && !getLocked()
        && (getOptionalOwnerUUID().isEmpty() || getOptionalOwnerUUID().get().equals(player.getUUID())))
      super.openMenu(player);
  }

  /** A remote entry supplies a physical seat before vanilla establishes its native passenger relation. */
  public boolean beginRemoteCrew(ServerPlayer player, UUID station, BlockPos stationPos, long revision, BlockPos bodyPos) {
    if (mountedCrew()) return false;
    if (!hasOperationalPower() || getLocked() || player.isPassenger() || !getPassengers().isEmpty()
        || !player.getUUID().equals(remoteManualOwner()) || player.level() != level()) return false;
    remoteStation = station;
    remoteStationPosition = stationPos.immutable();
    remoteStationRevision = revision;
    remoteCrewGeneration++;
    entityData.set(REMOTE_BODY, bodyPos.immutable());
    entityData.set(REMOTE_ACTOR, Optional.of(player.getUUID()));
    if (!player.startRiding(this, true)) { clearRemoteCrew(); return false; }
    positionRider(player);
    return true;
  }

  public UUID remoteStation() { return remoteStation; }
  public BlockPos remoteStationPosition() { return remoteStationPosition; }
  public long remoteStationRevision() { return remoteStationRevision; }
  public long remoteCrewGeneration() { return remoteCrewGeneration; }
  public BlockPos remoteBodyPosition() { return entityData.get(REMOTE_BODY); }
  public boolean isRemoteOperator(Entity entity) {
    return entity != null && entityData.get(REMOTE_ACTOR).filter(entity.getUUID()::equals).isPresent();
  }
  public boolean hasRemoteCrew() { return entityData.get(REMOTE_ACTOR).isPresent(); }

  public void endRemoteCrew() {
    if (endingRemote) return;
    endingRemote = true;
    for (Entity passenger : List.copyOf(getPassengers())) passenger.stopRiding();
    clearRemoteCrew();
    endingRemote = false;
  }

  private void clearRemoteCrew() {
    remoteCrewGeneration++;
    entityData.set(REMOTE_ACTOR, Optional.empty());
    remoteStation = null;
    remoteStationPosition = null;
  }

  @Override protected boolean canAddPassenger(Entity passenger) {
    return mountedCrew() && !nativeStateInvalid && !isDestroyed() && !getLocked()
        && passenger instanceof Player && getPassengers().size()<profile().seats && super.canAddPassenger(passenger);
  }

  // Remote crew is transient. Keep the turret in its world chunk, never in a player's RootVehicle save.
  @Override public boolean hasExactlyOnePlayerPassenger() { return mountedCrew() && super.hasExactlyOnePlayerPassenger(); }

  @Override public void positionRider(Entity passenger, MoveFunction move) {
    if (mountedCrew()) { super.positionRider(passenger, move); return; }
    if (hasPassenger(passenger) && isRemoteOperator(passenger)) {
      Vec3 body = Vec3.atBottomCenterOf(remoteBodyPosition());
      move.accept(passenger, body.x, body.y, body.z);
    }
  }

  @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
    if (mountedCrew()) return super.getDismountLocationForPassenger(passenger);
    return passenger.position();
  }

  @Override public boolean canShoot(LivingEntity operator) {
    if (!hasOperationalPower() || getLocked()) return false;
    // An outside crowbar actor has no native passenger seat. Readiness already checks named Main/GunData.
    if (operator == crowbarActor && crowbarActor != null) return crowbarReady(crowbarActor);
    if (mountedCrew()) return operator instanceof Player && operator.isAlive() && operator.level()==level() && hasPassenger(operator)
        && getSeatIndex(operator)==0 && !nativeStateInvalid && !getLocked() && !isDestroyed() && super.canShoot(operator);
    return operator != null && !nativeStateInvalid && isRemoteOperator(operator) && hasPassenger(operator)
        && (level().isClientSide || DnvM1RemoteCrew.valid(this)) && super.canShoot(operator);
  }

  @Override public void vehicleShoot(LivingEntity operator, UUID target, Vec3 point) {
    vehicleShoot(operator, "Main", target, point);
  }

  @Override public void vehicleShoot(LivingEntity operator, String weapon, Vec3 point) {
    vehicleShoot(operator, weapon, null, point);
  }

  @Override public void vehicleShoot(LivingEntity operator, String weapon, UUID target, Vec3 point) {
    if (!(level() instanceof ServerLevel) || !"Main".equals(weapon) || !canShoot(operator)) return;
    // The native packet supports named/non-riding fire too; all M1 entrances require its occupied remote seat.
    super.vehicleShoot(operator, weapon, target, point);
  }

  @Override public Runnable guardNativeShot(Runnable nativeWork) {
    Runnable scopedWork=() -> { if(hasOperationalPower())runNativeShotScope(nativeWork); };
    if (crowbarActor != null) {
      ServerPlayer actor = crowbarActor;
      return () -> { if (crowbarReady(actor)) withCrowbarActor(actor, scopedWork); };
    }
    if (mountedCrew()) {
      Entity actor=getPassengers().stream().filter(p->getSeatIndex(p)==0).findFirst().orElse(null);
      long epoch=mountedEpoch;
      return () -> {
        if(epoch==mountedEpoch && actor instanceof LivingEntity living && living.isAlive() && living.level()==level()
            && hasPassenger(living) && getSeatIndex(living)==0 && canShoot(living)) scopedWork.run();
      };
    }
    DnvM1IndicatorAim aim = indicatorSubmitting;
    if (aim == null) return DnvM1RemoteCrew.guardQueuedShot(this, scopedWork);
    return () -> {
      try {
        if (indicatorPending != aim || !(level() instanceof ServerLevel server)) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(aim.actor());
        if (player == null || !indicatorValid(aim, player) || !getPassengers().isEmpty()
            || !indicatorAligned(aim) || getGunData("Main") == null
            || !getGunData("Main").canShoot(getAmmoSupplier())) return;
        scopedWork.run();
      } finally {
        if (indicatorPending == aim) indicatorPending = null;
      }
    };
  }

  @Override public void onNativeShotQueueResult(boolean accepted) {
    if (!accepted && indicatorSubmitting != null && indicatorPending == indicatorSubmitting)
      indicatorPending = null;
  }
  private void runNativeShotScope(Runnable work) {
    boolean outer=admittingProjectile;
    var previous=outer?admittedParameters:null;
    boolean previousAccepted=outer&&admittedProjectile;
    try { work.run(); }
    finally { admittedParameters=previous;admittedProjectile=previousAccepted;admittingProjectile=outer; }
  }

  @Override public void afterShoot(GunData gunData, Vec3 shootVec) {
    // Queue submission is not a successful world shot. Admission publishes feedback below.
  }

  @Override public boolean deferNativeShotEffects() { return true; }
  @Override public boolean deferNativeClientSound() { return true; }
  @Override public boolean permitsNativeClientFire(Entity operator,Entity target,boolean locked,GunData gun) {
    return operator instanceof LivingEntity living && gun==getGunData("Main") && canShoot(living);
  }
  @Override public boolean admitNativeProjectile(ServerLevel server, Entity projectile,
      com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) {
    if(admittingProjectile || server!=level() || isRemoved() || isDestroyed() || nativeStateInvalid || getLocked()
        || parameters.ammoSupplier!=this || parameters.data!=getGunData("Main")
        || parameters.level!=server || projectile.level()!=server) return false;
    boolean crew=parameters.shooter instanceof LivingEntity living && canShoot(living);
    boolean indicator=indicatorPending!=null && parameters.shooter instanceof ServerPlayer player
        && indicatorValid(indicatorPending,player) && indicatorAligned(indicatorPending) && getPassengers().isEmpty();
    if(!crew&&!indicator)return false;
    if(parameters.shootPosition.distanceToSqr(getShootPos("Main",1))>1e-8
        || parameters.shootDirection.distanceToSqr(getShootVec("Main",1))>1e-8)return false;
    int barrel=Math.floorMod(parameters.data.fireIndex.get(),profile().barrels);
    Vec3 direction=parameters.shootDirection.normalize();Vec3 lip=parameters.shootPosition.subtract(direction.scale(.03));
    admittedParameters=parameters;admittedProjectile=false;admittingProjectile=true;
    try {
      admittedProjectile=server.addFreshEntity(projectile);
      if(admittedProjectile) publishAcceptedShot(server,parameters,barrel,lip,direction);
      return admittedProjectile;
    } finally { admittingProjectile=false;if(!admittedProjectile)admittedParameters=null; }
  }
  @Override public boolean nativeProjectileAccepted(com.atsuishio.superbwarfare.data.gun.ShootParameters parameters) {
    if(parameters!=admittedParameters)return false;
    boolean result=admittedProjectile;admittedParameters=null;admittedProjectile=false;return result;
  }
  private void publishAcceptedShot(ServerLevel server,com.atsuishio.superbwarfare.data.gun.ShootParameters parameters,
      int barrel,Vec3 lip,Vec3 direction) {
    super.afterShoot(parameters.data,direction);
    super.playShootSound3p(parameters.shooter instanceof LivingEntity actor?actor:null,parameters.data,parameters.shootPosition);
    if(parameters.shooter!=null) com.atsuishio.superbwarfare.tools.MinecraftUtil.sendPacketToAll(
        new com.atsuishio.superbwarfare.network.message.receive.VehicleShootClientMessage(
            parameters.shooter.getUUID(),getUUID(),barrel,"Main"));
    server.broadcastEntityEvent(this,(byte)(80+barrel));
    if(mountedCrew()) {
      com.atsuishio.superbwarfare.tools.ParticleTool.spawnDirectionalParticles(1,.02,server,
          new com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareOption(1,1,1,4,.35F,1,.14F),direction,lip,.1);
      com.atsuishio.superbwarfare.tools.ParticleTool.spawnBarrelSmoke(1,server,direction,lip);
    } else if(profile().entity.equals("dragon_flak88")) {
      com.atsuishio.superbwarfare.tools.ParticleTool.spawnMediumCannonMuzzleParticles(direction,lip,server,this);
    } else {
      com.atsuishio.superbwarfare.tools.ParticleTool.spawnBigCannonMuzzleParticles(direction,lip,server,this);
    }
  }
  @Override public void handleEntityEvent(byte event) {
    int barrel=event-80;
    if(barrel>=0&&barrel<profile().barrels)visualRecoil.shot(barrel,tickCount);
    else super.handleEntityEvent(event);
  }
  public float barrelRecoil(int barrel,float partialTick) {
    return isDestroyed()?0:visualRecoil.progress(barrel,tickCount,partialTick,profile().recoilTicks);
  }
  public void clearVisualRecoil(){visualRecoil.reset();}
  @Override protected void addPassenger(Entity passenger){super.addPassenger(passenger);mountedEpoch++;}
  @Override protected void removePassenger(Entity passenger){super.removePassenger(passenger);mountedEpoch++;}

  @Override public void turretTurnSound(float diffX, float diffY, float pitch) {
    // M1 turns below SBW's stock 0.5-degree audio threshold; retain its native sound at this scale.
    super.turretTurnSound(diffX * 2.0F, diffY * 2.0F, pitch);
  }

  @Override public void adjustTurretAngle() {
    if (level().isClientSide) return;
    aimWithServo(super::adjustTurretAngle);
  }

  private void aimWithServo(Runnable aim) {
    if (isDestroyed() || nativeStateInvalid) return;
    if (mountedCrew()) { aim.run(); return; }
    long tick = level().getGameTime();
    if (!getActive() || !servoEnergy.canAim(tick, getEnergy())) return;
    float yaw = getTurretYRot(), pitch = getTurretXRot();
    aim.run();
    int cost = servoEnergy.charge(tick, yaw, pitch, getTurretYRot(), getTurretXRot());
    if (cost > 0) consumeEnergy(cost);
  }

  @Override public void tick() {
    if (!level().isClientSide && !mountedCrew() && (hasRemoteCrew() || !getPassengers().isEmpty()) && !DnvM1RemoteCrew.valid(this)) endRemoteCrew();
    if (nativeStateInvalid) return;
    super.tick();
    if (!level().isClientSide) tickIndicatorAim();
    synchronizeTurretPose();
  }

  /** Native turret fields are not continuously synchronized; the server owns both pose and FE. */
  private void synchronizeTurretPose() {
    if (level().isClientSide) {
      Rotations pose = entityData.get(SYNCED_TURRET_POSE);
      var motion = clientTurretPose.advance(pose);
      setTurretYRot(clientTurretPose.currentYaw(pose));
      setTurretXRot(clientTurretPose.currentPitch(pose));
      // Native seat/camera matrices lerp the raw O/current fields, not our visual getters.
      // Put both consumers on the same shortest arc, including the +/-180 boundary.
      setTurretYRotO(getTurretYRot()-Mth.wrapDegrees(getTurretYRot()-clientTurretPose.yaw(0,pose)));
      setTurretXRotO(getTurretXRot()-Mth.wrapDegrees(getTurretXRot()-clientTurretPose.pitch(0,pose)));
      turretTurnSound(motion.pitchDelta(), motion.yawDelta(), 0.95F);
    } else {
      entityData.set(SYNCED_TURRET_POSE, new Rotations(getTurretYRot(), getTurretXRot(), 0));
    }
  }

  @Override public float getTurretYaw(float partialTick) {
    return level().isClientSide
        ? clientTurretPose.yaw(partialTick, entityData.get(SYNCED_TURRET_POSE))
        : super.getTurretYaw(partialTick);
  }

  @Override public float getTurretPitch(float partialTick) {
    return level().isClientSide
        ? clientTurretPose.pitch(partialTick, entityData.get(SYNCED_TURRET_POSE))
        : super.getTurretPitch(partialTick);
  }

  private void tickIndicatorAim() {
    if (!(level() instanceof ServerLevel server) || indicatorAim == null) return;
    DnvM1IndicatorAim aim = indicatorAim;
    ServerPlayer player = server.getServer().getPlayerList().getPlayer(aim.actor());
    if (player == null || !indicatorValid(aim, player) || !getPassengers().isEmpty()) {
      indicatorAim = null;
      indicatorPending = null;
      indicatorRequested = false;
      return;
    }
    aimWithServo(() -> turretAutoAimFromVector(aim.launch()));
    if (!indicatorRequested || indicatorPending != null || !indicatorAligned(aim)) return;
    indicatorRequested = false;
    indicatorPending = aim;
    indicatorSubmitting = aim;
    try {
      // Use the existing SBW GunData, named weapon and queued projectile path, not a second ammo ledger.
      super.vehicleShoot(player, "Main", null, aim.target().getCenter());
    } finally {
      indicatorSubmitting = null;
    }
  }

  @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
      net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
    if (nativeStateInvalid && (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER
        || capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY))
      return net.minecraftforge.common.util.LazyOptional.empty();
    return super.getCapability(capability, side);
  }

    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
    if (!level().isClientSide) endRemoteCrew();
    indicatorAim = null;
    indicatorPending = null;
    indicatorSubmitting = null;
    indicatorRequested = false;
    }


  public float getVisualBaseYaw(float partialTick) { return Mth.rotLerp(partialTick, yRotO, getYRot()); }
  public float getVisualAimYaw(float partialTick) { return getVisualBaseYaw(partialTick) - getTurretYaw(partialTick); }
  public float getVisualAimElevation(float partialTick) { return -getTurretPitch(partialTick); }
  public Vec3 getAdsCameraPosition(float partialTick) {
    return position().add(profile().posed(profile().ads,getVisualAimYaw(partialTick),getVisualAimElevation(partialTick)));
  }
  public float getHealthRatio() { return Mth.clamp(getHealth() / Math.max(1,getMaxHealth()), 0, 1); }
  public boolean isDamaged() { return getHealth() <= getMaxHealth()*.6F; }
  public boolean isDestroyed() { return isWreck() || getHealth() <= 0; }
  /** Mounted guns need no FE or activation; remote servos use synced native power and Active. */
  public boolean hasOperationalPower() {
    return !nativeStateInvalid && !isDestroyed() && (mountedCrew() || getActive() && hasEnergyStorage() && getEnergy() > 0);
  }
  @Override public String remoteCrewReadiness() {
    if(nativeStateInvalid||isDestroyed()||getLocked())return "unavailable";
    if(mountedCrew())return "mounted";
    if(!getActive())return "inactive";
    if(!hasOperationalPower())return "unpowered";
    var gun=getGunData("Main");
    if(gun==null || gun.ammo.get()<=0 && gun.virtualAmmo.get()<=0 && !gun.reloading() && !gun.hasBackupAmmo(this))return "no_ammo";
    return "ready";
  }
  @Override public AABB getBoundingBoxForCulling() {
    double r=profile().renderRadius;
    return new AABB(-r,-r,-r,r,r,r).move(position());
  }
  public float getRecoilProgress(float partialTick) {
    float phase = Mth.clamp((entityData.get(CANNON_RECOIL_TIME) - partialTick) / RECOIL_TICKS, 0, 1);
    return phase * phase * (3 - 2 * phase);
  }
  public String fireReadinessReason() { return nativeStateInvalid ? "WEAPON_RESOURCE_STATE_INVALID" : "NATIVE_CREW"; }
  public List<AABB> deploymentClearance() {
    return profile().clearance(getYRot()).stream().map(box -> box.move(position())).toList();
  }

  @Override protected void readAdditionalSaveData(CompoundTag tag) {
    super.m_7378_(tag);
    clearRemoteCrew(); // A saved/captured turret never restores an occupied remote seat.
    indicatorAim = null;
    indicatorPending = null;
    indicatorSubmitting = null;
    indicatorRequested = false;
    // New M1 state uses SBW's native resources. Unknown saved schemas stay inert.
    nativeStateInvalid = !tag.contains(NATIVE_SCHEMA, Tag.TAG_INT)
        || tag.getInt(NATIVE_SCHEMA) != 1
        || tag.getBoolean("DnvM1YunlongNativeInvalid");
  }

  @Override public void addAdditionalSaveData(CompoundTag tag) {
    super.m_7380_(tag);
    tag.putInt(NATIVE_SCHEMA, 1);
    if (nativeStateInvalid) tag.putBoolean("DnvM1YunlongNativeInvalid", true);
  }
}

package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.entity.vehicle.AnnihilatorEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.boss.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import com.liucodex.doggiesnewvehicle.combat.DnvDamageTypes;
import com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.core.MothershipDefense;
import com.liucodex.doggiesnewvehicle.core.MothershipAnnihilatorDefense;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

/** Registered boss owner. No client decision, player seat or generic aircraft mission state. */
public final class DnvMothershipEntity extends VehicleEntity implements DnvCombatPolicy.BossAutonomy,
        com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget, com.liucodex.doggiesnewvehicle.api.combat.DnvWreckLifecycle,
        com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor, com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
    @Override public Tier coldArmorTier() { return Tier.HEAVY; }
    public static final float MAX_HEALTH = 8_000;
    private static final DamageModifier IDENTITY_DAMAGE_MODIFIER = new DamageModifier();
    private static final String SAVE = "DnvMothership";
    private static final String PARENT = "DnvMothershipParent";
    private static final List<EntityDataAccessor<Float>> YAW = floats(6), ELEVATION = floats(6);
    private static final EntityDataAccessor<CompoundTag> FX = SynchedEntityData.defineId(DnvMothershipEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(DnvMothershipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ENTRY_AGE = SynchedEntityData.defineId(DnvMothershipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> ENTRY_ANCHOR = SynchedEntityData.defineId(DnvMothershipEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final int[][] DROP_OFFSETS = {{10,0},{0,10},{-10,0},{0,-10},{8,8},{-8,8},{8,-8},{-8,-8}};
    private static final int[][] BELOW_OFFSETS = {{0,0},{0,6},{0,-6},{4,0},{-4,0}};

    private final MothershipRig rig = MothershipRig.get();
    private final MothershipArmament armament = new MothershipArmament();
    private final MothershipDoomsday doomsday = new MothershipDoomsday();
    private final MothershipDrops drops = new MothershipDrops();
    private final MothershipTactics tactics = new MothershipTactics();
    private final MothershipFlight flight = new MothershipFlight();
    private final MothershipCrash crashSequence = new MothershipCrash();
    private final MothershipTerrainBlast[] terrainBlasts = {
            new MothershipTerrainBlast(4), new MothershipTerrainBlast(8),
            new MothershipTerrainBlast(12), new MothershipTerrainBlast(16)};
    private final MothershipNavigation navigation = new MothershipNavigation();
    private final DnvTargetCandidateCache candidates = new DnvTargetCandidateCache(80);
    private final com.liucodex.doggiesnewvehicle.compat.DnvTargetSight pursuitSight = new com.liucodex.doggiesnewvehicle.compat.DnvTargetSight(0);
    private final com.liucodex.doggiesnewvehicle.compat.DnvTargetSight dropSight = new com.liucodex.doggiesnewvehicle.compat.DnvTargetSight(7);
    private final com.liucodex.doggiesnewvehicle.compat.DnvTargetSight[] stationSights = java.util.stream.IntStream.range(1, 7)
            .mapToObj(com.liucodex.doggiesnewvehicle.compat.DnvTargetSight::new).toArray(com.liucodex.doggiesnewvehicle.compat.DnvTargetSight[]::new);
    private final MothershipStationTracking stationTracking = new MothershipStationTracking();
    private final int[] rocketTube = new int[6];
    private final Angles[] poses = new Angles[6], previousDisplay = new Angles[6], display = new Angles[6];
    private List<com.atsuishio.superbwarfare.tools.OBB> hullBoxes;
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.doggievehicle.ses_executor_of_truth"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private Vec3 home, pursuitOrigin, course = Vec3.ZERO;
    private Vec3 idleDestination;
    private long nextIdle, idleDeadline;
    private double idleClearance = MothershipTactics.GROUND_CLEARANCE;
    private double encounterGround;
    private boolean approaching, retreating, returning;
    private long returnProgressAt = -1;
    private double returnDistance = Double.POSITIVE_INFINITY;
    private boolean arrivalStarted;
    private int arrivalTicks;
    private int entryAge;
    private Vec3 beaconAnchor;
    private long annihilatorBudgetTick = Long.MIN_VALUE;
    private final Map<UUID, Float> annihilatorDamageBySource = new HashMap<>();
    private long nextNavigation, nextDrop, shotStarted = -1;
    private UUID combatTarget, doomTarget, glowingTarget;
    private Vec3 fixedBeamEnd, beamEnd = Vec3.ZERO, doomGround = Vec3.ZERO;
    private boolean beamImpact;
    private MothershipDoomsday.Update weapon = new MothershipDoomsday.Update(MothershipDoomsday.Phase.READY,-1,0,null,0,0);

    public DnvMothershipEntity(EntityType<? extends DnvMothershipEntity> type, Level level) {
        super(type,level);
        setNoGravity(true);
        setHealth(MAX_HEALTH);
        for(int i=0;i<6;i++) poses[i]=previousDisplay[i]=display[i]=rig.rest(i);
        updateOBB();refreshBoundingBoxFromOBBs();
    }

    private static List<EntityDataAccessor<Float>> floats(int count) {
        List<EntityDataAccessor<Float>> ids=new ArrayList<>();
        for(int i=0;i<count;i++) ids.add(SynchedEntityData.defineId(DnvMothershipEntity.class,EntityDataSerializers.FLOAT));
        return List.copyOf(ids);
    }
    @Override protected void defineSynchedData() {
        super.m_8097_();
        for(int i=0;i<6;i++) {
            Angles rest=MothershipRig.get().rest(i);
            entityData.define(YAW.get(i),(float)rest.yaw()); entityData.define(ELEVATION.get(i),(float)rest.elevation());
        }
        entityData.define(FX,new CompoundTag()); entityData.define(THROTTLE,0F);
        entityData.define(ENTRY_AGE,0);entityData.define(ENTRY_ANCHOR,new CompoundTag());
    }
    @Override public float getMaxHealth() { return MAX_HEALTH; }
    @Override public void travel() { /* tick() is the only flight executor. */ }
    @Override public void updateOBB() {hullBoxes=collisionPose(position(),getYRot());}
    @Override public List<com.atsuishio.superbwarfare.tools.OBB> getOBBs() {
        if(hullBoxes==null)updateOBB();return hullBoxes;
    }
    @Override public void refreshBoundingBoxFromOBBs() {setBoundingBox(MothershipHull.bounds(getOBBs()));}
    public List<com.atsuishio.superbwarfare.tools.OBB> collisionPose(Vec3 bottom,double yaw) {
        return collisionPose(bottom,yaw,getXRot(),getRoll());
    }
    public List<com.atsuishio.superbwarfare.tools.OBB> collisionPose(Vec3 bottom,double yaw,double pitch,double roll) {
        Angles[] current=poses;
        if(level().isClientSide&&display!=null)current=display;
        return MothershipHull.pose(bottom,yaw,pitch,roll,current);
    }
    // SBW baseTick adds gravity and calls move outside travel(). Only our validated setPos may move this boss.
    @Override public void move(MoverType type, Vec3 motion) { }
    @Override public void setPos(double x, double y, double z) {
        // Another vehicle's support callback writes setPos directly, bypassing move/push overrides.
        if (crashSequence != null && crashSequence.state() != MothershipCrash.ALIVE && !level().isClientSide) return;
        super.setPos(x, y, z);
    }
    @Override public void moveOnDragonTeeth() { }
    @Override public void collideBlocks() { }
    // Run once after our own flight has committed its pose, so deck passengers inherit this tick's motion.
    @Override public void supportEntities() { }
    @Override public void crushEntities() { }
    private void supportAfterMovement() {
        if(!entryActive())return;
        super.supportEntities();
        super.crushEntities();
    }
    @Override public void bounceHorizontal(net.minecraft.core.Direction direction) { }
    @Override public void bounceVertical(net.minecraft.core.Direction direction) { }
    @Override public void keepChunkLoaded(Vec3 ignored) { /* Even a datapack cannot add chunk tickets for this boss. */ }
    @Override public void towedTick() { }
    @Override public void towingTick() { }
    @Override public void preventStacking() { }
    @Override public void push(double x,double y,double z) { }
    @Override public void pushNew(double x,double y,double z) { }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return entryActive() && super.isPickable(); }
    @Override public boolean canBeCollidedWith() { return entryActive() && super.canBeCollidedWith(); }
    @Override public boolean isAttackable() { return entryActive() && super.isAttackable(); }
    @Override public boolean engineRunning() { return false; }
    @Override public void vehicleExplosion(com.atsuishio.superbwarfare.data.vehicle.subdata.DestroyInfo ignored) {
        // The crash sequence owns the sole entity-only impact; even a datapack cannot add a second terrain blast.
    }
    @Override public boolean dnvWreckReadyForCleanup() { return crashSequence.state()==MothershipCrash.FINISHED; }
    @Override public int dnvWreckLifetimeTicks() { return 1; }
    @Override public boolean dnvShowWreckHealth() { return false; }
    @Override public boolean dnvShowVehicleMarker() { return entryActive(); }
    @Override public void openCustomInventoryScreen(Player player) { }
    @Override public net.minecraft.world.InteractionResult m_6096_(Player player,net.minecraft.world.InteractionHand hand) {
        if(!entryActive())return net.minecraft.world.InteractionResult.FAIL;
        if(DnvBossInteraction.isRetrievalTool(player.getItemInHand(hand)))return DnvBossInteraction.rejectRetrieval(player);
        return super.m_6096_(player,hand);
    }
    @Override public net.minecraft.world.InteractionResult onCrowbarInteract(net.minecraft.world.item.ItemStack stack,
            Player player, net.minecraft.world.InteractionHand hand) {
        return DnvBossInteraction.rejectRetrieval(player);
    }
    @Override public void vehicleShoot(LivingEntity living,UUID target,Vec3 position) { }
    @Override public void vehicleShoot(LivingEntity living,String weapon,UUID target,Vec3 position) { }
    @Override public void vehicleShoot(LivingEntity living,String weapon,Vec3 position) { }

    @Override public void tick() {
        setNoGravity(true);
        if(!level().isClientSide) setDeltaMovement(Vec3.ZERO);
        super.tick();
        if(level().isClientSide) {
            for(int i=0;i<6;i++) {
                previousDisplay[i]=display[i];
                display[i]=new Angles(display[i].yaw()+MothershipRig.wrap(entityData.get(YAW.get(i))-display[i].yaw())*.5,
                        Mth.lerp(.5,display[i].elevation(),entityData.get(ELEVATION.get(i))));
            }
            updateOBB();refreshBoundingBoxFromOBBs();supportAfterMovement();
            return;
        }
        ServerLevel level=(ServerLevel)level();
        setDeltaMovement(Vec3.ZERO);
        if(isRemoved())return;
        // Disk-loaded duplicate ships are retained, but only the dimension's lease holder acts.
        if(!MothershipPresence.get(level).active(getUUID(),blockPosition())) {
            bossEvent.setVisible(false);bossEvent.removeAllPlayers();return;
        }
        Vec3 ignition = null;
        for (MothershipTerrainBlast terrainBlast : terrainBlasts) {
            if (!terrainBlast.active()) continue;
            ignition = terrainBlast.tick(this);
            break; // One bounded block batch per ship and server tick, even while pulses overlap.
        }
        if (ignition != null) igniteGround(level, ignition, MothershipBlastFront.RADIUS);
        if(getHealth()<=0 || isWreck() || crashSequence.state()!=MothershipCrash.ALIVE) {tickCrash();return;}
        long now=level.getGameTime();
        if(!tickEntry(level))return;
        tickDropCharge(now);
        updateBossBar(level);
        if(home==null) {
            double ground=MothershipWorld.groundBelow(level,position(),getYRot());
            if(!Double.isFinite(ground)) return;
            encounterGround=ground;home=new Vec3(getX(),ground+MothershipTactics.GROUND_CLEARANCE,getZ());
            DnvBossTeamMembership.assignIfUnassigned(this);
        }
        refreshCandidates();
        Entity target=resolveTarget(level,now);
        tickDoomsday(level,target,now);
        moveShip(level,target,now);
        tickStations(level,now);
        updateOBB();refreshBoundingBoxFromOBBs();
        supportAfterMovement();
        tickDrops(level,now);
    }

    private void beginCrash() {
        if(!crashSequence.begin())return;
        setHealth(0);setWreck(true);clearCombat();entityData.set(THROTTLE,0F);syncFx();
    }
    private void tickCrash() {
        beginCrash();
        if(crashSequence.state()==MothershipCrash.FINISHED) {
            com.liucodex.doggiesnewvehicle.compat.DnvWreckLifetime.finish(this);return;
        }
        if(crashSequence.state()==MothershipCrash.LANDED) {
            setDeltaMovement(Vec3.ZERO);setOnGround(true);updateOBB();refreshBoundingBoxFromOBBs();supportAfterMovement();
            crashSequence.advance();
            if(crashSequence.finish()) {
                try {MothershipWeapons.crashImpact(this);}
                finally {com.liucodex.doggiesnewvehicle.compat.DnvWreckLifetime.finish(this);}
            }
            return;
        }
        crashSequence.advance();
        if(crashSequence.timedOut()) {
            // Void/missing surrounding chunks: bounded cleanup without forcing terrain or an airborne impact.
            com.liucodex.doggiesnewvehicle.compat.DnvWreckLifetime.finish(this);return;
        }
        if(!crashSequence.planned())crashSequence.plan(getY(),MothershipWorld.wreckLandingY(this));
        if(!crashSequence.planned())return;
        Vec3 before=position(),next=new Vec3(getX(),Math.max(getY()-MothershipCrash.BLOCKS_PER_TICK,Math.min(getY(),crashSequence.nextY())),getZ());
        double pitch=Mth.approach(getXRot(),(float)crashSequence.pitch(),.12F);
        double roll=Mth.approach(getRoll(),(float)crashSequence.roll(),.08F);
        // At most 16 samples, each <= .25 blocks of combined translation/rotating-tip travel.
        // A thin roof must stop the descent even if the final pose has already passed through it.
        int steps=Math.min(16,Math.max(1,(int)Math.ceil((before.distanceTo(next)
                +90*Math.toRadians(Math.abs(pitch-getXRot())+Math.abs(roll-getRoll())))*4)));
        int available=1;double low=0,high=1;
        var candidates=MothershipWorld.wreckCandidates(this,next,pitch,roll);
        for(int i=1;i<=steps;i++) {
            double fraction=i/(double)steps;
            available=MothershipWorld.wreckPose(this,before.lerp(next,fraction),
                    Mth.lerp(fraction,getXRot(),pitch),Mth.lerp(fraction,getRoll(),roll),candidates);
            if(available<0){crashSequence.hold();return;}
            if(available==0){high=fraction;break;}
            low=fraction;
        }
        if(available==0) {
            for(int i=0;i<8;i++) {
                double fraction=(low+high)*.5;
                Vec3 trial=before.lerp(next,fraction);
                int clear=MothershipWorld.wreckPose(this,trial,Mth.lerp(fraction,getXRot(),pitch),Mth.lerp(fraction,getRoll(),roll),candidates);
                if(clear==1)low=fraction;else high=fraction;
            }
            next=before.lerp(next,low);pitch=Mth.lerp(low,getXRot(),pitch);roll=Mth.lerp(low,getRoll(),roll);
        }
        super.setPos(next.x,next.y,next.z);setXRot((float)pitch);setRoll((float)roll);setDeltaMovement(next.subtract(before));
        updateOBB();refreshBoundingBoxFromOBBs();supportAfterMovement();
        if(available>0 && crashSequence.localExplosionDue())MothershipWeapons.fallingExplosion(this,crashSequence.age());
        if(available==0 && crashSequence.impact()) {
            setDeltaMovement(Vec3.ZERO);setOnGround(true);
        }
    }

    private boolean tickEntry(ServerLevel level) {
        if(entryActive())return true;
        if(!MothershipWorld.loaded(level,getBoundingBox()))return false;
        if(beaconAnchor==null) {
            double ground=MothershipWorld.groundBelow(level,position(),getYRot());
            if(!Double.isFinite(ground))return false;
            setBeaconAnchor(new Vec3(getX(),ground,getZ()));
        }
        if(entryAge==0)level.playSound(null,beaconAnchor.x,beaconAnchor.y,beaconAnchor.z,
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,SoundSource.HOSTILE,2F,.75F);
        entryAge=MothershipArrival.advance(entryAge);
        entityData.set(ENTRY_AGE,entryAge);
        if(entryAge==MothershipArrival.BEACON_TICKS)level.playSound(null,getX(),getY(),getZ(),
                net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,SoundSource.HOSTILE,4F,.72F);
        return entryActive();
    }
    private void tickDropCharge(long now) {
        if(!arrivalStarted) {
            arrivalStarted=true;
        }
        arrivalTicks=Math.min(MothershipDrops.SECOND_CHARGE_TICKS,arrivalTicks+1);
    }

    private Entity resolveTarget(ServerLevel level,long now) {
        UUID old=tactics.target();Entity attacker=old==null?null:level.getEntity(old);
        Boolean attackerVisible=legalTarget(attacker,MothershipTactics.RETALIATION_RANGE)
                ?pursuitSight.visible(this,world(rig.doomsday),attacker):false;
        if(attackerVisible==null)return null;
        UUID pursuit=tactics.pursuitTarget(now,attacker==null?Double.POSITIVE_INFINITY:horizontal(attacker.position()),
                pursuitOrigin==null?0:horizontal(pursuitOrigin),Boolean.TRUE.equals(attackerVisible));
        if(old!=null && pursuit==null){returning=true;combatTarget=null;nextNavigation=0;
            returnProgressAt=now;returnDistance=horizontal(returnPoint());}
        if(returning) {
            double remaining=horizontal(returnPoint());
            if(remaining<1){returning=false;pursuitOrigin=null;}
            else {
                if(returnProgressAt<0 || remaining<returnDistance-.5){returnProgressAt=now;returnDistance=remaining;}
                if(now-returnProgressAt<200)return null;
                // A blocked return path must not blind the boss to nearby threats forever.
                returning=false;pursuitOrigin=null;nextNavigation=0;
            }
            returnProgressAt=-1;returnDistance=Double.POSITIVE_INFINITY;
        }
        if(pursuit!=null) {combatTarget=pursuit;return attacker;}
        Entity current=combatTarget==null?null:level.getEntity(combatTarget);
        if(legalTarget(current,MothershipTactics.TARGET_RANGE)
                && pursuitSight.visible(this,world(rig.doomsday),current)==null)return null;
        Entity selected=pursuitSight.select(this,world(rig.doomsday),candidates.candidates(this),current,
                e->legalTarget(e,MothershipTactics.TARGET_RANGE));
        combatTarget=selected==null?null:selected.getUUID();
        return selected;
    }

    private void refreshCandidates() {
        candidates.refreshScoredOnStartCadence(this, MothershipTactics.DISCOVERY_RANGE,
                MothershipTactics.MAX_TARGET_HEIGHT_DIFFERENCE + 16, this::candidatePriority);
    }
    private double candidatePriority(Entity target) {
        if (!stationTargetAllowed(target)) return Double.POSITIVE_INFINITY;
        double distance = distanceToSqr(target);
        if (legalTarget(target, MothershipTactics.TARGET_RANGE)) return distance;
        for(var station:MothershipArmament.Station.values()) {
            if (MothershipWeapons.compatibleWithStation(station, target) && MothershipWeapons.inStationRange(station, stationMuzzle(station.ordinal()),
                    target.getBoundingBox().getCenter(), 8)) return distance;
        }
        return DnvTargetExclusions.isSporeScent(target) ? Double.POSITIVE_INFINITY
                : MothershipTactics.distantDiscoveryScore(distance, horizontal(target.position()), target.getY() - getY());
    }
    private boolean legalTarget(Entity target,double range) {
        return stationTargetAllowed(target) && !DnvTargetExclusions.isSporeScent(target) && horizontal(target.position()) <= range
                && Math.abs(target.getY() - getY()) <= MothershipTactics.MAX_TARGET_HEIGHT_DIFFERENCE;
    }
    private boolean stationTargetAllowed(Entity target) {
        return MothershipWeapons.stationTargetAvailable(this, target)
                && !(target.getPersistentData().hasUUID(PARENT) && getUUID().equals(target.getPersistentData().getUUID(PARENT)));
    }
    private double horizontal(Vec3 point) {return Math.hypot(point.x-getX(),point.z-getZ());}
    private Vec3 returnPoint() {return pursuitOrigin==null?home:pursuitOrigin;}

    private void updateIdle(Entity target,long now,double ground) {
        if(target!=null || returning) {
            idleDestination=null;nextIdle=now+100;idleClearance=MothershipTactics.GROUND_CLEARANCE;return;
        }
        if(home==null || !Double.isFinite(ground))return;
        if(idleDestination!=null && (now>=idleDeadline || horizontal(idleDestination)<.5 && Math.abs(getY()-ground-idleClearance)<.3)) {
            idleDestination=null;nextIdle=now+100+random.nextInt(101);
        }
        if(nextIdle==0)nextIdle=now+100+random.nextInt(101);
        if(idleDestination==null && now>=nextIdle) {
            var leg=MothershipPatrol.choose(v(position()),v(home),getY()-ground,random.nextDouble(),random.nextDouble(),random.nextDouble());
            idleDestination=mc(leg.destination());idleClearance=leg.clearance();idleDeadline=now+MothershipPatrol.LEG_TIMEOUT;
        }
    }

    private void moveShip(ServerLevel level,Entity target,long now) {
        if(weapon.movementLocked()) {
            idleDestination=null;nextIdle=now+100;nextNavigation=0;
            var fixed=weapon.firingPose();setPos(fixed.x(),fixed.y(),fixed.z());setYRot(fixed.yaw());setXRot(0);
            flight.advance(now,false,weapon);course=Vec3.ZERO;setDeltaMovement(Vec3.ZERO);entityData.set(THROTTLE,0F);return;
        }
        if((target!=null || returning) && idleDestination!=null)nextNavigation=0;
        double ground=now>=nextNavigation?MothershipWorld.groundBelow(level,position(),getYRot()):Double.NaN;
        if(now>=nextNavigation)updateIdle(target,now,ground);
        Vec3 destination=target==null?(returning?returnPoint():idleDestination==null?home:idleDestination):target.position();
        double desiredYaw=target==null&&idleDestination==null&&!returning?getYRot():Math.toDegrees(Math.atan2(getX()-destination.x,destination.z-getZ()));
        float yaw=(float)MothershipAim.turnHull(getYRot(),desiredYaw);
        if(Math.abs(MothershipRig.wrap(yaw-getYRot()))>1e-5 && !MothershipWorld.safeStep(this,position(),yaw,false))yaw=getYRot();
        // Course/terrain work is bounded and staggered; movement safety still runs for every actual step.
        if(now>=nextNavigation) {
            nextNavigation=now+10;
            double maxY=MothershipTactics.maximumBottomY(encounterGround,level.getMaxBuildHeight(),rig.dimensions.y()+.25);
            if(Double.isFinite(ground))maxY=Math.min(maxY,ground+MothershipTactics.MAX_GROUND_CLEARANCE);
            double clearance=target==null&&!returning?idleClearance:MothershipTactics.GROUND_CLEARANCE;
            double desiredY=Math.min(maxY,ground+clearance);
            approaching=returning?horizontal(destination)>1
                    :target==null?idleDestination!=null&&horizontal(destination)>.5
                    :MothershipTactics.shouldApproach(horizontal(destination),approaching);
            retreating=!returning&&target!=null&&MothershipTactics.shouldRetreat(horizontal(destination),retreating);
            Vec3 horizontal=approaching||retreating?new Vec3(destination.x-getX(),0,destination.z-getZ()).normalize().scale(retreating?-1:1):Vec3.ZERO;
            Vec3 ahead=position().add(horizontal.scale(2));
            double aheadGround=MothershipWorld.groundBelow(level,ahead,yaw);
            if(Double.isFinite(aheadGround)&&aheadGround+clearance<=maxY)desiredY=Math.max(desiredY,aheadGround+clearance);
            if(!Double.isFinite(ground)||ground+clearance>maxY) {
                // A tower blocks this route; preserve the original datum and look for a lateral path.
                desiredY=Math.min(maxY,Math.max(encounterGround+clearance,getY()));
            }
            if(Math.abs(desiredY-getY())>.25) {
                Vec3 vertical=new Vec3(0,Math.signum(desiredY-getY()),0);
                course=MothershipWorld.safePose(this,position().add(vertical.scale(.5)),yaw)?vertical:Vec3.ZERO;
            } else if(horizontal.lengthSqr()>0) {
                final float plannedYaw=yaw;
                final double ceiling=maxY;
                course=mc(navigation.choose(now,v(horizontal),candidate->{
                    Vec3 probe=position().add(mc(candidate).scale(2));
                    double top=MothershipWorld.groundBelow(level,probe,plannedYaw)+clearance;
                    return top<=ceiling && top<=getY()+.5 && MothershipWorld.safePose(this,probe,plannedYaw);
                }));
            } else course=Vec3.ZERO;
        }
        var motion=flight.advance(now,course.lengthSqr()>0,weapon);
        Vec3 next=position().add(course.scale(motion.blocksPerTick()));
        if(next.equals(position()) && Math.abs(MothershipRig.wrap(yaw-getYRot()))<1e-5 && tickCount%20!=0) {
            setDeltaMovement(Vec3.ZERO);entityData.set(THROTTLE,0F);return;
        }
        if(!MothershipWorld.safeStep(this,next,yaw,tickCount%20==0)) {
            course=Vec3.ZERO;nextNavigation=now+10;flight.advance(now,false,weapon);setDeltaMovement(Vec3.ZERO);entityData.set(THROTTLE,0F);return;
        }
        Vec3 actual=next.subtract(position());setPos(next.x,next.y,next.z);setYRot(yaw);setXRot(0);setDeltaMovement(actual);
        if(tickCount%2==0)entityData.set(THROTTLE,(float)(actual.length()/MothershipFlight.MAX_BLOCKS_PER_TICK));
    }

    private void tickStations(ServerLevel level,long now) {
        var stations=MothershipArmament.Station.values();
        // One solution per station/target in this update, shared by selection, tracking and fire.
        List<Map<UUID,Vec3>> aimPoints=new ArrayList<>(stations.length);
        for(var ignored:stations)aimPoints.add(new HashMap<>());
        List<Entity> selected=stationTracking.select(now,level::getEntity,()->{
            List<Entity> live=candidates.candidates(this);
            for(var station:stations) {
                int i=station.ordinal();UUID old=stationTracking.targetId(i);
                stationSights[i].select(this,stationMuzzle(i),live,old==null?null:level.getEntity(old),
                        target -> stationTargetAllowed(target) && MothershipWeapons.compatibleWithStation(station, target));
            }
            return live;
        },
                Entity::getUUID,this::distanceToSqr,(index,target,grace)->stationEligible(index,target,aimPoints.get(index),grace));
        for(var station:stations) {
            int i=station.ordinal();
            Entity target=selected.get(i);
            if(!stationTargetAllowed(target) || !Boolean.TRUE.equals(
                    stationSights[i].visible(this,stationMuzzle(i),target)))continue;
            Vec3 aimPoint=stationAimPoint(i,target,aimPoints.get(i));
            Angles desired=rig.aim(i,model(aimPoint));
            Angles limited=rig.clamp(i,desired),old=poses[i];
            poses[i]=new Angles(old.yaw()+Mth.clamp(MothershipRig.wrap(limited.yaw()-old.yaw()),-3,3),
                    old.elevation()+Mth.clamp(limited.elevation()-old.elevation(),-2,2));
            if(tickCount%2==0)syncMount(i);
            if(!stationTracking.mayAttempt(i,now) || !armament.ready(station,now))continue;
            if(!MothershipWeapons.inStationRange(station,stationMuzzle(i),aimPoint,0))continue;
            if(!rig.permits(i,desired) || Math.abs(MothershipRig.wrap(desired.yaw()-poses[i].yaw()))>1
                    || Math.abs(desired.elevation()-poses[i].elevation())>1) {
                stationTracking.stalled(i);continue;
            }
            final Entity shotTarget=target;
            int fired=armament.tryFire(station,now,(mount,barrel)->{
                int tube=mount.kind==MothershipArmament.Kind.HE_ROCKET?rocketTube[i]:barrel;
                Vec3 muzzle=world(rig.muzzle(i,tube,poses[i]));
                Vec3 end=aimPoint,direction=end.subtract(muzzle).normalize();
                if(rig.obstructed(model(muzzle.add(direction.scale(.05))),model(end),poses))return false;
                boolean accepted=MothershipWeapons.fireSecondary(mount,barrel,this,shotTarget,muzzle,aimPoint);
                if(accepted && mount.kind==MothershipArmament.Kind.HE_ROCKET)rocketTube[i]^=1;
                return accepted;
            });
            stationTracking.recordShot(i,now,fired>0);
            if(fired>0){
                MothershipWeapons.playStationSound(station,this,world(rig.muzzle(i,0,poses[i])));
            }
        }
    }
    private Vec3 stationMuzzle(int index) {
        var station=MothershipArmament.Station.values()[index];
        return world(rig.muzzle(index,station.kind==MothershipArmament.Kind.HE_ROCKET?rocketTube[index]:0,poses[index]));
    }
    private Vec3 stationAimPoint(int index,Entity target,Map<UUID,Vec3> points) {
        return points.computeIfAbsent(target.getUUID(),ignored->MothershipWeapons.aimPoint(
                MothershipArmament.Station.values()[index],target,stationMuzzle(index)));
    }
    private boolean stationEligible(int index,Entity target,Map<UUID,Vec3> points,double grace) {
        var station = MothershipArmament.Station.values()[index];
        if (!stationTargetAllowed(target) || !MothershipWeapons.compatibleWithStation(station, target)) return false;
        Boolean visible=stationSights[index].visible(this,stationMuzzle(index),target);
        if(!Boolean.TRUE.equals(visible) && !(visible==null && target.getUUID().equals(stationTracking.targetId(index))))return false;
        Vec3 point=stationAimPoint(index,target,points);
        return MothershipWeapons.inStationRange(station,stationMuzzle(index),point,grace)
                && rig.permits(index,rig.aim(index,model(point)));
    }
    private void syncMount(int index) {
        entityData.set(YAW.get(index),(float)poses[index].yaw());entityData.set(ELEVATION.get(index),(float)poses[index].elevation());
    }

    private void tickDoomsday(ServerLevel level,Entity selected,long now) {
        Entity locked=doomTarget==null?null:level.getEntity(doomTarget);
        boolean fired=weapon.phase()==MothershipDoomsday.Phase.FIRING;
        if(!fired && doomTarget!=null && !doomEligible(locked,false)) {
            abortDoom(now,locked==null || !locked.isAlive());
            locked=null;
        }
        if(doomTarget==null && !fired && doomsday.ready(now) && doomEligible(selected,true) && doomsday.begin(now)) {
            doomTarget=selected.getUUID();locked=selected;shotStarted=now;fixedBeamEnd=null;
            doomGround=selected.position();
            if(selected instanceof LivingEntity living)
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400,2,false,true,true),this);
        }
        Vec3 muzzle=world(rig.doomsday);
        if(locked!=null && !fired) {
            beamEnd=locked.getBoundingBox().getCenter();
            doomGround=locked.position();
        }
        Vec3 delta=beamEnd.subtract(muzzle);
        float pitch=(float)Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        weapon=doomsday.advance(now,new MothershipDoomsday.Pose(getX(),getY(),getZ(),getYRot(),pitch));
        if(weapon.movementLocked()) {
            if(fixedBeamEnd==null)fixedBeamEnd=muzzle.add(beamEnd.subtract(muzzle).normalize().scale(com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx.MAX_LENGTH));
            var trace=MothershipWorld.trace(this,muzzle,fixedBeamEnd);
            beamEnd=trace.end();beamImpact=trace.impact();
            for(int ordinal=1;ordinal<=4;ordinal++)if((weapon.hitMask()&(1<<(ordinal-1)))!=0) {
                var hit=doomsday.hit(ordinal);
                for(Entity pierced:MothershipWorld.intersected(this,muzzle,trace.end()))
                    MothershipWeapons.doomsdayDamage(this,pierced,hit,false);
                if(hit.blastDamage()>0 && MothershipWorld.loaded(level,new AABB(doomGround,doomGround))) {
                    blast(level,doomGround,hit);
                    MothershipWeapons.playDoomsdayImpact(this,doomGround,hit);
                }
            }
        } else if(weapon.phase()==MothershipDoomsday.Phase.LOCKING || weapon.phase()==MothershipDoomsday.Phase.CHARGING) {
            var trace=MothershipWorld.trace(this,muzzle,beamEnd);beamImpact=trace.impact();
            if(trace.end().distanceToSqr(beamEnd)>.01){abortDoom(now,false);weapon=doomsday.advance(now,new MothershipDoomsday.Pose(getX(),getY(),getZ(),getYRot(),0));}
        }
        if((weapon.soundMask()&1)!=0) level.playSound(null,muzzle.x,muzzle.y,muzzle.z,
                MothershipSounds.LOCK.get(),SoundSource.HOSTILE,MothershipSounds.LOCK_VOLUME,MothershipSounds.LOCK_PITCH);
        if((weapon.soundMask()&2)!=0) level.playSound(null,muzzle.x,muzzle.y,muzzle.z,
                MothershipSounds.CHARGE.get(),SoundSource.HOSTILE,MothershipSounds.DOOMSDAY_VOLUME,1F);
        if((weapon.soundMask()&4)!=0) level.playSound(null,muzzle.x,muzzle.y,muzzle.z,
                MothershipSounds.FIRE.get(),SoundSource.HOSTILE,MothershipSounds.DOOMSDAY_VOLUME,1F);
        if((weapon.soundMask()&8)!=0) level.playSound(null,muzzle.x,muzzle.y,muzzle.z,
                MothershipSounds.COOL.get(),SoundSource.HOSTILE,MothershipSounds.DOOMSDAY_VOLUME,1F);
        if(weapon.targetGlowing() && locked!=null && legalTarget(locked,MothershipTactics.RETALIATION_RANGE)) {
            if(glowingTarget==null || !glowingTarget.equals(locked.getUUID())) {
                releaseGlow();glowingTarget=locked.getUUID();MothershipGlow.acquire(locked,getUUID(),now);
            }
        } else releaseGlow();
        if(weapon.phase()==MothershipDoomsday.Phase.COOLDOWN || weapon.phase()==MothershipDoomsday.Phase.READY)doomTarget=null;
        if(tickCount%2==0 || weapon.hitMask()!=0 || weapon.elapsed()==MothershipDoomsday.LOCK_TICKS
                || weapon.elapsed()==MothershipDoomsday.FIRE_END_TICK)syncFx();
    }
    private boolean doomEligible(Entity target,boolean beginning) {
        if(!legalTarget(target,MothershipTactics.DOOMSDAY_RANGE))return false;
        Vec3 local=mc(model(target.getBoundingBox().getCenter())).subtract(mc(rig.doomsday));
        double yaw=Math.toDegrees(Math.atan2(-local.x,local.z));
        if(!(beginning?MothershipAim.mayBeginDoomsdayLock(yaw):MothershipAim.doomsdayArc(yaw)))return false;
        Vec3 muzzle=world(rig.doomsday);
        Vec3 direction=target.getBoundingBox().getCenter().subtract(muzzle).normalize();
        if(rig.obstructed(model(muzzle.add(direction.scale(.05))),model(target.getBoundingBox().getCenter()),poses))return false;
        return MothershipWorld.trace(this,muzzle,target.getBoundingBox().getCenter()).end().distanceToSqr(target.getBoundingBox().getCenter())<.01;
    }
    private void blast(ServerLevel level,Vec3 center,MothershipDoomsday.Hit hit) {
        double radius=hit.blastRadius();AABB area=new AABB(center,center).inflate(radius);
        if(!MothershipWorld.loaded(level,area))return;
        terrainBlasts[hit.ordinal()-1].begin(this,center,hit.igniteGround());
        for(Entity target:level.getEntities(this,area,e->e.isAlive()&&!e.isSpectator())) {
            if(target.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius)
                MothershipWeapons.doomsdayDamage(this,target,hit,true);
        }
    }

    private void igniteGround(ServerLevel level,Vec3 center,double radius) {
        if(!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,this))return;
        java.util.Set<BlockPos> unique=new java.util.HashSet<>();
        for(var offset:doomsday.fireSites()) {
            int x=Mth.floor(center.x+offset.x()),z=Mth.floor(center.z+offset.z());
            if(!level.hasChunk(x>>4,z>>4))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos site=new BlockPos(x,y,z);
            if(!unique.add(site)||site.getCenter().distanceToSqr(center)>radius*radius||!level.getWorldBorder().isWithinBounds(site)
                    || y<=level.getMinBuildHeight()||y>=level.getMaxBuildHeight()||!level.isEmptyBlock(site)
                    || !level.getBlockState(site.below()).isFaceSturdy(level,site.below(),net.minecraft.core.Direction.UP))continue;
            com.liucodex.doggiesnewvehicle.world.DnvControlledFire.place(level,site,this,
                    com.liucodex.doggiesnewvehicle.world.DnvControlledFire.FIRE.get());
        }
    }

    private void tickDrops(ServerLevel level,long now) {
        if(drops.remaining()==0 || now<nextDrop || !(getTeam() instanceof PlayerTeam))return;
        for(int bay=0;bay<2;bay++) {
            if(!drops.ready(bay,arrivalTicks))continue;
            final int slot=bay;Vec3 anchor=world(rig.drops.get(bay));
            Entity target=dropSight.select(this,anchor,candidates.candidates(this),null,
                    e->legalTarget(e,MothershipTactics.DROP_RANGE));
            if(target==null)continue;
            nextDrop=now+40;
            Vec3 point=target.position();
            boolean open=MothershipWorld.overhead(this,target.getBoundingBox().getCenter(),point.y+56);
            if(!open)point=anchor;
            NoiseVehicleEntity noise=prepareDrop(level,point,slot,open);
            if(noise==null)continue;
            boolean deployed=drops.deploy(slot,()->{
                if(!legalTarget(target,MothershipTactics.DROP_RANGE)
                        || MothershipWorld.trace(this,anchor,target.getBoundingBox().getCenter()).end()
                            .distanceToSqr(target.getBoundingBox().getCenter())>=.01)return false;
                // The deterministic child ID also rejects an already-loaded child after interrupted insertion.
                if(level.getEntity(noise.getUUID())!=null)return true;
                if(!level.addFreshEntity(noise))return false;
                if(getTeam() instanceof PlayerTeam team)DnvBossTeamMembership.inheritIfUnassigned(noise,team);
                return true;
            });
            if(deployed)break;
        }
    }
    private NoiseVehicleEntity prepareDrop(ServerLevel level,Vec3 center,int bay,boolean aroundTarget) {
        int[][] offsets=aroundTarget?DROP_OFFSETS:BELOW_OFFSETS;
        for(int index=0;index<offsets.length;index++) {
            int[] offset=offsets[(index+bay*4)%offsets.length];
            V shifted=aroundTarget?new V(offset[0],0,offset[1]):MothershipRig.yaw(new V(offset[0],0,offset[1]),getYRot());
            int x=Mth.floor(center.x+shifted.x()),z=Mth.floor(center.z+shifted.z());
            if(!level.hasChunk(x>>4,z>>4))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos site=new BlockPos(x,y,z);
            if(y<=level.getMinBuildHeight()||y+8>=level.getMaxBuildHeight()||!level.getBlockState(site.below()).isSolid())continue;
            float dropStart=com.liucodex.noise.entity.NoiseDropTimeline.mothershipStartOffset(getY()-y);
            if(dropStart<0)continue;
            NoiseVehicleEntity noise=com.liucodex.noise.init.ModEntities.NOISE.get().create(level);
            if(noise==null)return null;
            noise.setPos(x+.5,y,z+.5);noise.setYRot(getYRot());noise.alignCoreToHull();noise.setHealth(NoiseVehicleEntity.MAX_HEALTH);
            noise.updateOBB();noise.refreshBoundingBoxFromOBBs();
            // Keep the clearance margin horizontal: inflating below the feet would reject every solid floor.
            AABB bounds=noise.getBoundingBox().inflate(.25,0,.25);
            if(!MothershipWorld.withinWorld(level,bounds)||!level.noCollision(noise,bounds))continue;
            double dropTop=y+com.liucodex.noise.entity.NoiseDropTimeline.rigVerticalOffset(0,dropStart)
                    +com.liucodex.noise.entity.NoiseDropTimeline.RIG_TOP+.25;
            double[] carrier=MothershipNavigation.halfExtents(
                    2*com.liucodex.noise.entity.NoiseDropTimeline.RIG_HALF_WIDTH,
                    2*com.liucodex.noise.entity.NoiseDropTimeline.RIG_HALF_LENGTH,getYRot());
            // The visual carrier is wider than the ground vehicle's collision boxes.
            AABB column=bounds.minmax(new AABB(x+.5-carrier[0],bounds.minY,z+.5-carrier[1],
                    x+.5+carrier[0],bounds.maxY,z+.5+carrier[1]));
            if(!MothershipWorld.clearDropColumn(this,column,dropTop))continue;
            noise.setMothershipDropStart(dropStart);
            noise.setUUID(UUID.nameUUIDFromBytes((getUUID()+":mothership_noise:"+bay).getBytes(StandardCharsets.UTF_8)));
            noise.getPersistentData().putUUID(PARENT,getUUID());
            return noise;
        }
        return null;
    }

    @Override public boolean hurt(DamageSource source,float amount) {
        float rawAmount=amount;
        if(level().isClientSide || !entryActive() || isRemoved() || getHealth()<=0 || !Float.isFinite(amount) || amount<=0 || source.getEntity()==this)return false;
        if(com.liucodex.doggiesnewvehicle.core.DnvStarProjectileHits.starAlreadyHit(source,getUUID()))return false;
        long now=level().getGameTime();
        boolean laser=source.is(ModDamageTypes.LASER) || source.is(DnvDamageTypes.BOSS_PIERCING_LASER);
        amount=MothershipDefense.mitigate(source,amount,laser);
        Entity direct=source.getDirectEntity();
        boolean blast=source.is(DamageTypeTags.IS_EXPLOSION) || source.is(ModDamageTypes.CUSTOM_EXPLOSION);
        // SBW sets the vehicle as the direct source for both its laser and its
        // accompanying explosion, with the pilot (if any) as the attacker.
        boolean pulseDamage=direct instanceof AnnihilatorEntity && (laser || blast);
        UUID pulseSource=pulseDamage?direct.getUUID():null;
        if(pulseDamage) {
            if(annihilatorBudgetTick!=now) {annihilatorBudgetTick=now;annihilatorDamageBySource.clear();}
            amount=MothershipAnnihilatorDefense.withinTick(annihilatorDamageBySource.getOrDefault(pulseSource,0F),amount);
        }
        if(amount<=0)return false;
        amount=com.liucodex.doggiesnewvehicle.core.DnvPercentHits.apply(this,source,rawAmount,amount);
        float before=getHealth();
        boolean accepted=super.m_6469_(source,amount);
        if(getHealth()<before)com.liucodex.doggiesnewvehicle.core.DnvStarProjectileHits.recordStarHit(source,getUUID());
        if(pulseDamage)annihilatorDamageBySource.merge(pulseSource,Math.max(0,before-getHealth()),Float::sum);
        // Native SBW onHurt can reduce vehicle health even when Entity.hurt returns false.
        if(getHealth()<=0) {beginCrash();return true;}
        Entity attacker=source.getEntity();
        if(accepted && !returning && attacker!=null && legalTarget(attacker,MothershipTactics.RETALIATION_RANGE)) {
            if(tactics.target()!=null && tactics.remaining(level().getGameTime())==0)return true;
            boolean starting=tactics.target()==null;
            if(tactics.onAttacked(attacker.getUUID(),level().getGameTime(),horizontal(attacker.position()),true)&&starting) {
                pursuitOrigin=position();nextNavigation=0;
            }
        }
        return accepted;
    }
    @Override public DamageModifier getDamageModifier() { return IDENTITY_DAMAGE_MODIFIER; }
    private void updateBossBar(ServerLevel level) {
        bossEvent.setProgress(Mth.clamp(getHealth()/MAX_HEALTH,0,1));bossEvent.setName(getDisplayName());
        bossEvent.setVisible(true);
        if(tickCount>1&&tickCount%20!=0)return;
        var online=level.getServer().getPlayerList().getPlayers();
        for(ServerPlayer player:List.copyOf(bossEvent.getPlayers()))
            if(!online.contains(player))bossEvent.removePlayer(player);
        for(ServerPlayer player:online) {
            if(player.level()==level&&!player.isSpectator()&&distanceToSqr(player)<=192*192)bossEvent.addPlayer(player);else bossEvent.removePlayer(player);
        }
    }
    private void abortDoom(long now,boolean lostTarget) {
        if(!doomsday.abort(now,lostTarget))return;
        MothershipSounds.stopDoomsday(this);
        doomTarget=null;fixedBeamEnd=null;shotStarted=-1;releaseGlow();
        if(level() instanceof ServerLevel serverLevel) {
            Vec3 muzzle=world(rig.doomsday);
            serverLevel.playSound(null,muzzle.x,muzzle.y,muzzle.z,MothershipSounds.ABORT.get(),
                    SoundSource.HOSTILE,MothershipSounds.ABORT_VOLUME,1F);
        }
        syncFx();
    }
    private void cancelDoom() {
        if(shotStarted>=0 || doomsday.abortStarted()>=0)MothershipSounds.stopDoomsday(this);
        doomsday.cancel();doomTarget=null;fixedBeamEnd=null;shotStarted=-1;releaseGlow();
    }
    private void releaseGlow() {
        if(glowingTarget!=null && level() instanceof ServerLevel level)MothershipGlow.release(level,glowingTarget,getUUID());
        glowingTarget=null;
    }
    private void clearCombat() {
        cancelDoom();candidates.clear();pursuitSight.clear();dropSight.clear();
        for(var sight:stationSights)sight.clear();
        annihilatorDamageBySource.clear();annihilatorBudgetTick=Long.MIN_VALUE;
        stationTracking.clear();
        course=Vec3.ZERO;idleDestination=null;nextIdle=0;idleDeadline=0;
        returnProgressAt=-1;returnDistance=Double.POSITIVE_INFINITY;
        idleClearance=MothershipTactics.GROUND_CLEARANCE;nextNavigation=0;
        setDeltaMovement(Vec3.ZERO);bossEvent.setVisible(false);bossEvent.removeAllPlayers();
    }
    @Override public void onAddedToWorld() {
        super.onAddedToWorld();
        if(level() instanceof ServerLevel serverLevel)MothershipPresence.get(serverLevel).active(getUUID(),blockPosition());
    }
    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }
    @Override public void releaseExternalState(RemovalReason reason) {
        clearCombat();
        DnvBossTeamMembership.releaseOnTerminalRemoval(this, reason);
        if (reason == null || !(level() instanceof ServerLevel serverLevel)) return;
        var presence = MothershipPresence.get(serverLevel);
        if (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION) presence.release(getUUID());
        else presence.active(getUUID(), blockPosition());
    }
    @Override public void onRemovedFromWorld() {
        releaseExternalState(getRemovalReason());
        super.onRemovedFromWorld();
    }

    private void syncFx() {
        CompoundTag tag=new CompoundTag();
        tag.putInt("Arrival",arrivalTicks);tag.putInt("Spent",drops.spentMask());
        boolean visible=shotStarted>=0 && weapon.elapsed()<=MothershipDoomsday.LAST_HIT_TICK+com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx.COOL_TICKS;
        tag.putLong("Start",visible?shotStarted:-1);
        tag.putLong("Abort",doomsday.abortStarted());
        tag.putLong("AbortElapsed",doomsday.abortElapsed());
        if(visible || doomsday.abortStarted()>=0) {
            tag.putDouble("X",beamEnd.x);tag.putDouble("Y",beamEnd.y);tag.putDouble("Z",beamEnd.z);tag.putBoolean("Impact",beamImpact);
            tag.putDouble("GroundX",doomGround.x);tag.putDouble("GroundY",doomGround.y);tag.putDouble("GroundZ",doomGround.z);
        }
        entityData.set(FX,tag);
    }
    public Angles renderPose(int station,float partialTick) {
        var a=previousDisplay[station];var b=display[station];
        return new Angles(a.yaw()+MothershipRig.wrap(b.yaw()-a.yaw())*partialTick,Mth.lerp(partialTick,a.elevation(),b.elevation()));
    }
    public long renderShotStarted() {var tag=entityData.get(FX);return tag.contains("Start")?tag.getLong("Start"):-1;}
    public long renderAbortStarted() {var tag=entityData.get(FX);return tag.contains("Abort")?tag.getLong("Abort"):-1;}
    public long renderAbortElapsed() {return entityData.get(FX).getLong("AbortElapsed");}
    public Vec3 renderBeamEnd() {var t=entityData.get(FX);return new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
    public Vec3 renderGroundPoint() {var t=entityData.get(FX);return new Vec3(t.getDouble("GroundX"),t.getDouble("GroundY"),t.getDouble("GroundZ"));}
    public Vec3 renderBeamOffset() {return renderShotStarted()<0?Vec3.ZERO:renderBeamEnd().subtract(position());}
    public boolean renderImpact() {return entityData.get(FX).getBoolean("Impact");}
    public float renderThrottle() {return entityData.get(THROTTLE);}
    public boolean entryActive() {return MothershipArrival.phase(level().isClientSide?entityData.get(ENTRY_AGE):entryAge)==MothershipArrival.Phase.ACTIVE;}
    public int renderEntryAge() {return entityData.get(ENTRY_AGE);}
    public void setBeaconAnchor(Vec3 point) {
        if(point==null||!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z))throw new IllegalArgumentException("beacon anchor");
        beaconAnchor=point;
        CompoundTag tag=new CompoundTag();putVector(tag,"Point",point);entityData.set(ENTRY_ANCHOR,tag);
    }
    public Vec3 renderBeaconAnchor() {
        Vec3 point=readVector(entityData.get(ENTRY_ANCHOR),"Point");
        return point==null?position().add(0,-MothershipTactics.GROUND_CLEARANCE,0):point;
    }
    public double renderDropCharge(int bay) {
        if(isWreck()||getHealth()<=0)return 0;
        var tag=entityData.get(FX);
        if((tag.getInt("Spent")&(1<<bay))!=0)return 0;
        return Math.min(1,tag.getInt("Arrival")/(double)(bay==0?MothershipDrops.FIRST_CHARGE_TICKS:MothershipDrops.SECOND_CHARGE_TICKS));
    }
    private Vec3 world(V point) {return mc(rig.modelToWorld(point,v(position()),getYRot()));}
    private V model(Vec3 point) {return rig.worldToModel(v(point),v(position()),getYRot());}
    public static V v(Vec3 p) {return new V(p.x,p.y,p.z);}
    public static Vec3 mc(V p) {return new Vec3(p.x(),p.y(),p.z());}

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.m_7380_(tag);
        CompoundTag data=new CompoundTag();long now=level().getGameTime();data.putInt("Schema",5);
        data.putInt("CrashState",crashSequence.state());data.putInt("CrashAge",crashSequence.age());
        data.putInt("CrashLandedAge",crashSequence.landedAge());
        for (int i=0;i<3;i++) data.put("TerrainBlast"+i,terrainBlasts[i].save());
        data.put("TerrainBlast",terrainBlasts[3].save());
        if(crashSequence.planned()) {
            data.putDouble("CrashStartY",crashSequence.startY());data.putDouble("CrashEndY",crashSequence.endY());data.putInt("CrashPlanAge",crashSequence.planAge());
        }
        data.putBoolean("ArrivalStarted",arrivalStarted);data.putInt("ArrivalTicks",arrivalTicks);
        data.putInt("EntryAge",entryAge);
        if(beaconAnchor!=null)putVector(data,"BeaconAnchor",beaconAnchor);
        data.putInt("Drops",drops.spentMask());data.putLongArray("Stations",armament.remaining(now));data.putLong("DoomCooldown",doomsday.cooldownToSave(now));
        data.putIntArray("RocketTubes",rocketTube);
        if(home!=null){putVector(data,"Home",home);data.putDouble("Ground",encounterGround);}
        if(pursuitOrigin!=null)putVector(data,"PursuitOrigin",pursuitOrigin);
        if(tactics.target()!=null){data.putUUID("Pursuit",tactics.target());data.putLong("PursuitRemaining",tactics.remaining(now));}
        data.putBoolean("Returning",returning);
        for(int i=0;i<6;i++){data.putDouble("Yaw"+i,poses[i].yaw());data.putDouble("Elevation"+i,poses[i].elevation());}
        tag.put(SAVE,data);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag);clearCombat();setNoGravity(true);
        if(!Float.isFinite(getHealth()))setHealth(MAX_HEALTH);else setHealth(Math.min(MAX_HEALTH,getHealth()));
        // Vanilla /summon also calls this with empty/custom NBT on a newly created entity.
        // Only an explicit legacy DNV schema below may skip the introduction.
        if(!tag.contains(SAVE,Tag.TAG_COMPOUND)) {entryAge=0;entityData.set(ENTRY_AGE,entryAge);return;}
        CompoundTag data=tag.getCompound(SAVE);long now=level().getGameTime();int schema=data.getInt("Schema");boolean known=schema>=1&&schema<=5;
        for(int i=0;i<3;i++) terrainBlasts[i].restore(known ? data.getCompound("TerrainBlast"+i) : new CompoundTag());
        terrainBlasts[3].restore(known ? data.getCompound("TerrainBlast") : new CompoundTag());
        if(schema>=3&&schema<=5) {
            int state=data.getInt("CrashState");
            // Schema 3 landed wrecks already consumed their immediate impact.
            if(schema==3&&state==MothershipCrash.LANDED)state=MothershipCrash.FINISHED;
            crashSequence.restore(state,data.getInt("CrashAge"),data.getInt("CrashLandedAge"));
            if(crashSequence.state()!=MothershipCrash.ALIVE){setHealth(0);setWreck(true);}
        }
        if(schema>=3&&schema<=5&&data.contains("CrashStartY"))crashSequence.restorePlan(data.getDouble("CrashStartY"),data.getDouble("CrashEndY"),data.getInt("CrashPlanAge"));
        entryAge=schema==5?MothershipArrival.restore(data.getInt("EntryAge")):MothershipArrival.TOTAL_TICKS;
        entityData.set(ENTRY_AGE,entryAge);
        Vec3 restoredAnchor=schema==5?readVector(data,"BeaconAnchor"):null;
        if(restoredAnchor!=null)setBeaconAnchor(restoredAnchor);
        arrivalStarted=schema>=2&&data.getBoolean("ArrivalStarted");
        arrivalTicks=schema>=2?Mth.clamp(data.getInt("ArrivalTicks"),0,MothershipDrops.SECOND_CHARGE_TICKS):0;
        drops.restore(known&&data.contains("Drops",Tag.TAG_INT)?data.getInt("Drops"):-1);
        armament.restore(now,known?data.getLongArray("Stations"):null);
        doomsday.restoreCooldown(now,known&&data.contains("DoomCooldown",Tag.TAG_LONG)?data.getLong("DoomCooldown"):-1);
        home=readVector(data,"Home");encounterGround=data.getDouble("Ground");
        if(!Double.isFinite(encounterGround))home=null;
        pursuitOrigin=readVector(data,"PursuitOrigin");returning=data.getBoolean("Returning");
        if(known&&pursuitOrigin!=null&&data.hasUUID("Pursuit"))tactics.restore(data.getUUID("Pursuit"),now,data.getLong("PursuitRemaining"));
        int[] tubes=data.getIntArray("RocketTubes");
        for(int i=0;i<6;i++) {
            rocketTube[i]=i<tubes.length?tubes[i]&1:0;
            double yaw=data.getDouble("Yaw"+i),elevation=data.getDouble("Elevation"+i);
            poses[i]=Double.isFinite(yaw)&&Double.isFinite(elevation)&&data.contains("Yaw"+i)?rig.savedPose(i,new Angles(yaw,elevation)):rig.rest(i);
            previousDisplay[i]=display[i]=poses[i];syncMount(i);
        }
        syncFx();
    }
    private static void putVector(CompoundTag tag,String key,Vec3 p) {
        CompoundTag v=new CompoundTag();v.putDouble("X",p.x);v.putDouble("Y",p.y);v.putDouble("Z",p.z);tag.put(key,v);
    }
    private static Vec3 readVector(CompoundTag tag,String key) {
        if(!tag.contains(key,Tag.TAG_COMPOUND))return null;var p=tag.getCompound(key);
        double x=p.getDouble("X"),y=p.getDouble("Y"),z=p.getDouble("Z");
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30_000_000&&Math.abs(z)<=30_000_000&&Math.abs(y)<4096?new Vec3(x,y,z):null;
    }
}

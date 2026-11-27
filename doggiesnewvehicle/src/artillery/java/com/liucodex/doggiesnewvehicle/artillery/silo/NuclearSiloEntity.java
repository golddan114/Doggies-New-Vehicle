package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.item.IVehicleInteract;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryItems;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Fixed native-inventory facility. Only the server commits resources, launch facts and mechanical state. */
public final class NuclearSiloEntity extends AutoAimableEntity implements DnvPlayerArtilleryTarget,
        com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider,com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    @Override public float ordinaryArmorReduction(){return com.liucodex.doggiesnewvehicle.core.DnvArmorRules.Profile.SPECIAL.reduction();}
    private java.util.List<net.minecraft.world.phys.AABB> collisionBoxes;
    private java.util.List<com.atsuishio.superbwarfare.tools.OBB> collisionObbs;
    private double collisionFrame=Double.NaN;
    private float collisionYaw;
    private Vec3 collisionOrigin;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(NuclearSiloEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);
    private BlockPos anchor;
    private BlockPos indicatorTarget;
    private UUID lastDebit;
    private UUID rackId;
    private int rackLoadTicks;
    private CompoundTag invalidData;
    private long haltedAt=-1;
    private boolean claiming;
    private NuclearClearance clearance;
    private boolean clearanceFinal;
    private final NuclearTickGate clock=new NuclearTickGate();
    private final NuclearReadyCue readyCue=new NuclearReadyCue();
    public NuclearSiloEntity(EntityType<? extends NuclearSiloEntity> type,Level level){super(type,level);setNoGravity(true);}
    public boolean dogEgg(){return DnvArtilleryEntities.DOGEGG_SILO.isPresent()&&getType()==DnvArtilleryEntities.DOGEGG_SILO.get();}
    public SiloPayloadKind payloadKind(){return dogEgg()?SiloPayloadKind.DOGEGG:SiloPayloadKind.NUCLEAR;}
    private net.minecraft.world.item.Item ammunition(){return dogEgg()?DnvArtilleryItems.DOGEGG_MISSILE.get():DnvArtilleryItems.NUCLEAR_MISSILE.get();}
    @Override protected void defineSynchedData(){super.m_8097_();entityData.define(VIEW,new CompoundTag());}
    @Override public void autoAim(){ /* Coordinates are player orders, not autonomous target acquisition. */ }
    @Override public java.util.List<net.minecraft.world.phys.AABB> dnv$collisionBoxes(){
        var snapshot=cycle==null?new SiloCycle.Snapshot(SiloCycle.Phase.EMPTY,0,null,null):cycle.snapshot();
        double remaining=snapshot.remaining();var phase=snapshot.phase();
        if(level().isClientSide){var view=visualSnapshot();
            try{phase=SiloCycle.Phase.valueOf(view.getString("Phase"));}
            catch(IllegalArgumentException absent){phase=SiloCycle.Phase.EMPTY;}
            remaining=NuclearMechanicalTimeline.remaining(view.getInt("Remaining"),view.getLong("Observed"),level().getGameTime(),mechanicsRunning());}
        double frame=NuclearMechanicalTimeline.frame(phase,remaining,NuclearRules.DEFAULT);
        if(collisionBoxes==null||collisionFrame!=frame||collisionYaw!=getYRot()||!position().equals(collisionOrigin)){
            collisionBoxes=NuclearCollision.silo(frame,getYRot(),position());collisionFrame=frame;collisionYaw=getYRot();collisionOrigin=position();
            collisionObbs=collisionBoxes.stream().map(b->new com.atsuishio.superbwarfare.tools.OBB(
                    new org.joml.Vector3d(b.getCenter().x,b.getCenter().y,b.getCenter().z),
                    new org.joml.Vector3d(b.getXsize()/2,b.getYsize()/2,b.getZsize()/2),new org.joml.Quaterniond(),com.atsuishio.superbwarfare.tools.OBB.Part.EMPTY)).toList();
        }
        return collisionBoxes;
    }
    @Override public java.util.List<com.atsuishio.superbwarfare.tools.OBB> getOBBs(){dnv$collisionBoxes();return collisionObbs;}
    @Override public void updateOBB(){dnv$collisionBoxes();}
    @Override public void setPos(double x,double y,double z){
        // Native SAT support writes setPos directly, bypassing move/push. Deployment owns the pose.
        if(anchor!=null){x=anchor.getX()+.5;y=anchor.getY();z=anchor.getZ()+.5;}
        super.setPos(x,y,z);
    }
    @Override public void setDeltaMovement(Vec3 motion){super.m_20256_(Vec3.ZERO);}
    @Override public void move(net.minecraft.world.entity.MoverType type,Vec3 motion){setDeltaMovement(Vec3.ZERO);}
    @Override public Entity changeDimension(ServerLevel destination){return null;}
    public boolean initializeOwner(UUID owner){
        if(!(level() instanceof ServerLevel)||owner==null||getOptionalOwnerUUID().isPresent())return false;
        setOwnerUUID(owner);anchor=blockPosition().immutable();return true;
    }
    @Override public void onAddedToWorld(){
        if(anchor!=null)setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);
        super.onAddedToWorld();
        if(ensureClaim()&&level() instanceof ServerLevel server){
            NuclearSiloStore.get(server).finishUnfold(getUUID());
            if(!mechanicsRunning()&&haltedAt<0)haltedAt=server.getGameTime();
            publish();
        }
    }
    public boolean prepareContainerOwner(UUID owner,BlockPos centre){
        if(!(level() instanceof ServerLevel)||owner==null||centre==null||invalidData!=null
                ||getOptionalOwnerUUID().filter(id->!id.equals(owner)).isPresent())return false;
        setOwnerUUID(owner);anchor=centre.immutable();cycle.reset();indicatorTarget=null;lastDebit=null;
        setEnergy(0);return true;
    }
    private boolean ensureClaim(){
        if(!(level() instanceof ServerLevel server))return true;
        if(claiming)return false;claiming=true;
        try{
            if(invalidData!=null)return false;
            NuclearSiloStore store=NuclearSiloStore.get(server);if(!store.writable())return false;
            if(anchor==null||getOptionalOwnerUUID().isEmpty()||!store.claim(getUUID(),anchor)){discard();return false;}
            SiloChunkTickets.update(server,getUUID(),Set.of(new net.minecraft.world.level.ChunkPos(anchor).toLong()));return true;
        }finally{claiming=false;}
    }
    @Override public boolean canBindIndicator(Player player){
        return player!=null&&player.level()==level()&&!isRemoved()&&mechanicsRunning()&&!getLocked()
                &&getOptionalOwnerUUID().filter(player.getUUID()::equals).isPresent();
    }
    private boolean validTarget(BlockPos target){
        return target!=null&&!level().isOutsideBuildHeight(target)&&level().getWorldBorder().isWithinBounds(target)
                &&NuclearFlight.withinRange(point(position()),point(target.getCenter()),NuclearRules.DEFAULT);
    }
    @Override public boolean setIndicatorTarget(ServerPlayer player,BlockPos target,int radius,boolean depressed){
        if(!canBindIndicator(player)||!player.isAlive()||player.isSpectator()||radius<0||radius>256||!cycle.canPack()||!validTarget(target))return false;
        // A nuclear coordinate is exact; the indicator's conventional artillery spread is not inherited.
        indicatorTarget=target.immutable();publish();
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.target_set",target.getX(),target.getY(),target.getZ())
                .append("\n").append(statusMessage()),false);return true;
    }
    @Override public boolean requestIndicatorFire(ServerPlayer player,BlockPos target,int radius,boolean depressed){
        if(!canBindIndicator(player)||!player.isAlive()||player.isSpectator()||radius<0||radius>256
                ||target==null||!target.equals(indicatorTarget)||!validTarget(target))return false;
        var rack=rack();
        boolean accepted=rack!=null&&cycle.request(rack.getUUID(),new SiloCycle.Target(target.getX(),target.getY(),target.getZ()),ammoSlot()>=0,getEnergy());
        if(accepted){publish();announceLaunch(player.serverLevel(),payloadKind());
            player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.fire_accepted",target.getX(),target.getY(),target.getZ()),false);}return accepted;
    }
    @Override public void clearIndicatorTarget(ServerPlayer player){if(canBindIndicator(player)){
        indicatorTarget=null;publish();player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.target_cleared"),false);}}
    @Override public Component indicatorAccessProblem(Player player) {
        if (isRemoved() || player.level() != level() || !player.isAlive() || player.isSpectator())
            return indicatorMessage("unavailable");
        if (invalidData != null) return indicatorMessage("data");
        if (!mechanicsRunning()) return indicatorMessage("damaged");
        if (getOptionalOwnerUUID().filter(player.getUUID()::equals).isEmpty()) return indicatorMessage("owner");
        return indicatorMessage(getLocked() ? "locked" : "unavailable");
    }

    @Override public Component indicatorProblem(ServerPlayer player, BlockPos target, int radius, boolean firing) {
        if (!canBindIndicator(player) || !player.isAlive() || player.isSpectator()) return indicatorAccessProblem(player);
        if (!cycle.canPack()) return indicatorMessage("busy");
        if (radius < 0 || radius > 256) return indicatorMessage("radius");
        if (target == null) return indicatorMessage("no_target");
        if (level().isOutsideBuildHeight(target) || !level().getWorldBorder().isWithinBounds(target))
            return indicatorMessage("world_bounds", target.getX(), target.getY(), target.getZ());
        if (!validTarget(target)) return indicatorMessage("range", (int) NuclearRules.DEFAULT.coordinateRange());
        if (!firing) return indicatorMessage("target");
        if (!target.equals(indicatorTarget)) return indicatorMessage("unconfirmed", target.getX(), target.getY(), target.getZ());
        if (ammoSlot() < 0) return indicatorMessage("ammo");
        var state = cycle.snapshot();
        if (state.phase() != SiloCycle.Phase.READY) {
            int ticks = state.phase() == SiloCycle.Phase.EMPTY ? NuclearRules.DEFAULT.reloadTicks() : state.remaining();
            return indicatorMessage("loading", (ticks + 19) / 20);
        }
        if (getEnergy() < NuclearRules.DEFAULT.shotFE())
            return indicatorMessage("energy_status", getEnergy(), NuclearRules.DEFAULT.shotFE());
        return indicatorMessage("rack");
    }

    private static Component indicatorMessage(String reason, Object... arguments) {
        return Component.translatable("message.doggiesnewvehicle.nuclear.indicator_" + reason, arguments);
    }

    @Override public String indicatorRejectionKey(boolean firing){
        String reason="target";
        var phase=cycle.snapshot().phase();
        if(phase==SiloCycle.Phase.OPENING||phase==SiloCycle.Phase.RETURNING
                ||phase==SiloCycle.Phase.DEPARTING||phase==SiloCycle.Phase.CLOSING)reason="busy";
        else if(firing){
            if(ammoSlot()<0)reason="ammo";
            else if(phase!=SiloCycle.Phase.READY)reason="reload";
            else if(getEnergy()<NuclearRules.DEFAULT.shotFE())reason="energy";
        }
        return "message.doggiesnewvehicle.nuclear.indicator_"+reason;
    }
    private int ammoSlot(){for(int i=0;i<getContainerSize();i++)if(getItem(i).is(ammunition())&&getItem(i).getCount()>0)return i;return -1;}
    /** Explicit creative test action; the subsequent launch keeps its native resource and clearance gates. */
    public boolean completeCreativePreparation(ServerPlayer player){
        if(!creativePreparationAllowed(player)||!(level() instanceof ServerLevel server))return false;
        if(cycle.snapshot().phase()==SiloCycle.Phase.READY){
            player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_already_ready"),false);return true;
        }
        if(!cycle.completePreparation(true))return false;
        updateRack(server);publish();
        if(dogEgg())nuclearSound(com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.DOGEGG_READY.get());
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_ready"),false);return true;
    }
    /** Creative item entry: keep the original rise/open animation and never rewind an active preparation. */
    public boolean startCreativePreparation(ServerPlayer player){
        if(!creativePreparationAllowed(player)||!(level() instanceof ServerLevel server))return false;
        var state=cycle.snapshot();
        if(state.phase()==SiloCycle.Phase.READY){
            player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_already_ready"),false);return true;
        }
        if(state.phase()==SiloCycle.Phase.RELOADING&&state.remaining()<=NuclearMechanicalTimeline.ANIMATION_START_TICKS){
            player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_preparing"),false);return true;
        }
        if(!cycle.startPreparation(true))return false;
        updateRack(server);publish();
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_started"),false);return true;
    }
    private boolean creativePreparationAllowed(ServerPlayer player){
        return player!=null&&player.isCreative()&&!player.isSpectator()&&player.isAlive()
                &&canBindIndicator(player)&&ammoSlot()>=0;
    }
    public boolean mechanicsRunning(){return invalidData==null&&getHealth()>0&&!isWreck();}
    private void halt(long time){releaseClearance();if(haltedAt<0){haltedAt=time;publish();}}
    @Override public void destroy(){
        if(level() instanceof ServerLevel server&&!isRemoved()&&invalidData==null&&!mechanicsRunning()){
            halt(server.getGameTime());
            var owner=getOptionalOwnerUUID();var store=NuclearSiloStore.get(server);
            if(ammoSlot()>=0&&anchor!=null&&owner.isPresent()&&getUUID().equals(store.facility())
                    &&(store.detonateFacility(getUUID(),owner.get(),anchor,payloadKind())||store.committed(getUUID()))){
                // The rack is a view of the same physical inventory. Consume stock once before native wreck loot.
                for(int slot=0;slot<getContainerSize();slot++)if(getItem(slot).is(ammunition()))
                    removeItem(slot,getItem(slot).getCount());
            }
            var missile=rack();if(missile!=null)missile.discard();rackId=null;publish();
        }
        super.destroy();
    }
    @Override public void tick(){
        if(invalidData!=null)return;
        if(level() instanceof ServerLevel serverLevel&&!clock.claim(serverLevel.getGameTime()))return;
        super.tick();if(!(level() instanceof ServerLevel server)||isRemoved())return;
        if(!ensureClaim())return;
        setDeltaMovement(Vec3.ZERO);setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);
        if(!mechanicsRunning()){halt(server.getGameTime());return;}
        if(haltedAt>=0){haltedAt=-1;publish();}
        var before=cycle.snapshot();if(cycle.tick(ammoSlot()>=0))launch(server);
        updateRack(server);
        if(dogEgg()&&before.phase()==SiloCycle.Phase.RELOADING
                &&cycle.snapshot().phase()==SiloCycle.Phase.RELOADING
                &&before.remaining()>NuclearMechanicalTimeline.READY_LEAD_TICKS
                &&cycle.snapshot().remaining()<=NuclearMechanicalTimeline.READY_LEAD_TICKS)
            nuclearSound(com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.DOGEGG_EXTEND.get());
        if(readyCue.observe(before,cycle.snapshot())){
            if(!dogEgg())nuclearSound(com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.NUCLEAR_READY.get());
        }
        if(dogEgg()&&before.phase()!=SiloCycle.Phase.READY&&cycle.snapshot().phase()==SiloCycle.Phase.READY)
            nuclearSound(com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.DOGEGG_READY.get());
        if(!dogEgg()&&readyCue.announce(before,cycle.snapshot(),ammoSlot()>=0&&getEnergy()>=NuclearRules.DEFAULT.shotFE()))announceReady(server);
        if(before.phase()!=cycle.snapshot().phase()||server.getGameTime()%10==0)publish();
    }
    public boolean ownsRack(UUID id){return mechanicsRunning()&&id!=null&&id.equals(rackId)
            &&(cycle.canPack()||cycle.snapshot().phase()==SiloCycle.Phase.OPENING
                ||cycle.snapshot().phase()==SiloCycle.Phase.RETURNING&&cycle.snapshot().preparationRetained());}
    public boolean recoverCommittedRack(NuclearMissileEntity missile){
        var state=cycle.snapshot();
        if(!(level() instanceof ServerLevel server)||!missile.belongsToRack(getUUID())
                ||!missile.getUUID().equals(state.launch())||state.target()==null
                ||NuclearSiloStore.get(server).flight(missile.getUUID())==null
                ||!NuclearSiloStore.get(server).committed(missile.getUUID())||missile.payloadKind()!=payloadKind())return false;
        var target=state.target();missile.initialize(missile.getUUID(),getOptionalOwnerUUID().orElseThrow(),getUUID(),
                new BlockPos(target.x(),target.y(),target.z()),missile.position());missile.arm();return true;
    }
    private NuclearMissileEntity rack(){
        if(!(level() instanceof ServerLevel server)||rackId==null)return null;
        return server.getEntity(rackId) instanceof NuclearMissileEntity missile&&!missile.isRemoved()
                &&missile.belongsToRack(getUUID())&&missile.payloadKind()==payloadKind()?missile:null;
    }
    private void updateRack(ServerLevel server){
        var state=cycle.snapshot();
        boolean needed=ammoSlot()>=0&&(state.phase()==SiloCycle.Phase.RELOADING||state.phase()==SiloCycle.Phase.READY
                ||state.phase()==SiloCycle.Phase.OPENING||state.phase()==SiloCycle.Phase.RETURNING&&state.preparationRetained());
        var missile=rack();
        if(!needed){if(missile!=null)missile.discard();rackId=null;return;}
        float frame=Math.min(NuclearMechanicalTimeline.LAUNCH_FRAME,NuclearMechanicalTimeline.frame(state.phase(),state.remaining(),NuclearRules.DEFAULT));
        var offset=NuclearRackMotion.tail(frame,getYRot());Vec3 origin=position().add(offset.x,offset.y,offset.z);
        if(missile==null){
            if(rackId!=null&&++rackLoadTicks<=20)return;
            if(rackId==null)rackId=state.launch()!=null?state.launch():UUID.randomUUID();
            if(NuclearSiloStore.get(server).committed(rackId))return;
            missile=(dogEgg()?DnvArtilleryEntities.DOGEGG_MISSILE.get():DnvArtilleryEntities.NUCLEAR_MISSILE.get()).create(server);if(missile==null)return;
            missile.initializeRack(rackId,getOptionalOwnerUUID().orElseThrow(),getUUID(),origin,getYRot());
            if(!server.addFreshEntity(missile)){missile.discard();return;}
            publish();
        }
        missile.setPos(origin);missile.setYRot(getYRot());missile.setXRot(-90);rackLoadTicks=0;
    }
    private void launch(ServerLevel server){
        var state=cycle.snapshot();UUID operation=state.launch();NuclearSiloStore store=NuclearSiloStore.get(server);
        if(store.committed(operation)){
            // A newer SavedData receipt with an older facility snapshot never respawns a missile.
            if(!operation.equals(lastDebit)){
                int slot=ammoSlot();if(slot<0||getEnergy()<NuclearRules.DEFAULT.shotFE()){invalidData=SiloCycleCodec.write(state);return;}
                removeItem(slot,1);consumeEnergy(NuclearRules.DEFAULT.shotFE());lastDebit=operation;
            }
            cycle.launched(true);return;
        }
        var t=state.target();BlockPos target=new BlockPos(t.x(),t.y(),t.z());
        var missile=rack();Vec3 origin=missile==null?position():missile.position();
        Component failure = !validTarget(target) ? indicatorMessage("target")
                : ammoSlot() < 0 ? indicatorMessage("ammo")
                : getEnergy() < NuclearRules.DEFAULT.shotFE()
                    ? indicatorMessage("energy_status", getEnergy(), NuclearRules.DEFAULT.shotFE())
                    : missile == null ? indicatorMessage("rack") : null;
        if (failure != null) {
            releaseClearance();
            cycle.launched(false, ammoSlot() >= 0);
            launchFailure(server, failure);
            return;
        }
        // Opening stops at the launch gate until the current world's approach is checked. No reservation or debit yet.
        if(!checkLaunchClearance(server,target,origin))return;
        if (!store.reserve(new NuclearSiloStore.Flight(operation,getOptionalOwnerUUID().orElseThrow(),getUUID(),
                server.getGameTime()+NuclearRules.DEFAULT.lifeTicks(),BlockPos.containing(origin),false,payloadKind()))) {
            cycle.launched(false, ammoSlot() >= 0);
            launchFailure(server, indicatorMessage("reservation"));
            return;
        }
        missile.initialize(operation,getOptionalOwnerUUID().orElseThrow(),getUUID(),target,origin);
        missile.setYRot(getYRot());
        boolean success=SiloLaunchTransaction.execute(new SiloLaunchTransaction.Port(){
            @Override public boolean resourcesAvailable(){return !isRemoved()&&!missile.isRemoved()&&getHealth()>0&&invalidData==null&&validTarget(target)
                    &&hasEnergyStorage()&&getEnergy()>=NuclearRules.DEFAULT.shotFE()&&ammoSlot()>=0&&getUUID().equals(store.facility())
                    &&missile.payloadKind()==payloadKind()
                    &&store.flight(operation)!=null&&!store.committed(operation)
                    &&cycle.snapshot().phase()==SiloCycle.Phase.OPENING&&cycle.snapshot().remaining()==0&&operation.equals(cycle.snapshot().launch());}
            @Override public boolean spawnUnarmed(){return server.getEntity(operation)==missile&&missile.belongsToRack(getUUID());}
            @Override public void discardCandidate(){missile.discard();store.forgetFlight(operation);}
            @Override public void debitAndArm(){removeItem(ammoSlot(),1);consumeEnergy(NuclearRules.DEFAULT.shotFE());lastDebit=operation;store.commit(operation);missile.arm();}
        });
        cycle.launched(success,ammoSlot()>=0);
        if(success)nuclearSound(com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.NUCLEAR_LAUNCH.get());
        else launchFailure(server, indicatorMessage("commit"));
    }

    private void launchFailure(ServerLevel server, Component reason) {
        var owner = getOptionalOwnerUUID().map(server.getServer().getPlayerList()::getPlayer).orElse(null);
        if (owner != null) owner.displayClientMessage(Component.translatable(
                "message.doggiesnewvehicle.nuclear.launch_cancelled", reason), false);
    }
    private boolean checkLaunchClearance(ServerLevel server,BlockPos target,Vec3 origin){
        if(clearance==null){
            clearance=new NuclearClearance(new NuclearClearanceWorld(server),origin,target,getYRot(),server.getGameTime(),false,payloadKind());
            clearanceMessage(server,"checking");
        }
        NuclearClearance.State result;
        try{result=clearance.tick(server.getGameTime());}
        catch(RuntimeException failure){
            logClearanceFailure(target,failure);
            releaseClearance();cycle.launched(false,ammoSlot()>=0);clearanceMessage(server,"failed");return false;
        }
        if(result==NuclearClearance.State.CHECKING)return false;
        if(result==NuclearClearance.State.CLEAR){
            if(clearanceFinal){releaseClearance();return true;}
            releaseClearance();clearanceFinal=true;
            // Distant generation may take time: inspect the launch column again immediately before committing.
            clearance=new NuclearClearance(new NuclearClearanceWorld(server),origin,target,getYRot(),server.getGameTime(),true,payloadKind());
            return false;
        }
        var block=clearance.blocked();
        if(block!=null){
            var detail=clearance.description();var reason=clearance.rejection();
            clearanceMessage(server,clearance.launchSide()?"launch_blocked":"target_blocked",block.getX(),block.getY(),block.getZ(),
                    Component.translatable(detail.translationKey()),detail.id(),target.getX(),target.getY(),target.getZ(),block.getY()-target.getY(),
                    Component.translatable("message.doggiesnewvehicle.nuclear.clearance_reason_"+reason.name().toLowerCase(java.util.Locale.ROOT)));
            logClearanceBlocked(target,block,detail,reason);
        }
        else clearanceMessage(server,"timeout");
        releaseClearance();cycle.launched(false,ammoSlot()>=0);return false;
    }
    private void logClearanceFailure(BlockPos target,RuntimeException failure){
        LOGGER.warn("Nuclear clearance failed: silo={}, operation={}, target={}",getUUID(),cycle.snapshot().launch(),target,failure);
    }
    private void logClearanceBlocked(BlockPos target,BlockPos block,NuclearClearance.Description detail,NuclearClearance.Rejection reason){
        LOGGER.info("Nuclear clearance rejected: silo={}, operation={}, target={}, block={} at {}, reason={}",
                getUUID(),cycle.snapshot().launch(),target,detail.id(),block,reason);
    }
    private void clearanceMessage(ServerLevel server,String reason,Object... args){
        var owner=getOptionalOwnerUUID().map(server::getPlayerByUUID).orElse(null);
        if(owner!=null)owner.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.clearance_"+reason,args),false);
    }
    private void releaseClearance(){if(clearance!=null){clearance.close();clearance=null;}clearanceFinal=false;}
    private void nuclearSound(net.minecraft.sounds.SoundEvent sound){
        level().playSound(null,getX(),getY(),getZ(),sound,net.minecraft.sounds.SoundSource.BLOCKS,1.6F,1F);
    }
    private static void announceReady(ServerLevel server){
        var voice=com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.NUCLEAR_READY_VOICE.get();
        for(ServerPlayer player:server.players())player.playNotifySound(voice,net.minecraft.sounds.SoundSource.VOICE,1F,1F);
    }
    private static void announceLaunch(ServerLevel server){
        var siren=com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.NUCLEAR_SIREN.get();
        for(ServerPlayer player:server.players())player.playNotifySound(siren,net.minecraft.sounds.SoundSource.BLOCKS,1.6F,1F);
    }
    private static void announceLaunch(ServerLevel server,SiloPayloadKind kind){
        if(kind==SiloPayloadKind.NUCLEAR){announceLaunch(server);return;}
        var sound=com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.DOGEGG_LAUNCH.get();
        for(ServerPlayer player:server.players())player.playNotifySound(sound,net.minecraft.sounds.SoundSource.BLOCKS,1.6F,1F);
    }
    public CompoundTag visualSnapshot(){return entityData.get(VIEW).copy();}
    private void publish(){
        CompoundTag tag=SiloCycleCodec.write(cycle.snapshot());tag.putLong("Observed",level().getGameTime());
        if(haltedAt>=0)tag.putLong("HaltedAt",haltedAt);
        if(indicatorTarget!=null)tag.putLong("IndicatorTarget",indicatorTarget.asLong());
        tag.putBoolean("Ammo",ammoSlot()>=0);var rack=rack();if(rack!=null)tag.putInt("RackEntity",rack.getId());entityData.set(VIEW,tag);
    }
    private Component statusMessage(){
        if(clearance!=null)return Component.translatable("message.doggiesnewvehicle.nuclear.clearance_checking");
        int ammo=0;for(int i=0;i<getContainerSize();i++)if(getItem(i).is(ammunition()))ammo+=getItem(i).getCount();
        Component target=indicatorTarget==null?Component.translatable("message.doggiesnewvehicle.nuclear.target_unset"):
                Component.literal(indicatorTarget.getX()+", "+indicatorTarget.getY()+", "+indicatorTarget.getZ());
        var state=cycle.snapshot();
        return Component.translatable("message.doggiesnewvehicle.nuclear.status_full",
                Component.translatable("phase.doggiesnewvehicle.nuclear."+state.phase().name().toLowerCase(java.util.Locale.ROOT)),
                (state.remaining()+19)/20,getEnergy(),NuclearRules.DEFAULT.shotFE(),ammo,target);
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(player.getItemInHand(hand).is(DnvArtilleryItems.NUCLEAR_PREPARATION_REMOTE.get()))
            return NuclearPreparationRemoteItem.useOnSilo(this,player);
        if(invalidData!=null)return InteractionResult.FAIL;
        ItemStack held=player.getMainHandItem();
        if(getLocked()||player.isShiftKeyDown()||held.is(ModTags.Items.TOOLS_CROWBAR)
                ||held.getItem() instanceof IVehicleInteract||held.getItem() instanceof net.minecraft.world.item.NameTagItem
                ||held.is(ModItems.C4_BOMB.get())||held.is(ModItems.DETONATOR.get()))return super.m_6096_(player,hand);
        if(!canBindIndicator(player))return InteractionResult.FAIL;
        if(!level().isClientSide){
            var message=statusMessage();
            if(player.isCreative())message=message.copy().append("\n").append(Component.translatable("message.doggiesnewvehicle.nuclear.creative_hint"));
            player.displayClientMessage(message,false);
        }
        if(hand==InteractionHand.MAIN_HAND)openMenu(player);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void openMenu(Player player){if(canBindIndicator(player))super.openMenu(player);}
    @Override public InteractionResult onCrowbarInteract(ItemStack stack,Player player,InteractionHand hand){
        if(!canBindIndicator(player)||!cycle.canPack())return InteractionResult.FAIL;
        if(!level().isClientSide){
            var before=cycle.snapshot();int energy=getEnergy();
            boolean packed=false;
            try {
                cycle.reset();setEnergy(0);
                ItemStack box=com.atsuishio.superbwarfare.item.container.ContainerBlockItem.createInstance(this);
                var drop=spawnAtLocation(box);
                // Vanilla returns the ItemEntity even when addFreshEntity rejects it. Verify actual world registration.
                packed=drop!=null&&!drop.isRemoved()&&level() instanceof ServerLevel server&&server.getEntity(drop.getUUID())==drop;
                if(!packed){if(drop!=null)drop.discard();return InteractionResult.FAIL;}
                player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.packed_reset"),true);
            } finally {
                if(packed){var rack=rack();if(rack!=null)rack.discard();rackId=null;discard();}
                else {cycle.restore(before);setEnergy(energy);publish();}
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override protected boolean canAddPassenger(Entity passenger){return false;}
    @Override public void remove(RemovalReason reason){
        releaseRegistration(reason);
        super.remove(reason);
    }
    @Override public void onRemovedFromWorld(){
        releaseRegistration(getRemovalReason());
        super.onRemovedFromWorld();
    }
    private void releaseRegistration(RemovalReason reason){
        releaseClearance();
        if(reason!=null&&reason.shouldDestroy()&&level() instanceof ServerLevel server){
            var rack=rack();if(rack!=null)rack.discard();
            NuclearSiloStore store=NuclearSiloStore.get(server);if(store.writable()){SiloChunkTickets.release(server,getUUID());store.releaseFacility(getUUID());}}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.m_7380_(tag);CompoundTag state=invalidData!=null?invalidData.copy():SiloCycleCodec.write(cycle.snapshot());
        if(anchor!=null)state.putLong("Anchor",anchor.asLong());if(lastDebit!=null)state.putUUID("LastDebit",lastDebit);
        if(rackId!=null)state.putUUID("Rack",rackId);
        if(haltedAt>=0)state.putLong("HaltedAt",haltedAt);
        if(indicatorTarget!=null)state.putLong("IndicatorTarget",indicatorTarget.asLong());
        tag.put("NuclearSilo",state);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.m_7378_(tag);
        if(!tag.contains("NuclearSilo")){anchor=blockPosition().immutable();return;}
        var state=tag.getCompound("NuclearSilo");
        try{cycle.restore(SiloCycleCodec.read(state));if(!state.contains("Anchor",net.minecraft.nbt.Tag.TAG_LONG))throw new IllegalArgumentException("Anchor missing");
            anchor=BlockPos.of(state.getLong("Anchor"));lastDebit=state.hasUUID("LastDebit")?state.getUUID("LastDebit"):null;
            if(state.contains("HaltedAt")&&(!state.contains("HaltedAt",net.minecraft.nbt.Tag.TAG_LONG)||state.getLong("HaltedAt")<0))
                throw new IllegalArgumentException("Invalid halted clock");
            haltedAt=state.contains("HaltedAt")?state.getLong("HaltedAt"):-1;
            rackId=state.hasUUID("Rack")?state.getUUID("Rack"):null;
            indicatorTarget=state.contains("IndicatorTarget",net.minecraft.nbt.Tag.TAG_LONG)?BlockPos.of(state.getLong("IndicatorTarget")):null;
            if(indicatorTarget!=null&&!validTarget(indicatorTarget))indicatorTarget=null;
        }catch(RuntimeException bad){invalidData=state.copy();}
    }
    private static NuclearFlight.Point point(Vec3 p){return new NuclearFlight.Point(p.x,p.y,p.z);}
}

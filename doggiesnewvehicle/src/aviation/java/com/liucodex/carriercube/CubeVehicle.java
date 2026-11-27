package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.atsuishio.superbwarfare.item.weapon.CrowbarItem;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.doggiesnewvehicle.naval.Shipwreck;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Predicate;

class CubeVehicle extends VehicleEntity {
    private static final float TAKEOFF_POWER = .90F;
    private enum Phase { CRUISE, STATION_KEEP, ALIGNING, READY, COUNTDOWN, LAUNCH, CAPTURE, RETRACT, LOWERING, RAISING }
    private Phase phase = Phase.CRUISE;
    private boolean packingCarrier;
    private boolean unpacking;

    void beginUnpacking() { unpacking = true; }
    private UUID homeCarrier;
    private UUID soloOperator;
    boolean selectSoloStation(ServerPlayer p) {
        if (!pilot(p) || getPassengers().size()!=1 || phase!=Phase.READY || !changeSeat(p,1)) return false;
        soloOperator=p.getUUID();return true;
    }
    private boolean operatorPresent(CubeVehicle plane) {
        return getFirstPassenger() instanceof ServerPlayer helm && pilot(helm) || plane!=null && plane.getFirstPassenger() instanceof ServerPlayer rider && rider.getUUID().equals(soloOperator);
    }
    private UUID deckAircraft;
    private CubeHangar hangar;
    private Vec3 hover;
    private float hoverYaw;
    private int railTick;
    private int countdown;
    private boolean autoLaunch,autoCancelled;
    private int takeoffTicks;
    private int liftTick;
    private boolean crewReturned;
    private static final String PILOT_EXIT = "pilotExit";
    private int legacyMissileReserve;
    private CubeRecovery recovery;
    private int recoveryTick;
    private boolean attached;
    private boolean writingPose;
    private boolean commissioned;
    private CubeHoverFlight hoverFlight;

    CubeVehicle(EntityType<? extends CubeVehicle> type, Level level) {
        super(type, level);
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HOVER_MODE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LIFT_FRAME =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LIFT_RIDER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> CHIN_YAW =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> CHIN_PITCH =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> AIRCRAFT_ROLL =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ON_DECK =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HOVER_MODE,false);
        entityData.define(ON_DECK,false);
        entityData.define(CHIN_YAW,0F);
        entityData.define(CHIN_PITCH,0F);
        entityData.define(AIRCRAFT_ROLL,0F);
        entityData.define(LIFT_FRAME,0);
        entityData.define(LIFT_RIDER,-1);
        entityData.define(BRIDGE,new CompoundTag());
        entityData.define(MAP_MARKERS,new CompoundTag());
    }

    boolean hoverMode() { return entityData.get(HOVER_MODE); }
    boolean onDeck() { return entityData.get(ON_DECK); }
    int deckFrame() { return entityData.get(LIFT_FRAME); }
    private int previousLiftFrame, currentLiftFrame, lastLiftRider = -1;
    double liftHeight(float partial) {
        return Mth.lerp(partial,CubeDeckStow.platformHeight(previousLiftFrame),CubeDeckStow.platformHeight(currentLiftFrame));
    }

    void placeClientLiftRider() {
        int id = entityData.get(LIFT_RIDER);
        if (id < 0 || !(level().getEntity(id) instanceof CubeVehicle plane) || !plane.onDeck()) return;
        float yaw=getYRot();
        float previousYaw=yaw-Mth.wrapDegrees(yaw-yRotO);
        Vec3 previous=CubeDeckStow.planePosition(new Vec3(xOld,yOld,zOld),CubeGeometry.forward(previousYaw),previousLiftFrame);
        Vec3 current=CubeDeckStow.planePosition(position(),CubeGeometry.forward(yaw),currentLiftFrame);
        // The elevator owns both ends of the render interval, even when the cabin is empty.
        // SBW normalizes -180 to +180 in its tick. Mixing the old angle with the deck's
        // equivalent angle can render a whole revolution between two stationary poses.
        plane.setInterpolationSteps(0);
        plane.positionFixture(current,yaw);
        setPreviousPosition(plane,previous);
        plane.yRotO=previousYaw;
        plane.xRotO=0;
        plane.setPrevRoll(0);
        Vec3 step = current.subtract(previous);
        for (var rider : plane.getPassengers()) {
            plane.positionRider(rider);
            setPreviousPosition(rider,rider.position().subtract(step));
        }
    }

    private static void setPreviousPosition(Entity entity, Vec3 point) {
        entity.xOld = entity.xo = point.x;
        entity.yOld = entity.yo = point.y;
        entity.zOld = entity.zo = point.z;
    }

    private void setAttached(boolean value) {
        attached=value;
        entityData.set(ON_DECK,value);
    }
    private void setHoverMode(CubeHoverFlight flight) {
        sightOwner=null;
        hoverFlight=flight;
        entityData.set(HOVER_MODE,flight!=null);
        if (flight == null) {
            setTurretYRot(0);
            setTurretYRotO(0);
            setTurretXRot(0);
            setTurretXRotO(0);
            entityData.set(CHIN_YAW,0F);
            entityData.set(CHIN_PITCH,0F);
        }
    }

    private float lastNozzle,nozzle;
    private float previousGear, currentGear;
    private boolean gearKeyDown, keepGearDown;

    float gearFold(float partial) {
        float t = Mth.lerp(partial, previousGear, currentGear);
        return t * t * (3 - 2 * t);
    }

    private void updateLandingGear(float previous, boolean wasUp) {
        if (onGround() || onDeck()) keepGearDown=false;
        double dy = getY() - yo;
        // Keep the wheels down for a landing approach, including a vertical hover descent.
        double clearance = dy < -.015 ? 6 : wasUp ? 1 : 2;
        var beneath = new AABB(getX()-1.5, getY()-clearance, getZ()-1.5,
                getX()+1.5, getY()+.04, getZ()+1.5);
        boolean fold = !onDeck() && !keepGearDown && (hoverMode() || !onGround())
                && !level().getBlockCollisions(this, beneath).iterator().hasNext();
        setGearUp(fold);
        float progress = Mth.approach(previous, fold ? 1 : 0, 1F / 24);
        setSynchedGearRot(progress);
        setGearRot(85 * progress);
    }
    float nozzleAngle(float partial) { return Mth.lerp(partial,lastNozzle,nozzle); }
    @Override public boolean engineRunning() {
        if (isWreck()) return false;
        if (onDeck()) return getPower()>.005F;
        return hoverMode() || super.engineRunning();
    }

    @Override public float getEngineSoundVolume() {
        // SBW's active engine loop multiplies the base gain by three after its fade-in.
        return hoverMode() ? .08F + .18F * Mth.clamp(getPower(),0,1) : super.getEngineSoundVolume();
    }

    double thrust() {
        if (isWreck()) return 0;
        if (onDeck()) return Mth.clamp(getPower(),0,1);
        double speed = Math.sqrt(distanceToSqr(xOld, yOld, zOld));
        if (carrier()) return Mth.clamp(.18 + Math.max(Math.abs(getPower()), speed * 2), .18, 1);
        return hoverMode() ? Math.max(.34, Math.min(1, speed * 3)) : Mth.clamp(getPower(), 0, 1);
    }

    private void updateNozzle() {
        lastNozzle = nozzle;
        nozzle = Mth.approach(nozzle, hoverMode() ? 90 : 0, 6);
    }

    private net.minecraft.world.level.ChunkPos ticket;
    private void maintainTicket() {
        var world=(ServerLevel)level();
        var next=chunkPosition();
        if(next.equals(ticket)) return;
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(world,CubePrototype.ID,getUUID(),next.x,next.z,true,true);
        if(ticket!=null) net.minecraftforge.common.world.ForgeChunkManager.forceChunk(world,CubePrototype.ID,getUUID(),ticket.x,ticket.z,false,true);
        ticket=next;
    }

    @Override public void onAddedToWorld() {
        unpacking = false;
        super.onAddedToWorld();
        if (!level().isClientSide && !commissioned) {
            // SBW requires positive stored energy even when operating cost is zero.
            if (getEnergy() == 0) setEnergy(getMaxEnergy());
            commissioned = true;
        }
        if(level() instanceof ServerLevel world && carrier()) {
            CubeFleet.in(world).register(getUUID());
            maintainTicket();
            bridgeTag(t->t.put("structureTypes",CubeRadar.structureTypes(world)));
        }
    }

    @Override public void remove(Entity.RemovalReason reason) {
        if (level() instanceof ServerLevel server && (reason.shouldDestroy() || reason==Entity.RemovalReason.CHANGED_DIMENSION)) {
            if (ticket!=null) {
                net.minecraftforge.common.world.ForgeChunkManager.forceChunk(server,CubePrototype.ID,getUUID(),ticket.x,ticket.z,false,true);
                ticket=null;
            }
            if(carrier() && isAddedToWorld() && reason.shouldDestroy() && !packingCarrier) {
                for(int i=0;i<2;i++){
                    UUID id=hangar().identity(hangar().bayAt(i));
                    if(id!=null)releaseHome(server,id);
                }
            }
            if (!carrier() && reason == Entity.RemovalReason.KILLED) reportAircraftLoss(server);
        }
        if(navigation!=null && level() instanceof ServerLevel)navigation.close(this);
        super.remove(reason);
    }

    private void reportAircraftLoss(ServerLevel server) {
        if (homeCarrier == null) return;
        var ship = lookup(homeCarrier);
        if (ship == null) CubeFleet.notices(server).lost(getUUID(), homeCarrier);
        else ship.loseAircraft(getUUID());
        if (attached) {
            setAttached(false);
            setDeckPower(0);
            setNoGravity(false);
        }
        setHome(null);
    }

    private void loseAircraft(UUID id) {
        hangar().lost(id);
        if (!id.equals(deckAircraft)) return;
        deckAircraft = null;
        crewReturned = false;
        soloOperator = null;
        countdown = 0;
        railTick = 0;
        liftTick = 0;
        recovery = null;
        recoveryTick = 0;
        entityData.set(LIFT_FRAME, 0);
        bridgeTag(t -> { t.remove("trackBlocked"); t.remove("requested"); });
        phase = hover == null ? Phase.CRUISE : Phase.READY;
        message(Component.translatable("message.dnvcarriercube.aircraft_lost"));
    }

    static final int LINK_RANGE=256;

    private void setHome(UUID id) {
        homeCarrier=id;
        if(id==null && attached){
            setAttached(false);setHoverMode(new CubeHoverFlight(Vec3.ZERO));setDeltaMovement(Vec3.ZERO);setNoGravity(true);
        }
        bridgeTag(t->{
            t.putBoolean("linked",id!=null);t.remove("homePoint");
            if(id==null){t.remove("parked");t.remove("soloReady");t.remove("rolling");t.remove("countdown");t.remove(PILOT_EXIT);}
        });
    }
    private void releaseHome(ServerLevel world,UUID id) {
        for(var dimension:world.getServer().getAllLevels()) {
            if(dimension.getEntity(id) instanceof CubeVehicle plane && getUUID().equals(plane.homeCarrier)){
                plane.setHome(null);
                plane.message("母舰已解除编组，你可以继续驾驶或连接附近母舰");
                return;
            }
        }
        CubeFleet.notices(world).revoke(id,getUUID());
    }
    boolean disband(ServerPlayer helm,UUID id) {
        if(!carrier() || !pilot(helm) || id.equals(deckAircraft) || !hangar().inFlight(hangar().source(id),id))return false;
        hangar().lost(id);
        releaseHome((ServerLevel)level(),id);
        reportDeck();return true;
    }
    private void reconcileHome() {
        if(homeCarrier==null || !(level() instanceof ServerLevel world))return;
        if(CubeFleet.notices(world).consume(getUUID(),homeCarrier))setHome(null);
    }
    boolean joinCarrier(ServerPlayer rider) {
        if(carrier() || attached || !pilot(rider))return false;
        reconcileHome();
        if(homeCarrier!=null)return false;
        var candidates=level().getEntitiesOfClass(CubeVehicle.class,getBoundingBox().inflate(LINK_RANGE),
                v->v.carrier() && v.isAlive() && v.deckAircraft==null && v.distanceToSqr(this)<=LINK_RANGE*LINK_RANGE && v.hangar().returnBay()!=null);
        candidates.sort(java.util.Comparator.comparingDouble((CubeVehicle c)->distanceToSqr(c)).thenComparing(Entity::getUUID));
        if(candidates.isEmpty())return false;
        var ship=candidates.get(0);
        if(!ship.hangar().enroll(getUUID()))return false;
        setHome(ship.getUUID());
        ship.hangar().seen(ship.hangar().source(getUUID()),blockPosition().asLong());
        ship.reportDeck();return true;
    }
    private void updateHomeBeacon() {
        if(tickCount%4==0)reconcileHome();
        if (homeCarrier==null || (tickCount%4)!=0 || !(getFirstPassenger() instanceof ServerPlayer)) return;
        var ship=lookup(homeCarrier);
        if(ship!=null && ship.hangar().source(getUUID())==null){setHome(null);return;}
        if (ship==null) {
            if(bridgeView().contains("homePoint"))bridgeTag(t->t.remove("homePoint"));
            return;
        }
        long next=BlockPos.containing(ship.getX(),ship.getY()+CubeGeometry.DECK,ship.getZ()).asLong();
        int yaw=Math.floorMod(Math.round(ship.getYRot()),360);
        if(!bridgeView().contains("homePoint") || bridgeView().getLong("homePoint")!=next || bridgeView().getInt("homeYaw")!=yaw)
            bridgeTag(t->{t.putLong("homePoint",next);t.putInt("homeYaw",yaw);});
    }
    private CubeNavigation navigation;
    private CubeRadar radar;
    private long radarAvailable;
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.nbt.CompoundTag> BRIDGE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class,net.minecraft.network.syncher.EntityDataSerializers.COMPOUND_TAG);
    private static final net.minecraft.network.syncher.EntityDataAccessor<CompoundTag> MAP_MARKERS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CubeVehicle.class,net.minecraft.network.syncher.EntityDataSerializers.COMPOUND_TAG);
    net.minecraft.nbt.ListTag mapMarkers(){return entityData.get(MAP_MARKERS).getList("rows",Tag.TAG_COMPOUND);}
    net.minecraft.nbt.CompoundTag bridgeView() { return entityData.get(BRIDGE); }
    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key==BRIDGE && level().isClientSide && carrier()) {
            var state=bridgeView();
            // A phase packet can arrive between client ticks, while partial-tick rendering still runs.
            if ((state.getString("nav").equals("WARP_CALIBRATE") || state.getString("nav").equals("WARP_CHARGE"))
                    && state.contains("warpBearing")) {
                float locked=getYRot()+Mth.wrapDegrees(state.getFloat("warpBearing")-getYRot());
                setYRot(locked);
                yRotO=locked;
            }
        }
    }

    private void bridgeTag(java.util.function.Consumer<net.minecraft.nbt.CompoundTag> edit) {
        var tag=bridgeView().copy();edit.accept(tag);entityData.set(BRIDGE,tag);
    }
    private void reportDeck() {
        CubeHangar store=hangar();
        CubeVehicle plane=lookup(deckAircraft);
        if (deckAircraft != null && level() instanceof ServerLevel world) {
            if (plane != null && plane.isWreck()) {
                UUID destroyed = deckAircraft;
                plane.reportAircraftLoss(world);
                loseAircraft(destroyed);
                plane = null;
            }
            if (plane == null && CubeFleet.notices(world).consumeLoss(deckAircraft, getUUID())) loseAircraft(deckAircraft);
        }
        boolean aligned=phase==Phase.READY && railTick==0;
        boolean constrained = phase == Phase.RAISING || phase == Phase.LOWERING || aligned || phase == Phase.COUNTDOWN;
        entityData.set(LIFT_RIDER, constrained && plane != null && plane.attached ? plane.getId() : -1);
        int mask=store.eligibleMask();
        boolean open=aligned && (deckAircraft==null ? mask!=0 && store.freeSlot()>=0
                : plane!=null && plane.getPassengers().isEmpty());
        CompoundTag info=new CompoundTag();
        info.putInt("stock",store.stockMask());info.putInt("eligible",mask);info.putString("phase",phase.name());
        var source=store.source(deckAircraft);
        info.putInt("boardable",open && source!=null ? 1<<source.ordinal() : open ? mask : 0);
        info.putBoolean("board",open);info.putBoolean("launch",aligned && !crewReturned && plane!=null && plane.getFirstPassenger() instanceof ServerPlayer);
        info.putBoolean("lower",canStow());info.putBoolean("helmVacant",seatAvailable(0));info.putBoolean("unlock",aligned && deckAircraft==null);
        info.putBoolean("auto",autoLaunch);info.putInt("seconds",(countdown+19)/20);
        for(int i=0;i<2;i++) {
            var bay=store.bayAt(i);UUID id=store.identity(bay);
            String status="empty";
            if (id!=null) {
                boolean active=id.equals(deckAircraft);
                CubeVehicle away=active?null:lookup(id);
                if(!active && away==null && level() instanceof ServerLevel world && CubeFleet.notices(world).consumeLoss(id,getUUID())){
                    store.lost(id);info.putString("slot"+i,"empty");continue;
                }
                if((active && plane!=null || away!=null) && (tickCount%20==0 || store.lastPoint(bay)==null))
                    store.seen(bay,(active?plane:away).blockPosition().asLong());
                if(!active) {
                    info.putUUID("flight"+i,id);
                    if(store.lastPoint(bay)!=null)info.putLong("point"+i,store.lastPoint(bay));
                }
                status=!active?(away==null || !(away.getFirstPassenger() instanceof ServerPlayer)?"lostContact":"flying"):switch(phase) {
                    case RAISING -> "loading";
                    case COUNTDOWN -> "countdown";
                    case LAUNCH -> "launching";
                    case CAPTURE,RETRACT,LOWERING -> "recovering";
                    case READY -> crewReturned?"awaitStow":plane!=null && plane.bridgeView().getBoolean(PILOT_EXIT)?"awaitCrew":"ready";
                    default -> "loading";
                };
                info.putInt("cell"+i,bay.ordinal()+1);
            }
            info.putString("slot"+i,status);
        }
        if(plane!=null && plane.attached) {
            boolean solo=aligned && plane.getFirstPassenger() instanceof ServerPlayer p && p.getUUID().equals(soloOperator);
            boolean rolling=phase==Phase.LAUNCH;
            float deckPower=switch (phase) {
                case LAUNCH -> 1F;
                case COUNTDOWN -> (float)CubeDeckStow.ease(1-countdown/200.0);
                case CAPTURE -> recovery==null ? 0F : recovery.power(recoveryTick);
                default -> 0F;
            };
            if (plane.attached && (plane.getPower()!=deckPower || plane.getEngineStartOver()!=(deckPower>0)))
                plane.setDeckPower(deckPower);
            boolean cabinFull=plane.getFirstPassenger() instanceof ServerPlayer rider && !seatAvailable(returnSeat(rider));
            int seconds=phase==Phase.COUNTDOWN?(countdown+19)/20:0;
            var state=plane.bridgeView();
            int guidance=phase!=Phase.CAPTURE || recovery==null ? 0
                    : recoveryTick<recovery.approachTicks()?1:recoveryTick<=recovery.rolloutEnd()?2:3;
            if(state.getInt("guidance")!=guidance || state.getBoolean("cabinFull")!=cabinFull || state.getBoolean("parked")!=aligned || state.getBoolean("soloReady")!=solo || state.getBoolean("rolling")!=rolling || state.getInt("countdown")!=seconds)
                plane.bridgeTag(t->{t.putInt("guidance",guidance);t.putBoolean("cabinFull",cabinFull);t.putBoolean("parked",aligned);t.putBoolean("soloReady",solo);t.putBoolean("rolling",rolling);t.putInt("countdown",seconds);});
        }
        if(!info.equals(bridgeView().get("deck"))) {
            bridgeTag(t->{t.put("deck",info);for(String key:info.getAllKeys()) t.put(key,info.get(key));});
        }
    }

    boolean askForRecovery(ServerPlayer p) {
        if (!carrier() || !p.isAlive() || p.isSpectator() || getSeatIndex(p)!=1 || p.getVehicle()!=this
                || !canStow()) return false;
        if (seatAvailable(0)) return beginStow();
        if (bridgeView().getBoolean("requested")) return false;
        bridgeTag(t->t.putBoolean("requested",true));
        message("航空席请求回收已停妥的舰载机");return true;
    }
    private CubeRadar latestRadar;
    boolean scanRadar(ServerPlayer p) {
        if(!carrier() || !pilot(p) || radar!=null || level().getGameTime()<radarAvailable)return false;
        radar=new CubeRadar((ServerLevel)level(),blockPosition());radarAvailable=level().getGameTime()+300;
        bridgeTag(t->{t.putInt("scanProgress",0);t.putInt("scanTotal",0);t.putBoolean("scanning",true);t.putLong("radarAvailable",radarAvailable);});
        return true;
    }
    boolean radarPage(ServerPlayer p,String filter,boolean grouped,int offset) {
        if (!carrier() || !pilot(p)) return false;
        var id=net.minecraft.resources.ResourceLocation.tryParse(filter);
        if (!filter.isEmpty() && !filter.equals(CubeRadar.SLIME) && !filter.equals(CubeRadar.PORTAL)
                && (id==null || !level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).containsKey(id))) return false;
        CompoundTag page=latestRadar==null?new CompoundTag():latestRadar.page(filter,grouped,offset);
        page.putString("filter",filter);page.putBoolean("group",grouped);
        boolean filterChanged=!bridgeView().getCompound("radarPage").getString("filter").equals(filter);
        bridgeTag(t->t.put("radarPage",page));
        if(filterChanged)publishMap();
        return true;
    }
    private int mapX,mapZ,mapRadius;
    private long lastViewRequest=-1;
    boolean mapView(ServerPlayer p,int x,int z,int radius) {
        if(!carrier() || !pilot(p) || radius<1 || radius>1024 || lastViewRequest==level().getGameTime())return false;
        lastViewRequest=level().getGameTime();mapX=x;mapZ=z;mapRadius=radius;
        publishMap();
        return true;
    }
    private void publishMap() {
        if(latestRadar==null || mapRadius==0)return;
        var rows=latestRadar.markers(bridgeView().getCompound("radarPage").getString("filter"),mapX,mapZ,mapRadius);
        if(!rows.equals(mapMarkers())){
            var data=new CompoundTag();data.put("rows",rows);entityData.set(MAP_MARKERS,data);
        }
    }
    private void tickRadar(ServerLevel world) {
        if(radar==null)return;
        if(!radar.step(world)) {
            if(tickCount%20==0)bridgeTag(t->{t.putInt("scanProgress",radar.progress());t.putInt("scanTotal",radar.total());});
            return;
        }
        latestRadar=radar;radar=null;
        var selected=bridgeView().getCompound("radarPage");
        var page=latestRadar.page(selected.getString("filter"),selected.getBoolean("group"),0);
        bridgeTag(t->{t.putBoolean("scanning",false);t.putBoolean("scanned",true);t.putBoolean("partial",latestRadar.failed());t.put("radarPage",page);});
        publishMap();
    }



    boolean setWaypoint(ServerPlayer p,int x,int z) {
        if(!carrier() || !pilot(p) || !level().getWorldBorder().isWithinBounds(new BlockPos(x,0,z)))return false;
        long point=new BlockPos(x,0,z).asLong();
        if(!bridgeView().contains("waypoint") || bridgeView().getLong("waypoint")!=point)bridgeTag(t->t.putLong("waypoint",point));
        return true;
    }

    boolean setCruise(ServerPlayer p,int x,int z) {return startNavigation(p,x,z,false);}
    boolean startNavigation(ServerPlayer p,int x,int z,boolean warp) {
        if (!carrier() || !pilot(p) || navigation!=null || deckAircraft!=null || phase!=Phase.CRUISE && phase!=Phase.STATION_KEEP && phase!=Phase.READY
                || !level().getWorldBorder().isWithinBounds(CubeGeometry.hullEnvelope(new Vec3(x+.5,getY(),z+.5))))return false;
        phase=Phase.CRUISE;hover=null;setDeltaMovement(Vec3.ZERO);setPower(0);setTargetSpeed(0);setLiftSpeed(0);
        setWaypoint(p,x,z);
        navigation=new CubeNavigation(x,z,warp);reportNavigation();return true;
    }
    boolean cancelCruise(ServerPlayer p) {
        if(!pilot(p)||navigation==null)return false;
        if(navigation.warp())endCruise();else {navigation.stop();reportNavigation();}
        return true;
    }
    void missingSurface(){message("航行中止，目的地舰体下方没有有效地表");}
    void navigationBlocked(){message("航行中止，舰体前方受到阻挡");}
    boolean navigationClear(Vec3 from,Vec3 to,float yaw) {
        AABB swept=CubeGeometry.headingEnvelope(from,getYRot()).minmax(CubeGeometry.headingEnvelope(to,yaw)).inflate(.01,0,.01);
        return clear(swept,null);
    }
    private void reportNavigation() {
        var n=navigation;var old=bridgeView();
        String state=n==null?"":n.stage();int seconds=n==null?0:n.seconds(this);
        long turnStart=n!=null && n.warp() && "PREPARE".equals(state) ? n.turnStarted : -1;
        if(old.getString("nav").equals(state) && old.getInt("navSeconds")==seconds && old.getBoolean("cruise")== (n!=null)
                && (old.contains("turnStarted") ? old.getLong("turnStarted") : -1)==turnStart)return;
        bridgeTag(t->{t.putString("nav",state);t.putInt("navSeconds",seconds);t.putBoolean("cruise",n!=null);
            if (turnStart>=0) {t.putLong("turnStarted",turnStart);t.putFloat("turnOrigin",n.turnOrigin);}
            else {t.remove("turnStarted");t.remove("turnOrigin");}
            if (n!=null && n.warp()) t.putFloat("warpBearing",n.warpBearing(this));
            else t.remove("warpBearing");
            if ("WARP_CHARGE".equals(state)) t.putLong("chargeEnd",level().getGameTime()+n.ticksLeft());
            else t.remove("chargeEnd");
            if(n!=null){t.putInt("x",n.x());t.putInt("z",n.z());t.putBoolean("warp",n.warp());}});
    }
    private void endCruise() {if(navigation!=null)navigation.close(this);navigation=null;hover=position();hoverYaw=getYRot();phase=Phase.STATION_KEEP;reportNavigation();}
    private void cruiseTick() {if(navigation!=null) {if(navigation.tick(this))endCruise();else reportNavigation();}}

    static CompoundTag vectorTag(Vec3 point) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x",point.x);
        t.putDouble("y",point.y);
        t.putDouble("z",point.z);
        return t;
    }

    static Vec3 vector(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ShanyueCommissioned", commissioned);
        if (!carrier()) {
            tag.putFloat("ShanyueGearFold", getSynchedGearRot());
            tag.putBoolean("ShanyueKeepGearDown",keepGearDown);
            tag.putBoolean("ShanyueGearUp", getGearUp());
        }
        CompoundTag state = new CompoundTag();
        if (carrier()) {
            if(bridgeView().contains("waypoint"))state.putLong("waypoint",bridgeView().getLong("waypoint"));
            if(soloOperator!=null)state.putUUID("solo",soloOperator);
            if(navigation!=null)state.put("navigation",navigation.save());
            if(ticket!=null)state.putLong("ticket",ticket.toLong());
            state.put("hangar", hangar().save());
            state.putString("phase", phase.name());
            if (hover != null) state.put("anchor",vectorTag(hover));
            state.putFloat("yaw",hoverYaw);
            if (deckAircraft != null) state.putUUID("aircraft",deckAircraft);
            state.putInt("rail",railTick);
            state.putInt("railTicks",CubeGeometry.LAUNCH_TICKS);
            state.putInt("countdown",countdown);
            state.putBoolean("autoLaunch",autoLaunch);
            state.putBoolean("autoCancelled",autoCancelled);
            state.putInt("lift",liftTick);
            state.putBoolean("crewReturned",crewReturned);
            if (recovery != null) {
                state.put("recovery",recovery.save());
                state.putInt("recoveryTick",recoveryTick);
            }
        } else {
            if (homeCarrier != null) state.putUUID("home",homeCarrier);
            state.putBoolean("attached",attached);
            if (legacyMissileReserve > 0) state.putInt("legacyMissileReserve", legacyMissileReserve);
            if (hoverFlight != null) state.put("hoverVelocity",vectorTag(hoverFlight.velocity()));
            state.putInt("boost",takeoffTicks);
            state.putBoolean("soloReady",bridgeView().getBoolean("soloReady"));
            state.putBoolean(PILOT_EXIT,bridgeView().getBoolean(PILOT_EXIT));
        }
        tag.put("ShanyueDeck",state);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!carrier()) restoreMissileMount(tag);
        commissioned = tag.getBoolean("ShanyueCommissioned");
        if (!carrier() && tag.contains("ShanyueGearFold", Tag.TAG_ANY_NUMERIC)) {
            keepGearDown=tag.getBoolean("ShanyueKeepGearDown");
            setSynchedGearRot(Mth.clamp(tag.getFloat("ShanyueGearFold"), 0, 1));
            setGearUp(tag.getBoolean("ShanyueGearUp"));
        }
        if (!tag.contains("ShanyueDeck",Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = tag.getCompound("ShanyueDeck");
        if (carrier()) {
            if(state.contains("waypoint"))bridgeTag(t->t.putLong("waypoint",state.getLong("waypoint")));
            if (state.contains("hangar",Tag.TAG_COMPOUND)) hangar().restore(state.getCompound("hangar"));
            if (unpacking) {
                // World saves resume the deck operation. A portable hull starts at its new berth.
                reportDeck();
                return;
            }
            soloOperator=state.hasUUID("solo")?state.getUUID("solo"):null;
            if(state.contains("navigation"))navigation=CubeNavigation.restore(state.getCompound("navigation"));
            ticket=state.contains("ticket")?new net.minecraft.world.level.ChunkPos(state.getLong("ticket")):null;
            hover = state.contains("anchor") ? vector(state.getCompound("anchor")) : null;
            hoverYaw = state.getFloat("yaw");
            deckAircraft = state.hasUUID("aircraft") ? state.getUUID("aircraft") : null;
            phase = Phase.CRUISE;
            if (hover!=null) {
                try { phase=Phase.valueOf(state.getString("phase")); }
                catch (IllegalArgumentException ignored) { phase=Phase.READY; }
            }
            countdown=Mth.clamp(state.getInt("countdown"),0,200);
            autoLaunch=state.getBoolean("autoLaunch");autoCancelled=state.getBoolean("autoCancelled");
            int savedDuration=state.contains("railTicks") ? Math.max(1,state.getInt("railTicks")) : 132;
            railTick=Mth.clamp(Math.round(state.getInt("rail")*CubeGeometry.LAUNCH_TICKS/(float)savedDuration),0,CubeGeometry.LAUNCH_TICKS);
            if(phase==Phase.READY && railTick>0 && deckAircraft!=null)phase=Phase.RETRACT;
            liftTick = Mth.clamp(state.getInt("lift"),0,CubeDeckStow.TOTAL);
            entityData.set(LIFT_FRAME,liftTick);
            crewReturned = state.getBoolean("crewReturned");
            if (phase==Phase.CAPTURE) {
                recovery = CubeRecovery.load(state.getCompound("recovery"),hover,hoverYaw);
                recoveryTick = Mth.clamp(state.getInt("recoveryTick"),0,recovery.duration()-1);
            }
            reportDeck();
        } else {
            homeCarrier = state.hasUUID("home") ? state.getUUID("home") : null;
            setAttached(state.getBoolean("attached"));
            setHoverMode(state.contains("hoverVelocity") ? new CubeHoverFlight(vector(state.getCompound("hoverVelocity"))) : null);
            takeoffTicks = Mth.clamp(state.getInt("boost"),0,CubeGeometry.TAKEOFF_BLEND_TICKS);
            bridgeTag(t -> {
                t.putBoolean(PILOT_EXIT, state.getBoolean(PILOT_EXIT));
                t.putBoolean("soloReady", state.getBoolean("soloReady"));
                t.putBoolean("linked", homeCarrier != null);
            });
            if (unpacking && attached) setHome(null);
        }
    }

    boolean chargingWarp() {return bridgeView().contains("chargeEnd");}
    double chargeFraction(float partial) {
        return chargingWarp() ? Mth.clamp(1-(bridgeView().getLong("chargeEnd")-level().getGameTime()-partial)/400.,0,1) : 0;
    }
    double arrivalAge(float partial) {
        return bridgeView().contains("warpArrival") ? level().getGameTime()-bridgeView().getLong("warpArrival")+partial : 100;
    }

    boolean carrier() {
        return getType() == CubePrototype.CARRIER.get();
    }

    static net.minecraft.world.phys.EntityHitResult pickHull(Player player, Vec3 start, Vec3 end) {
        var scan = new AABB(start, end).inflate(CubeGeometry.HALF_LENGTH + 4, 30, CubeGeometry.HALF_LENGTH + 4);
        var from = com.atsuishio.superbwarfare.tools.OBB.vec3ToVector3d(start);
        var to = com.atsuishio.superbwarfare.tools.OBB.vec3ToVector3d(end);
        double nearest = start.distanceToSqr(end) + 1.0e-8;
        net.minecraft.world.phys.EntityHitResult result = null;
        for (var ship : player.level().getEntitiesOfClass(CubeVehicle.class, scan, candidate -> candidate.carrier() && candidate.isPickable())) {
            if (!ship.getBoundingBox().inflate(.1).intersects(new AABB(start,end).inflate(.01))) continue;
            for (var box : ship.getOBBs()) {
                if (box.part != com.atsuishio.superbwarfare.tools.OBB.Part.BODY) continue;
                var hit = box.clip(from, to);
                if (hit.isEmpty()) continue;
                var point = com.atsuishio.superbwarfare.tools.OBB.vector3dToVec3(hit.get());
                double distance = start.distanceToSqr(point);
                if (distance < nearest) {
                    result = new net.minecraft.world.phys.EntityHitResult(ship, point);
                    nearest = distance;
                }
            }
        }
        return result;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (onDeck() || player.isSpectator() || !player.isAlive()) return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        if (carrier() && player.isShiftKeyDown() && getPassengers().isEmpty()
                && (stack.is(ModTags.Items.TOOLS_CROWBAR) || stack.getItem() instanceof CrowbarItem)) {
            // A portable carrier keeps its UUID and its flights. Destruction still disbands them.
            packingCarrier = true;
            try {
                return super.interact(player, hand);
            } finally {
                packingCarrier = false;
            }
        }
        return super.interact(player, hand);
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return !level().isClientSide;
    }

    private boolean guided() {
        return carrier() ? phase != null && (phase != Phase.CRUISE || navigation!=null) : attached || hoverFlight != null;
    }

    private boolean simulatesMotion() {
        return isControlledByLocalInstance() && !guided();
    }

    private Entity inputPilot;
    private short heldMovement;
    @Override
    public void processInput(short keys) {
        inputPilot=getFirstPassenger();
        heldMovement=(short)(keys & 15);
        boolean gearKey=(keys & 16)!=0;
        if (!level().isClientSide && inputPilot instanceof Player && !carrier() && !guided()
                && gearKey && !gearKeyDown && !wheelsOnRunway()) {
            keepGearDown=!keepGearDown && getGearUp();
        }
        gearKeyDown=gearKey;
        applyPilotInput(keys);
    }

    private void resumeHeldMovement() {
        applyPilotInput(inputPilot!=null && inputPilot==getFirstPassenger() ? heldMovement : (short)0);
    }

    private void applyPilotInput(short keys) {
        // SBW consumes its ascent bit for the native gear switch during engine work.
        // A held W + ascent arrives only once, so retain the pilot's packet until key-up.
        runwayPilot = !carrier() && !guided() && (keys & 20) == 20 && getFirstPassenger() instanceof Player p ? p : null;
        if (phase==Phase.STATION_KEEP && keys!=0 && !level().isClientSide) {phase=Phase.CRUISE;hover=null;}
        if (hoverFlight != null) {
            hoverFlight.input(keys);
        }
        super.processInput(guided() ? (short) 0 : keys);
    }

    @Override
    public void mouseInput(double horizontal, double vertical) {
        if (guided()) {
            super.mouseInput(0, 0);
        } else {
            super.mouseInput(horizontal, vertical);
        }
    }

    @Override
    public void travel() {
        if (!simulatesMotion()) return;
        if (brakingOnWheels()) setOnGround(true);
        super.travel();
    }

    private boolean brakingOnWheels() {
        return !carrier() && getFirstPassenger() instanceof Player && (backInputDown() || downInputDown())
                && !forwardInputDown() && takeoffTicks==0 && runwayClimbTicks==0 && getDeltaMovement().y<=.12
                && wheelsOnRunway();
    }

    private Player runwayPilot;
    private int runwayClimbTicks;

    private Vec3 runwayDeparture(Vec3 motion) {
        if (runwayPilot == null || runwayPilot != getFirstPassenger() || getPower() < .42F) {
            runwayClimbTicks = 0;
            return motion;
        }
        if (runwayClimbTicks == 0) {
            if (!wheelsOnRunway()) return motion;
            runwayClimbTicks = 12;
        }
        Vec3 heading = CubeHoverFlight.heading(getYRot());
        Vec3 departure = heading.scale(Math.max(1.08, motion.dot(heading))).add(0, Math.max(.27, motion.y), 0);
        var body = getBoundingBox();
        var clearance = new AABB(body.minX+.04, Math.max(getY()+.04, body.minY+.04), body.minZ+.04,
                body.maxX-.04, body.maxY-.04, body.maxZ-.04).expandTowards(departure);
        if (!clear(clearance, this)) {
            runwayClimbTicks = 0;
            return motion;
        }
        runwayClimbTicks--;
        setOnGround(false);
        setXRot(-6);
        super.setDeltaMovement(departure);
        return departure;
    }

    private boolean wheelsOnRunway() {
        if (getSynchedGearRot()>.15F) return false;
        var transform=getVehicleTransform(1);
        for (Vec3 wheel : computed().getTerrainCompat()) {
            var foot=modelPoint(transform,wheel.x,wheel.y,wheel.z);
            var tread=new AABB(foot.x-.12,foot.y-.14,foot.z-.08,foot.x+.12,foot.y+.02,foot.z+.08);
            if(level().getBlockCollisions(this,tread).iterator().hasNext())return true;
        }
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 motion) {
        if (simulatesMotion()) {
            if (!carrier() && type == MoverType.SELF) {
                // SBW applies ground friction between engine work and this final collision-aware move.
                motion = runwayDeparture(motion);
                if (brakingOnWheels()) {
                    // Ground roll has wheel friction and cannot regain lift while the pilot brakes.
                    // SBW already applies its .97 wheel brake to the descent key.
                    double friction=downInputDown() ? 1 : .97;
                    motion=new Vec3(motion.x*friction,Math.min(motion.y,-computed().getGravity()),motion.z*friction);
                    super.setDeltaMovement(motion);
                }
                if (takeoffTicks > 0) {
                    // Carry the final rail step through SBW's first engine ticks, including ground friction.
                    Vec3 heading = CubeGeometry.forward(getYRot());
                    Vec3 release = CubeGeometry.departureVelocity(heading);
                    double blend = (double)takeoffTicks / CubeGeometry.TAKEOFF_BLEND_TICKS;
                    double forwardSpeed = Mth.lerp(blend, CubeGeometry.FLIGHT_SPEED, CubeGeometry.LAST_RAIL_STEP);
                    motion = motion.add(heading.scale(Math.max(0, forwardSpeed - motion.dot(heading))))
                            .add(0, Math.max(0, release.y * blend - motion.y), 0);
                    super.setDeltaMovement(motion);
                }
                if (!onGround() && takeoffTicks == 0 && runwayClimbTicks == 0 && getPower() > .35F) {
                    // Damp lateral slip while preserving SBW's throttle, bank and pitch integration.
                    Vec3 forward=CubeHoverFlight.heading(getYRot());
                    Vec3 lateral=motion.subtract(forward.scale(motion.dot(forward))).multiply(1,0,1);
                    motion=motion.subtract(lateral.scale(.14));
                    super.setDeltaMovement(motion);
                }
                double limit = CubeGeometry.flightLimit(takeoffTicks);
                if (motion.lengthSqr() > limit * limit) {
                    motion = motion.scale(limit / motion.length());
                    super.setDeltaMovement(motion);
                }
            }
            super.move(type, motion);
        }
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
        // SBW integrates its own gravity in baseTick, even when vanilla NoGravity is set.
        if (isControlledByLocalInstance()) {
            super.setDeltaMovement(guided() ? Vec3.ZERO : motion);
        }
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        // Spawn and velocity packets may update the client readout without simulating movement.
        if (level().isClientSide) {
            super.setDeltaMovement(new Vec3(x, y, z));
        } else {
            super.lerpMotion(x, y, z);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (level().isClientSide || unpacking || !guided() || writingPose || isWreck()) {
            super.setPos(x, y, z);
        }
    }

    @Override
    public void supportEntities() {
        if (carrier() || !onDeck()) {
            super.supportEntities();
        }
    }

    @Override
    public void preventStacking() {
        if (simulatesMotion()) {
            super.preventStacking();
        }
    }

    @Override
    public void crushEntities() {
        // The test hull is operated in a creative test world.
    }

    private boolean followsAircraftMotion() {
        return !onDeck() || bridgeView().getInt("guidance")>0 || bridgeView().getBoolean("rolling");
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        // SBW uses SERVER_YAW for both entity metadata and vanilla byte-angle movement packets.
        // The carrier already has its exact heading in metadata; retain it when updating position.
        boolean client=level().isClientSide;
        super.lerpTo(x,y,z,client ? getServerYaw() : yaw,client && !carrier() ? getServerPitch() : pitch,steps,teleport);
        if (client && carrier() && distanceToSqr(x,y,z) > 64 * 64) {
            setInterpolationSteps(0);
            setPos(x,y,z);
            setPreviousPosition(this,position());
            for (var rider : getPassengers()) {
                positionRider(rider);
                setPreviousPosition(rider,rider.position());
            }
        }
        // One movement packet per tick. Render between the last two positions, including the firing socket.
        if (client && !carrier() && followsAircraftMotion()) setInterpolationSteps(1);
    }

    @Override public void destroy() {
        if (!carrier()) {
            if (level() instanceof ServerLevel server && !isRemoved()) {
                reportAircraftLoss(server);
                setHoverMode(null);
                setNoGravity(false);
                takeoffTicks = 0;
                runwayClimbTicks = 0;
                // SBW evaluates its overkill removal threshold again after this callback.
                if (getHealth() <= -getMaxHealth()) setHealth(0);
                Shipwreck.playBreakup(server, position().add(0, 1.5, 0));
            }
            super.destroy();
            return;
        }
        if (level().isClientSide || isRemoved()) return;
        setWreck(true);
        setHealth(0);
        setSympatheticDetonated(true);
        ejectPassengers();
    }

    @Override public void vehicleExplosion(com.atsuishio.superbwarfare.data.vehicle.subdata.DestroyInfo info) {
        if (!carrier()) super.vehicleExplosion(info);
    }

    @Override
    public void tick() {
        if (carrier() && level() instanceof ServerLevel wreckLevel && (isWreck() || getHealth() <= 0)) {
            destroy();
            Shipwreck.tick(this, wreckLevel, false);
            return;
        }
        float previousYaw=getYRot();
        float previousPitch=getXRot(), previousBank=getRoll();
        float gearBeforeTravel = getSynchedGearRot();
        boolean wasGearUp = getGearUp();
        Vec3 takeoffStart = takeoffTicks > 0 && !level().isClientSide ? position() : null;
        if (!carrier() && !level().isClientSide) {
            if (legacyMissileReserve > 0) returnLegacyReserve();
            GunData launcher = getGunData("Missile");
            if (launcher != null && launcher.shouldStartReloading(this)) launcher.startReload();
        }
        super.tick();
        if (level().isClientSide) {
            if (!carrier()) {
                float bank=entityData.get(AIRCRAFT_ROLL);
                setPrevRoll(tickCount<=1 ? bank : bank-Mth.wrapDegrees(bank-previousBank));
                setZRot(bank);
                if (followsAircraftMotion()) {
                    float yaw=getServerYaw(), pitch=getServerPitch();
                    yRotO=yaw-Mth.wrapDegrees(yaw-previousYaw);
                    xRotO=pitch-Mth.wrapDegrees(pitch-previousPitch);
                    setYRot(yaw);
                    setXRot(pitch);
                }
                updateNozzle();
                // SBW's raw turret fields are local. Render the server-authoritative aim on every client.
                setTurretYRot(entityData.get(CHIN_YAW));
                setTurretXRot(entityData.get(CHIN_PITCH));

                previousGear = tickCount <= 1 ? getSynchedGearRot() : currentGear;
                currentGear = getSynchedGearRot();
            }
            else {
                float target=getServerYaw();
                var nav=bridgeView();
                boolean aligning=nav.getBoolean("warp") && nav.getString("nav").equals("PREPARE");
                boolean locked=nav.getString("nav").equals("WARP_CALIBRATE") || nav.getString("nav").equals("WARP_CHARGE");
                if (locked && nav.contains("warpBearing")) {
                    // The server has reached the bearing. Do not ease the remaining error during the silent hold.
                    setYRot(previousYaw+Mth.wrapDegrees(nav.getFloat("warpBearing")-previousYaw));
                    yRotO=getYRot();
                } else if (aligning && nav.contains("turnStarted")) {
                    // The rotation is deterministic. Movement packets may arrive in pairs or skip a client tick.
                    long elapsed=level().getGameTime()-nav.getLong("turnStarted");
                    float origin=nav.getFloat("turnOrigin"), bearing=nav.getFloat("warpBearing");
                    yRotO=CubeNavigation.turnYaw(origin,bearing,elapsed);
                    setYRot(CubeNavigation.turnYaw(origin,bearing,elapsed+1));
                } else {
                    float delta=Mth.wrapDegrees(target-previousYaw);
                    yRotO=tickCount<=1 ? target : previousYaw;
                    setYRot(tickCount<=1 ? target : previousYaw+(aligning || Math.abs(delta)<.01F ? delta : .5F*delta));
                }
                previousLiftFrame=currentLiftFrame;
                currentLiftFrame=deckFrame();
                int rider = entityData.get(LIFT_RIDER);
                if (tickCount<=1 || rider != lastLiftRider && rider >= 0) previousLiftFrame=currentLiftFrame;
                lastLiftRider=rider;
            }
        }
        if (level().isClientSide || !isAlive() || isWreck()) {
            return;
        }
        if (!carrier()) {
            if (inputPilot!=getFirstPassenger()) { inputPilot=null;heldMovement=0; }
            if (runwayPilot != getFirstPassenger()) runwayPilot = null;
            updateHomeBeacon();
            if (hoverFlight != null) {
                tickHoverFlight();
            }
            updateLandingGear(gearBeforeTravel, wasGearUp);
            entityData.set(AIRCRAFT_ROLL,Mth.wrapDegrees(getRoll()));
            setServerPitch(getXRot());
            setServerYaw(getYRot());
            if (takeoffTicks > 0 && takeoffStart != null) {
                // SBW rewrites velocity after move(). Publish the actual collision-resolved free-flight step.
                setDeltaMovement(position().subtract(takeoffStart));
                takeoffTicks--;
            }
            return;
        }
        maintainTicket();
        tickRadar((ServerLevel)level());
        reportDeck();
        switch (phase) {
            case CRUISE -> cruiseTick();
            case ALIGNING -> align();
            case READY, STATION_KEEP -> hold();
            case COUNTDOWN -> countdownTick();
            case LAUNCH -> launchTick();
            case CAPTURE -> captureTick();
            case RETRACT -> retractTick();
            case LOWERING -> lowerTick();
            case RAISING -> raiseTick();
        }
        setServerYaw(getYRot());
    }

    void jumpTo(Vec3 point) {
        positionFixture(point,getYRot());
        maintainTicket();
        bridgeTag(t->t.putLong("warpArrival",level().getGameTime()));

        for (var rider:getPassengers()) {
            positionRider(rider);
            if (rider instanceof ServerPlayer player) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(this));
                player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
            }
        }
        // Broadcast in the destination after all passengers have moved into sound range.
        level().playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                net.minecraft.sounds.SoundSource.NEUTRAL,4F,.72F);
    }

    void positionFixture(Vec3 point, float yaw) {
        positionFixture(point, yaw, 0, 0);
    }

    private void positionFixture(Vec3 point, float yaw, float pitch, float roll) {
        writingPose = true;
        try {
            setYRot(yaw);
            setXRot(pitch);
            setZRot(roll);
            setRoll(roll);
            setPos(point.x, point.y, point.z);
            setDeltaMovement(Vec3.ZERO);
            updateOBB();
            refreshBoundingBoxFromOBBs();
        } finally {
            writingPose = false;
        }
    }

    private Vec3 runway() {
        return CubeGeometry.forward(hoverYaw);
    }

    private CubeVehicle lookup(UUID identity) {
        if (identity != null && level() instanceof ServerLevel world
                && world.getEntity(identity) instanceof CubeVehicle cube && cube.isAlive()) {
            return cube;
        }
        return null;
    }

    CubeHangar hangar() {
        if (hangar == null) {
            hangar = new CubeHangar();
        }
        return hangar;
    }

    private void restoreMissileMount(CompoundTag save) {
        if (getWeaponIndex(0) == 2) setWeaponIndex(0, 1);
        legacyMissileReserve = save.getCompound("ShanyueDeck").getInt("legacyMissileReserve");
        var weapons = save.getCompound("WeaponState");
        if (!weapons.contains("AirMissile", Tag.TAG_COMPOUND)) return;
        var oldStack = weapons.getCompound("AirMissile").copy();
        oldStack.putString("id", "superbwarfare:vehicle_gun");
        oldStack.putByte("Count", (byte)1);
        var oldGun = GunData.from(ItemStack.of(oldStack));
        int rounds = oldGun.ammo.get() + oldGun.virtualAmmo.get();
        if (rounds <= 0) return;
        modifyGunData("Missile", launcher -> {
            int transfer = Math.min(rounds, Math.max(0, launcher.compute().getMagazine() - launcher.ammo.get()));
            if (transfer > 0) {
                launcher.resetStatus();
                launcher.ammo.set(launcher.ammo.get() + transfer);
            }
            legacyMissileReserve += rounds - transfer;
        });
    }

    private void returnLegacyReserve() {
        var hold = getInventory();
        var item = com.atsuishio.superbwarfare.init.ModItems.LARGE_ANTI_GROUND_MISSILE.get();
        int stackLimit = item.getDefaultInstance().getMaxStackSize();
        for (int slot = 0; slot < hold.getSlots() && legacyMissileReserve > 0; slot++) {
            int room = Math.min(hold.getSlotLimit(slot), stackLimit)
                    - hold.getStackInSlot(slot).getCount();
            int offer = Math.min(legacyMissileReserve, room);
            if (offer > 0) legacyMissileReserve -= offer - hold.insertItem(slot, new ItemStack(item, offer), false).getCount();
        }
    }

    @Override public void setChanged() {
        super.setChanged();
        // SBW caches the reserve count for ten ticks, including the tick a reload finishes.
        if (!level().isClientSide && !carrier() && isAddedToWorld()) {
            GunData mounted = getGunData("Missile");
            if (mounted != null) mounted.cachedBackupAmmo = -1;
        }
    }

    @Override public boolean canShoot(net.minecraft.world.entity.LivingEntity actor) {
        return !carrier() && !onDeck() && actor==getFirstPassenger() && super.canShoot(actor);
    }

    @Override public void vehicleShoot(net.minecraft.world.entity.LivingEntity actor,String weapon,Vec3 position) {
        if (!canShoot(actor) || CubePrototype.guidedWeapon(weapon)) return;
        super.vehicleShoot(actor,weapon,position);
    }

    private boolean validShot(net.minecraft.world.entity.LivingEntity actor,String weapon,UUID target) {
        if (!canShoot(actor) || weapon==null || !weapon.equals(getGunName(0))) return false;
        return !CubePrototype.guidedWeapon(weapon) || target!=null && level() instanceof ServerLevel level
                && CubePrototype.missileTarget(weapon,actor,level.getEntity(target));
    }

    @Override public void vehicleShoot(net.minecraft.world.entity.LivingEntity actor,String weapon,UUID target,Vec3 position) {
        if (validShot(actor,weapon,target)) super.vehicleShoot(actor,weapon,target,position);
    }

    @Override public void vehicleShoot(net.minecraft.world.entity.LivingEntity actor,UUID target,Vec3 position) {
        if (actor==getFirstPassenger() && validShot(actor,getGunName(0),target)) super.vehicleShoot(actor,target,position);
    }

    float chinYaw(float partial) {
        return hoverMode() ? Mth.rotLerp(partial,getTurretYRotO(),getTurretYRot()) : 0;
    }
    float chinPitch(float partial) {
        return hoverMode() ? Mth.lerp(partial,getTurretXRotO(),getTurretXRot()) : 0;
    }
    private org.joml.Matrix4d chinTransform(float partial) {
        // Fixed-wing rounds and the HUD share the airframe axis, even on the frame of a mode change.
        if (!hoverMode()) return getVehicleTransform(partial);
        Vec3 pivot=CubeGunRig.PIVOT;
        Vec3 axle=CubeGunRig.TRAVERSE_AXIS;
        return getVehicleTransform(partial).translate(axle.x,axle.y,axle.z)
                .rotateY(Math.toRadians(-chinYaw(partial))).translate(pivot.x-axle.x,pivot.y-axle.y,pivot.z-axle.z)
                .rotateX(Math.toRadians(chinPitch(partial)))
                .translate(-pivot.x,-pivot.y,-pivot.z);
    }

    @Override public org.joml.Matrix4d getTransformFromString(String key,float partial) {
        if ("ShanyueLift".equals(key)) {
            double height = level().isClientSide ? liftHeight(partial) : CubeDeckStow.platformHeight(deckFrame());
            return getVehicleTransform(partial).translate(0, height, 0);
        }
        return "ShanyueGun".equals(key) ? chinTransform(partial) : super.getTransformFromString(key,partial);
    }
    @Override public org.joml.Quaterniond getRotationFromString(String key,float partial) {
        return "ShanyueGun".equals(key) ? chinTransform(partial).getNormalizedRotation(new org.joml.Quaterniond()) : super.getRotationFromString(key,partial);
    }
    @Override public org.joml.Quaterniond getRotationFromString(String key) {
        return "ShanyueGun".equals(key) ? getRotationFromString(key,1) : super.getRotationFromString(key);
    }
    static Vec3 modelPoint(org.joml.Matrix4d matrix,double x,double y,double z) {
        var v=matrix.transformPosition(new org.joml.Vector3d(x,y,z));
        return new Vec3(v.x,v.y,v.z);
    }
    @Override public Vec3 getShootPos(String weapon,float partial) {
        if ("Cannon".equals(weapon)) {
            Vec3 muzzle=CubeGunRig.MUZZLE;
            return modelPoint(chinTransform(partial),muzzle.x,muzzle.y,muzzle.z);
        }
        return super.getShootPos(weapon,partial);
    }
    @Override public Vec3 getShootVec(String weapon,float partial) {
        if (!"Cannon".equals(weapon)) return super.getShootVec(weapon,partial);
        var dir=chinTransform(partial).transformDirection(new org.joml.Vector3d(0,0,1)).normalize();
        return new Vec3(dir.x,dir.y,dir.z);
    }
    private boolean usingChinGun(Entity rider) {
        return !carrier() && rider==getFirstPassenger() && "Cannon".equals(getGunName(0));
    }
    @Override public Vec3 getShootPos(Entity rider,float partial) {
        return usingChinGun(rider) ? getShootPos("Cannon",partial) : super.getShootPos(rider,partial);
    }
    @Override public Vec3 getShootVec(Entity rider,float partial) {
        return usingChinGun(rider) ? getShootVec("Cannon",partial) : super.getShootVec(rider,partial);
    }
    @Override public Vec3 getShootPosForHud(Entity rider,float partial) {
        return usingChinGun(rider) ? getShootPos("Cannon",partial) : super.getShootPosForHud(rider,partial);
    }
    @Override public Vec3 getShootDirectionForHud(Entity rider,float partial) {
        return usingChinGun(rider) ? getShootVec("Cannon",partial) : super.getShootDirectionForHud(rider,partial);
    }
    @Override public Vec3 getCameraPosition(float partial,Player player,boolean zoom,boolean firstPerson) {
        if (firstPerson && getFirstPassenger()==player) {
            if (carrier()) return modelPoint(getVehicleTransform(partial),15.15 * CubeGeometry.SHIP_SCALE,27.92 * CubeGeometry.SHIP_SCALE,-42.15 * CubeGeometry.SHIP_SCALE);
            if (hoverMode()) {
                Vec3 muzzle = CubeGunRig.MUZZLE;
                return modelPoint(chinTransform(partial), muzzle.x, muzzle.y + .18, muzzle.z - .63);
            }
            return modelPoint(getVehicleTransform(partial),0,2.48,4.82);
        }
        return super.getCameraPosition(partial,player,zoom,firstPerson);
    }
    @Override public net.minecraft.world.phys.Vec2 getCameraRotation(float partial, Player player, boolean zoom, boolean firstPerson) {
        if (firstPerson && hoverMode() && player == getFirstPassenger())
            return new net.minecraft.world.phys.Vec2(player.getViewYRot(partial), player.getViewXRot(partial));
        return super.getCameraRotation(partial, player, zoom, firstPerson);
    }
    @Override public double getSensitivity(double original, boolean zoom, int seat, boolean onGround) {
        // The fixed-wing pilot steers via SBW mouse-input packets. In hover, mouse-look drives the sight.
        if (!carrier() && !hoverMode() && seat==0) return 0;
        return super.getSensitivity(original,zoom,seat,onGround);
    }
    @Override public boolean useAircraftCamera(int seat) {
        return !hoverMode() && super.useAircraftCamera(seat);
    }
    @Override public void adjustTurretAngle() {
        if (!hoverMode()) { setTurretXRot(0);setTurretYRot(0); }
    }
    private float aimHover(float yaw,ServerPlayer operator) {
        if (operator==null) return yaw;
        float manualTurn=hoverFlight.turn();
        yaw+=manualTurn;
        float sightYaw=operator.getYRot(), sightPitch=operator.getXRot();
        if (operator.getUUID().equals(sightOwner) && tickCount-sightTick<=15) {
            Vec3 socket=CubeGunRig.PIVOT;
            Vec3 delta=sightPoint.subtract(modelPoint(getVehicleTransform(1),socket.x,socket.y,socket.z));
            sightYaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            sightPitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        }
        float error=Mth.wrapDegrees(sightYaw-yaw);
        float excess=error-Mth.clamp(error,-60,60);
        if (Math.abs(manualTurn)<.01F) yaw+=Mth.clamp(excess,-2.1F,2.1F);
        float relative=Mth.clamp(Mth.wrapDegrees(sightYaw-yaw),-60,60);
        float newYaw=Mth.approachDegrees(getTurretYRot(),relative,2.8F);
        float floor=gunPitchFloor(newYaw);
        // Lower the gun before traversing past the airframe. Elevation always moves at its normal speed.
        float desired=Mth.clamp(sightPitch,Math.max(floor,gunPitchFloor(getTurretYRot())),90);
        float pitch=Mth.approach(getTurretXRot(),desired,1.6F);
        if (pitch>=floor) setTurretYRot(newYaw);
        setTurretXRot(pitch);
        entityData.set(CHIN_YAW,getTurretYRot());
        entityData.set(CHIN_PITCH,pitch);
        return yaw;
    }

    private Vec3 sightPoint;
    private UUID sightOwner;
    private int sightTick;

    void acceptSight(ServerPlayer pilot,Vec3 point) {
        if (!hoverMode() || !usingChinGun(pilot) || !this.pilot(pilot) || !(distanceToSqr(point)<=560*560)) return;
        sightPoint=point;
        sightOwner=pilot.getUUID();
        sightTick=tickCount;
    }

    private static float gunPitchFloor(float yaw) {
        float side=Math.abs(yaw);
        if (side<=30) return 0;
        if (side<=40) return 9;
        if (side<=50) return 32;
        if (side<=55) return 33;
        return 38;
    }

    boolean toggleFlightMode(ServerPlayer player) {
        if (carrier() || !pilot(player) || attached) {
            return false;
        }
        if (hoverFlight == null) {
            setHoverMode(new CubeHoverFlight(getDeltaMovement()));
            takeoffTicks = 0;
            resumeHeldMovement();
            mouseInput(0, 0);
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            player.displayClientMessage(Component.translatable("message.dnvcarriercube.hover_mode"), true);
            return true;
        }

        Vec3 heading = CubeHoverFlight.heading(getYRot());
        Vec3 exit = CubeGeometry.departureVelocity(heading).scale(CubeGeometry.FLIGHT_SPEED / CubeGeometry.TAKEOFF_SPEED);
        if (!clearSweep(this, position(), position().add(exit.scale(8)))) {
            player.displayClientMessage(Component.translatable("message.dnvcarriercube.jet_blocked"), true);
            return false;
        }
        beginJetFlight(exit, 0);
        player.displayClientMessage(Component.translatable("message.dnvcarriercube.jet_mode"), true);
        return true;
    }

    private void tickHoverFlight() {
        // SBW derives jet pitch from native throttle. Keep the same loop running through the mode change.
        setPower(Mth.approach(getPower(), .25F, .10F));
        if (!(getFirstPassenger() instanceof ServerPlayer pilot) || !pilot(pilot)) {
            hoverFlight.input((short) 0);
        }
        float yaw = aimHover(getYRot(), getFirstPassenger() instanceof ServerPlayer p && pilot(p) ? p : null);
        Vec3 next = position().add(hoverFlight.step(yaw));
        // The deck capture envelope extends below the wheels. Use the real hull for free flight.
        Vec3 step=next.subtract(position());
        var swept=getBoundingBox().expandTowards(step).inflate(.02);
        if (!next.equals(position()) && !clear(swept,this,obstacle -> touchesAircraft(obstacle,step))) {
            hoverFlight.stop();
            next = position();
        }
        positionFixture(next, yaw, Mth.approachDegrees(getXRot(), 0, 5),
                Mth.approachDegrees(getRoll(), 0, 5));
    }

    @Override public Vec3 vCollide(Vec3 motion) {
        Vec3 belly=super.vCollide(motion);
        if(carrier() || !simulatesMotion() || belly.lengthSqr()==0)return belly;
        var area=getCombinedAABB().expandTowards(belly);
        if(area.minY>=level().getMaxBuildHeight() || level() instanceof ServerLevel world && airSections(world,area))return belly;
        var obstacles=new java.util.ArrayList<AABB>();
        for(var shape:level().getBlockCollisions(this,area))obstacles.addAll(shape.toAabbs());
        if(obstacles.isEmpty())return belly;
        Vec3 resolved=CubeAirframeCollision.resolve(getOBBs(),obstacles,belly);
        if(!resolved.equals(belly))takeoffTicks=0;
        // SBW compares this displacement with its input to classify ground, damage and braking.
        return resolved;
    }

    boolean touchesAircraft(AABB obstacle,Vec3 step) {
        for(var box:getOBBs()){
            if(CubeAirframeCollision.contact(box,step,obstacle)<1)return true;
        }
        return false;
    }

    private void beginJetFlight(Vec3 exit, int boostTicks) {
        positionFixture(position(), getYRot());
        setHoverMode(null);
        setAttached(false);
        if (bridgeView().getBoolean(PILOT_EXIT)) bridgeTag(t -> t.remove(PILOT_EXIT));
        resumeHeldMovement();
        mouseInput(0, 0);
        setDeltaRot(0);
        setTargetSpeed(0);
        setLiftSpeed(0);
        setEngineStartOver(true);
        setPower(boostTicks > 0 ? 1F : TAKEOFF_POWER);
        setNoGravity(false);
        takeoffTicks = boostTicks;
        if (boostTicks > 0) {
            setOnGround(false);
            setXRot((float)-Math.toDegrees(Math.atan2(exit.y, exit.horizontalDistance())));
        }
        setDeltaMovement(exit);
    }

    boolean toggleHover(ServerPlayer player) {
        return phase == Phase.CRUISE || phase == Phase.STATION_KEEP ? requestHover(player) : resume(player);
    }

    boolean requestHover(ServerPlayer player) {
        if (!pilot(player)) return deckRejected(player, "helm");
        if (isWreck() || getHealth() <= 0) return deckRejected(player, "wreck");
        if (navigation != null) return deckRejected(player, "navigation");
        if (phase == Phase.ALIGNING) return deckRejected(player, "aligning");
        if (phase != Phase.CRUISE && phase != Phase.STATION_KEEP) return deckRejected(player, "operation");
        Vec3 anchor = new Vec3(getX(), CubeGeometry.alignedHullY(getY()), getZ());
        AABB sweep = CubeGeometry.hullEnvelope(position()).minmax(CubeGeometry.hullEnvelope(anchor));
        Component obstruction = clearanceProblem(sweep, null, sweep::intersects);
        if (obstruction != null) {
            player.sendSystemMessage(Component.translatable("message.dnvcarriercube.deck.blocked", obstruction));
            return false;
        }
        hover = anchor;
        hoverYaw = CubeGeometry.cardinal(getYRot());
        phase = Phase.ALIGNING;
        applyPilotInput((short) 0);
        setPower(0);
        setTargetSpeed(0);
        setLiftSpeed(0);
        setDeltaRot(0);
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private void align() {
        Vec3 next = position().add(0, Mth.clamp(hover.y - getY(), -.12, .12), 0);
        AABB sweep = CubeGeometry.hullEnvelope(position()).minmax(CubeGeometry.hullEnvelope(next));
        Component obstruction = clearanceProblem(sweep, null, sweep::intersects);
        if (obstruction != null) {
            phase = Phase.CRUISE;
            hover = null;
            message(Component.translatable("message.dnvcarriercube.deck.interrupted", obstruction));
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), hoverYaw, 1.35F);
        if (next.equals(hover) && Math.abs(Mth.wrapDegrees(yaw - hoverYaw)) < .001) {
            hold();
            phase = Phase.READY;
            message(Component.translatable("message.dnvcarriercube.deck.locked", Mth.floor(hover.y + CubeGeometry.DECK)));
        } else {
            positionFixture(next, yaw);
        }
    }

    private void hold() {
        positionFixture(hover, hoverYaw);
    }

    private boolean deckRejected(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.translatable("message.dnvcarriercube.deck." + reason));
        return false;
    }

    boolean resume(ServerPlayer player) {
        if (!pilot(player)) return deckRejected(player, "helm");
        if (isWreck() || getHealth() <= 0) return deckRejected(player, "wreck");
        if (phase == Phase.ALIGNING) return deckRejected(player, "aligning");
        if (phase != Phase.READY) return deckRejected(player, "operation");
        if (deckAircraft != null) return deckRejected(player, "occupied");
        phase = Phase.CRUISE;
        hover = null;
        applyPilotInput((short) 0);
        return true;
    }

    boolean boardAircraft(ServerPlayer player) {
        if(deckAircraft!=null) return boardAircraft(player,hangar().source(deckAircraft));
        for(var bay:CubeHangar.Bay.values()) if((hangar().eligibleMask() & (1<<bay.ordinal()))!=0) return boardAircraft(player,bay);
        return false;
    }

    boolean boardAircraft(ServerPlayer player,CubeHangar.Bay bay) {
        if (phase != Phase.READY || player.getVehicle() != this || getSeatIndex(player) != 1
                || !player.isAlive() || player.isSpectator() || !(level() instanceof ServerLevel world)) {
            return false;
        }
        if (deckAircraft != null) {
            CubeVehicle plane = lookup(deckAircraft);
            if (bay!=hangar().source(deckAircraft) || railTick!=0 || plane==null || !plane.attached
                    || !plane.getPassengers().isEmpty() || !transfer(player,plane,0)) return false;
            if (seatAvailable(0)) soloOperator=player.getUUID();
            crewReturned=false;
            bridgeTag(t->t.remove("requested"));
            plane.bridgeTag(t->t.remove(PILOT_EXIT));
            reportDeck();
            return true;
        }
        if (hangar().freeSlot()<0 || !CubeHangar.allowed(hangar().box(bay))) return false;
        var box = BlockItem.getBlockEntityData(hangar().box(bay));
        Vec3 parked=CubeDeckStow.planePosition(hover,runway(),CubeDeckStow.TOTAL);
        if (box == null || !clear(CubeGeometry.aircraftEnvelope(parked), null)) {
            return false;
        }
        CubeVehicle plane = CubePrototype.AIRCRAFT.get().create(world);
        if (plane == null) {
            return false;
        }
        plane.setAttached(true);
        plane.setHome(getUUID());
        plane.prepareFixture(parked, hoverYaw);
        if (box.contains("Entity", Tag.TAG_COMPOUND)) {
            plane.load(box.getCompound("Entity"));
            plane.setHome(getUUID());
            plane.setAttached(true);
            plane.setHoverMode(null);
            plane.bridgeTag(t -> t.remove(PILOT_EXIT));
            plane.positionFixture(parked, hoverYaw);
        }
        UUID identity = plane.getUUID();
        if (!hangar.reserve(bay, identity)) {
            return false;
        }
        if (!world.addFreshEntity(plane) || !transfer(player, plane, 0)) {
            plane.discard();
            hangar.cancel(bay, identity);
            return false;
        }
        if (seatAvailable(0)) soloOperator=player.getUUID();
        hangar.checkout(bay, identity);
        deckAircraft = identity;
        railTick = 0;
        liftTick=CubeDeckStow.TOTAL;
        entityData.set(LIFT_RIDER,plane.getId());
        entityData.set(LIFT_FRAME,liftTick);
        autoCancelled=false;
        phase=Phase.RAISING;
        return true;
    }

    void prepareFixture(Vec3 point, float yaw) {
        setNoGravity(true);
        positionFixture(point, yaw);
        setEnergy(getMaxEnergy());
        setEngineStartOver(true);
    }

    boolean launch(ServerPlayer player) {return pilot(player) && beginLaunch();}

    boolean pilotLaunch(ServerPlayer pilot) {
        var ship=lookup(homeCarrier);
        return pilot(pilot) && ship!=null && getUUID().equals(ship.deckAircraft) && pilot.getUUID().equals(ship.soloOperator) && ship.beginLaunch();
    }

    private boolean beginLaunch() {
        CubeVehicle plane = lookup(deckAircraft);
        if (phase != Phase.READY || plane == null || !plane.attached || !operatorPresent(plane)
                || !(plane.getFirstPassenger() instanceof ServerPlayer rider)
                || !rider.isAlive() || rider.isSpectator()) {
            return false;
        }
        Vec3 bow = CubeGeometry.rail(hover, runway(), CubeGeometry.LAUNCH_TICKS);
        Vec3 exit = bow.add(CubeGeometry.departureVelocity(runway()));
        if (!clearSweep(plane, plane.position(), exit)) {
            message("弹射通道受阻，请清空跑道");
            plane.message("弹射通道受阻，请清空跑道");
            return false;
        }
        countdown=200;
        plane.setDeckPower(0);
        plane.bridgeTag(t->t.putInt("countdown",10));
        phase=Phase.COUNTDOWN;
        return true;
    }

    boolean setAutoLaunch(ServerPlayer p,boolean enabled) {
        if (!carrier() || !pilot(p)) return false;
        autoLaunch=enabled;
        autoCancelled=false;
        if (enabled && phase==Phase.READY && deckAircraft!=null && !crewReturned) launch(p);
        reportDeck();
        return true;
    }
    boolean returnKey(ServerPlayer p) {
        CubeVehicle ship=lookup(homeCarrier);
        if (pilot(p) && ship!=null && getUUID().equals(ship.deckAircraft) && ship.phase==Phase.LAUNCH) {
            ship.stopLaunch("飞行员已取消弹射，正在退轨");return true;
        }
        if (pilot(p) && ship!=null && getUUID().equals(ship.deckAircraft) && ship.phase==Phase.COUNTDOWN) {
            ship.phase=Phase.READY;ship.countdown=0;ship.autoCancelled=true;
            bridgeTag(t->t.remove("countdown"));ship.reportDeck();
            return true;
        }
        if (pilot(p) && ship != null && getUUID().equals(ship.deckAircraft) && ship.phase == Phase.CAPTURE
                && ship.recoveryTick < ship.recovery.approachTicks()) {
            ship.abortApproach(this,"已退出接引，战机保持悬停");
            return true;
        }
        return attached?returnToCarrier(p):requestCapture(p);
    }
    private void countdownTick() {
        hold();
        CubeVehicle plane=lookup(deckAircraft);
        if (plane==null) return;
        if (!operatorPresent(plane) || !(plane.getFirstPassenger() instanceof ServerPlayer)) {
            phase=Phase.READY;autoCancelled=true;countdown=0;
        } else if (--countdown==0) {
            Vec3 bow=CubeGeometry.rail(hover,runway(),CubeGeometry.LAUNCH_TICKS);
            if(clearSweep(plane,plane.position(),bow.add(CubeGeometry.departureVelocity(runway())))) {
                phase=Phase.LAUNCH;
                entityData.set(LIFT_RIDER,-1);
                plane.setDeckPower(1);
                plane.bridgeTag(t->t.putBoolean("rolling",true));
            }
            else { phase=Phase.READY;autoCancelled=true;message("弹射通道受阻，倒计时已取消"); }
        }
        if (phase==Phase.COUNTDOWN) plane.setDeckPower((float)CubeDeckStow.ease(1-countdown/200.0));
        else if (phase==Phase.READY) plane.setDeckPower(0);
        int seconds=(countdown+19)/20;
        if(plane.bridgeView().getInt("countdown")!=seconds)plane.bridgeTag(t->t.putInt("countdown",seconds));
    }

    private void setDeckPower(float power) {
        setEngineStart(false);
        setEngineStartOver(power>0);
        setPower(power);
    }

    private void stopLaunch(String reason) {
        autoCancelled=true;
        phase=Phase.RETRACT;
        message(reason);
        var plane=lookup(deckAircraft);
        if(plane!=null) {
            plane.setDeckPower(0);
            plane.message(reason);
        }
        reportDeck();
    }

    private void launchTick() {
        hold();
        CubeVehicle plane = lookup(deckAircraft);
        if (!operatorPresent(plane)
                || plane == null || !(plane.getFirstPassenger() instanceof ServerPlayer rider)
                || !rider.isAlive() || rider.isSpectator()) {
            stopLaunch("弹射中断，驾驶席无人，正在退轨");
            return;
        }
        int step = railTick+1;
        Vec3 next = CubeGeometry.rail(hover, runway(), step);
        Vec3 end=step==CubeGeometry.LAUNCH_TICKS?next.add(CubeGeometry.departureVelocity(runway())):next;
        if(!clearSweep(plane,plane.position(),end)) {
            stopLaunch("弹射通道受阻，正在退轨");
            return;
        }
        railTick=step;
        plane.positionFixture(next, hoverYaw);
        if (step == CubeGeometry.LAUNCH_TICKS) {
            plane.bridgeTag(t->{t.remove("rolling");t.remove("soloReady");});
            plane.beginJetFlight(CubeGeometry.departureVelocity(runway()), CubeGeometry.TAKEOFF_BLEND_TICKS);
            deckAircraft = null;
            railTick = 0;
            phase = Phase.READY;
            message("Aircraft released to SBW flight");
        }
    }

    private String lastReturnProblem;

    String returnProblem() {
        String reason = lastReturnProblem;
        lastReturnProblem = null;
        if (reason != null) return reason;
        return attached ? "战机停稳后才能回舱，请确认母舰座位空闲" : "需要在舰载机驾驶席申请接引";
    }

    private boolean declineReturn(String reason) {
        lastReturnProblem = reason;
        return false;
    }

    boolean requestCapture(ServerPlayer player) {
        lastReturnProblem = null;
        if (!pilot(player) || attached) return false;
        CubeVehicle ship = lookup(homeCarrier);
        if (ship == null) return declineReturn(homeCarrier == null ? "战机尚未编组，请先连接母舰" : "本维度暂时无法定位已编组母舰");
        return ship.capture(this);
    }

    private boolean capture(CubeVehicle plane) {
        if (!hangar().inFlight(hangar().source(plane.getUUID()),plane.getUUID()))
            return plane.declineReturn("该战机已解除编组，请重新连接母舰");
        if (deckAircraft != null) return plane.declineReturn("甲板正在处理另一架战机，请等待跑道空闲");
        boolean soloHold = getPassengers().isEmpty() && navigation == null
                && (phase == Phase.STATION_KEEP || phase == Phase.CRUISE);
        if (phase != Phase.READY && !soloHold)
            return plane.declineReturn(navigation != null ? "母舰正在航行，请结束航行后申请接引" : "请舰桥锁定甲板后再申请接引");
        Vec3 anchor = soloHold ? position() : hover;
        float yaw = soloHold ? getYRot() : hoverYaw;
        Vec3 forward = CubeGeometry.forward(yaw);
        double distance = CubeGeometry.bowDistance(anchor,forward,plane.position());
        if (!CubeGeometry.canCapture(anchor,forward,plane.position()))
            return plane.declineReturn("距舰首 " + Math.round(distance) + " 格，进入 " + CubeGeometry.CAPTURE_RADIUS + " 格内即可接引");
        if (plane.onGround()) return plane.declineReturn("战机仍在地面，请离地后申请接引");
        Vec3 velocity = plane.hoverFlight == null ? plane.getDeltaMovement() : plane.hoverFlight.velocity();
        var start = new CubeRecovery.Pose(plane.position(), plane.getYRot(), plane.getXRot(), plane.getRoll());
        var plan = new CubeRecovery(start, anchor, yaw, velocity);
        plan.enterAtPower(plane.getPower());
        Vec3 bow = CubeGeometry.rail(anchor,forward,CubeGeometry.LAUNCH_TICKS);
        Vec3 parked = CubeGeometry.rail(anchor,forward,0);
        if (!clearSweep(plane,bow,parked)) return plane.declineReturn("舰首至停机位的跑道受阻，请清空甲板");
        if (!clearSweep(plane,start.position(),plan.pose(1).position()))
            return plane.declineReturn("战机周围有障碍或区块尚未就绪，请移到开阔处再试");
        hover = anchor;
        hoverYaw = yaw;
        if(seatAvailable(0) && seatAvailable(1))soloOperator=plane.getFirstPassenger().getUUID();
        recovery = plan;
        recoveryTick = 0;
        deckAircraft = plane.getUUID();
        plane.takeoffTicks = 0;
        plane.setHoverMode(null);
        plane.setAttached(true);
        plane.setDeckPower(plan.power(0));
        plane.applyPilotInput((short) 0);
        plane.mouseInput(0, 0);
        plane.setNoGravity(true);
        plane.setDeltaMovement(Vec3.ZERO);
        phase = Phase.CAPTURE;
        plane.message("母舰已接管，正在飞往舰首进场区并自动减速降高；着舰前再次按回航可退出");
        return true;
    }

    private void captureTick() {
        hold();
        CubeVehicle plane = lookup(deckAircraft);
        if (plane == null) {
            return;
        }
        var pose = recovery.pose(recoveryTick+1);
        if (recoveryTick < recovery.approachTicks() && (!clearSweep(plane,plane.position(),pose.position())
                || crossesHullOnApproach(plane,pose.position())
                || !(plane.getFirstPassenger() instanceof ServerPlayer))) {
            abortApproach(plane,"接引中断，战机已悬停，跑道已释放；请确认航线净空后重试");
            return;
        }
        if (recoveryTick >= recovery.approachTicks() && !clearDeckStep(plane,pose.position())) return;
        recoveryTick++;
        plane.setDeckPower(recovery.power(recoveryTick));
        plane.positionFixture(pose.position(), pose.yaw(), pose.pitch(), pose.roll());
        if (recoveryTick == recovery.duration()) {
            recovery = null;
            finishParking(plane);
        }
    }

    private boolean crossesHullOnApproach(CubeVehicle plane, Vec3 next) {
        // Touchdown and rollout intentionally rest on the bow. A crossing astern must also clear the island.
        Vec3 forward = runway();
        Vec3 bow = CubeGeometry.rail(hover, forward, CubeGeometry.LAUNCH_TICKS);
        if (next.subtract(bow).dot(forward) >= -1e-7) return false;
        Vec3 step=next.subtract(plane.position());
        if (!getBoundingBox().intersects(plane.getBoundingBox().expandTowards(step)))return false;
        for(var aircraftBox:plane.getOBBs()){
            var moved=aircraftBox.move(step);
            for(var hullBox:getOBBs())
                if(com.atsuishio.superbwarfare.tools.OBB.isColliding(moved,hullBox))return true;
        }
        return false;
    }

    private void abortApproach(CubeVehicle plane,String reason) {
        recovery = null;
        recoveryTick = 0;
        deckAircraft = null;
        phase = Phase.READY;
        plane.setAttached(false);
        plane.setHoverMode(new CubeHoverFlight(Vec3.ZERO));
        plane.setNoGravity(true);
        plane.setDeltaMovement(Vec3.ZERO);
        plane.bridgeTag(t->t.remove("guidance"));
        plane.message(reason);
        reportDeck();
    }

    private void retractTick() {
        hold();
        CubeVehicle plane = lookup(deckAircraft);
        if (plane == null) {
            return;
        }
        Vec3 parked = CubeGeometry.rail(hover, runway(), 0);
        Vec3 displacement = parked.subtract(plane.position());
        Vec3 next = displacement.lengthSqr() <= .12 * .12
                ? parked : plane.position().add(displacement.normalize().scale(.12));
        if(!clearDeckStep(plane,next))return;
        plane.positionFixture(next, hoverYaw);
        if (next.equals(parked)) {
            finishParking(plane);
        }
    }

    private boolean clearDeckStep(CubeVehicle plane,Vec3 next) {
        boolean clear=clearSweep(plane,plane.position(),next);
        if(bridgeView().getBoolean("trackBlocked")==clear){
            bridgeTag(t->t.putBoolean("trackBlocked",!clear));
            if(!clear){message("甲板通道受阻，清空后自动继续");plane.message("甲板通道受阻，清空后自动继续");}
        }
        return clear;
    }
    private void finishParking(CubeVehicle plane) {
        railTick = 0;
        phase = Phase.READY;
        plane.bridgeTag(t -> {t.remove("soloReady");t.remove("rolling");t.putBoolean(PILOT_EXIT,true);});
        message("Aircraft secured; pilot can return to carrier");
        plane.message("Aircraft secured; pilot can return to carrier");
    }

    boolean abort(ServerPlayer player) {
        if(!pilot(player))return false;
        CubeVehicle plane = lookup(deckAircraft);
        if(plane!=null && phase==Phase.COUNTDOWN){
            autoCancelled=true;phase=Phase.READY;countdown=0;reportDeck();return true;
        }
        if ((phase!=Phase.LAUNCH && !(phase==Phase.READY && railTick>0)) || plane==null || !plane.attached) {
            return false;
        }
        stopLaunch("舰桥已取消弹射，正在退轨");
        return true;
    }

    boolean returnToCarrier(ServerPlayer player) {
        CubeVehicle ship = lookup(homeCarrier);
        UUID identity = getUUID();
        var bay = ship==null?null:ship.hangar().source(identity);
        if (!pilot(player) || !attached || ship == null || ship.phase != Phase.READY
                || ship.railTick != 0 || !identity.equals(ship.deckAircraft) || !ship.hangar().inFlight(bay, identity)
                || !transfer(player, ship, ship.returnSeat(player))) {
            return false;
        }
        bridgeTag(t -> t.remove(PILOT_EXIT));
        ship.crewReturned = true;
        ship.reportDeck();return true;
    }

    private boolean canStow() {
        CubeVehicle plane=lookup(deckAircraft);
        return phase==Phase.READY && railTick==0 && plane!=null && plane.attached && plane.getPassengers().isEmpty()
                && hangar().inFlight(hangar().source(deckAircraft),deckAircraft);
    }

    boolean lowerAircraft(ServerPlayer commander) {
        return pilot(commander) && canStow() && beginStow();
    }

    private boolean beginStow() {
        liftTick = 0;
        phase = Phase.LOWERING;
        bridgeTag(t -> t.remove("requested"));
        return true;
    }

    private void raiseTick() {
        hold();
        CubeVehicle plane=lookup(deckAircraft);
        if(plane==null)return;
        int next=liftTick-1;
        Vec3 point=CubeDeckStow.planePosition(hover,runway(),next);
        if(!clearDeckStep(plane,point))return;
        plane.positionFixture(point,hoverYaw);
        entityData.set(LIFT_FRAME,next);
        liftTick=next;
        if(next==0) {phase=Phase.READY;plane.bridgeTag(t->t.putBoolean("soloReady",plane.getFirstPassenger() instanceof ServerPlayer p && p.getUUID().equals(soloOperator))); if(autoLaunch && !autoCancelled) beginLaunch();}
    }

    private void lowerTick() {
        hold();
        CubeVehicle plane = lookup(deckAircraft);
        if (plane == null || !plane.getPassengers().isEmpty()) return;
        Vec3 next = CubeDeckStow.planePosition(hover,runway(),liftTick + 1);
        if (!clearDeckStep(plane,next)) return;
        plane.positionFixture(next, hoverYaw);
        entityData.set(LIFT_FRAME, ++liftTick);
        if (liftTick != CubeDeckStow.TOTAL) return;
        var box = ContainerBlockItem.createInstance(plane);
        hangar().stow(hangar().source(plane.getUUID()), plane.getUUID(), box);
        plane.discard();
        deckAircraft = null;
        crewReturned = false;
        phase = Phase.READY;
        liftTick = 0;
        entityData.set(LIFT_FRAME, 0);
    }

    private int returnSeat(ServerPlayer player) {
        return seatAvailable(0) && (getPassengers().isEmpty() || player.getUUID().equals(soloOperator)) ? 0 : 1;
    }

    private boolean seatAvailable(int seat) {
        return seat >= 0 && seat < getMaxPassengers() && getOrderedPassengers().get(seat) == null;
    }

    private static boolean transfer(ServerPlayer player, CubeVehicle target, int seat) {
        if (!(player.getVehicle() instanceof CubeVehicle previous) || previous == target
                || previous.level() != target.level() || !target.isAlive()
                || !target.seatAvailable(seat)) {
            return false;
        }
        int originalSeat = previous.getSeatIndex(player);
        Vec3 originalPosition = player.position();
        player.stopRiding();
        if (player.isPassenger()) {
            return false;
        }
        // The native dismount starts a boarding cooldown. Only this validated transfer bypasses it.
        if (player.startRiding(target, true)) {
            if (target.getSeatIndex(player) == seat || target.changeSeat(player, seat)) {
                return player.getVehicle() == target && target.getSeatIndex(player) == seat;
            }
            player.stopRiding();
        }
        if (!player.isPassenger() && player.startRiding(previous, true)) {
            previous.changeSeat(player, originalSeat);
        } else if (!player.isPassenger()) {
            player.teleportTo(originalPosition.x, originalPosition.y, originalPosition.z);
        }
        return false;
    }

    private boolean pilot(ServerPlayer player) {
        // SBW's movement packet authorizes the ordered seat-zero rider through getFirstPassenger().
        return player.isAlive() && !player.isSpectator() && player.getVehicle() == this
                && getFirstPassenger() == player;
    }

    private void message(String message) {
        message(Component.literal(message));
    }

    private void message(Component message) {
        if (getFirstPassenger() instanceof ServerPlayer driver) driver.sendSystemMessage(message);
    }

    private boolean clearSweep(CubeVehicle aircraft, Vec3 from, Vec3 to) {
        return clear(CubeGeometry.aircraftSweep(from, to), aircraft);
    }

    boolean clear(AABB bounds, CubeVehicle aircraft) {
        return clear(bounds, aircraft, bounds::intersects);
    }

    private static boolean airSections(ServerLevel world,AABB bounds) {
        AABB scan=bounds.inflate(1);
        int minY=Math.max(world.getMinSection(), Mth.floor(scan.minY)>>4);
        int maxY=Math.min(world.getMaxSection()-1, Mth.floor(scan.maxY)>>4);
        for(int x=Mth.floor(scan.minX)>>4;x<=Mth.floor(scan.maxX)>>4;x++) {
            for(int z=Mth.floor(scan.minZ)>>4;z<=Mth.floor(scan.maxZ)>>4;z++) {
                var chunk=world.getChunkSource().getChunkNow(x,z);
                if (chunk==null) return false;
                for(int y=minY;y<=maxY;y++)
                    if(!chunk.getSection(world.getSectionIndexFromSectionY(y)).hasOnlyAir()) return false;
            }
        }
        return true;
    }

    private boolean clear(AABB bounds, CubeVehicle aircraft, Predicate<AABB> inRoute) {
        return clearanceProblem(bounds, aircraft, inRoute) == null;
    }

    private Component clearanceProblem(AABB bounds, CubeVehicle aircraft, Predicate<AABB> inRoute) {
        if (!(level() instanceof ServerLevel world)) return Component.translatable("message.dnvcarriercube.deck.operation");
        if (bounds.minY < world.getMinBuildHeight()) {
            return Component.translatable("message.dnvcarriercube.deck.height", world.getMinBuildHeight());
        }
        if (!world.getWorldBorder().isWithinBounds(bounds)) {
            return Component.translatable("message.dnvcarriercube.deck.border");
        }
        if(bounds.minY<world.getMaxBuildHeight()) {
            AABB terrain=new AABB(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,
                    Math.min(bounds.maxY,world.getMaxBuildHeight()),bounds.maxZ);
            if(!world.hasChunksAt(BlockPos.containing(terrain.minX,terrain.minY,terrain.minZ),
                    BlockPos.containing(terrain.maxX,terrain.maxY,terrain.maxZ))) {
                com.mojang.logging.LogUtils.getLogger().debug("Yue clearance awaiting chunks in {}", terrain);
                return Component.translatable("message.dnvcarriercube.deck.chunks");
            }
            if(terrain.getXsize()<32 || !airSections(world,terrain))
                for(var shape:world.getBlockCollisions(this,terrain))
                    if(shape.toAabbs().stream().anyMatch(inRoute)) {
                        var block = BlockPos.containing(shape.bounds().getCenter());
                        return Component.translatable("message.dnvcarriercube.deck.terrain",
                                block.getX(), block.getY(), block.getZ());
                    }
        }
        var obstacles = world.getEntities(this, bounds, candidate -> candidate != aircraft && candidate.isAlive()
                && candidate.getVehicle() != this
                && (aircraft == null || candidate.getVehicle() != aircraft)
                && !candidate.isSpectator() && inRoute.test(candidate.getBoundingBox()));
        if (obstacles.isEmpty()) return null;
        var first = obstacles.get(0);
        BlockPos block = first.blockPosition();
        return Component.translatable("message.dnvcarriercube.deck.entity",
                first.getDisplayName(), block.getX(), block.getY(), block.getZ());
    }
}

package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** Actual independent large projectile. Coordinate flight never depends on a loaded target entity. */
public final class NuclearMissileEntity extends Entity implements IEntityAdditionalSpawnData,
        com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider,com.atsuishio.superbwarfare.entity.OBBEntity,
        com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.combat.DnvNuclearInterceptionTarget {
    public static final float MAX_HEALTH=500F;
    private static final EntityDataAccessor<Boolean> ARMED=SynchedEntityData.defineId(NuclearMissileEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(NuclearMissileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH=SynchedEntityData.defineId(NuclearMissileEntity.class,EntityDataSerializers.FLOAT);
    private UUID actor;
    private UUID silo;
    private BlockPos target;
    private int age;
    private NuclearFlight.Stage stage=NuclearFlight.Stage.CLIMB;
    private boolean armed;
    private boolean racked;
    private int missingRackTicks;
    private boolean invalid;
    private final NuclearTickGate clock=new NuclearTickGate();
    public NuclearMissileEntity(EntityType<? extends NuclearMissileEntity> type,Level level){super(type,level);setNoGravity(true);}
    public boolean dogEgg(){return com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities.DOGEGG_MISSILE.isPresent()
            &&getType()==com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities.DOGEGG_MISSILE.get();}
    public SiloPayloadKind payloadKind(){return dogEgg()?SiloPayloadKind.DOGEGG:SiloPayloadKind.NUCLEAR;}
    @Override protected void defineSynchedData(){entityData.define(ARMED,false);entityData.define(AGE,0);entityData.define(HEALTH,MAX_HEALTH);}
    public float getHealth(){return entityData.get(HEALTH);}
    @Override public float ordinaryArmorReduction(){return com.liucodex.doggiesnewvehicle.core.DnvArmorRules.Profile.SPECIAL.reduction();}
    public void initialize(UUID launch,UUID actor,UUID silo,BlockPos target,Vec3 origin) {
        if(level().isClientSide||armed||launch==null||actor==null||silo==null||target==null)throw new IllegalStateException("Invalid missile initialization");
        if(racked&&!getUUID().equals(launch))throw new IllegalStateException("Rack identity changed");
        setUUID(launch);this.actor=actor;this.silo=silo;this.target=target.immutable();setPos(origin);setXRot(-90);
    }
    public void initializeRack(UUID id,UUID actor,UUID silo,Vec3 origin,float yaw){
        if(level().isClientSide||armed||id==null||actor==null||silo==null)throw new IllegalStateException("Invalid rack");
        setUUID(id);this.actor=actor;this.silo=silo;racked=true;setPos(origin);setYRot(yaw);setXRot(-90);
    }
    public boolean belongsToRack(UUID facility){return racked&&!armed&&!invalid&&facility.equals(silo);}
    public void arm(){
        if(!(level() instanceof ServerLevel server)||!NuclearSiloStore.get(server).committed(getUUID())
                ||NuclearSiloStore.get(server).flight(getUUID())==null
                ||NuclearSiloStore.get(server).flight(getUUID()).kind()!=payloadKind())
            throw new IllegalStateException("Launch not committed");
        racked=false;armed=true;entityData.set(ARMED,true);
    }
    @Override public Entity changeDimension(ServerLevel destination){return null;}
    public boolean armed(){return level().isClientSide?entityData.get(ARMED):armed;}
    /** The existing launch actor owns affiliation; no cached team or second projectile identity. */
    public Entity interceptionOwner(){
        if(actor==null)return null;
        return level() instanceof ServerLevel server?server.getEntity(actor):level().getPlayerByUUID(actor);
    }
    @Override public java.util.List<net.minecraft.world.phys.AABB> dnv$collisionBoxes(){
        return dogEgg()?NuclearCollision.dogEgg(getYRot(),getXRot(),position()):NuclearCollision.missile(getYRot(),getXRot(),position());
    }
    @Override public java.util.List<com.atsuishio.superbwarfare.tools.OBB> getOBBs(){
        return dnv$collisionBoxes().stream().map(b->new com.atsuishio.superbwarfare.tools.OBB(
                new org.joml.Vector3d(b.getCenter().x,b.getCenter().y,b.getCenter().z),
                new org.joml.Vector3d(b.getXsize()/2,b.getYsize()/2,b.getZsize()/2),new org.joml.Quaterniond(),com.atsuishio.superbwarfare.tools.OBB.Part.EMPTY)).toList();
    }
    @Override public boolean enableAABB(){return false;}
    @Override public boolean isInObb(BlockPos position,Vec3 offset){
        return dnv$collisionBoxes().stream().anyMatch(b->b.move(offset).intersects(new net.minecraft.world.phys.AABB(position)));
    }
    @Override public boolean isInObb(Entity entity,Vec3 offset){return dnv$collisionBoxes().stream().anyMatch(b->b.move(offset).intersects(entity.getBoundingBox()));}
    @Override public boolean isPickable(){return armed()&&!isRemoved();}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        if(level().isClientSide||!armed||invalid||isRemoved()||!Float.isFinite(amount)||amount<=0||isInvulnerableTo(source))return false;
        boolean laser=source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.LASER)
                ||source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.LASER_STATIC)
                ||source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.LASER_HEADSHOT);
        boolean physical=!laser&&com.liucodex.doggiesnewvehicle.core.DnvArmorRules.ordinaryPhysical(source);
        if(!physical&&!laser
                &&!source.is(com.liucodex.doggiesnewvehicle.core.DnvArmorDamageTags.SPECIAL))return false;
        float damage=physical?com.liucodex.doggiesnewvehicle.core.DnvArmorRules.apply(ordinaryArmorReduction(),amount):amount;
        damage=com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.apply(source,amount,damage);
        if(!Float.isFinite(damage)||damage<=0)return false;
        float remaining=Math.max(0,getHealth()-damage);entityData.set(HEALTH,remaining);
        // Only lethal interception removes the committed projectile. No refund or airburst payload.
        if(remaining<=0)discard();return true;
    }
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox(){return NuclearCollision.envelope(dnv$collisionBoxes());}
    public int flightAge(){return level().isClientSide?entityData.get(AGE):age;}
    public long lastServerTick(){return clock.last();}
    @Override public boolean isAlwaysTicking(){return !level().isClientSide;}
    @Override public void onAddedToWorld(){super.onAddedToWorld();if(level() instanceof ServerLevel server)NuclearRuntimeEvents.track(server,this);}
    @Override public void tick(){
        if(level().isClientSide){super.tick();return;}
        if(!(level() instanceof ServerLevel server)||!clock.claim(server.getGameTime()))return;
        super.tick();
        if(invalid){discard();return;}
        if(racked&&!armed){
            Entity owner=server.getEntity(silo);
            if(owner instanceof NuclearSiloEntity facility&&facility.recoverCommittedRack(this))racked=false;
            if(racked){
            if(owner instanceof NuclearSiloEntity facility&&facility.ownsRack(getUUID()))missingRackTicks=0;
            else if(++missingRackTicks>40)discard();
            setDeltaMovement(Vec3.ZERO);return;
            }
        }
        NuclearSiloStore store=NuclearSiloStore.get(server);var f=store.flight(getUUID());
        if(invalid||!armed||target==null||f==null||!f.committed()||f.kind()!=payloadKind()||age>=NuclearRules.DEFAULT.lifeTicks()) { discard();return; }
        Vec3 from=position();stage=NuclearFlight.stage(point(from),point(target.getCenter()),stage,NuclearRules.DEFAULT);
        var velocity=NuclearFlight.velocity(point(from),point(target.getCenter()),age,stage,NuclearRules.DEFAULT);
        Vec3 motion=new Vec3(velocity.x(),velocity.y(),velocity.z());
        SiloChunkTickets.update(server,getUUID(),NuclearFlight.chunks(point(from),velocity));
        Vec3 to=from.add(motion);setDeltaMovement(motion);
        if(age>=NuclearRules.DEFAULT.exitTicks()) {
            double horizontal=Math.hypot(motion.x,motion.z);
            if(motion.lengthSqr()>1e-8){setYRot(NuclearFlight.yaw(velocity,getYRot()));setXRot((float)-Math.toDegrees(Math.atan2(motion.y,horizontal)));}
            var collision=NuclearMissileCollision.sweep(server,this,dnv$collisionBoxes(),motion,silo);
            if(!collision.loaded()){discard();return;}
            if(Double.isFinite(collision.fraction())){land(server,collision.contact());return;}
            if(stage==NuclearFlight.Stage.DIVE&&from.distanceTo(target.getCenter())<=motion.length()+.5){land(server,target.getCenter());return;}
        }
        if(!server.getWorldBorder().isWithinBounds(BlockPos.containing(to))) {discard();return;}
        setPos(to);age++;entityData.set(AGE,age);store.observe(getUUID(),blockPosition());
    }
    private void land(ServerLevel level,Vec3 position){
        NuclearSiloStore store=NuclearSiloStore.get(level);
        // Every release checkpoint still needs the flight as its valid ticket owner.
        SiloChunkTickets.release(level,getUUID());
        store.impact(getUUID(),BlockPos.containing(position));
        discard();
    }
    @Override public void remove(RemovalReason reason){
        releaseFlight(reason);
        super.remove(reason);
    }
    @Override public void onRemovedFromWorld(){
        releaseFlight(getRemovalReason());
        super.onRemovedFromWorld();
    }
    private void releaseFlight(RemovalReason reason){
        if(level() instanceof ServerLevel server){NuclearRuntimeEvents.untrack(server,getUUID());
            if(reason!=null&&reason.shouldDestroy()){SiloChunkTickets.release(server,getUUID());NuclearSiloStore.get(server).forgetFlight(getUUID());}}
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putInt("NuclearSchema",2);tag.putFloat("Health",getHealth());tag.putBoolean("Armed",armed);tag.putBoolean("Racked",racked);tag.putInt("FlightAge",age);
        tag.putString("Stage",stage.name());
        if(actor!=null)tag.putUUID("Actor",actor);if(silo!=null)tag.putUUID("Silo",silo);if(target!=null)tag.putLong("Target",target.asLong());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        int schema=tag.getInt("NuclearSchema");
        invalid=(schema!=1&&schema!=2)||!tag.hasUUID("Actor")||!tag.hasUUID("Silo")
                ||(!tag.getBoolean("Racked")&&!tag.contains("Target",net.minecraft.nbt.Tag.TAG_LONG))||!tag.contains("FlightAge",net.minecraft.nbt.Tag.TAG_INT);
        if(invalid)return;
        float health=schema==1?MAX_HEALTH:tag.getFloat("Health");
        if((schema==2&&!tag.contains("Health",net.minecraft.nbt.Tag.TAG_FLOAT))||!Float.isFinite(health)||health<=0||health>MAX_HEALTH){invalid=true;return;}
        entityData.set(HEALTH,health);
        actor=tag.getUUID("Actor");silo=tag.getUUID("Silo");target=tag.contains("Target")?BlockPos.of(tag.getLong("Target")):null;
        age=tag.getInt("FlightAge");armed=tag.getBoolean("Armed");racked=tag.getBoolean("Racked");
        try{stage=tag.contains("Stage")?NuclearFlight.Stage.valueOf(tag.getString("Stage")):NuclearFlight.Stage.CLIMB;}
        catch(IllegalArgumentException bad){invalid=true;return;}
        invalid=age<0||age>NuclearRules.DEFAULT.lifeTicks()||racked&&armed;entityData.set(ARMED,armed);entityData.set(AGE,age);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void writeSpawnData(FriendlyByteBuf buffer){buffer.writeInt(age);buffer.writeBoolean(armed);}
    @Override public void readSpawnData(FriendlyByteBuf buffer){entityData.set(AGE,buffer.readInt());entityData.set(ARMED,buffer.readBoolean());}
    private static NuclearFlight.Point point(Vec3 p){return new NuclearFlight.Point(p.x,p.y,p.z);}
}

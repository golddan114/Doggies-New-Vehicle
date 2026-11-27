package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Executes the compiled entity orchestration and flight methods against a deterministic loaded-world port.
 * It does not boot Minecraft or substitute for Forge/OBB/packet acceptance. */
public final class MothershipEntityExecutionFixture {
    private static final String SOURCE="com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity";
    private static final String SUBJECT="com/liucodex/doggiesnewvehicle/boss/ExecutedMothership";
    private static Class<?> compiled;
    public static Base create() throws Exception {
        if(compiled==null)compiled=compile();
        return (Base)compiled.getConstructor().newInstance();
    }
    private static Class<?> compile() throws Exception {
        ClassNode node=new ClassNode();
        try(var input=MothershipEntityExecutionFixture.class.getClassLoader().getResourceAsStream(SOURCE+".class")) {
            if(input==null)throw new IllegalStateException("Compile the production entity first");new ClassReader(input).accept(node,0);
        }
        Set<String> keep=Set.of("tickDoomsday","blast","cancelDoom","abortDoom","releaseGlow","tickDrops","moveShip",
                "refreshCandidates","candidatePriority","legalTarget","stationTargetAllowed","stationMuzzle","stationAimPoint","updateIdle","tickStations","stationEligible","clearCombat","addAdditionalSaveData","readAdditionalSaveData",
                "syncFx","renderBeamEnd","renderGroundPoint","renderBeamOffset","renderShotStarted","renderAbortStarted","renderAbortElapsed","putVector","readVector","returnPoint","resolveTarget","tickDropCharge","updateBossBar",
                "tickEntry","entryActive","setBeaconAnchor","renderBeaconAnchor","dnvShowVehicleMarker",
                "beginCrash","tickCrash","setPos","hurt","dnvWreckReadyForCleanup","dnvWreckLifetimeTicks","onRemovedFromWorld","releaseExternalState");
        node.methods.removeIf(method->!keep.contains(method.name)&&keep.stream().noneMatch(name->method.name.startsWith("lambda$"+name+"$")));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        node.superName=Type.getInternalName(Base.class);node.access=Opcodes.ACC_PUBLIC;
        for(var method:node.methods)method.access=(method.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
        MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));constructor.maxStack=1;constructor.maxLocals=1;node.methods.add(constructor);
        Map<String,String> mapping=new HashMap<>();mapping.put(SOURCE,SUBJECT);
        String defense="com/liucodex/doggiesnewvehicle/core/MothershipDefense";
        mapping.put(defense,SUBJECT+"Defense");
        String starHits="com/liucodex/doggiesnewvehicle/core/DnvStarProjectileHits";
        mapping.put(starHits,SUBJECT+"StarHits");
        mapping.put("com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity",Type.getInternalName(Bullet.class));
        mapping.put("net/minecraft/world/entity/EntityType",Type.getInternalName(Kind.class));
        mapping.put("net/minecraft/core/registries/BuiltInRegistries",Type.getInternalName(Registries.class));
        mapping.put("net/minecraft/core/DefaultedRegistry",Type.getInternalName(Registry.class));
        mapping.put("net/minecraft/core/Registry",Type.getInternalName(Registry.class));
        mapping.put("net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class));
        mapping.put("net/minecraft/world/damagesource/DamageSource",Type.getInternalName(Damage.class));
        mapping.put("net/minecraft/resources/ResourceKey",Type.getInternalName(Key.class));
        mapping.put("net/minecraft/tags/TagKey",Type.getInternalName(Tag.class));
        mapping.put("net/minecraft/tags/DamageTypeTags",Type.getInternalName(DamageTags.class));
        mapping.put("com/atsuishio/superbwarfare/entity/vehicle/AnnihilatorEntity",Type.getInternalName(Annihilator.class));
        mapping.put("com/atsuishio/superbwarfare/init/ModDamageTypes",Type.getInternalName(DamageKeys.class));
        mapping.put("com/liucodex/doggiesnewvehicle/combat/DnvDamageTypes",Type.getInternalName(DamageKeys.class));
        mapping.put("com/liucodex/doggiesnewvehicle/core/DnvArmorRules",Type.getInternalName(CombatRules.class));
        mapping.put("com/liucodex/doggiesnewvehicle/core/DnvArmorRules$Profile",Type.getInternalName(ArmorProfile.class));
        mapping.put("com/liucodex/doggiesnewvehicle/core/DnvTaggedProjectileArmor",Type.getInternalName(TaggedArmor.class));
        mapping.put("com/liucodex/doggiesnewvehicle/core/DnvPercentHits",Type.getInternalName(Percent.class));
        mapping.put("net/minecraft/world/entity/LivingEntity",Type.getInternalName(Actor.class));
        mapping.put("net/minecraft/world/effect/MobEffect",Type.getInternalName(EffectType.class));
        mapping.put("net/minecraft/world/effect/MobEffectInstance",Type.getInternalName(Effect.class));
        mapping.put("net/minecraft/world/effect/MobEffects",Type.getInternalName(Effects.class));
        mapping.put("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(Base.class));
        mapping.put("net/minecraft/server/level/ServerBossEvent",Type.getInternalName(BossBar.class));
        mapping.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class));
        mapping.put("net/minecraft/server/level/ServerPlayer",Type.getInternalName(Actor.class));
        mapping.put("net/minecraft/server/MinecraftServer",Type.getInternalName(Server.class));
        mapping.put("net/minecraft/server/players/PlayerList",Type.getInternalName(Players.class));
        mapping.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
        mapping.put("net/minecraft/world/scores/Team",Type.getInternalName(Team.class));
        mapping.put("net/minecraft/world/scores/PlayerTeam",Type.getInternalName(Team.class));
        mapping.put("net/minecraft/sounds/SoundSource",Type.getInternalName(Channel.class));
        mapping.put("net/minecraft/sounds/SoundEvent",Type.getInternalName(Sound.class));
        mapping.put("net/minecraft/sounds/SoundEvents",Type.getInternalName(VanillaSounds.class));
        mapping.put("net/minecraft/world/entity/player/Player",Type.getInternalName(Actor.class));
        mapping.put("net/minecraftforge/registries/RegistryObject",Type.getInternalName(Holder.class));
        mapping.put("net/minecraft/network/syncher/EntityDataAccessor",Type.getInternalName(Accessor.class));
        mapping.put("net/minecraft/network/syncher/SynchedEntityData",Type.getInternalName(Data.class));
        mapping.put("com/liucodex/noise/entity/NoiseVehicleEntity",Type.getInternalName(Noise.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache",Type.getInternalName(Candidates.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetSight",Type.getInternalName(Sight.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvBossTeamMembership",Type.getInternalName(Membership.class));
        mapping.put("com/liucodex/doggiesnewvehicle/boss/MothershipPresence",Type.getInternalName(Presence.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetExclusions",Type.getInternalName(Policy.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime",Type.getInternalName(Policy.class));
        mapping.put("com/liucodex/doggiesnewvehicle/compat/DnvWreckLifetime",Type.getInternalName(Expiry.class));
        mapping.put("com/liucodex/doggiesnewvehicle/boss/MothershipTerrainBlast",Type.getInternalName(Terrain.class));
        for(var entry:Map.of("MothershipWorld",Queries.class,"MothershipWorld$Trace",Trace.class,"MothershipWeapons",Weapons.class,
                "MothershipSounds",Sounds.class,"MothershipGlow",Glow.class).entrySet())mapping.put("com/liucodex/doggiesnewvehicle/boss/"+entry.getKey(),Type.getInternalName(entry.getValue()));
        ClassWriter output=new ClassWriter(0);node.accept(new ClassRemapper(output,new SimpleRemapper(mapping)));
        byte[] bytes=output.toByteArray();
        ClassWriter defenseOutput=new ClassWriter(0);
        try(var input=MothershipEntityExecutionFixture.class.getClassLoader().getResourceAsStream(defense+".class")) {
            new ClassReader(input).accept(new ClassRemapper(defenseOutput,new SimpleRemapper(mapping)),0);
        }
        ClassWriter starOutput=new ClassWriter(0);
        try(var input=MothershipEntityExecutionFixture.class.getClassLoader().getResourceAsStream(starHits+".class")) {
            new ClassReader(input).accept(new ClassRemapper(starOutput,new SimpleRemapper(mapping)),0);
        }
        return new ClassLoader(MothershipEntityExecutionFixture.class.getClassLoader()) {
            Class<?> define(){
                byte[] starBytes=starOutput.toByteArray();
                defineClass((SUBJECT+"StarHits").replace('/','.'),starBytes,0,starBytes.length);
                byte[] defenseBytes=defenseOutput.toByteArray();
                defineClass((SUBJECT+"Defense").replace('/','.'),defenseBytes,0,defenseBytes.length);
                return defineClass(SUBJECT.replace('/','.'),bytes,0,bytes.length);
            }
        }.define();
    }
    public static void execute(Base ship,String method,Actor target,long now) throws Exception {
        ship.world.now=now;
        try {
            if(method.equals("tickDrops")||method.equals("tickStations"))compiled.getMethod(method,World.class,long.class).invoke(ship,ship.world,now);
            else compiled.getMethod(method,World.class,Actor.class,long.class).invoke(ship,ship.world,target,now);
        } catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof Error error)throw error;
            throw (Exception)failure.getCause();
        }
    }
    public static void cancel(Base ship) throws Exception {compiled.getMethod("cancelDoom").invoke(ship);}
    public static void arrival(Base ship,long now)throws Exception {ship.world.now=now;compiled.getMethod("tickDropCharge",long.class).invoke(ship,now);}
    public static boolean enter(Base ship,long now)throws Exception {
        ship.world.now=now;
        return (boolean)compiled.getMethod("tickEntry",World.class).invoke(ship,ship.world);
    }
    public static void bar(Base ship)throws Exception {compiled.getMethod("updateBossBar",World.class).invoke(ship,ship.world);}
    public static void clear(Base ship)throws Exception {compiled.getMethod("clearCombat").invoke(ship);}
    public static void removed(Base ship)throws Exception {compiled.getMethod("onRemovedFromWorld").invoke(ship);}
    public static void crash(Base ship)throws Exception {compiled.getMethod("tickCrash").invoke(ship);}
    public static Actor resolve(Base ship,long now) throws Exception {ship.world.now=now;return (Actor)compiled.getMethod("resolveTarget",World.class,long.class).invoke(ship,ship.world,now);}
    public static CompoundTag save(Base ship,long now) throws Exception {
        ship.world.now=now;CompoundTag tag=new CompoundTag();compiled.getMethod("addAdditionalSaveData",CompoundTag.class).invoke(ship,tag);return tag;
    }
    public static void restore(Base ship,CompoundTag tag,long now) throws Exception {
        ship.world.now=now;compiled.getMethod("readAdditionalSaveData",CompoundTag.class).invoke(ship,tag);
    }
    public static class Team {public final String name;public Team(String name){this.name=name;}}
    public static class Actor {
        public Kind kind=new Kind("minecraft:zombie");public Kind getType(){return kind;}
        public UUID uuid=UUID.randomUUID();public World world;public Vec3 position=new Vec3(0,64,30);
        public Actor vehicle;public Actor getVehicle(){return vehicle;}
        public boolean valid=true,visible=true,spectator,protectedPlayer,scent;public Team team;
        public boolean isSpectator(){return spectator;}
        public net.minecraft.network.chat.Component getDisplayName(){return net.minecraft.network.chat.Component.literal("SES Executor of Truth");}
        public boolean glowing,potionGlow;public final CompoundTag persistent=new CompoundTag();
        public Effect slow;public int effectApplications;
        public boolean addEffect(Effect effect,Actor source){slow=effect;effectApplications++;return true;}
        public boolean hasGlowingTag(){return glowing;}public void setGlowingTag(boolean value){glowing=value;}
        public CompoundTag getPersistentData(){return persistent;}public boolean isAlive(){return valid;}
        public net.minecraft.core.BlockPos blockPosition(){return net.minecraft.core.BlockPos.containing(position);}
        public UUID getUUID(){return uuid;}public Vec3 position(){return position;}
        public double getX(){return position.x;}public double getY(){return position.y;}public double getZ(){return position.z;}
        public double distanceToSqr(Actor target){return position.distanceToSqr(target.position);}
        public AABB getBoundingBox(){return new AABB(position,position.add(1,2,1));}
        public Team getTeam(){return team;}public World level(){return world;}
    }
    public static class Noise extends Actor { }
    public static class Annihilator extends Actor { }
    public static class Bullet extends Actor {
        public String gunId;public String getGunItemId(){return gunId;}
        public Bullet(String gun){kind=new Kind("superbwarfare:projectile");gunId=gun;}
    }
    public record Kind(net.minecraft.resources.ResourceLocation id) {
        public Kind(String id){this(new net.minecraft.resources.ResourceLocation(id));}
    }
    public interface Registry {net.minecraft.resources.ResourceLocation getKey(Object type);}
    public static class Registries {public static final Registry ENTITY_TYPE=type->((Kind)type).id();}
    public record Key(String id) { }
    public record Tag(String id) { }
    public static class DamageTags {public static final Tag IS_EXPLOSION=new Tag("explosion");}
    public static class DamageKeys {
        public static final Key LASER=new Key("laser"),CUSTOM_EXPLOSION=new Key("custom_explosion"),
                BOSS_PIERCING_LASER=new Key("boss_piercing_laser"),
                SUPER_STAR_HIT=new Key("super_star_hit"),SUPER_STAR_SLASH=new Key("super_star_slash");
    }
    public static class Damage {
        public float percent; public final java.util.Set<java.util.UUID> claims=new java.util.HashSet<>();
        public String kind="";public boolean physical;public float penetration;
        public final Actor attacker,direct;public final boolean laser,blast,bossLaser;
        public Damage(Actor attacker){this(attacker,null,false,false);}
        public Damage(Actor attacker,Actor direct,boolean laser,boolean blast){this(attacker,direct,laser,blast,false);}
        public Damage(Actor attacker,Actor direct,boolean laser,boolean blast,boolean bossLaser){
            this.attacker=attacker;this.direct=direct;this.laser=laser;this.blast=blast;this.bossLaser=bossLaser;
        }
        public Damage(Actor attacker,Actor direct,String kind){
            this(attacker,direct,false,kind.equals("projectile_explosion"));this.kind=kind;physical=true;
        }
        public Actor getEntity(){return attacker;}
        public Actor getDirectEntity(){return direct;}
        public boolean is(Key key){
            return key==DamageKeys.LASER&&laser || key==DamageKeys.CUSTOM_EXPLOSION&&blast
                    || key==DamageKeys.BOSS_PIERCING_LASER&&bossLaser || key.id().equals(kind);
        }
        public boolean is(Tag key){return blast;}
    }
    public enum ArmorProfile { SUPERHEAVY }
    public static class CombatRules {
        public static boolean ordinaryPhysical(Damage source){return (source.blast||source.physical) && !source.laser && !source.bossLaser;}
        public static float apply(ArmorProfile profile,float raw){return raw*.2F;}
        public static float apply(float reduction,float raw){return com.liucodex.doggiesnewvehicle.core.DnvArmorRules.apply(reduction,raw);}
    }
    public static class TaggedArmor {
        public static float apply(Damage source,float raw,float mitigated){
            return com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.penetrate(raw,mitigated,source.blast?0:source.penetration);
        }
    }
    /** Entity/source port; production merge math and real dedup are tested by the shared adapter fixture. */
    public static class Percent {
        public static float apply(Actor target,Damage source,float raw,float armored){
            if(source.percent<=0||!source.claims.add(target.uuid))return armored;
            return com.liucodex.doggiesnewvehicle.core.DnvPercentDamage.merge(raw,armored,8000,source.percent);
        }
    }
    public static class Base extends Actor {
        public static final String PARENT="DnvMothershipParent";
        public final MothershipRig rig=MothershipRig.get();
        public final MothershipDoomsday doomsday=new MothershipDoomsday();
        public final MothershipArmament armament=new MothershipArmament();
        public final MothershipTactics tactics=new MothershipTactics();
        public final MothershipDrops drops=new MothershipDrops();
        public final MothershipFlight flight=new MothershipFlight();
        public final MothershipCrash crashSequence=new MothershipCrash();
        public final Terrain[] terrainBlasts={new Terrain(4),new Terrain(8),new Terrain(12),new Terrain(16)};
        public final MothershipNavigation navigation=new MothershipNavigation();
        public MothershipDoomsday.Update weapon=new MothershipDoomsday.Update(MothershipDoomsday.Phase.READY,-1,0,null,0,0);
        public final Sight pursuitSight=new Sight(),dropSight=new Sight();
        public final Sight[] stationSights={new Sight(),new Sight(),new Sight(),new Sight(),new Sight(),new Sight()};
        public final Candidates candidates=new Candidates();public final Data entityData=new Data();
        public final BossBar bossEvent=new BossBar();
        public final int[] rocketTube=new int[6];
        public final MothershipStationTracking stationTracking=new MothershipStationTracking();
        public final int[] stationStalledTicks=stationTracking.stalledTicks;
        public final long[] stationOutOfRangeSince=stationTracking.outOfRangeSince;
        public final UUID[] stationTargets=stationTracking.targets;
        public final long[] nextAttempt=stationTracking.nextAttempt,stationAcquiredAt=stationTracking.acquiredAt,nextStationBalance=stationTracking.nextBalance;
        public final int[] blockedAttempts=stationTracking.blockedAttempts;
        public final net.minecraft.util.RandomSource random=net.minecraft.util.RandomSource.create(8734);
        public Vec3 idleDestination;public long nextIdle,idleDeadline;public double idleClearance=35;
        public final MothershipRig.Angles[] poses=new MothershipRig.Angles[6],previousDisplay=new MothershipRig.Angles[6],display=new MothershipRig.Angles[6];
        public static final Accessor THROTTLE=new Accessor(),FX=new Accessor(),ENTRY_AGE=new Accessor(),ENTRY_ANCHOR=new Accessor();
        public UUID doomTarget,glowingTarget,combatTarget;public long shotStarted=-1,nextNavigation,nextDrop;
        public long annihilatorBudgetTick=Long.MIN_VALUE;
        public final Map<UUID,Float> annihilatorDamageBySource=new HashMap<>();
        public boolean arrivalStarted;public int arrivalTicks,entryAge=MothershipArrival.TOTAL_TICKS;public Vec3 beaconAnchor;
        public Vec3 fixedBeamEnd,beamEnd=Vec3.ZERO,doomGround=Vec3.ZERO,course=Vec3.ZERO,home=new Vec3(0,99,0),pursuitOrigin;
        public double encounterGround=64,returnDistance=Double.POSITIVE_INFINITY;public long returnProgressAt=-1;
        public boolean approaching,retreating,returning,beamImpact;
        public int tickCount,blasts,fxUpdates,prepared;public float yaw;public Vec3 velocity=Vec3.ZERO;
        public boolean prepareAccepted=true;public final List<Boolean> around=new ArrayList<>();
        public float health=8000,pitch,roll;public int supports;public boolean onGround,removed,wreck;
        public void setWreck(boolean value){wreck=value;}public boolean isWreck(){return wreck;}
        public boolean isRemoved(){return removed;}
        public net.minecraft.world.entity.Entity.RemovalReason removalReason;
        public int nativeRemovals,presenceReleases,teamReleases;
        public net.minecraft.world.entity.Entity.RemovalReason getRemovalReason(){return removalReason;}
        public void onRemovedFromWorld(){nativeRemovals++;}
        public int nativeHurtCalls;
        private final ExactSbwDamageProbe.VehicleHealth nativeHealth=ExactSbwDamageProbe.health(com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity.MAX_HEALTH);
        public boolean m_6469_(Damage source,float amount){
            nativeHurtCalls++;nativeHealth.setHealth(health);nativeHealth.onHurt(amount,null,true);
            health=nativeHealth.getHealth();return false;
        }
        public Base(){world=new World();world.ship=this;position=new Vec3(0,99,0);team=new Team("blue");for(int i=0;i<6;i++)poses[i]=previousDisplay[i]=display[i]=rig.rest(i);}
        public float getHealth(){return health;}public void setHealth(float value){health=value;}public void setNoGravity(boolean value){}
        public void syncMount(int index){}
        public void m_7380_(CompoundTag tag){tag.putFloat("Health",health);}
        public void m_7378_(CompoundTag tag){if(tag.contains("Health"))health=tag.getFloat("Health");}
        public boolean legalTarget(Actor target,double range){return target!=null&&target.valid&&target.team!=team&&Math.hypot(target.getX()-getX(),target.getZ()-getZ())<=range;}
        public boolean doomEligible(Actor target,boolean beginning){return legalTarget(target,MothershipTactics.DOOMSDAY_RANGE)&&world.lineClear;}
        public Vec3 world(V p){return mc(rig.modelToWorld(p,v(position),yaw));}
        public V model(Vec3 p){return rig.worldToModel(v(p),v(position),yaw);}
        public float getYRot(){return yaw;}public void setYRot(float value){yaw=value;}public void setXRot(float value){pitch=value;}
        public float getXRot(){return pitch;}public float getRoll(){return roll;}public void setRoll(float value){roll=value;}
        public void setOnGround(boolean value){onGround=value;}public void updateOBB(){}public void refreshBoundingBoxFromOBBs(){}
        public void supportAfterMovement(){supports++;}
        public void setPos(double x,double y,double z){position=new Vec3(x,y,z);}
        public void setDeltaMovement(Vec3 value){velocity=value;}
        public double horizontal(Vec3 p){return Math.hypot(p.x-getX(),p.z-getZ());}
        public double distanceToSqr(Actor target){return position.distanceToSqr(target.position);}
        public void syncFx(){fxUpdates++;}
        public Noise prepareDrop(World level,Vec3 center,int bay,boolean aroundTarget) {
            prepared++;around.add(aroundTarget);if(!prepareAccepted)return null;
            Noise noise=new Noise();noise.world=level;return noise;
        }
        public static V v(Vec3 p){return new V(p.x,p.y,p.z);}public static Vec3 mc(V p){return new Vec3(p.x(),p.y(),p.z());}
    }
    public static class Accessor { }
    public static class EffectType { }
    public static class Effects {public static final EffectType MOVEMENT_SLOWDOWN=new EffectType();}
    public record Effect(EffectType type,int duration,int amplifier,boolean ambient,boolean visible,boolean icon) { }
    public static class BossBar {
        public float progress;public boolean visible;public net.minecraft.network.chat.Component name;
        public final Set<Actor> players=new HashSet<>();
        public void setProgress(float value){progress=value;}public void setName(net.minecraft.network.chat.Component value){name=value;}
        public void setVisible(boolean value){visible=value;}public void removeAllPlayers(){players.clear();}
        public Collection<Actor> getPlayers(){return players;}public void addPlayer(Actor player){players.add(player);}public void removePlayer(Actor player){players.remove(player);}
    }
    public static class Players {public final List<Actor> players=new ArrayList<>();public List<Actor> getPlayers(){return players;}}
    public static class Server {public final Players players=new Players();public Players getPlayerList(){return players;}}
    public static class Data {
        private final Map<Accessor,Object> values=new HashMap<>();
        public float throttle;public CompoundTag fx=new CompoundTag();
        public void set(Accessor key,Object value){
            values.put(key,value);
            if(key==Base.THROTTLE)throttle=((Number)value).floatValue();
            if(key==Base.FX)fx=(CompoundTag)value;
        }
        public Object get(Accessor key){return key==Base.THROTTLE?throttle:key==Base.FX?fx:values.get(key);}
    }
    public static class Terrain {
        public final int radius;
        public final List<Vec3> centers=new ArrayList<>();
        public Terrain(int radius){this.radius=radius;}
        public boolean active(){return false;}
        public Vec3 tick(Actor ship){return null;}
        public void begin(Actor ship,Vec3 center,boolean ignite){centers.add(center);}
        public CompoundTag save(){return new CompoundTag();}
        public void restore(CompoundTag tag){}
    }
    public enum Channel {HOSTILE}
    public static class Sound {public final String id;public Sound(String id){this.id=id;}}
    public static class VanillaSounds {
        public static final Sound BEACON_ACTIVATE=new Sound("beacon"),END_PORTAL_SPAWN=new Sound("warp");
    }
    public static class Holder {public final Object value;public Holder(Object value){this.value=value;}public Object get(){return value;}}
    public static class Sounds {
        public static final Holder LOCK=new Holder(new Sound("lock")),FIRE=new Holder(new Sound("fire")),
                CHARGE=new Holder(new Sound("charge")),COOL=new Holder(new Sound("cool")),ABORT=new Holder(new Sound("abort"));
        public static final float LOCK_PITCH=.82F, LOCK_VOLUME=1.6F, DOOMSDAY_VOLUME=3F;
        public static void stopDoomsday(Actor ship){ship.world.stoppedWeaponSounds++;}
    }
    public static class World {
        public final Server server=new Server();public Server getServer(){return server;}
        public int stoppedWeaponSounds;
        public Base ship;public long now;public boolean loaded=true,lineClear=true,overhead=true,accept=true,isClientSide;
        public boolean sightDeferred,obstructed,throwInsert,fireAccepted=true;public double ground=64;public int ceiling=320,secondaryShots,stationSounds,aimCalls;
        public boolean surfaceImpact=true;public Vec3 blockHit;
        public final List<Vec3> blastCenters=new ArrayList<>(),impactEvents=new ArrayList<>();
        public final List<Integer> fallingExplosions=new ArrayList<>();
        public final Map<UUID,Actor> actors=new HashMap<>();public final List<String> sounds=new ArrayList<>();
        public final List<Float> soundVolumes=new ArrayList<>();
        public final List<Noise> spawned=new ArrayList<>();public int directDamage,areaDamage;
        public Actor getEntity(UUID id){return actors.get(id);}public long getGameTime(){return now;}public int getMaxBuildHeight(){return ceiling;}
        public void playSound(Actor player,double x,double y,double z,Sound sound,Channel channel,float volume,float pitch){sounds.add(sound.id+"@"+now);soundVolumes.add(volume);}
        public boolean addFreshEntity(Actor entity){if(throwInsert)throw new IllegalStateException("ambiguous insertion");if(!accept)return false;spawned.add((Noise)entity);actors.put(entity.uuid,entity);return true;}
        public List<Actor> getEntities(Actor excluded,AABB area,Predicate<Actor> filter){
            ship.blasts++;blastCenters.add(area.getCenter());
            return actors.values().stream().filter(actor->actor!=excluded && actor.getBoundingBox().intersects(area)).filter(filter).toList();
        }
    }
    public static class Sight {
        public int clears;
        public Boolean visible(Actor source,Vec3 origin,Actor target) {
            return source.world.sightDeferred?null:target!=null&&target.visible&&source.world.loaded&&source.world.lineClear;
        }
        public Actor select(Actor source,Vec3 origin,List<Actor> candidates,Actor current,Predicate<Actor> eligible) {
            if(current!=null&&eligible.test(current)&&Boolean.TRUE.equals(visible(source,origin,current)))return current;
            return candidates.stream().filter(eligible).filter(e->Boolean.TRUE.equals(visible(source,origin,e)))
                    .min(Comparator.comparingDouble(source::distanceToSqr)).orElse(null);
        }
        public void clear(){clears++;}
    }
    public static class Candidates {
        public final List<Actor> targets=new ArrayList<>();
        public final List<Actor> scanned=new ArrayList<>();
        public int frameReads;
        public boolean refreshScoredOnStartCadence(Actor source, double range, double height, ToDoubleFunction<Actor> score) {
            if (range != MothershipTactics.DISCOVERY_RANGE || height != 80) throw new AssertionError("discovery bounds");
            targets.clear();
            targets.addAll(scanned.stream().map(candidate -> Map.entry(candidate, score.applyAsDouble(candidate)))
                    .filter(entry -> Double.isFinite(entry.getValue())).sorted(Map.Entry.comparingByValue())
                    .limit(32).map(Map.Entry::getKey).toList());
            return true;
        }
        public void clear(){targets.clear();}
        public List<Actor> candidates(Actor source){frameReads++;return List.copyOf(targets);}
        public Actor select(Actor source,Actor current,Predicate<Actor> eligible,ToDoubleFunction<Actor> distance,boolean ignored) {
            return targets.stream().filter(eligible).min(Comparator.comparingDouble(distance)).orElse(null);
        }
    }
    public static class Membership {
        public static boolean inheritIfUnassigned(Actor child,Team team){if(child.team!=null)return false;child.team=team;return true;}
        public static void releaseOnTerminalRemoval(Actor actor,net.minecraft.world.entity.Entity.RemovalReason reason){if(reason!=null&&reason.shouldDestroy())((Base)actor).teamReleases++;}
    }
    public static class Presence {
        private final World world;Presence(World world){this.world=world;}
        public static Presence get(World world){return new Presence(world);}
        public void release(UUID id){world.ship.presenceReleases++;}
        public boolean active(UUID id,net.minecraft.core.BlockPos position){return true;}
    }
    public static class Policy {
        public static boolean isUnavailableCombatTarget(Actor target){return target.spectator||target.protectedPlayer;}
        public static boolean isUnavailableLaserTarget(Actor target){return isUnavailableCombatTarget(target);}
        public static boolean isSporeScent(Actor target){return target.scent;}
        public static boolean allowsBossNativeTarget(Actor ship,Actor target,boolean ignored){return ship.team!=target.team;}
        public static boolean allowsBossLaserTarget(Actor ship,Actor target,boolean ignored){return allowsBossNativeTarget(ship,target,ignored);}
    }
    public record Trace(Vec3 end,boolean impact) { }
    public static class Queries {
        public static boolean loaded(World world,AABB box){return world.loaded;}
        public static List<AABB> wreckCandidates(Actor ship,Vec3 next,double pitch,double roll){return null;}
        public static int wreckPose(Actor ship,Vec3 bottom,double pitch,double roll,List<AABB> candidates){return wreckPose(ship,bottom,pitch,roll);}
        public static int wreckPose(Actor ship,Vec3 bottom,double pitch,double roll){return !ship.world.loaded?-1:bottom.y<=ship.world.ground?0:1;}
        public static double wreckLandingY(Actor ship){return ship.world.loaded?ship.world.ground-.02:Double.NaN;}
        public static Trace trace(Actor source,Vec3 start,Vec3 end){
            var world=source.world;
            return new Trace(!world.lineClear?start:world.now>=MothershipDoomsday.FIRST_HIT_TICK&&world.blockHit!=null?world.blockHit:end,world.surfaceImpact);
        }
        public static List<Actor> intersected(Actor source,Vec3 start,Vec3 end){return source.world.lineClear?new ArrayList<>(source.world.actors.values()):List.of();}
        public static boolean overhead(Actor source,Vec3 point,double top){return source.world.overhead;}
        public static boolean safePose(Actor source,Vec3 bottom,double yaw){return source.world.loaded&&!source.world.obstructed;}
        public static boolean safeStep(Actor source,Vec3 bottom,double yaw,boolean full){return safePose(source,bottom,yaw);}
        public static double groundBelow(World world,Vec3 bottom,double yaw){return world.loaded?world.ground:Double.NaN;}
    }
    public static class Weapons {
        public static boolean stationTargetAvailable(Actor ship, Actor target) {
            return target != null && target != ship && target.world == ship.world && target.valid
                    && !target.spectator && !target.protectedPlayer && ship.team != target.team;
        }
        public static boolean compatibleWithStation(MothershipArmament.Station station, Actor target) {
            return !target.scent || station.kind == MothershipArmament.Kind.TWIN_LASER;
        }
        public static boolean inStationRange(MothershipArmament.Station station,Vec3 muzzle,Vec3 point,double grace) {
            return MothershipWeapons.inStationRange(station,muzzle,point,grace);
        }
        public static void fallingExplosion(Base ship,int age){ship.world.fallingExplosions.add(age);}
        public static Vec3 aimPoint(MothershipArmament.Station station,Actor target,Vec3 muzzle){target.world.aimCalls++;return target.getBoundingBox().getCenter();}
        public static void crashImpact(Actor ship){ship.world.impactEvents.add(ship.position());}
        public static boolean fireSecondary(MothershipArmament.Station station,int barrel,Actor ship,Actor target,Vec3 muzzle,Vec3 aimPoint) {
            if(!ship.world.fireAccepted)return false;ship.world.secondaryShots++;return true;
        }
        public static void playStationSound(MothershipArmament.Station station,Actor ship,Vec3 muzzle) {ship.world.stationSounds++;}
        public static void playDoomsdayImpact(Actor ship,Vec3 impact,MothershipDoomsday.Hit hit) {ship.world.impactEvents.add(impact);}
        public static boolean doomsdayDamage(Actor ship,Actor target,MothershipDoomsday.Hit hit,boolean blast) {
            if(target==null||!target.valid||target.team==ship.team)return false;
            if(blast)ship.world.areaDamage+=hit.blastDamage();else ship.world.directDamage+=hit.directDamage();return true;
        }
    }
    public static class Expiry {public static void finish(Actor ship){((Base)ship).removed=true;}}
    public static class Glow {
        public static final Map<UUID,Set<UUID>> OWNERS=new HashMap<>();
        public static void acquire(Actor target,UUID ship,long now){OWNERS.computeIfAbsent(target.uuid,ignored->new HashSet<>()).add(ship);}
        public static void release(World world,UUID target,UUID ship){var owners=OWNERS.get(target);if(owners!=null){owners.remove(ship);if(owners.isEmpty())OWNERS.remove(target);}}
    }
}

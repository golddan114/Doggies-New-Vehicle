package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvGroundDrive;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled boss lifecycle and main-gun admission, with unrelated world/weapon backends as ports. */
class BossMainTargetExecutionTest {
    @Test void bothCompiledBossControllersBackAwayFromHighTargetsAndRespectHold() throws Exception {
        for (boolean heaven : new boolean[]{false,true}) {
            Base boss=create(heaven,false,true);
            boss.current.point=new Vec3(0,65,35);
            boss.updateAutonomousControls(boss.current);
            assertTrue(boss.requestedThrottle<0, "high target requires actual reverse drive");
            boss.holdPosition=true;boss.world.now++;
            boss.updateAutonomousControls(boss.current);
            assertEquals(0,boss.requestedThrottle);assertTrue(boss.driveBrake);
            boss.holdPosition=false;boss.current.point=new Vec3(0,4,35);boss.world.now++;
            boss.updateAutonomousControls(boss.current);
            assertEquals(0,boss.requestedThrottle,"level target returns to ordinary pressure band");
        }
    }
    @Test void bothBossesRequireSightForNativeAssignedAndRetainedTargets() throws Exception {
        for(boolean heaven:new boolean[]{false,true}) {
            Base boss=create(heaven,false,false,true);
            Actor hidden=new Actor(),visible=new Actor();hidden.visible=false;visible.point=new Vec3(0,4,80);
            boss.targetCandidates.live=List.of(hidden,visible);
            boss.world.actors.put(hidden.id,hidden);boss.world.actors.put(visible.id,visible);
            assertSame(visible,boss.resolveOrAcquireTarget());assertEquals(visible.id,boss.combatTargetId);
            visible.visible=false;assertNull(boss.resolveCurrentTarget());assertNull(boss.combatTargetId);
            boss.assigned=hidden;assertNull(boss.resolveOrAcquireTarget());assertNull(boss.combatTargetId);
            hidden.visible=true;assertSame(hidden,boss.resolveOrAcquireTarget());
            boss.world.deferred=true;assertNull(boss.resolveCurrentTarget());
            boss.assigned=null;assertNull(boss.resolveOrAcquireTarget());assertEquals(hidden.id,boss.combatTargetId);
            boss.world.deferred=false;hidden.allowed=false;assertNull(boss.resolveCurrentTarget());
        }
    }
    @Test void prolongedBlockedPursuitStopsAndVisibleTargetUnlocksTheCompiledControls() throws Exception {
        for(boolean heaven:new boolean[]{false,true}){
            Base boss=create(heaven,false,true);boss.current.point=new Vec3(0,0,80);
            boss.updateAutonomousControls(boss.current);assertTrue(boss.requestedThrottle>0);
            boss.blockedTicks=60;boss.motion=new Vec3(1,.2,1);boss.world.now++;
            boss.updateAutonomousControls(boss.current);
            assertTrue(boss.autonomyDecisionMovementLocked);assertEquals(0,boss.requestedThrottle);
            assertTrue(boss.driveBrake);assertEquals(Vec3.ZERO,boss.motion);
            assertEquals(0F,boss.entityData.values.get(Base.POWER));
            boss.blockedTicks=0;boss.world.now++;
            boss.updateAutonomousControls(boss.current);
            assertFalse(boss.autonomyDecisionMovementLocked);assertTrue(boss.requestedThrottle>0);
        }
    }
    @Test void obstructionReplacementReachesMovementAimAndFireWithoutOldTargetOverwrite() throws Exception {
        for(boolean heaven:new boolean[]{false,true}){
            Base boss=create(heaven,false);Actor replacement=boss.replacement;
            boss.tick();
            assertSame(replacement,boss.controlled);assertSame(replacement,boss.aimed);assertSame(replacement,boss.fired);
            assertSame(replacement,boss.current);assertEquals(1,boss.nativeTicks);
        }
    }
    @Test void switchingFromVillagerToRearVehicleRevokesSettlingAndCannotFireSidewaysOrWithStaleAim() throws Exception {
        for(boolean heaven:new boolean[]{false,true}){
            Base boss=create(heaven,true);Actor villager=boss.current,rear=boss.replacement;
            boss.mainSettledTicks=5;boss.mainAimTargetId=villager.id;boss.mainAimTick=boss.tickCount;
            rear.point=new Vec3(0,4,-40);
            assertFalse(boss.mainAimReady(rear,Vec3.ZERO,new Vec3(0,0,-1)));
            boss.tickCoreAim(rear);assertEquals(0,boss.mainSettledTicks);assertEquals(rear.id,boss.mainAimTargetId);
            assertFalse(boss.mainAimReady(rear,Vec3.ZERO,new Vec3(0,0,-1)));
            boss.yaw=180;boss.pitch=0;boss.mainSettledTicks=5;
            assertTrue(boss.mainAimReady(rear,Vec3.ZERO,new Vec3(0,0,-1)));
            assertFalse(boss.mainAimReady(villager,Vec3.ZERO,new Vec3(0,0,-1)));
            boss.tickCount++;assertFalse(boss.mainAimReady(rear,Vec3.ZERO,new Vec3(0,0,-1)));
        }
    }
    @Test void compiledFinalGatePrecedesNativeDamageOrProjectileCreationOnBothBosses() throws Exception {
        for(String name:List.of("noise/AbstractNoiseVehicleWeapons","silentheaven/AbstractSilentHeavenVehicleWeapons")){
            String[] parts=name.split("/");var node=read("com/liucodex/"+parts[0]+"/entity/"+parts[1]);
            var fire=node.methods.stream().filter(m->m.name.equals("fireMainGun")).findFirst().orElseThrow();
            int index=0,gate=-1,firstSideEffect=Integer.MAX_VALUE;
            for(var instruction:fire.instructions){
                if(instruction instanceof MethodInsnNode call){
                    if(call.name.equals("mainAimReady"))gate=index;
                    if(call.name.equals("doDamage")||call.name.equals("create")
                            || call.owner.endsWith("/DnvBossLaserHit") && call.name.equals("apply"))firstSideEffect=Math.min(firstSideEffect,index);
                }
                index++;
            }
            assertTrue(gate>=0&&gate<firstSideEffect);
        }
        assertFalse(DnvBossMainAim.aligned(0,0,new Vec3(0,0,-1),8));
        assertFalse(DnvBossMainAim.aligned(Float.NaN,0,new Vec3(0,0,1),40));
        assertTrue(DnvBossMainAim.aligned(0,0,new Vec3(.05,0,1),40));
    }
    static Base create(boolean heaven,boolean aiming)throws Exception {
        return create(heaven,aiming,false);
    }
    static Base create(boolean heaven,boolean aiming,boolean controls)throws Exception {
        return create(heaven,aiming,controls,false);
    }
    static Base create(boolean heaven,boolean aiming,boolean controls,boolean acquisition)throws Exception {
        String prefix=heaven?"SilentHeaven":"Noise",root="com/liucodex/"+(heaven?"silentheaven":"noise")+"/entity/";
        String owner=root+"Abstract"+prefix+"Vehicle"+(aiming?"Weapons":"Lifecycle"),dest="com/liucodex/doggiesnewvehicle/boss/executionprobe/MainTarget";
        ClassNode node=read(owner);node.methods.removeIf(m->!(acquisition?(Set.of("resolveOrAcquireTarget","resolveCurrentTarget").contains(m.name)||m.name.startsWith("lambda$resolveOrAcquireTarget$")):controls?m.name.equals("updateAutonomousControls"):aiming?Set.of("tickCoreAim","mainAimReady").contains(m.name):m.name.equals("tick")||m.name.startsWith("lambda$tick$")));
        node.name=dest;node.superName=Type.getInternalName(Base.class);node.access=Opcodes.ACC_PUBLIC;node.fields.clear();node.interfaces.clear();node.nestMembers=null;node.nestHostClass=null;node.innerClasses.clear();
        node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));init.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(init);
        Map<String,String> names=new HashMap<>();
        for(String part:List.of("Contract","State","Lifecycle","Navigation","Weapons"))names.put(root+"Abstract"+prefix+"Vehicle"+part,Type.getInternalName(Base.class));
        names.put(owner,dest);
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetSight",Type.getInternalName(Sight.class));
        Map.of("net/minecraft/world/entity/Entity",Actor.class,"net/minecraft/world/level/Level",World.class,
            "com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache",Cache.class,
            root+"Abstract"+prefix+"VehicleState$WeaponStation",Station.class)
            .forEach((k,v)->names.put(k,Type.getInternalName(v)));
        Map.of("net/minecraft/server/level/ServerLevel",World.class,
            "com/atsuishio/superbwarfare/entity/vehicle/VehicleEntity",Base.class,
            "com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator",Coordinator.class,
            "com/liucodex/doggiesnewvehicle/compat/DnvGroundTerrainProbe",Terrain.class,
            "net/minecraft/network/syncher/SynchedEntityData",Data.class,
            "net/minecraft/network/syncher/EntityDataAccessor",Accessor.class)
            .forEach((k,v)->names.put(k,Type.getInternalName(v)));
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
        node.accept(new ClassRemapper(out,new SimpleRemapper(names)));byte[] bytes=out.toByteArray();
        var type=new ClassLoader(BossMainTargetExecutionTest.class.getClassLoader()){Class<?> define(){return defineClass(dest.replace('/','.'),bytes,0,bytes.length);}}.define();
        return (Base)type.getConstructor().newInstance();
    }
    static ClassNode read(String name)throws Exception{var node=new ClassNode();try(var in=BossMainTargetExecutionTest.class.getClassLoader().getResourceAsStream(name+".class")){assertNotNull(in);new ClassReader(in).accept(node,ClassReader.SKIP_FRAMES);}return node;}
    public static class Actor{public boolean visible=true,allowed=true;public boolean isAlive(){return true;}public final UUID id=UUID.randomUUID();public Vec3 point=new Vec3(0,4,40);public UUID getUUID(){return id;}public Vec3 position(){return point;}public AABB getBoundingBox(){return new AABB(point,point.add(1,2,1));}}
    public static class World{public final Map<UUID,Actor> actors=new HashMap<>();public Actor getEntity(UUID id){return actors.get(id);}public boolean deferred,isClientSide;public long now=100;public long getGameTime(){return now;}}
    public static class Coordinator{
        public static Optional<Actor> assignedTarget(Actor source){return Optional.ofNullable(((Base)source).assigned);}
        public static com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior lossBehavior(Actor source){return com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior.NATIVE_FALLBACK;}
        public static boolean isActiveAssignment(Actor source,Actor target){return false;}}
    public static class Terrain implements DnvGroundDrive.Terrain{public Terrain(Actor owner){}public boolean clear(float yaw,double distance){return true;}}
    public static class Accessor{}
    public static class Data{public final Map<Accessor,Object> values=new HashMap<>();public void set(Accessor key,Object value){values.put(key,value);}}
    public static class Sight {
        public Boolean visible(Actor source,Vec3 origin,Actor target){return ((Base)source).world.deferred?null:target.visible;}
        public Actor select(Actor source,Vec3 origin,List<Actor> candidates,Actor current,java.util.function.Predicate<Actor> eligible){
            if(current!=null&&eligible.test(current)&&Boolean.TRUE.equals(visible(source,origin,current)))return current;
            return candidates.stream().filter(eligible).filter(e->Boolean.TRUE.equals(visible(source,origin,e))).findFirst().orElse(null);
        }
    }
    public static class Cache{public List<Actor> live=List.of();public void pinExternal(Actor target){}public void clearPinnedExternal(){}public boolean refreshIfDue(Actor source,double range,java.util.function.Predicate<Actor> filter){return false;}public boolean refreshedAt(int tick){return false;}public List<Actor> candidates(Actor source){return live;}}
    public static class Station{public void disableAndEmpty(){}}
    public static class Base extends Actor {
        public final Sight acquisitionSight=new Sight();public Actor assigned;
        public com.liucodex.doggiesnewvehicle.compat.DnvSightObservation mainSight,attackSight;
        public int lastAttackLineTick;
        public boolean isCheapTargetValid(Actor target){return target!=null&&target.allowed;}
        public boolean isAcquisitionTarget(Actor target){return isCheapTargetValid(target);}
        public boolean isTrackedTargetValid(Actor target){return isCheapTargetValid(target);}
        public void resetObstructionTracking(){}public void queueTargetAcquiredVoice(){}public void queueVoice(int voice){}
        public double distanceToSqr(Actor other){return other.point.lengthSqr();}

        public final DnvAutonomyCore autonomyCore=new DnvAutonomyCore(24,48);public final DnvGroundDrive groundDrive=new DnvGroundDrive();
        public final Data entityData=new Data();public static final Accessor POWER=new Accessor(),DELTA_ROT=new Accessor();
        public UUID combatTargetId,autonomyDecisionTargetId;public Optional<UUID> autonomyDecisionForcedTarget=Optional.empty();
        public boolean holdPosition,autonomyDecisionHold,autonomyDecisionMovementLocked,autonomyDecisionHasDestination,driveBrake,horizontalCollision;
        public Map<String,DnvAutonomyCore.DomainStatus> autonomyDecisionDomains=Map.of();
        public long nextAutonomyDecisionGameTime;public Vec3 patrolDestination,motion=Vec3.ZERO;public int blockedTicks;public float requestedThrottle,requestedSteering;
        public int obstructionRecoveryTicks(){return blockedTicks;}public Vec3 updatePatrolDestination(){return null;}
        public float getYRot(){return 0;}public double getX(){return 0;}public double getZ(){return 0;}public boolean onGround(){return true;}public int getId(){return 42;}
        public double horizontalDistance(Vec3 destination){return Math.hypot(destination.x,destination.z);}
        public Vec3 getDeltaMovement(){return motion;}public void setDeltaMovement(double x,double y,double z){motion=new Vec3(x,y,z);}
        public Map<String,DnvAutonomyCore.DomainStatus> coreDomains(boolean visible){return Map.of();}
        public DnvAutonomyCore.TargetObservation observeTarget(Actor target,boolean visible){return new DnvAutonomyCore.TargetObservation(target.id,target.point.x,target.point.y,target.point.z,80,0,1,true,true,visible);}
        public final World world=new World();public final Cache targetCandidates=new Cache();public final Station vlsBattery=new Station();
        public Actor current=new Actor(),replacement=new Actor(),controlled,aimed,fired;
        public boolean wreckCombatStateCleared,coreOrientationInitialized=true;public int teleportCooldownTicks,tickCount=100,nativeTicks;
        public UUID mainAimTargetId;public int mainAimTick,mainSettledTicks,idleCorePauseTicks;public float yaw,pitch,idleCoreYawOffset;public Vec3 mainAim=Vec3.ZERO;
        public World level(){return world;}public int getDestructionTicks(){return 0;}public boolean isWreck(){return false;}public float getHealth(){return 4000;}public boolean isRemoved(){return false;}public boolean isEntering(){return false;}public boolean isTransitioning(){return false;}public boolean isTeleporting(){return false;}public int getPhase(){return 0;}
        public void tick(){nativeTicks++;}public void tickDestructionSequence(){}public void clearCombatStateForWreck(){}public void clearBossBar(){}public void alignCoreToHull(){}public void tickEntrancePresentation(){}public void lockForEntrance(){}public void beginTransition(){}public void lockForTransition(){}public void tickTeleportSequence(){}public void tickTransition(){}public void updateBossBar(){}public void tickVoiceState(){}public void tickPendingStrikes(){}public void stabilizeFallingWarheads(){}
        public Actor resolveOrAcquireTarget(){return current;}public Actor resolveCurrentTarget(){return current;}public void tickCombatRecovery(Actor e){}
        public void tickObstructionRecovery(Actor e){current=replacement;}public void tickObstructionTeleport(Actor e){current=replacement;}
        public void updateAutonomousControls(Actor e){controlled=e;current=e;}
        public void tickCoreAim(Actor e){aimed=e;}public void tickMainGuns(Actor e){fired=e;}
        public void prepareSecondaryAllocation(DnvLazyCandidateFrame<Actor> frame,boolean refreshed){}public void tickAutocannons(){}public void tickAutocannons(DnvLazyCandidateFrame<Actor> frame){}public void tickMissilePods(){}public void tickMissilePods(DnvLazyCandidateFrame<Actor> frame){}public void tickVlsBattery(){}public void tickVlsBattery(DnvLazyCandidateFrame<Actor> frame){}public void tickHaloWeapon(Actor e){}public void tickDroneLauncher(Actor e){}public float getHaloAngle(){return 0;}public void setHaloAngle(float value){}
        public Vec3 mainGunMuzzle(boolean left){return new Vec3(left?-1:1,3.57,2.22);}
        public Vec3 position(){return Vec3.ZERO;}public Vec3 firingSolution(Vec3 muzzle,Actor target,double velocity,double gravity){return target.point.subtract(muzzle);}public static boolean isFinite(Vec3 v){return v!=null&&v.lengthSqr()>1E-8;}
        public float getCoreYaw(){return yaw;}public void setCoreYaw(float value){yaw=value;}public float getMainGunPitch(){return pitch;}public void setMainGunPitch(float value){pitch=value;}public boolean hasCachedMainLineOfSight(Vec3 muzzle,Actor target){return true;}public void tickIdleCoreScan(){}
        public boolean mainAimReady(Actor target,Vec3 muzzle,Vec3 direction){throw new AssertionError("production method required");}
    }
}

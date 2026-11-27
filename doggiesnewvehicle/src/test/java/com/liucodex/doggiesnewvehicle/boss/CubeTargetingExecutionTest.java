package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled CUBE lock/warning/following entry points; terrain insertion is an isolated port. */
class CubeTargetingExecutionTest {
    @Test void hiddenEnemyCannotPrepareLandingLockWarnOrDriveOrbit() throws Exception {
        Base cube=create();Actor target=new Actor();target.visible=false;cube.candidates.live=List.of(target);
        cube.world.actors.put(target.id,target);cube.tickNoiseSummon(cube.world);
        assertNull(cube.lockedTargetId);assertEquals(0,cube.prepared);assertEquals(0,cube.warningTicks);
        assertNull(cube.visibleFlightTarget(cube.world));
        target.visible=true;cube.tickNoiseSummon(cube.world);
        assertEquals(target.id,cube.lockedTargetId);assertEquals(1,cube.warningTicks);
        assertSame(target,cube.visibleFlightTarget(cube.world));
        target.visible=false;cube.tickNoiseSummon(cube.world);
        assertNull(cube.lockedTargetId);assertEquals(0,cube.warningTicks);
        assertNull(cube.visibleFlightTarget(cube.world));assertEquals(0,cube.spawnedNoiseCount);
    }
    @Test void budgetDeferralPausesWarningAndMovementWithoutTurningIntoSuccessfulSight() throws Exception {
        Base cube=create();Actor target=new Actor();cube.candidates.live=List.of(target);
        cube.world.actors.put(target.id,target);cube.tickNoiseSummon(cube.world);
        cube.world.deferred=true;cube.tickNoiseSummon(cube.world);
        assertEquals(target.id,cube.lockedTargetId);assertEquals(1,cube.warningTicks);
        assertNull(cube.visibleFlightTarget(cube.world));
        cube.world.deferred=false;cube.tickNoiseSummon(cube.world);
        assertSame(target,cube.visibleFlightTarget(cube.world));assertEquals(2,cube.warningTicks);
    }
    private static Base create() throws Exception {
        String original="com/liucodex/doggiesnewvehicle/entity/DnvHoverCubeBossEntity",dest=original+"Executed";
        ClassNode node=new ClassNode();
        try(var in=CubeTargetingExecutionTest.class.getResourceAsStream("/"+original+".class")) {
            new ClassReader(in).accept(node,ClassReader.SKIP_FRAMES);
        }
        node.methods.removeIf(m->!Set.of("tickNoiseSummon","visibleFlightTarget","clearWarning").contains(m.name)
                &&!m.name.startsWith("lambda$tickNoiseSummon$"));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;
        node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
        String parent=node.superName;
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(init);
        Map<String,String> names=new HashMap<>();names.put(original,dest);names.put(parent,Type.getInternalName(Base.class));
        Map.of("net/minecraft/world/entity/Entity",Actor.class,"net/minecraft/world/level/Level",World.class,
                "net/minecraft/server/level/ServerLevel",World.class,"net/minecraft/world/scores/Team",Team.class,
                "net/minecraft/world/scores/PlayerTeam",Team.class,"net/minecraft/server/level/ServerPlayer",Player.class,
                "com/liucodex/noise/entity/NoiseVehicleEntity",Actor.class,
                "com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache",Candidates.class,
                "com/liucodex/doggiesnewvehicle/compat/DnvTargetSight",Sight.class)
                .forEach((k,v)->names.put(k,Type.getInternalName(v)));
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}
        };
        node.accept(new ClassRemapper(out,new SimpleRemapper(names)));byte[] bytes=out.toByteArray();
        Class<?> type=new ClassLoader(CubeTargetingExecutionTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(dest.replace('/','.'),bytes,0,bytes.length);}
        }.define();
        return (Base)type.getConstructor().newInstance();
    }
    public static class Actor {
        public final UUID id=UUID.randomUUID();public boolean visible=true;
        public UUID getUUID(){return id;}public double getX(){return .5;}public double getZ(){return .5;}
        public Stream<Actor> getSelfAndPassengers(){return Stream.empty();}
    }
    public static class Player extends Actor {public void displayClientMessage(net.minecraft.network.chat.Component message,boolean overlay){}}
    public static class Team {}
    public static class World {
        public boolean deferred;public long getGameTime(){return 100;}
        public final Map<UUID,Actor> actors=new HashMap<>();public Actor getEntity(UUID id){return actors.get(id);}
        public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed){return count;}
    }
    public static class Candidates {
        public List<Actor> live=List.of();public boolean refreshIfDue(Actor source,double range,Predicate<Actor> filter){return false;}
        public List<Actor> candidates(Actor source){return live;}public void clear(){}
    }
    public static class Sight {
        public Boolean visible(Actor source,Vec3 origin,Actor target){return ((Base)source).world.deferred?null:target.visible;}
        public Actor select(Actor source,Vec3 origin,List<Actor> live,Actor current,Predicate<Actor> filter){
            return live.stream().filter(filter).filter(e->Boolean.TRUE.equals(visible(source,origin,e))).findFirst().orElse(null);
        }
        public void clear(){}
    }
    public static class Base extends Actor {
        public final World world=new World();public final Team team=new Team();
        public final Sight targetSight=new Sight();public final Candidates candidates=new Candidates(),targetCandidates=candidates;
        public UUID lockedTargetId;public BlockPos markedLandingSite;public boolean targetVisible;
        public int warningTicks,spawnedNoiseCount,prepared,tickCount;public long nextSummonGameTime;
        public World level(){return world;}public Team getTeam(){return team;}public Vec3 getEyePosition(){return Vec3.ZERO;}
        public boolean isLegalTarget(Actor target){return target!=null;}
        public BlockPos findLandingSite(World level,Actor target){prepared++;return BlockPos.ZERO;}
        public Actor prepareNoiseAt(World level,BlockPos site){return new Actor();}
        public boolean visibleLoadedTarget(World level,Actor target){return target.visible;}
        public boolean spawnNoiseAt(World level,BlockPos site){return true;}
        public void tickNoiseSummon(World level){throw new AssertionError("compiled entry point required");}
        public Actor visibleFlightTarget(World level){throw new AssertionError("compiled follow gate required");}
        public void clearWarning(){throw new AssertionError("compiled lifecycle required");}
    }
}

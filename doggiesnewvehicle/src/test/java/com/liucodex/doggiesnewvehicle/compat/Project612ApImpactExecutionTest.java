package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes exact mixed CannonShell.afterHitBlock and FastThrowable.destroyBlock, including live DNV admission. */
class Project612ApImpactExecutionTest {
    @TempDir Path temp;
    static JsonObject weapon()throws Exception{return JsonParser.parseString(Files.readString(Path.of(
            "src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("Main");}
    @Test void configuredApRemovesTheImpactBlockAndKeepsExplosionDamageAndRadiusZero()throws Exception {
        var f=new Fixture(temp);var shot=f.shot;JsonObject gun=weapon();
        assertEquals("AP",gun.get("ShellType").getAsString());assertEquals("superbwarfare:cannon_shell",gun.get("Projectile").getAsString());
        shot.explosionDestroyValue=gun.get("ExplosionDestroy").getAsBoolean();shot.durability=gun.get("ApDurability").getAsInt();
        shot.damage=gun.get("Damage").getAsFloat();shot.radius=gun.get("ExplosionRadius").getAsFloat();shot.blast=gun.get("ExplosionDamage").getAsFloat();
        f.hit();assertEquals(List.of(BlockPos.ZERO),shot.world.writes);
        assertEquals(0,shot.blast);assertEquals(0,shot.radius);assertTrue(shot.damage<gun.get("Damage").getAsFloat(),"Native impact attenuation still runs");
        assertEquals(0,shot.explosions,"Penetrating a single ordinary block needs no extra blast");
    }
    @Test void exactOldDisabledFlagAndEachNativeConfigVetoPreventBlockWrites()throws Exception {
        for(int mode=0;mode<3;mode++) {
            var f=new Fixture(temp.resolve("native-"+mode));
            if(mode==0)f.shot.explosionDestroyValue=false;
            if(mode==1)Config.EXPLOSION_DESTROY.value=false;
            if(mode==2)Config.EXTRA_EXPLOSION_EFFECT.value=false;
            f.hit();assertTrue(f.shot.world.writes.isEmpty());
        }
    }
    @Test void liveGameruleAndProtectionHookVetoWritesEvenAfterTheShellWasCreated()throws Exception {
        var f=new Fixture(temp);f.shot.world.rules.grief=false;f.hit();assertTrue(f.shot.world.writes.isEmpty());
        f.shot.world.rules.grief=true;f.shot.world.eventAllows=false;f.hit();assertTrue(f.shot.world.writes.isEmpty());
        f.shot.world.eventAllows=true;f.hit();assertEquals(List.of(BlockPos.ZERO),f.shot.world.writes);
    }
    @Test void unbreakableAndInsufficientPenetrationStopAtTheOriginalBoundary()throws Exception {
        for(int mode=0;mode<2;mode++) {
            var f=new Fixture(temp.resolve("hard-"+mode));
            if(mode==0)f.shot.world.solid.block.hardness=-1;
            else {f.shot.world.solid.sound=Sound.METAL;f.shot.durability=1;}
            f.hit();assertTrue(f.shot.world.writes.isEmpty());assertTrue(f.shot.removed);assertEquals(1,f.shot.explosions);
        }
    }
    static class Fixture extends ClassLoader {
        static final String SBW="com/atsuishio/superbwarfare/",CANNON=SBW+"entity/projectile/CannonShellEntity",BASE=SBW+"entity/projectile/FastThrowableProjectile",
                PREFIX="com/liucodex/doggiesnewvehicle/compat/apimpact/";
        final Map<String,byte[]> bytes=new HashMap<>();final Map<String,String> types=new HashMap<>(),methods=new HashMap<>(),fields=new HashMap<>();
        final Shot shot;
        Fixture(Path temp)throws Exception {
            Config.EXPLOSION_DESTROY.value=Config.EXTRA_EXPLOSION_EFFECT.value=true;
            types.put(CANNON,PREFIX+"Cannon");types.put(BASE,PREFIX+"Base");
            Map.ofEntries(Map.entry(CANNON+"$Type",ShellType.class),Map.entry("net/minecraft/world/entity/Entity",Body.class),
                    Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",World.class),
                    Map.entry("net/minecraft/world/level/block/state/BlockState",State.class),Map.entry("net/minecraft/world/level/block/Block",Block.class),
                    Map.entry("net/minecraft/world/level/block/SoundType",Sound.class),Map.entry(SBW+"config/server/ExplosionConfig",Config.class),
                    Map.entry("net/minecraftforge/common/ForgeConfigSpec$BooleanValue",Flag.class),Map.entry("net/neoforged/neoforge/common/ModConfigSpec$BooleanValue",Flag.class),
                    Map.entry(SBW+"tools/ParticleTool",Particles.class),Map.entry(SBW+"Mod",Mod.class),Map.entry(SBW+"Mod$Companion",Queue.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Policy.class)).forEach((k,v)->types.put(k,Type.getInternalName(v)));
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] p=line.split(" ");if(p[0].equals("MD:"))methods.put(shortName(p[1]),shortName(p[3]));if(p[0].equals("FD:"))fields.put(shortName(p[1]),shortName(p[2]));
            }
            var harness=new DnvMuzzleMixinApplicationTest();Files.createDirectories(temp);harness.temp=temp;
            for(String source:List.of(BASE,CANNON))try(var loader=harness.loader()) {
                var n=new ClassNode();new ClassReader(DnvMuzzleMixinApplicationTest.transform(loader,source.substring(SBW.length()),"ProjectileBlockDamageMixin","SERVER",false)[1]).accept(n,0);
                n.methods.removeIf(m->!m.name.equals("afterHitBlock")&&!m.name.startsWith("destroyBlock")&&!m.name.contains("dnv$"));
                // The superclass owns afterHitBlock for non-AP projectiles; this probe exercises CannonShell's override.
                if(source.equals(BASE))n.methods.removeIf(m->m.name.equals("afterHitBlock"));
                n.fields.clear();n.interfaces.clear();n.innerClasses.clear();n.nestMembers=null;n.visibleAnnotations=null;n.access=Opcodes.ACC_PUBLIC;
                n.superName=source.equals(BASE)?Type.getInternalName(Shot.class):BASE;
                var init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,n.superName,"<init>","()V",false));init.instructions.add(new InsnNode(Opcodes.RETURN));init.maxStack=1;init.maxLocals=1;n.methods.add(init);
                var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(new ClassRemapper(out,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String o,String value,String d){return methods.getOrDefault(value,value);}
                    @Override public String mapFieldName(String o,String value,String d){return fields.getOrDefault(value,value);}
                }));bytes.put(types.get(source).replace('/','.'),out.toByteArray());
            }
            shot=(Shot)loadClass((PREFIX+"Cannon").replace('/','.')).getConstructor().newInstance();
        }
        static String shortName(String s){return s.substring(s.lastIndexOf('/')+1);}
        void hit()throws Exception{shot.getClass().getMethod("afterHitBlock",BlockHitResult.class).invoke(shot,new BlockHitResult(new Vec3(.5,.5,0),Direction.NORTH,BlockPos.ZERO,false));}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException{var b=bytes.get(name);if(b==null)throw new ClassNotFoundException(name);return defineClass(name,b,0,b.length);}
    }
    public static class Body extends DnvBlockDamagePolicyExecutionTest.Body{}
    public static class Shot extends Body {
        public final World world=new World();public ShellType type=ShellType.AP;public boolean explosionDestroyValue=true,firstHit=true,removed;
        public int durability=100,explosions;public float damage=800,blast,radius;public Vec3 velocity=new Vec3(0,0,36),position=Vec3.ZERO;
        @Override public World level(){return world;}public Body getOwner(){return null;}
        public boolean getExplosionDestroyValue(){return explosionDestroyValue;}public int getDurability(){return durability;}public void setDurability(int v){durability=v;}
        public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(Vec3 v){velocity=v;}public void setPos(double x,double y,double z){position=new Vec3(x,y,z);}
        public float getDamageValue(){return damage;}public void setDamage(float v){damage=v;}public float getExplosionDamageValue(){return blast;}public void setExplosionDamage(float v){blast=v;}
        public float getExplosionRadiusValue(){return radius;}public void setExplosionRadius(float v){radius=v;}public void causeExplode(Vec3 v){explosions++;}public void discard(){removed=true;}
        public void causeWPEffect(Vec3 v,Body body){throw new AssertionError("AP must not use WP");}
    }
    public static class World extends DnvBlockDamagePolicyExecutionTest.World {
        public final List<BlockPos> writes=new ArrayList<>();public final State solid=new State(false),air=new State(true);
        public State getBlockState(BlockPos pos){return pos.equals(BlockPos.ZERO)&&!writes.contains(pos)?solid:air;}
        @Override public boolean destroyBlock(BlockPos pos,boolean drops){writes.add(pos);return true;}
    }
    public static class State {
        final boolean air;public final Block block=new Block();public Sound sound=Sound.STONE;State(boolean a){air=a;}
        public Block getBlock(){return block;}public boolean isAir(){return air;}public boolean canOcclude(){return !air;}public Sound getSoundType(){return sound;}
    }
    public static class Block {public float hardness=1.5F;public float defaultDestroyTime(){return hardness;}}
    public static class Sound {public static final Sound STONE=new Sound(),WOOD=new Sound(),METAL=new Sound(),NETHERITE_BLOCK=new Sound(),ANVIL=new Sound(),COPPER=new Sound(),GLASS=new Sound();}
    public enum ShellType {AP,HE,WP}
    public static class Flag {public boolean value=true;public Object get(){return value;}}
    public static class Config {public static final Flag EXPLOSION_DESTROY=new Flag(),EXTRA_EXPLOSION_EFFECT=new Flag();}
    public static class Particles {public static void cannonHitParticles(World w,Vec3 v){}}
    public static class Mod {public static final Queue Companion=new Queue();}
    public static class Queue {public boolean queueServerWork(int delay,Runnable work){return true;}}
    public static class Policy {
        public static boolean owned(Body body){return DnvBlockDamagePolicyExecutionTest.Bridge.owned(body);}
        public static boolean allowed(World world,Body body){return DnvBlockDamagePolicyExecutionTest.Bridge.allowed(world,body);}
    }
}

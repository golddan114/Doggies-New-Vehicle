package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.entity.DnvVehicleCombatRules;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes the compiled phase capacity and damage entry against a controlled native hurt port. */
class SilentHeavenPhaseExecutionTest {
    private static final String ROOT = "com/liucodex/silentheaven/entity/";
    private static final String SUBJECT = "com/liucodex/silentheaven/entity/ExecutedSilentHeavenPhase";
    private static Class<?> compiled;

    @Test void percentageUsesOwnPhaseArmorBeforeNativeDebitAndCannotCarryAcrossTransition() throws Exception {
        Base vehicle = create();
        var damage = new Damage(new Actor(), ""); damage.percent = .06F;
        vehicle.hurt(damage, 100);
        assertEquals(153, vehicle.lastNativeDamage, .001);
        assertEquals(3847, vehicle.health, .001);
        assertEquals(1, vehicle.nativeCalls);
        vehicle.health = 100;
        var lethal = new Damage(new Actor(), ""); lethal.percent = .06F;
        assertTrue(vehicle.hurt(lethal, 100));
        assertEquals(1, vehicle.phase); assertEquals(1, vehicle.health);
        assertFalse(vehicle.hurt(new Damage(), 100));
    }

    @Test void noiseAndBothHeavenPhasesLimitOneStarPairWithoutChangingTheirArmor() throws Exception {
        for(int variant=0;variant<3;variant++){
            Base vehicle=variant==0?(Base)compile(true).getConstructor().newInstance():create();
            vehicle.phase=variant==2?2:0;vehicle.health=vehicle.getMaxHealth();float before=vehicle.health;
            Actor projectile=new Actor();Damage hit=new Damage(projectile,"hit"),slash=new Damage(projectile,"slash");
            for(int repeat=0;repeat<11;repeat++){vehicle.hurt(hit,60);vehicle.hurt(slash,45);}
            assertEquals(variant==0?26.25F:variant==1?47.25F:21F,before-vehicle.health,.002);
            assertEquals(2,vehicle.nativeCalls,"Native hurt may return false after subtracting health");
            vehicle.hurt(new Damage(new Actor(),"hit"),60);assertEquals(3,vehicle.nativeCalls);
        }
    }

    @Test void rejectedStarHitDoesNotConsumeItsStageAndTransitionKeepsTheCommittedReceipt() throws Exception {
        Base vehicle=create();Actor projectile=new Actor();Damage hit=new Damage(projectile,"hit");
        vehicle.nativeAccept=false;vehicle.hurt(hit,60);assertEquals(4000,vehicle.health);
        vehicle.nativeAccept=true;vehicle.hurt(hit,60);assertEquals(3973,vehicle.health,.002);
        vehicle.hurt(hit,60);assertEquals(2,vehicle.nativeCalls);
        vehicle.health=10;Damage lethal=new Damage(new Actor(),"hit");vehicle.hurt(lethal,60);
        assertEquals(1,vehicle.phase);vehicle.phase=2;vehicle.health=3000;
        vehicle.hurt(lethal,60);assertEquals(3000,vehicle.health);
    }

    @Test void nativeRejectionCannotForceTransitionButAcceptedLethalHitCannotCarryOverflow() throws Exception {
        Base vehicle = create();
        assertEquals(4000, vehicle.getMaxHealth());
        vehicle.nativeAccept = false;
        assertFalse(vehicle.hurt(new Damage(), 10_000));
        assertEquals(4000, vehicle.getHealth());
        assertEquals(0, vehicle.phase);
        assertEquals(0, vehicle.transitions);

        vehicle.nativeAccept = true;
        vehicle.nativeReturn = false; // SBW may subtract health before Entity.hurt returns false.
        assertTrue(vehicle.hurt(new Damage(), 10_000));
        assertEquals(1, vehicle.transitions);
        assertEquals(1, vehicle.phase);
        assertEquals(1, vehicle.getHealth());
        assertEquals(3000, vehicle.getMaxHealth());
        assertFalse(vehicle.hurt(new Damage(), 10_000));
        assertEquals(1, vehicle.transitions);
    }

    @Test void lowFirstLifeAndExternalRepairRespectBothNativePhaseCapacities() throws Exception {
        Base vehicle = create();
        vehicle.setHealth(1);
        assertTrue(vehicle.hurt(new Damage(), 10));
        assertEquals(.5F, vehicle.lastNativeDamage);
        assertEquals(1, vehicle.getHealth());
        vehicle.setHealth(4000);
        assertEquals(3000, vehicle.getHealth(), "The transition bar must fill against 3000");
        vehicle.phase = 2;
        vehicle.setHealth(2500);
        vehicle.heal(1000);
        assertEquals(3000, vehicle.getHealth(), "External native repair must use the second-life cap");
        vehicle.phase = 0;
        vehicle.heal(1000);
        assertEquals(4000, vehicle.getHealth(), "First life still retains its 4000 cap");
    }

    @Test void compiledRestoreSetsPhaseBeforeApplyingThePhaseHealthCap() throws Exception {
        var source = read(ROOT + "SilentHeavenVehicleEntity");
        MethodNode restore = source.methods.stream().filter(m -> m.name.equals("readAdditionalSaveData")).findFirst().orElseThrow();
        int phase = -1, capacity = -1, health = -1, index = 0;
        for (var instruction : restore.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("setPhase")) phase = index;
                if (call.owner.endsWith("SilentHeavenPhaseHealth") && call.name.equals("capacity")) capacity = index;
                if (call.name.equals("setHealth") && health < 0) health = index;
            }
            index++;
        }
        assertTrue(phase >= 0 && phase < capacity && capacity < health,
                "Saved phase must be restored before its health is clamped");
    }

    private static Base create() throws Exception {
        if (compiled == null) compiled = compile();
        return (Base) compiled.getConstructor().newInstance();
    }

    private static Class<?> compile() throws Exception {
        return compile(false);
    }
    private static Class<?> compile(boolean noise) throws Exception {
        String owner=noise?"com/liucodex/noise/entity/AbstractNoiseVehicleCombat":ROOT+"AbstractSilentHeavenVehicleCombat";
        ClassNode combat = read(owner);
        ClassNode state = read(ROOT + "AbstractSilentHeavenVehicleState");
        ClassNode subject = new ClassNode();
        subject.version = Opcodes.V17;
        subject.access = Opcodes.ACC_PUBLIC;
        subject.name = SUBJECT;
        subject.superName = Type.getInternalName(Base.class);
        for (MethodNode method : combat.methods) if (method.name.equals("hurt")) subject.methods.add(method);
        for (MethodNode method : state.methods) if (!noise && method.name.equals("getMaxHealth")) subject.methods.add(method);
        subject.methods.forEach(method -> method.access = Opcodes.ACC_PUBLIC);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, subject.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 1;
        subject.methods.add(constructor);

        Map<String, String> names = new HashMap<>();
        names.put(owner,SUBJECT);
        names.put("com/liucodex/noise/entity/AbstractNoiseVehicleWeapons",subject.superName);
        names.put(ROOT + "AbstractSilentHeavenVehicleCombat", SUBJECT);
        names.put(ROOT + "AbstractSilentHeavenVehicleState", SUBJECT);
        for (String parent : new String[]{"AbstractSilentHeavenVehicleWeapons", "AbstractSilentHeavenVehicleNavigation",
                "AbstractSilentHeavenVehicleLifecycle", "AbstractSilentHeavenVehicleContract"})
            names.put(ROOT + parent, subject.superName);
        names.put("net/minecraft/world/damagesource/DamageSource", Type.getInternalName(Damage.class));
        names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Actor.class));
        String receipt="com/liucodex/doggiesnewvehicle/core/DnvStarProjectileHits";
        names.put(receipt,SUBJECT+"Receipt");
        names.put("net/minecraft/resources/ResourceKey","java/lang/String");
        names.put("com/atsuishio/superbwarfare/init/ModDamageTypes",Type.getInternalName(Keys.class));
        names.put("com/liucodex/doggiesnewvehicle/core/DnvTaggedProjectileArmor",Type.getInternalName(Armor.class));
        names.put("com/liucodex/doggiesnewvehicle/core/DnvPercentHits",Type.getInternalName(Percent.class));
        ClassWriter writer = new ClassWriter(0);
        subject.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        byte[] bytes = writer.toByteArray();
        ClassWriter receiptWriter=new ClassWriter(0);read(receipt).accept(new ClassRemapper(receiptWriter,new SimpleRemapper(names)));
        return new ClassLoader(SilentHeavenPhaseExecutionTest.class.getClassLoader()) {
            Class<?> define() {
                byte[] receiptBytes=receiptWriter.toByteArray();defineClass((SUBJECT+"Receipt").replace('/','.'),receiptBytes,0,receiptBytes.length);
                return defineClass(SUBJECT.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream stream = SilentHeavenPhaseExecutionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, "Compile the production boss source first: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    public static class Actor {
        public final UUID id=UUID.randomUUID();public final CompoundTag data=new CompoundTag();
        public UUID getUUID(){return id;}public CompoundTag getPersistentData(){return data;}
    }
    public static class Keys {public static final String SUPER_STAR_HIT="hit",SUPER_STAR_SLASH="slash";}
    public static class Armor {public static float apply(Damage source,float raw,float mitigated){return mitigated;}}
    /** Entity/source port; production merge math is shared with the real hit adapter tests. */
    public static class Percent {
        public static float apply(Actor target,Damage source,float raw,float armored){
            if(source.percent<=0||!source.claims.add(target.id))return armored;
            return com.liucodex.doggiesnewvehicle.core.DnvPercentDamage.merge(raw,armored,((Base)target).getMaxHealth(),source.percent);
        }
    }
    public static class Damage {
        public float percent; final java.util.Set<UUID> claims=new java.util.HashSet<>();
        private final Actor direct;private final String stage;
        public Damage(){this(null,"");}public Damage(Actor direct,String stage){this.direct=direct;this.stage=stage;}
        public boolean is(String key){return stage.equals(key);}
        public Actor getEntity() { return null; }
        public Actor getDirectEntity() { return direct; }
    }
    public static class Base extends Actor {
        public boolean phaseDataReady = true, nativeAccept = true, nativeReturn;
        public int phase, transitions, outOfCombatTicks, nativeCalls;
        public float health = 4000, lastNativeDamage;
        public float getMaxHealth() { return 4000; }
        public float getHealth() { return health; }
        public void setHealth(float value) { health = Math.min(getMaxHealth(), value); }
        public void heal(float value) { if (health > 0) setHealth(health + value); }
        public int getPhase() { return phase; }
        public boolean isWreck() { return false; }
        public int getDestructionTicks() { return 0; }
        public boolean isEntering() { return false; }
        public boolean isTransitioning() { return phase == 1; }
        public boolean isDamageImmune(Damage source) { return false; }
        public DnvVehicleCombatRules.DamageCategory classifyDamage(Damage source) {
            return source.direct==null?DnvVehicleCombatRules.DamageCategory.OTHER:DnvVehicleCombatRules.DamageCategory.BULLET;
        }
        public static boolean isSilentHeavenOwned(Actor actor) { return false; }
        public static boolean isNoiseOwned(Actor actor) { return false; }
        public void beginTransition() { transitions++;setHealth(1);phase = 1; }
        public void tryPlayDamageVoice() { }
        public boolean hurt(Damage source, float amount) {
            nativeCalls++;lastNativeDamage = amount;
            if (!nativeAccept) return false;
            setHealth(health - amount);
            return nativeReturn;
        }
    }
}

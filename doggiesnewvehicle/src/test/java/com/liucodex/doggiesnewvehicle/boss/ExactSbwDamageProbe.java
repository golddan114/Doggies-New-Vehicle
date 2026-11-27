package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Exact normal dispatch and vehicle health debit bytecode; events, network and force fallback are explicit boundaries. */
public final class ExactSbwDamageProbe {
    private static final String UPSTREAM = "com/atsuishio/superbwarfare/";
    private static final Class<?> DISPATCH = normalDispatch();
    private static final Class<?> HEALTH = nativeHealth();
    private static final Class<?> BULLET = nativeBullet();
    private ExactSbwDamageProbe() { }

    public static boolean damage(Target target, Source source, float amount) {
        try { return (boolean) DISPATCH.getMethod("doDamage", Target.class, Source.class, float.class).invoke(null, target, source, amount); }
        catch (ReflectiveOperationException failure) { throw unwrap(failure); }
    }
    public static VehicleHealth health(float maximum) {
        try {
            VehicleHealth value = (VehicleHealth) HEALTH.getConstructor().newInstance();
            value.maximum = maximum; value.setHealth(maximum); return value;
        } catch (ReflectiveOperationException failure) { throw unwrap(failure); }
    }
    public static BulletHost bullet(float bypass, float headshot) {
        try {
            BulletHost value=(BulletHost)BULLET.getConstructor().newInstance();
            value.bypassArmorRateValue=bypass;value.headShotValue=headshot;return value;
        } catch(ReflectiveOperationException failure){throw unwrap(failure);}
    }
    private static RuntimeException unwrap(ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException wrapped ? wrapped.getCause() : failure;
        if (cause instanceof Error error) throw error;
        return cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
    }

    public record Source(String id) { }
    public record Submitted(Source source,float amount) { }
    public static class Target {
        public int calls;
        public int invulnerableTime;
        public final java.util.List<Submitted> submitted=new java.util.ArrayList<>();
        public float health = 10000, cap = Float.POSITIVE_INFINITY, input;
        public boolean accepted = true;
        public boolean hurt(Source source, float amount) {
            calls++; input += amount;submitted.add(new Submitted(source,amount));
            if (accepted) health -= Math.min(cap, amount);
            return accepted;
        }
    }
    public static class LivingTarget extends Target { }
    public static class VehicleTarget extends Target {
        public final VehicleHealth nativeHealth;
        public VehicleTarget(float maximum) { nativeHealth = health(maximum); }
        @Override public boolean hurt(Source source, float amount) {
            calls++; input += amount; nativeHealth.onHurt(amount, this, true);
            return false; // Native Entity.hurt may return false after VehicleEntity.onHurt commits health.
        }
    }
    public static class RegistryPort { }
    public static class RegistryLevel {public RegistryPort registryAccess(){return new RegistryPort();}}
    public abstract static class BulletHost extends Target {
        public String gunItemId;
        public abstract String getGunItemId();
        public void identify(Stack stack) {
            try { getClass().getMethod("setGunItemId",Stack.class).invoke(this,stack); }
            catch(ReflectiveOperationException failure){throw unwrap(failure);}
        }
        public float bypassArmorRateValue,headShotValue=1;
        public RegistryLevel level(){return new RegistryLevel();}
        public Target getOwner(){return this;}
        public abstract void performDamage(Target target,float amount,boolean headshot);
    }
    public record Stack(String descriptionId) {
        public String getDescriptionId(){return descriptionId;}
    }
    public static class BulletDamage {
        public static boolean forceHurt(Target target,Source source,float amount){return damage(target,source,amount);}
        public static Source causeGunFireDamage(RegistryPort registry,Target direct,Target owner){return new Source("gunfire");}
        public static Source causeGunFireAbsoluteDamage(RegistryPort registry,Target direct,Target owner){return new Source("gunfire_absolute");}
        public static Source causeGunFireHeadshotDamage(RegistryPort registry,Target direct,Target owner){return new Source("gunfire_headshot");}
        public static Source causeGunFireHeadshotAbsoluteDamage(RegistryPort registry,Target direct,Target owner){return new Source("gunfire_headshot_absolute");}
    }
    public static final class BooleanValue {
        public boolean enabled;
        public Object get() { return enabled; }
    }
    public static final class Options { public static final BooleanValue FORCE_DAMAGE_MODE = new BooleanValue(); }
    public static class Accessor { }
    public static class Data {
        private Object value = 0F;
        public Object get(Accessor ignored) { return value; }
        public void set(Accessor ignored, Object value) { this.value = value; }
    }
    public abstract static class VehicleHealth {
        public static final Accessor HEALTH = new Accessor();
        public final Data entityData = new Data();
        public float maximum;
        public float getMaxHealth() { return maximum; }
        public abstract float getHealth();
        public abstract void setHealth(float value);
        public abstract void onHurt(float amount, Target attacker, boolean indication);
    }

    private static ClassNode read(String path) throws Exception {
        try (var zip = new ZipFile(SbwTestDependency.path().toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(UPSTREAM + path + ".class"))).accept(node, 0);
            return node;
        }
    }
    private static Class<?> normalDispatch() {
        try {
            ClassNode node = read("tools/DamageHandler");
            MethodNode method = node.methods.stream().filter(m -> m.name.equals("doDamage")).findFirst().orElseThrow();
            LabelNode notLiving = null, forceFallback = null;
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                        && type.desc.equals("net/minecraft/world/entity/LivingEntity")) {
                    notLiving = ((JumpInsnNode) nextCode(instruction)).label;
                }
                if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/Boolean") && call.name.equals("booleanValue")) {
                    forceFallback = ((JumpInsnNode) nextCode(instruction)).label;
                    break;
                }
            }
            if (notLiving == null || forceFallback == null) throw new AssertionError("Review changed native dispatch topology");
            while (method.instructions.getLast() != forceFallback) method.instructions.remove(method.instructions.getLast());
            method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"));
            method.instructions.add(new InsnNode(Opcodes.DUP));
            method.instructions.add(new LdcInsnNode("Force fallback is outside this normal-dispatch probe"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
            method.instructions.add(notLiving);
            method.instructions.add(new InsnNode(Opcodes.ICONST_0));
            method.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.clear(); node.methods.add(method);
            return define(node, "NativeNormalDispatch", Object.class, Map.of(
                    "net/minecraft/world/entity/Entity", Target.class,
                    "net/minecraft/world/entity/LivingEntity", LivingTarget.class,
                    "net/minecraft/world/damagesource/DamageSource", Source.class,
                    UPSTREAM + "config/server/MiscConfig", Options.class,
                    "net/minecraftforge/common/ForgeConfigSpec$BooleanValue", BooleanValue.class,
                    "net/neoforged/neoforge/common/ModConfigSpec$BooleanValue", BooleanValue.class));
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static Class<?> nativeHealth() {
        try {
            ClassNode node = read("entity/vehicle/base/VehicleEntity");
            MethodNode debit = node.methods.stream().filter(m -> m.name.equals("onHurt")).findFirst().orElseThrow();
            MethodInsnNode write = null;
            for (var instruction : debit.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("setHealth")) {
                    if (write != null) throw new AssertionError("Review multiple native health writes");
                    write = call;
                }
            }
            if (write == null) throw new AssertionError("Missing native health write");
            AbstractInsnNode read = write.getPrevious();
            while (!(read instanceof MethodInsnNode call && call.name.equals("getHealth"))) read = read.getPrevious();
            AbstractInsnNode start = previousCode(previousCode(read));
            if (start.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) start).var != 0) throw new AssertionError("Review health debit receiver");
            while (debit.instructions.getFirst() != start) debit.instructions.remove(debit.instructions.getFirst());
            while (debit.instructions.getLast() != write) debit.instructions.remove(debit.instructions.getLast());
            debit.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.removeIf(m -> m != debit && !m.name.equals("getHealth") && !m.name.equals("setHealth"));
            MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, Type.getInternalName(VehicleHealth.class), "<init>", "()V", false));
            constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
            return define(node, "NativeVehicleHealthDebit", VehicleHealth.class, Map.of(
                    "net/minecraft/world/entity/Entity", VehicleHealth.class,
                    "net/minecraft/network/syncher/SynchedEntityData", Data.class,
                    "net/minecraft/network/syncher/EntityDataAccessor", Accessor.class));
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static Class<?> nativeBullet() {
        try {
            ClassNode node=read("entity/projectile/ProjectileEntity");
            node.methods.removeIf(method->!java.util.Set.of("performDamage","setGunItemId","getGunItemId").contains(method.name));
            MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,Type.getInternalName(BulletHost.class),"<init>","()V",false));
            constructor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(constructor);
            return define(node,"NativeBulletDamage",BulletHost.class,Map.of(
                    "net/minecraft/world/entity/Entity",Target.class,
                    "net/minecraft/world/damagesource/DamageSource",Source.class,
                    "net/minecraft/world/level/Level",RegistryLevel.class,
                    "net/minecraft/core/RegistryAccess",RegistryPort.class,
                    "net/minecraft/world/item/ItemStack",Stack.class,
                    UPSTREAM+"entity/vehicle/base/VehicleEntity",VehicleTarget.class,
                    UPSTREAM+"init/ModDamageTypes",BulletDamage.class,
                    UPSTREAM+"tools/DamageHandlerKt",BulletDamage.class));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction.getOpcode() < 0);
        return instruction;
    }
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction.getOpcode() < 0);
        return instruction;
    }
    private static Class<?> define(ClassNode node, String simple, Class<?> parent, Map<String,Class<?>> ports) {
        String name = "com/liucodex/doggiesnewvehicle/boss/damageprobe/" + simple;
        Map<String,String> mapping = new HashMap<>();
        ports.forEach((key, value) -> mapping.put(key, Type.getInternalName(value)));
        mapping.put(node.name, name);
        node.superName = Type.getInternalName(parent); node.access = Opcodes.ACC_PUBLIC;
        node.fields.clear(); node.interfaces.clear(); node.innerClasses.clear(); node.nestMembers = null; node.nestHostClass = null;
        node.visibleAnnotations = null; node.invisibleAnnotations = null;
        for (var method : node.methods) {
            method.localVariables = null; method.tryCatchBlocks.clear();
            for (var instruction : method.instructions.toArray()) if (instruction instanceof FrameNode) method.instructions.remove(instruction);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String value) { return mapping.getOrDefault(value, value); }
            @Override public String mapFieldName(String owner, String field, String desc) {
                return switch(field){case "f_19804_"->"entityData";case "f_19802_"->"invulnerableTime";default->field;};
            }
            @Override public String mapMethodName(String owner, String method, String desc) {
                return switch (method) {
                    case "m_6469_" -> "hurt"; case "m_135370_" -> "get"; case "m_135381_" -> "set";
                    case "m_9236_"->"level";case "m_9598_"->"registryAccess";case "m_19749_"->"getOwner";
                    case "m_41778_"->"getDescriptionId";default -> method;
                };
            }
            @Override public String mapMethodDesc(String descriptor) {
                // The extracted debit never touches its attacker; keep that unused port separate from the receiver.
                if (parent == VehicleHealth.class && descriptor.equals("(FLnet/minecraft/world/entity/Entity;Z)V"))
                    return "(F" + Type.getDescriptor(Target.class) + "Z)V";
                return super.mapMethodDesc(descriptor);
            }
        }));
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(ExactSbwDamageProbe.class.getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }
}

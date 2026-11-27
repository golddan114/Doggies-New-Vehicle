package com.liucodex.doggiesnewvehicle.compat;

import java.util.*;
import java.util.function.Consumer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled tank ammo scheduling plus exact SBW weapon switching. Storage/world are explicit doubles. */
final class ExactApocalypseSelectionFixture extends ClassLoader {
    private static final String SBW = "com/atsuishio/superbwarfare/";
    private static final String ARM = "com/liucodex/doggiesnewvehicle/armored/ApocalypseArmament";
    private static final String VEHICLE = SBW + "entity/vehicle/base/VehicleEntity";
    private static final String PREFIX = "com/liucodex/doggiesnewvehicle/compat/apocalypseprobe/";
    private final Map<String, String> names = new HashMap<>();
    final Tank tank;
    private final Object armament;
    private final Class<?> armamentType;

    ExactApocalypseSelectionFixture() throws Exception {
        names.put(ARM, PREFIX + "Armament");
        names.put(VEHICLE, PREFIX + "Vehicle");
        Map.ofEntries(
            Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity", Tank.class),
            Map.entry(SBW + "data/gun/GunData", Data.class), Map.entry(SBW + "data/gun/GunProp", Prop.class),
            Map.entry(SBW + "data/gun/value/IntValue", Int.class), Map.entry(SBW + "data/gun/NbtVersion", Version.class),
            Map.entry(SBW + "data/gun/SoundInfo", Sound.class), Map.entry(SBW + "data/vehicle/DefaultVehicleData", Computed.class),
            Map.entry(SBW + "data/vehicle/subdata/SeatInfo", Seat.class),
            Map.entry("net/minecraft/world/entity/Entity", Body.class),
            Map.entry("net/minecraft/server/level/ServerPlayer", Passenger.class),
            Map.entry("net/minecraft/network/chat/Component", Message.class),
            Map.entry("net/minecraft/network/chat/MutableComponent", Message.class),
            Map.entry("net/minecraft/world/level/Level", World.class),
            Map.entry("net/minecraft/sounds/SoundEvent", SoundEvent.class),
            Map.entry("net/minecraft/sounds/SoundSource", SoundSource.class),
            Map.entry("net/minecraft/util/Mth", Maths.class))
            .forEach((name, type) -> names.put(name, Type.getInternalName(type)));
        Class<?> vehicle = compile(VEHICLE, Set.of("changeWeapon", "setWeaponIndex", "setWeaponIndex$lambda$43"), Tank.class);
        tank = (Tank) vehicle.getConstructor().newInstance();
        armamentType = compile(ARM, Set.of("requestAmmo", "prepareReloads", "lambda$prepareReloads$1"), null);
        armament = armamentType.getConstructor(Tank.class).newInstance(tank);
    }

    boolean request(String weapon, int index) throws Exception {
        return (boolean) armamentType.getMethod("requestAmmo", Data.class, int.class).invoke(armament, tank.guns.get(weapon), index);
    }
    void prepare() throws Exception { armamentType.getMethod("prepareReloads").invoke(armament); }
    void change(int value, boolean scroll) throws Exception {
        tank.getClass().getMethod("changeWeapon", int.class, int.class, boolean.class).invoke(tank, 0, value, scroll);
    }

    private Class<?> compile(String original, Set<String> selected, Class<?> parent) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = getParent().getResourceAsStream(original + ".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node, ClassReader.SKIP_FRAMES);
        }
        node.methods.removeIf(method -> !selected.contains(method.name) && !(original.equals(ARM) && method.name.equals("<init>")));
        node.innerClasses.clear(); node.nestMembers = null; node.visibleAnnotations = null;
        if (parent != null) {
            node.superName = Type.getInternalName(parent); node.fields.clear(); node.interfaces.clear();
            node.access = Opcodes.ACC_PUBLIC;
            MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
            constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        } else {
            node.fields.removeIf(field -> !Set.of("tank", "nextTrigger").contains(field.name));
        }
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        node.accept(new ClassRemapper(out, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        byte[] bytes = out.toByteArray();
        return defineClass(names.get(original).replace('/', '.'), bytes, 0, bytes.length);
    }

    public static class Body {}
    public static class Passenger extends Body { public void displayClientMessage(Message message, boolean actionBar) {} }
    public static class Message { public static Message translatable(String text, Object[] args) { return new Message(); } }
    public static class World {
        public boolean isClientSide, f_46443_;
        public void m_6269_(Body source, Body entity, SoundEvent sound, SoundSource category, float volume, float pitch) {}
    }
    public enum SoundSource { NEUTRAL }
    public static class SoundEvent {}
    public static class Sound { public SoundEvent getChange() { return null; } }
    public enum Prop { SOUND_INFO, WITHDRAW_AMMO_WHEN_CHANGE_SLOT }
    public static class Int {
        private int value;
        public int get() { return value; }
        public void set(int value) { this.value = value; }
    }
    public static class Version { public void invalidateStructural() {} }
    public static class Data {
        public final Int ammo = new Int(), selectedAmmoType = new Int();
        public final Version nbtVersion = new Version();
        public int cachedBackupAmmo, remaining, withdrawn;
        public boolean reloading() { return remaining > 0; }
        public Object get(Prop prop) { return prop == Prop.SOUND_INFO ? new Sound() : false; }
        public void withdrawAmmo(Body supplier) { withdrawn++; ammo.set(0); }
    }
    public static class Computed { public List<Seat> seats() { return List.of(new Seat()); } }
    public static class Seat { public List<String> weapons() { return List.of("MainL", "RocketL", "Missiles"); } }
    public static class Maths { public static int m_14045_(int value, int min, int max) { return Math.max(min, Math.min(max, value)); } }
    public static class Tank extends Body {
        public final Map<String, Data> guns = new LinkedHashMap<>();
        public final World world = new World();
        public int preferred, main, rocket;
        private List<Integer> selected = List.of(0);
        public Tank() { for (String key : List.of("MainL", "MainR", "RocketL", "RocketR", "Missiles")) guns.put(key, new Data()); }
        public Data getGunData(String name) { return guns.get(name); }
        public void modifyGunData(String name, Consumer<Data> consumer) { consumer.accept(getGunData(name)); }
        public void modifyGunData(int seat, int weapon, Consumer<Data> consumer) {
            consumer.accept(getGunData(weapon == 0 ? main == 0 ? "MainL" : "MainR" : weapon == 1 ? rocket == 0 ? "RocketL" : "RocketR" : "Missiles"));
        }
        public World level() { return world; }
        public World m_9236_() { return world; }
        public Body getFirstPassenger() { return null; }
        public Body getAmmoSupplier() { return this; }
        public int preferredAmmo() { return preferred; }
        public void setPreferredAmmo(int value) { preferred = value; }
        public List<Integer> getSelectedWeapon() { return selected; }
        public void setSelectedWeapon(List<Integer> value) { selected = value; }
        public int getMaxPassengers() { return 1; }
        public int getWeaponIndex(int seat) { return selected.get(seat); }
        public void updateBackupAmmoCount() {}
        public Computed computed() { return new Computed(); }
        public SoundSource m_5720_() { return SoundSource.NEUTRAL; }
    }
}

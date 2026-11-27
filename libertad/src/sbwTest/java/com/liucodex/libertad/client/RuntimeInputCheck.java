package com.liucodex.libertad.client;

import java.lang.reflect.Method;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import net.minecraftforge.event.TickEvent;

/** Executes the compiled client tick with window, mapping, seat and packet ports.
 * Mouse state deliberately differs from KeyMapping: SBW cancels MouseButton.Pre.
 * This is an input-path regression, not a simulated Minecraft rendering session. */
public final class RuntimeInputCheck {
    private static Method tick;
    private static Class<?> entry;

    public static void main(String[] args) throws Exception {
        checkZoomContract();
        entry = load();
        tick = entry.getMethod("tick", TickEvent.ClientTickEvent.class);
        if (Boolean.getBoolean("libertad.input.legacy")) {
            reset(1); run(); Sink.sent.clear(); Mouse.pressed.addAll(List.of(0, 1)); run();
            require(lastShot() == null && !NativeClient.zoomVehicle, "legacy test.14 did not reproduce input failure");
            System.out.println("PASS: actual test.14 client bytecode reproduces canceled-mouse fire and absent zoom");
            return;
        }
        checkWatchCamera();
        for (int seat = 1; seat <= 2; seat++) {
            reset(seat);
            run();
            Sink.sent.clear();
            Mouse.pressed.add(0);
            Mouse.pressed.add(1);
            require(!Keys.FIRE.isDown(), "fixture must reproduce canceled mouse mapping");
            run();
            Side shot = lastShot();
            require(shot != null && shot.generation == 100 + seat && !shot.high, "mouse fire did not reach current side token");
            require(NativeClient.zoomVehicle, "right mouse did not activate native zoom");
            Mouse.pressed.clear(); run();
            require(!NativeClient.zoomVehicle, "release left native zoom active");
            Client.INSTANCE.options.camera.first = false; run();
            Side view = (Side) Sink.sent.get(Sink.sent.size() - 1);
            require(view.high && !view.shoot, "high-camera origin not sent without shooting");
            Mouse.pressed.add(1); run();
            require(NativeClient.zoomVehicle, "high view cannot zoom");
        }
        System.out.println("PASS: both sides receive canceled mouse fire, hold zoom/release and high-view updates");

        reset(1); run(); Sink.sent.clear();
        Keys.FIRE.key = new Key(KeyType.MOUSE, 4); Keys.HOLD_ZOOM.key = new Key(KeyType.MOUSE, 5);
        Mouse.pressed.addAll(List.of(0, 1)); run();
        require(lastShot() == null && !NativeClient.zoomVehicle, "hard-coded left/right buttons ignored rebind");
        Mouse.pressed.addAll(List.of(4, 5)); run();
        require(lastShot() != null && NativeClient.zoomVehicle, "rebound mouse controls failed");
        Sink.sent.clear(); Keys.FIRE.active = Keys.HOLD_ZOOM.active = false; run();
        require(lastShot() == null && !NativeClient.zoomVehicle, "inactive conflict context/modifier bypassed");
        Keys.FIRE.active = Keys.HOLD_ZOOM.active = true;
        Keys.FIRE.key = new Key(KeyType.KEYSYM, 70); Keys.HOLD_ZOOM.key = new Key(KeyType.SCANCODE, 71);
        Keys.FIRE.down = Keys.HOLD_ZOOM.down = true; run();
        require(lastShot() != null && NativeClient.zoomVehicle, "keyboard/scancode mappings failed");
        Keys.HOLD_ZOOM.down = false;
        Keys.SWITCH_ZOOM.key = new Key(KeyType.KEYSYM, 90); Keys.SWITCH_ZOOM.down = true; run();
        require(NativeClient.zoomVehicle, "toggle zoom did not latch");
        run(); require(NativeClient.zoomVehicle, "held toggle repeatedly changed zoom");
        Keys.SWITCH_ZOOM.down = false; run(); Keys.SWITCH_ZOOM.down = true; run();
        require(!NativeClient.zoomVehicle, "second toggle did not release zoom");
        System.out.println("PASS: rebound mouse, keyboard/scancode, modifier/context and toggle zoom");

        for (int reason = 0; reason < 10; reason++) {
            reset(1); run(); Mouse.pressed.addAll(List.of(0, 1)); run();
            require(lastShot() != null && NativeClient.zoomVehicle, "initial station unavailable");
            Sink.sent.clear();
            var c = Client.INSTANCE;
            switch (reason) {
                case 0 -> c.screen = new Screen();
                case 1 -> c.paused = true;
                case 2 -> c.focused = false;
                case 3 -> c.player.shock = true;
                case 4 -> c.player.alive = false;
                case 5 -> c.player.spectator = true;
                case 6 -> c.player.vehicle.wreck = true;
                case 7 -> c.player.vehicle.crew = false;
                case 8 -> c.player.vehicle = null;
                case 9 -> c.player = null;
            }
            run();
            require(lastShot() == null && !NativeClient.zoomVehicle, "input/zoom leaked after loss reason=" + reason);
            c.screen = null; c.paused = false; c.focused = true; c.player = new Player();
            c.player.vehicle.seat = 1; run();
            require(lastShot() == null, "held fire survived loss/reentry reason=" + reason);
            Mouse.pressed.clear(); run(); Mouse.pressed.add(0); run();
            require(lastShot() != null, "release/repress did not recover reason=" + reason);
        }
        reset(1); run(); Mouse.pressed.add(0); run(); Sink.sent.clear();
        Client.INSTANCE.player.vehicle.seat = 2; run();
        require(lastShot() == null, "seat change carried held fire consent");
        Mouse.pressed.clear(); run(); Mouse.pressed.add(0); run();
        require(lastShot() != null && lastShot().generation == 102, "new side token not used");
        Sink.sent.clear(); Client.INSTANCE.player.vehicle.crew = false; run();
        require(Sink.sent.isEmpty(), "solo observer sent side input");
        Client.INSTANCE.player.vehicle.crew = true; run();
        require(lastShot() == null, "mode return carried held fire consent");
        reset(0); NativeClient.zoomVehicle = true; Mouse.pressed.add(0); run();
        require(lastShot() == null && NativeClient.zoomVehicle, "side bridge changed native helm control");
        require(Sink.sent.equals(List.of(new Optic(4, 100, true))), "helm did not report the native zoom transition");
        run();
        require(Sink.sent.size() == 1, "held zoom sent redundant helm input");
        NativeClient.zoomVehicle = false; run();
        require(Sink.sent.get(1).equals(new Optic(4, 100, false)), "release kept the magnified fire-control origin");
        Client.INSTANCE.player.vehicle.seat = 1; run();
        Client.INSTANCE.player.vehicle.seat = 0; run();
        require(Sink.sent.get(Sink.sent.size() - 1).equals(new Optic(4, 100, false)), "helm reentry inherited the side optic");
        System.out.println("PASS: helm zoom and release select the optical origin once per transition and resynchronize on seat return");
        System.out.println("PASS: menu/pause/focus/shock/death/spectator/wreck/mode/dismount/disconnect clear zoom and fire; reentry requires release");
    }

    private static void reset(int seat) throws Exception {
        Client.INSTANCE = new Client(); Client.INSTANCE.player.vehicle.seat = seat;
        Keys.FIRE = new Mapping(new Key(KeyType.MOUSE, 0));
        Keys.HOLD_ZOOM = new Mapping(new Key(KeyType.MOUSE, 1));
        Keys.SWITCH_ZOOM = new Mapping(new Key(KeyType.KEYSYM, -1));
        Mouse.pressed.clear(); Sink.sent.clear(); NativeClient.zoomVehicle = false;
        for (var field : entry.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() == boolean.class) field.setBoolean(null, false);
            else if (field.getType() == int.class) field.setInt(null, -1);
            else if (field.getType() == long.class) field.setLong(null, 0);
        }
        entry.getDeclaredField("SECONDARIES").set(null, new Mapping(new Key(KeyType.KEYSYM, 86)));
    }
    private static void run() throws Exception { tick.invoke(null, new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private static Side lastShot() { return Sink.sent.stream().filter(x -> x instanceof Side s && s.shoot).map(x -> (Side)x).reduce((a,b)->b).orElse(null); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static void checkWatchCamera() throws Exception {
        String source = "com/liucodex/libertad/client/WatchCamera", target = source + "InputExecution";
        var node = read(source);
        node.methods.removeIf(m -> !Set.of("tick", "observerShip", "active", "exit").contains(m.name));
        node.fields.forEach(f -> f.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        node.innerClasses.clear(); node.nestMembers = null;
        var names = new HashMap<String,String>();
        names.put(source, target);
        Map.ofEntries(Map.entry("net/minecraft/client/Minecraft", Client.class),
                Map.entry("net/minecraft/client/player/LocalPlayer", Player.class),
                Map.entry("net/minecraft/world/entity/player/Player", Player.class),
                Map.entry("net/minecraft/world/entity/Entity", Entity.class),
                Map.entry("com/liucodex/libertad/ship/LibertadEntity", Ship.class),
                Map.entry("net/minecraft/client/Options", Options.class), Map.entry("net/minecraft/client/CameraType", Camera.class),
                Map.entry("net/minecraft/client/KeyMapping", Mapping.class), Map.entry("net/minecraft/client/gui/screens/Screen", Screen.class),
                Map.entry("com/liucodex/libertad/client/WatchOrbit", Rig.class))
                .forEach((key,type) -> names.put(key,Type.getInternalName(type)));
        var type = define(node,target,names);
        var update = type.getMethod("tick", TickEvent.ClientTickEvent.class);
        var event = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
        var orbit = type.getField("orbit");
        var key = new Mapping(new Key(KeyType.KEYSYM, 0));
        type.getField("KEY").set(null,key);
        for (int seat : List.of(1,2)) {
            var c = Client.INSTANCE = new Client();
            var ship = c.player.vehicle;
            var oldView = c.options.camera;
            ship.crew = false; ship.seat = 0;
            key.clicked = true; update.invoke(null,event);
            require(orbit.get(null) == null, "helm entered observer camera");
            ship.seat = seat; ship.crew = true; key.clicked = true; update.invoke(null,event);
            require(orbit.get(null) == null, "gunner entered observer camera");
            ship.crew = false; key.clicked = true; update.invoke(null,event);
            var rig = (Rig) orbit.get(null);
            require(rig != null && rig.seat == seat && c.options.camera == Camera.THIRD_PERSON_BACK, "observer failed to enter");
            key.clicked = true; update.invoke(null,event);
            c.focused = false; key.clicked = true; update.invoke(null,event);
            require(orbit.get(null) == rig && rig.locked(), "switching to driving window lost the shot");
            c.focused = true; key.clicked = true; update.invoke(null,event);
            require(orbit.get(null) == null && c.options.camera == oldView, "manual exit did not restore view");
            for (int loss = 0; loss < 5; loss++) {
                ship.crew = false; ship.seat = seat; ship.wreck = false; c.player.vehicle = ship;
                key.clicked = true; update.invoke(null,event);
                require(orbit.get(null) != null, "observer did not re-enter");
                switch (loss) {
                    case 0 -> ship.crew = true;
                    case 1 -> ship.seat = 3 - seat;
                    case 2 -> c.player.vehicle = null;
                    case 3 -> ship.wreck = true;
                    case 4 -> c.player = null;
                }
                update.invoke(null,event);
                require(orbit.get(null) == null && c.options.camera == oldView, "stale camera after loss " + loss);
            }
        }
        System.out.println("PASS: native client tick enters both solo watch seats, locks across window focus, restores on exit, seat/mode change, dismount, wreck and disconnect");
    }

    public static class Rig {
        final int seat;
        boolean locked;
        public Rig(int seat) { this.seat=seat; }
        public void lock() { locked=true; }
        public boolean locked() { return locked; }
    }

    private static void checkZoomContract() throws Exception {
        if (Boolean.getBoolean("libertad.input.legacy")) return;
        String source = "com/liucodex/libertad/ship/LibertadEntity", target = source + "ZoomExecution";
        var node = read(source);
        node.methods.removeIf(m -> !m.name.equals("getDefaultZoom")); node.fields.clear();
        node.superName = Type.getInternalName(ShipBase.class); node.interfaces.clear(); node.innerClasses.clear(); node.nestMembers = null;
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN)); ctor.maxStack = ctor.maxLocals = 1; node.methods.add(ctor);
        var names = Map.of(source, target, "net/minecraft/world/entity/Entity", Type.getInternalName(Entity.class),
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", node.superName);
        Class<?> type = define(node, target, names);
        var ship = (ShipBase) type.getConstructor().newInstance();
        var zoom = type.getMethod("getDefaultZoom", Entity.class);
        for (int seat = 1; seat <= 2; seat++) {
            ship.seat = seat; ship.crew = true;
            require((double) zoom.invoke(ship, new Entity()) == 5, "native FOV receives no side magnification");
            ship.crew = false;
            require((double) zoom.invoke(ship, new Entity()) == 1, "observer inherits gunner magnification");
        }
        ship.seat = 0;
        require((double) zoom.invoke(ship, new Entity()) == 3.5, "helm zoom no longer delegates to native gun");
        System.out.println("PASS: compiled entity supplies 5x to native FOV for both crew seats, 1x for observers; helm delegates");
    }

    private static ClassNode read(String source) throws Exception {
        var node = new ClassNode(); String archive = System.getProperty("libertad.input.archive");
        if (archive == null) {
            try (var in = RuntimeInputCheck.class.getResourceAsStream("/" + source + ".class")) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        } else {
            try (var zip = new java.util.zip.ZipFile(archive); var in = zip.getInputStream(zip.getEntry(source + ".class"))) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        }
        return node;
    }

    private static Class<?> load() throws Exception {
        String source = "com/liucodex/libertad/client/LibertadClient", target = source + "InputExecution";
        var node = read(source);
        node.methods.removeIf(m -> !Set.of("tick", "keyDown", "riding", "modeKey", "constrainLocalHelm").contains(m.name));
        // Yaw constraints have their own tests; no fake window/player rotations in this input fixture.
        for (var m : node.methods) {
            m.access = (m.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            if (m.name.equals("constrainLocalHelm")) {
                m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables.clear();
                m.instructions.add(new InsnNode(Opcodes.RETURN)); m.maxStack = 0;
            }
        }
        node.fields.removeIf(f -> f.name.equals("projection") || f.name.contains("CROSSHAIR"));
        node.fields.forEach(f -> f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) | Opcodes.ACC_PUBLIC);
        node.innerClasses.clear(); node.nestMembers = null;
        var names = new HashMap<String,String>(); names.put(source, target);
        Map<String,Class<?>> ports = Map.ofEntries(
                Map.entry("net/minecraft/client/Minecraft", Client.class), Map.entry("net/minecraft/client/Options", Options.class),
                Map.entry("net/minecraft/client/CameraType", Camera.class), Map.entry("net/minecraft/client/KeyMapping", Mapping.class),
                Map.entry("net/minecraft/client/gui/screens/Screen", Screen.class), Map.entry("net/minecraft/client/player/LocalPlayer", Player.class),
                Map.entry("net/minecraft/world/entity/Entity", Entity.class), Map.entry("net/minecraft/world/effect/MobEffect", Effect.class),
                Map.entry("net/minecraftforge/registries/RegistryObject", Registry.class), Map.entry("net/minecraft/nbt/CompoundTag", Tag.class),
                Map.entry("com/mojang/blaze3d/platform/InputConstants$Key", Key.class), Map.entry("com/mojang/blaze3d/platform/InputConstants$Type", KeyType.class),
                Map.entry("com/mojang/blaze3d/platform/Window", Window.class), Map.entry("org/lwjgl/glfw/GLFW", Mouse.class),
                Map.entry("com/atsuishio/superbwarfare/init/ModKeyMappings", Keys.class), Map.entry("com/atsuishio/superbwarfare/init/ModMobEffects", Effects.class),
                Map.entry("com/atsuishio/superbwarfare/event/ClientEventHandler", NativeClient.class),
                Map.entry("com/liucodex/libertad/ship/LibertadEntity", Ship.class), Map.entry("com/liucodex/libertad/Libertad", NetworkOwner.class),
                Map.entry("net/minecraftforge/network/simple/SimpleChannel", Sink.class), Map.entry("com/liucodex/libertad/Libertad$SideInput", Side.class),
                Map.entry("com/liucodex/libertad/Libertad$HelmOptic", Optic.class),
                Map.entry("com/liucodex/libertad/Libertad$ToggleCrewMode", Toggle.class), Map.entry("com/liucodex/libertad/Libertad$ToggleSecondaries", Secondary.class));
        ports.forEach((name,type) -> names.put(name, Type.getInternalName(type)));
        return define(node, target, names);
    }

    private static Class<?> define(ClassNode node, String target, Map<String,String> names) {
        // Exact 1.20.1 SRG names also permit running the same scenarios against the shipped JAR.
        var methods = Map.ofEntries(Map.entry("m_91087_", "getInstance"), Map.entry("m_91104_", "isPaused"),
                Map.entry("m_91302_", "isWindowActive"), Map.entry("m_90860_", "getName"), Map.entry("m_90857_", "isDown"),
                Map.entry("m_90859_", "consumeClick"), Map.entry("m_19879_", "getId"), Map.entry("m_128454_", "getLong"),
                Map.entry("m_92176_", "getCameraType"), Map.entry("m_90612_", "isFirstPerson"), Map.entry("m_6084_", "isAlive"),
                Map.entry("m_5833_", "isSpectator"), Map.entry("m_20202_", "getVehicle"), Map.entry("m_21023_", "hasEffect"),
                Map.entry("m_84868_", "getType"), Map.entry("m_84873_", "getValue"), Map.entry("m_91288_", "getCameraEntity"), Map.entry("m_92157_", "setCameraType"), Map.entry("m_213877_", "isRemoved"),
                Map.entry("m_91268_", "getWindow"), Map.entry("m_85439_", "getWindow"));
        var fields = Map.of("f_91066_", "options", "f_92059_", "keyMappings", "f_91074_", "player", "f_91080_", "screen");
        var out = new ClassWriter(0);
        node.accept(new ClassRemapper(out, new SimpleRemapper(names) {
            @Override public String mapMethodName(String owner, String name, String descriptor) { return methods.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return fields.getOrDefault(name, name); }
        }));
        return new ClassLoader(RuntimeInputCheck.class.getClassLoader()) {
            Class<?> define() { return defineClass(target.replace('/', '.'), out.toByteArray(), 0, out.toByteArray().length); }
        }.define();
    }

    public enum KeyType { MOUSE, KEYSYM, SCANCODE }
    public record Key(KeyType type, int value) { public KeyType getType() { return type; } public int getValue() { return value; } }
    public static class Mapping {
        public Key key; public boolean down, clicked, active = true;
        Mapping(Key key) { this.key = key; }
        public boolean isDown() { return down; } public boolean consumeClick() { boolean pending = clicked; clicked = false; return pending; }
        public Key getKey() { return key; } public boolean isConflictContextAndModifierActive() { return active; }
        public String getName() { return "unused"; }
    }
    public static class Keys { public static Mapping FIRE, HOLD_ZOOM, SWITCH_ZOOM; }
    public static class Mouse { static Set<Integer> pressed = new HashSet<>(); public static int glfwGetMouseButton(long window, int button) { require(window == 17, "wrong window"); return pressed.contains(button) ? 1 : 0; } }
    public static class Window { public long getWindow() { return 17; } }
    public static class NativeClient { public static boolean zoomVehicle; }
    public static class Screen { }
    public static class Effect { }
    public static class Registry { public Object get() { return new Effect(); } }
    public static class Effects { public static final Registry SHOCK = new Registry(); }
    public static class Entity { }
    public static class Player extends Entity {
        public Ship vehicle = new Ship(); public boolean alive = true, spectator, shock;
        public Entity getVehicle() { return vehicle; } public boolean isAlive() { return alive; }
        public boolean isSpectator() { return spectator; } public boolean hasEffect(Effect effect) { return shock; }
    }
    public static class Ship extends Entity {
        public int seat = 1; public boolean crew = true, wreck;
        public int getId() { return 4; } public int getSeatIndex(Entity actor) { return seat; }
        public boolean multicrew() { return crew; } public boolean isWreck() { return wreck; } public boolean isRemoved() { return false; }
        public Tag fireSnapshot() { return new Tag(); }
    }
    public static class ShipBase extends Ship { public double getDefaultZoom(Entity actor) { return 3.5; } }
    public static class Tag { public long getLong(String name) { return switch (name) {
        case "operator0" -> 100; case "operator1" -> 101; case "operator2" -> 102; default -> 0; }; } }
    public static class Camera { public static final Camera THIRD_PERSON_BACK = new Camera(); boolean first = true; public boolean isFirstPerson() { return first; } }
    public static class Options { public Mapping[] keyMappings = {}; public Camera camera = new Camera(); public Camera getCameraType() { return camera; } public void setCameraType(Camera type) { camera=type; } }
    public static class Client {
        public static Client INSTANCE; public Player player = new Player(); public Screen screen;
        public Options options = new Options(); public boolean paused, focused = true;
        public Entity getCameraEntity() { return player; }
        public static Client getInstance() { return INSTANCE; } public boolean isPaused() { return paused; }
        public boolean isWindowActive() { return focused; } public Window getWindow() { return new Window(); }
    }
    public static class NetworkOwner { public static final Sink NETWORK = new Sink(); }
    public static class Sink { static final List<Object> sent = new ArrayList<>(); public void sendToServer(Object packet) { sent.add(packet); } }
    public record Side(int shipId, long generation, boolean high, boolean shoot) { }
    public record Optic(int shipId, long generation, boolean zoom) { }
    public record Toggle(int shipId) { }
    public record Secondary(int shipId) { }
}

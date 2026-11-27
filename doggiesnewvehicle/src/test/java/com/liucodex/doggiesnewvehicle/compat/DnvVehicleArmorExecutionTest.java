package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Executes the compiled armor bridge and both production rules with entity/registry ports. */
class DnvVehicleArmorExecutionTest {
    private static final String ROOT = "com/liucodex/doggiesnewvehicle/";

    @Test void lightAaApAdmissionUsesPerMountPenetrationAndPreservesImmunity() throws Exception {
        var probe = new Probe();
        for (String id : List.of("dragon_bofors_twin", "dragon_bofors_quad", "dragon_flak38")) {
            var shell = new SmallShell();
            probe.markShot(shell, "doggievehicle:" + id, "AP");
            var hit = new Source(shell, Keys.PROJECTILE_HIT);
            double expected = id.equals("dragon_flak38") ? 24 : 22.4;
            assertEquals(expected, probe.apply(new Vehicle(), hit, 100, 20), .0001, id);
            assertEquals(100, probe.apply(new Vehicle(), hit, 100, 100));
            assertEquals(0, probe.apply(new Vehicle(), hit, 100, 0));
            assertEquals(150, probe.apply(new Vehicle(), hit, 100, 150));
            assertEquals(20, probe.livingApply(hit, 100, 20), "Existing penetration bridge targets vehicle armour, not infantry armour");
            assertEquals(20, probe.apply(new Vehicle(), new Source(shell, Keys.PROJECTILE_EXPLOSION), 100, 20));
        }
    }

    @Test void lightAaHeAndUnrelatedShellsDoNotAcquireTheApBonus() throws Exception {
        var probe = new Probe();
        for (String id : List.of("dragon_bofors_twin", "dragon_bofors_quad", "dragon_flak38")) {
            var he = new SmallShell(); probe.markShot(he, "doggievehicle:" + id, "HE");
            assertTrue(he.getPersistentData().getBoolean("DnvLightHeShell"));
            assertEquals(20, probe.apply(new Vehicle(), new Source(he, Keys.PROJECTILE_HIT), 100, 20));
        }
        for (String id : List.of("doggievehicle:dragon_flak88", "doggievehicle:dragon_flak40",
                "doggievehicle:m1_yunlong", "doggievehicle:50_changfeng_cannon", "other:dragon_bofors_twin")) {
            var shell = new SmallShell(); probe.markShot(shell, id, "AP");
            assertEquals(20, probe.apply(new Vehicle(), new Source(shell, Keys.PROJECTILE_HIT), 100, 20), id);
        }
        var unknown = new SmallShell(); probe.markShot(unknown, "doggievehicle:dragon_bofors_twin", "OTHER");
        assertEquals(20, probe.apply(new Vehicle(), new Source(unknown, Keys.PROJECTILE_HIT), 100, 20));
        var large = new LargeShell(); probe.markShot(large, "doggievehicle:dragon_bofors_twin", "AP");
        assertEquals(20, probe.apply(new Vehicle(), new Source(large, Keys.PROJECTILE_HIT), 100, 20));
    }

    @Test void coldPercentKeepsNativeLightMediumAndHeavyCoefficientsInBothModes() throws Exception {
        var probe = new Probe();
        assertEquals(19, probe.cold(new LightHull(), false), .0001);
        assertEquals(11.4F, probe.cold(new MediumHull(), false), .0001);
        assertEquals(5.7F, probe.cold(new HeavyHull(), false), .0001);
        assertEquals(552F, probe.cold(new LightHull(), true), .0001);
        assertEquals(331.2F, probe.cold(new MediumHull(), true), .0001);
        assertEquals(165.6F, probe.cold(new HeavyHull(), true), .0001);
        assertEquals(331.2F, probe.cold(new Part(new MediumHull()), true), .0001);
        assertEquals(5.7F, probe.cold(new Part(new HeavyHull()), false), .0001);
    }

    @Test void percentUsesPenetratedArmorAndClaimsOnlyTheDirectTargetOnce() throws Exception {
        var probe = new Probe(); var target = new Vehicle(); var bullet = new Bullet();
        probe.markPercent(bullet, .001F);
        assertEquals(0, probe.apply(target, new Source(bullet, Keys.PROJECTILE_HIT), 10, 0));
        assertEquals(10, probe.apply(target, new Source(bullet, Keys.PROJECTILE_EXPLOSION), 10, 10));
        probe.mark(bullet, .10F);
        assertEquals(5.98F, probe.apply(target, new Source(bullet, Keys.PROJECTILE_HIT), 10, 4), .0001);
        assertEquals(4.6F, probe.apply(target, new Source(bullet, Keys.PROJECTILE_HIT), 10, 4), .0001);
        assertEquals(5.98F, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 10, 4), .0001);
        var second = new Bullet(); probe.markPercent(second, .001F); probe.mark(second, .10F);
        assertEquals(5.98F, probe.apply(target, new Source(second, Keys.PROJECTILE_HIT), 10, 4), .0001);
    }

    @Test void livingBridgeAddsAfterArmorAndMultipartClaimsUseParentIdentity() throws Exception {
        var probe = new Probe(); var bullet = new Bullet(); probe.markPercent(bullet, .06F);
        var source = new Source(bullet, Keys.PROJECTILE_HIT);
        Living living = (Living) probe.living;
        assertEquals(1, probe.livingApply(new Source(bullet, Keys.GUN_FIRE_ABSOLUTE), 1, 1));
        assertEquals(1, probe.livingApply(new Source(bullet, Keys.GUN_FIRE_HEADSHOT_ABSOLUTE), 1, 1));
        assertEquals(200, probe.livingApply(source, 220, 110), .0001);
        assertEquals(110, probe.livingApply(source, 220, 110));
        var other = new Bullet(); probe.markPercent(other, .06F);
        var direct = new Source(other, Keys.PROJECTILE_HIT);
        assertEquals(200, probe.percent(new Part(living), direct, 220, 110), .0001);
        assertEquals(110, probe.percent(new Part(living), direct, 220, 110));
        living.world.isClientSide = true;
        assertEquals(110, probe.livingApply(new DirectSource(.6F, .04F), 220, 110));
    }

    @Test void actualSiloProfileUsesSpecialArmorAndNativeEightHundredHealthWithoutASecondHealthWriter() throws Exception {
        var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/nuclear_silo.json"))).getAsJsonObject();
        assertEquals(800,json.get("MaxHealth").getAsInt());
        String source=ROOT+"artillery/silo/NuclearSiloEntity",target=ROOT+"compat/armorprobe/SiloHull";
        var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in).accept(node,ClassReader.SKIP_FRAMES);}
        assertTrue(node.interfaces.contains(Type.getInternalName(DnvPhysicalArmor.class)));
        assertFalse(node.methods.stream().anyMatch(m->m.name.equals("setHealth")||m.name.equals("getMaxHealth")),"Native SBW owns health and configured maximum");
        node.name=target;node.superName=Type.getInternalName(Vehicle.class);node.interfaces=List.of(Type.getInternalName(DnvPhysicalArmor.class));
        node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;node.fields.clear();node.methods.removeIf(m->!m.name.equals("ordinaryArmorReduction"));
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);node.accept(writer);
        var ctor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,Type.getInternalName(Vehicle.class),"<init>","()V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();writer.visitEnd();
        Object silo=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.')).getConstructor().newInstance();
        assertEquals(.8F,((DnvPhysicalArmor)silo).ordinaryArmorReduction());
        var probe=new Probe();assertEquals(20,probe.apply((Vehicle)silo,new Source(new Bullet(),Keys.PROJECTILE_HIT),100),.001);
        assertEquals(20,probe.apply((Vehicle)silo,new Source(null,Keys.CUSTOM_EXPLOSION),100),.001);
        assertEquals(100,probe.apply((Vehicle)silo,new Source(null,Keys.SPECIAL,Keys.CUSTOM_EXPLOSION),100),.001);
        assertEquals(100,probe.apply((Vehicle)silo,new Source(null,Keys.LASER),100),.001);
    }

    @Test void nativeAndLightArmorReceiveOneDirectAdjustmentWhileExplosionKeepsItsOwnDamage() throws Exception {
        var probe = new Probe();
        var bullet = new Bullet();
        probe.mark(bullet, .60F);
        assertEquals(68, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 20), .001);
        assertEquals(88, probe.apply(new LightHull(), new Source(bullet, Keys.PROJECTILE_HIT), 20), .001);
        assertEquals(20, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_EXPLOSION, Keys.IS_EXPLOSION), 20), .001);
        assertEquals(70, probe.apply(new LightHull(), new Source(bullet, Keys.PROJECTILE_EXPLOSION, Keys.IS_EXPLOSION), 20), .001);
        assertEquals(68, probe.apply(new Vehicle(), new DirectSource(.60F), 20), .001);
        assertEquals(88, probe.apply(new LightHull(), new DirectSource(.60F), 20), .001);
    }

    @Test void nativeBulletDamageKindsReadTheMarkerButUnmarkedAndNonProjectileSourcesDoNot() throws Exception {
        var probe = new Probe();
        for (Key kind : List.of(Keys.PROJECTILE_HIT, Keys.PROJECTILE_HIT_HEADSHOT,
                Keys.GUN_FIRE, Keys.GUN_FIRE_HEADSHOT, Keys.GUN_FIRE_ABSOLUTE, Keys.GUN_FIRE_HEADSHOT_ABSOLUTE)) {
            var bullet = new Bullet();
            assertEquals(20, probe.apply(new Vehicle(), new Source(bullet, kind), 20), .001);
            probe.mark(bullet, .10F);
            assertEquals(28, probe.apply(new Vehicle(), new Source(bullet, kind), 20), .001, kind.id());
        }
        var vehicle = new Vehicle();
        probe.mark(vehicle, .70F);
        assertEquals(20, probe.apply(new Vehicle(), new Source(vehicle, Keys.GUN_FIRE), 20), .001);
    }

    @Test void immunityVulnerabilityAndSpecialDamageAreNotReplacedByPhysicalArmor() throws Exception {
        var probe = new Probe();
        var bullet = new Bullet();
        probe.mark(bullet, .70F);
        assertEquals(0, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 0));
        assertEquals(150, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 150));
        assertEquals(60, probe.apply(new LightHull(), new Source(bullet, Keys.SPECIAL, Keys.IS_EXPLOSION), 60));
        assertEquals(2, probe.apply(new LightHull(), new Source(bullet, Keys.LASER), 2));
    }

    @Test void bossOnlyAssemblyKeepsTheSharedBridgeWithoutLoadingArtilleryImplementations() throws Exception {
        String base = Files.readString(Path.of("src/main/resources/mixins.doggiesnewvehicle.json"));
        String artillery = Files.readString(Path.of("src/artillery/resources/mixins.dnvartillery.json"));
        assertTrue(base.contains("\"DnvVehicleArmorMixin\""));
        assertFalse(artillery.contains("ArmorMixin"));
        assertTrue(Files.isRegularFile(Path.of("src/main/java/" + ROOT + "compat/mixin/DnvVehicleArmorMixin.java")));
        assertFalse(Files.exists(Path.of("src/artillery/java/" + ROOT + "compat/mixin/ApocalypseRocketArmorMixin.java")));
    }

    @Test void translatedInputAmountAndSourceAreUsedOnceInsteadOfTheOriginalGunfireAmount() throws Exception {
        var probe = new Probe();
        var bullet = new Bullet(); probe.mark(bullet, .60F);
        assertEquals(136, probe.apply(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 200, 40), .001);
        assertEquals(176, probe.apply(new LightHull(), new Source(bullet, Keys.PROJECTILE_HIT), 200, 40), .001);
        assertEquals(140, probe.apply(new LightHull(), new Source(bullet, Keys.CUSTOM_EXPLOSION), 200, 40), .001);
        assertEquals(40, probe.apply(new Vehicle(), new Source(bullet, Keys.CUSTOM_EXPLOSION), 200, 40), .001);
        assertEquals(37, probe.apply(new LightHull(), new Source(bullet, Keys.SPECIAL, Keys.CUSTOM_EXPLOSION), 200, 37), .001);
    }

    @Test void nonVehicleComputeAndUnchangedResultsAreNotCancelled() throws Exception {
        var probe = new Probe();
        var bullet = new Bullet(); probe.mark(bullet, .60F);
        var untouched = probe.result(new NonVehicleHull(), new Source(bullet, Keys.PROJECTILE_HIT), 100, 20);
        assertEquals(20, untouched.getReturnValueF());
        assertFalse(untouched.isCancelled());
        assertFalse(probe.result(new Vehicle(), new Source(bullet, Keys.PROJECTILE_HIT), 100, 0).isCancelled());
        assertFalse(probe.result(new LightHull(), new Source(bullet, Keys.LASER), 100, 2).isCancelled());
    }

    public record Key(String id) { }
    public static class Keys {
        public static final Key SPECIAL = new Key("special"), LASER = new Key("laser"),
                IS_EXPLOSION = new Key("is_explosion"), IS_PROJECTILE = new Key("is_projectile"),
                CUSTOM_EXPLOSION = new Key("custom_explosion"), PROJECTILE_EXPLOSION = new Key("projectile_explosion"),
                VEHICLE_EXPLOSION = new Key("vehicle_explosion"), PROJECTILE_HIT = new Key("projectile_hit"),
                PROJECTILE_HIT_HEADSHOT = new Key("projectile_hit_headshot"), GRAPESHOT_HIT = new Key("grapeshot_hit"),
                PROJECTILE = new Key("projectile"), PROJECTILE_ABSOLUTE = new Key("projectile_absolute"),
                GUN_FIRE = new Key("gunfire"), GUN_FIRE_HEADSHOT = new Key("gunfire_headshot"),
                GUN_FIRE_ABSOLUTE = new Key("gunfire_absolute"), GUN_FIRE_HEADSHOT_ABSOLUTE = new Key("gunfire_headshot_absolute");
    }
    public static class Actor {
        private final CompoundTag data = new CompoundTag();
        public String type = "doggievehicle:dragon_bofors_twin";
        public TypePort getType() { return new TypePort(type); }
        final UUID id = UUID.randomUUID(); final World world = new World();
        public UUID getUUID() { return id; }
        public World level() { return world; }
        public CompoundTag getPersistentData() { return data; }
    }
    public static class World { public boolean isClientSide; public long getGameTime() { return 100; } }
    public static class Vehicle extends Actor { public float getMaxHealth() { return 3000; } }
    public static class Living extends Actor { public float getMaxHealth() { return 3000; } }
    public static class Part extends Actor { final Actor parent; Part(Actor parent) { this.parent = parent; } public Actor getParent() { return parent; } }
    public static class Bullet extends Actor { }
    public static class Shell extends Bullet { public int getLife() { return 200; } public int getSyncedTick() { return 0; } }
    public static class SmallShell extends Shell { }
    public static class LargeShell extends Shell { }
    public static class NativeBullet extends Bullet { public int getLife() { return 200; } }
    public record TypePort(String id) {
        public static net.minecraft.resources.ResourceLocation getKey(TypePort type) {
            return net.minecraft.resources.ResourceLocation.tryParse(type.id);
        }
    }
    public static class Prop { public static final Prop SHELL_TYPE = new Prop(); }
    public static class Item { }
    public static class GunData {
        public Item item = new Item(); public String shellType;
        public Object get(Prop prop) { return shellType; }
    }
    public static class Shot {
        public Actor ammoSupplier; public GunData data = new GunData();
    }
    public static class RegistryPorts { public static final ItemRegistry ITEM = new ItemRegistry(); }
    public static class ItemRegistry {
        public net.minecraft.resources.ResourceLocation getKey(Object item) {
            return net.minecraft.resources.ResourceLocation.tryParse("other:item");
        }
    }
    public static class LightHull extends Vehicle implements DnvPhysicalArmor {
        @Override public float ordinaryArmorReduction() { return .30F; }
    }
    public static class MediumHull extends Vehicle implements DnvPhysicalArmor { public float ordinaryArmorReduction() { return .55F; } }
    public static class HeavyHull extends Vehicle implements DnvPhysicalArmor { public float ordinaryArmorReduction() { return .75F; } }
    public static class NonVehicleHull extends Actor implements DnvPhysicalArmor {
        @Override public float ordinaryArmorReduction() { return .30F; }
    }
    public static class Source {
        private final Actor direct;
        private final Set<Key> keys;
        Source(Actor direct, Key... keys) { this.direct = direct; this.keys = Set.of(keys); }
        public Actor getDirectEntity() { return direct; }
        public boolean is(Key key) { return keys.contains(key); }
    }
    public static class DirectSource extends Source {
        private final float fraction;
        private final float percent; private final Set<UUID> claimed = new HashSet<>();
        DirectSource(float fraction) { this(fraction, 0); }
        DirectSource(float fraction, float percent) { super(null); this.fraction = fraction; this.percent = percent; }
        public float fraction() { return fraction; }
        public float percent() { return percent; }
        public boolean claim(UUID id) { return claimed.add(id); }
    }
    private static final class Probe extends ClassLoader {
        private final Map<String, String> names = new HashMap<>();
        private final Class<?> tagged;
        private final Class<?> percent;
        private final Object living;
        private final Object bridge;
        Probe() throws Exception {
            super(DnvVehicleArmorExecutionTest.class.getClassLoader());
            for (String type : List.of("core/DnvArmorRules", "core/DnvTaggedProjectileArmor", "core/DnvPercentHits", "armored/BlizzardColdDamage", "compat/mixin/DnvLivingPercentDamageMixin", "compat/mixin/DnvVehicleArmorMixin", "compat/DnvTurretShells"))
                names.put(ROOT + type, ROOT + "compat/armorprobe/" + type.substring(type.lastIndexOf('/') + 1));
            for (var entry : Map.ofEntries(
                    Map.entry("net/minecraft/world/entity/Entity", Actor.class),
                    Map.entry("net/minecraft/world/entity/EntityType", TypePort.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile", Shell.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/SmallCannonShellEntity", SmallShell.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/CannonShellEntity", LargeShell.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity", NativeBullet.class),
                    Map.entry("com/atsuishio/superbwarfare/data/gun/ShootParameters", Shot.class),
                    Map.entry("com/atsuishio/superbwarfare/data/gun/GunData", GunData.class),
                    Map.entry("com/atsuishio/superbwarfare/data/gun/GunProp", Prop.class),
                    Map.entry("net/minecraft/world/item/Item", Item.class),
                    Map.entry("net/minecraft/core/registries/BuiltInRegistries", RegistryPorts.class),
                    Map.entry("net/minecraft/core/DefaultedRegistry", ItemRegistry.class),
                    Map.entry("net/minecraft/world/entity/LivingEntity", Living.class),
                    Map.entry("net/minecraft/world/level/Level", World.class),
                    Map.entry("net/minecraftforge/entity/PartEntity", Part.class),
                    Map.entry("net/neoforged/neoforge/entity/PartEntity", Part.class),
                    Map.entry(ROOT + "core/DnvDirectHitSource", DirectSource.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Vehicle.class),
                    Map.entry("net/minecraft/world/entity/projectile/Projectile", Bullet.class),
                    Map.entry("net/minecraft/world/damagesource/DamageSource", Source.class),
                    Map.entry("net/minecraft/resources/ResourceKey", Key.class), Map.entry("net/minecraft/tags/TagKey", Key.class),
                    Map.entry("net/minecraft/tags/DamageTypeTags", Keys.class),
                    Map.entry("com/atsuishio/superbwarfare/init/ModDamageTypes", Keys.class),
                    Map.entry("com/atsuishio/superbwarfare/init/ModTags$DamageTypes", Keys.class),
                    Map.entry(ROOT + "core/DnvArmorDamageTags", Keys.class),
                    Map.entry(ROOT + "core/DnvDirectArmorPenetration", DirectSource.class)).entrySet())
                names.put(entry.getKey(), Type.getInternalName(entry.getValue()));
            tagged = loadClass(names.get(ROOT + "core/DnvTaggedProjectileArmor").replace('/', '.'));
            percent = loadClass(names.get(ROOT + "core/DnvPercentHits").replace('/', '.'));
            living = loadClass(names.get(ROOT + "compat/mixin/DnvLivingPercentDamageMixin").replace('/', '.')).getConstructor().newInstance();
            bridge = loadClass(names.get(ROOT + "compat/mixin/DnvVehicleArmorMixin").replace('/', '.')).getConstructor().newInstance();
        }
        void mark(Actor projectile, float fraction) throws Exception { tagged.getMethod("mark", Actor.class, float.class).invoke(null, projectile, fraction); }
        void markShot(Actor shell, String sourceId, String shellType) throws Exception {
            var shot = new Shot(); shot.ammoSupplier = new Vehicle(); shot.ammoSupplier.type = sourceId; shot.data.shellType = shellType;
            loadClass(names.get(ROOT + "compat/DnvTurretShells").replace('/', '.'))
                    .getMethod("mark", Actor.class, Shot.class).invoke(null, shell, shot);
        }
        void markPercent(Actor projectile, float fraction) throws Exception { percent.getMethod("mark", Actor.class, float.class).invoke(null, projectile, fraction); }
        float cold(Object target, boolean mode2) throws Exception {
            return (float)loadClass(names.get(ROOT+"armored/BlizzardColdDamage").replace('/', '.')).getMethod("amount", Object.class, boolean.class).invoke(null, target, mode2);
        }
        float percent(Actor target, Source source, float incoming, float armored) throws Exception {
            return (float) percent.getMethod("apply", Actor.class, Source.class, float.class, float.class).invoke(null, target, source, incoming, armored);
        }
        float livingApply(Source source, float incoming, float armored) throws Exception {
            var result = new CallbackInfoReturnable<Float>("armor", true, armored);
            living.getClass().getMethod("dnv$percent", Source.class, float.class, CallbackInfoReturnable.class).invoke(living, source, incoming, result);
            return result.getReturnValueF();
        }
        float apply(Actor target, Source source, float nativeDamage) throws Exception {
            return apply(target, source, 100F, nativeDamage);
        }
        float apply(Actor target, Source source, float incoming, float nativeDamage) throws Exception {
            return result(target, source, incoming, nativeDamage).getReturnValueF();
        }
        CallbackInfoReturnable<Float> result(Actor target, Source source, float incoming, float nativeDamage) throws Exception {
            var result = new CallbackInfoReturnable<Float>("compute", true, nativeDamage);
            bridge.getClass().getMethod("dnv$vehicleArmor", Actor.class, Source.class, float.class, CallbackInfoReturnable.class)
                    .invoke(bridge, target, source, incoming, result);
            return result;
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String original = names.entrySet().stream().filter(e -> e.getValue().equals(name.replace('.', '/')))
                    .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new ClassNotFoundException(name));
            try (var input = getParent().getResourceAsStream(original + ".class")) {
                var node = new ClassNode(); new ClassReader(input).accept(node, 0);
                if (original.endsWith("DnvTurretShells"))
                    node.methods.removeIf(m -> !Set.of("<init>", "<clinit>", "mark", "lightTurret", "vehicleSource", "track", "deadline").contains(m.name));
                if (original.endsWith("DnvLivingPercentDamageMixin")) {
                    node.superName = Type.getInternalName(Living.class);
                    for (var method : node.methods) if (method.name.equals("<init>"))
                        for (var instruction : method.instructions) if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals("java/lang/Object")) call.owner = node.superName;
                }
                node.access = Opcodes.ACC_PUBLIC;
                for (var method : node.methods) method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                var writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
                byte[] bytes = writer.toByteArray(); return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception exception) { throw new ClassNotFoundException(name, exception); }
        }
    }
}

package com.liucodex.libertad.ship;

import com.liucodex.libertad.naval.AirDefenseRules;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.SecondaryDirector.Relation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import org.joml.Matrix4d;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the compiled AA owner with bounded world/inventory ports and the real ship geometry. */
public final class RuntimeAirDefenseCheck {
    public static void main(String[] args) throws Exception {
        RuntimePhysicsCheck.bootstrapStandalone();
        net.minecraft.core.registries.BuiltInRegistries.FLUID.bindTags(Map.of(FluidTags.WATER, List.of(
                net.minecraft.core.registries.BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))));
        var probe = new Probe();
        var air = new Aircraft(25, 25, 110);
        var ground = new Target(45, 2, 100);
        probe.world.targets.addAll(List.of(ground, air));
        probe.run(200, false);
        require(probe.ship.inventory.spent == 0 && probe.world.queries == 0, "disabled AA scanned or fired");
        probe.run(200, true);
        require(air.hits > 0 && ground.hits == 0, "air priority did not reach actual damage");
        require(probe.anyPitchAbove(5), "tracking an aircraft never elevated the AA barrels");
        int hits = air.hits, spent = probe.ship.inventory.spent, sounds = probe.world.sounds;
        double damage = air.damageTaken;
        int muzzles = probe.world.particles;
        probe.run(40, true);
        require(air.hits-hits == 5 && probe.ship.inventory.spent-spent == 5 && probe.world.sounds-sounds == 5,
                "one assigned zone must fire once every eight ticks");
        require(air.damageTaken - damage == 225 && probe.world.particles - muzzles == 15,
                "each virtual pulse must deal 45 damage and flash all three guns");
        require(probe.world.maxQueries <= 8 && probe.world.maxReturned <= 48, "discovery exceeded its bounds");
        var occluded = new Probe();
        var blockedAir = new Aircraft(25, 25, 110);
        var exposedGround = new Target(45, 2, 100);
        occluded.world.targets.addAll(List.of(blockedAir, exposedGround));
        occluded.world.blockAimAbove = 10;
        occluded.run(240, true);
        require(blockedAir.hits == 0 && exposedGround.hits > 0, "blocked air contact starved a clear surface target");
        occluded.world.blockAimAbove = Double.POSITIVE_INFINITY;
        occluded.run(120, true);
        require(blockedAir.hits > 0, "air priority did not recover after the obstruction cleared");
        air.hostile = false;
        probe.run(60, true);
        require(air.hits == hits+5 && ground.hits > 0, "team change failed to release aircraft for surface fallback");
        hits = ground.hits;
        probe.ship.energy = 0;
        probe.run(20, true);
        require(ground.hits == hits, "power loss did not stop firing");
        probe.ship.energy = 1;
        probe.ship.inventory.ammo = 0;
        probe.run(20, true);
        require(ground.hits == hits, "empty inventory fired");
        probe.ship.inventory.ammo = 100;
        probe.run(200, true);
        require(ground.hits > hits, "resupply failed to resume");
        hits = ground.hits;
        probe.world.waterY = 30;
        probe.run(40, true);
        require(ground.hits == hits, "submerged targets received damage");
        probe.world.waterY = Double.NEGATIVE_INFINITY;
        probe.world.wall = true;
        probe.run(40, true);
        require(ground.hits == hits, "solid or water ray obstruction allowed damage");
        probe.world.wall = false;
        probe.world.loaded = false;
        probe.run(40, true);
        require(ground.hits == hits, "unloaded path admitted damage");
        var crowded = new Probe();
        crowded.world.waterY = 3;
        for (int i = 0; i < 30; i++) crowded.world.targets.add(new Target(20+i*.1, 0, 0));
        var exposed = new Target(65, 10, 0);
        crowded.world.targets.add(exposed);
        crowded.run(400, true);
        require(exposed.hits > 0, "ineligible submerged contacts starved a visible surface target");
        require(crowded.world.targets.stream().filter(e -> e != exposed).allMatch(e -> e.hits == 0),
                "submerged crowd received damage");
        var allZones = new Probe();
        for (int i = 0; i < 4; i++) {
            final int zone = i;
            var mounts = ShipModel.get().aaMounts.stream().filter(m -> m.zone() == zone).toList();
            Vec3 center = mounts.stream().map(ShipModel.AaMount::pivot).reduce(Vec3.ZERO, Vec3::add).scale(1./mounts.size());
            allZones.world.targets.add(new Aircraft(center.x + ((i&1)==0 ? 60 : -60), center.y+20,
                    center.z+(i<2 ? 60 : -60)));
        }
        allZones.run(300,true);
        require(allZones.world.targets.stream().allMatch(e -> e.hits > 0), "one of the four actual sectors cannot engage");
        int total = allZones.ship.inventory.spent;
        int beforeSounds = allZones.world.sounds;
        allZones.run(40,true);
        require(allZones.ship.inventory.spent-total==20 && allZones.world.sounds-beforeSounds==20,
                "four active sectors must each fire five times in forty ticks");
        allZones.ship.inventory.ammo = 0;
        allZones.ship.creativeAmmo = true;
        beforeSounds = allZones.world.sounds;
        total = allZones.ship.inventory.spent;
        allZones.run(40,true);
        require(allZones.ship.inventory.spent==total && allZones.world.sounds-beforeSounds==20,
                "creative supply must serve all four sectors without consuming an item");
        require(allZones.world.maxRays <= 1, "staggered zones exceeded one world ray per tick");
        var overlap = new Probe();
        var shared = new Aircraft(65, 20, -30);
        overlap.world.targets.add(shared);
        overlap.run(200, true);
        hits = shared.hits;
        damage = shared.damageTaken;
        overlap.run(40, true);
        require(shared.hits - hits == 10 && shared.damageTaken - damage == 450,
                "both eligible zones must keep independent 45-point pulses on one aircraft");
        var creative = new Probe();
        creative.ship.inventory.ammo = 0;
        creative.ship.creativeAmmo = true;
        var nearby = new Aircraft(25, 25, 110);
        creative.world.targets.add(nearby);
        creative.run(200, true);
        require(nearby.hits > 0 && creative.ship.inventory.spent == 0,
                "creative ammo box alone must supply automatic AA without being consumed");
        hits = nearby.hits;
        sounds = creative.world.sounds;
        creative.run(40, true);
        require(nearby.hits - hits == 5 && creative.world.sounds - sounds == 5,
                "creative supply must preserve the eight-tick cadence");
        creative.ship.creativeAmmo = false;
        hits = nearby.hits;
        creative.run(20, true);
        require(nearby.hits == hits, "removed creative supply kept firing from a stale grant");
        creative.ship.inventory.ammo = 20;
        creative.run(100, true);
        require(nearby.hits > hits && creative.ship.inventory.spent > 0, "finite ammo did not take over");
        creative.ship.creativeAmmo = true;
        int finiteAmmo = creative.ship.inventory.ammo;
        creative.run(20, true);
        require(creative.ship.inventory.ammo == finiteAmmo, "creative supply consumed finite ammunition");
        hits = nearby.hits;
        creative.ship.energy = 0;
        creative.run(20, true);
        require(nearby.hits == hits, "creative ammunition bypassed the energy gate");
        creative.ship.energy = 1;
        creative.run(20, false);
        require(nearby.hits == hits, "creative ammunition bypassed the mode gate");
        checkSuperstructure("forward funnel", 0, 13, 12.9);
        checkSuperstructure("aft funnel", 0, 13, -23.3);
        checkSuperstructure("bridge roof", 0, 12.2, 22.2);
        System.out.println("PASS: compiled AA 8-tick/45-point pulses, shared contacts, one ray per tick, roof and funnel defense, inventory and visibility");
    }

    private static void checkSuperstructure(String location, double x, double y, double z) throws Exception {
        var probe = new Probe();
        var boarder = new Target(x, y, z) {
            @Override public AABB getBoundingBox() {
                return new AABB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
            }
            @Override public double getEyeY() { return y + 1.62; }
        };
        probe.world.targets.add(boarder);
        probe.run(200, true);
        require(boarder.hits > 0, "no AA gun could engage a boarder at " + location);
        int hits = boarder.hits;
        probe.run(40, true);
        require(boarder.hits - hits >= 10, "the visible zones did not converge on " + location);
        probe.world.wall = true;
        hits = boarder.hits;
        probe.run(40, true);
        require(boarder.hits == hits, "close defense fired through a block at " + location);
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private static final class Probe {
        static Class<?> adapterType;
        final Ship ship = new Ship();
        final World world = new World();
        final Object owner;
        final Method tick;
        final Angles[] poses = new Angles[24];
        Probe() throws Exception {
            Arrays.fill(poses, new Angles(0, 0));
            String prefix = "com/liucodex/libertad/ship/ShipAirDefense";
            var names = new HashMap<String, String>();
            var owners = List.of(prefix, prefix+"$Zone", prefix+"$Contact", prefix+"$Solution",
                    "com/liucodex/libertad/ship/ShipMuzzleEffects");
            for (String owner : owners) names.put(owner, owner + "Probe");
            Map.ofEntries(
                    Map.entry("net/minecraft/server/level/ServerLevel", World.class),
                    Map.entry("net/minecraft/world/entity/Entity", Target.class),
                    Map.entry("net/minecraft/server/level/ServerPlayer", Player.class),
                    Map.entry("net/minecraft/world/entity/player/Player", Player.class),
                    Map.entry("net/minecraft/world/entity/FlyingMob", AirMob.class),
                    Map.entry("net/minecraft/world/entity/projectile/Projectile", Bullet.class),
                    Map.entry("net/minecraft/world/level/ClipContext", Ray.class),
                    Map.entry("net/minecraft/world/item/ItemStack", Stack.class),
                    Map.entry("net/minecraft/world/item/Item", Item.class),
                    Map.entry("net/minecraftforge/registries/RegistryObject", Ref.class),
                    Map.entry("com/liucodex/libertad/ship/LibertadEntity", Ship.class),
                    Map.entry("com/liucodex/libertad/Libertad", Registered.class),
                    Map.entry("com/liucodex/libertad/ship/ShipFireControl", Search.class),
                    Map.entry("com/liucodex/libertad/compat/sbw/LoadedPath", Path.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Aircraft.class),
                    Map.entry("com/atsuishio/superbwarfare/inventory/handler/VehicleContainerHandler", Inventory.class),
                    Map.entry("com/atsuishio/superbwarfare/data/vehicle/DefaultVehicleData", Vehicle.class),
                    Map.entry("com/atsuishio/superbwarfare/data/vehicle/subdata/EngineType", Engine.class),
                    Map.entry("com/atsuishio/superbwarfare/init/ModItems", Items.class),
                    Map.entry("com/atsuishio/superbwarfare/init/ModDamageTypes", Damage.class),
                    Map.entry("com/atsuishio/superbwarfare/client/particle/CannonMuzzleFlareOption", Flare.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/ParticleTool", Particles.class)
            ).forEach((name, type) -> names.put(name, Type.getInternalName(type)));
            var bytes = new HashMap<String, byte[]>();
            for (String owner : owners) {
                var writer = new ClassWriter(0);
                try (var in = RuntimeAirDefenseCheck.class.getClassLoader().getResourceAsStream(owner+".class")) {
                    new ClassReader(Objects.requireNonNull(in)).accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
                }
                bytes.put(names.get(owner).replace('/', '.'), writer.toByteArray());
            }
            if (adapterType == null) {
                var lookup = java.lang.invoke.MethodHandles.lookup();
                for (byte[] data : bytes.values()) lookup.defineClass(data);
                adapterType = Class.forName(names.get(prefix).replace('/', '.'));
            }
            var type = adapterType;
            var constructor = type.getDeclaredConstructor(Ship.class, ShipModel.class);
            constructor.setAccessible(true);
            owner = constructor.newInstance(ship, ShipModel.get());
            tick = type.getDeclaredMethod("tick", World.class, boolean.class, Player.class, Function.class, Angles[].class);
            tick.setAccessible(true);
        }
        void run(int count, boolean enabled) throws Exception {
            for (int i = 0; i < count; i++) {
                world.queries = 0;
                world.rays = 0;
                tick.invoke(owner, world, AirDefenseRules.enabled(true, false, enabled && ship.energy>0), null,
                        (Function<Target, Relation>) e -> e.hostile ? Relation.HOSTILE : Relation.FRIENDLY, poses);
                world.maxQueries = Math.max(world.maxQueries, world.queries);
                world.maxRays = Math.max(world.maxRays, world.rays);
                world.now++;
            }
        }
        boolean anyPitchAbove(float degrees) throws Exception {
            var read = owner.getClass().getDeclaredMethod("pitch", int.class);
            read.setAccessible(true);
            for (int i=0;i<4;i++) if ((float)read.invoke(owner,i)>degrees) return true;
            return false;
        }
    }

    public static class Target {
        final UUID id = UUID.randomUUID();
        Vec3 position;
        boolean hostile = true;
        int hits;
        double damageTaken;
        public Target(double x, double y, double z) { position = new Vec3(x,y,z); }
        public UUID getUUID() { return id; }
        public Target getRootVehicle() { return this; }
        public AABB getBoundingBox() { return new AABB(position, position.add(1,1,1)); }
        public Vec3 position() { return position; }
        public BlockPos blockPosition() { return BlockPos.containing(position); }
        public double getEyeY() { return position.y+.8; }
        public boolean onGround() { return true; }
        public boolean hurt(DamageSource source, float amount) { hits++; damageTaken += amount; return true; }
    }
    public static class Aircraft extends Target {
        public Aircraft(double x,double y,double z) { super(x,y,z); }
        public Vehicle computed() { return new Vehicle(); }
        @Override public boolean onGround() { return false; }
    }
    public static class Player extends Target {
        public Player() { super(0,0,0); }
        public boolean isFallFlying() { return false; }
    }
    public static class AirMob extends Target { public AirMob() { super(0,0,0); } }
    public static class Bullet extends Target { public Bullet() { super(0,0,0); } }
    public static class Ship extends Target {
        final Inventory inventory = new Inventory();
        int energy = 1;
        boolean creativeAmmo;
        public Ship() { super(0,0,0); }
        public Inventory getInventory() { return inventory; }
        public int getBlockY() { return 0; }
        public Matrix4d getVehicleTransform(float partial) { return new Matrix4d(); }
        public int getEnergy() { return energy; }
        public boolean hasCreativeAmmoBoxCached() { return creativeAmmo; }
    }
    public static class Item {}
    public static class Ref {
        final Object value;
        public Ref() { this.value = Items.AP; }
        public Ref(Object value) { this.value = value; }
        public Object get() { return value; }
    }
    public static class Registered {
        public static final Ref AIR_DEFENSE_FIRE = new Ref(SoundEvent.createFixedRangeEvent(new ResourceLocation("libertad","air_defense_fire"),200));
    }
    public static class Items { static final Item AP = new Item(); public static final Ref SMALL_SHELL_AP = new Ref(); }
    public static class Stack {
        final boolean present;
        Stack(boolean present) { this.present=present; }
        public boolean is(Item item) { return present && item==Items.AP; }
    }
    public static class Inventory {
        int ammo=1000,spent;
        public Stack getStackInSlot(int slot) { return new Stack(ammo>0); }
        public int getSlots() { return 1; }
        public Stack extractItem(int slot,int count,boolean simulate) {
            if (ammo<=0) return new Stack(false);
            if (!simulate) { ammo--; spent++; }
            return new Stack(true);
        }
    }
    public enum Engine { HELICOPTER,AIRCRAFT,AIRSHIP }
    public static class Vehicle {
        public Engine getEngineType() { return Engine.AIRCRAFT; }
    }
    public static class Damage {
        public static DamageSource causeProjectileHitDamage(RegistryAccess access,Target direct,Target attacker) { return null; }
    }
    public record Flare(int color, int life, float fade, int frames, float growth) implements ParticleOptions {
        public ParticleType<?> getType() { throw new UnsupportedOperationException(); }
        public void writeToNetwork(FriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
        public String writeToString() { throw new UnsupportedOperationException(); }
    }
    public static class Particles {
        public static void sendParticle(World world, ParticleOptions option, double x, double y, double z,
                                        int count, double dx, double dy, double dz, double speed, boolean distant) {
            require(option instanceof Flare flare && flare.life() == 2 && flare.growth() == 0,
                    "each AA barrel must use the short SBW flare profile");
            require(count == 0 && distant && speed > 0 && Math.abs(dx*dx + dy*dy + dz*dz - 1) < 1e-6,
                    "AA must emit a single directed flare visible from the chase camera");
            world.particles++;
        }
    }
    public static class Ray {
        final Vec3 from,to;
        public Ray(Vec3 from,Vec3 to,ClipContext.Block blocks,ClipContext.Fluid fluid,Target target) {
            require(fluid==ClipContext.Fluid.WATER,"AA ray must reject water"); this.from=from;this.to=to;
        }
    }
    public static class Search {
        public static Iterator<BlockPos.MutableBlockPos> searchColumns(Vec3 origin,double radius) {
            return ShipFireControl.searchColumns(origin,radius);
        }
    }
    public static class Path {
        public static double limit(World world,Vec3 from,Vec3 to) {
            return com.liucodex.libertad.compat.sbw.LoadedPath.limit(from,to,(x,z)->world.loaded);
        }
    }
    public static class World implements BlockGetter {
        long now;
        int queries,maxQueries,maxReturned,sounds,particles,rays,maxRays;
        boolean loaded=true,wall;
        double waterY=Double.NEGATIVE_INFINITY;
        double blockAimAbove=Double.POSITIVE_INFINITY;
        final List<Target> targets=new ArrayList<>();
        public long getGameTime() { return now; }
        public boolean isPositionEntityTicking(BlockPos pos) { return loaded; }
        public boolean hasChunkAt(BlockPos pos) { return loaded; }
        public Target getEntity(UUID id) { return targets.stream().filter(e->e.id.equals(id)).findFirst().orElse(null); }
        public void getEntities(EntityTypeTest<?,?> type,AABB box,Predicate<Target> filter,List<Target> out,int maximum) {
            queries++;
            for(Target target:targets) if(out.size()<maximum && box.intersects(target.getBoundingBox()) && filter.test(target)) out.add(target);
            maxReturned=Math.max(maxReturned,out.size());
        }
        public RegistryAccess registryAccess() { return null; }
        public void playSound(Player player,double x,double y,double z,SoundEvent sound,SoundSource category,float volume,float pitch) {
            require(volume == .75F && sound.getRange(volume) == 200, "AA report must be quieter and stay within 200 blocks");
            sounds++;
        }
        public WorldBorder getWorldBorder() { return new WorldBorder(); }
        public BlockHitResult clip(Ray ray) {
            rays++;
            return wall || ray.to.y > blockAimAbove ? new BlockHitResult(ray.to,Direction.UP,BlockPos.containing(ray.to),false)
                    : BlockHitResult.miss(ray.to,Direction.UP,BlockPos.containing(ray.to));
        }
        public FluidState getFluidState(BlockPos pos) { return pos.getY()<waterY ? Fluids.WATER.defaultFluidState() : Fluids.EMPTY.defaultFluidState(); }
        public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
}

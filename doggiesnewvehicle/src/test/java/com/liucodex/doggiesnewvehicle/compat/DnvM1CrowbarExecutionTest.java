package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;

/** Runs the production interaction, readiness and queued guard. Native GunData itself has separate exact-bytecode coverage. */
class DnvM1CrowbarExecutionTest {
    private static final String ROOT = "com/liucodex/doggiesnewvehicle/";
    private static final String SBW = "com/atsuishio/superbwarfare/";
    @Test void singleMainHandClickQueuesOneShotAndTheScopeCannotLeak() throws Exception {
        var f = new Fixture();
        assertEquals(InteractionResult.SUCCESS, f.click(InteractionHand.MAIN_HAND));
        assertNull(f.host.crowbarActor); assertEquals(1, f.host.queue.size());
        assertEquals(InteractionResult.FAIL, f.click(InteractionHand.OFF_HAND));
        assertEquals(InteractionResult.FAIL, f.click(InteractionHand.MAIN_HAND));
        assertEquals(1, f.host.queue.size());
        f.host.queue.remove(0).run(); assertEquals(1, f.host.shots); assertNull(f.host.crowbarActor);
        assertFalse(f.host.canShoot(f.actor), "Temporary crowbar authority must not survive queue execution");
    }
    @Test void rejectedOrRevokedPermissionPowerAmmoAndReachNeverEmitEffects() throws Exception {
        for (int scenario = 0; scenario < 8; scenario++) {
            var f = new Fixture();
            switch (scenario) {
                case 0 -> f.host.owner = false;
                case 1 -> f.host.powered = false;
                case 2 -> f.host.gun.ready = false;
                case 3 -> f.actor.alive = false;
                case 4 -> f.actor.spectator = true;
                case 5 -> f.actor.distance = 65;
                case 6 -> f.actor.stack.crowbar = false;
                case 7 -> f.host.world.registered = null;
            }
            assertEquals(InteractionResult.FAIL, f.click(InteractionHand.MAIN_HAND));
            assertTrue(f.host.queue.isEmpty()); assertEquals(0, f.host.shots);
        }
        var f = new Fixture(); f.click(InteractionHand.MAIN_HAND); f.host.powered = false;
        f.host.queue.remove(0).run(); assertEquals(0, f.host.shots); assertNull(f.host.crowbarActor);
        f.host.powered = true; f.host.world.tick++;
        f.click(InteractionHand.MAIN_HAND); f.actor.stack.crowbar = false;
        f.host.queue.remove(0).run(); assertEquals(0, f.host.shots);
    }
    @Test void scopeAlwaysRestoresItsCallerEvenWhenTheNativeActionFails() throws Exception {
        var f = new Fixture(); f.host.fail = true;
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> f.click(InteractionHand.MAIN_HAND));
        assertNull(f.host.crowbarActor);
    }
    private static class Fixture {
        final Host host; final Actor actor = new Actor(); final Class<?> type;
        Fixture() throws Exception {
            var names = new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names, SBW + "entity/vehicle/base/AutoAimableEntity", Host.class);
            NuclearRecoveryExecutionTest.map(names, SBW + "entity/vehicle/base/VehicleEntity", Host.class);
            NuclearRecoveryExecutionTest.map(names, SBW + "data/gun/GunData", Gun.class);
            NuclearRecoveryExecutionTest.map(names, SBW + "init/ModTags$Items", Items.class);
            NuclearRecoveryExecutionTest.map(names, "net/minecraft/tags/TagKey", Object.class);
            for (String name : new String[]{"world/entity/player/Player", "world/entity/LivingEntity", "server/level/ServerPlayer"})
                NuclearRecoveryExecutionTest.map(names, "net/minecraft/" + name, Actor.class);
            NuclearRecoveryExecutionTest.map(names, "net/minecraft/world/entity/Entity", Body.class);
            NuclearRecoveryExecutionTest.map(names, "net/minecraft/world/item/ItemStack", Stack.class);
            NuclearRecoveryExecutionTest.map(names, "net/minecraft/world/level/Level", World.class);
            NuclearRecoveryExecutionTest.map(names, "net/minecraft/server/level/ServerLevel", World.class);
            NuclearRecoveryExecutionTest.map(names, ROOT + "entity/DnvDragonTurretProfile", Profile.class);
            NuclearRecoveryExecutionTest.map(names, ROOT + "entity/DnvM1IndicatorAim", Aim.class);
            NuclearRecoveryExecutionTest.map(names, ROOT + "compat/DnvM1RemoteCrew", Crew.class);
            type = NuclearRecoveryExecutionTest.extract(ROOT + "entity/DnvM1YunlongEntity",
                    Set.of("onCrowbarInteract", "crowbarReady", "withCrowbarActor", "guardNativeShot", "canShoot"), names, Host.class, false);
            host = (Host)type.getConstructor().newInstance(); host.world.registered = host; actor.world = host.world;
        }
        Object click(InteractionHand hand) throws Exception {
            return type.getMethod("onCrowbarInteract", Stack.class, Actor.class, InteractionHand.class).invoke(host, actor.stack, actor, hand);
        }
    }
    public static class Body { public World world = new World(); public World level(){return world;}
        public UUID getUUID(){return new UUID(1,2);} }
    public static class Actor extends Body {public boolean alive=true,spectator; public double distance=16;public Stack stack=new Stack();
        public boolean isAlive(){return alive;} public boolean isSpectator(){return spectator;}
        public double distanceToSqr(Body body){return distance;} public Stack getMainHandItem(){return stack;}
        public boolean isShiftKeyDown(){return false;} }
    public static class Stack {public boolean crowbar=true;public boolean is(Object tag){return crowbar;} }
    public static class Items {public static final Object TOOLS_CROWBAR=new Object();}
    public static class World {public boolean isClientSide;public long tick=10;public Body registered;
        public long getGameTime(){return tick;} public Body getEntity(UUID id){return registered;} }
    public static class Profile {public String entity="m1_yunlong";}
    public static class Gun {public boolean ready=true;public boolean canShoot(Body supply){return ready;} }
    public static class Aim {public UUID actor(){return new UUID(1,2);} }
    public static class Crew {
        public static Runnable guardQueuedShot(Host host,Runnable work){return work;}
        public static boolean valid(Host host){return false;}
    }
    public static class Host extends Body {
        public Actor crowbarActor;public long lastCrowbarTick=Long.MIN_VALUE;public boolean nativeStateInvalid,owner=true,powered=true,fail;
        public Aim indicatorSubmitting,indicatorPending;public long mountedEpoch;public Gun gun=new Gun();
        public int shots;public List<Runnable> queue=new ArrayList<>();
        public Profile profile(){return new Profile();}public List<Body> getPassengers(){return List.of();}
        public boolean canBindIndicator(Actor actor){return owner;}public boolean hasOperationalPower(){return powered;}
        public Gun getGunData(String name){return gun;}public Body getAmmoSupplier(){return this;}
        public boolean mountedCrew(){return false;}public void runNativeShotScope(Runnable work){work.run();}
        public InteractionResult onCrowbarInteract(Stack stack,Actor actor,InteractionHand hand){return InteractionResult.PASS;}
        public void vehicleShoot(Actor actor,String name,UUID target,net.minecraft.world.phys.Vec3 point){
            if(fail)throw new IllegalStateException("native rejection");
            queue.add(guardNativeShot(()-> { if (canShoot(actor)) { shots++; gun.ready=false; } }));
        }
        public Runnable guardNativeShot(Runnable work){return work;}
        public int getSeatIndex(Body actor){return 0;}public boolean hasPassenger(Body actor){return false;}
        // Exact VehicleEntity.canShoot obtains GunData via the passenger's seat; an outside actor has none.
        public boolean canShoot(Actor actor){return false;}
        public boolean getLocked(){return false;}public boolean isDestroyed(){return false;}
        public boolean isRemoteOperator(Body actor){return false;}
        public boolean indicatorValid(Aim aim,Actor actor){return false;}
        public boolean indicatorAligned(Aim aim){return false;}
    }
}

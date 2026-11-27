package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvProjectilePath;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Executes production shot-local lifecycle and HE handling, with explicit native world/damage ports. */
class DnvTurretShellLifecycleTest {
    @Test void unchangedAdmissionDoesNotCancelAnotherAddonsNativeReturnHook() throws Exception {
        var names = new HashMap<String, String>();
        NuclearRecoveryExecutionTest.map(names, "com/atsuishio/superbwarfare/data/gun/ShootParameters", Shot.class);
        NuclearRecoveryExecutionTest.map(names, "com/liucodex/doggiesnewvehicle/compat/DnvSbwShotExecution", ShotPort.class);
        var hook = NuclearRecoveryExecutionTest.extract(
                "com/liucodex/doggiesnewvehicle/compat/mixin/GunShotAdmissionMixin",
                Set.of("dnv$admissionResult"), names, Object.class);
        var invoke = hook.getMethod("dnv$admissionResult", Shot.class, CallbackInfoReturnable.class);
        var mixin = hook.getConstructor().newInstance();
        for (boolean nativeResult : new boolean[] {false, true}) {
            var untouched = new CallbackInfoReturnable<>("shootBullet", true, nativeResult);
            invoke.invoke(mixin, new Shot(false), untouched);
            assertFalse(untouched.isCancelled());
            assertEquals(nativeResult, untouched.getReturnValueZ());
        }
        var rejected = new CallbackInfoReturnable<>("shootBullet", true, true);
        invoke.invoke(mixin, new Shot(true), rejected);
        assertTrue(rejected.isCancelled());
        assertFalse(rejected.getReturnValueZ());
    }

    public record Shot(boolean reject) {}
    public static class ShotPort {
        public static boolean bulletResult(Shot shot, boolean nativeResult) {
            return nativeResult && !shot.reject();
        }
    }

    @Test void loadedStepClipsAtFirstGapInEitherDirectionEvenWhenBothEndpointsAreLoaded() {
        var start = new Vec3(8, 64, 2);
        var end = new Vec3(42, 64, 2);
        var forward = DnvProjectilePath.limit(start, end, (x, z) -> x != 1);
        var reverse = DnvProjectilePath.limit(end, start, (x, z) -> x != 1);
        assertEquals(16, start.lerp(end, forward).x, .001);
        assertTrue(start.lerp(end, forward).x < 16);
        assertEquals(32, end.lerp(start, reverse).x, .001);
        assertTrue(end.lerp(start, reverse).x > 32);
    }

    @Test void loadedStepKeepsClearNegativePathAndRejectsCornerAdjacentInactiveChunk() {
        var start = new Vec3(-24, 64, -24);
        var end = new Vec3(-8, 64, -8);
        assertEquals(1, DnvProjectilePath.limit(start, end, (x, z) -> true));
        var limit = DnvProjectilePath.limit(start, end, (x, z) -> x != -1 || z != -2);
        assertEquals(.5, limit, .00001);
        assertTrue(start.lerp(end, limit).x < -16);
    }

    @Test void inactiveStartAndNonFiniteMotionHaveNoLegalNativeStep() {
        assertEquals(0, DnvProjectilePath.limit(Vec3.ZERO, new Vec3(10, 0, 0), (x, z) -> false));
        assertEquals(0, DnvProjectilePath.limit(Vec3.ZERO, new Vec3(Double.NaN, 0, 0),
                (x, z) -> fail("Invalid motion must not query chunks")));
    }

    @Test void blastQueriesItsInteriorAndChecksPastTheConfiguredRadius() {
        var point = new Vec3(9, 64, 9);
        assertTrue(DnvProjectilePath.blastLoaded(point, 4, (x, z) -> true));
        assertFalse(DnvProjectilePath.blastLoaded(point, 4, (x, z) -> x != 1 || z != 0));
        assertFalse(DnvProjectilePath.blastLoaded(point, 24, (x, z) -> x != 0 || z != 0));
        assertFalse(DnvProjectilePath.blastLoaded(point, Float.POSITIVE_INFINITY,
                (x, z) -> fail("Invalid blast must not query chunks")));
    }

    private Class<?> policy() throws Exception {
        var names=new HashMap<String,String>();
        for(String type:List.of("com/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile",
                "com/atsuishio/superbwarfare/entity/projectile/SmallCannonShellEntity"))
            NuclearRecoveryExecutionTest.map(names,type,Shell.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Actor.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity",Bullet.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/phys/EntityHitResult",Hit.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/init/ModDamageTypes",NativeDamage.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/tools/DamageHandlerKt",NativeDamage.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",ApPolicy.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/core/RegistryAccess",Registry.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/damagesource/DamageSource",Source.class);
        return NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/DnvTurretShells",
                Set.of("beforeTick","managed","track","deadline","directHe","directHeTarget","emptyApBlast"),names,null,false);
    }
    @Test void onlyMarkedZeroBlastApSkipsTheNativeExplosionWork() throws Exception {
        var method=policy().getMethod("emptyApBlast",Shell.class);var shell=new Shell();
        assertFalse((boolean)method.invoke(null,shell));
        shell.data.putBoolean("DnvSmallApImpact",true);assertTrue((boolean)method.invoke(null,shell));
        shell.damage=14;assertFalse((boolean)method.invoke(null,shell));
        shell.damage=0;shell.radius=1;assertFalse((boolean)method.invoke(null,shell));
    }
    @Test void tenThousandFrozenOrRepeatedlyReloadedShotsExpireWithoutAWorldScanOrNativeTick() throws Exception {
        var p=policy();var tick=p.getMethod("beforeTick",Actor.class);
        var shots=new ArrayList<Shell>();
        for(int i=0;i<10_000;i++){
            var shell=new Shell();shell.data.putLong("DnvTurretShellExpiry",201);shots.add(shell);
            shell.world.time=200;assertFalse((boolean)tick.invoke(null,shell));
            // Vanilla tickCount can stay frozen; the persisted absolute deadline does not move.
            shell.data=shell.data.copy();shell.world.time=202;assertTrue((boolean)tick.invoke(null,shell));
        }
        assertEquals(10_000,shots.stream().filter(s->s.removed).count());
        var nativeShell=new Shell();nativeShell.world.time=10_000;
        assertFalse((boolean)tick.invoke(null,nativeShell));assertFalse(nativeShell.removed);
        nativeShell.data.putLong("DnvTurretShellExpiry",1);nativeShell.world.isClientSide=true;
        assertFalse((boolean)tick.invoke(null,nativeShell));assertFalse(nativeShell.removed);
    }
    @Test void olderVlsRoundsUsePersistedNativeAgeAndBulletsKeepTheFirstDeadline() throws Exception {
        var policy = policy();
        var tick = policy.getMethod("beforeTick", Actor.class);
        var vls = new Shell();
        vls.age = 90;
        vls.world.time = 200;
        vls.data.putBoolean("DnvVlsOwned", true);
        assertFalse((boolean) tick.invoke(null, vls));
        assertEquals(211, vls.data.getLong("DnvTurretShellExpiry"));
        vls.world.time = 212;
        vls.data = vls.data.copy();
        assertTrue((boolean) tick.invoke(null, vls));

        var bullet = new Bullet();
        bullet.world.time = 300;
        policy.getMethod("track", Bullet.class).invoke(null, bullet);
        bullet.data = bullet.data.copy();
        assertEquals(321, bullet.data.getLong("DnvTurretShellExpiry"));
        bullet.world.time = 322;
        assertTrue((boolean) tick.invoke(null, bullet));
    }
    @Test void confirmedHeHitUsesConfiguredExplosionDamageOnceAndOnlyThatTargetIsExcludedFromSplash() throws Exception {
        var p=policy();var direct=p.getMethod("directHe",Shell.class,Hit.class);
        var excludes=p.getMethod("directHeTarget",Actor.class,Actor.class);
        var shell=new Shell();var target=new Actor();var neighbor=new Actor();
        direct.invoke(null,shell,new Hit(target));assertEquals(0,target.hurts,"Native and AP shells are unaffected");
        shell.data.putBoolean("DnvLightHeShell",true);shell.damage=14;
        direct.invoke(null,shell,new Hit(target));direct.invoke(null,shell,new Hit(target));
        assertEquals(1,target.hurts);assertEquals(14,target.damage);assertSame(shell,target.source.projectile);
        assertTrue((boolean)excludes.invoke(null,shell,target));assertFalse((boolean)excludes.invoke(null,shell,neighbor));
        var rejected=new Actor();rejected.accept=false;var second=new Shell();second.damage=14;second.data.putBoolean("DnvLightHeShell",true);
        direct.invoke(null,second,new Hit(rejected));assertEquals(0,rejected.hurts,"Native hurt rejection remains final");
    }
    public static class World {public boolean isClientSide;public long time;public long getGameTime(){return time;}public Registry registryAccess(){return new Registry();}}
    public static class Registry {}
    public static class Actor {
        public World world=new World();public CompoundTag data=new CompoundTag();public UUID id=UUID.randomUUID();
        public boolean removed,accept=true;public int hurts;public float damage;public Source source;
        public World level(){return world;}public boolean isRemoved(){return removed;}public void discard(){removed=true;}
        public CompoundTag getPersistentData(){return data;}public UUID getUUID(){return id;}
    }
    public static class Shell extends Actor {public float damage,radius;public int age;public int getLife(){return 100;}public int getSyncedTick(){return age;}public float getExplosionDamage(){return damage;}public float getExplosionRadius(){return radius;}public Actor getOwner(){return null;}}
    public static class Bullet extends Actor {public int getLife(){return 20;}}
    public static class ApPolicy {public static boolean deterministicApImpact(Actor source){return source.data.getBoolean("DnvSmallApImpact");}}
    public record Hit(Actor entity) {public Actor getEntity(){return entity;}}
    public record Source(Actor projectile) {}
    public static class NativeDamage {
        public static Source causeCustomExplosionDamage(Registry registry,Actor shell,Actor owner){return new Source(shell);}
        public static boolean forceHurt(Actor target,Source source,float amount){if(!target.accept)return false;target.hurts++;target.damage+=amount;target.source=source;return true;}
    }
}

package com.liucodex.libertadcheck;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.atsuishio.superbwarfare.tools.DamageHandler;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.naval.ShellRange;
import com.liucodex.libertad.naval.MountGeometry;
import com.liucodex.libertad.naval.SecondaryDirector;
import java.io.File;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exact two-mod archives, actual DNV deployers, survival helm, shore-height target and admitted HE. */
@Mod("libertadcheck")
@GameTestHolder("libertadcheck")
@PrefixGameTestTemplate(false)
public final class NativeCombatGameTest {
    public NativeCombatGameTest() throws Exception {
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(System.getProperty("libertad.testReport"))));
    }

    @GameTest(template="water_test", timeoutTicks=1800)
    public static void deployedBossesAreAcquiredFromTheSurvivalHelm(GameTestHelper test) {
        test.assertTrue(ModList.get().isLoaded("doggiesnewvehicle"),"DNV required for the field reproduction");
        verifyBoforsArmor(test);
        var level=test.getLevel(); var anchor=test.absolutePos(BlockPos.ZERO).atY(240);
        var center=new net.minecraft.world.level.ChunkPos(anchor);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)level.setChunkForced(center.x+x,center.z+z,true);
        for(int x=-12;x<=12;x++)for(int z=-78;z<=78;z++)for(int y=-7;y<=0;y++)
            level.setBlock(anchor.offset(x,y,z),Blocks.WATER.defaultBlockState(),2);
        var player=FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.SURVIVAL);
        var team=level.getScoreboard().addPlayerTeam("libertad_probe_players");
        level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);
        LibertadEntity ship=Libertad.SHIP.get().create(level);
        ship.moveTo(anchor.getX()+.5,anchor.getY()+8.0/9-.22,anchor.getZ()+.5,0,0);
        test.assertTrue(level.addFreshEntity(ship),"ship admission failed");
        test.assertTrue(player.startRiding(ship,true)&&ship.getSeatIndex(player)==0,"survival helm missing");
        test.assertTrue(!ship.multicrew(),"scenario must be single-controller mode");
        // Supply native ammunition explicitly; this regression diagnoses targeting, not creative-box inventory.
        ship.getGunDataMap().forEach((name,gun)->{if(name.startsWith("secondary_"))gun.ammo.set(1);});
        test.runAfterDelay(80,()->stage(test,ship,player,anchor,0));
    }

    private static void verifyBoforsArmor(GameTestHelper test) {
        var level=test.getLevel();
        var operator=FakePlayerFactory.getMinecraft(level);
        float twinDebit=0;
        for (String gunId : List.of("dragon_bofors_twin", "dragon_bofors_quad")) {
            var gunVehicle=(VehicleEntity)BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("doggievehicle",gunId)).create(level);
            var gun=gunVehicle.getGunData("Main");
            gun.selectedAmmoType.set(0);
            float raw=gun.get(GunProp.DAMAGE).floatValue();
            var target=(VehicleEntity)BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("doggievehicle","apocalypse")).create(level);
            target.moveTo(0,245,0);target.setHealth(target.getMaxHealth());
            var shell=ModEntities.SMALL_CANNON_SHELL.get().create(level);
            shell.setOwner(operator);
            var shot=new ShootParameters(gunVehicle.getAmmoSupplier(),operator,level,Vec3.ZERO,new Vec3(0,0,1),gun,0,true,null,null);
            try {
                Class.forName("com.liucodex.doggiesnewvehicle.compat.DnvTurretShells")
                        .getMethod("mark",Entity.class,ShootParameters.class).invoke(null,shell,shot);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            test.assertTrue(shell.getPersistentData().getFloat("DnvProjectileArmorBoost")==.03F,"incorrect Bofors AP penetration");
            var damage=ModDamageTypes.causeProjectileHitDamage(level.registryAccess(),shell,operator);
            var modifier=target.getDamageModifier();
            test.assertTrue(modifier.compute(target,damage,48)<=0,"old 48 must be blocked by actual Apocalypse armor");
            test.assertTrue(modifier.compute(target,damage,raw)>0,gunId+" failed AP armor admission, raw="+raw);
            float before=target.getHealth();DamageHandler.doDamage(target,damage,raw);
            float debit=before-target.getHealth();
            test.assertTrue(debit>0,gunId+" AP did not reduce actual Apocalypse health");
            if(gunId.endsWith("twin"))twinDebit=debit;
            else {
                // 240/400 RPM, 32/64 rounds and 60/80 tick empty reload; compare whole magazines.
                double twinDpm=twinDebit*32*1200/(32*5+60);
                double quadDpm=debit*64*1200/(64*3+80);
                test.assertTrue(quadDpm>twinDpm,"quad AP must deliver greater sustained hull damage");
            }
            System.out.println(gunId+" AP raw="+raw+" hull debit="+debit);
        }
    }

    private static void stage(GameTestHelper test,LibertadEntity ship,ServerPlayer player,BlockPos anchor,int index) {
        var level=test.getLevel(); String name=index==0?"noise":"silent_heaven";
        BlockPos ground=anchor.offset(80,0,0);
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)
            level.setBlock(ground.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        var item=BuiltInRegistries.ITEM.get(new ResourceLocation("doggiesnewvehicle",name+"_deployer"));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground),Direction.UP,ground,false));
        test.assertTrue(item.useOn(context).consumesAction(),name+" actual deployer failed");
        List<Entity> found=level.getEntities((Entity)null,new AABB(ground).inflate(12),e->
                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("doggievehicle:"+name));
        test.assertTrue(found.size()==1,name+" must spawn once");
        Entity target=found.get(0);
        try {target.getClass().getMethod("setHoldingPosition",boolean.class).invoke(target,true);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
        test.assertTrue(!player.isAlliedTo(target),name+" is allied unexpectedly");
        ship.getGunDataMap().forEach((key,gun)->{if(key.startsWith("secondary_"))gun.ammo.set(1);});
        if(ship.fireSnapshot().getInt("secondary")!=SecondaryDirector.Mode.ACTIVE.ordinal())ship.toggleSecondaries(player);
        int ammo=ammunition(ship);
        waitForShot(test,ship,player,target,anchor,index,ammo,test.getTick()+650);
    }

    private static int ammunition(LibertadEntity ship) {
        return ship.getGunDataMap().entrySet().stream().filter(e->e.getKey().startsWith("secondary_"))
                .mapToInt(e->e.getValue().ammo.get()).sum();
    }

    private static void waitForShot(GameTestHelper test,LibertadEntity ship,ServerPlayer player,Entity target,
                                    BlockPos anchor,int index,int ammo,long deadline) {
        boolean shot=!test.getLevel().getEntitiesOfClass(CannonShellEntity.class,
                ship.getBoundingBox().inflate(270),e->e.getOwner()==player
                        && e.getPersistentData().getCompound("LibertadShot").getInt("Range")==ShellRange.SECONDARY).isEmpty();
        if(shot) {
            test.assertTrue(ammunition(ship)<ammo,"admitted secondary must consume native ammunition");
            System.out.println("Libertad secondary fired at actual deployed "+target.getType()+" "+diagnostics(ship,player,target));
            ship.toggleSecondaries(player);target.discard();
            if(index==0)test.runAfterDelay(120,()->stage(test,ship,player,anchor,1));
            else {player.stopRiding();ship.discard();test.succeed();}
            return;
        }
        if(test.getTick()%100==0)System.out.println("Libertad combat waiting "+diagnostics(ship,player,target));
        test.assertTrue(test.getTick()<deadline,"No automatic HE at deployed boss: "+diagnostics(ship,player,target));
        test.runAfterDelay(1,()->waitForShot(test,ship,player,target,anchor,index,ammo,deadline));
    }

    private static String diagnostics(LibertadEntity ship,ServerPlayer player,Entity target) {
        try {
            var field=LibertadEntity.class.getDeclaredField("fire");field.setAccessible(true);Object fire=field.get(ship);Class<?> type=fire.getClass();
            var relation=type.getDeclaredMethod("relation",Entity.class,ServerPlayer.class);relation.setAccessible(true);
            var secondary=type.getDeclaredField("secondary");secondary.setAccessible(true);
            var state=(SecondaryDirector)secondary.get(fire);
            var solve=type.getDeclaredMethod("solve",int.class,Vec3.class,Vec3.class,boolean.class);solve.setAccessible(true);
            var visible=type.getDeclaredMethod("visibleFrom",ServerLevel.class,int.class,MountGeometry.Angles.class,Vec3.class);visible.setAccessible(true);
            var center=target.getBoundingBox().getCenter();
            var out=new StringBuilder().append(relation.invoke(fire,target,player)).append(" mode=").append(state.mode())
                    .append(" tracks=").append(state.assignments()).append(" ship=").append(ship.position())
                    .append(" target=").append(center).append(" team=").append(target.getTeam()).append(" ammo=").append(ammunition(ship));
            for(int i=6;i<24;i++) {
                var goal=(MountGeometry.Angles)solve.invoke(fire,i,center,target.getDeltaMovement(),true);
                if(goal!=null && (boolean)visible.invoke(fire,(ServerLevel)ship.level(),i,goal,center))
                    out.append(" mount=").append(i).append(" goal=").append(goal).append(" pose=").append(ship.angles(i,1));
            }
            return out.toString();
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
}

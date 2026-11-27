package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.boss.DnvBossContent;
import com.liucodex.doggiesnewvehicle.boss.MothershipTactics;
import com.liucodex.doggiesnewvehicle.boss.MothershipPresence;
import com.liucodex.doggiesnewvehicle.boss.MothershipWorld;
import com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A boss summon transaction; it never edits blocks or loads the surrounding chunks. */
public final class DnvMothershipDeployerItem extends Item {
    public DnvMothershipDeployerItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        var ground = context.getClickedPos().above();
        if (player == null || stack.isEmpty() || !player.getAbilities().mayBuild || player.isSpectator()
                || !player.mayUseItemAt(ground, context.getClickedFace(), stack)
                || !level.mayInteract(player, context.getClickedPos())) return reject(player, "denied");
        var ship = DnvBossContent.MOTHERSHIP.get().create(level);
        if (ship == null) return reject(player, "failed");
        if (!MothershipPresence.get(level).permitsFresh(level, ship.getUUID())) return reject(player, "limit");
        ship.moveTo(ground.getX() + .5, ground.getY() + MothershipTactics.GROUND_CLEARANCE,
                ground.getZ() + .5, player.getYRot(), 0);
        if (!MothershipWorld.safePose(ship, ship.position(), ship.getYRot())) return reject(player, "space");
        ship.setBeaconAnchor(new net.minecraft.world.phys.Vec3(ground.getX() + .5, ground.getY(), ground.getZ() + .5));
        ship.updateOBB();
        ship.refreshBoundingBoxFromOBBs();
        if (!level.addFreshEntity(ship)) return reject(player, "failed");
        DnvBossTeamMembership.assignIfUnassigned(ship);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult reject(Player player, String reason) {
        if (player != null) player.displayClientMessage(
                Component.translatable("message.doggiesnewvehicle.ses_executor_of_truth_deployer." + reason), true);
        return InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.ses_executor_of_truth_deployer"));
    }
}

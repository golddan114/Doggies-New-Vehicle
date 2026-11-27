package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.init.DnvM1Content;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Survival deployment item produced by SBW vehicle assembly for the non-SBW M1 host. */
public final class DnvM1DeployerItem extends Item {
  public DnvM1DeployerItem(Properties properties) {
    super(properties);
  }

  @Override public InteractionResult useOn(UseOnContext context) {
    if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
    Player player = context.getPlayer();
    if (player == null || !player.getAbilities().mayBuild
        || !player.mayUseItemAt(context.getClickedPos().above(), context.getClickedFace(), context.getItemInHand()))
      return InteractionResult.FAIL;
    var pos = context.getClickedPos().above();
    if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)
        || level.isOutsideBuildHeight(pos)) return InteractionResult.FAIL;
    var entity = DnvM1Content.M1.get().create(level);
    if (entity == null) return InteractionResult.FAIL;
    entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
    if (!entity.initializeOwner(player.getUUID())) return InteractionResult.FAIL;
    for (var bounds : entity.deploymentClearance()) {
      BlockPos minimum = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
      BlockPos maximum = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
      if (!level.hasChunksAt(minimum, maximum)
          || !level.getWorldBorder().isWithinBounds(bounds)
          || level.isOutsideBuildHeight(minimum)
          || level.isOutsideBuildHeight(maximum)) return InteractionResult.FAIL;
      for (BlockPos occupied : BlockPos.betweenClosed(minimum, maximum)) {
        if (!level.mayInteract(player, occupied)) return InteractionResult.FAIL;
      }
      if (!level.noCollision(entity, bounds)) return InteractionResult.FAIL;
    }
    if (!level.addFreshEntity(entity)) return InteractionResult.FAIL;
    if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.m1_deployed"));
    return InteractionResult.CONSUME;
  }

  @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.m1_yunlong_deployer"));
  }
}

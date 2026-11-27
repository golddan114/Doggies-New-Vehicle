package com.liucodex.libertad;

import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Converts the legacy test.2 item in existing inventories to the native container. */
final class ShipDeployer extends Item {
    ShipDeployer(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        return InteractionResultHolder.consume(ContainerBlockItem.createInstance(Libertad.SHIP.get()));
    }
}

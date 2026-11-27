package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.init.ModTags;
import com.atsuishio.superbwarfare.item.weapon.CrowbarItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Boss encounters cannot be converted into portable SBW vehicle containers. */
public final class DnvBossInteraction {
    private DnvBossInteraction() { }

    public static boolean isRetrievalTool(ItemStack stack) {
        return stack.is(ModTags.Items.TOOLS_CROWBAR) || stack.getItem() instanceof CrowbarItem;
    }

    public static InteractionResult rejectRetrieval(Player player) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.boss.no_retrieval"), true);
        }
        // Both sides consume the native crowbar path before it can remove a client-only entity.
        return InteractionResult.FAIL;
    }
}

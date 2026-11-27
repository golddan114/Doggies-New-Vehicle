package com.liucodex.doggiesnewvehicle.item;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Shared item-NBT and interaction owner for vehicle behavior controllers. */
public final class DnvBehaviorControllerItem<T extends Entity> extends Item {
    private final Class<T> vehicleType;
    private final String translationNamespace;
    private final String targetTag;
    private final String dimensionTag;
    private final String holdTag;
    private final Predicate<T> holdingPosition;
    private final BiConsumer<T, Boolean> setHoldingPosition;
    private final ToIntFunction<T> teleportCooldownTicks;
    private final TeleportCommand<T> teleportCommand;

    public DnvBehaviorControllerItem(
            Properties properties,
            Class<T> vehicleType,
            String nbtPrefix,
            String translationNamespace,
            Predicate<T> holdingPosition,
            BiConsumer<T, Boolean> setHoldingPosition,
            ToIntFunction<T> teleportCooldownTicks,
            TeleportCommand<T> teleportCommand) {
        super(properties);
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType");
        this.translationNamespace = requireText(
                translationNamespace, "translationNamespace");
        String prefix = requireText(nbtPrefix, "nbtPrefix");
        targetTag = prefix + "ControllerTarget";
        dimensionTag = prefix + "ControllerDimension";
        holdTag = prefix + "ControllerHold";
        this.holdingPosition = Objects.requireNonNull(
                holdingPosition, "holdingPosition");
        this.setHoldingPosition = Objects.requireNonNull(
                setHoldingPosition, "setHoldingPosition");
        this.teleportCooldownTicks = Objects.requireNonNull(
                teleportCooldownTicks, "teleportCooldownTicks");
        this.teleportCommand = Objects.requireNonNull(
                teleportCommand, "teleportCommand");
    }

    public InteractionResult bind(ItemStack stack, T vehicle, Player player) {
        if (vehicle.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(targetTag, vehicle.getUUID());
        tag.putString(
                dimensionTag,
                vehicle.level().dimension().location().toString());
        tag.putBoolean(holdTag, holdingPosition.test(vehicle));
        notify(player, "bound");
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        T vehicle = findBoundVehicle(serverLevel, stack);
        if (vehicle == null) {
            notify(player, "unavailable");
            return InteractionResultHolder.consume(stack);
        }
        boolean hold = !holdingPosition.test(vehicle);
        setHoldingPosition.accept(vehicle, hold);
        stack.getOrCreateTag().putBoolean(holdTag, hold);
        notify(player, hold ? "hold" : "autonomous");
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        T vehicle = findBoundVehicle(serverLevel, context.getItemInHand());
        if (vehicle == null) {
            notify(player, "unavailable");
            return InteractionResult.CONSUME;
        }

        int cooldown = teleportCooldownTicks.applyAsInt(vehicle);
        if (cooldown > 0) {
            player.displayClientMessage(
                    Component.translatable(
                            messageKey("teleport_cooldown"),
                            (cooldown + 19) / 20),
                    true);
            return InteractionResult.CONSUME;
        }

        BlockPos clicked = context.getClickedPos();
        int x = clicked.getX();
        int z = clicked.getZ();
        int y = serverLevel.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 destination = new Vec3(x + 0.5D, y, z + 0.5D);
        boolean success = teleportCommand.execute(
                vehicle, serverLevel, destination);
        notify(player, success ? "teleport_success" : "teleport_blocked");
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(tooltip("bind", ChatFormatting.GRAY));
        tooltip.add(tooltip("mode", ChatFormatting.GRAY));
        tooltip.add(tooltip("teleport", ChatFormatting.GRAY));
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(targetTag)) {
            tooltip.add(tooltip(
                    tag.getBoolean(holdTag)
                            ? "bound_hold" : "bound_autonomous",
                    ChatFormatting.AQUA));
        }
    }

    @Nullable
    private T findBoundVehicle(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !tag.hasUUID(targetTag)
                || !tag.contains(dimensionTag, Tag.TAG_STRING)
                || !level.dimension().location().toString()
                        .equals(tag.getString(dimensionTag))) {
            return null;
        }
        Entity entity = level.getEntity(tag.getUUID(targetTag));
        return vehicleType.isInstance(entity) && entity.isAlive()
                ? vehicleType.cast(entity) : null;
    }

    private void notify(Player player, String action) {
        player.displayClientMessage(
                Component.translatable(messageKey(action)), true);
    }

    private Component tooltip(String action, ChatFormatting color) {
        return Component.translatable(
                "tooltip." + translationNamespace + ".controller." + action)
                .withStyle(color);
    }

    private String messageKey(String action) {
        return "message." + translationNamespace + ".controller." + action;
    }

    private static String requireText(String value, String label) {
        String result = Objects.requireNonNull(value, label).trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return result;
    }

    @FunctionalInterface
    public interface TeleportCommand<T> {
        boolean execute(T vehicle, ServerLevel level, Vec3 destination);
    }
}

package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Shared placement transaction for DNV vehicle deployer items. */
public final class DnvVehicleDeployerItem<T extends Entity> extends Item {
    private final Supplier<? extends EntityType<T>> entityType;
    private final Consumer<T> initialize;

    public DnvVehicleDeployerItem(
            Properties properties,
            Supplier<? extends EntityType<T>> entityType,
            Consumer<T> initialize) {
        super(properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.initialize = Objects.requireNonNull(initialize, "initialize");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        T vehicle = entityType.get().create(level);
        if (vehicle == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player == null ? 0.0F : player.getYRot();
        vehicle.moveTo(
                context.getClickedPos().getX() + 0.5D,
                context.getClickedPos().getY() + 1.0D,
                context.getClickedPos().getZ() + 0.5D,
                yaw,
                0.0F);
        initialize.accept(vehicle);
        if (!level.addFreshEntity(vehicle)) {
            return InteractionResult.FAIL;
        }
        if (vehicle instanceof DnvCombatPolicy.BossAutonomy) {
            DnvBossTeamMembership.assignIfUnassigned(vehicle);
        }
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

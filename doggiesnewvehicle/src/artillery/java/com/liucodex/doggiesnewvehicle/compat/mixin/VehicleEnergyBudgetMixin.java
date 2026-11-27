package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.artillery.energy.*;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/** Native inventory adds only the amount extracted; use the same budget as capability receive. */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleEnergyBudgetMixin implements DnvEnergyInput {
    @Unique private boolean dnv$energyPolicyResolved;
    @Unique private DnvEnergyInputBudget dnv$energyBudget;
    @ModifyConstant(method = "m_6075_", constant = @Constant(intValue = 20), slice = @Slice(
            from = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;hasEnergyStorage()Z"),
            to = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;setEnergy(I)V")), remap = false)
    private int dnv$inventoryInterval(int nativeInterval) {
        VehicleEntity entity = (VehicleEntity) (Object) this;
        return !entity.level().isClientSide && !(entity instanceof DnvAutonomousEnergy)
                && dnv$energyInputBudget() != null ? 1 : nativeInterval;
    }
    @Override public DnvEnergyInputBudget dnv$energyInputBudget() {
        if (!dnv$energyPolicyResolved) {
            VehicleEntity entity = (VehicleEntity) (Object) this;
            dnv$energyBudget = DnvEnergyInputPolicy.forVehicle(EntityType.getKey(entity.getType()).toString());
            dnv$energyPolicyResolved = true;
        }
        return dnv$energyBudget;
    }
    @Redirect(method = "m_6075_", at = @At(value = "INVOKE", target =
        "Lnet/minecraftforge/energy/IEnergyStorage;extractEnergy(IZ)I"), remap = false)
    private int dnv$chargeInventory(IEnergyStorage battery, int requested, boolean simulate) {
        VehicleEntity entity = (VehicleEntity) (Object) this;
        var budget = dnv$energyInputBudget();
        if (budget == null) return battery.extractEnergy(requested, simulate);
        if (entity.level().isClientSide) return 0;
        long tick = entity.level().getGameTime();
        // Native setEnergy credits this amount through receiveEnergy immediately afterwards.
        // Reserve by admission here; commit only at that native receive return, never twice.
        return battery.extractEnergy(budget.allowance(tick, requested), simulate);
    }
}

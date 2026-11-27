package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.capability.energy.VehicleEnergyStorage;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.artillery.energy.DnvAutonomousEnergy;
import com.liucodex.doggiesnewvehicle.artillery.energy.DnvEnergyInput;
import com.liucodex.doggiesnewvehicle.artillery.energy.DnvEnergyInputBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNV-only admission; native storage still writes ENERGY and returns actual received FE. */
@Mixin(value = VehicleEnergyStorage.class, remap = false)
public abstract class VehicleEnergyInputMixin {
    @Shadow protected abstract VehicleEntity getVehicle();
    @Unique private DnvEnergyInputBudget dnv$budget() {
        return ((DnvEnergyInput) getVehicle()).dnv$energyInputBudget();
    }
    @Unique private boolean dnv$autonomousInternalWrite() {
        return getVehicle() instanceof DnvAutonomousEnergy owner && owner.nativeEnergyMutation();
    }
    @Inject(method = "receiveEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$rejectUnavailableInput(int requested, boolean simulate, CallbackInfoReturnable<Integer> callback) {
        if (dnv$budget() != null && (getVehicle().level().isClientSide || !getVehicle().hasEnergyStorage()
                || getVehicle().getEnergy() >= getVehicle().getMaxEnergy())) callback.setReturnValue(0);
    }
    @ModifyVariable(method = "receiveEnergy", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int dnv$limitReceive(int requested) {
        var budget = dnv$budget();
        if (budget == null || dnv$autonomousInternalWrite()) return requested;
        return getVehicle().level().isClientSide ? 0 : budget.allowance(getVehicle().level().getGameTime(), requested);
    }
    @Inject(method = "receiveEnergy", at = @At("RETURN"), remap = false)
    private void dnv$received(int requested, boolean simulate, CallbackInfoReturnable<Integer> callback) {
        var budget = dnv$budget();
        if (budget != null && !dnv$autonomousInternalWrite())
            budget.received(getVehicle().level().getGameTime(), callback.getReturnValueI(), simulate);
    }
    @Inject(method = "extractEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$excludeAutonomousExport(int requested, boolean simulate, CallbackInfoReturnable<Integer> callback) {
        if (getVehicle() instanceof DnvAutonomousEnergy && !dnv$autonomousInternalWrite()) callback.setReturnValue(0);
    }
    @Inject(method = "canExtract", at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$excludeAutonomousExportCapability(CallbackInfoReturnable<Boolean> callback) {
        if (getVehicle() instanceof DnvAutonomousEnergy && !dnv$autonomousInternalWrite()) callback.setReturnValue(false);
    }
}

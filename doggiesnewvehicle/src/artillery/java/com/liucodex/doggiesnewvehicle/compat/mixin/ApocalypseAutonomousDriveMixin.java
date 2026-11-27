package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleEngineUtils;
import com.liucodex.doggiesnewvehicle.armored.DnvAlliedApocalypseEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps the native or another add-on's control reference before adding the authorized DNV permit. */
@Mixin(value = VehicleEngineUtils.class, remap = false)
public abstract class ApocalypseAutonomousDriveMixin {
    @ModifyVariable(method = "trackEngine", at = @At("STORE"), ordinal = 0,
            require = 1, allow = 1, remap = false)
    private static Entity dnv$autonomousTrackedControl(Entity driver, VehicleEntity vehicle, EngineInfo.Track engine) {
        return driver == null && vehicle instanceof DnvAlliedApocalypseEntity tank && tank.nativeDriveAuthorized() ? tank : driver;
    }
}

package com.liucodex.libertad.compat.sbw;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.libertad.naval.MountGeometry.BarrelPose;
import com.liucodex.libertad.naval.SalvoControl.Control;
import com.liucodex.libertad.naval.SalvoControl.NativeGuns;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/** Native readiness and seat checks adapted from DNV Project612Armament. */
public record SbwGunPort(EntityType<? extends VehicleEntity> libertadType, VehicleEntity ship,
                         ServerPlayer player, int seat, Supplier<Control> currentControl,
                         WorldAdmission world) implements NativeGuns {
    public SbwGunPort {
        Objects.requireNonNull(libertadType);
        Objects.requireNonNull(ship);
        Objects.requireNonNull(player);
        Objects.requireNonNull(currentControl);
        Objects.requireNonNull(world);
    }

    @Override
    public boolean isCurrentMaster(Control control) {
        return control != null && ship.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && ship.getType() == libertadType
                && player.level() == level && !ship.isRemoved() && !ship.isWreck()
                && player.isAlive() && !player.isSpectator() && player.getVehicle() == ship
                && ship.getSeatIndex(player) == seat && player.getUUID().equals(control.player())
                && control.equals(currentControl.get());
    }

    @Override
    public boolean ready(String gunId) {
        return readyGun(gunId) != null;
    }

    @Override
    public boolean clear(BarrelPose barrel) {
        return world.clear(ship, barrel);
    }

    @Override
    public boolean tryFire(BarrelPose barrel) {
        GunData gun = readyGun(barrel.gunId());
        return gun != null && isCurrentMaster(currentControl.get()) && world.admit(ship, player, gun, barrel);
    }

    private GunData readyGun(String id) {
        GunData gun = ship.getGunDataMap().get(id);
        return gun != null && gun.get(GunProp.PROJECTILE_AMOUNT) == 1 && gun.canShoot(ship.getAmmoSupplier()) ? gun : null;
    }

    /** The eventual projectile hook reports actual admission. vehicleShoot itself has no such return value. */
    public interface WorldAdmission {
        boolean clear(VehicleEntity ship, BarrelPose barrel);
        boolean admit(VehicleEntity ship, ServerPlayer player, GunData gun, BarrelPose barrel);
    }
}

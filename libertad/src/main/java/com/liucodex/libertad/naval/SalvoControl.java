package com.liucodex.libertad.naval;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Final per-turret gate on the server thread. Gun state and world admission stay behind the SBW port. */
public final class SalvoControl {
    private SalvoControl() {}

    public record Control(UUID player, long seatEpoch) {
        public Control { Objects.requireNonNull(player); }
    }

    public record YawSector(double from, double to) {
        public YawSector {
            if (!Double.isFinite(from) || !Double.isFinite(to) || from < -180 || from > 180 || to < -180 || to > 180) {
                throw new IllegalArgumentException("Yaw sector outside [-180,180]");
            }
        }

        boolean allows(double yaw) {
            return contains(yaw) || (Math.abs(yaw) == 180 && contains(-yaw));
        }

        private boolean contains(double yaw) {
            return from <= to ? yaw >= from && yaw <= to : yaw >= from || yaw <= to;
        }
    }

    public record Arc(List<YawSector> yawSectors, double minPitch, double maxPitch) {
        public Arc {
            yawSectors = List.copyOf(yawSectors);
            if (!Double.isFinite(minPitch) || !Double.isFinite(maxPitch) || minPitch < -90 || maxPitch > 90 || minPitch > maxPitch) {
                throw new IllegalArgumentException("Invalid elevation limits");
            }
        }

        public boolean allows(MountGeometry.Angles aim) {
            return aim.pitch() >= minPitch && aim.pitch() <= maxPitch
                    && yawSectors.stream().anyMatch(sector -> sector.allows(aim.yaw()));
        }
    }

    public interface NativeGuns {
        boolean isCurrentMaster(Control control);
        boolean ready(String gunId);
        boolean clear(MountGeometry.BarrelPose barrel);

        /** True only after the native projectile has been admitted to the world. */
        boolean tryFire(MountGeometry.BarrelPose barrel);
    }

    public enum Result { REJECTED, WORLD_REJECTED, PARTIAL, FIRED }

    public static Result executeTwin(Control control, MountGeometry mount, Arc arc, MountGeometry.Angles actual,
                                     MountGeometry.Angles requested, double tolerance, NativeGuns guns) {
        if (!guns.isCurrentMaster(control) || mount.barrels().size() != 2 || !arc.allows(requested) || !arc.allows(actual)
                || !(tolerance >= 0 && tolerance <= 5)
                || Math.abs(Math.IEEEremainder(actual.yaw() - requested.yaw(), 360)) > tolerance
                || Math.abs(actual.pitch() - requested.pitch()) > tolerance) {
            return Result.REJECTED;
        }
        List<MountGeometry.BarrelPose> barrels = mount.at(actual);
        for (var barrel : barrels) {
            if (!guns.ready(barrel.gunId()) || !guns.clear(barrel)) return Result.REJECTED;
        }
        if (!guns.tryFire(barrels.get(0))) return Result.WORLD_REJECTED;
        // A world event may change the controller or path after the first admission.
        var second = barrels.get(1);
        return guns.isCurrentMaster(control) && guns.ready(second.gunId()) && guns.clear(second) && guns.tryFire(second)
                ? Result.FIRED : Result.PARTIAL;
    }
}

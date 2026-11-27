package com.liucodex.doggiesnewvehicle.armored;

import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvGroundDrive;
import com.liucodex.doggiesnewvehicle.compat.DnvGroundTerrainProbe;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Converts bounded local movement intent to native controls, never position or velocity. */
final class ApocalypseAutonomousNavigation {
    private final DnvAlliedApocalypseEntity tank;
    private final DnvAutonomyCore intent = new DnvAutonomyCore(24, 48);
    private final DnvGroundDrive drive = new DnvGroundDrive();
    private UUID budgetId;
    private float throttlePulse, steeringPulse;

    ApocalypseAutonomousNavigation(DnvAlliedApocalypseEntity tank) { this.tank = tank; }

    void move(Vec3 destination, boolean enemy, boolean blockedLine) {
        move(destination, enemy, blockedLine, 24);
    }

    void move(Vec3 destination, boolean enemy, boolean blockedLine, double minimumDistance) {
        if (destination == null || !tank.autonomousReady()) { stop(); return; }
        Vec3 delta = destination.subtract(tank.position());
        double distance = delta.horizontalDistance();
        if (!enemy && distance <= 3) { stop(); return; }
        float bearing = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        var action = intent.decideMovement(new DnvAutonomyCore.SensorSnapshot(false, false,
                enemy, true, blockedLine, distance, tank.getYRot(), bearing), enemy ? minimumDistance : 24);
        if (budgetId == null) budgetId = new UUID(tank.getUUID().getMostSignificantBits() ^ 0x41494e41564cL,
                tank.getUUID().getLeastSignificantBits());
        var probe = new DnvGroundTerrainProbe(tank);
        boolean[] budget = {false, false, false}; // requested, accepted, deferred
        var control = drive.command(new DnvGroundDrive.Input(action, tank.getYRot(), bearing,
                tank.getX(), tank.getZ(), distance, tank.onGround(), tank.horizontalCollision, blockedLine),
                (yaw, length) -> {
                    if (!budget[0]) {
                        budget[0] = true;
                        budget[1] = DnvTargetingBudget.admitGeometryCheck((ServerLevel) tank.level(), budgetId);
                    }
                    if (!budget[1]) { budget[2] = true; return false; }
                    return probe.clear(yaw, length);
                });
        if (budget[2]) { stop(); return; }
        // The native engine decays power on every released key tick. Full straight retreat must hold reverse.
        if (action.state() == DnvAutonomyCore.TacticalState.RETREATING && control.throttle() < 0)
            control = new DnvGroundDrive.Control(Math.max(-1, control.throttle() / .65F), control.steering(), control.brake());
        apply(control);
    }

    private void apply(DnvGroundDrive.Control control) {
        throttlePulse += Math.abs(control.throttle());
        steeringPulse += Math.abs(control.steering());
        boolean throttle = throttlePulse >= 1, steering = steeringPulse >= 1;
        if (throttle) throttlePulse -= 1;
        if (steering) steeringPulse -= 1;
        // Native trackEngine reverses steering on the actual reverse key, even when power is still negative.
        boolean reversing = throttle && control.throttle() < 0;
        boolean right = control.steering() * (reversing ? -1 : 1) > 0;
        tank.setForwardInputDown(throttle && control.throttle() > 0);
        tank.setBackInputDown(throttle && control.throttle() < 0);
        tank.setRightInputDown(steering && right);
        tank.setLeftInputDown(steering && !right);
        tank.setUpInputDown(control.brake() || control.throttle() == 0 && control.steering() == 0);
        tank.setDownInputDown(false);
        tank.setSprintInputDown(false);
    }

    void stop() {
        drive.reset(); throttlePulse = steeringPulse = 0;
        apply(new DnvGroundDrive.Control(0, 0, true));
    }

    void clear() {
        stop();
        if (budgetId != null && tank.level() instanceof ServerLevel server)
            DnvTargetingBudget.releaseGeometryOwner(server, budgetId);
        budgetId = null;
    }
}

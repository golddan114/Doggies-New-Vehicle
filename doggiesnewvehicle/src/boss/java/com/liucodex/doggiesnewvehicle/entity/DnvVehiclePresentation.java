package com.liucodex.doggiesnewvehicle.entity;

import net.minecraft.world.entity.Entity;

/** Read-only rendering and audio state shared by DNV boss vehicle presentations. */
public interface DnvVehiclePresentation {
    Entity asEntity();

    default int getTransitionTick() {
        return 0;
    }

    int getEntranceTicks();

    default int getTeleportTicks() {
        return 0;
    }

    boolean isEntering();

    float getCoreYaw();

    float getMainGunPitch();

    float getAutocannonLYaw();

    float getAutocannonLPitch();

    float getAutocannonRYaw();

    float getAutocannonRPitch();

    float getMissilePodLYaw();

    float getMissilePodLPitch();

    float getMissilePodRYaw();

    float getMissilePodRPitch();

    default float getHaloAngle() {
        return 0.0F;
    }

    default boolean isPhaseTwo() {
        return false;
    }

    default boolean isTransitioning() {
        return false;
    }

    default boolean isHaloVisible() {
        return false;
    }

    float getDriveIntensity();

    float getTrackPhaseL();

    float getTrackPhaseR();

    float getTrackSpeedL();

    float getTrackSpeedR();

    float getRoadWheelAngleL();

    float getRoadWheelAngleR();

    boolean hasCombatTarget();

    boolean isCriticalState();
}

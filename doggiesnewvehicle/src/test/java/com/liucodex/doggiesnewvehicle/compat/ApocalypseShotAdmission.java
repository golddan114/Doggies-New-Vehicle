package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.Entity;
import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.Level;
import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.Parameters;

/** Test-world version of the small native admission boundary; no firing implementation. */
public interface ApocalypseShotAdmission {
    boolean admitNativeProjectile(Level world, Entity projectile, Parameters parameters);
    boolean nativeProjectileAccepted(Parameters parameters);
}

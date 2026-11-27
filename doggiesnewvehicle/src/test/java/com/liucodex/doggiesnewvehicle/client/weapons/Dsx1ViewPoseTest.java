package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Dsx1ViewPoseTest {
    @Test void nativeAdsSurvivesTheEntireActionIncludingPartialAimAndReverseSampling() {
        assertEquals(1, Dsx1ViewPose.ads(1, true, 0));
        assertEquals(1, Dsx1ViewPose.ads(1, true, 1));
        for (double zoom : new double[]{0, .2, .6, 1}) for (int i = 1000; i >= 0; i--) {
            double p = i / 1000D;
            assertEquals(zoom, Dsx1ViewPose.ads(zoom, true, p));
            assertEquals(Dsx1ViewPose.depthScale(zoom), Dsx1ViewPose.depthScale(Dsx1ViewPose.ads(zoom, true, p)));
        }
        assertEquals(.6, Dsx1ViewPose.ads(.6, false, .5));
        assertEquals(0, Dsx1ViewPose.ads(Double.NaN, true, .5));
    }

    @Test void aimDampsWholeGunActionsWithoutChangingMechanismTiming() {
        assertEquals(1, Dsx1ViewPose.actionRotation(0));
        assertEquals(1, Dsx1ViewPose.actionTranslation(0));
        assertEquals(.08, Dsx1ViewPose.actionRotation(1), 1e-6);
        assertEquals(.18, Dsx1ViewPose.actionTranslation(1), 1e-6);
        assertEquals(1, Dsx1ViewPose.actionRotation(Double.NaN));
        assertEquals(.18, Dsx1ViewPose.actionTranslation(2), 1e-6);
    }
}

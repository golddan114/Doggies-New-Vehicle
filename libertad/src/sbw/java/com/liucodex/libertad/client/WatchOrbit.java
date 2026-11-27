package com.liucodex.libertad.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Ship-relative camera. Only the ship's horizontal heading rotates the horizon. */
final class WatchOrbit {
    private double bearing;
    private double elevation = 14;
    private double range = 86;
    private double fore = 20;
    private boolean locked;

    WatchOrbit(int seat) {
        bearing = seat == 1 ? 62 : -62;
    }

    boolean locked() { return locked; }
    void lock() { locked = true; }

    void orbit(double horizontal, double vertical) {
        if (locked) return;
        bearing = Mth.wrapDegrees(bearing + horizontal);
        elevation = Mth.clamp(elevation + vertical, 3, 75);
    }

    void scroll(double steps, boolean frameAlongDeck) {
        if (locked) return;
        if (frameAlongDeck) fore = Mth.clamp(fore + steps * 4, -48, 48);
        else range = Mth.clamp(range * Math.pow(.92, steps), 25, 170);
    }

    Vec2 rotation(float bow) {
        return new Vec2(Mth.wrapDegrees((float)(bow + 180 - bearing)), (float)elevation);
    }

    Vec3 position(Vec3 origin, float bow) {
        double a = Math.toRadians(bearing), e = Math.toRadians(elevation);
        double x = Math.sin(a) * Math.cos(e), z = Math.cos(a) * Math.cos(e), y = Math.sin(e);
        // Stop zoom before the eye enters the hull or bridge, including at a fore/aft framing offset.
        double clearance = Math.min(14 / Math.abs(x), (z > 0 ? 80 - fore : 80 + fore) / Math.abs(z));
        clearance = Math.min(clearance, (22 - 8) / y);
        double distance = Math.max(range, clearance + 1);
        return origin.add(new Vec3(x * distance, 8 + y * distance, fore + z * distance)
                .yRot((float)-Math.toRadians(bow)));
    }
}

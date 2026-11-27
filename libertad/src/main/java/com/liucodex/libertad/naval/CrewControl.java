package com.liucodex.libertad.naval;

import com.liucodex.libertad.naval.SalvoControl.Control;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Seat facts come from SBW; this owner invalidates fire consent across crew/mode changes. */
public final class CrewControl {
    private final Control[] occupants = new Control[3], requests = new Control[3];
    private final long[] requestedAt = new long[3];
    private long epoch, lastToggle = Long.MIN_VALUE;
    private boolean multicrew;

    public boolean multicrew() { return multicrew; }

    public void assign(int seat, UUID player) {
        if (seat < 0 || seat >= occupants.length) return;
        Control old = occupants[seat];
        if (Objects.equals(player, old == null ? null : old.player())) return;
        occupants[seat] = player == null ? null : new Control(player, ++epoch);
        requests[seat] = null;
    }

    public Control controller(int seat) {
        return seat >= 0 && seat < occupants.length && (seat == 0 || multicrew) ? occupants[seat] : null;
    }

    public boolean current(int seat, Control token) { return token != null && token.equals(controller(seat)); }

    public boolean toggle(UUID player, long tick) {
        var helm = controller(0);
        if (helm == null || !helm.player().equals(player) || (lastToggle != Long.MIN_VALUE && tick - lastToggle < 10)) return false;
        lastToggle = tick;
        multicrew = !multicrew;
        invalidate();
        return true;
    }

    public void restore(boolean enabled) {
        multicrew = enabled;
        Arrays.fill(occupants, null);
        Arrays.fill(requests, null);
        lastToggle = Long.MIN_VALUE;
        ++epoch;
    }

    private void invalidate() {
        for (int seat = 0; seat < occupants.length; seat++) {
            if (occupants[seat] != null) occupants[seat] = new Control(occupants[seat].player(), ++epoch);
        }
        Arrays.fill(requests, null);
    }

    public void request(int seat, UUID player, long tick) {
        Control token = controller(seat);
        if (token == null || !token.player().equals(player)) return;
        requests[seat] = token;
        requestedAt[seat] = tick;
    }

    public boolean consume(int seat, long tick) {
        Control token = requests[seat];
        requests[seat] = null;
        long age = tick - requestedAt[seat];
        return current(seat, token) && age >= 0 && age <= 1;
    }

    /** Ship forward is local +Z: +X is port (left when looking forward). */
    public static int sideSeat(double localX) { return localX > 0 ? 1 : 2; }

    public static boolean controls(boolean multicrew, int seat, int mount, double localX) {
        if (mount < 0 || mount >= 24) return false;
        return mount < 6 ? seat == 0 : multicrew ? seat == sideSeat(localX) : seat == 0;
    }

    public static float viewYaw(float yaw, float bow, int seat, boolean firstPerson) {
        if (seat < 0 || seat > 2 || (seat == 0 && !firstPerson)) return yaw;
        float center = bow + (seat == 1 ? -90 : seat == 2 ? 90 : 0);
        float relative = (yaw - center) % 360;
        if (relative >= 180) relative -= 360;
        if (relative < -180) relative += 360;
        return yaw + Math.max(-90, Math.min(90, relative)) - relative;
    }
}

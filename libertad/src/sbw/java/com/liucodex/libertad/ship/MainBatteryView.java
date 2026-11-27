package com.liucodex.libertad.ship;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** The read-only display fields carried by the existing synchronized fire state. */
public record MainBatteryView(Aim aim, Load left, Load right, Marker leftMarker, Marker rightMarker) {
    public static final int MOUNTS = 6;
    static final MainBatteryView EMPTY = new MainBatteryView(Aim.UNKNOWN, Load.EMPTY, Load.EMPTY, null, null);
    private static final String KEY = "MainBattery";

    public enum Aim { UNKNOWN, LIMIT, TURNING, BLOCKED, CLEAR }
    public enum Endpoint { CONTACT, RANGE, BOUNDARY, WATER, BLOCKED }
    private static final Aim[] AIMS = Aim.values();
    private static final Endpoint[] ENDPOINTS = Endpoint.values();

    /** A zero-length timer is idle. Chamber state is observed independently of reload. */
    public record Load(int remaining, int total, boolean chambered) {
        static final Load EMPTY = new Load(0, 0, false);

        public Load {
            total = Math.max(0, Math.min(total, 72000));
            remaining = Math.max(0, Math.min(remaining, total));
        }

        public boolean reloading() { return total > 0; }

        public float progress() {
            return reloading() ? Math.min(.99f, 1 - remaining / (float) total) : chambered ? 1 : 0;
        }
    }

    public record Marker(Vec3 point, Endpoint endpoint) {}

    /** A twin mount is ready only when its slower/empty tube is ready. */
    public float progress() { return Math.min(left.progress(), right.progress()); }

    public Marker marker() {
        if (leftMarker == null) return rightMarker;
        if (rightMarker == null) return leftMarker;
        // Keep obstruction/boundary feedback; never average a blocked muzzle with a distant impact.
        if (leftMarker.endpoint != rightMarker.endpoint) {
            if (leftMarker.endpoint == Endpoint.BLOCKED || rightMarker.endpoint == Endpoint.BLOCKED)
                return leftMarker.endpoint == Endpoint.BLOCKED ? leftMarker : rightMarker;
            return leftMarker.point.distanceToSqr(rightMarker.point) > 100 ? leftMarker
                    : new Marker(leftMarker.point.lerp(rightMarker.point, .5), Endpoint.BOUNDARY);
        }
        return new Marker(leftMarker.point.lerp(rightMarker.point, .5), leftMarker.endpoint);
    }

    static void write(CompoundTag tag, MainBatteryView[] mounts) {
        write(tag, KEY, mounts);
    }

    static void write(CompoundTag tag, String key, MainBatteryView[] mounts) {
        var list = new ListTag();
        for (var mount : mounts) {
            var item = new CompoundTag();
            item.putByte("aim", (byte) mount.aim.ordinal());
            item.putIntArray("loads", new int[] { mount.left.remaining, mount.left.total, mount.left.chambered ? 1 : 0,
                    mount.right.remaining, mount.right.total, mount.right.chambered ? 1 : 0 });
            if (mount.leftMarker != null) item.put("left", encode(mount.leftMarker));
            if (mount.rightMarker != null) item.put("right", encode(mount.rightMarker));
            list.add(item);
        }
        tag.put(key, list);
    }

    public static List<MainBatteryView> read(CompoundTag tag) {
        return read(tag, KEY, MOUNTS);
    }

    public static List<MainBatteryView> read(CompoundTag tag, String key, int count) {
        var list = tag.getList(key, Tag.TAG_COMPOUND);
        if (list.size() != count) return List.of();
        var result = new ArrayList<MainBatteryView>(count);
        for (int i = 0; i < count; i++) {
            var item = list.getCompound(i);
            int aim = item.getByte("aim");
            int[] loads = item.getIntArray("loads");
            if (aim < 0 || aim >= AIMS.length || loads.length != 6) return List.of();
            result.add(new MainBatteryView(AIMS[aim],
                    new Load(loads[0], loads[1], loads[2] == 1), new Load(loads[3], loads[4], loads[5] == 1),
                    decode(item.getCompound("left")), decode(item.getCompound("right"))));
        }
        return List.copyOf(result);
    }

    private static CompoundTag encode(Marker marker) {
        var tag = new CompoundTag();
        tag.putByte("kind", (byte) marker.endpoint.ordinal());
        tag.putDouble("x", marker.point.x);
        tag.putDouble("y", marker.point.y);
        tag.putDouble("z", marker.point.z);
        return tag;
    }

    private static Marker decode(CompoundTag tag) {
        if (!tag.contains("kind", Tag.TAG_BYTE) || !tag.contains("x", Tag.TAG_DOUBLE)
                || !tag.contains("y", Tag.TAG_DOUBLE) || !tag.contains("z", Tag.TAG_DOUBLE)) return null;
        int kind = tag.getByte("kind");
        double x = tag.getDouble("x"), y = tag.getDouble("y"), z = tag.getDouble("z");
        return kind >= 0 && kind < ENDPOINTS.length && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Marker(new Vec3(x, y, z), ENDPOINTS[kind]) : null;
    }
}

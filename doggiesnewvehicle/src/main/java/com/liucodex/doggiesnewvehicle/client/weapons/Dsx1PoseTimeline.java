package com.liucodex.doggiesnewvehicle.client.weapons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** DSX1-only, immutable keyframes. Native remaining time is the only action clock. */
public final class Dsx1PoseTimeline {
    public enum Action { IDLE, BOLT, RELOAD_NORMAL, RELOAD_EMPTY }
    public static final String[] BONES = {"0", "camera", "bolt", "rot", "magazine", "Lefthand", "Righthand"};
    private static final String[] CLIPS = {"idle", "bolt", "reload_normal", "reload_empty"};
    public static final Dsx1PoseTimeline EMPTY = new Dsx1PoseTimeline(new EnumMap<>(Action.class));
    private final Map<Action, Clip> clips;

    private Dsx1PoseTimeline(Map<Action, Clip> clips) { this.clips = Map.copyOf(clips); }

    public static final class Frame {
        private final float[] values = new float[BONES.length * 6];
        public float value(int bone, int channel) { return values[bone * 6 + channel]; }
    }

    /** An absent/dead/foreign/third-person view has a neutral pose, regardless of stale timers. */
    public static Action action(boolean activeView, int boltRemaining, int reloadRemaining, boolean emptyReload) {
        if (!activeView) return Action.IDLE;
        if (boltRemaining > 0) return Action.BOLT;
        if (reloadRemaining > 0) return emptyReload ? Action.RELOAD_EMPTY : Action.RELOAD_NORMAL;
        return Action.IDLE;
    }

    public static double progress(int remaining, int duration, float partialTick) {
        if (remaining <= 0 || duration <= 0) return 0;
        double partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        // SBW starts a bolt/reload timer at duration + 1; this also clamps delayed snapshots.
        return Math.max(0, Math.min(1, (duration + 1.0 - remaining + partial) / duration));
    }

    public void sample(Action action, double progress, Frame target) {
        Arrays.fill(target.values, 0);
        Clip clip = clips.get(action);
        if (clip == null) return;
        double time = (Double.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0) * clip.duration;
        for (int i = 0; i < clip.tracks.length; i++) {
            Track track = clip.tracks[i];
            if (track != null) track.sample(time, target.values, i * 3);
        }
    }

    public static Dsx1PoseTimeline read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(131073);
        if (bytes.length > 131072) throw new IOException("DSX1 pose resource exceeds limit");
        try {
            JsonObject animations = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            if (animations.size() != Action.values().length) throw new IllegalArgumentException("Unexpected DSX1 clips");
            var clips = new EnumMap<Action, Clip>(Action.class);
            for (Action action : Action.values()) {
                JsonObject json = animations.getAsJsonObject("animation.dsx1." + CLIPS[action.ordinal()]);
                double duration = json.get("animation_length").getAsDouble();
                if (!Double.isFinite(duration) || duration <= 0 || duration > 10) throw new IllegalArgumentException("Invalid duration");
                Track[] tracks = new Track[BONES.length * 2];
                var bones = json.getAsJsonObject("bones");
                if (bones.size() > BONES.length) throw new IllegalArgumentException("Too many pose bones");
                for (var bone : bones.entrySet()) {
                    int index = Arrays.asList(BONES).indexOf(bone.getKey());
                    if (index < 0) throw new IllegalArgumentException("Unknown pose bone " + bone.getKey());
                    var channels = bone.getValue().getAsJsonObject();
                    for (var channel : channels.entrySet()) {
                        int kind = switch (channel.getKey()) { case "position" -> 0; case "rotation" -> 1; default -> -1; };
                        if (kind < 0) throw new IllegalArgumentException("Unsupported pose channel");
                        tracks[index * 2 + kind] = Track.read(channel.getValue().getAsJsonObject(), duration);
                    }
                }
                clips.put(action, new Clip(duration, tracks));
            }
            return new Dsx1PoseTimeline(clips);
        } catch (RuntimeException e) { throw new IOException("Invalid DSX1 pose resource", e); }
    }

    private record Clip(double duration, Track[] tracks) {}
    private record Track(double[] times, float[][] values, boolean[] linear) {
        static Track read(JsonObject json, double duration) {
            if (json.size() < 1 || json.size() > 128) throw new IllegalArgumentException("Invalid key count");
            var sorted = new java.util.TreeMap<Double, float[]>();
            var linearTimes = new java.util.HashSet<Double>();
            for (var entry : json.entrySet()) {
                double t = Double.parseDouble(entry.getKey());
                if (!Double.isFinite(t) || t < 0 || t > duration) throw new IllegalArgumentException("Invalid key time");
                var vector = entry.getValue().getAsJsonObject().getAsJsonArray("vector");
                if (vector.size() != 3) throw new IllegalArgumentException("Invalid vector");
                float[] v = new float[3];
                for (int i = 0; i < 3; i++) {
                    v[i] = vector.get(i).getAsFloat();
                    if (!Float.isFinite(v[i]) || Math.abs(v[i]) > 180) throw new IllegalArgumentException("Invalid pose value");
                }
                if (sorted.put(t, v) != null) throw new IllegalArgumentException("Duplicate key time");
                var key = entry.getValue().getAsJsonObject();
                if (key.has("lerp_mode")) {
                    if (!key.get("lerp_mode").getAsString().equals("linear")) throw new IllegalArgumentException("Unsupported interpolation");
                    linearTimes.add(t);
                }
            }
            if (sorted.firstKey() != 0 || sorted.lastKey() != duration) throw new IllegalArgumentException("Missing pose endpoints");
            // Every action ends at its authored rest pose: no residual magazine/bolt/hand displacement.
            for (float v : sorted.firstEntry().getValue()) if (v != 0) throw new IllegalArgumentException("Non-neutral start");
            for (float v : sorted.lastEntry().getValue()) if (v != 0) throw new IllegalArgumentException("Non-neutral end");
            double[] times = sorted.keySet().stream().mapToDouble(Double::doubleValue).toArray();
            boolean[] linear = new boolean[times.length];
            for (int i = 0; i < times.length; i++) linear[i] = linearTimes.contains(times[i]);
            return new Track(times, sorted.values().toArray(float[][]::new), linear);
        }
        void sample(double time, float[] out, int offset) {
            int right = Arrays.binarySearch(times, time);
            if (right >= 0) { System.arraycopy(values[right], 0, out, offset, 3); return; }
            right = -right - 1;
            if (right == 0 || right == times.length) {
                System.arraycopy(values[right == 0 ? 0 : right - 1], 0, out, offset, 3); return;
            }
            double u = (time - times[right - 1]) / (times[right] - times[right - 1]);
            // Smooth endpoints without overshooting hand/mechanism contact constraints.
            if (!linear[right - 1]) u = u * u * (3 - 2 * u);
            for (int axis = 0; axis < 3; axis++) out[offset + axis] = (float)(values[right - 1][axis] + u * (values[right][axis] - values[right - 1][axis]));
        }
    }
}

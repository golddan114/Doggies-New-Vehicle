package com.liucodex.doggiesnewvehicle.fx;

/** Stateless eight-sided jet mesh shared by vehicle owners. Detail: 0 hidden, 1 far, 2 near. */
public final class DnvJetMesh {
    @FunctionalInterface public interface Sink { void vertex(double x, double y, double z, float r, float g, float b, float a); }
    private static final double[] X = {1, .7071067811865476, 0, -.7071067811865476, -1, -.7071067811865476, 0, .7071067811865476};
    private static final double[] Y = {0, .7071067811865476, 1, .7071067811865476, 0, -.7071067811865476, -1, -.7071067811865476};
    private DnvJetMesh() { }
    public static void exhaust(Sink sink, double thrust, double radius, double tick, int detail) {
        if (detail == 0 || !Double.isFinite(radius) || radius <= 0 || !Double.isFinite(tick)) return;
        thrust = Double.isFinite(thrust) ? Math.max(0, Math.min(1, thrust)) : 0;
        if (thrust <= 0) return;
        radius = Math.min(radius, 4);
        int step = detail == 2 ? 1 : 2;
        double pulse = 1 + Math.sin(tick * 0.63) * 0.035;
        double length = radius * (1.3 + thrust * 6) * pulse;
        double belly = radius * 0.7;
        frustum(sink, 0, 0, 0, length * 0.22, radius * 0.44, belly * 0.52, step, 0.7f, 0.92f, 1, 0.9f, 0.85f);
        frustum(sink, 0, 0, length * 0.22, length * 0.7, belly * 0.52, radius * 0.1, step, 0.5f, 0.8f, 1, 0.85f, 0.25f);
        frustum(sink, 0, 0, length * 0.7, length, radius * 0.1, 0, step, 0.3f, 0.6f, 1, 0.25f, 0);
        if (step == 1) {
            frustum(sink, 0, 0, 0, length * 0.22, radius * 0.78, belly, 1, 0.05f, 0.35f, 1, 0.25f, 0.3f);
            frustum(sink, 0, 0, length * 0.22, length * 0.7, belly, radius * 0.3, 1, 0.05f, 0.35f, 1, 0.3f, 0.09f);
            frustum(sink, 0, 0, length * 0.7, length, radius * 0.3, 0, 1, 0.05f, 0.35f, 1, 0.09f, 0);
        }
    }

    private static void frustum(Sink sink, double cx, double cy, double z0, double z1, double r0, double r1,
            int step, float red, float green, float blue, float a0, float a1) {
        if (Math.abs(z1 - z0) < 0.000001) return;
        for (int i = 0; i < 8; i += step) {
            int j = (i + step) % 8;
            sink.vertex(cx + X[i] * r0, cy + Y[i] * r0, z0, red, green, blue, a0);
            sink.vertex(cx + X[j] * r0, cy + Y[j] * r0, z0, red, green, blue, a0);
            sink.vertex(cx + X[j] * r1, cy + Y[j] * r1, z1, red, green, blue, a1);
            sink.vertex(cx + X[i] * r1, cy + Y[i] * r1, z1, red, green, blue, a1);
        }
    }

}

package com.liucodex.doggiesnewvehicle.client.mesh;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** Adapts authored triangle streams to the existing entity QUADS render types once per mesh load. */
public final class DnvQuadStream {
    private static final int VERTEX_STRIDE = 8;
    private static final int TRIANGLE_STRIDE = 3 * VERTEX_STRIDE;
    private static final int QUAD_STRIDE = 4 * VERTEX_STRIDE;
    // Resource reload replaces the source arrays. Weak keys let obsolete expanded streams go with them.
    private static final Map<float[], float[]> CACHE = new WeakHashMap<>();

    private DnvQuadStream() { }

    public static float[] quads(float[] triangles) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(triangles, DnvQuadStream::convert);
        }
    }

    static float[] convert(float[] triangles) {
        if (triangles.length % TRIANGLE_STRIDE != 0) {
            throw new IllegalArgumentException("Incomplete triangle stream");
        }
        if (triangles.length == 0) {
            return triangles;
        }
        float[] result = new float[triangles.length / TRIANGLE_STRIDE * QUAD_STRIDE];
        int used = 0;
        for (int first = 0; first < triangles.length; first += TRIANGLE_STRIDE) {
            int second = first + TRIANGLE_STRIDE;
            int[] merge = second < triangles.length ? mergeCorners(triangles, first, second) : null;
            if (merge != null) {
                for (int corner : merge) {
                    System.arraycopy(triangles, corner, result, used, VERTEX_STRIDE);
                    used += VERTEX_STRIDE;
                }
                first = second;
            } else {
                System.arraycopy(triangles, first, result, used, TRIANGLE_STRIDE);
                used += TRIANGLE_STRIDE;
                System.arraycopy(triangles, first + 2 * VERTEX_STRIDE, result, used, VERTEX_STRIDE);
                used += VERTEX_STRIDE;
            }
        }
        return Arrays.copyOf(result, used);
    }

    /**
     * Minecraft draws a quad as (0,1,2) and (2,3,0). The shared diagonal must
     * be corners 0 and 2 so positions, UV interpolation and winding stay exact.
     */
    private static int[] mergeCorners(float[] stream, int first, int second) {
        for (int unique = 0; unique < 3; unique++) {
            int before = first + ((unique + 2) % 3) * VERTEX_STRIDE;
            int middle = first + unique * VERTEX_STRIDE;
            int after = first + ((unique + 1) % 3) * VERTEX_STRIDE;
            if (same(stream, before, middle) || same(stream, middle, after)
                    || same(stream, before, after)) {
                continue;
            }
            for (int start = 0; start < 3; start++) {
                int nextBefore = second + start * VERTEX_STRIDE;
                int nextAfter = second + ((start + 1) % 3) * VERTEX_STRIDE;
                int nextUnique = second + ((start + 2) % 3) * VERTEX_STRIDE;
                if (same(stream, before, nextBefore) && same(stream, after, nextAfter)
                        && !same(stream, nextUnique, middle)
                        && !same(stream, nextUnique, before)
                        && !same(stream, nextUnique, after)) {
                    return new int[] {before, middle, after, nextUnique};
                }
            }
        }
        return null;
    }

    private static boolean same(float[] stream, int left, int right) {
        for (int index = 0; index < VERTEX_STRIDE; index++) {
            if (Float.floatToRawIntBits(stream[left + index])
                    != Float.floatToRawIntBits(stream[right + index])) {
                return false;
            }
        }
        return true;
    }
}

package com.liucodex.doggiesnewvehicle.boss;

/** Deterministic expanding cube shells, clipped to the blast sphere by the world adapter. */
public final class MothershipBlastFront {
    public static final int RADIUS = 16;
    public static final int MAX_VISITS_PER_TICK = 4096;
    public static final int MAX_BLOCKS_PER_TICK = 256;
    public static final int TOTAL = (RADIUS * 2 + 1) * (RADIUS * 2 + 1) * (RADIUS * 2 + 1);
    public record Cell(int x, int y, int z) { }
    private final int total;
    private int cursor;

    public MothershipBlastFront() { this(RADIUS); }
    public MothershipBlastFront(int radius) {
        if (radius < 1 || radius > RADIUS) throw new IllegalArgumentException("Invalid blast radius");
        total = cube(2 * radius + 1);
        cursor = total;
    }

    public void begin() { cursor = 0; }
    public boolean active() { return cursor < total; }
    public int cursor() { return cursor; }
    public void restore(int value) { cursor = value >= 0 && value <= total ? value : total; }
    public Cell next() {
        if (!active()) throw new IllegalStateException("Blast front is complete");
        int index = cursor++; // Consume before the world side effect; a resumed batch never replays cells.
        if (index == 0) return new Cell(0, 0, 0);
        int radius = 1;
        while (cube(2 * radius + 1) <= index) radius++;
        int width = radius * 2 + 1, inside = width - 2;
        int local = index - cube(inside), plane = width * width;
        if (local < plane * 2) {
            int face = local / plane; local %= plane;
            return new Cell(local % width - radius, local / width - radius, face == 0 ? -radius : radius);
        }
        local -= plane * 2; plane = width * inside;
        if (local < plane * 2) {
            int face = local / plane; local %= plane;
            return new Cell(local % width - radius, face == 0 ? -radius : radius, local / width - radius + 1);
        }
        local -= plane * 2; plane = inside * inside;
        int face = local / plane; local %= plane;
        return new Cell(face == 0 ? -radius : radius, local % inside - radius + 1, local / inside - radius + 1);
    }
    private static int cube(int value) { return value * value * value; }
}

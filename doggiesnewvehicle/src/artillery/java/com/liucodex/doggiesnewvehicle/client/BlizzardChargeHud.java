package com.liucodex.doggiesnewvehicle.client;

import net.minecraft.client.gui.GuiGraphics;

/** A narrow rounded column beside the native reticle; only reads the server's charge snapshot. */
public final class BlizzardChargeHud {
    private BlizzardChargeHud() { }
    public static void draw(GuiGraphics graphics, int centerX, int centerY, int phase, float progress) {
        int x = centerX + 30, top = centerY - 20;
        float bounded = Float.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        int height = Math.round(38 * bounded);
        int color = phase == 2 ? 0xFFE55F62 : 0xFF46BFEA;
        int light = phase == 2 ? 0xFFFFA2A3 : 0xFFA2E7FF;
        for (int row = 0; row < 42; row++) {
            int inset = row == 0 || row == 41 ? 2 : row == 1 || row == 40 ? 1 : 0;
            graphics.fill(x + inset, top + row, x + 7 - inset, top + row + 1, 100, 0xDDE4EDF0);
            if (row >= 2 && row < 40) {
                graphics.fill(x + 1, top + row, x + 6, top + row + 1, 100, 0xD016242C);
                if (row >= 40 - height) {
                    graphics.fill(x + 1, top + row, x + 6, top + row + 1, 100, color);
                    graphics.fill(x + 2, top + row, x + 3, top + row + 1, 100, light);
                }
            }
        }
    }
}

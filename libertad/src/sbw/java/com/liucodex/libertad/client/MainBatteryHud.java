package com.liucodex.libertad.client;

import com.liucodex.libertad.ship.MainBatteryView;
import com.liucodex.libertad.ship.MainBatteryView.Marker;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Projects the last confirmed mount samples using the camera for this frame. */
final class MainBatteryHud {
    private MainBatteryHud() {}

    record Projection(int shipId, Vec3 eye, Matrix4f clip) {
        ScreenPoint project(Vec3 point, int width, int height) {
            Vec3 relative = point.subtract(eye);
            var q = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1).mul(clip);
            if (!(q.w > 1e-4f) || q.z < -q.w) return null;
            float x = q.x / q.w, y = q.y / q.w;
            if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
            float scale = Math.max(1, Math.max(Math.abs(x), Math.abs(y)) / .93f);
            return new ScreenPoint(Math.round((.5f + .5f * x / scale) * width),
                    Math.round((.5f - .5f * y / scale) * height), scale > 1);
        }
    }

    record ScreenPoint(int x, int y, boolean edge) {}

    static void draw(GuiGraphics gui, Font font, List<MainBatteryView> battery, int readyMask,
                     Component secondaryHint, int vanillaHudHeight, Projection view) {
        int width = gui.guiWidth(), height = gui.guiHeight();
        if (width < 96 || height < 85) return;
        int y = barTop(height, vanillaHudHeight);
        if (view != null) {
            for (var mount : battery) marker(gui, view, mount, Math.max(7, y - 7));
        }
        cross(gui, width / 2, height / 2, 6, 0xFFF0F6FA);
        if (y < 0) return;
        int cell = Math.min(34, (width - 10) / Math.max(1, battery.size()));
        int start = (width - cell * battery.size()) / 2;
        for (int i = 0; i < battery.size(); i++) {
            bar(gui, start + i * cell + 4, y, cell - 8, battery.get(i), (readyMask & (1 << i)) != 0);
        }
        gui.drawCenteredString(font, font.plainSubstrByWidth(secondaryHint.getString(), width - 10),
                width / 2, hintTop(y), 0xF2F5F8);
    }

    static int barTop(int height, int vanillaHudHeight) {
        return height - Math.max(39, vanillaHudHeight - 6) - 16;
    }

    static int hintTop(int barTop) { return barTop + 5; }

    static String secondaryHintKey(int mode) {
        return switch (mode) {
            case 1 -> "hud.libertad.secondary.disable";
            case 2 -> "hud.libertad.secondary.returning";
            default -> "hud.libertad.secondary.enable";
        };
    }

    static int reloadOpacity(boolean canFire, float progress) {
        return canFire && progress == 1 ? 0xFF : 0x59;
    }

    private static void bar(GuiGraphics gui, int x, int y, int width, MainBatteryView mount, boolean canFire) {
        float progress = mount.progress();
        int opacity = reloadOpacity(canFire, progress) << 24;
        int filled = (int) Math.floor(width * progress);
        if (filled < width) gui.fill(x + filled, y, x + width, y + 2, opacity | 0x22333F);
        if (filled > 0) {
            int color = mount.left().reloading() || mount.right().reloading() ? 0xF0CC81 : 0x80D3AC;
            gui.fill(x, y, x + filled, y + 2, opacity | color);
        }
    }

    private static void marker(GuiGraphics gui, Projection view, MainBatteryView mount, int ceiling) {
        Marker marker = mount.marker();
        if (marker == null) return;
        var point = view.project(marker.point(), gui.guiWidth(), gui.guiHeight());
        if (point == null) return;
        int x = Math.max(6, Math.min(point.x, gui.guiWidth() - 6));
        int y = Math.max(6, Math.min(point.y, ceiling));
        int color = switch (marker.endpoint()) {
            case CONTACT -> 0xFFA9E9C5;
            case RANGE -> 0xFFF5D895;
            case BOUNDARY, WATER -> 0xFFB1DFF7;
            case BLOCKED -> 0xFFF3AA97;
        };
        cross(gui, x, y, 4, color);
    }

    private static void cross(GuiGraphics gui, int x, int y, int arm, int color) {
        int outline = 0xFF192731;
        gui.fill(x - arm - 1, y - 1, x + arm + 2, y + 2, outline);
        gui.fill(x - 1, y - arm - 1, x + 2, y + arm + 2, outline);
        gui.fill(x - arm, y, x + arm + 1, y + 1, color);
        gui.fill(x, y - arm, x + 1, y + arm + 1, color);
    }
}

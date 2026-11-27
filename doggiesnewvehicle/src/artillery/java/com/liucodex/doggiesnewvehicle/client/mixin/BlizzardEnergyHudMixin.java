package com.liucodex.doggiesnewvehicle.client.mixin;

import com.atsuishio.superbwarfare.client.overlay.VehicleHudOverlay;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.liucodex.doggiesnewvehicle.armored.BlizzardRules;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Preserve native energy/health bars; bound only Blizzard's displayed FE, never its stored FE. */
@Mixin(value = VehicleHudOverlay.class, remap = false)
public abstract class BlizzardEnergyHudMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;getEnergy()I"), require = 1)
    private int dnv$hudEnergy(VehicleEntity vehicle) {
        int energy=vehicle.getEnergy();
        return vehicle instanceof DnvBlizzardEntity ? Math.max(0,Math.min(energy,vehicle.getMaxEnergy())) : energy;
    }

    // The native speed text occupies the same row at x=85. Move only Blizzard's text to the health row.
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/client/overlay/VehicleHudOverlay;renderSpeedInfo("
            + "Lnet/minecraft/client/gui/GuiGraphics;Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;IIFI)V"),
            index = 5, require = 1)
    private int dnv$jumpSpeedRow(GuiGraphics graphics, VehicleEntity vehicle,
            int width, int height, float partial, int armorOffset) {
        return vehicle instanceof DnvBlizzardEntity ? armorOffset - 9 : armorOffset;
    }

    // Follow the native energy draw and its armor-plate offset/visibility gate.
    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/client/RenderHelper;preciseBlit(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;FFFFFFFFF)V",
            ordinal = 2, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void dnv$jumpCharge(RenderContext context, CallbackInfo ci,
            Entity vehicle, PoseStack pose, int armorOffset) {
        if (!(vehicle instanceof DnvBlizzardEntity blizzard)) return;
        int remaining = Math.max(0, Math.min(BlizzardRules.JUMP_COOLDOWN, blizzard.jumpCooldownTicks()));
        int width = 36 * (BlizzardRules.JUMP_COOLDOWN - remaining) / BlizzardRules.JUMP_COOLDOWN;
        boolean blocked = !blizzard.isAlive() || blizzard.isWreck() || !blizzard.hovering()
                || blizzard.hasTv() || blizzard.getEnergy() < BlizzardRules.JUMP_FE;
        int color = blocked ? 0xFF74828B : remaining == 0 ? 0xFF8EEAFF : 0xFF409CB5;
        int y = context.getScreenHeight() - 21 - armorOffset;
        var graphics = context.getGuiGraphics();
        // Small ascent arrow followed by a 38x6 bar, six pixels beyond the native FE bar.
        graphics.fill(88, y, 89, y + 1, 100, color);
        graphics.fill(87, y + 1, 90, y + 2, 100, color);
        graphics.fill(86, y + 2, 91, y + 3, 100, color);
        graphics.fill(88, y + 3, 89, y + 6, 100, color);
        graphics.fill(94, y, 132, y + 6, 100, blocked ? 0xFF46525A : 0xFF86A4AF);
        graphics.fill(95, y + 1, 131, y + 5, 100, 0xD0101B23);
        if (width > 0) graphics.fill(95, y + 1, 95 + width, y + 5, 100, color);
    }
}

package com.liucodex.doggiesnewvehicle.weapons;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.item.gun.sniper.AwmItem;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.object.PlayState;

/** SBW owns ammunition, chamber, reload, bolt timers, sounds and persistence. */
public final class Dsx1Item extends AwmItem implements com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotVisual {
    private static volatile Supplier<? extends GeoItemRenderer<? extends Item>> renderer;

    @Override public void prepareShotVisual(net.minecraft.world.entity.Entity projectile, net.minecraft.world.phys.Vec3 origin) {
        if (projectile instanceof com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotVisual.Trail trail)
            trail.dnv$startDsx1Trail(origin, com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork::send);
    }

    /** Installed on the client during construction, before item registration calls initializeClient. */
    public static void installRenderer(Supplier<? extends GeoItemRenderer<? extends Item>> factory) {
        renderer = Objects.requireNonNull(factory, "factory");
    }

    @Override public Supplier<? extends GeoItemRenderer<? extends Item>> getRenderer() {
        return Objects.requireNonNull(renderer, "DSX1 client renderer must be installed before item registration");
    }

    @Override public void init(GunData data) {
        if (isInitialized(data)) return;
        super.init(data);
        data.attachment.set(AttachmentType.SCOPE, 3);
    }

    @Override public int[] getValidScopes() { return new int[] {3}; }
    @Override public int[] getValidMagazines() { return new int[] {0}; }
    @Override public int[] getValidBarrels() { return new int[] {0}; }
    @Override public boolean canEditAttachments(GunData data) { return false; }
    @Override public boolean hasBipod(GunData data) { return false; }
    @Override public boolean hasCustomBarrel(GunData data) { return false; }
    @Override public boolean hasCustomMagazine(GunData data) { return false; }
    @Override public int getCustomMagazine(GunData data) { return 0; }

    /** DSX1 projects native remaining timers in its model; no AWM playback clock is retained. */
    @Override public void registerControllers(ControllerRegistrar registrar) {
        registrar.add(new AnimationController<AwmItem>(this, "dsx1_native_pose", 0, state -> PlayState.STOP));
    }
}

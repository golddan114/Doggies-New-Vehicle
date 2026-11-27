package com.liucodex.doggiesnewvehicle.client.weapons;

import com.atsuishio.superbwarfare.client.model.item.CustomGunModel;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.item.gun.sniper.AwmItem;
import com.liucodex.doggiesnewvehicle.weapons.Dsx1Item;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;

/** DSX1 poses are a stateless projection of native timers, including after re-equipping. */
public final class Dsx1Model extends CustomGunModel<AwmItem> {
    private final Dsx1PoseTimeline.Frame frame = new Dsx1PoseTimeline.Frame();
    private static final String[] FROZEN_BONES = {"root", "bone", "fire", "0", "camera", "bolt", "rot", "magazine", "Lefthand", "Righthand"};
    private float[][] frozen;
    private long frozenId;
    private Object frozenLevel, frozenPerspective;
    private double displayAds;
    private double frozenAds;
    public double displayAds() { return displayAds; }

    @Override public void handleAnimations(AwmItem item, long instanceId, AnimationState<AwmItem> state) {
        // This DSX1 projection has no playback clock. Evaluate even when another perspective
        // rendered the same item in the same frame (the native model's fast path skips that).
        setCustomAnimations(item, instanceId, state);
    }

    @Override public void setCustomAnimations(AwmItem item, long instanceId, AnimationState<AwmItem> state) {
        var processor = getAnimationProcessor();
        var player = Minecraft.getInstance().player;
        var rendered = state.getData(DataTickets.ITEMSTACK);
        var perspective = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        boolean active = Minecraft.getInstance().level != null && player != null && player.isAlive() && rendered != null && rendered.getItem() instanceof Dsx1Item
                && perspective != null && perspective.firstPerson() && player.getMainHandItem().getItem() == item
                && GeoItem.getId(rendered) == instanceId && GeoItem.getId(player.getMainHandItem()) == instanceId;
        var client = Minecraft.getInstance();
        if (active && client.isPaused() && frozen != null && frozenId == instanceId
                && frozenLevel == client.level && frozenPerspective == perspective) {
            for (int i = 0; i < FROZEN_BONES.length; i++) Dsx1BonePose.restore(processor.getBone(FROZEN_BONES[i]), frozen[i]);
            displayAds = frozenAds;
            return;
        }
        for (String name : FROZEN_BONES) Dsx1BonePose.reset(processor.getBone(name));
        if (!active) {
            if (frozenLevel != client.level || player == null || !player.isAlive()
                    || !(player.getMainHandItem().getItem() instanceof Dsx1Item)) { frozen = null; frozenLevel = null; }
            displayAds = 0; return;
        }
        var data = GunData.from(rendered);
        int bolt = data.bolt.actionTimer.get();
        int reload = data.reload.normal() || data.reload.empty() ? data.reload.time() : 0;
        var action = Dsx1PoseTimeline.action(true, bolt, reload, data.reload.empty());
        int remaining = action == Dsx1PoseTimeline.Action.BOLT ? bolt : reload;
        int duration = switch (action) {
            case BOLT -> data.get(GunProp.BOLT_ACTION_TIME);
            case RELOAD_NORMAL -> data.get(GunProp.NORMAL_RELOAD_TIME);
            case RELOAD_EMPTY -> data.get(GunProp.EMPTY_RELOAD_TIME);
            default -> 0;
        };
        double progress = Dsx1PoseTimeline.progress(remaining, duration, client.getFrameTime());
        Dsx1PoseCache.get().sample(action, progress, frame);
        double zoom = Dsx1ViewPose.ads(ClientEventHandler.zoomPos, action != Dsx1PoseTimeline.Action.IDLE, progress);
        for (int i = 0; i < Dsx1PoseTimeline.BONES.length; i++) {
            String name = Dsx1PoseTimeline.BONES[i];
            boolean wholeGun = name.equals("0") || name.equals("camera");
            Dsx1BonePose.apply(processor.getBone(name), frame, i,
                    wholeGun ? Dsx1ViewPose.actionTranslation(zoom) : 1,
                    wholeGun ? Dsx1ViewPose.actionRotation(zoom) : 1);
        }
        var gun = processor.getBone("0");
        var camera = processor.getBone("camera");
        displayAds = zoom;
        // Compress in the animated gun's local axes AFTER its rotation. Scaling an ancestor
        // first turns small reload rotations into a sheared optical axis and a flat frame.
        gun.setScaleZ(Dsx1ViewPose.depthScale(zoom));
        ClientEventHandler.gunRootMove(processor, 0, 0, 0, false);
        ClientEventHandler.handleShootAnimation(processor.getBone("fire"), .5f, 2, 3, 2.5f, .3f, .5f, .4f, .45f);
        ClientEventHandler.handleReloadShake(Math.toDegrees(camera.getRotX()), Math.toDegrees(camera.getRotY()), Math.toDegrees(camera.getRotZ()));
        if (frozen == null) frozen = new float[FROZEN_BONES.length][];
        for (int i = 0; i < FROZEN_BONES.length; i++) frozen[i] = Dsx1BonePose.capture(processor.getBone(FROZEN_BONES[i]), frozen[i]);
        frozenId = instanceId; frozenLevel = client.level; frozenPerspective = perspective;
        frozenAds = displayAds;
    }
}

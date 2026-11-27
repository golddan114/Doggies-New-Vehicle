package com.liucodex.doggiesnewvehicle.client.weapons;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Reload lifetime only; holds no player/item/action timer. */
final class Dsx1PoseCache {
    private static final ResourceLocation RESOURCE = new ResourceLocation(DoggiesNewVehicle.MOD_ID, "animations/dsx1.animation.json");
    private static volatile Dsx1PoseTimeline timeline;
    private Dsx1PoseCache() {}
    static Dsx1PoseTimeline get() {
        var result = timeline;
        if (result != null) return result;
        synchronized (Dsx1PoseCache.class) {
            if (timeline == null) {
                try (var input = Minecraft.getInstance().getResourceManager().open(RESOURCE)) {
                    timeline = Dsx1PoseTimeline.read(input);
                } catch (IOException | RuntimeException e) {
                    LogUtils.getLogger().error("Cannot load DSX1 poses {}; retry on resource reload", RESOURCE, e);
                    timeline = Dsx1PoseTimeline.EMPTY;
                }
            }
            return timeline;
        }
    }
    static synchronized void clear() { timeline = null; }
}

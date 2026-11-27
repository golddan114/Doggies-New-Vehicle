package com.liucodex.doggiesnewvehicle.client.weapons;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Dsx1MeshCache {
    private static final ResourceLocation MESH = new ResourceLocation(DoggiesNewVehicle.MOD_ID, "models/item/dsx1.dsm");
    private static volatile Dsx1Mesh mesh;
    private static volatile Dsx1Optics optics;
    private Dsx1MeshCache() {}

    public static Dsx1Mesh get() {
        Dsx1Mesh result = mesh;
        if (result != null) return result;
        synchronized (Dsx1MeshCache.class) {
            if (mesh == null) {
                try (var input = Minecraft.getInstance().getResourceManager().open(MESH)) {
                    var loaded = Dsx1Mesh.read(input);
                    optics = Dsx1Optics.from(loaded.part("dsx_glass"));
                    mesh = loaded;
                } catch (IOException | RuntimeException e) {
                    LogUtils.getLogger().error("Cannot load DSX1 mesh {}; retry on resource reload", MESH, e);
                    mesh = Dsx1Mesh.EMPTY;
                }
            }
            return mesh;
        }
    }

    public static Dsx1Optics optics() { get(); return optics; }

    @SubscribeEvent public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
    }

    private static synchronized void clear() { mesh = null; optics = null; Dsx1PoseCache.clear(); Dsx1TrailRenderer.clear(); }
}

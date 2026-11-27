package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.api.event.LoadingJsonEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Optional artillery registration installs this adapter only when the module is assembled. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvArtilleryConfiguration {
    private static final DnvArtillerySettings SETTINGS = initialize();
    private DnvArtilleryConfiguration() { }
    private static DnvArtillerySettings initialize() {
        DnvArtillerySettings selected;
        try { selected = DnvArtillerySettings.load(FMLPaths.CONFIGDIR.get().resolve("doggiesnewvehicle/artillery")); }
        catch (java.io.IOException | IllegalArgumentException failure) {
            com.mojang.logging.LogUtils.getLogger().error("DNV炮塔配置无效，整份使用包内默认：{}", failure.getMessage());
            selected = DnvArtillerySettings.packaged();
        }
        return selected;
    }
    @SubscribeEvent public static void load(LoadingJsonEvent event) {
        if (!event.getId().startsWith("doggievehicle:")) return;
        var input = event.getAsGsonObject();
        var output = SETTINGS.apply(event.getId(), input);
        if (output != input) event.setJsonStr(output.toString());
    }
}

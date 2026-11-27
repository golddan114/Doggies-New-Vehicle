package com.liucodex.doggiesnewvehicle.neoforge;

import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class DnvNeoDist {
    private DnvNeoDist() {}
    public static void unsafeRunWhenOn(Dist side, Supplier<Runnable> work) {
        if (FMLEnvironment.dist == side) work.get().run();
    }
}

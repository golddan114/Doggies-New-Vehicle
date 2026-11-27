package com.liucodex.doggiesnewvehicle;

import com.liucodex.doggiesnewvehicle.init.ModEntities;
import com.liucodex.doggiesnewvehicle.init.ModCreativeTabs;
import com.liucodex.doggiesnewvehicle.init.ModItems;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleAutonomousFireNetwork;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewConfigNetwork;
import com.liucodex.doggiesnewvehicle.compat.DnvManagedVehicleNetwork;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatUiNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(DoggiesNewVehicle.MOD_ID)
public final class DoggiesNewVehicle {
    public static final String MOD_ID = "doggiesnewvehicle";
    public static final String ENTITY_NAMESPACE = "doggievehicle";

    public DoggiesNewVehicle(FMLJavaModLoadingContext loadingContext) {
        DnvVehicleAutonomousFireNetwork.initialize();
        DnvVehicleCrewConfigNetwork.initialize();
        DnvManagedVehicleNetwork.initialize();
        DnvCombatUiNetwork.initialize();
        IEventBus modBus = loadingContext.getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        com.liucodex.doggiesnewvehicle.world.DnvControlledFire.BLOCKS.register(modBus);
        registerOptionalArtillery(modBus);
        registerOptionalPlatform(modBus);
        registerOptionalBoss(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(DoggiesNewVehicle::onServerStopping);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        DnvCombatCoordinator.clearRuntimeState();
    }

    private static void registerOptionalArtillery(IEventBus modBus) {
        try {
            Class.forName("com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryContent")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ClassNotFoundException absent) {
            // A base-only assembly has no artillery classes or registrations.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("DNV artillery registration failed", failure);
        }
    }

    private static void registerOptionalPlatform(IEventBus modBus) {
        try {
            Class.forName("com.liucodex.doggiesnewvehicle.platform.DnvPlatformContent")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ClassNotFoundException absent) {
            // The base-only assembly has no platform classes or registrations.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("DNV platform registration failed", failure);
        }
    }

    private static void registerOptionalBoss(IEventBus modBus) {
        try {
            Class.forName("com.liucodex.doggiesnewvehicle.boss.DnvBossContent")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ClassNotFoundException absent) {
            // The base-only assembly has no boss content or registrations.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("DNV boss registration failed", failure);
        }
    }

}

package com.liucodex.doggiesnewvehicle.artillery;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DnvArtillerySounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<SoundEvent> CANNON_FIRE_01 =
            SOUND_EVENTS.register(
                    "cannon_fire_01",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    DoggiesNewVehicle.MOD_ID, "cannon_fire_01")));
    public static final RegistryObject<SoundEvent> M1_CANNON_FIRE =
            register("m1_cannon_fire");
    public static final RegistryObject<SoundEvent> BLIZZARD_FIRE = registerFixed("blizzard_fire", 48F);
    public static final RegistryObject<SoundEvent> BLIZZARD_HOVER = registerFixed("blizzard_hover", 24F);
    public static final RegistryObject<SoundEvent> BLIZZARD_IDLE = registerFixed("blizzard_idle", 24F);
    public static final RegistryObject<SoundEvent> BLIZZARD_LASER = registerFixed("blizzard_laser", 80F);
    public static final RegistryObject<SoundEvent> BLIZZARD_CHARGE = registerFixed("blizzard_charge", 32F);
    public static final RegistryObject<SoundEvent> NUCLEAR_READY = registerFixed("nuclear_ready", 64F);
    public static final RegistryObject<SoundEvent> NUCLEAR_READY_VOICE = register("nuclear_ready_voice");
    public static final RegistryObject<SoundEvent> NUCLEAR_SIREN = registerFixed("nuclear_siren", 128F);
    public static final RegistryObject<SoundEvent> NUCLEAR_LAUNCH = registerFixed("nuclear_launch", 256F);
    public static final RegistryObject<SoundEvent> NUCLEAR_EXPLOSION = registerFixed("nuclear_explosion", 256F);
    public static final RegistryObject<SoundEvent> NUCLEAR_TURNTABLE = registerFixed("nuclear_turntable", 64F);
    public static final RegistryObject<SoundEvent> NUCLEAR_COVER = registerFixed("nuclear_cover", 64F);
    public static final RegistryObject<SoundEvent> NUCLEAR_LIFT = registerFixed("nuclear_lift", 64F);
    public static final RegistryObject<SoundEvent> DOGEGG_EXTEND = registerFixed("dogegg_extend",64F);
    public static final RegistryObject<SoundEvent> DOGEGG_READY = registerFixed("dogegg_ready",64F);
    public static final RegistryObject<SoundEvent> DOGEGG_LAUNCH = registerFixed("dogegg_launch",128F);
    public static final RegistryObject<SoundEvent> DOGEGG_IMPACT = registerFixed("dogegg_impact",128F);

    public static final RegistryObject<SoundEvent> JINGZHE_ACTIVATE =
            register("jingzhe_activate");
    public static final RegistryObject<SoundEvent> JINGZHE_ATTACK =
            registerFixed("jingzhe_attack", 48.0F);
    public static final RegistryObject<SoundEvent> JINGZHE_IDLE =
            registerFixed("jingzhe_idle", 16.0F);

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(
                                DoggiesNewVehicle.MOD_ID, name)));
    }

    private static RegistryObject<SoundEvent> registerFixed(String name, float range) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createFixedRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(
                                DoggiesNewVehicle.MOD_ID, name),
                        range));
    }

    private DnvArtillerySounds() {
    }
}

package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NuclearEffects {
    public static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,DoggiesNewVehicle.MOD_ID);
    public static final RegistryObject<MobEffect> RADIATION=EFFECTS.register("nuclear_radiation",NuclearRadiationEffect::new);
    private NuclearEffects(){ }
}

package com.liucodex.silentheaven.init;

import com.liucodex.silentheaven.SilentHeavenContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final float VOICE_RANGE = 48.0F;

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
            ForgeRegistries.SOUND_EVENTS, SilentHeavenContent.ASSET_NAMESPACE);

    public static final RegistryObject<SoundEvent> STORM_AUTOCANNON_FIRE =
            registerFixedRange("storm_autocannon_fire", 25.0F);

    public static final RegistryObject<SoundEvent> VOICE_READY_FOR_BATTLE = voice("ready_for_battle");
    public static final RegistryObject<SoundEvent> VOICE_ATTACK = voice("attack");
    public static final RegistryObject<SoundEvent> VOICE_DESTROY = voice("destroy");
    public static final RegistryObject<SoundEvent> VOICE_TERMINATE = voice("terminate");
    public static final RegistryObject<SoundEvent> VOICE_FOCUS_ON_TARGET = voice("focus_on_target");
    public static final RegistryObject<SoundEvent> VOICE_RESETTING = voice("resetting");
    public static final RegistryObject<SoundEvent> VOICE_MOVING = voice("moving");
    public static final RegistryObject<SoundEvent> VOICE_DESTINATION_SET = voice("destination_set");
    public static final RegistryObject<SoundEvent> VOICE_I_MOVE = voice("i_move");
    public static final RegistryObject<SoundEvent> VOICE_ENEMY_OUT_OF_RANGE = voice("enemy_out_of_range");
    public static final RegistryObject<SoundEvent> VOICE_TARGET_ACQUIRED = voice("target_acquired");
    public static final RegistryObject<SoundEvent> VOICE_SET_TO_KILL = voice("set_to_kill");
    public static final RegistryObject<SoundEvent> VOICE_ENEMY_ENGAGED = voice("enemy_engaged");
    public static final RegistryObject<SoundEvent> VOICE_HELLO = voice("hello");
    public static final RegistryObject<SoundEvent> VOICE_I_AM_READY = voice("i_am_ready");
    public static final RegistryObject<SoundEvent> VOICE_SYSTEMS_ONLINE = voice("systems_online");
    public static final RegistryObject<SoundEvent> VOICE_RECEIVING_DAMAGE = voice("receiving_damage");
    public static final RegistryObject<SoundEvent> VOICE_UNDER_ATTACK = voice("under_attack");
    public static final RegistryObject<SoundEvent> VOICE_SYSTEMS_DAMAGED = voice("systems_damaged");

    private static RegistryObject<SoundEvent> voice(String name) {
        return registerFixedRange("voice_" + name, VOICE_RANGE);
    }

    private static RegistryObject<SoundEvent> registerFixedRange(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(
                        SilentHeavenContent.ASSET_NAMESPACE, name), range));
    }

    private ModSounds() {
    }
}

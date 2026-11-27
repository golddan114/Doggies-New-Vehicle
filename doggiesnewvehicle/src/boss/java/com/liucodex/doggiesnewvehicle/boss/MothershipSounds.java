package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Boss-owned copies of the requested samples keep the optional artillery module optional. */
public final class MothershipSounds {
    public static final int DOOMSDAY_RANGE = 256;
    // Volume above one extends vanilla attenuation; playback gain itself is clamped to one.
    // Authored mono sample dynamics determine the actual phase loudness.
    public static final float LOCK_VOLUME = 1.6F;
    public static final float DOOMSDAY_VOLUME = 3F;
    public static final float ABORT_VOLUME = 1.6F;
    public static final int WEAPON_RANGE = 192;
    public static final float AUTOCANNON_VOLUME = .4F;
    public static final float ROCKET_VOLUME = .7F;
    public static final float LASER_VOLUME = .65F;
    public static final int IDLE_RANGE = 96;
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "dnv_mothership");
    public static final RegistryObject<SoundEvent> LOCK = register("lock");
    public static final RegistryObject<SoundEvent> CHARGE = register("charge");
    public static final RegistryObject<SoundEvent> FIRE = register("fire");
    public static final RegistryObject<SoundEvent> COOL = register("cool");
    public static final RegistryObject<SoundEvent> ABORT = register("abort");
    public static final RegistryObject<SoundEvent> BLAST = register("blast");
    public static final RegistryObject<SoundEvent> IDLE = register("idle");
    public static final RegistryObject<SoundEvent> PROPULSION = register("propulsion");
    public static final RegistryObject<SoundEvent> AUTOCANNON_FIRE = register("autocannon_fire");
    public static final RegistryObject<SoundEvent> ROCKET_FIRE = register("rocket_fire");
    public static final RegistryObject<SoundEvent> LASER_FIRE = register("laser_fire");
    public static final float LOCK_PITCH = 0.82F;
    private MothershipSounds() { }

    /** The dimension-unique ship owns these phase IDs. Stop them for every listener on cancellation. */
    public static void stopDoomsday(net.minecraft.world.entity.Entity ship) {
        if(!(ship.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        for(var player:level.players())for(String phase:java.util.List.of("lock","charge","fire","cool","abort")) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                    ResourceLocation.fromNamespaceAndPath("dnv_mothership",phase),net.minecraft.sounds.SoundSource.HOSTILE));
        }
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath("dnv_mothership", name),
                switch (name) {
                    case "lock", "charge", "fire", "cool", "blast", "abort" -> DOOMSDAY_RANGE;
                    case "idle", "propulsion" -> IDLE_RANGE;
                    default -> WEAPON_RANGE;
                }));
    }
}

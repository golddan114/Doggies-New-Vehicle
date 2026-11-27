package com.liucodex.libertad;

import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.liucodex.libertad.compat.sbw.NativeShells;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.ship.ShipPresence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Libertad.ID)
public final class Libertad {
    public static final String ID = "libertad";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    public static final RegistryObject<SoundEvent> MAIN_CANNON_FIRE = SOUNDS.register("main_cannon_fire", () ->
            mainCannonSound());
    public static final RegistryObject<SoundEvent> AIR_DEFENSE_FIRE = SOUNDS.register("air_defense_fire", () ->
            airDefenseSound());

    static SoundEvent airDefenseSound() {
        return SoundEvent.createFixedRangeEvent(new ResourceLocation(ID, "air_defense_fire"),
                com.liucodex.libertad.naval.AirDefenseRules.FIRE_SOUND_RANGE);
    }

    static SoundEvent mainCannonSound() {
        // Default dispatch was 16 * volume = 96 blocks, shorter than the 150-block ship.
        // Match the existing 40 * 6 = 240-block audible falloff without increasing loudness.
        return SoundEvent.createFixedRangeEvent(new ResourceLocation(ID, "main_cannon_fire"), 256);
    }
    public static final RegistryObject<EntityType<LibertadEntity>> SHIP = ENTITIES.register("libertad", () ->
            EntityType.Builder.<LibertadEntity>of(LibertadEntity::new, MobCategory.MISC)
                    .sized(18, 14).clientTrackingRange(28).updateInterval(2).build("libertad:libertad"));
    public static final RegistryObject<Item> DEPLOYER = ITEMS.register("libertad_deployer", () -> new ShipDeployer(new Item.Properties().stacksTo(1)));
    public static final net.minecraftforge.network.simple.SimpleChannel NETWORK = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ID, "controls")).networkProtocolVersion(() -> "3")
            .clientAcceptedVersions("3"::equals).serverAcceptedVersions("3"::equals).simpleChannel();

    private static void addContainers(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().toString().equals("doggiesnewvehicle:doggies_vehicle")) {
            event.accept(ContainerBlockItem.createInstance(SHIP.get()));
        }
    }

    public Libertad() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        bus.addListener(Libertad::addContainers);
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(ShipPresence.class);
        MinecraftForge.EVENT_BUS.register(NativeShells.class);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ForgeChunkManager.setForcedChunkLoadingCallback(ID, (level, helper) ->
                    java.util.List.copyOf(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));
            NETWORK.messageBuilder(ToggleSecondaries.class, 0, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((packet, buffer) -> buffer.writeVarInt(packet.shipId()))
                    .decoder(buffer -> new ToggleSecondaries(buffer.readVarInt()))
                    .consumerMainThread((packet, context) -> {
                        var player = context.get().getSender();
                        if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId()) {
                            ship.toggleSecondaries(player);
                        }
                        context.get().setPacketHandled(true);
                    }).add();
            NETWORK.messageBuilder(ToggleCrewMode.class, 1, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((packet, buffer) -> buffer.writeVarInt(packet.shipId()))
                    .decoder(buffer -> new ToggleCrewMode(buffer.readVarInt()))
                    .consumerMainThread((packet, context) -> {
                        var player = context.get().getSender();
                        if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId()) {
                            ship.toggleCrewMode(player);
                        }
                        context.get().setPacketHandled(true);
                    }).add();
            NETWORK.messageBuilder(SideInput.class, 2, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((packet, buffer) -> {
                        buffer.writeVarInt(packet.shipId()).writeVarLong(packet.generation());
                        buffer.writeBoolean(packet.high()).writeBoolean(packet.shoot());
                    })
                    .decoder(buffer -> new SideInput(buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean(), buffer.readBoolean()))
                    .consumerMainThread((packet, context) -> {
                        var player = context.get().getSender();
                        if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId()) {
                            ship.sideInput(player, packet.generation(), packet.high(), packet.shoot());
                        }
                        context.get().setPacketHandled(true);
                    }).add();
            NETWORK.messageBuilder(HelmOptic.class, 3, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((packet, buffer) -> buffer.writeVarInt(packet.shipId()).writeVarLong(packet.generation())
                            .writeBoolean(packet.zoom()))
                    .decoder(buffer -> new HelmOptic(buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean()))
                    .consumerMainThread((packet, context) -> {
                        var player = context.get().getSender();
                        if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId()) {
                            ship.helmOptic(player, packet.generation(), packet.zoom());
                        }
                        context.get().setPacketHandled(true);
                    }).add();
        });
    }

    public record ToggleSecondaries(int shipId) {}
    public record ToggleCrewMode(int shipId) {}
    public record SideInput(int shipId, long generation, boolean high, boolean shoot) {}
    public record HelmOptic(int shipId, long generation, boolean zoom) {}
}

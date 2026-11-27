package com.liucodex.libertad;

import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.liucodex.doggiesnewvehicle.neoforge.DnvNeoChannel;
import com.liucodex.libertad.compat.sbw.NativeShells;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.ship.ShipPresence;
import com.liucodex.libertad.ship.ShipTicketController;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Libertad.ID)
public final class Libertad {
    public static final String ID = "libertad";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAIN_CANNON_FIRE = SOUNDS.register(
            "main_cannon_fire", Libertad::mainCannonSound);
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_DEFENSE_FIRE = SOUNDS.register(
            "air_defense_fire", Libertad::airDefenseSound);
    public static final DeferredHolder<EntityType<?>, EntityType<LibertadEntity>> SHIP = ENTITIES.register("libertad",
            () -> EntityType.Builder.<LibertadEntity>of(LibertadEntity::new, MobCategory.MISC)
                    .sized(18,14).clientTrackingRange(28).updateInterval(2).build("libertad:libertad"));
    public static final DeferredHolder<Item, Item> DEPLOYER = ITEMS.register("libertad_deployer",
            () -> new ShipDeployer(new Item.Properties().stacksTo(1)));
    public static final DnvNeoChannel NETWORK = DnvNeoChannel.create(
            ResourceLocation.fromNamespaceAndPath(ID, "controls"), () -> "3", "3"::equals, "3"::equals);

    private static void addContainers(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().toString().equals("doggiesnewvehicle:doggies_vehicle")) {
            event.accept(ContainerBlockItem.createInstance(SHIP.get()));
        }
    }

    public Libertad(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        bus.addListener(Libertad::addContainers);
        ShipTicketController.register(bus);
        NeoForge.EVENT_BUS.register(ShipPresence.class);
        NeoForge.EVENT_BUS.register(NativeShells.class);
        NETWORK.registerMessage(0, ToggleSecondaries.class,
                (packet, buffer) -> buffer.writeVarInt(packet.shipId()),
                buffer -> new ToggleSecondaries(buffer.readVarInt()),
                (packet, context) -> context.get().enqueueWork(() -> {
                    var player = context.get().getSender();
                    if (player != null && player.getVehicle() instanceof LibertadEntity ship
                            && ship.getId() == packet.shipId()) ship.toggleSecondaries(player);
                }), Optional.of(DnvNeoChannel.Direction.PLAY_TO_SERVER));
        NETWORK.registerMessage(1, ToggleCrewMode.class,
                (packet, buffer) -> buffer.writeVarInt(packet.shipId()),
                buffer -> new ToggleCrewMode(buffer.readVarInt()),
                (packet, context) -> context.get().enqueueWork(() -> {
                    var player = context.get().getSender();
                    if (player != null && player.getVehicle() instanceof LibertadEntity ship
                            && ship.getId() == packet.shipId()) ship.toggleCrewMode(player);
                }), Optional.of(DnvNeoChannel.Direction.PLAY_TO_SERVER));
        NETWORK.registerMessage(2, SideInput.class,
                (packet, buffer) -> buffer.writeVarInt(packet.shipId()).writeVarLong(packet.generation())
                        .writeBoolean(packet.high()).writeBoolean(packet.shoot()),
                buffer -> new SideInput(buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean(), buffer.readBoolean()),
                (packet, context) -> context.get().enqueueWork(() -> {
                    var player = context.get().getSender();
                    if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId())
                        ship.sideInput(player, packet.generation(), packet.high(), packet.shoot());
                }), Optional.of(DnvNeoChannel.Direction.PLAY_TO_SERVER));
        NETWORK.registerMessage(3, HelmOptic.class,
                (packet, buffer) -> buffer.writeVarInt(packet.shipId()).writeVarLong(packet.generation()).writeBoolean(packet.zoom()),
                buffer -> new HelmOptic(buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean()),
                (packet, context) -> context.get().enqueueWork(() -> {
                    var player = context.get().getSender();
                    if (player != null && player.getVehicle() instanceof LibertadEntity ship && ship.getId() == packet.shipId())
                        ship.helmOptic(player, packet.generation(), packet.zoom());
                }), Optional.of(DnvNeoChannel.Direction.PLAY_TO_SERVER));
    }

    static SoundEvent airDefenseSound() {
        return SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, "air_defense_fire"),
                com.liucodex.libertad.naval.AirDefenseRules.FIRE_SOUND_RANGE);
    }

    static SoundEvent mainCannonSound() {
        return SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, "main_cannon_fire"), 256);
    }

    public record ToggleSecondaries(int shipId) {}
    public record ToggleCrewMode(int shipId) {}
    public record SideInput(int shipId, long generation, boolean high, boolean shoot) {}
    public record HelmOptic(int shipId, long generation, boolean zoom) {}
}

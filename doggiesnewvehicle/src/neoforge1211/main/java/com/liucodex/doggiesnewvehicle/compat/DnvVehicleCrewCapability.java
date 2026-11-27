package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.Optional;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Non-serialized, entity-owned runtime. Inventory core remains the persistence owner. */
public final class DnvVehicleCrewCapability {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "doggiesnewvehicle");
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<DnvVehicleCrewAttachment>> CREW =
            TYPES.register("vehicle_crew_runtime", () -> AttachmentType.builder(holder -> {
                if (!(holder instanceof VehicleEntity vehicle)) {
                    throw new IllegalArgumentException("Crew runtime requires a vehicle");
                }
                return new DnvVehicleCrewAttachment(vehicle);
            }).build());
    private DnvVehicleCrewCapability() {}
    public static void register(IEventBus modBus) { TYPES.register(modBus); }
    public static Optional<DnvVehicleCrewAttachment> find(VehicleEntity vehicle) {
        return Optional.of(vehicle.getData(CREW));
    }
}

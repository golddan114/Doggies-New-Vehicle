package com.liucodex.doggiesnewvehicle.item;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwVehicleCrewAdapter;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwVehicleCrewCatalog;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewServices;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Portable vehicle crew policy item. Shift-interacting with an allowed vehicle binds its layout. */
public final class DnvVehicleCrewCoreItem extends Item {
  public DnvVehicleCrewCoreItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(
      Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (player instanceof ServerPlayer serverPlayer) {
      DnvVehicleCrewServices.openCoreConfiguration(serverPlayer, hand);
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
  }

  public BindResult bind(
      ItemStack stack,
      VehicleEntity vehicle,
      ServerPlayer player,
      DnvSbwVehicleCrewCatalog catalog) {
    Objects.requireNonNull(stack, "stack");
    Objects.requireNonNull(vehicle, "vehicle");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(catalog, "catalog");
    if (vehicle.level().isClientSide()) {
      return BindResult.rejected(BindIssue.CLIENT_SIDE);
    }
    boolean admin = player.hasPermissions(2);
    if (vehicle.getLocked()
        && !player.getStringUUID().equals(vehicle.getLastDriverUUID())
        && !admin) {
      return BindResult.rejected(BindIssue.LOCKED_BY_ANOTHER_PLAYER);
    }
    var typeId = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
    if (typeId == null) {
      return BindResult.rejected(BindIssue.UNREGISTERED_VEHICLE_TYPE);
    }
    Optional<DnvSbwVehicleCrewAdapter.AllowlistEntry> allowlist =
        catalog.find(typeId.toString());
    if (allowlist.isEmpty()) {
      return BindResult.rejected(BindIssue.VEHICLE_NOT_ALLOWED);
    }
    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspect(vehicle, allowlist.orElseThrow());
    if (!inspection.accepted()) {
      return BindResult.rejected(BindIssue.LAYOUT_MISMATCH);
    }

    DnvVehicleCrewCoreCodec.DecodeResult existing =
        DnvVehicleCrewCoreCodec.decode(stack.getTag());
    UUID coreId = UUID.randomUUID();
    if (existing.accepted()) {
      DnvVehicleCrewProfile.Draft old = existing.draft().orElseThrow();
      if (!admin && !player.getUUID().equals(old.configuredBy())) {
        return BindResult.rejected(BindIssue.CORE_OWNED_BY_ANOTHER_PLAYER);
      }
      coreId = old.coreId();
    }

    DnvVehicleCrewProfile.AdapterTemplate template = inspection.template().orElseThrow();
    DnvVehicleCrewProfile.ValidationResult validation = DnvVehicleCrewProfile.validate(
        DnvVehicleCrewProfile.createDefaultDraft(coreId, player.getUUID(), template),
        template,
        new DnvVehicleCrewProfile.ValidationContext(player.getUUID(), admin));
    if (!validation.accepted()) {
      return BindResult.rejected(BindIssue.PROFILE_REJECTED);
    }
    DnvVehicleCrewProfile.Snapshot snapshot = validation.snapshot().orElseThrow();
    stack.setTag(DnvVehicleCrewCoreCodec.encode(snapshot));
    return new BindResult(true, BindIssue.NONE, Optional.of(snapshot));
  }

  @Override
  public boolean isFoil(ItemStack stack) {
    return DnvVehicleCrewCoreCodec.decode(stack.getTag()).accepted();
  }

  @Override
  public void appendHoverText(
      ItemStack stack,
      @Nullable Level level,
      List<Component> tooltip,
      TooltipFlag flag) {
    DnvVehicleCrewCoreCodec.DecodeResult decoded =
        DnvVehicleCrewCoreCodec.decode(stack.getTag());
    if (!decoded.accepted()) {
      tooltip.add(Component.translatable(
          "tooltip.doggiesnewvehicle.vehicle_crew_core.unconfigured")
          .withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable(
          "tooltip.doggiesnewvehicle.vehicle_crew_core.bind")
          .withStyle(ChatFormatting.DARK_GRAY));
      return;
    }
    DnvVehicleCrewProfile.Draft draft = decoded.draft().orElseThrow();
    long aiStations = draft.stations().stream()
        .filter(station -> station.mode() == DnvControlAuthority.WeaponMode.AUTO_ENGAGE)
        .count();
    tooltip.add(Component.translatable(
        "tooltip.doggiesnewvehicle.vehicle_crew_core.vehicle",
        draft.vehicleTypeId()).withStyle(ChatFormatting.AQUA));
    tooltip.add(Component.translatable(
        "tooltip.doggiesnewvehicle.vehicle_crew_core.stations",
        aiStations,
        draft.stations().size()).withStyle(ChatFormatting.GRAY));
    tooltip.add(Component.translatable(
        "tooltip.doggiesnewvehicle.vehicle_crew_core.reconfigure")
        .withStyle(ChatFormatting.DARK_GRAY));
  }

  public record BindResult(
      boolean accepted,
      BindIssue issue,
      Optional<DnvVehicleCrewProfile.Snapshot> snapshot) {
    public BindResult {
      Objects.requireNonNull(issue, "issue");
      snapshot = Objects.requireNonNull(snapshot, "snapshot");
      if (accepted != (issue == BindIssue.NONE && snapshot.isPresent())) {
        throw new IllegalArgumentException("inconsistent core binding result");
      }
    }

    public String vehicleTypeId() {
      return snapshot.orElseThrow().vehicleTypeId();
    }

    public UUID coreId() {
      return snapshot.orElseThrow().coreId();
    }

    private static BindResult rejected(BindIssue issue) {
      return new BindResult(false, issue, Optional.empty());
    }
  }

  public enum BindIssue {
    NONE,
    CLIENT_SIDE,
    LOCKED_BY_ANOTHER_PLAYER,
    UNREGISTERED_VEHICLE_TYPE,
    VEHICLE_NOT_ALLOWED,
    LAYOUT_MISMATCH,
    CORE_OWNED_BY_ANOTHER_PLAYER,
    PROFILE_REJECTED
  }
}

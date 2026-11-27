package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Client-only bounded list/detail controller for loaded crew-core vehicles. */
final class DnvManagedVehiclePanel {
  private DnvManagedVehicleDirectory.View directory =
      new DnvManagedVehicleDirectory.View(
          new UUID(0L, 0L), false, "NONE", List.of(), Optional.empty());
  private Map<UUID, DnvManagedVehicleDirectory.Vehicle> vehiclesById =
      Map.of();
  private Optional<UUID> selectedVehicleId = Optional.empty();
  private Button[] rows = new Button[0];
  private DnvVehicleOperationsLayout.Geometry layout;
  private int scroll;
  private final DnvManagedVehicleRequestGate requestGate = new DnvManagedVehicleRequestGate();
  private int detailScroll;
  private int maximumDetailScroll;
  private List<FormattedCharSequence> wrappedDetails = List.of();
  private Font detailFont;
  private int detailWidth;
  private boolean visible;

  void initialize(
      DnvVehicleOperationsLayout.Geometry currentLayout,
      Consumer<Button> registrar) {
    layout = Objects.requireNonNull(currentLayout, "currentLayout");
    wrappedDetails = List.of();
    Objects.requireNonNull(registrar, "registrar");
    rows = new Button[layout.visibleVehicleRows()];
    for (int slot = 0; slot < rows.length; slot++) {
      int capturedSlot = slot;
      DnvVehicleOperationsLayout.Rect bounds = layout.vehicleRow(slot);
      rows[slot] = DnvButton.builder(
          Component.empty(), button -> select(capturedSlot))
          .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
          .build();
      registrar.accept(rows[slot]);
    }
    refreshWidgets(false);
  }

  DnvManagedVehicleDirectory.Query requestStarted() {
    DnvManagedVehicleDirectory.Query query = requestGate.begin(
        selectedVehicleId.map(vehiclesById::get).map(DnvManagedVehicleDirectory.Vehicle::selection));
    refreshWidgets(visible);
    return query;
  }

  boolean tick() {
    requestGate.tick();
    if (requestGate.expired()) {
      refreshWidgets(visible);
    }
    return visible && requestGate.refreshDue();
  }

  Optional<Component> accept(
      DnvManagedVehicleDirectory.View updated,
      Optional<UUID> preferredVehicleId) {
    Objects.requireNonNull(updated, "updated");
    if (!requestGate.accept(updated)) {
      return Optional.empty();
    }
    directory = updated;
    wrappedDetails = List.of();
    Objects.requireNonNull(preferredVehicleId, "preferredVehicleId");
    Map<UUID, DnvManagedVehicleDirectory.Vehicle> indexed =
        new LinkedHashMap<>();
    updated.vehicles().forEach(vehicle ->
        indexed.putIfAbsent(vehicle.vehicleId(), vehicle));
    vehiclesById = Map.copyOf(indexed);
    if (selectedVehicleId.isPresent()
        && !vehiclesById.containsKey(selectedVehicleId.orElseThrow())) {
      selectedVehicleId = Optional.empty();
    }
    if (selectedVehicleId.isEmpty()) {
      selectedVehicleId = preferredVehicleId
          .filter(vehiclesById::containsKey)
          .or(() -> updated.vehicles().stream().findFirst()
              .map(DnvManagedVehicleDirectory.Vehicle::vehicleId));
    }
    clampScroll();
    refreshWidgets(visible);
    return Optional.of(switch (updated.issue()) {
      case "TELEPORTED" -> message("teleported");
      case "TELEPORT_PERMISSION_DENIED" -> message("teleport_denied");
      case "VEHICLE_NOT_LOADED" -> message("not_loaded");
      case "NO_SAFE_TELEPORT_POSITION" -> message("no_safe_position");
      default -> updated.detail()
          .filter(detail -> detail.status() == DnvManagedVehicleDirectory.DetailStatus.CORE_CHANGED
              || detail.status() == DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE)
          .map(detail -> message("detail." + detail.status().name().toLowerCase(Locale.ROOT)))
          .orElseGet(() -> Component.translatable(
              "screen.doggiesnewvehicle.vehicle_operations.vehicle.loaded", updated.vehicles().size()));
    });
  }

  void refreshWidgets(boolean show) {
    if (visible && !show) {
      requestGate.invalidate();
    }
    visible = show;
    if (rows.length == 0) {
      return;
    }
    clampScroll();
    for (int slot = 0; slot < rows.length; slot++) {
      int index = scroll + slot;
      Button button = rows[slot];
      button.visible = show && !requestGate.expired() && index < directory.vehicles().size();
      button.active = button.visible;
      if (button.visible) {
        DnvManagedVehicleDirectory.Vehicle vehicle = directory.vehicles().get(index);
        button.setMessage(Component.translatable(
            "screen.doggiesnewvehicle.vehicle_operations.vehicle.row",
            vehicleName(vehicle.vehicleTypeId()),
            vehicle.x(), vehicle.y(), vehicle.z()));
      }
    }
  }

  void render(GuiGraphics graphics, Font font) {
    render(graphics, font, true);
  }

  void render(GuiGraphics graphics, Font font, boolean showDetail) {
    if (!visible) {
      return;
    }
    if (requestGate.expired()) {
      DnvVehicleOperationsLayout.Rect bounds = layout.vehicleListBounds();
      draw(graphics, font, message("detail.waiting"),
          bounds.x() + 4, bounds.y() + 4, bounds.width() - 8, DnvUiColors.WARNING);
      return;
    }
    if (directory.vehicles().isEmpty()) {
      DnvVehicleOperationsLayout.Rect list = layout.vehicleListBounds();
      draw(graphics, font, message("empty"),
          list.x() + 4, list.y() + 6, list.width() - 8, DnvUiColors.WARNING);
      return;
    }
    if (!showDetail) {
      return;
    }
    DnvVehicleOperationsLayout.Rect detail = layout.vehicleDetailBounds();
    DnvManagedVehicleDirectory.Vehicle selected = selected().orElse(null);
    if (selected == null) {
      draw(graphics, font, message("select"),
          detail.x() + 4, detail.y() + 6, detail.width() - 8, DnvUiColors.MUTED);
      return;
    }
    if (wrappedDetails.isEmpty() || detailFont != font || detailWidth != detail.width()) {
      List<FormattedCharSequence> wrapped = new ArrayList<>();
      for (Component value : detailLines(selected)) {
        wrapped.addAll(font.split(value, Math.max(1, detail.width() - 8)));
      }
      wrappedDetails = List.copyOf(wrapped);
      detailFont = font;
      detailWidth = detail.width();
    }
    List<FormattedCharSequence> lines = wrappedDetails;
    int visibleLines = layout.visibleVehicleDetailLines(font.lineHeight);
    maximumDetailScroll = Math.max(0, lines.size() - visibleLines);
    detailScroll = Math.min(detailScroll, maximumDetailScroll);
    graphics.enableScissor(detail.x(), detail.y(), detail.right(), detail.bottom());
    try {
      for (int index = detailScroll; index < lines.size(); index++) {
        int y = detail.y() + 4 + (index - detailScroll) * font.lineHeight;
        if (y + font.lineHeight > detail.bottom() - 2) {
          break;
        }
        graphics.drawString(font, lines.get(index), detail.x() + 4, y, DnvUiColors.TEXT);
      }
    } finally {
      graphics.disableScissor();
    }
  }

  private List<Component> detailLines(DnvManagedVehicleDirectory.Vehicle selected) {
    List<Component> lines = new ArrayList<>();
    lines.add(vehicleName(selected.vehicleTypeId()));
    lines.add(Component.translatable("screen.doggiesnewvehicle.vehicle_operations.vehicle.position",
        selected.dimension(), selected.x(), selected.y(), selected.z()));
    lines.add(message("core_scope"));
    if (requestGate.expired()) {
      lines.add(message("detail.waiting"));
      return lines;
    }
    DnvManagedVehicleDirectory.Detail detail = selectedDetail().orElse(null);
    if (detail == null) {
      lines.add(message("detail.waiting"));
      return lines;
    }
    lines.add(message("detail." + detail.status().name().toLowerCase(Locale.ROOT)));
    if (detail.status() == DnvManagedVehicleDirectory.DetailStatus.CORE_CHANGED
        || detail.status() == DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE) {
      return lines;
    }
    lines.add(message("detail.core_active"));
    lines.add(message(detail.routeSupported() ? "detail.route_supported" : "detail.route_unsupported"));
    for (DnvManagedVehicleDirectory.Weapon weapon : detail.weapons()) {
      lines.add(Component.literal(weapon.stationId() + " / " + weapon.weaponName()));
      lines.add(Component.translatable("screen.doggiesnewvehicle.vehicle_operations.vehicle.detail.weapon",
          detailCode("control", weapon.controlOwner()),
          weapon.ammoCount(), weapon.cooldownTicks(),
          message(weapon.canShoot() ? "detail.can_shoot" : "detail.cannot_shoot")));
      lines.add(detailCode("gate", weapon.gateReason()));
    }
    return lines;
  }

  private static Component detailCode(String category, String code) {
    String key = "screen.doggiesnewvehicle.vehicle_operations.vehicle.detail."
        + category + "." + code.toLowerCase(Locale.ROOT);
    return Component.translatableWithFallback(key, code);
  }

  boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    if (!visible || layout == null) {
      return false;
    }
    DnvVehicleOperationsLayout.Rect pointer = new DnvVehicleOperationsLayout.Rect((int) mouseX, (int) mouseY, 1, 1);
    if (layout.vehicleDetailBounds().contains(pointer)) {
      detailScroll = DnvVehicleOperationsLayout.clampDetailScroll(
          detailScroll - (int) Math.signum(delta) * 3, maximumDetailScroll);
      return true;
    }
    if (!layout.vehicleListBounds().contains(pointer)) {
      return false;
    }
    int next = Math.max(0, Math.min(maximumScroll(),
        scroll - (int) Math.signum(delta)));
    if (next != scroll) {
      scroll = next;
      refreshWidgets(true);
    }
    return true;
  }

  private void select(int slot) {
    int index = scroll + slot;
    if (index >= 0 && index < directory.vehicles().size()) {
      selectVehicle(directory.vehicles().get(index).vehicleId());
    }
  }

  void selectVehicle(UUID vehicleId) {
    if (vehiclesById.containsKey(vehicleId)) {
      selectedVehicleId = Optional.of(vehicleId);
      requestGate.invalidate();
      detailScroll = 0;
      wrappedDetails = List.of();
      refreshWidgets(true);
    }
  }

  Optional<DnvManagedVehicleDirectory.Vehicle> selected() {
    return requestGate.expired() ? Optional.empty() : selectedVehicleId.map(vehiclesById::get);
  }

  Optional<DnvManagedVehicleDirectory.Detail> selectedDetail() {
    return selected().flatMap(vehicle -> directory.detail()
        .filter(detail -> detail.identity().equals(vehicle.selection())));
  }

  boolean administrator() {
    return !requestGate.expired() && directory.administrator();
  }

  boolean waiting() {
    return requestGate.waiting();
  }

  private void clampScroll() {
    scroll = Math.max(0, Math.min(scroll, maximumScroll()));
  }

  private int maximumScroll() {
    return Math.max(0, directory.vehicles().size() - rows.length);
  }

  private static Component vehicleName(String vehicleTypeId) {
    ResourceLocation id = ResourceLocation.tryParse(vehicleTypeId);
    return id == null ? message("unknown")
        : Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
  }

  private static Component message(String suffix) {
    return Component.translatable(
        "screen.doggiesnewvehicle.vehicle_operations.vehicle." + suffix);
  }

  private static void draw(
      GuiGraphics graphics, Font font, Component value,
      int x, int y, int width, int color) {
    graphics.drawString(
        font, font.plainSubstrByWidth(value.getString(), Math.max(1, width)),
        x, y, color);
  }
}

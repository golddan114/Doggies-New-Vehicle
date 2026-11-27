package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewConfigNetwork;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewConfigSessions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Responsive, list-first editor for one server-authorized portable crew core session. */
@OnlyIn(Dist.CLIENT)
public final class DnvVehicleCrewCoreScreen extends Screen {

  private final DnvVehicleCrewConfigSessions.View view;
  private final Component vehicleName;
  private final Component coreHelp;
  private final List<Component> stationLabels;
  private final List<Component> stationRoles;
  private final Map<String, String> stationModes =
      new LinkedHashMap<>();
  private final Map<String, String> savedStationModes =
      new LinkedHashMap<>();
  private Page page = Page.STATIONS;
  private Button corePageButton;
  private Button stationsPageButton;
  private Button saveButton;
  private Button cancelButton;
  private Button[] stationButtons = new Button[0];
  private int panelX;
  private int panelY;
  private int panelWidth;
  private int panelHeight;
  private int detailX;
  private int detailY;
  private int detailWidth;
  private int detailHeight;
  private int stationScroll;
  private int coreHelpScroll;
  private int visibleRows;
  private boolean waiting;
  private boolean resolved;
  private boolean timedOut;
  private int pendingTicks;
  private Component status = Component.empty();
  private DnvVehicleCrewCoreLayout.Geometry layout;

  public DnvVehicleCrewCoreScreen(DnvVehicleCrewConfigSessions.View view) {
    super(Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.title"));
    this.view = Objects.requireNonNull(view, "view");
    this.vehicleName = vehicleName(view.vehicleTypeId());
    this.coreHelp = Component.translatable(
        "screen.doggiesnewvehicle.vehicle_crew_core.core.help");
    this.stationLabels = view.stations().stream().<Component>map(station ->
        Component.translatable(
            "screen.doggiesnewvehicle.vehicle_crew_core.station",
            station.seatIndex() + 1)).toList();
    this.stationRoles = view.stations().stream().<Component>map(station ->
        Component.translatable(station.aiCapable()
            ? "screen.doggiesnewvehicle.vehicle_crew_core.station.ai"
            : "screen.doggiesnewvehicle.vehicle_crew_core.station.manual"))
        .toList();
    view.stations().forEach(station -> {
      stationModes.put(station.stationId(), station.modeName());
      savedStationModes.put(station.stationId(), station.modeName());
    });
  }

  @Override
  protected void init() {
    layout = DnvVehicleCrewCoreLayout.calculate(width, height);
    panelX = layout.panel().x();
    panelY = layout.panel().y();
    panelWidth = layout.panel().width();
    panelHeight = layout.panel().height();
    detailX = layout.detail().x();
    detailY = layout.detail().y();
    detailWidth = layout.detail().width();
    detailHeight = layout.detail().height();
    corePageButton = addRenderableWidget(DnvButton.builder(
        pageLabel(Page.CORE), button -> selectPage(Page.CORE))
        .bounds(
            layout.coreTab().x(), layout.coreTab().y(),
            layout.coreTab().width(), layout.coreTab().height())
        .build());
    stationsPageButton = addRenderableWidget(DnvButton.builder(
        pageLabel(Page.STATIONS), button -> selectPage(Page.STATIONS))
        .bounds(
            layout.stationsTab().x(), layout.stationsTab().y(),
            layout.stationsTab().width(), layout.stationsTab().height())
        .build());

    visibleRows = layout.visibleStationRows();
    stationButtons = new Button[visibleRows];
    for (int slot = 0; slot < visibleRows; slot++) {
      int capturedSlot = slot;
      DnvVehicleCrewCoreLayout.Rect bounds = layout.stationButton(slot);
      stationButtons[slot] = addRenderableWidget(DnvButton.builder(
          Component.empty(), button -> cycleStation(capturedSlot))
          .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
          .build());
    }

    saveButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.save"),
        button -> submit())
        .bounds(
            layout.saveButton().x(), layout.saveButton().y(),
            layout.saveButton().width(), layout.saveButton().height())
        .build());
    cancelButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.cancel"),
        button -> onClose())
        .bounds(
            layout.cancelButton().x(), layout.cancelButton().y(),
            layout.cancelButton().width(), layout.cancelButton().height())
        .build());
    refreshWidgets();
  }
  private void selectPage(Page next) {
    page = next;
    refreshWidgets();
  }

  private void cycleStation(int slot) {
    int index = stationScroll + slot;
    if (index < 0 || index >= view.stations().size()) {
      return;
    }
    DnvVehicleCrewConfigSessions.StationView station = view.stations().get(index);
    String current = stationModes.get(station.stationId());
    int currentIndex = station.allowedModeNames().indexOf(current);
    String next = station.allowedModeNames().get(
        (Math.max(0, currentIndex) + 1) % station.allowedModeNames().size());
    stationModes.put(station.stationId(), next);
    refreshWidgets();
  }

  private void submit() {
    if (waiting) {
      return;
    }
    waiting = true;
    timedOut = false;
    pendingTicks = 0;
    status = Component.translatable(
        "screen.doggiesnewvehicle.vehicle_crew_core.status.saving");
    refreshWidgets();
    List<DnvVehicleCrewProfile.StationDraft> stations = view.stations().stream()
        .map(station -> DnvVehicleCrewProfile.StationDraft.fromModeName(
            station.stationId(), stationModes.get(station.stationId())))
        .toList();
    DnvVehicleCrewConfigNetwork.commit(
        view.token(), view.hand(), view.flightMode(), stations);
  }

  public void handleResponse(
      UUID token,
      boolean accepted,
      DnvVehicleCrewConfigSessions.Issue issue,
      Optional<String> savedFlightModeName,
      List<String> savedStationIds,
      List<String> savedStationModeNames) {
    if (!view.token().equals(token)) {
      return;
    }
    waiting = false;
    pendingTicks = 0;
    if (accepted) {
      DnvVehicleCrewProfile.FlightMode.valueOf(savedFlightModeName.orElseThrow());
      stationModes.clear();
      savedStationModes.clear();
      for (int index = 0; index < savedStationIds.size(); index++) {
        String mode =
            savedStationModeNames.get(index);
        stationModes.put(savedStationIds.get(index), mode);
        savedStationModes.put(savedStationIds.get(index), mode);
      }
      resolved = true;
      status = Component.translatable(
          "screen.doggiesnewvehicle.vehicle_crew_core.status.saved_verified");
      refreshWidgets();
      return;
    }
    status = Component.translatable(
        "screen.doggiesnewvehicle.vehicle_crew_core.status.rejected",
        issue.name());
    refreshWidgets();
  }

  private void refreshWidgets() {
    if (corePageButton == null) {
      return;
    }
    corePageButton.active = page != Page.CORE && !waiting;
    stationsPageButton.active = page != Page.STATIONS && !waiting;
    saveButton.active = !waiting && !resolved && !timedOut && dirty();
    cancelButton.active = !waiting;
    refreshStationButtons();
  }

  private void refreshStationButtons() {
    if (stationButtons.length == 0) {
      return;
    }
    int maximumScroll = Math.max(0, view.stations().size() - visibleRows);
    stationScroll = Math.max(0, Math.min(stationScroll, maximumScroll));
    for (int slot = 0; slot < stationButtons.length; slot++) {
      int index = stationScroll + slot;
      Button button = stationButtons[slot];
      button.visible = page == Page.STATIONS && index < view.stations().size();
      button.active = button.visible && !waiting && !resolved && !timedOut;
      if (button.visible) {
        DnvVehicleCrewConfigSessions.StationView station = view.stations().get(index);
        button.setMessage(modeLabel(stationModes.get(station.stationId())));
      }
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (!waiting) {
      pendingTicks = 0;
      return;
    }
    pendingTicks++;
    if (pendingTicks < 200) {
      return;
    }
    waiting = false;
    timedOut = true;
    pendingTicks = 0;
    status = Component.translatable(
        "screen.doggiesnewvehicle.vehicle_crew_core.status.timeout");
    refreshWidgets();
  }

  private boolean dirty() {
    return !stationModes.equals(savedStationModes);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    if (page == Page.CORE) {
      DnvVehicleCrewCoreLayout.Rect help = layout.coreHelpBounds();
      if (mouseX >= help.x() && mouseX < help.right()
          && mouseY >= help.y() && mouseY < help.bottom()) {
        int contentHeight = font.split(coreHelp, help.width()).size() * font.lineHeight;
        int maximumScroll = Math.max(0, contentHeight - help.height());
        coreHelpScroll = Math.max(0, Math.min(maximumScroll,
            coreHelpScroll - (int) Math.signum(delta) * font.lineHeight));
        return true;
      }
    }
    if (page == Page.STATIONS
        && mouseX >= detailX
        && mouseX < detailX + detailWidth
        && mouseY >= detailY
        && mouseY < detailY + detailHeight) {
      int maximumScroll = Math.max(0, view.stations().size() - visibleRows);
      int next = Math.max(0, Math.min(maximumScroll, stationScroll - (int) Math.signum(delta)));
      if (next != stationScroll) {
        stationScroll = next;
        refreshStationButtons();
      }
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, delta);
  }

  @Override
  public void render(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DnvUiColors.PANEL);
    graphics.fill(
        detailX,
        detailY,
        detailX + detailWidth,
        detailY + detailHeight,
        DnvUiColors.SECTION);
    graphics.drawCenteredString(
        font, title, panelX + panelWidth / 2, panelY + 12, DnvUiColors.TEXT);
    if (page == Page.CORE) {
      renderCorePage(graphics);
    } else {
      renderStationsPage(graphics);
    }
    if (!status.getString().isEmpty()) {
      drawBounded(graphics, status, layout.statusText(), DnvUiColors.WARNING);
    }
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  private void renderCorePage(GuiGraphics graphics) {
    drawBounded(
        graphics,
        Component.translatable(
            "screen.doggiesnewvehicle.vehicle_crew_core.vehicle", vehicleName),
        layout.headingText(false),
        DnvUiColors.TEXT);
    drawBounded(
        graphics,
        Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.core.heading"),
        new DnvVehicleCrewCoreLayout.Rect(
            detailX + 8, detailY + 30, Math.max(1, detailWidth - 16), 10),
        DnvUiColors.ACCENT);
    DnvVehicleCrewCoreLayout.Rect help = layout.coreHelpBounds();
    List<FormattedCharSequence> lines = font.split(coreHelp, help.width());
    coreHelpScroll = Math.min(coreHelpScroll,
        Math.max(0, lines.size() * font.lineHeight - help.height()));
    graphics.enableScissor(help.x(), help.y(), help.right(), help.bottom());
    try {
      for (int index = 0; index < lines.size(); index++) {
        int y = help.y() + index * font.lineHeight - coreHelpScroll;
        if (y + font.lineHeight > help.y() && y < help.bottom()) {
          graphics.drawString(font, lines.get(index), help.x(), y, DnvUiColors.MUTED);
        }
      }
    } finally {
      graphics.disableScissor();
    }
  }

  private void renderStationsPage(GuiGraphics graphics) {
    boolean withScrollSummary = view.stations().size() > visibleRows;
    drawBounded(
        graphics,
        Component.translatable(
            "screen.doggiesnewvehicle.vehicle_crew_core.stations",
            view.stations().size()),
        layout.headingText(withScrollSummary),
        DnvUiColors.ACCENT);
    for (int slot = 0; slot < visibleRows; slot++) {
      int index = stationScroll + slot;
      if (index >= view.stations().size()) {
        break;
      }
      DnvVehicleCrewConfigSessions.StationView station = view.stations().get(index);
      DnvVehicleCrewCoreLayout.Rect labelBounds = layout.stationLabel(slot);
      Component label = stationLabels.get(index);
      drawBounded(graphics, label, labelBounds, DnvUiColors.TEXT);
      DnvVehicleCrewCoreLayout.Rect roleBounds = new DnvVehicleCrewCoreLayout.Rect(
          labelBounds.x(), labelBounds.y() + 10, labelBounds.width(), 9);
      drawBounded(
          graphics,
          stationRoles.get(index),
          roleBounds,
          station.aiCapable() ? DnvUiColors.SUCCESS : DnvUiColors.MUTED);
    }
    if (withScrollSummary) {
      drawBounded(
          graphics,
          Component.translatable(
              "screen.doggiesnewvehicle.vehicle_crew_core.scroll",
              stationScroll + 1,
              Math.max(1, view.stations().size() - visibleRows + 1)),
          layout.scrollText(),
          DnvUiColors.MUTED);
    }
  }

  private void drawBounded(
      GuiGraphics graphics,
      Component text,
      DnvVehicleCrewCoreLayout.Rect bounds,
      int color) {
    graphics.drawString(
        font,
        font.plainSubstrByWidth(text.getString(), Math.max(1, bounds.width())),
        bounds.x(),
        bounds.y(),
        color);
  }
  private static Component modeLabel(String mode) {
    return Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.weapon." + mode.toLowerCase(Locale.ROOT));
  }

  private static Component pageLabel(Page page) {
    return Component.translatable(
        "screen.doggiesnewvehicle.vehicle_crew_core.page."
            + page.name().toLowerCase(Locale.ROOT));
  }

  private static Component vehicleName(String vehicleTypeId) {
    ResourceLocation id = ResourceLocation.tryParse(vehicleTypeId);
    if (id == null) {
      return Component.translatable("screen.doggiesnewvehicle.vehicle_crew_core.unknown_vehicle");
    }
    return Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
  }

  @Override
  public void onClose() {
    if (!resolved) {
      resolved = true;
      DnvVehicleCrewConfigNetwork.cancel(view.token());
    }
    super.onClose();
  }

  @Override
  public void removed() {
    if (!resolved) {
      resolved = true;
      DnvVehicleCrewConfigNetwork.cancel(view.token());
    }
    super.removed();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private enum Page {
    CORE,
    STATIONS
  }
}

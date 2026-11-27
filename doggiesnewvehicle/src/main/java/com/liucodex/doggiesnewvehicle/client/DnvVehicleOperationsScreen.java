package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.compat.DnvManagedVehicleNetwork;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatIntentView;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Loaded-vehicle and entity-order console; no historical task state is displayed. */
@OnlyIn(Dist.CLIENT)
public final class DnvVehicleOperationsScreen extends Screen implements DnvCombatIntentView {
  private static final int PANEL_COLOR = DnvUiColors.PANEL;
  private static final int CARD_COLOR = DnvUiColors.SECTION;
  private static final int CARD_BORDER = DnvUiColors.BORDER;
  private static final int TEXT = DnvUiColors.TEXT;
  private static final int MUTED = DnvUiColors.MUTED;
  private DnvVehicleOperationsLayout.Geometry layout;
  private final DnvManagedVehiclePanel vehiclePanel = new DnvManagedVehiclePanel();
  private final DnvCombatOrderPanel combatPanel = new DnvCombatOrderPanel();
  private Page page = Page.TARGETS;
  private Button vehiclesButton;
  private Button targetsButton;
  private Button refreshButton;
  private Button teleportButton;
  private Component localStatus = Component.empty();
  private boolean showDirectoryStatus;

  public DnvVehicleOperationsScreen() {
    super(Component.translatable("screen.doggiesnewvehicle.vehicle_operations.title"));
  }

  @Override
  protected void init() {
    double guiScale = minecraft == null ? 1.0D : minecraft.getWindow().getGuiScale();
    layout = DnvVehicleOperationsLayout.calculate(width, height, guiScale);
    DnvVehicleOperationsLayout.Rect close = layout.closeButton();
    addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_operations.close"),
        button -> onClose())
        .bounds(close.x(), close.y(), close.width(), close.height()).build());
    DnvVehicleOperationsLayout.Rect vehicles = layout.headerActionButton(0);
    vehiclesButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_operations.vehicles"),
        button -> switchPage(Page.VEHICLES))
        .bounds(vehicles.x(), vehicles.y(), vehicles.width(), vehicles.height()).build());
    DnvVehicleOperationsLayout.Rect targets = layout.headerActionButton(1);
    targetsButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_operations.targets"),
        button -> switchPage(Page.TARGETS))
        .bounds(targets.x(), targets.y(), targets.width(), targets.height()).build());
    DnvVehicleOperationsLayout.Rect refresh = layout.headerActionButton(2);
    refreshButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_operations.refresh"),
        button -> requestDirectory())
        .bounds(refresh.x(), refresh.y(), refresh.width(), refresh.height()).build());
    DnvVehicleOperationsLayout.Rect teleport = layout.headerActionButton(3);
    teleportButton = addRenderableWidget(DnvButton.builder(
        Component.translatable("screen.doggiesnewvehicle.vehicle_operations.teleport"),
        button -> teleport())
        .bounds(teleport.x(), teleport.y(), teleport.width(), teleport.height()).build());
    vehiclePanel.initialize(layout, this::addRenderableWidget);
    combatPanel.initialize(layout, font, this::addRenderableWidget);
    vehiclePanel.refreshWidgets(true);
    combatPanel.setVisible(page == Page.TARGETS, vehiclePanel.selected());
    refreshButtons();
    requestDirectory();
  }

  @Override
  public void dnv$acceptVehicleDirectory(DnvManagedVehicleDirectory.View view) {
    vehiclePanel.accept(view, Optional.empty()).ifPresent(status -> {
      localStatus = status;
      showDirectoryStatus = !"NONE".equals(view.issue());
    });
    combatPanel.setVisible(page == Page.TARGETS, vehiclePanel.selected());
    refreshButtons();
  }

  private void switchPage(Page next) {
    page = next;
    combatPanel.setVisible(page == Page.TARGETS, vehiclePanel.selected());
    refreshButtons();
  }

  @Override
  public void dnv$acceptCombat(DnvCombatUi.View view) {
    combatPanel.accept(view);
  }

  private void requestDirectory() {
    if (vehiclePanel.waiting()) return;
    localStatus = Component.translatable("screen.doggiesnewvehicle.vehicle_operations.vehicle.loading");
    showDirectoryStatus = true;
    DnvManagedVehicleNetwork.requestDirectory(vehiclePanel.requestStarted());
    refreshButtons();
  }

  private void teleport() {
    if (!canTeleport()) return;
    DnvManagedVehicleNetwork.teleport(vehiclePanel.requestStarted());
    refreshButtons();
  }

  private boolean canTeleport() {
    return vehiclePanel.administrator() && vehiclePanel.selected()
        .map(DnvManagedVehicleDirectory.Vehicle::teleportAllowed).orElse(false);
  }

  private void refreshButtons() {
    if (vehiclesButton != null) vehiclesButton.active = page != Page.VEHICLES;
    if (targetsButton != null) targetsButton.active = page != Page.TARGETS;
    if (refreshButton != null) refreshButton.active = !vehiclePanel.waiting();
    if (teleportButton != null) teleportButton.active = !vehiclePanel.waiting() && canTeleport();
  }

  @Override
  public void tick() {
    super.tick();
    if (vehiclePanel.tick()) requestDirectory();
    combatPanel.tick(vehiclePanel.selected());
    refreshButtons();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    DnvVehicleOperationsLayout.Rect panel = layout.panelBounds();
    graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL_COLOR);
    draw(graphics, title, panel.x() + 8, panel.y() + 8, panel.width() - 90, TEXT);
    DnvVehicleOperationsLayout.Rect status = layout.statusCardBounds();
    graphics.fill(status.x(), status.y(), status.right(), status.bottom(), CARD_COLOR);
    Component primary = page == Page.VEHICLES || showDirectoryStatus
        ? localStatus : combatPanel.status();
    draw(graphics, primary,
        status.x() + 6, status.y() + 4, status.width() - 12, TEXT);
    Component hint = page == Page.VEHICLES
        ? Component.translatable("screen.doggiesnewvehicle.vehicle_operations.vehicle.select")
        : combatPanel.hint();
    draw(graphics, hint, status.x() + 6, status.y() + 15,
        status.width() - 12, MUTED);
    card(graphics, layout.vehicleListBounds());
    card(graphics, layout.vehicleDetailBounds());
    vehiclePanel.render(graphics, font, page == Page.VEHICLES);
    combatPanel.render(graphics, font);
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  private void card(GuiGraphics graphics, DnvVehicleOperationsLayout.Rect bounds) {
    graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), CARD_BORDER);
    graphics.fill(bounds.x() + 1, bounds.y() + 1,
        bounds.right() - 1, bounds.bottom() - 1, CARD_COLOR);
  }

  private void draw(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
    graphics.drawString(font,
        font.plainSubstrByWidth(value.getString(), Math.max(1, width)), x, y, color);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    if (page == Page.TARGETS && combatPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
    if (vehiclePanel.mouseScrolled(mouseX, mouseY, delta)) return true;
    return super.mouseScrolled(mouseX, mouseY, delta);
  }

  private enum Page { VEHICLES, TARGETS }
}

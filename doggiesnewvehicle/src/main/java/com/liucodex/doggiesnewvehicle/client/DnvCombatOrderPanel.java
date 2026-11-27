package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUiClientGateway;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Client-only entity designation draft. The server owns the order and fire authority. */
final class DnvCombatOrderPanel {
  private static final int POLL_TICKS = 20;
  private static final int ACCENT = DnvUiColors.ACCENT;
  private static final int GOOD = DnvUiColors.SUCCESS;
  private static final int WARN = DnvUiColors.WARNING;
  private static final int BAD = DnvUiColors.ERROR;
  private static final int TEXT = DnvUiColors.TEXT;
  private static final int MUTED = DnvUiColors.MUTED;

  private DnvVehicleOperationsLayout.Geometry layout;
  private Optional<DnvManagedVehicleDirectory.Selection> selection = Optional.empty();
  private DnvCombatUi.View view;
  private Optional<DnvCombatUi.TargetIdentity> selectedTarget = Optional.empty();
  private Preview preview;
  private DnvCombatUi.Request pending;
  private DnvCombatUiCorrelation.Cursor cursor;
  private boolean timedOut;
  private boolean visible;
  private long sequence;
  private int pendingTicks;
  private int sinceViewTicks;
  private int targetScroll;
  private Button[] targetButtons = new Button[0];
  private final Button[] actions = new Button[4];
  private Component localStatus = Component.empty();

  void initialize(
      DnvVehicleOperationsLayout.Geometry geometry, Font font, Consumer<AbstractWidget> registrar) {
    layout = Objects.requireNonNull(geometry, "geometry");
    Objects.requireNonNull(font, "font");
    Objects.requireNonNull(registrar, "registrar");
    DnvVehicleOperationsLayout.Rect detail = layout.vehicleDetailBounds();
    int targetStart = layout.wide() ? detail.y() + 76 : detail.y() + 4;
    int targetRows = Math.max(0, Math.min(layout.wide() ? 8 : 2,
        (detail.bottom() - targetStart - 2) / 20));
    targetButtons = new Button[targetRows];
    for (int slot = 0; slot < targetButtons.length; slot++) {
      int captured = slot;
      targetButtons[slot] = DnvButton.builder(Component.empty(), button -> selectTarget(captured))
          .bounds(detail.x() + 4, targetStart + slot * 20, Math.max(1, detail.width() - 8), 18)
          .build();
      registrar.accept(targetButtons[slot]);
    }
    for (int slot = 0; slot < actions.length; slot++) {
      int captured = slot;
      DnvVehicleOperationsLayout.Rect bounds = layout.combatActionButton(slot);
      actions[slot] = DnvButton.builder(Component.empty(), button -> act(captured))
          .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
      registrar.accept(actions[slot]);
    }
    refreshWidgets();
  }

  void setVisible(boolean show, Optional<DnvManagedVehicleDirectory.Vehicle> selected) {
    visible = show;
    updateSelection(selected);
    refreshWidgets();
  }

  void accept(DnvCombatUi.View updated) {
    if (cursor == null
        || DnvCombatUiCorrelation.evaluate(cursor, updated)
            != DnvCombatUiCorrelation.Decision.ACCEPT) return;
    long previousRevision = view == null ? -1L : view.orderRevision();
    view = updated;
    pending = null;
    cursor = null;
    pendingTicks = 0;
    sinceViewTicks = 0;
    timedOut = false;
    if (previousRevision >= 0L && previousRevision != updated.orderRevision()) preview = null;
    selectedTarget = selectedTarget.filter(identity -> updated.targets().stream()
        .anyMatch(target -> target.identity().equals(identity)));
    localStatus = code(updated.result());
    clampTargetScroll();
    refreshWidgets();
  }

  void tick(Optional<DnvManagedVehicleDirectory.Vehicle> selected) {
    updateSelection(selected);
    if (!visible || selection.isEmpty()) return;
    sinceViewTicks++;
    if (pending != null) {
      pendingTicks++;
      if (DnvCombatUiCorrelation.timedOut(pendingTicks)) {
        timedOut = true;
        localStatus = message("result_unconfirmed");
        refreshWidgets();
      }
      return;
    }
    if (view == null || sinceViewTicks >= POLL_TICKS) sendRefresh();
  }

  void render(GuiGraphics graphics, Font font) {
    if (!visible) return;
    DnvVehicleOperationsLayout.Rect detail = layout.vehicleDetailBounds();
    if (selection.isEmpty()) {
      draw(graphics, font, message("select_platform"), detail.x() + 4, detail.y() + 4,
          detail.width() - 8, WARN);
      return;
    }
    if (layout.wide()) {
      DnvManagedVehicleDirectory.Selection selected = selection.orElseThrow();
      draw(graphics, font, message("identity", shortId(selected.vehicleId()), shortId(selected.coreId()),
          selected.coreRevision()), detail.x() + 4, detail.y() + 4, detail.width() - 8, ACCENT);
      draw(graphics, font, capabilityLine(), detail.x() + 4, detail.y() + 17,
          detail.width() - 8, capabilityReady() ? GOOD : WARN);
      draw(graphics, font, orderLine(), detail.x() + 4, detail.y() + 30,
          detail.width() - 8, orderColor());
      draw(graphics, font, previewLine(), detail.x() + 4, detail.y() + 43,
          detail.width() - 8, preview == null ? MUTED : TEXT);
    }
  }

  Component status() {
    if (!localStatus.getString().isEmpty()) return localStatus;
    return view == null ? message("waiting") : orderLine();
  }

  Component hint() {
    return preview == null ? message("preview_required") : previewLine();
  }

  boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    if (!visible || view == null
        || !layout.vehicleDetailBounds().contains(new DnvVehicleOperationsLayout.Rect(
            (int) mouseX, (int) mouseY, 1, 1))) return false;
    int maximum = Math.max(0, view.targets().size() - targetButtons.length);
    int next = Math.max(0, Math.min(maximum, targetScroll - (int) Math.signum(delta)));
    if (next != targetScroll) {
      targetScroll = next;
      refreshWidgets();
    }
    return true;
  }

  private void updateSelection(Optional<DnvManagedVehicleDirectory.Vehicle> selected) {
    Optional<DnvManagedVehicleDirectory.Selection> next = selected
        .map(DnvManagedVehicleDirectory.Vehicle::selection);
    if (selection.equals(next)) return;
    selection = next;
    view = null;
    preview = null;
    pending = null;
    cursor = null;
    timedOut = false;
    selectedTarget = Optional.empty();
    targetScroll = 0;
    sinceViewTicks = POLL_TICKS;
    localStatus = next.isPresent() ? message("platform_changed") : message("select_platform");
  }

  private void act(int slot) {
    switch (slot) {
      case 0 -> sendRefresh();
      case 1 -> preview();
      case 2 -> submitOrRetry();
      case 3 -> cancel();
      default -> throw new IllegalArgumentException("combat action slot is out of range");
    }
  }

  private void preview() {
    if (selection.isEmpty() || view == null) return;
    if (selectedTarget.isEmpty()) {
      localStatus = message("select_target");
      return;
    }
    preview = new Preview(selection.orElseThrow(), view.orderRevision(), selectedTarget.orElseThrow());
    localStatus = message("preview_ready");
    refreshWidgets();
  }

  private void submitOrRetry() {
    if (timedOut && pending != null) {
      pendingTicks = 0;
      timedOut = false;
      DnvCombatUiClientGateway.send(pending);
      localStatus = message("retrying");
      refreshWidgets();
      return;
    }
    if (preview == null || view == null
        || !preview.selection().equals(selection.orElse(null))
        || preview.expectedRevision() != view.orderRevision()) {
      localStatus = message("preview_required");
      return;
    }
    send(DnvCombatUi.Action.DESIGNATE_ENTITY, Optional.of(preview.target()),
        preview.expectedRevision());
  }

  private void cancel() {
    if (view == null || view.order().filter(DnvCombatUi.Order::cancellable).isEmpty()) return;
    send(DnvCombatUi.Action.CANCEL, Optional.empty(), view.orderRevision());
  }

  private void sendRefresh() {
    if (selection.isEmpty() || pending != null) return;
    send(DnvCombatUi.Action.REFRESH, Optional.empty(),
        view == null ? 0L : view.orderRevision());
  }

  private void send(
      DnvCombatUi.Action action, Optional<DnvCombatUi.TargetIdentity> target,
      long expectedRevision) {
    if (selection.isEmpty()) return;
    if (sequence == Long.MAX_VALUE) {
      localStatus = code("SEQUENCE_EXHAUSTED");
      return;
    }
    UUID requestId = UUID.randomUUID();
    sequence++;
    pending = new DnvCombatUi.Request(requestId, sequence, selection.orElseThrow(),
        expectedRevision, UUID.randomUUID(), action, target);
    cursor = new DnvCombatUiCorrelation.Cursor(requestId, sequence, selection.orElseThrow());
    pendingTicks = 0;
    timedOut = false;
    DnvCombatUiClientGateway.send(pending);
    localStatus = message(action == DnvCombatUi.Action.REFRESH ? "refreshing" : "submitting");
    refreshWidgets();
  }

  private void selectTarget(int slot) {
    if (view == null) return;
    int index = targetScroll + slot;
    if (index < 0 || index >= view.targets().size()) return;
    selectedTarget = Optional.of(view.targets().get(index).identity());
    preview = null;
    localStatus = code(view.targets().get(index).issue());
    refreshWidgets();
  }

  private void refreshWidgets() {
    for (int slot = 0; slot < targetButtons.length; slot++) {
      int index = targetScroll + slot;
      Button button = targetButtons[slot];
      button.visible = visible && view != null && index < view.targets().size();
      button.active = button.visible && pending == null;
      if (button.visible) {
        DnvCombatUi.Target target = view.targets().get(index);
        button.setMessage(Component.literal((selectedTarget.filter(target.identity()::equals).isPresent()
            ? "▶ " : "") + target.label() + (target.eligible() ? "" : " · " + code(target.issue()).getString())));
      }
    }
    if (actions[0] == null) return;
    String[] labels = {"refresh", "preview", timedOut ? "retry" : "submit", "cancel"};
    for (int slot = 0; slot < actions.length; slot++) {
      actions[slot].visible = visible;
      actions[slot].setMessage(message("action." + labels[slot]));
      actions[slot].active = visible && switch (slot) {
        case 0 -> selection.isPresent() && pending == null;
        case 1 -> view != null && pending == null && selectedTarget.isPresent();
        case 2 -> timedOut && pending != null || pending == null && preview != null;
        case 3 -> pending == null && view != null
            && view.order().filter(DnvCombatUi.Order::cancellable).isPresent();
        default -> false;
      };
    }
  }

  private void clampTargetScroll() {
    int maximum = view == null ? 0 : Math.max(0, view.targets().size() - targetButtons.length);
    targetScroll = Math.max(0, Math.min(targetScroll, maximum));
  }

  private DnvCombatUi.Capability capability() {
    if (view == null) return null;
    return view.capabilities().stream().filter(DnvCombatUi.Capability::entityDesignation)
        .findFirst().orElse(null);
  }

  private boolean capabilityReady() {
    DnvCombatUi.Capability capability = capability();
    return capability != null && capability.ready();
  }

  private Component capabilityLine() {
    DnvCombatUi.Capability capability = capability();
    if (capability == null) return message("capability_missing");
    return message("designation_capability", code(capability.issue()));
  }

  private Component orderLine() {
    if (view == null || view.order().isEmpty()) return message("no_order");
    DnvCombatUi.Order order = view.order().orElseThrow();
    long remaining = Math.max(0L, order.expiresGameTime() - view.serverGameTime() - sinceViewTicks);
    return message("order", phase(order.phase()), remaining, code(order.reason()));
  }

  private int orderColor() {
    if (view == null || view.order().isEmpty()) return MUTED;
    return switch (view.order().orElseThrow().phase()) {
      case REJECTED, EXPIRED -> BAD;
      case CANCELLED -> MUTED;
      default -> WARN;
    };
  }

  private Component previewLine() {
    return preview == null ? message("preview_none")
        : message("preview_entity", shortId(preview.target().entityId()));
  }

  private static Component phase(DnvCombatUi.Phase phase) {
    return message("phase." + phase.name().toLowerCase(Locale.ROOT));
  }

  private static Component code(String value) {
    return Component.translatableWithFallback(
        "message.doggiesnewvehicle.combat.code." + value.toLowerCase(Locale.ROOT), value);
  }

  private static String shortId(UUID value) {
    return value.toString().substring(0, 8);
  }

  private static Component message(String suffix, Object... arguments) {
    return Component.translatable("screen.doggiesnewvehicle.vehicle_operations.combat." + suffix, arguments);
  }

  private static void draw(
      GuiGraphics graphics, Font font, Component value, int x, int y, int width, int color) {
    graphics.drawString(font, font.plainSubstrByWidth(value.getString(), Math.max(1, width)), x, y, color);
  }

  private record Preview(
      DnvManagedVehicleDirectory.Selection selection, long expectedRevision,
      DnvCombatUi.TargetIdentity target) {}
}

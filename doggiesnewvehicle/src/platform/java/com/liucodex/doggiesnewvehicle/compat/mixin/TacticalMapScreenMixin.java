package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import com.atsuishio.superbwarfare.client.screens.TacticalMapScreen;
import com.liucodex.doggiesnewvehicle.compat.DnvTacticalMapLayout;
import com.liucodex.doggiesnewvehicle.compat.DnvTacticalRouteClientGate;
import com.liucodex.doggiesnewvehicle.compat.DnvTacticalRouteNetwork;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteView;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 0.8.9.1 tactical-map route draft layer; SBW markers stay client-only. */
@Mixin(value = TacticalMapScreen.class, remap = false)
public abstract class TacticalMapScreenMixin extends Screen
    implements DnvTacticalRouteView {
  @Shadow(remap = false) private int mapLeft;
  @Shadow(remap = false) private int mapTop;
  @Shadow(remap = false) private int mapAreaW;
  @Shadow(remap = false) private int mapAreaH;
  @Shadow(remap = false) private float mapCenterX;
  @Shadow(remap = false) private float mapCenterY;
  @Shadow(remap = false) private double viewBlockX;
  @Shadow(remap = false) private double viewBlockZ;
  @Shadow(remap = false) private double zoom;
  @Shadow(remap = false) @Final private Button followBtn;

  @Unique private final List<DnvRouteWaypoint> dnv$route =
      new ArrayList<>();
  @Unique private Button dnv$routeButton;
  @Unique private Button dnv$applyButton;
  @Unique private Button dnv$undoButton;
  @Unique private Button dnv$cycleDraftButton;
  @Unique private Button dnv$newDraftButton;
  @Unique private Button dnv$startButton;
  @Unique private Button dnv$loopButton;
  @Unique private Button dnv$stopButton;
  @Unique private Button dnv$landButton;
  @Unique private DnvTacticalRouteClientGate dnv$routeGate;
  @Unique private DnvTacticalRouteDrafts.DraftProjection dnv$draft;
  @Unique private DnvAirRouteValidation.Result dnv$routeValidation;
  @Unique private long dnv$routeRevision;
  @Unique private long dnv$editVersion;
  @Unique private boolean dnv$routeLoaded;
  @Unique private boolean dnv$routeMode;
  @Unique private boolean dnv$routeDirty;
  @Unique private boolean dnv$routeWaiting;
  @Unique private boolean dnv$controlsVisible;
  @Unique private Component dnv$routeStatus = Component.empty();

  protected TacticalMapScreenMixin() {
    super(Component.empty());
  }

  @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"), remap = false)
  private void dnv$addRouteControls(CallbackInfo callback) {
    dnv$routeButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.open"),
        button -> dnv$toggleRouteMode())
        .bounds(0, 0, 72, 20)
        .build());
    dnv$applyButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.apply"),
        button -> dnv$applyRoute())
        .bounds(0, 0, 56, 20)
        .build());
    dnv$undoButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.undo"),
        button -> dnv$undoWaypoint())
        .bounds(0, 0, 52, 20)
        .build());
    dnv$cycleDraftButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.cycle"),
        button -> dnv$changeDraft(DnvTacticalRouteProtocol.Action.CYCLE))
        .bounds(0, 0, 72, 20)
        .build());
    dnv$newDraftButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.new"),
        button -> dnv$changeDraft(DnvTacticalRouteProtocol.Action.CREATE))
        .bounds(0, 0, 56, 20)
        .build());
    dnv$startButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.start"),
        button -> dnv$sendPlatformCommand("start"))
        .bounds(0, 0, 56, 20)
        .build());
    dnv$loopButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.loop"),
        button -> dnv$sendPlatformCommand("loop"))
        .bounds(0, 0, 56, 20)
        .build());
    dnv$stopButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.stop"),
        button -> dnv$sendPlatformCommand("stop"))
        .bounds(0, 0, 56, 20)
        .build());
    dnv$landButton = addRenderableWidget(Button.builder(
        Component.translatable("screen.doggiesnewvehicle.tactical_route.land"),
        button -> dnv$sendPlatformCommand("land"))
        .bounds(0, 0, 56, 20)
        .build());
    dnv$routeGate = new DnvTacticalRouteClientGate();
    dnv$draft = null;
    dnv$routeValidation = null;
    dnv$routeRevision = 0L;
    dnv$editVersion = 0L;
    dnv$routeLoaded = false;
    dnv$routeMode = false;
    dnv$routeDirty = false;
    dnv$routeWaiting = true;
    dnv$route.clear();
    dnv$routeStatus = Component.empty();
    dnv$refreshControls();
    DnvTacticalRouteProtocol.RequestContext context = dnv$routeGate.begin(
        DnvTacticalRouteProtocol.Action.SNAPSHOT, Optional.empty(), dnv$editVersion);
    DnvTacticalRouteNetwork.requestSnapshot(context, Optional.empty(), 0L);
  }

  @Inject(
      method = {"render", "m_88315_"},
      at = @At(
          value = "INVOKE",
          target = "Lcom/atsuishio/superbwarfare/client/screens/"
              + "TacticalMapScreen;recomputeLayout()V",
          shift = At.Shift.AFTER),
      remap = false)
  private void dnv$layoutRouteControls(
      GuiGraphics graphics,
      int mouseX,
      int mouseY,
      float partialTick,
      CallbackInfo callback) {
    DnvTacticalMapLayout.Geometry layout = dnv$layout();
    dnv$controlsVisible = layout.controlsVisible();
    dnv$place(dnv$routeButton, layout.routeButton());
    dnv$place(dnv$applyButton, layout.applyButton());
    dnv$place(dnv$undoButton, layout.undoButton());
    dnv$place(dnv$cycleDraftButton, layout.cycleDraftButton());
    dnv$place(dnv$newDraftButton, layout.newDraftButton());
    dnv$place(dnv$startButton, layout.applyButton());
    dnv$place(dnv$loopButton, layout.undoButton());
    dnv$place(dnv$stopButton, layout.cycleDraftButton());
    dnv$place(dnv$landButton, layout.newDraftButton());
    dnv$refreshControls();
  }

  @Inject(
      method = {"render", "m_88315_"},
      at = @At(
          value = "INVOKE",
          target = "Lcom/atsuishio/superbwarfare/client/screens/"
              + "TacticalMapScreen;renderPositionMarkers("
              + "Lnet/minecraft/client/gui/GuiGraphics;)V",
          shift = At.Shift.AFTER),
      remap = false)
  private void dnv$renderRouteOverlay(
      GuiGraphics graphics,
      int mouseX,
      int mouseY,
      float partialTick,
      CallbackInfo callback) {
    dnv$renderRoute(graphics);
  }

  @Inject(
      method = {"mouseClicked", "m_6375_"},
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void dnv$routeMapClick(
      double mouseX,
      double mouseY,
      int button,
      CallbackInfoReturnable<Boolean> callback) {
    if (!dnv$routeMode || !dnv$isInsideMap(mouseX, mouseY)) return;
    if (dnv$routeWaiting) {
      callback.setReturnValue(true);
      return;
    }
    if (button == 0) {
      dnv$addWaypoint(mouseX, mouseY);
      callback.setReturnValue(true);
    } else if (button == 1) {
      dnv$undoWaypoint();
      callback.setReturnValue(true);
    }
  }

  @Unique
  private void dnv$toggleRouteMode() {
    if (!dnv$routeLoaded || dnv$routeWaiting) return;
    dnv$routeMode = !dnv$routeMode;
    dnv$routeStatus = Component.translatable(dnv$routeMode
        ? "screen.doggiesnewvehicle.tactical_route.status.editing"
        : "screen.doggiesnewvehicle.tactical_route.status.closed");
    dnv$refreshControls();
  }

  @Unique
  private void dnv$addWaypoint(double mouseX, double mouseY) {
    if (minecraft == null || minecraft.player == null
        || dnv$route.size() >= DnvRouteWaypoint.MAX_WAYPOINTS) {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.limit");
      return;
    }
    double scale = zoom / 5.0D;
    if (!Double.isFinite(scale) || scale == 0.0D) return;
    double worldX = viewBlockX + (mouseX - mapCenterX) / scale;
    double worldZ = viewBlockZ + (mouseY - mapCenterY) / scale;
    int blockX = (int) Math.floor(worldX);
    int blockZ = (int) Math.floor(worldZ);
    int worldY = minecraft.player.getBlockY();
    if (minecraft.player.level().hasChunk(blockX >> 4, blockZ >> 4)) {
      worldY = minecraft.player.level().getHeight(
          Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
    }
    DnvRouteWaypoint waypoint =
        new DnvRouteWaypoint(worldX, worldY, worldZ);
    if (!dnv$route.isEmpty()
        && dnv$distance(dnv$route.get(dnv$route.size() - 1), waypoint)
            < DnvTacticalRouteDrafts.MIN_WAYPOINT_SPACING) {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.too_close");
      return;
    }
    dnv$route.add(waypoint);
    dnv$routeValidation = null;
    dnv$editVersion++;
    dnv$routeDirty = true;
    dnv$routeStatus = Component.translatable(
        "screen.doggiesnewvehicle.tactical_route.status.added", dnv$route.size());
    dnv$refreshControls();
  }

  @Unique
  private void dnv$undoWaypoint() {
    if (!dnv$routeLoaded || dnv$routeWaiting || dnv$route.isEmpty()) return;
    dnv$route.remove(dnv$route.size() - 1);
    dnv$routeValidation = null;
    dnv$editVersion++;
    dnv$routeDirty = true;
    dnv$routeStatus = Component.translatable(
        "screen.doggiesnewvehicle.tactical_route.status.removed", dnv$route.size());
    dnv$refreshControls();
  }

  @Unique
  private void dnv$applyRoute() {
    if (!dnv$routeLoaded || dnv$routeWaiting || dnv$draft == null) return;
    DnvTacticalRouteProtocol.Action action = dnv$routeDirty
        ? DnvTacticalRouteProtocol.Action.COMMIT
        : DnvTacticalRouteProtocol.Action.SNAPSHOT;
    DnvTacticalRouteProtocol.RequestContext context = dnv$routeGate.begin(
        action, Optional.of(dnv$draft.reference()), dnv$editVersion);
    dnv$routeWaiting = true;
    dnv$routeStatus = Component.translatable(dnv$routeDirty
        ? "screen.doggiesnewvehicle.tactical_route.status.saving"
        : "screen.doggiesnewvehicle.tactical_route.status.validating");
    dnv$refreshControls();
    if (dnv$routeDirty) {
      DnvTacticalRouteNetwork.commit(
          context, dnv$draft.reference(), dnv$routeRevision, List.copyOf(dnv$route));
    } else {
      DnvTacticalRouteNetwork.requestSnapshot(
          context, Optional.of(dnv$draft.reference()), dnv$routeRevision);
    }
  }

  @Unique
  private void dnv$changeDraft(DnvTacticalRouteProtocol.Action action) {
    if (!dnv$routeLoaded || dnv$routeWaiting || dnv$routeDirty || dnv$draft == null
        || action != DnvTacticalRouteProtocol.Action.CREATE
            && action != DnvTacticalRouteProtocol.Action.CYCLE) return;
    DnvTacticalRouteProtocol.RequestContext context = dnv$routeGate.begin(
        action, Optional.of(dnv$draft.reference()), dnv$editVersion);
    dnv$routeWaiting = true;
    dnv$routeStatus = Component.translatable(
        "screen.doggiesnewvehicle.tactical_route.status.switching");
    dnv$refreshControls();
    DnvTacticalRouteNetwork.action(
        context, action, dnv$draft.reference(), dnv$routeRevision);
  }

  @Unique
  private void dnv$refreshControls() {
    if (dnv$routeButton == null) return;
    dnv$routeButton.visible = dnv$controlsVisible;
    dnv$routeButton.active = dnv$controlsVisible && dnv$routeLoaded
        && !dnv$routeWaiting;
    dnv$routeButton.setMessage(Component.translatable(dnv$routeMode
        ? "screen.doggiesnewvehicle.tactical_route.close"
        : "screen.doggiesnewvehicle.tactical_route.open"));
    dnv$applyButton.visible = dnv$controlsVisible && dnv$routeMode;
    dnv$applyButton.active = dnv$applyButton.visible
        && dnv$routeLoaded && !dnv$routeWaiting;
    dnv$applyButton.setMessage(Component.translatable(dnv$routeDirty
        ? "screen.doggiesnewvehicle.tactical_route.apply"
        : "screen.doggiesnewvehicle.tactical_route.revalidate"));
    dnv$undoButton.visible = dnv$controlsVisible && dnv$routeMode;
    dnv$undoButton.active = dnv$undoButton.visible
        && dnv$routeLoaded && !dnv$routeWaiting && !dnv$route.isEmpty();
    dnv$cycleDraftButton.visible = dnv$controlsVisible && dnv$routeMode;
    dnv$cycleDraftButton.active = dnv$cycleDraftButton.visible
        && !dnv$routeDirty && !dnv$routeWaiting && dnv$draft != null
        && dnv$draft.totalDrafts() > 1;
    dnv$newDraftButton.visible = dnv$controlsVisible && dnv$routeMode;
    dnv$newDraftButton.active = dnv$newDraftButton.visible
        && !dnv$routeDirty && !dnv$routeWaiting && dnv$draft != null;
    boolean actionVisible = dnv$controlsVisible && !dnv$routeMode
        && dnv$routeLoaded && dnv$draft != null;
    boolean routeExecutable = actionVisible && !dnv$routeDirty
        && !dnv$routeWaiting && dnv$routeValidation != null
        && dnv$routeValidation.status() == DnvAirRouteValidation.Status.EXECUTABLE;
    dnv$startButton.visible = actionVisible;
    dnv$startButton.active = routeExecutable;
    dnv$loopButton.visible = actionVisible;
    dnv$loopButton.active = routeExecutable;
    dnv$stopButton.visible = actionVisible;
    dnv$stopButton.active = actionVisible && !dnv$routeWaiting;
    dnv$landButton.visible = actionVisible;
    dnv$landButton.active = actionVisible && !dnv$routeWaiting;
  }

  @Unique
  private void dnv$sendPlatformCommand(String action) {
    if (dnv$routeMode || !dnv$routeLoaded || dnv$routeWaiting
        || dnv$draft == null || minecraft == null || minecraft.player == null) return;
    if (!action.equals("stop") && !action.equals("land")
        && (dnv$routeDirty || dnv$routeValidation == null
        || dnv$routeValidation.status() != DnvAirRouteValidation.Status.EXECUTABLE)) return;
    minecraft.player.connection.sendCommand("dnv hover_platform " + action + " "
        + dnv$draft.binding().vehicleId());
    dnv$routeStatus = Component.translatable(
        "screen.doggiesnewvehicle.tactical_route.status.command_requested",
        Component.translatable("screen.doggiesnewvehicle.tactical_route." + action));
  }

  @Unique
  private void dnv$renderRoute(GuiGraphics graphics) {
    if (dnv$routeLoaded && !dnv$route.isEmpty() && mapAreaW > 0 && mapAreaH > 0) {
      double scale = zoom / 5.0D;
      if (Double.isFinite(scale) && scale > 0.0D) {
        graphics.enableScissor(mapLeft, mapTop, mapLeft + mapAreaW, mapTop + mapAreaH);
        try {
          for (int index = 1; index < dnv$route.size(); index++) {
            DnvRouteWaypoint first = dnv$route.get(index - 1);
            DnvRouteWaypoint second = dnv$route.get(index);
            dnv$drawLine(graphics,
                mapCenterX + (first.x() - viewBlockX) * scale,
                mapCenterY + (first.z() - viewBlockZ) * scale,
                mapCenterX + (second.x() - viewBlockX) * scale,
                mapCenterY + (second.z() - viewBlockZ) * scale,
                dnv$segmentColor(index - 1));
          }
          for (int index = 0; index < dnv$route.size(); index++) {
            DnvRouteWaypoint waypoint = dnv$route.get(index);
            int x = (int) Math.round(mapCenterX + (waypoint.x() - viewBlockX) * scale);
            int y = (int) Math.round(mapCenterY + (waypoint.z() - viewBlockZ) * scale);
            if (dnv$isInsideMap(x, y)) {
              int color = dnv$waypointColor(index);
              graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF101820);
              graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
              graphics.drawString(font, Integer.toString(index + 1), x + 5, y - 4,
                  color, true);
            }
          }
        } finally {
          graphics.disableScissor();
        }
      }
    }
    if (!dnv$routeStatus.getString().isEmpty()
        && mapAreaW > 12 && mapAreaH > 16) {
      int textWidth = Math.max(1, Math.min(mapAreaW - 12,
          width - Math.max(0, mapLeft) - 12));
      graphics.drawString(font,
          font.plainSubstrByWidth(dnv$routeStatus.getString(), textWidth),
          mapLeft + 6, mapTop + mapAreaH - 14, 0xFFFFFFFF, true);
    }
  }

  @Unique
  private static void dnv$drawLine(
      GuiGraphics graphics, double firstX, double firstY,
      double secondX, double secondY, int color) {
    double deltaX = secondX - firstX;
    double deltaY = secondY - firstY;
    float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    if (!Float.isFinite(length) || length < 1.0F) return;
    float centerX = (float) ((firstX + secondX) * 0.5D);
    float centerY = (float) ((firstY + secondY) * 0.5D);
    float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    graphics.pose().pushPose();
    graphics.pose().translate(centerX, centerY, 0.0F);
    graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
    graphics.fill((int) (-length * 0.5F), 0, (int) (length * 0.5F), 2, color);
    graphics.pose().popPose();
  }

  @Unique
  private int dnv$segmentColor(int segmentIndex) {
    if (dnv$routeDirty || dnv$routeValidation == null) {
      return dnv$routeDirty ? 0xFFFFB347 : 0xFF6FE7FF;
    }
    return dnv$routeValidation.segments().stream()
        .filter(segment -> segment.segmentIndex() == segmentIndex)
        .findFirst()
        .map(segment -> switch (segment.status()) {
          case EXECUTABLE -> 0xFF62E68A;
          case BLOCKED -> 0xFFFF5A5F;
          case UNVERIFIED -> 0xFFFFD166;
        })
        .orElse(0xFF6FE7FF);
  }

  @Unique
  private int dnv$waypointColor(int waypointIndex) {
    if (waypointIndex > 0) return dnv$segmentColor(waypointIndex - 1);
    return dnv$route.size() > 1 ? dnv$segmentColor(0) : 0xFF6FE7FF;
  }

  @Unique
  private static double dnv$distance(
      DnvRouteWaypoint first,
      DnvRouteWaypoint second) {
    double x = second.x() - first.x();
    double y = second.y() - first.y();
    double z = second.z() - first.z();
    return Math.sqrt(x * x + y * y + z * z);
  }

  @Unique
  private DnvTacticalMapLayout.Geometry dnv$layout() {
    return DnvTacticalMapLayout.calculate(
        width,
        height,
        mapLeft,
        mapTop,
        mapAreaW,
        mapAreaH,
        followBtn.getX(),
        followBtn.getY());
  }

  @Unique
  private static void dnv$place(
      Button button,
      DnvTacticalMapLayout.Rect bounds) {
    button.setX(bounds.x());
    button.setY(bounds.y());
    button.setWidth(bounds.width());
  }

  @Unique
  private boolean dnv$isInsideMap(double x, double y) {
    return x >= mapLeft
        && x <= mapLeft + mapAreaW
        && y >= mapTop
        && y <= mapTop + mapAreaH;
  }

  @Override
  public void dnv$acceptRouteResponse(DnvTacticalRouteProtocol.RouteResponse response) {
    if (dnv$routeGate == null) return;
    DnvTacticalRouteClientGate.Decision decision = dnv$routeGate.accept(
        response, Optional.ofNullable(dnv$draft).map(
            DnvTacticalRouteDrafts.DraftProjection::reference), dnv$editVersion);
    if (decision.disposition() == DnvTacticalRouteClientGate.Disposition.IGNORED) {
      if (!dnv$routeGate.waiting()) {
        dnv$routeWaiting = false;
        dnv$refreshControls();
      }
      return;
    }
    dnv$routeWaiting = false;
    if (decision.disposition() == DnvTacticalRouteClientGate.Disposition.NO_PROJECTION) {
      dnv$draft = null;
      dnv$route.clear();
      dnv$routeRevision = 0L;
      dnv$routeValidation = null;
      dnv$routeLoaded = false;
      dnv$routeMode = false;
      dnv$routeDirty = false;
      dnv$routeStatus = response.issue() == DnvTacticalRouteDrafts.Issue.BINDING_REQUIRED
          ? Component.translatable(
              "screen.doggiesnewvehicle.tactical_route.status.binding_required")
          : Component.translatable(
              "screen.doggiesnewvehicle.tactical_route.status.rejected",
              response.issue().name());
      dnv$refreshControls();
      return;
    }
    DnvTacticalRouteDrafts.DraftProjection projection = response.projection().orElseThrow();
    dnv$draft = projection;
    dnv$routeRevision = projection.snapshot().revision();
    if (decision.disposition() == DnvTacticalRouteClientGate.Disposition.REPLACE_LOCAL) {
      dnv$route.clear();
      dnv$route.addAll(projection.snapshot().waypoints());
      dnv$routeDirty = false;
      dnv$routeValidation = response.validation().orElse(null);
    } else {
      dnv$routeDirty = true;
      dnv$routeValidation = null;
    }
    dnv$routeLoaded = true;
    if (!response.accepted()) {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.rejected",
          response.issue().name());
    } else if (dnv$routeValidation != null) {
      dnv$routeStatus = dnv$validationStatus(dnv$routeValidation);
    } else if (response.action() == DnvTacticalRouteProtocol.Action.CREATE) {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.created",
          projection.ordinal(), projection.totalDrafts());
    } else if (response.action() == DnvTacticalRouteProtocol.Action.CYCLE
        || response.action() == DnvTacticalRouteProtocol.Action.SELECT) {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.selected",
          projection.ordinal(), projection.totalDrafts());
    } else {
      dnv$routeStatus = Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.status.loaded", dnv$route.size());
    }
    dnv$refreshControls();
  }

  @Unique
  private Component dnv$validationStatus(DnvAirRouteValidation.Result validation) {
    if (validation.status() == DnvAirRouteValidation.Status.EXECUTABLE) {
      return Component.translatable(
          "screen.doggiesnewvehicle.tactical_route.validation.executable",
          validation.segments().size());
    }
    int problemSegment = validation.firstProblemSegmentIndex();
    String reasonKey = "screen.doggiesnewvehicle.tactical_route.reason."
        + validation.reason().name().toLowerCase(java.util.Locale.ROOT);
    return problemSegment >= 0
        ? Component.translatable(
            validation.status() == DnvAirRouteValidation.Status.BLOCKED
                ? "screen.doggiesnewvehicle.tactical_route.validation.blocked_segment"
                : "screen.doggiesnewvehicle.tactical_route.validation.unverified_segment",
            problemSegment + 1, Component.translatable(reasonKey))
        : Component.translatable(
            validation.status() == DnvAirRouteValidation.Status.BLOCKED
                ? "screen.doggiesnewvehicle.tactical_route.validation.blocked_route"
                : "screen.doggiesnewvehicle.tactical_route.validation.unverified_route",
            Component.translatable(reasonKey));
  }

}

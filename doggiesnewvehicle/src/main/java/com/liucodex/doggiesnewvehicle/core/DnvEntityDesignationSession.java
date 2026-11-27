package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Core-bound, transient command lifecycle. The combat order service remains the intent owner. */
public final class DnvEntityDesignationSession {
  private final UUID platformId;
  private final DnvCombatOrderService orders;
  private Optional<Binding> binding = Optional.empty();
  private State state = State.UNAVAILABLE;
  private String executionReason = "NONE";
  private DnvCombatOrderService.OrderSnapshot lastActiveOrder;
  private DnvCombatOrderService.OrderSnapshot terminalOrder;

  public DnvEntityDesignationSession(UUID platformId) {
    this.platformId = Objects.requireNonNull(platformId, "platformId");
    orders = new DnvCombatOrderService(platformId);
  }

  public static boolean allowsOperator(
      UUID issuer, UUID owner, boolean administrator, boolean available, double distanceSquared) {
    return available && (issuer.equals(owner) || administrator)
        && Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
        && distanceSquared <= DnvCombatOrderService.MAX_INTERACTION_RANGE
            * DnvCombatOrderService.MAX_INTERACTION_RANGE;
  }

  public void synchronize(Optional<Binding> current) {
    Objects.requireNonNull(current, "current");
    Optional<Binding> normalized = current.filter(value -> value.platformId().equals(platformId));
    if (!binding.equals(normalized)) {
      orders.clear();
      binding = normalized;
      state = normalized.isEmpty() ? State.UNAVAILABLE : State.CORE_CHANGED;
      executionReason = "NONE";
      lastActiveOrder = null;
      terminalOrder = null;
    }
  }

  public Result submit(
      Binding expectedBinding, DnvCombatOrderService.Command command,
      DnvCombatOrderService.ValidationContext context) {
    View before = view(context.gameTime());
    if (binding.filter(expectedBinding::equals).isEmpty()) {
      return new Result(false, "CORE_CHANGED", command.operationId(), before);
    }
    Binding current = binding.orElseThrow();
    if (!command.dimension().equals(current.dimension())) {
      return new Result(false, "DIMENSION_MISMATCH", command.operationId(), before);
    }
    DnvCombatOrderService.Receipt receipt = orders.submit(command, context);
    boolean accepted = receipt.status() == DnvCombatOrderService.Status.ACCEPTED;
    if (accepted && receipt.authoritativeRevision() > before.orders().revision()) {
      executionReason = "NONE";
      if (command.type() == DnvCombatOrderService.OrderType.CANCEL_ORDER) {
        terminalOrder = before.orders().activeOrder().orElse(before.terminalOrder().orElse(null));
        state = State.CANCELLED;
      } else {
        terminalOrder = null;
      }
    }
    return new Result(accepted, receipt.reason().name(), command.operationId(), view(context.gameTime()));
  }

  public View view(long gameTime) {
    DnvCombatOrderService.Snapshot snapshot = orders.snapshot(gameTime);
    if (snapshot.activeOrder().isPresent()) {
      lastActiveOrder = snapshot.activeOrder().orElseThrow();
      state = State.ACTIVE;
    } else if (state == State.ACTIVE) {
      terminalOrder = lastActiveOrder;
      state = State.EXPIRED;
      executionReason = "EXPIRED";
    }
    return new View(binding, snapshot, Optional.ofNullable(terminalOrder), state, executionReason);
  }

  public Optional<DnvCombatOrderService.Command> commandForOperation(UUID operationId) {
    return orders.commandForOperation(operationId);
  }

  public void invalidate(State reason, long gameTime) {
    if (reason == State.ACTIVE || reason == State.EXPIRED) {
      throw new IllegalArgumentException("not an external invalidation reason");
    }
    DnvCombatOrderService.OrderSnapshot active = view(gameTime).orders().activeOrder().orElse(null);
    if (active != null) {
      terminalOrder = active;
      orders.clear();
      state = reason;
      executionReason = reason.name();
    }
  }

  public record Binding(
      UUID platformId, UUID coreId, long coreRevision, UUID ownerId, String dimension) {
    public Binding {
      Objects.requireNonNull(platformId, "platformId");
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(ownerId, "ownerId");
      Objects.requireNonNull(dimension, "dimension");
      if (coreRevision < 0L) {
        throw new IllegalArgumentException("negative core revision");
      }
    }
  }

  public record View(
      Optional<Binding> binding, DnvCombatOrderService.Snapshot orders,
      Optional<DnvCombatOrderService.OrderSnapshot> terminalOrder,
      State state, String executionReason) {
    public Optional<UUID> targetId() {
      return orders.activeOrder().flatMap(DnvCombatOrderService.OrderSnapshot::targetId);
    }
    public Optional<DnvCombatOrderService.OrderSnapshot> displayedOrder() {
      return orders.activeOrder().or(() -> terminalOrder);
    }
  }

  public record Result(boolean accepted, String reason, UUID operationId, View view) {}

  public enum State {
    UNAVAILABLE, CORE_CHANGED, ACTIVE, CANCELLED, EXPIRED,
    TARGET_INVALID, ISSUER_UNAVAILABLE, EXTERNAL_CONTROL
  }
}

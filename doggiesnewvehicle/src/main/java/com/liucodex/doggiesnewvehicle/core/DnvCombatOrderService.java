package com.liucodex.doggiesnewvehicle.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-authoritative owner of finite crew entity designations and cancellation.
 * Accepted orders are intent snapshots only; this class has no weapon or world mutation method.
 */
public final class DnvCombatOrderService {
  public static final int CONTRACT_VERSION = 3;
  public static final double MAX_INTERACTION_RANGE = 256.0D;
  public static final int MAX_TTL_TICKS = 1_200;
  private static final int MAX_OPERATION_HISTORY = 128;
  private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

  private final UUID platformId;
  private final Map<UUID, StoredOperation> operations = new LinkedHashMap<>();
  private long revision;
  private OrderSnapshot activeOrder;

  public DnvCombatOrderService(UUID platformId) {
    this.platformId = Objects.requireNonNull(platformId, "platformId");
  }

  public Receipt submit(Command command, ValidationContext context) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(context, "context");
    snapshot(context.gameTime());
    Reason access = validateAccess(command, context);
    if (access != Reason.NONE) {
      return Receipt.rejected(revision, access);
    }
    StoredOperation replay = operations.get(command.operationId());
    if (replay != null) {
      return replay.command().equals(command)
          ? replay.receipt()
          : Receipt.rejected(revision, Reason.OPERATION_COLLISION);
    }
    Reason rejection = validate(command, context);
    if (rejection != Reason.NONE) {
      Receipt receipt = Receipt.rejected(revision, rejection);
      remember(command, receipt);
      return receipt;
    }
    revision = nextRevision(revision);
    if (command.type() == OrderType.CANCEL_ORDER) {
      activeOrder = null;
    } else {
      activeOrder = new OrderSnapshot(
          command.orderId(), command.issuerId(), platformId, command.type(),
          command.dimension(), command.targetId(),
          revision, command.issuedGameTime(), command.expiresGameTime());
    }
    Receipt receipt = new Receipt(Status.ACCEPTED, revision, Reason.NONE);
    remember(command, receipt);
    return receipt;
  }

  public Snapshot snapshot(long gameTime) {
    if (gameTime < 0L) {
      throw new IllegalArgumentException("game time must not be negative");
    }
    if (activeOrder != null && !activeOrder.liveAt(gameTime)) {
      activeOrder = null;
      revision = nextRevision(revision);
    }
    return new Snapshot(platformId, revision, Optional.ofNullable(activeOrder));
  }

  public void clear() {
    activeOrder = null;
    operations.clear();
    revision = nextRevision(revision);
  }

  /** Returns the canonical server-timed command for an idempotent retry. */
  public Optional<Command> commandForOperation(UUID operationId) {
    Objects.requireNonNull(operationId, "operationId");
    return Optional.ofNullable(operations.get(operationId)).map(StoredOperation::command);
  }

  private Reason validateAccess(Command command, ValidationContext context) {
    if (!command.platformId().equals(platformId)) {
      return Reason.PLATFORM_MISMATCH;
    }
    if (!command.issuerId().equals(context.authenticatedIssuer())) {
      return Reason.ISSUER_MISMATCH;
    }
    if (!context.permissionGranted()) {
      return Reason.PERMISSION_DENIED;
    }
    if (!command.dimension().equals(context.activeDimension())) {
      return Reason.DIMENSION_MISMATCH;
    }
    if (!Double.isFinite(context.interactionDistance())
        || context.interactionDistance() < 0.0D
        || context.interactionDistance() > MAX_INTERACTION_RANGE) {
      return Reason.OUT_OF_RANGE;
    }
    if (command.issuedGameTime() > context.gameTime()
        || command.expiresGameTime() < context.gameTime()
        || command.expiresGameTime() - command.issuedGameTime() > MAX_TTL_TICKS) {
      return Reason.TIME_INVALID;
    }
    return Reason.NONE;
  }

  private Reason validate(Command command, ValidationContext context) {
    if (command.expectedRevision() != revision) {
      return Reason.REVISION_MISMATCH;
    }
    if (command.type() == OrderType.DESIGNATE_ENTITY) {
      if (!context.targetExists() || !context.targetEligible()) {
        return Reason.TARGET_INVALID;
      }
      if (context.targetKind() == TargetKind.PLAYER
          || context.targetKind() == TargetKind.PLAYER_OCCUPIED_VEHICLE) {
        return Reason.PVP_FORBIDDEN;
      }
    }
    return Reason.NONE;
  }

  private void remember(Command command, Receipt receipt) {
    while (operations.size() >= MAX_OPERATION_HISTORY) {
      operations.remove(operations.keySet().iterator().next());
    }
    operations.put(command.operationId(), new StoredOperation(command, receipt));
  }

  private static long nextRevision(long current) {
    if (current == Long.MAX_VALUE) {
      throw new IllegalStateException("combat order revision exhausted");
    }
    return current + 1L;
  }

  private static String checkedDimension(String dimension) {
    String checked = Objects.requireNonNull(dimension, "dimension").trim();
    if (!DIMENSION.matcher(checked).matches()) {
      throw new IllegalArgumentException("dimension must be a namespaced key");
    }
    return checked;
  }

  public record Command(
      UUID operationId,
      UUID orderId,
      UUID issuerId,
      UUID platformId,
      long expectedRevision,
      long issuedGameTime,
      long expiresGameTime,
      String dimension,
      OrderType type,
      Optional<UUID> targetId) {
    public Command {
      Objects.requireNonNull(operationId, "operationId");
      Objects.requireNonNull(orderId, "orderId");
      Objects.requireNonNull(issuerId, "issuerId");
      Objects.requireNonNull(platformId, "platformId");
      dimension = checkedDimension(dimension);
      Objects.requireNonNull(type, "type");
      targetId = Objects.requireNonNull(targetId, "targetId");
      if (expectedRevision < 0L
          || issuedGameTime < 0L
          || expiresGameTime < issuedGameTime) {
        throw new IllegalArgumentException("command time or revision is invalid");
      }
      boolean needsTarget = type == OrderType.DESIGNATE_ENTITY;
      if (needsTarget != targetId.isPresent()) {
        throw new IllegalArgumentException("command payload does not match its type");
      }
    }
  }

  public record ValidationContext(
      long gameTime,
      UUID authenticatedIssuer,
      String activeDimension,
      boolean permissionGranted,
      double interactionDistance,
      boolean targetExists,
      boolean targetEligible,
      TargetKind targetKind) {
    public ValidationContext {
      if (gameTime < 0L) {
        throw new IllegalArgumentException("game time must not be negative");
      }
      Objects.requireNonNull(authenticatedIssuer, "authenticatedIssuer");
      activeDimension = checkedDimension(activeDimension);
      Objects.requireNonNull(targetKind, "targetKind");
    }
  }

  public record OrderSnapshot(
      UUID orderId,
      UUID issuerId,
      UUID platformId,
      OrderType type,
      String dimension,
      Optional<UUID> targetId,
      long revision,
      long issuedGameTime,
      long expiresGameTime) {
    public OrderSnapshot {
      Objects.requireNonNull(orderId, "orderId");
      Objects.requireNonNull(issuerId, "issuerId");
      Objects.requireNonNull(platformId, "platformId");
      Objects.requireNonNull(type, "type");
      dimension = checkedDimension(dimension);
      targetId = Objects.requireNonNull(targetId, "targetId");
    }

    public boolean liveAt(long gameTime) {
      return gameTime >= issuedGameTime && gameTime <= expiresGameTime;
    }
  }

  public record Snapshot(UUID platformId, long revision, Optional<OrderSnapshot> activeOrder) {
    public Snapshot {
      Objects.requireNonNull(platformId, "platformId");
      activeOrder = Objects.requireNonNull(activeOrder, "activeOrder");
    }
  }

  public record Receipt(Status status, long authoritativeRevision, Reason reason) {
    public Receipt {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(reason, "reason");
      if ((status == Status.ACCEPTED) != (reason == Reason.NONE)) {
        throw new IllegalArgumentException("receipt status is inconsistent");
      }
    }

    private static Receipt rejected(long revision, Reason reason) {
      return new Receipt(Status.REJECTED, revision, reason);
    }
  }

  private record StoredOperation(Command command, Receipt receipt) {}

  public enum OrderType {
    DESIGNATE_ENTITY,
    CANCEL_ORDER
  }

  public enum TargetKind {
    NONE,
    LIVING,
    VEHICLE_GROUND,
    VEHICLE_AIR,
    VEHICLE_SURFACE,
    PLAYER,
    PLAYER_OCCUPIED_VEHICLE,
    UNKNOWN
  }

  public enum Status {
    ACCEPTED,
    REJECTED
  }

  public enum Reason {
    NONE,
    OPERATION_COLLISION,
    PLATFORM_MISMATCH,
    ISSUER_MISMATCH,
    PERMISSION_DENIED,
    DIMENSION_MISMATCH,
    OUT_OF_RANGE,
    TIME_INVALID,
    REVISION_MISMATCH,
    TARGET_INVALID,
    PVP_FORBIDDEN
  }
}

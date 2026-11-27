package com.liucodex.doggiesnewvehicle.mission;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded neutral DTOs for the server-owned combat intent UI. */
public final class DnvCombatUi {
  public static final int MAX_TARGETS = 32;
  public static final int MAX_CAPABILITIES = 8;
  public static final int MAX_TEXT = 160;

  private DnvCombatUi() {}

  public record TargetIdentity(String dimension, UUID entityId) {
    public TargetIdentity {
      dimension = text(dimension, 256);
      Objects.requireNonNull(entityId, "entityId");
    }
  }

  public record Request(
      UUID requestId,
      long sequence,
      DnvManagedVehicleDirectory.Selection selection,
      long expectedOrderRevision,
      UUID operationId,
      Action action,
      Optional<TargetIdentity> target) {
    public Request {
      Objects.requireNonNull(requestId, "requestId");
      Objects.requireNonNull(selection, "selection");
      Objects.requireNonNull(operationId, "operationId");
      Objects.requireNonNull(action, "action");
      target = Objects.requireNonNull(target, "target");
      if (sequence < 0L || expectedOrderRevision < 0L) {
        throw new IllegalArgumentException("negative combat request sequence or revision");
      }
      boolean entity = action == Action.DESIGNATE_ENTITY;
      if (entity != target.isPresent()) {
        throw new IllegalArgumentException("combat request payload does not match its action");
      }
    }
  }

  public record Capability(
      boolean entityDesignation,
      boolean ready,
      String issue) {
    public Capability {
      issue = text(issue, 96);
    }
  }

  public record Target(
      TargetIdentity identity, String label, boolean eligible, String issue) {
    public Target {
      Objects.requireNonNull(identity, "identity");
      label = text(label, MAX_TEXT);
      issue = text(issue, 96);
    }
  }

  public record Order(
      UUID orderId,
      long revision,
      Optional<TargetIdentity> target,
      boolean cancellable,
      Phase phase,
      String reason,
      long expiresGameTime) {
    public Order {
      Objects.requireNonNull(orderId, "orderId");
      target = Objects.requireNonNull(target, "target");
      Objects.requireNonNull(phase, "phase");
      reason = text(reason, 96);
      if (revision < 0L || expiresGameTime < 0L || target.isEmpty()) {
        throw new IllegalArgumentException("invalid combat order projection");
      }
    }
  }

  public record View(
      UUID requestId,
      long sequence,
      DnvManagedVehicleDirectory.Selection selection,
      String result,
      long orderRevision,
      long serverGameTime,
      List<Capability> capabilities,
      List<Target> targets,
      Optional<Order> order) {
    public View {
      Objects.requireNonNull(requestId, "requestId");
      Objects.requireNonNull(selection, "selection");
      result = text(result, 96);
      capabilities = List.copyOf(capabilities);
      targets = List.copyOf(targets);
      order = Objects.requireNonNull(order, "order");
      if (sequence < 0L || orderRevision < 0L || serverGameTime < 0L
          || capabilities.size() > MAX_CAPABILITIES || targets.size() > MAX_TARGETS) {
        throw new IllegalArgumentException("combat view is out of bounds");
      }
    }
  }

  public enum Action { REFRESH, DESIGNATE_ENTITY, CANCEL }
  public enum Phase {
    INTENT_ACCEPTED, REJECTED, CANCELLED, EXPIRED
  }

  private static String text(String value, int maximum) {
    String checked = Objects.requireNonNull(value, "text");
    if (checked.isBlank() || checked.length() > maximum) {
      throw new IllegalArgumentException("combat text is out of bounds");
    }
    return checked;
  }
}

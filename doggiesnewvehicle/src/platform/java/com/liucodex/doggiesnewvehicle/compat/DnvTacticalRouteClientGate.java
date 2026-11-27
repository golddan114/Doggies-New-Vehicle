package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Screen-local correlation gate; it owns no route data and survives no screen close. */
public final class DnvTacticalRouteClientGate {
  private final Supplier<UUID> requestIds;
  private long nextSequence;
  private Pending pending;
  private boolean closed;

  public DnvTacticalRouteClientGate() {
    this(UUID::randomUUID);
  }

  DnvTacticalRouteClientGate(Supplier<UUID> requestIds) {
    this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
  }

  public DnvTacticalRouteProtocol.RequestContext begin(
      DnvTacticalRouteProtocol.Action action,
      Optional<DnvTacticalRouteDrafts.DraftRef> reference,
      long editVersion) {
    Objects.requireNonNull(action, "action");
    reference = Objects.requireNonNull(reference, "reference");
    if (closed || pending != null || editVersion < 0L) {
      throw new IllegalStateException("route screen cannot start this request");
    }
    if (nextSequence == Long.MAX_VALUE) {
      throw new IllegalStateException("route request sequence exhausted");
    }
    nextSequence++;
    DnvTacticalRouteProtocol.RequestContext context =
        new DnvTacticalRouteProtocol.RequestContext(
            Objects.requireNonNull(requestIds.get(), "request id"), nextSequence);
    pending = new Pending(context, action, reference, editVersion);
    return context;
  }

  public Decision accept(
      DnvTacticalRouteProtocol.RouteResponse response,
      Optional<DnvTacticalRouteDrafts.DraftRef> currentReference,
      long editVersion) {
    Objects.requireNonNull(response, "response");
    currentReference = Objects.requireNonNull(currentReference, "currentReference");
    if (closed || pending == null
        || !pending.context().equals(response.context())
        || pending.action() != response.action()) {
      return Decision.ignored();
    }
    Pending completed = pending;
    pending = null;
    Optional<DnvTacticalRouteDrafts.DraftProjection> projection = response.projection();
    if (projection.isEmpty()) {
      return new Decision(Disposition.NO_PROJECTION, response);
    }
    DnvTacticalRouteDrafts.DraftRef projected =
        projection.orElseThrow().reference();
    boolean sameSubmittedDraft = completed.reference()
        .map(projected::equals)
        .orElse(response.action() == DnvTacticalRouteProtocol.Action.SNAPSHOT);
    boolean stillCurrentDraft = currentReference
        .map(projected::equals)
        .orElse(response.action() == DnvTacticalRouteProtocol.Action.SNAPSHOT);
    if ((response.action() == DnvTacticalRouteProtocol.Action.COMMIT
            || response.action() == DnvTacticalRouteProtocol.Action.SELECT)
        && (!sameSubmittedDraft || !stillCurrentDraft)) {
      return Decision.ignored();
    }
    if (editVersion > completed.editVersion() && sameSubmittedDraft) {
      return new Decision(Disposition.KEEP_LOCAL_EDITS, response);
    }
    return new Decision(Disposition.REPLACE_LOCAL, response);
  }

  public boolean waiting() {
    return pending != null;
  }

  public void close() {
    closed = true;
    pending = null;
  }

  private record Pending(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteProtocol.Action action,
      Optional<DnvTacticalRouteDrafts.DraftRef> reference,
      long editVersion) {}

  public record Decision(
      Disposition disposition,
      DnvTacticalRouteProtocol.RouteResponse response) {
    public Decision {
      Objects.requireNonNull(disposition, "disposition");
    }

    private static Decision ignored() {
      return new Decision(Disposition.IGNORED, null);
    }
  }

  public enum Disposition {
    IGNORED,
    NO_PROJECTION,
    KEEP_LOCAL_EDITS,
    REPLACE_LOCAL
  }
}

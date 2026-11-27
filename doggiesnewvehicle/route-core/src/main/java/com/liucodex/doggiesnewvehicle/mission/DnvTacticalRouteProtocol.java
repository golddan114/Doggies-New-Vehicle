package com.liucodex.doggiesnewvehicle.mission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Network-neutral route messages shared by the route owner and transport adapters. */
public final class DnvTacticalRouteProtocol {
  private DnvTacticalRouteProtocol() {}

  public record RequestContext(UUID requestId, long sequence) {
    public RequestContext {
      Objects.requireNonNull(requestId, "requestId");
      if (sequence < 0L) {
        throw new IllegalArgumentException("route request sequence must not be negative");
      }
    }
  }

  public record RouteResponse(
      RequestContext context,
      Action action,
      boolean accepted,
      boolean replayed,
      Optional<DnvTacticalRouteDrafts.DraftProjection> projection,
      Optional<DnvAirRouteValidation.Result> validation,
      DnvTacticalRouteDrafts.Issue issue) {
    public RouteResponse {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(action, "action");
      projection = Objects.requireNonNull(projection, "projection");
      validation = Objects.requireNonNull(validation, "validation");
      Objects.requireNonNull(issue, "issue");
      if (accepted != (issue == DnvTacticalRouteDrafts.Issue.NONE)
          || accepted && projection.isEmpty()
          || projection.isPresent() != validation.isPresent()) {
        throw new IllegalArgumentException("inconsistent tactical route response");
      }
    }

    public static RouteResponse from(
        RequestContext context,
        Action action,
        DnvTacticalRouteDrafts.OperationResult result,
        Optional<DnvAirRouteValidation.Result> validation) {
      return new RouteResponse(
          context,
          action,
          result.accepted(),
          result.replayed(),
          result.projection(),
          validation,
          result.issue());
    }

    public static RouteResponse snapshot(
        RequestContext context,
        Optional<DnvTacticalRouteDrafts.DraftProjection> projection,
        Optional<DnvAirRouteValidation.Result> validation) {
      return new RouteResponse(
          context,
          Action.SNAPSHOT,
          projection.isPresent(),
          false,
          projection,
          validation,
          projection.isPresent()
              ? DnvTacticalRouteDrafts.Issue.NONE
              : DnvTacticalRouteDrafts.Issue.BINDING_REQUIRED);
    }
  }

  public enum Action {
    SNAPSHOT,
    SELECT,
    CREATE,
    CYCLE,
    COMMIT
  }
}

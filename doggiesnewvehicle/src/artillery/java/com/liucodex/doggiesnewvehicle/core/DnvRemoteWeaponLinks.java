package com.liucodex.doggiesnewvehicle.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Connection state owned exclusively by the world store; never grants a control or fire lease. */
public final class DnvRemoteWeaponLinks {
  public static final int MAX_STATIONS = 256;
  public static final int MAX_RECEIPTS = 512;
  private final Map<UUID, Station> stations = new LinkedHashMap<>();
  private final Map<UUID, UUID> targets = new LinkedHashMap<>();
  private final Map<Operation, Receipt> receipts = new LinkedHashMap<>();

  public Optional<Station> station(UUID id) { return Optional.ofNullable(stations.get(id)); }

  public Optional<Station> at(String dimension, long position) {
    return stations.values().stream()
        .filter(station -> station.dimension().equals(dimension) && station.position() == position).findFirst();
  }

  public Reason create(UUID id, UUID owner, String dimension, long position) {
    Station station = new Station(id, owner, dimension, position, 0, Optional.empty());
    if (stations.containsKey(id) || at(dimension, position).isPresent()) return Reason.STATION_EXISTS;
    if (stations.size() >= MAX_STATIONS) return Reason.CAPACITY;
    stations.put(id, station);
    return Reason.NONE;
  }

  /** Every request is already on the server thread. Observation is recomputed by the server service. */
  public Result apply(Request request, Reason observation) {
    Objects.requireNonNull(observation);
    Station station = stations.get(request.station());
    if (station == null) {
      Receipt retired = receipts.get(new Operation(request.actor(), request.operation()));
      if (retired != null && retired.request().equals(request) && request.action() == Action.RETIRE) {
        return new Result(retired.reason(), retired.revision(), request.operation(), true);
      }
      return result(request, Reason.STATION_MISSING, -1);
    }
    if (!station.owner().equals(request.actor())) return result(request, Reason.NOT_OWNER, station.revision());
    Operation key = new Operation(request.actor(), request.operation());
    Receipt previous = receipts.get(key);
    if (previous != null) {
      return previous.request().equals(request)
          ? new Result(previous.reason(), previous.revision(), request.operation(), true)
          : result(request, Reason.OPERATION_CONFLICT, station.revision());
    }
    Reason reason = request.expectedRevision() != station.revision() ? Reason.STALE_REVISION : observation;
    if (reason == Reason.NONE) {
      if (station.revision() == Long.MAX_VALUE && request.action() != Action.RETIRE) reason = Reason.REVISION_EXHAUSTED;
      else if (request.action() == Action.LINK && station.target().isPresent()) reason = Reason.STATION_ALREADY_LINKED;
      else if ((request.action() == Action.REPLACE || request.action() == Action.UNLINK)
          && station.target().isEmpty()) reason = Reason.STATION_UNLINKED;
      else if (request.target().isPresent()) {
        Target target = request.target().orElseThrow();
        UUID occupied = targets.get(target.id());
        if (!station.dimension().equals(target.dimension())) reason = Reason.WRONG_DIMENSION;
        else if (station.target().equals(request.target())) reason = Reason.SAME_TARGET;
        else if (occupied != null && !occupied.equals(station.id())) reason = Reason.TARGET_ALREADY_LINKED;
      }
    }
    long revision = station.revision();
    if (reason == Reason.NONE) {
      // Terminal retirement must still release an exhausted station; it never creates a successor revision.
      if (revision < Long.MAX_VALUE) revision++;
      station.target().ifPresent(target -> targets.remove(target.id()));
      if (request.action() == Action.RETIRE) {
        stations.remove(station.id());
      } else {
        stations.put(station.id(), new Station(station.id(), station.owner(), station.dimension(),
            station.position(), revision, request.target()));
        request.target().ifPresent(target -> targets.put(target.id(), station.id()));
      }
    }
    remember(new Receipt(request, reason, revision));
    return result(request, reason, revision);
  }

  private void remember(Receipt receipt) {
    while (receipts.size() >= MAX_RECEIPTS) receipts.remove(receipts.keySet().iterator().next());
    receipts.put(new Operation(receipt.request().actor(), receipt.request().operation()), receipt);
  }

  public Snapshot snapshot() { return new Snapshot(List.copyOf(stations.values()), List.copyOf(receipts.values())); }

  public static DnvRemoteWeaponLinks restore(Snapshot snapshot) {
    DnvRemoteWeaponLinks state = new DnvRemoteWeaponLinks();
    if (snapshot.stations().size() > MAX_STATIONS || snapshot.receipts().size() > MAX_RECEIPTS) {
      throw new IllegalArgumentException("RWS state exceeds capacity");
    }
    for (Station station : snapshot.stations()) {
      if (state.create(station.id(), station.owner(), station.dimension(), station.position()) != Reason.NONE) {
        throw new IllegalArgumentException("duplicate station");
      }
      station.target().ifPresent(target -> {
        if (!station.dimension().equals(target.dimension())
            || state.targets.putIfAbsent(target.id(), station.id()) != null) {
          throw new IllegalArgumentException("conflicting target");
        }
      });
      state.stations.put(station.id(), station);
    }
    for (Receipt receipt : snapshot.receipts()) {
      Operation key = new Operation(receipt.request().actor(), receipt.request().operation());
      if (state.receipts.putIfAbsent(key, receipt) != null) throw new IllegalArgumentException("duplicate operation");
    }
    return state;
  }

  private static Result result(Request request, Reason reason, long revision) {
    return new Result(reason, revision, request.operation(), false);
  }

  private static String key(String value) {
    if (value == null || value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
      throw new IllegalArgumentException("invalid resource key");
    }
    return value;
  }

  public record Target(UUID id, String type, String dimension) {
    public Target { Objects.requireNonNull(id); type = key(type); dimension = key(dimension); }
  }

  public record Station(UUID id, UUID owner, String dimension, long position, long revision, Optional<Target> target) {
    public Station {
      Objects.requireNonNull(id); Objects.requireNonNull(owner); dimension = key(dimension);
      Objects.requireNonNull(target);
      if (revision < 0) throw new IllegalArgumentException("negative revision");
    }
  }

  public record Request(UUID actor, UUID operation, UUID station, long expectedRevision, Action action, Optional<Target> target) {
    public Request {
      Objects.requireNonNull(actor); Objects.requireNonNull(operation); Objects.requireNonNull(station);
      Objects.requireNonNull(action); Objects.requireNonNull(target);
      if (expectedRevision < 0 || ((action == Action.LINK || action == Action.REPLACE) != target.isPresent())) {
        throw new IllegalArgumentException("invalid RWS request");
      }
    }
  }

  public record Result(Reason reason, long revision, UUID operation, boolean replayed) {
    public boolean accepted() { return reason == Reason.NONE; }
  }
  public record Receipt(Request request, Reason reason, long revision) {
    public Receipt {
      Objects.requireNonNull(request); Objects.requireNonNull(reason);
      if (revision < 0) throw new IllegalArgumentException("invalid receipt revision");
    }
  }
  public record Snapshot(List<Station> stations, List<Receipt> receipts) {
    public Snapshot { stations = List.copyOf(stations); receipts = List.copyOf(receipts); }
  }
  private record Operation(UUID actor, UUID id) {}

  public enum Action { LINK, UNLINK, REPLACE, RETIRE }
  public enum Reason {
    NONE, STATION_EXISTS, STATION_MISSING, STATION_CHANGED, STATION_UNLOADED,
    NOT_OWNER, ACTOR_UNAVAILABLE, TOO_FAR, WRONG_DIMENSION, TARGET_UNLOADED,
    TARGET_CHANGED, TARGET_NOT_REMOTE_MANUAL_CAPABLE, TARGET_NOT_OWNED,
    STATION_ALREADY_LINKED, STATION_UNLINKED, TARGET_ALREADY_LINKED, SAME_TARGET,
    STALE_REVISION, OPERATION_CONFLICT, REVISION_EXHAUSTED, CAPACITY, STORAGE_INVALID,
    DRAFT_EXPIRED, DRAFT_MISSING
  }
}

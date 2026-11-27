package com.liucodex.doggiesnewvehicle.mission;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Per-server authority for bounded, non-persistent tactical-map route drafts. */
public final class DnvTacticalRouteDrafts {
  public static final double MIN_WAYPOINT_SPACING = 4.0D;
  public static final double MAX_SEGMENT_LENGTH = 8_192.0D;
  public static final double MAX_TOTAL_LENGTH = 32_768.0D;
  public static final int MAX_DRAFTS_PER_PLAYER = 8;
  public static final int MAX_DRAFTS_PER_CORE = 4;
  public static final int MAX_OPERATION_RECEIPTS_PER_PLAYER = 32;
  public static final long DRAFT_LIFETIME_TICKS = 20L * 60L * 10L;

  private final Map<DraftKey, DraftState> drafts = new LinkedHashMap<>();
  private final Map<PlayerScope, DraftKey> selections = new LinkedHashMap<>();
  private final Map<OperationKey, OperationReceipt> receipts = new LinkedHashMap<>();
  private final Supplier<UUID> draftIds;
  private long latestGameTime;

  public DnvTacticalRouteDrafts() {
    this(UUID::randomUUID);
  }

  DnvTacticalRouteDrafts(Supplier<UUID> draftIds) {
    this.draftIds = Objects.requireNonNull(draftIds, "draftIds");
  }

  /** Selects an existing core/vehicle draft or creates an empty server-identified draft. */
  public BindResult bindVehicle(
      UUID playerId,
      String dimension,
      UUID coreId,
      UUID vehicleId,
      long gameTime) {
    Objects.requireNonNull(playerId, "playerId");
    String checkedDimension = requireDimension(dimension);
    Objects.requireNonNull(coreId, "coreId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    prepare(gameTime);
    Optional<Map.Entry<DraftKey, DraftState>> matching = drafts.entrySet().stream()
        .filter(entry -> entry.getKey().playerId().equals(playerId))
        .filter(entry -> entry.getKey().coreId().equals(coreId))
        .filter(entry -> entry.getValue().binding().dimension().equals(checkedDimension))
        .filter(entry -> entry.getValue().binding().vehicleId().equals(vehicleId))
        .reduce((first, second) -> second);
    if (matching.isPresent()) {
      DraftKey key = matching.orElseThrow().getKey();
      touch(matching.orElseThrow().getValue(), gameTime);
      selections.put(new PlayerScope(playerId, checkedDimension), key);
      return BindResult.accepted(projection(key));
    }
    Issue capacity = capacityIssue(playerId, coreId);
    if (capacity != Issue.NONE) {
      return BindResult.rejected(capacity);
    }
    DraftKey key = new DraftKey(playerId, coreId, nextDraftId(playerId, coreId));
    VehicleBinding binding = new VehicleBinding(checkedDimension, coreId, vehicleId);
    drafts.put(key, new DraftState(
        new Snapshot(0L, checkedDimension, List.of()),
        binding,
        expiresAt(gameTime)));
    selections.put(new PlayerScope(playerId, checkedDimension), key);
    return BindResult.accepted(projection(key));
  }

  /** An owned platform uses its stable ID for both scope and vehicle identity. */
  public BindResult bindOwnedPlatform(
      UUID playerId, String dimension, UUID platformId, long gameTime) {
    return bindVehicle(playerId, dimension, platformId, platformId, gameTime);
  }

  public Optional<DraftProjection> active(
      UUID playerId, String dimension, long gameTime) {
    Objects.requireNonNull(playerId, "playerId");
    String checkedDimension = requireDimension(dimension);
    prepare(gameTime);
    DraftKey key = selections.get(new PlayerScope(playerId, checkedDimension));
    DraftState state = key == null ? null : drafts.get(key);
    if (state == null) {
      return Optional.empty();
    }
    touch(state, gameTime);
    return Optional.of(projection(key));
  }

  public OperationResult select(
      UUID playerId,
      DraftRef reference,
      UUID operationId,
      long expectedRevision,
      String dimension,
      long gameTime) {
    String checkedDimension = requireDimension(dimension);
    CommandFingerprint fingerprint = new CommandFingerprint(
        Operation.SELECT, reference, expectedRevision, checkedDimension, List.of());
    return execute(playerId, operationId, fingerprint, gameTime, () -> {
      DraftKey key = key(playerId, reference);
      DraftState state = drafts.get(key);
      if (state == null || !state.binding().dimension().equals(checkedDimension)) {
        return rejected(operationId, playerId, checkedDimension, Issue.DRAFT_NOT_FOUND);
      }
      if (state.snapshot().revision() != expectedRevision) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(key)), Issue.STALE_REVISION);
      }
      touch(state, gameTime);
      selections.put(new PlayerScope(playerId, checkedDimension), key);
      return OperationResult.accepted(operationId, projection(key));
    });
  }

  /** Creates a separate empty draft in the same immutable core/vehicle context. */
  public OperationResult create(
      UUID playerId,
      DraftRef context,
      UUID operationId,
      long expectedRevision,
      String dimension,
      long gameTime) {
    String checkedDimension = requireDimension(dimension);
    CommandFingerprint fingerprint = new CommandFingerprint(
        Operation.CREATE, context, expectedRevision, checkedDimension, List.of());
    return execute(playerId, operationId, fingerprint, gameTime, () -> {
      DraftKey contextKey = key(playerId, context);
      DraftState current = drafts.get(contextKey);
      if (current == null || !current.binding().dimension().equals(checkedDimension)) {
        return rejected(operationId, playerId, checkedDimension, Issue.DRAFT_NOT_FOUND);
      }
      if (current.snapshot().revision() != expectedRevision) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(contextKey)), Issue.STALE_REVISION);
      }
      Issue capacity = capacityIssue(playerId, context.coreId());
      if (capacity != Issue.NONE) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(contextKey)), capacity);
      }
      DraftKey created = new DraftKey(
          playerId, context.coreId(), nextDraftId(playerId, context.coreId()));
      drafts.put(created, new DraftState(
          new Snapshot(0L, checkedDimension, List.of()),
          current.binding(),
          expiresAt(gameTime)));
      selections.put(new PlayerScope(playerId, checkedDimension), created);
      return OperationResult.accepted(operationId, projection(created));
    });
  }

  /** Cycles only through the caller's bounded drafts in the current dimension. */
  public OperationResult cycle(
      UUID playerId,
      DraftRef context,
      UUID operationId,
      long expectedRevision,
      String dimension,
      long gameTime) {
    String checkedDimension = requireDimension(dimension);
    CommandFingerprint fingerprint = new CommandFingerprint(
        Operation.CYCLE, context, expectedRevision, checkedDimension, List.of());
    return execute(playerId, operationId, fingerprint, gameTime, () -> {
      DraftKey contextKey = key(playerId, context);
      DraftState current = drafts.get(contextKey);
      if (current == null || !current.binding().dimension().equals(checkedDimension)) {
        return rejected(operationId, playerId, checkedDimension, Issue.DRAFT_NOT_FOUND);
      }
      if (current.snapshot().revision() != expectedRevision) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(contextKey)), Issue.STALE_REVISION);
      }
      List<DraftKey> available = keys(playerId, checkedDimension);
      int currentIndex = available.indexOf(contextKey);
      if (currentIndex < 0) {
        return rejected(operationId, playerId, checkedDimension, Issue.DRAFT_NOT_FOUND);
      }
      DraftKey selected = available.get((currentIndex + 1) % available.size());
      touch(drafts.get(selected), gameTime);
      selections.put(new PlayerScope(playerId, checkedDimension), selected);
      return OperationResult.accepted(operationId, projection(selected));
    });
  }

  public OperationResult commit(
      UUID playerId,
      DraftRef reference,
      UUID operationId,
      long expectedRevision,
      String dimension,
      List<DnvRouteWaypoint> waypoints,
      Bounds bounds,
      long gameTime) {
    String checkedDimension = requireDimension(dimension);
    List<DnvRouteWaypoint> copied = List.copyOf(waypoints);
    Objects.requireNonNull(bounds, "bounds");
    CommandFingerprint fingerprint = new CommandFingerprint(
        Operation.COMMIT, reference, expectedRevision, checkedDimension, copied);
    return execute(playerId, operationId, fingerprint, gameTime, () -> {
      DraftKey key = key(playerId, reference);
      DraftState state = drafts.get(key);
      if (state == null || !state.binding().dimension().equals(checkedDimension)) {
        return rejected(operationId, playerId, checkedDimension, Issue.DRAFT_NOT_FOUND);
      }
      if (expectedRevision < 0L || expectedRevision != state.snapshot().revision()) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(key)), Issue.STALE_REVISION);
      }
      Issue validation = validate(copied, bounds);
      if (validation != Issue.NONE) {
        return OperationResult.rejected(
            operationId, Optional.of(projection(key)), validation);
      }
      Snapshot accepted = new Snapshot(
          nextRevision(state.snapshot().revision()), checkedDimension, copied);
      state.snapshot(accepted);
      touch(state, gameTime);
      selections.put(new PlayerScope(playerId, checkedDimension), key);
      return OperationResult.accepted(operationId, projection(key));
    });
  }

  /** Invalidates every runtime draft and receipt owned by one disconnected player. */
  public int invalidate(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    int before = drafts.size();
    drafts.keySet().removeIf(key -> key.playerId().equals(playerId));
    selections.keySet().removeIf(scope -> scope.playerId().equals(playerId));
    receipts.keySet().removeIf(key -> key.playerId().equals(playerId));
    return before - drafts.size();
  }

  /** Explicit core destruction invalidates only that core's runtime drafts. */
  public int invalidateCore(UUID playerId, UUID coreId) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(coreId, "coreId");
    int before = drafts.size();
    drafts.keySet().removeIf(key ->
        key.playerId().equals(playerId) && key.coreId().equals(coreId));
    selections.entrySet().removeIf(entry -> !drafts.containsKey(entry.getValue()));
    receipts.entrySet().removeIf(entry ->
        entry.getKey().playerId().equals(playerId)
            && entry.getValue().fingerprint().reference().coreId().equals(coreId));
    return before - drafts.size();
  }

  public void expire(long gameTime) {
    prepare(gameTime);
  }

  public void clear() {
    drafts.clear();
    selections.clear();
    receipts.clear();
    latestGameTime = 0L;
  }

  int routeCount() {
    return drafts.size();
  }

  int bindingCount() {
    return drafts.size();
  }

  int receiptCount() {
    return receipts.size();
  }

  private OperationResult execute(
      UUID playerId,
      UUID operationId,
      CommandFingerprint fingerprint,
      long gameTime,
      Supplier<OperationResult> action) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(fingerprint, "fingerprint");
    prepare(gameTime);
    OperationKey operationKey = new OperationKey(playerId, operationId);
    OperationReceipt existing = receipts.get(operationKey);
    if (existing != null) {
      if (existing.fingerprint().equals(fingerprint)) {
        return existing.result().replayedCopy();
      }
      return rejected(
          operationId, playerId, fingerprint.dimension(), Issue.OPERATION_MISMATCH);
    }
    OperationResult result = Objects.requireNonNull(action.get(), "operation result");
    trimReceipts(playerId);
    receipts.put(operationKey, new OperationReceipt(
        fingerprint, result, expiresAt(gameTime)));
    return result;
  }

  private OperationResult rejected(
      UUID operationId, UUID playerId, String dimension, Issue issue) {
    return OperationResult.rejected(
        operationId,
        activeWithoutTouch(playerId, dimension),
        issue);
  }

  private Optional<DraftProjection> activeWithoutTouch(
      UUID playerId, String dimension) {
    DraftKey selected = selections.get(new PlayerScope(playerId, dimension));
    return selected == null || !drafts.containsKey(selected)
        ? Optional.empty()
        : Optional.of(projection(selected));
  }

  private void prepare(long gameTime) {
    requireGameTime(gameTime);
    if (gameTime < latestGameTime) {
      clear();
    }
    latestGameTime = gameTime;
    drafts.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAtGameTime());
    receipts.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAtGameTime());
    selections.entrySet().removeIf(entry -> !drafts.containsKey(entry.getValue()));
  }

  private void trimReceipts(UUID playerId) {
    while (receipts.keySet().stream()
        .filter(key -> key.playerId().equals(playerId))
        .count() >= MAX_OPERATION_RECEIPTS_PER_PLAYER) {
      OperationKey oldest = receipts.keySet().stream()
          .filter(key -> key.playerId().equals(playerId))
          .findFirst()
          .orElseThrow();
      receipts.remove(oldest);
    }
  }

  private Issue capacityIssue(UUID playerId, UUID coreId) {
    long playerDrafts = drafts.keySet().stream()
        .filter(key -> key.playerId().equals(playerId))
        .count();
    if (playerDrafts >= MAX_DRAFTS_PER_PLAYER) {
      return Issue.PLAYER_CAPACITY_REACHED;
    }
    long coreDrafts = drafts.keySet().stream()
        .filter(key -> key.playerId().equals(playerId))
        .filter(key -> key.coreId().equals(coreId))
        .count();
    return coreDrafts >= MAX_DRAFTS_PER_CORE
        ? Issue.CORE_CAPACITY_REACHED
        : Issue.NONE;
  }

  private UUID nextDraftId(UUID playerId, UUID coreId) {
    for (int attempt = 0; attempt < 16; attempt++) {
      UUID candidate = Objects.requireNonNull(draftIds.get(), "draft id");
      if (!candidate.equals(new UUID(0L, 0L))
          && !drafts.containsKey(new DraftKey(playerId, coreId, candidate))) {
        return candidate;
      }
    }
    throw new IllegalStateException("unable to allocate a unique tactical draft id");
  }

  private DraftProjection projection(DraftKey key) {
    DraftState state = drafts.get(key);
    if (state == null) {
      throw new IllegalStateException("missing tactical draft state");
    }
    List<DraftKey> keys = keys(key.playerId(), state.binding().dimension());
    int index = keys.indexOf(key);
    if (index < 0) {
      throw new IllegalStateException("draft is outside its player scope");
    }
    return new DraftProjection(
        key.reference(), state.snapshot(), state.binding(), index + 1, keys.size());
  }

  private List<DraftKey> keys(UUID playerId, String dimension) {
    List<DraftKey> keys = new ArrayList<>();
    for (Map.Entry<DraftKey, DraftState> entry : drafts.entrySet()) {
      if (entry.getKey().playerId().equals(playerId)
          && entry.getValue().binding().dimension().equals(dimension)) {
        keys.add(entry.getKey());
      }
    }
    return List.copyOf(keys);
  }

  private static DraftKey key(UUID playerId, DraftRef reference) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(reference, "reference");
    return new DraftKey(playerId, reference.coreId(), reference.draftId());
  }

  private static void touch(DraftState state, long gameTime) {
    state.expiresAtGameTime(expiresAt(gameTime));
  }

  private static long expiresAt(long gameTime) {
    requireGameTime(gameTime);
    return gameTime > Long.MAX_VALUE - DRAFT_LIFETIME_TICKS
        ? Long.MAX_VALUE
        : gameTime + DRAFT_LIFETIME_TICKS;
  }

  static Issue validate(
      List<DnvRouteWaypoint> waypoints, Bounds bounds) {
    DnvAirRouteValidation.GeometryResult geometry =
        DnvAirRouteValidation.checkGeometry(waypoints, bounds);
    if (geometry.accepted()) {
      return Issue.NONE;
    }
    return switch (geometry.reason()) {
      case TOO_MANY_WAYPOINTS -> Issue.TOO_MANY_WAYPOINTS;
      case NON_FINITE_WAYPOINT -> Issue.NON_FINITE_WAYPOINT;
      case OUTSIDE_WORLD_BOUNDS -> Issue.OUTSIDE_WORLD_BOUNDS;
      case ZERO_LENGTH_SEGMENT, WAYPOINTS_TOO_CLOSE -> Issue.WAYPOINTS_TOO_CLOSE;
      case SEGMENT_TOO_LONG -> Issue.SEGMENT_TOO_LONG;
      case ROUTE_TOO_LONG -> Issue.ROUTE_TOO_LONG;
      default -> throw new IllegalStateException(
          "unexpected draft geometry issue: " + geometry.reason());
    };
  }

  private static long nextRevision(long current) {
    if (current == Long.MAX_VALUE) {
      throw new IllegalStateException("tactical route revision exhausted");
    }
    return current + 1L;
  }

  private static String requireDimension(String dimension) {
    String checked = Objects.requireNonNull(dimension, "dimension").trim();
    if (checked.isEmpty() || checked.length() > 256) {
      throw new IllegalArgumentException("dimension must be a bounded namespaced key");
    }
    return checked;
  }

  private static void requireGameTime(long gameTime) {
    if (gameTime < 0L) {
      throw new IllegalArgumentException("draft game time must not be negative");
    }
  }

  private static final class DraftState {
    private Snapshot snapshot;
    private final VehicleBinding binding;
    private long expiresAtGameTime;

    private DraftState(
        Snapshot snapshot, VehicleBinding binding, long expiresAtGameTime) {
      this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
      this.binding = Objects.requireNonNull(binding, "binding");
      this.expiresAtGameTime = expiresAtGameTime;
    }

    private Snapshot snapshot() {
      return snapshot;
    }

    private void snapshot(Snapshot value) {
      snapshot = Objects.requireNonNull(value, "snapshot");
    }

    private VehicleBinding binding() {
      return binding;
    }

    private long expiresAtGameTime() {
      return expiresAtGameTime;
    }

    private void expiresAtGameTime(long value) {
      expiresAtGameTime = value;
    }
  }

  public record Bounds(
      double minX,
      double maxX,
      double minY,
      double maxY,
      double minZ,
      double maxZ) {
    public Bounds {
      if (!Double.isFinite(minX)
          || !Double.isFinite(maxX)
          || !Double.isFinite(minY)
          || !Double.isFinite(maxY)
          || !Double.isFinite(minZ)
          || !Double.isFinite(maxZ)
          || minX > maxX
          || minY > maxY
          || minZ > maxZ) {
        throw new IllegalArgumentException("route bounds must be finite and ordered");
      }
    }

    public boolean contains(DnvRouteWaypoint waypoint) {
      return waypoint.x() >= minX
          && waypoint.x() <= maxX
          && waypoint.y() >= minY
          && waypoint.y() <= maxY
          && waypoint.z() >= minZ
          && waypoint.z() <= maxZ;
    }
  }

  public record Snapshot(
      long revision,
      String dimension,
      List<DnvRouteWaypoint> waypoints) {
    public Snapshot {
      if (revision < 0L) {
        throw new IllegalArgumentException("route revision must not be negative");
      }
      dimension = requireDimension(dimension);
      waypoints = List.copyOf(waypoints);
    }
  }

  public record DraftRef(UUID coreId, UUID draftId) {
    public DraftRef {
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(draftId, "draftId");
      if (draftId.equals(new UUID(0L, 0L))) {
        throw new IllegalArgumentException("draft id must be server allocated");
      }
    }
  }

  public record DraftKey(UUID playerId, UUID coreId, UUID draftId) {
    public DraftKey {
      Objects.requireNonNull(playerId, "playerId");
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(draftId, "draftId");
    }

    public DraftRef reference() {
      return new DraftRef(coreId, draftId);
    }
  }

  public record DraftProjection(
      DraftRef reference,
      Snapshot snapshot,
      VehicleBinding binding,
      int ordinal,
      int totalDrafts) {
    public DraftProjection {
      Objects.requireNonNull(reference, "reference");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(binding, "binding");
      if (ordinal < 1 || totalDrafts < 1 || ordinal > totalDrafts) {
        throw new IllegalArgumentException("invalid draft position");
      }
      if (!reference.coreId().equals(binding.coreId())
          || !snapshot.dimension().equals(binding.dimension())) {
        throw new IllegalArgumentException("draft projection identity mismatch");
      }
    }
  }

  public record OperationResult(
      boolean accepted,
      boolean replayed,
      UUID operationId,
      Optional<DraftProjection> projection,
      Issue issue) {
    public OperationResult {
      Objects.requireNonNull(operationId, "operationId");
      projection = Objects.requireNonNull(projection, "projection");
      Objects.requireNonNull(issue, "issue");
      if (accepted != (issue == Issue.NONE) || accepted && projection.isEmpty()) {
        throw new IllegalArgumentException("inconsistent route operation result");
      }
    }

    private static OperationResult accepted(
        UUID operationId, DraftProjection projection) {
      return new OperationResult(
          true, false, operationId, Optional.of(projection), Issue.NONE);
    }

    private static OperationResult rejected(
        UUID operationId, Optional<DraftProjection> projection, Issue issue) {
      return new OperationResult(false, false, operationId, projection, issue);
    }

    private OperationResult replayedCopy() {
      return new OperationResult(accepted, true, operationId, projection, issue);
    }
  }

  public record BindResult(
      boolean accepted, Optional<DraftProjection> projection, Issue issue) {
    public BindResult {
      projection = Objects.requireNonNull(projection, "projection");
      Objects.requireNonNull(issue, "issue");
      if (accepted != (issue == Issue.NONE) || accepted != projection.isPresent()) {
        throw new IllegalArgumentException("inconsistent route bind result");
      }
    }

    private static BindResult accepted(DraftProjection projection) {
      return new BindResult(true, Optional.of(projection), Issue.NONE);
    }

    private static BindResult rejected(Issue issue) {
      return new BindResult(false, Optional.empty(), issue);
    }
  }

  public record VehicleBinding(String dimension, UUID coreId, UUID vehicleId) {
    public VehicleBinding {
      dimension = requireDimension(dimension);
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(vehicleId, "vehicleId");
    }
  }

  private record PlayerScope(UUID playerId, String dimension) {
    private PlayerScope {
      Objects.requireNonNull(playerId, "playerId");
      dimension = requireDimension(dimension);
    }
  }

  private record OperationKey(UUID playerId, UUID operationId) {}

  private record CommandFingerprint(
      Operation operation,
      DraftRef reference,
      long expectedRevision,
      String dimension,
      List<DnvRouteWaypoint> waypoints) {
    private CommandFingerprint {
      Objects.requireNonNull(operation, "operation");
      Objects.requireNonNull(reference, "reference");
      dimension = requireDimension(dimension);
      waypoints = List.copyOf(waypoints);
    }
  }

  private record OperationReceipt(
      CommandFingerprint fingerprint,
      OperationResult result,
      long expiresAtGameTime) {}

  public enum Operation {
    SELECT,
    CREATE,
    CYCLE,
    COMMIT
  }

  public enum Issue {
    NONE,
    BINDING_REQUIRED,
    DRAFT_NOT_FOUND,
    STALE_REVISION,
    OPERATION_MISMATCH,
    PLAYER_CAPACITY_REACHED,
    CORE_CAPACITY_REACHED,
    TOO_MANY_WAYPOINTS,
    NON_FINITE_WAYPOINT,
    OUTSIDE_WORLD_BOUNDS,
    WAYPOINTS_TOO_CLOSE,
    SEGMENT_TOO_LONG,
    ROUTE_TOO_LONG
  }
}

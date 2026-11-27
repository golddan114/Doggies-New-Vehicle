package com.liucodex.doggiesnewvehicle.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-unit deterministic autonomy owner. Minecraft adapters provide one bounded perception frame;
 * this pure core retains only value snapshots, arbitrates targets and control domains, emits an
 * intent plan, and consumes explicit execution feedback.
 */
public final class DnvAutonomyCore {
  public static final int MAX_TARGET_CANDIDATES = 32;
  public static final int MAX_CONTROL_DOMAINS = 32;
  public static final int TARGET_LOSS_GRACE_TICKS = 40;
  public static final int MIN_TARGET_HOLD_TICKS = 20;
  public static final int BLOCKED_REPOSITION_TICKS = 20;
  public static final double TARGET_SWITCH_ADVANTAGE = 25.0D;
  public static final int MAX_TEXT_LENGTH = 96;

  private static final float APPROACH_THROTTLE = 1.0F;
  private static final float RETREAT_THROTTLE = -0.65F;
  private static final float REPOSITION_THROTTLE = 0.52F;
  private static final float PATROL_THROTTLE = 0.58F;
  private static final float FULL_STEERING_ERROR_DEGREES = 18.0F;
  private static final float TURN_IN_PLACE_ERROR_DEGREES = 32.0F;

  private final double pressureMinRange;
  private final double pressureMaxRange;
  private ActionIntent lastMovementIntent = ActionIntent.idle();
  private MemorySnapshot memory = MemorySnapshot.empty(0L);
  private DecisionCycle lastDecision = DecisionCycle.idle(memory);

  public DnvAutonomyCore(double pressureMinRange, double pressureMaxRange) {
    if (!Double.isFinite(pressureMinRange)
        || !Double.isFinite(pressureMaxRange)
        || pressureMinRange < 0.0D
        || pressureMaxRange <= pressureMinRange) {
      throw new IllegalArgumentException("invalid pressure range");
    }
    this.pressureMinRange = pressureMinRange;
    this.pressureMaxRange = pressureMaxRange;
  }

  /**
   * Runs one complete perception-memory-decision cycle. Candidates and domain states are already
   * bounded by their records, so this method never scans a world or reaches client state.
   */
  public DecisionCycle decide(PerceptionSnapshot perception) {
    Objects.requireNonNull(perception, "perception");
    if (perception.gameTime() < memory.lastGameTime()) {
      return failClosed(perception, DecisionReason.TIME_REGRESSION);
    }

    Selection selection = selectTarget(perception);
    ActionIntent movement = decideMovement(perception, selection);
    Map<String, DomainIntent> stations = decideStations(perception, selection);
    MemorySnapshot nextMemory = advanceMemory(perception, selection, movement.state());
    memory = nextMemory;
    lastMovementIntent = movement;
    lastDecision = new DecisionCycle(
        selection.targetId(), selection.visible(), movement, stations, nextMemory);
    return lastDecision;
  }

  /**
   * Records the observed result of an adapter action. A command is not considered successful until
   * its adapter reports success; blocked and invalid-target results affect the following cycle.
   */
  public boolean acceptFeedback(ExecutionFeedback feedback) {
    Objects.requireNonNull(feedback, "feedback");
    if (feedback.gameTime() < memory.lastGameTime()) {
      return false;
    }
    Optional<UUID> targetId = memory.targetId();
    double x = memory.lastKnownX();
    double y = memory.lastKnownY();
    double z = memory.lastKnownZ();
    double threat = memory.targetThreat();
    long lastSeen = memory.targetLastSeenGameTime();
    long acquired = memory.targetAcquiredGameTime();
    if (feedback.outcome() == ExecutionOutcome.TARGET_INVALID
        && feedback.targetId().isPresent()
        && feedback.targetId().equals(targetId)) {
      targetId = Optional.empty();
      x = 0.0D;
      y = 0.0D;
      z = 0.0D;
      threat = 0.0D;
      lastSeen = 0L;
      acquired = 0L;
    }
    long blockedUntil = memory.blockedUntilGameTime();
    if (feedback.outcome() == ExecutionOutcome.BLOCKED
        && "movement".equals(feedback.domain())) {
      blockedUntil = saturatingAdd(feedback.gameTime(), BLOCKED_REPOSITION_TICKS);
    }
    memory = new MemorySnapshot(
        nextRevision(memory.revision()),
        feedback.gameTime(),
        targetId,
        x,
        y,
        z,
        threat,
        lastSeen,
        acquired,
        memory.state(),
        memory.stateSinceGameTime(),
        Optional.of(feedback),
        blockedUntil);
    return true;
  }

  /**
   * Restores only value memory. The target is deliberately not considered visible after restore;
   * a live perception frame must revalidate it before any station can fire.
   */
  public RestoreResult restore(MemorySnapshot saved, long currentGameTime) {
    Objects.requireNonNull(saved, "saved");
    if (currentGameTime < 0L || saved.lastGameTime() > currentGameTime) {
      memory = MemorySnapshot.empty(Math.max(0L, currentGameTime));
      lastMovementIntent = ActionIntent.stop(
          TacticalState.LOCKED, DecisionReason.TIME_REGRESSION, false, false);
      lastDecision = DecisionCycle.idle(memory);
      return new RestoreResult(false, RestoreReason.TIME_REGRESSION, memory);
    }
    TacticalState restoredState = saved.targetId().isPresent()
        ? TacticalState.SEARCHING
        : TacticalState.IDLE;
    memory = new MemorySnapshot(
        saved.revision(),
        currentGameTime,
        saved.targetId(),
        saved.lastKnownX(),
        saved.lastKnownY(),
        saved.lastKnownZ(),
        saved.targetThreat(),
        saved.targetLastSeenGameTime(),
        saved.targetAcquiredGameTime(),
        restoredState,
        currentGameTime,
        saved.lastFeedback(),
        0L);
    lastMovementIntent = ActionIntent.stop(
        restoredState,
        saved.targetId().isPresent()
            ? DecisionReason.TARGET_MEMORY_GRACE
            : DecisionReason.NO_DESTINATION,
        false,
        false);
    lastDecision = DecisionCycle.idle(memory);
    return new RestoreResult(true, RestoreReason.RESTORED_REVALIDATION_REQUIRED, memory);
  }
  public MemorySnapshot memorySnapshot() {
    return memory;
  }
  public DecisionCycle lastDecision() {
    return lastDecision;
  }
  /** Clears ephemeral authority/target memory on unload, wreck or core removal. */
  public void clear(long currentGameTime) {
    long safeGameTime = Math.max(0L, currentGameTime);
    memory = MemorySnapshot.empty(safeGameTime);
    lastMovementIntent = ActionIntent.idle();
    lastDecision = DecisionCycle.idle(memory);
  }
  /** Existing ground-boss movement seam retained for callers that have no target identity frame. */
  public ActionIntent decideMovement(SensorSnapshot snapshot) {
    ActionIntent intent = evaluateLegacyMovement(snapshot);
    lastMovementIntent = intent;
    return intent;
  }
  /** Shift the pressure band for a weapon's elevation limit without changing target observations. */
  public ActionIntent decideMovement(SensorSnapshot snapshot, double minimumDistance) {
    double shift = Double.isFinite(minimumDistance) ? Math.max(0, minimumDistance - pressureMinRange) : 0;
    return decideMovement(new SensorSnapshot(snapshot.holdPosition(), snapshot.movementLocked(),
        snapshot.targetPresent(), snapshot.destinationPresent(), snapshot.repositionForLine(),
        snapshot.horizontalTargetDistance() - shift, snapshot.currentYaw(), snapshot.destinationYaw()));
  }
  /** Exposed for diagnostics; callers must not use it as persistent gameplay state. */
  public ActionIntent lastMovementIntent() {
    return lastMovementIntent;
  }
  private Selection selectTarget(PerceptionSnapshot perception) {
    List<TargetObservation> live = perception.candidates().stream()
        .filter(candidate -> candidate.loaded() && candidate.valid())
        .sorted(targetOrder())
        .toList();
    Map<UUID, TargetObservation> byId = new LinkedHashMap<>();
    live.forEach(candidate -> byId.put(candidate.targetId(), candidate));

    if (perception.forcedTargetId().isPresent()) {
      TargetObservation forced = byId.get(perception.forcedTargetId().orElseThrow());
      if (forced != null) {
        return Selection.visible(forced);
      }
      return memory.targetId().equals(perception.forcedTargetId())
              && withinLossGrace(perception.gameTime())
          ? Selection.remembered(memory.targetId().orElseThrow())
          : Selection.none();
    }

    TargetObservation current = memory.targetId().map(byId::get).orElse(null);
    TargetObservation best = live.isEmpty() ? null : live.get(0);
    if (current != null) {
      if (best != null
          && !best.targetId().equals(current.targetId())
          && perception.gameTime() - memory.targetAcquiredGameTime()
              >= MIN_TARGET_HOLD_TICKS
          && best.threat() >= current.threat() + TARGET_SWITCH_ADVANTAGE) {
        return Selection.visible(best);
      }
      return Selection.visible(current);
    }
    if (memory.targetId().isPresent() && withinLossGrace(perception.gameTime())) {
      return Selection.remembered(memory.targetId().orElseThrow());
    }
    return best == null ? Selection.none() : Selection.visible(best);
  }

  private boolean withinLossGrace(long gameTime) {
    return gameTime >= memory.targetLastSeenGameTime()
        && gameTime - memory.targetLastSeenGameTime() <= TARGET_LOSS_GRACE_TICKS;
  }
  private ActionIntent decideMovement(PerceptionSnapshot perception, Selection selection) {
    if (perception.movementOwner() != DomainOwner.AI) {
      return ActionIntent.stop(
          TacticalState.LOCKED,
          perception.movementOwner() == DomainOwner.PLAYER
              ? DecisionReason.PLAYER_DOMAIN_OVERRIDE
              : DecisionReason.DOMAIN_NOT_AUTHORIZED,
          false,
          false);
    }
    if (perception.holdPosition()) {
      return ActionIntent.stop(
          TacticalState.HOLDING, DecisionReason.HOLD_ORDER, true, true);
    }
    if (perception.movementLocked()) {
      return ActionIntent.stop(
          TacticalState.LOCKED, DecisionReason.PLATFORM_TRANSITION, false, false);
    }
    if (selection.visible()) {
      TargetObservation target = selection.observation().orElseThrow();
      boolean feedbackBlocked = perception.gameTime() < memory.blockedUntilGameTime();
      boolean reposition = feedbackBlocked || !target.lineOfSight();
      return movementTowardTarget(
          target.horizontalDistance(),
          perception.currentYaw(),
          target.bearingYaw(),
          reposition,
          feedbackBlocked);
    }
    if (selection.targetId().isPresent()) {
      return ActionIntent.stop(
          TacticalState.SEARCHING, DecisionReason.TARGET_MEMORY_GRACE, false, false);
    }
    if (perception.patrolDestination().isPresent()) {
      DestinationObservation destination = perception.patrolDestination().orElseThrow();
      return movement(
          PATROL_THROTTLE,
          TacticalState.PATROLLING,
          DecisionReason.PATROL_DESTINATION,
          perception.currentYaw(),
          destination.bearingYaw());
    }
    return ActionIntent.idle();
  }

  private ActionIntent movementTowardTarget(
      double distance,
      float currentYaw,
      float bearingYaw,
      boolean reposition,
      boolean feedbackBlocked) {
    float throttle;
    TacticalState state;
    DecisionReason reason;
    if (distance > pressureMaxRange) {
      throttle = APPROACH_THROTTLE;
      state = TacticalState.APPROACHING;
      reason = DecisionReason.TARGET_TOO_FAR;
    } else if (distance < pressureMinRange) {
      throttle = RETREAT_THROTTLE;
      state = TacticalState.RETREATING;
      reason = DecisionReason.TARGET_TOO_CLOSE;
    } else if (!reposition) {
      return ActionIntent.stop(
          TacticalState.FIRE_HOLD, DecisionReason.TARGET_IN_PRESSURE_BAND, false, false);
    } else {
      throttle = REPOSITION_THROTTLE;
      state = TacticalState.REPOSITIONING;
      reason = feedbackBlocked
          ? DecisionReason.EXECUTION_BLOCKED
          : DecisionReason.FIRING_LINE_BLOCKED;
    }
    return movement(throttle, state, reason, currentYaw, bearingYaw);
  }

  private Map<String, DomainIntent> decideStations(
      PerceptionSnapshot perception, Selection selection) {
    Map<String, DomainIntent> decisions = new LinkedHashMap<>();
    for (Map.Entry<String, DomainStatus> entry : perception.stations().entrySet()) {
      DomainStatus station = entry.getValue();
      DomainIntent intent;
      if (station.owner() != DomainOwner.AI) {
        intent = new DomainIntent(
            DomainCommand.HOLD,
            station.owner() == DomainOwner.PLAYER
                ? DecisionReason.PLAYER_DOMAIN_OVERRIDE
                : DecisionReason.DOMAIN_NOT_AUTHORIZED);
      } else if (!selection.visible()) {
        intent = new DomainIntent(
            DomainCommand.HOLD,
            selection.targetId().isPresent()
                ? DecisionReason.TARGET_MEMORY_GRACE
                : DecisionReason.NO_TARGET);
      } else if (!station.available()) {
        intent = new DomainIntent(DomainCommand.TRACK, DecisionReason.WEAPON_UNAVAILABLE);
      } else if (!station.lineOfSight()
          || !selection.observation().orElseThrow().lineOfSight()) {
        intent = new DomainIntent(DomainCommand.TRACK, DecisionReason.FIRING_LINE_BLOCKED);
      } else {
        intent = new DomainIntent(DomainCommand.FIRE, DecisionReason.TARGET_AUTHORIZED);
      }
      decisions.put(entry.getKey(), intent);
    }
    return Collections.unmodifiableMap(decisions);
  }

  private MemorySnapshot advanceMemory(
      PerceptionSnapshot perception, Selection selection, TacticalState state) {
    Optional<UUID> targetId = selection.targetId();
    boolean targetChanged = !memory.targetId().equals(targetId);
    double x = targetId.isPresent() ? memory.lastKnownX() : 0.0D;
    double y = targetId.isPresent() ? memory.lastKnownY() : 0.0D;
    double z = targetId.isPresent() ? memory.lastKnownZ() : 0.0D;
    double threat = targetId.isPresent() ? memory.targetThreat() : 0.0D;
    long lastSeen = targetId.isPresent() ? memory.targetLastSeenGameTime() : 0L;
    long acquired = targetId.isEmpty()
        ? 0L
        : !targetChanged
            ? memory.targetAcquiredGameTime()
            : perception.gameTime();
    if (selection.visible()) {
      TargetObservation target = selection.observation().orElseThrow();
      x = target.x();
      y = target.y();
      z = target.z();
      threat = target.threat();
      lastSeen = perception.gameTime();
    }
    long stateSince = memory.state() == state
        ? memory.stateSinceGameTime()
        : perception.gameTime();
    return new MemorySnapshot(
        nextRevision(memory.revision()),
        perception.gameTime(),
        targetId,
        x,
        y,
        z,
        threat,
        lastSeen,
        acquired,
        state,
        stateSince,
        memory.lastFeedback(),
        memory.blockedUntilGameTime());
  }

  private DecisionCycle failClosed(
      PerceptionSnapshot perception, DecisionReason reason) {
    memory = new MemorySnapshot(
        nextRevision(memory.revision()),
        perception.gameTime(),
        Optional.empty(),
        0.0D,
        0.0D,
        0.0D,
        0.0D,
        0L,
        0L,
        TacticalState.LOCKED,
        perception.gameTime(),
        memory.lastFeedback(),
        0L);
    ActionIntent movement = ActionIntent.stop(TacticalState.LOCKED, reason, false, false);
    Map<String, DomainIntent> stations = new LinkedHashMap<>();
    perception.stations().keySet().forEach(
        station -> stations.put(station, new DomainIntent(DomainCommand.HOLD, reason)));
    lastMovementIntent = movement;
    lastDecision = new DecisionCycle(
        Optional.empty(), false, movement, Collections.unmodifiableMap(stations), memory);
    return lastDecision;
  }

  private ActionIntent evaluateLegacyMovement(SensorSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (!snapshot.hasFiniteGeometry()) {
      return ActionIntent.stop(
          TacticalState.LOCKED, DecisionReason.INVALID_SENSOR_GEOMETRY, false, false);
    }
    if (snapshot.holdPosition()) {
      return ActionIntent.stop(
          TacticalState.HOLDING, DecisionReason.HOLD_ORDER, true, true);
    }
    if (snapshot.movementLocked()) {
      return ActionIntent.stop(
          TacticalState.LOCKED, DecisionReason.PLATFORM_TRANSITION, false, false);
    }
    if (snapshot.targetPresent()) {
      return movementTowardTarget(
          snapshot.horizontalTargetDistance(),
          snapshot.currentYaw(),
          snapshot.destinationYaw(),
          snapshot.repositionForLine(),
          false);
    }
    if (snapshot.destinationPresent()) {
      return movement(
          PATROL_THROTTLE,
          TacticalState.PATROLLING,
          DecisionReason.PATROL_DESTINATION,
          snapshot.currentYaw(),
          snapshot.destinationYaw());
    }
    return ActionIntent.idle();
  }

  private static ActionIntent movement(
      float throttle,
      TacticalState state,
      DecisionReason reason,
      float currentYaw,
      float destinationYaw) {
    float yawError = wrapDegrees(destinationYaw - currentYaw);
    float steering = clamp(yawError / FULL_STEERING_ERROR_DEGREES, -1.0F, 1.0F);
    float adjustedThrottle = Math.abs(yawError) > TURN_IN_PLACE_ERROR_DEGREES
        ? 0.0F
        : throttle;
    return new ActionIntent(state, reason, adjustedThrottle, steering, false, false);
  }

  private static Comparator<TargetObservation> targetOrder() {
    return Comparator.comparingDouble(TargetObservation::threat)
        .reversed()
        .thenComparingDouble(TargetObservation::horizontalDistance)
        .thenComparing(candidate -> candidate.targetId().toString());
  }

  private static long nextRevision(long revision) {
    if (revision == Long.MAX_VALUE) {
      throw new IllegalStateException("autonomy revision exhausted");
    }
    return revision + 1L;
  }

  private static long saturatingAdd(long value, int delta) {
    return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
  }

  private static float wrapDegrees(float degrees) {
    float wrapped = degrees % 360.0F;
    if (wrapped >= 180.0F) {
      wrapped -= 360.0F;
    }
    if (wrapped < -180.0F) {
      wrapped += 360.0F;
    }
    return wrapped;
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static String requireDomain(String value) {
    String checked = Objects.requireNonNull(value, "domain").trim();
    if (checked.isEmpty() || checked.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("domain must be bounded text");
    }
    return checked;
  }

  private static String requireReason(String value) {
    String checked = Objects.requireNonNull(value, "reason").trim();
    if (checked.isEmpty() || checked.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("reason must be bounded text");
    }
    return checked;
  }
  public record SensorSnapshot(
      boolean holdPosition,
      boolean movementLocked,
      boolean targetPresent,
      boolean destinationPresent,
      boolean repositionForLine,
      double horizontalTargetDistance,
      float currentYaw,
      float destinationYaw) {
    private boolean hasFiniteGeometry() {
      return Double.isFinite(horizontalTargetDistance)
          && Float.isFinite(currentYaw)
          && Float.isFinite(destinationYaw);
    }
  }
  public record PerceptionSnapshot(
      long gameTime,
      boolean holdPosition,
      boolean movementLocked,
      Optional<UUID> forcedTargetId,
      List<TargetObservation> candidates,
      Optional<DestinationObservation> patrolDestination,
      float currentYaw,
      DomainOwner movementOwner,
      Map<String, DomainStatus> stations) {
    public PerceptionSnapshot {
      if (gameTime < 0L || !Float.isFinite(currentYaw)) {
        throw new IllegalArgumentException("perception time and yaw must be finite and positive");
      }
      forcedTargetId = Objects.requireNonNull(forcedTargetId, "forcedTargetId");
      candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
      if (candidates.size() > MAX_TARGET_CANDIDATES) {
        throw new IllegalArgumentException("target candidate budget exceeded");
      }
      Set<UUID> targetIds = new LinkedHashSet<>();
      if (candidates.stream().anyMatch(Objects::isNull)
          || candidates.stream().anyMatch(candidate -> !targetIds.add(candidate.targetId()))) {
        throw new IllegalArgumentException("target candidates must be non-null and unique");
      }
      patrolDestination = Objects.requireNonNull(patrolDestination, "patrolDestination");
      Objects.requireNonNull(movementOwner, "movementOwner");
      Objects.requireNonNull(stations, "stations");
      if (stations.size() > MAX_CONTROL_DOMAINS) {
        throw new IllegalArgumentException("control domain budget exceeded");
      }
      Map<String, DomainStatus> checkedStations = new LinkedHashMap<>();
      stations.forEach((id, status) ->
          checkedStations.put(requireDomain(id), Objects.requireNonNull(status, "domain status")));
      stations = Collections.unmodifiableMap(checkedStations);
    }
  }
  public record TargetObservation(
      UUID targetId,
      double x,
      double y,
      double z,
      double horizontalDistance,
      float bearingYaw,
      double threat,
      boolean loaded,
      boolean valid,
      boolean lineOfSight) {
    public TargetObservation {
      Objects.requireNonNull(targetId, "targetId");
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || !Double.isFinite(horizontalDistance)
          || horizontalDistance < 0.0D
          || !Float.isFinite(bearingYaw)
          || !Double.isFinite(threat)
          || threat < 0.0D) {
        throw new IllegalArgumentException("target observation must contain finite bounded values");
      }
    }
  }
  public record DestinationObservation(float bearingYaw) {
    public DestinationObservation {
      if (!Float.isFinite(bearingYaw)) {
        throw new IllegalArgumentException("destination bearing must be finite");
      }
    }
  }
  public record DomainStatus(DomainOwner owner, boolean available, boolean lineOfSight) {
    public DomainStatus {
      Objects.requireNonNull(owner, "owner");
    }
  }
  public record DomainIntent(DomainCommand command, DecisionReason reason) {
    public DomainIntent {
      Objects.requireNonNull(command, "command");
      Objects.requireNonNull(reason, "reason");
    }
  }
  public record ActionIntent(
      TacticalState state,
      DecisionReason reason,
      float throttle,
      float steering,
      boolean clearRoute,
      boolean hardStop) {
    public ActionIntent {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(reason, "reason");
      if (!Float.isFinite(throttle)
          || throttle < -1.0F
          || throttle > 1.0F
          || !Float.isFinite(steering)
          || steering < -1.0F
          || steering > 1.0F) {
        throw new IllegalArgumentException("movement intent must be finite and normalized");
      }
    }

    private static ActionIntent idle() {
      return stop(TacticalState.IDLE, DecisionReason.NO_DESTINATION, false, false);
    }

    private static ActionIntent stop(
        TacticalState state, DecisionReason reason, boolean clearRoute, boolean hardStop) {
      return new ActionIntent(state, reason, 0.0F, 0.0F, clearRoute, hardStop);
    }
  }
  public record DecisionCycle(
      Optional<UUID> targetId,
      boolean targetVisible,
      ActionIntent movement,
      Map<String, DomainIntent> stationIntents,
      MemorySnapshot memory) {
    public DecisionCycle {
      targetId = Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(movement, "movement");
      stationIntents = Collections.unmodifiableMap(new LinkedHashMap<>(stationIntents));
      Objects.requireNonNull(memory, "memory");
      if (targetVisible && targetId.isEmpty()) {
        throw new IllegalArgumentException("a visible target requires an identity");
      }
    }

    private static DecisionCycle idle(MemorySnapshot memory) {
      return new DecisionCycle(
          Optional.empty(), false, ActionIntent.idle(), Map.of(), memory);
    }
  }

  public record ExecutionFeedback(
      long gameTime,
      String domain,
      Optional<UUID> targetId,
      ExecutionOutcome outcome,
      String reason) {
    public ExecutionFeedback {
      if (gameTime < 0L) {
        throw new IllegalArgumentException("feedback time must not be negative");
      }
      domain = requireDomain(domain);
      targetId = Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(outcome, "outcome");
      reason = requireReason(reason);
    }
  }

  public record MemorySnapshot(
      long revision,
      long lastGameTime,
      Optional<UUID> targetId,
      double lastKnownX,
      double lastKnownY,
      double lastKnownZ,
      double targetThreat,
      long targetLastSeenGameTime,
      long targetAcquiredGameTime,
      TacticalState state,
      long stateSinceGameTime,
      Optional<ExecutionFeedback> lastFeedback,
      long blockedUntilGameTime) {
    public MemorySnapshot {
      targetId = Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(state, "state");
      lastFeedback = Objects.requireNonNull(lastFeedback, "lastFeedback");
      if (revision < 0L
          || lastGameTime < 0L
          || !Double.isFinite(lastKnownX)
          || !Double.isFinite(lastKnownY)
          || !Double.isFinite(lastKnownZ)
          || !Double.isFinite(targetThreat)
          || targetThreat < 0.0D
          || targetLastSeenGameTime < 0L
          || targetAcquiredGameTime < 0L
          || targetLastSeenGameTime > lastGameTime
          || targetAcquiredGameTime > lastGameTime
          || stateSinceGameTime < 0L
          || stateSinceGameTime > lastGameTime
          || blockedUntilGameTime < 0L) {
        throw new IllegalArgumentException("autonomy memory contains invalid bounded values");
      }
      if (targetId.isEmpty()
          && (targetThreat != 0.0D
              || targetLastSeenGameTime != 0L
              || targetAcquiredGameTime != 0L)) {
        throw new IllegalArgumentException("empty target memory must not retain target metadata");
      }
    }

    private static MemorySnapshot empty(long gameTime) {
      return new MemorySnapshot(
          0L,
          gameTime,
          Optional.empty(),
          0.0D,
          0.0D,
          0.0D,
          0.0D,
          0L,
          0L,
          TacticalState.IDLE,
          gameTime,
          Optional.empty(),
          0L);
    }
  }

  public record RestoreResult(
      boolean accepted, RestoreReason reason, MemorySnapshot memory) {
    public RestoreResult {
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(memory, "memory");
    }
  }

  private record Selection(
      Optional<UUID> targetId,
      boolean visible,
      Optional<TargetObservation> observation) {
    private static Selection visible(TargetObservation target) {
      return new Selection(Optional.of(target.targetId()), true, Optional.of(target));
    }

    private static Selection remembered(UUID targetId) {
      return new Selection(Optional.of(targetId), false, Optional.empty());
    }

    private static Selection none() {
      return new Selection(Optional.empty(), false, Optional.empty());
    }
  }

  public enum TacticalState {
    IDLE,
    LOCKED,
    HOLDING,
    SEARCHING,
    PATROLLING,
    APPROACHING,
    RETREATING,
    FIRE_HOLD,
    REPOSITIONING
  }

  public enum DecisionReason {
    NO_DESTINATION,
    NO_TARGET,
    INVALID_SENSOR_GEOMETRY,
    TIME_REGRESSION,
    HOLD_ORDER,
    PLATFORM_TRANSITION,
    PLAYER_DOMAIN_OVERRIDE,
    DOMAIN_NOT_AUTHORIZED,
    PATROL_DESTINATION,
    TARGET_MEMORY_GRACE,
    TARGET_TOO_FAR,
    TARGET_TOO_CLOSE,
    TARGET_IN_PRESSURE_BAND,
    TARGET_AUTHORIZED,
    FIRING_LINE_BLOCKED,
    EXECUTION_BLOCKED,
    WEAPON_UNAVAILABLE
  }

  public enum DomainOwner {
    AI,
    PLAYER,
    REMOTE_MANUAL_FIRE,
    NONE
  }

  public enum DomainCommand {
    HOLD,
    TRACK,
    FIRE
  }

  public enum ExecutionOutcome {
    SUCCEEDED,
    BLOCKED,
    UNAVAILABLE,
    TARGET_INVALID,
    OUT_OF_AMMO,
    AUTHORITY_DENIED,
    COOLDOWN, REQUESTED
  }

  public enum RestoreReason {
    RESTORED_REVALIDATION_REQUIRED,
    TIME_REGRESSION
  }
}

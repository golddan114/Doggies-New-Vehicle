package com.liucodex.doggiesnewvehicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic per-station target owner fed by one bounded vehicle perception frame. The core
 * stores only UUID/value memory; Minecraft entity lookup, line traces, aiming and firing stay in
 * server adapters and must be revalidated immediately before side effects.
 */
public final class DnvStationEngagementCore {
  public static final int MAX_CANDIDATES = 32;
  public static final int MAX_STATIONS = 32;
  public static final int MAX_YAW_ARCS = 4;
  public static final int MIN_DECISION_INTERVAL_TICKS = 10;
  public static final int MAX_POLICY_TICKS = 1_200;
  public static final int MAX_TEXT_LENGTH = 96;

  private final Map<String, StationMemory> memories = new LinkedHashMap<>();
  private Decision lastDecision = Decision.empty();
  private SafetyKey lastSafetyKey;
  private long lastGameTime = -1L;
  private long nextDecisionGameTime;

  public Decision decide(Request request) {
    Objects.requireNonNull(request, "request");
    if (request.gameTime() < lastGameTime) {
      return failClosed(request, Reason.TIME_REGRESSION);
    }
    SafetyKey safetyKey = SafetyKey.from(request);
    if (lastSafetyKey != null
        && lastSafetyKey.equals(safetyKey)
        && request.gameTime() < nextDecisionGameTime) {
      lastGameTime = request.gameTime();
      lastDecision = lastDecision.asCached();
      return lastDecision;
    }

    Map<UUID, TargetSnapshot> candidates = new LinkedHashMap<>();
    request.candidates().forEach(candidate -> candidates.put(candidate.targetId(), candidate));
    Map<UUID, Integer> allocations = new HashMap<>();
    Map<String, UUID> retained = reserveRetained(request, candidates, allocations);
    Map<String, StationIntent> intents = new LinkedHashMap<>();
    request.stations().forEach((stationId, station) -> {
      StationIntent intent = decideStation(request, stationId, station, candidates, allocations);
      intents.put(stationId, intent);
      UUID prior = retained.get(stationId);
      if (prior != null) adjustAllocation(allocations, prior, -1);
      if (intent.command() != DnvAutonomyCore.DomainCommand.HOLD) {
        intent.targetId().ifPresent(id -> adjustAllocation(allocations, id, 1));
      }
    });
    memories.keySet().retainAll(request.stations().keySet());

    lastGameTime = request.gameTime();
    lastSafetyKey = safetyKey;
    nextDecisionGameTime = nextDecisionTime(request.gameTime(), request.scheduleKey());
    lastDecision = new Decision(
        request.gameTime(), request.candidates().size(), false,
        Collections.unmodifiableMap(intents));
    return lastDecision;
  }

  public void clear(long gameTime) {
    memories.clear();
    lastDecision = Decision.empty();
    lastSafetyKey = null;
    lastGameTime = Math.max(0L, gameTime);
    nextDecisionGameTime = 0L;
  }

  private StationIntent decideStation(
      Request request,
      String stationId,
      StationSnapshot station,
      Map<UUID, TargetSnapshot> candidates,
      Map<UUID, Integer> allocations) {
    if (station.owner() == DnvAutonomyCore.DomainOwner.PLAYER) {
      memories.remove(stationId);
      return StationIntent.hold(Reason.PLAYER_DOMAIN_OVERRIDE);
    }
    if (station.owner() != DnvAutonomyCore.DomainOwner.AI) {
      memories.remove(stationId);
      return StationIntent.hold(Reason.DOMAIN_NOT_AUTHORIZED);
    }
    if (!station.available()) {
      return StationIntent.hold(Reason.STATION_UNAVAILABLE);
    }
    if (!station.readiness().authorityValid()) {
      return StationIntent.hold(Reason.AUTHORITY_DENIED);
    }

    Optional<UUID> designatedId = request.designatedTargetId();
    if (designatedId.isPresent()) {
      TargetSnapshot designated = candidates.get(designatedId.orElseThrow());
      if (designated != null && designated.kind().pvpProtected()) {
        memories.remove(stationId);
        return StationIntent.hold(Reason.PVP_TARGET_DENIED);
      }
    }

    List<EvaluatedTarget> eligible = evaluateTargets(request, station, candidates.values());
    EvaluatedTarget designated = designatedId.map(id -> eligible.stream()
        .filter(candidate -> id.equals(candidate.target().targetId()))
        .findFirst().orElse(null)).orElse(null);
    if (station.policy().targetingMode() == TargetingMode.DESIGNATED_ONLY
        && designated == null) {
      memories.remove(stationId);
      return StationIntent.hold(designatedFailureReason(
          request, station, candidates, designatedId));
    }

    EvaluatedTarget selected = selectTarget(
        request, stationId, station.policy(), eligible, designated, allocations);
    if (selected == null) {
      StationMemory memory = memories.get(stationId);
      if (memory != null
          && request.gameTime() >= memory.lastSeenGameTime()
          && request.gameTime() - memory.lastSeenGameTime()
              <= station.policy().lossGraceTicks()) {
        return new StationIntent(
            Optional.of(memory.targetId()),
            DnvAutonomyCore.DomainCommand.HOLD,
            Reason.TARGET_MEMORY_GRACE,
            memory.localYaw(),
            memory.localPitch(),
            false);
      }
      memories.remove(stationId);
      return StationIntent.hold(Reason.NO_LEGAL_TARGET);
    }

    remember(request.gameTime(), stationId, selected);
    if (!station.lineOfSight().getOrDefault(
        selected.target().targetId(), selected.target().lineOfSight())) {
      return selected.intent(DnvAutonomyCore.DomainCommand.TRACK,
          Reason.FIRING_LINE_BLOCKED, false);
    }
    if (!station.readiness().weaponAvailable()) {
      return selected.intent(DnvAutonomyCore.DomainCommand.TRACK, Reason.WEAPON_UNAVAILABLE, false);
    }
    if (!station.readiness().ammoAvailable()) {
      return selected.intent(DnvAutonomyCore.DomainCommand.TRACK, Reason.OUT_OF_AMMO, false);
    }
    if (!station.readiness().baseReady()) {
      return selected.intent(DnvAutonomyCore.DomainCommand.TRACK, Reason.WEAPON_NOT_READY, false);
    }
    if (!station.readiness().cooldownReady()) {
      return selected.intent(DnvAutonomyCore.DomainCommand.TRACK, Reason.COOLDOWN, false);
    }
    return selected.intent(
        DnvAutonomyCore.DomainCommand.FIRE, Reason.TARGET_AUTHORIZED, false);
  }

  private List<EvaluatedTarget> evaluateTargets(
      Request request,
      StationSnapshot station,
      Iterable<TargetSnapshot> candidates) {
    List<EvaluatedTarget> eligible = new ArrayList<>();
    for (TargetSnapshot candidate : candidates) {
      boolean designated = request.designatedTargetId()
          .filter(candidate.targetId()::equals)
          .isPresent();
      if (candidate.kind().pvpProtected()
          || !candidate.loaded()
          || !candidate.valid()
          || (!candidate.ambientEligible() && !designated)
          || !station.policy().allowedTargetKinds().contains(candidate.kind())) {
        continue;
      }
      RelativeTarget relative = relativeTarget(request, station.geometry(), candidate);
      if (relative.distance() < station.policy().minimumRange()
          || relative.distance() > station.policy().maximumRange()
          || !station.geometry().contains(relative.localYaw(), relative.localPitch())) {
        continue;
      }
      double score = candidate.threat() - relative.distance() * 0.01D;
      if (designated && station.policy().targetingMode() != TargetingMode.INDEPENDENT) {
        score += 1_000_000.0D;
      }
      eligible.add(new EvaluatedTarget(candidate, relative, score));
    }
    eligible.sort(Comparator.comparingDouble(EvaluatedTarget::score)
        .reversed()
        .thenComparing(candidate -> candidate.target().targetId().toString()));
    return eligible;
  }

  private EvaluatedTarget selectTarget(
      Request request,
      String stationId,
      StationEngagementPolicy policy,
      List<EvaluatedTarget> eligible,
      EvaluatedTarget designated,
      Map<UUID, Integer> allocations) {
    if (designated != null && policy.targetingMode() != TargetingMode.INDEPENDENT) {
      return designated;
    }
    if (eligible.isEmpty()) {
      return null;
    }
    StationMemory memory = memories.get(stationId);
    if (memory == null) {
      return leastAllocated(eligible, allocations);
    }
    EvaluatedTarget current = eligible.stream()
        .filter(candidate -> memory.targetId().equals(candidate.target().targetId()))
        .findFirst()
        .orElse(null);
    if (current == null) {
      return leastAllocated(eligible, allocations);
    }
    EvaluatedTarget best = eligible.get(0);
    long heldTicks = request.gameTime() - memory.acquiredGameTime();
    if (heldTicks < policy.minimumHoldTicks()
        || best.target().targetId().equals(current.target().targetId())
        || best.score() < current.score() + policy.switchAdvantage()
        || allocations.getOrDefault(best.target().targetId(), 0)
            > allocations.getOrDefault(current.target().targetId(), 0) - 1) {
      return current;
    }
    return best;
  }

  private Map<String, UUID> reserveRetained(
      Request request,
      Map<UUID, TargetSnapshot> candidates,
      Map<UUID, Integer> allocations) {
    Map<String, UUID> retained = new HashMap<>();
    request.stations().forEach((stationId, station) -> {
      StationMemory memory = memories.get(stationId);
      if (memory == null || station.owner() != DnvAutonomyCore.DomainOwner.AI
          || !station.available() || !station.readiness().authorityValid()
          || station.policy().targetingMode() == TargetingMode.DESIGNATED_ONLY
              && request.designatedTargetId().filter(memory.targetId()::equals).isEmpty()
          || request.designatedTargetId().isPresent()
              && station.policy().targetingMode() != TargetingMode.INDEPENDENT
              && !request.designatedTargetId().orElseThrow().equals(memory.targetId())) return;
      TargetSnapshot target = candidates.get(memory.targetId());
      if (target == null || evaluateTargets(request, station, List.of(target)).isEmpty()) return;
      retained.put(stationId, memory.targetId());
      adjustAllocation(allocations, memory.targetId(), 1);
    });
    return retained;
  }

  private static EvaluatedTarget leastAllocated(
      List<EvaluatedTarget> eligible, Map<UUID, Integer> allocations) {
    EvaluatedTarget selected = eligible.get(0);
    int fewest = allocations.getOrDefault(selected.target().targetId(), 0);
    for (int index = 1; index < eligible.size(); index++) {
      EvaluatedTarget candidate = eligible.get(index);
      int count = allocations.getOrDefault(candidate.target().targetId(), 0);
      if (count < fewest) {
        selected = candidate;
        fewest = count;
      }
    }
    return selected;
  }

  private static void adjustAllocation(Map<UUID, Integer> allocations, UUID id, int delta) {
    int next = allocations.getOrDefault(id, 0) + delta;
    if (next <= 0) allocations.remove(id);
    else allocations.put(id, next);
  }

  private void remember(long gameTime, String stationId, EvaluatedTarget target) {
    StationMemory previous = memories.get(stationId);
    long acquired = previous != null && previous.targetId().equals(target.target().targetId())
        ? previous.acquiredGameTime()
        : gameTime;
    memories.put(stationId, new StationMemory(
        target.target().targetId(), acquired, gameTime,
        target.relative().localYaw(), target.relative().localPitch()));
  }

  private Reason designatedFailureReason(
      Request request,
      StationSnapshot station,
      Map<UUID, TargetSnapshot> candidates,
      Optional<UUID> designatedId) {
    if (designatedId.isEmpty()) {
      return Reason.DESIGNATED_TARGET_REQUIRED;
    }
    TargetSnapshot designated = candidates.get(designatedId.orElseThrow());
    if (designated == null || !designated.loaded() || !designated.valid()) {
      return Reason.DESIGNATED_TARGET_UNAVAILABLE;
    }
    if (designated.kind().pvpProtected()) {
      return Reason.PVP_TARGET_DENIED;
    }
    RelativeTarget relative = relativeTarget(request, station.geometry(), designated);
    return station.geometry().contains(relative.localYaw(), relative.localPitch())
        ? Reason.DESIGNATED_TARGET_POLICY_DENIED
        : Reason.DESIGNATED_TARGET_OUTSIDE_ENVELOPE;
  }

  private Decision failClosed(Request request, Reason reason) {
    memories.clear();
    Map<String, StationIntent> intents = new LinkedHashMap<>();
    request.stations().keySet().forEach(id -> intents.put(id, StationIntent.hold(reason)));
    lastGameTime = request.gameTime();
    lastSafetyKey = null;
    nextDecisionGameTime = Long.MAX_VALUE;
    lastDecision = new Decision(
        request.gameTime(), request.candidates().size(), false,
        Collections.unmodifiableMap(intents));
    return lastDecision;
  }

  private static RelativeTarget relativeTarget(
      Request request,
      StationGeometryProfile geometry,
      TargetSnapshot target) {
    return relativeTarget(request.vehicleX(), request.vehicleY(), request.vehicleZ(),
        request.vehicleYaw(), geometry, target.x(), target.y(), target.z());
  }

  /** Cheap current-pose guard for a cached intent at the actual station execution site. */
  public static boolean withinExecutionEnvelope(
      double vehicleX, double vehicleY, double vehicleZ, float vehicleYaw,
      StationGeometryProfile geometry, StationEngagementPolicy policy,
      double targetX, double targetY, double targetZ) {
    Objects.requireNonNull(geometry, "geometry");
    Objects.requireNonNull(policy, "policy");
    if (!Double.isFinite(vehicleX) || !Double.isFinite(vehicleY) || !Double.isFinite(vehicleZ)
        || !Float.isFinite(vehicleYaw) || !Double.isFinite(targetX)
        || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) return false;
    RelativeTarget relative = relativeTarget(vehicleX, vehicleY, vehicleZ, vehicleYaw,
        geometry, targetX, targetY, targetZ);
    return relative.distance() >= policy.minimumRange()
        && relative.distance() <= policy.maximumRange()
        && geometry.contains(relative.localYaw(), relative.localPitch());
  }

  private static RelativeTarget relativeTarget(
      double vehicleX, double vehicleY, double vehicleZ, float vehicleYaw,
      StationGeometryProfile geometry, double targetX, double targetY, double targetZ) {
    double yawRadians = Math.toRadians(vehicleYaw);
    double cosine = Math.cos(yawRadians);
    double sine = Math.sin(yawRadians);
    double mountX = vehicleX
        + geometry.mountX() * cosine - geometry.mountZ() * sine;
    double mountY = vehicleY + geometry.mountY();
    double mountZ = vehicleZ
        + geometry.mountX() * sine + geometry.mountZ() * cosine;
    double x = targetX - mountX;
    double y = targetY - mountY;
    double z = targetZ - mountZ;
    double horizontal = Math.sqrt(x * x + z * z);
    double distance = Math.sqrt(horizontal * horizontal + y * y);
    float bearing = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
    float localYaw = wrapDegrees(bearing - vehicleYaw - geometry.baseYaw());
    float localPitch = (float) Math.toDegrees(Math.atan2(y, Math.max(1.0E-9D, horizontal)));
    return new RelativeTarget(distance, localYaw, localPitch);
  }

  private static long nextDecisionTime(long gameTime, int scheduleKey) {
    int spread = Math.floorMod(scheduleKey * 31 + 17, 5);
    long delta = MIN_DECISION_INTERVAL_TICKS + spread;
    return gameTime > Long.MAX_VALUE - delta ? Long.MAX_VALUE : gameTime + delta;
  }

  private static float wrapDegrees(float value) {
    float wrapped = value % 360.0F;
    if (wrapped >= 180.0F) {
      wrapped -= 360.0F;
    }
    if (wrapped < -180.0F) {
      wrapped += 360.0F;
    }
    return wrapped;
  }

  private static String requireId(String value, String label) {
    String checked = Objects.requireNonNull(value, label).trim();
    if (checked.isEmpty() || checked.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(label + " must be bounded text");
    }
    return checked;
  }

  public record Request(
      long gameTime,
      int scheduleKey,
      double vehicleX,
      double vehicleY,
      double vehicleZ,
      float vehicleYaw,
      Optional<UUID> designatedTargetId,
      List<TargetSnapshot> candidates,
      Map<String, StationSnapshot> stations) {
    public Request {
      if (gameTime < 0L
          || !Double.isFinite(vehicleX)
          || !Double.isFinite(vehicleY)
          || !Double.isFinite(vehicleZ)
          || !Float.isFinite(vehicleYaw)) {
        throw new IllegalArgumentException("request pose and time must be finite");
      }
      designatedTargetId = Objects.requireNonNull(designatedTargetId, "designatedTargetId");
      candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
      if (candidates.size() > MAX_CANDIDATES) {
        throw new IllegalArgumentException("candidate budget exceeded");
      }
      Set<UUID> identities = new LinkedHashSet<>();
      if (candidates.stream().anyMatch(Objects::isNull)
          || candidates.stream().anyMatch(candidate -> !identities.add(candidate.targetId()))) {
        throw new IllegalArgumentException("candidates must be non-null and unique");
      }
      Objects.requireNonNull(stations, "stations");
      if (stations.size() > MAX_STATIONS) {
        throw new IllegalArgumentException("station budget exceeded");
      }
      Map<String, StationSnapshot> checked = new LinkedHashMap<>();
      stations.forEach((id, station) -> checked.put(
          requireId(id, "stationId"), Objects.requireNonNull(station, "station")));
      stations = Collections.unmodifiableMap(checked);
    }
  }

  public record TargetSnapshot(
      UUID targetId,
      TargetKind kind,
      double x,
      double y,
      double z,
      double threat,
      boolean loaded,
      boolean valid,
      boolean lineOfSight,
      boolean ambientEligible) {
    public TargetSnapshot {
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(kind, "kind");
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || !Double.isFinite(threat)
          || threat < 0.0D) {
        throw new IllegalArgumentException("target values must be finite");
      }
    }
  }

  public record StationSnapshot(
      DnvAutonomyCore.DomainOwner owner,
      boolean available,
      StationGeometryProfile geometry,
      StationEngagementPolicy policy,
      WeaponReadiness readiness,
      Map<UUID, Boolean> lineOfSight) {
    public StationSnapshot {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(geometry, "geometry");
      Objects.requireNonNull(policy, "policy");
      Objects.requireNonNull(readiness, "readiness");
      lineOfSight = Map.copyOf(Objects.requireNonNull(lineOfSight, "lineOfSight"));
      if (lineOfSight.size() > MAX_CANDIDATES) {
        throw new IllegalArgumentException("station observation budget exceeded");
      }
    }

    public StationSnapshot(
        DnvAutonomyCore.DomainOwner owner, boolean available, StationGeometryProfile geometry,
        StationEngagementPolicy policy, WeaponReadiness readiness) {
      this(owner, available, geometry, policy, readiness, Map.of());
    }
  }

  public record StationGeometryProfile(
      String profileId,
      long revision,
      double mountX,
      double mountY,
      double mountZ,
      float baseYaw,
      List<YawArc> allowedYawArcs,
      PitchArc pitchArc,
      List<YawArc> forbiddenYawArcs,
      boolean allowReposition) {
    public StationGeometryProfile {
      profileId = requireId(profileId, "profileId");
      if (revision < 0L
          || !Double.isFinite(mountX)
          || !Double.isFinite(mountY)
          || !Double.isFinite(mountZ)
          || !Float.isFinite(baseYaw)) {
        throw new IllegalArgumentException("geometry values must be finite");
      }
      allowedYawArcs = List.copyOf(Objects.requireNonNull(allowedYawArcs, "allowedYawArcs"));
      forbiddenYawArcs = List.copyOf(Objects.requireNonNull(
          forbiddenYawArcs, "forbiddenYawArcs"));
      Objects.requireNonNull(pitchArc, "pitchArc");
      if (allowedYawArcs.isEmpty()
          || allowedYawArcs.size() > MAX_YAW_ARCS
          || forbiddenYawArcs.size() > MAX_YAW_ARCS
          || allowedYawArcs.stream().anyMatch(Objects::isNull)
          || forbiddenYawArcs.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("geometry yaw arc budget is invalid");
      }
      baseYaw = wrapDegrees(baseYaw);
    }

    public boolean contains(float localYaw, float localPitch) {
      if (!Float.isFinite(localYaw) || !Float.isFinite(localPitch)) {
        return false;
      }
      float normalizedYaw = wrapDegrees(localYaw);
      return pitchArc.contains(localPitch)
          && allowedYawArcs.stream().anyMatch(arc -> arc.contains(normalizedYaw))
          && forbiddenYawArcs.stream().noneMatch(arc -> arc.contains(normalizedYaw));
    }

    public boolean containsDirection(double x, double y, double z, float vehicleYaw) {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
          || x * x + y * y + z * z < 1.0E-12D) return false;
      float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F - vehicleYaw - baseYaw;
      float pitch = (float) Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
      return contains(yaw, pitch);
    }
  }

  public record YawArc(float minimum, float maximum) {
    public YawArc {
      if (!Float.isFinite(minimum)
          || !Float.isFinite(maximum)
          || minimum < -180.0F
          || minimum > 180.0F
          || maximum < -180.0F
          || maximum > 180.0F) {
        throw new IllegalArgumentException("yaw arc must be finite and normalized");
      }
    }

    public boolean contains(float yaw) {
      if (!Float.isFinite(yaw)) {
        return false;
      }
      if (minimum == -180.0F && maximum == 180.0F) {
        return true;
      }
      float value = wrapDegrees(yaw);
      float normalizedMinimum = wrapDegrees(minimum);
      float normalizedMaximum = wrapDegrees(maximum);
      return normalizedMinimum <= normalizedMaximum
          ? value >= normalizedMinimum && value <= normalizedMaximum
          : value >= normalizedMinimum || value <= normalizedMaximum;
    }
  }

  public record PitchArc(float minimum, float maximum) {
    public PitchArc {
      if (!Float.isFinite(minimum)
          || !Float.isFinite(maximum)
          || minimum < -90.0F
          || maximum > 90.0F
          || maximum < minimum) {
        throw new IllegalArgumentException("pitch arc is invalid");
      }
    }

    public boolean contains(float pitch) {
      return Float.isFinite(pitch) && pitch >= minimum && pitch <= maximum;
    }
  }

  public record StationEngagementPolicy(
      TargetingMode targetingMode,
      Set<TargetKind> allowedTargetKinds,
      double minimumRange,
      double maximumRange,
      double switchAdvantage,
      int minimumHoldTicks,
      int lossGraceTicks) {
    public StationEngagementPolicy {
      Objects.requireNonNull(targetingMode, "targetingMode");
      allowedTargetKinds = Set.copyOf(Objects.requireNonNull(
          allowedTargetKinds, "allowedTargetKinds"));
      if (allowedTargetKinds.isEmpty()
          || allowedTargetKinds.stream().anyMatch(Objects::isNull)
          || !Double.isFinite(minimumRange)
          || !Double.isFinite(maximumRange)
          || minimumRange < 0.0D
          || maximumRange <= minimumRange
          || !Double.isFinite(switchAdvantage)
          || switchAdvantage < 0.0D
          || minimumHoldTicks < 0
          || minimumHoldTicks > MAX_POLICY_TICKS
          || lossGraceTicks < 0
          || lossGraceTicks > MAX_POLICY_TICKS) {
        throw new IllegalArgumentException("station policy contains invalid bounds");
      }
    }
  }

  public record WeaponReadiness(
      boolean ammoAvailable, boolean cooldownReady, boolean authorityValid,
      boolean weaponAvailable, boolean baseReady) {
    public WeaponReadiness(boolean ammoAvailable, boolean cooldownReady, boolean authorityValid) {
      this(ammoAvailable, cooldownReady, authorityValid, true, true);
    }

    public static WeaponReadiness ready() {
      return new WeaponReadiness(true, true, true);
    }
  }

  public record StationIntent(
      Optional<UUID> targetId,
      DnvAutonomyCore.DomainCommand command,
      Reason reason,
      float localYaw,
      float localPitch,
      boolean requestReposition) {
    public StationIntent {
      targetId = Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(command, "command");
      Objects.requireNonNull(reason, "reason");
      if (!Float.isFinite(localYaw) || !Float.isFinite(localPitch)) {
        throw new IllegalArgumentException("station intent angles must be finite");
      }
      if (command == DnvAutonomyCore.DomainCommand.FIRE && targetId.isEmpty()) {
        throw new IllegalArgumentException("fire intent requires a target");
      }
    }

    private static StationIntent hold(Reason reason) {
      return new StationIntent(
          Optional.empty(), DnvAutonomyCore.DomainCommand.HOLD, reason,
          0.0F, 0.0F, false);
    }
  }

  public record Decision(
      long evaluatedGameTime,
      int candidateCount,
      boolean cached,
      Map<String, StationIntent> stationIntents) {
    public Decision {
      if (evaluatedGameTime < 0L
          || candidateCount < 0
          || candidateCount > MAX_CANDIDATES) {
        throw new IllegalArgumentException("decision counters are invalid");
      }
      stationIntents = Collections.unmodifiableMap(new LinkedHashMap<>(
          Objects.requireNonNull(stationIntents, "stationIntents")));
    }

    private static Decision empty() {
      return new Decision(0L, 0, false, Map.of());
    }

    private Decision asCached() {
      return new Decision(evaluatedGameTime, candidateCount, true, stationIntents);
    }
  }

  private record EvaluatedTarget(
      TargetSnapshot target, RelativeTarget relative, double score) {
    private StationIntent intent(
        DnvAutonomyCore.DomainCommand command, Reason reason, boolean requestReposition) {
      return new StationIntent(
          Optional.of(target.targetId()), command, reason,
          relative.localYaw(), relative.localPitch(), requestReposition);
    }
  }

  private record RelativeTarget(double distance, float localYaw, float localPitch) {}

  private record StationMemory(
      UUID targetId,
      long acquiredGameTime,
      long lastSeenGameTime,
      float localYaw,
      float localPitch) {}

  private record SafetyKey(
      Optional<UUID> designated, Map<String, StationSafety> stations, Map<UUID, TargetSafety> targets) {
    private static SafetyKey from(Request request) {
      Map<String, StationSafety> safety = new LinkedHashMap<>();
      request.stations().forEach((id, station) -> safety.put(id, new StationSafety(
          station.owner(), station.available(), station.geometry(),
          station.policy(), station.readiness(), station.lineOfSight())));
      Map<UUID, TargetSafety> targets = new LinkedHashMap<>();
      request.candidates().forEach(target -> targets.put(target.targetId(), new TargetSafety(
          target.kind(), target.loaded(), target.valid(), target.ambientEligible(), target.lineOfSight())));
      return new SafetyKey(request.designatedTargetId(), Collections.unmodifiableMap(safety), Map.copyOf(targets));
    }
  }

  private record TargetSafety(
      TargetKind kind, boolean loaded, boolean valid, boolean ambientEligible, boolean lineOfSight) {}

  private record StationSafety(
      DnvAutonomyCore.DomainOwner owner,
      boolean available,
      StationGeometryProfile geometry,
      StationEngagementPolicy policy,
      WeaponReadiness readiness,
      Map<UUID, Boolean> lineOfSight) {}

  public enum TargetKind {
    LIVING(false),
    VEHICLE(false),
    PROJECTILE(false),
    PLAYER(true),
    PLAYER_OCCUPIED_VEHICLE(true),
    UNKNOWN(false);

    private final boolean pvpProtected;

    TargetKind(boolean pvpProtected) {
      this.pvpProtected = pvpProtected;
    }

    public boolean pvpProtected() {
      return pvpProtected;
    }
  }

  public enum TargetingMode {
    SHARED_PREFERRED,
    INDEPENDENT,
    DESIGNATED_ONLY,
    SELF_DEFENSE,
    SECTOR_DEFENSE
  }

  public enum Reason {
    TARGET_AUTHORIZED,
    NO_LEGAL_TARGET,
    TARGET_MEMORY_GRACE,
    PLAYER_DOMAIN_OVERRIDE,
    DOMAIN_NOT_AUTHORIZED,
    STATION_UNAVAILABLE,
    AUTHORITY_DENIED,
    OUT_OF_AMMO,
    WEAPON_UNAVAILABLE,
    WEAPON_NOT_READY,
    COOLDOWN,
    FIRING_LINE_BLOCKED,
    DESIGNATED_TARGET_REQUIRED,
    DESIGNATED_TARGET_UNAVAILABLE,
    DESIGNATED_TARGET_OUTSIDE_ENVELOPE,
    DESIGNATED_TARGET_POLICY_DENIED,
    PVP_TARGET_DENIED,
    TIME_REGRESSION
  }
}

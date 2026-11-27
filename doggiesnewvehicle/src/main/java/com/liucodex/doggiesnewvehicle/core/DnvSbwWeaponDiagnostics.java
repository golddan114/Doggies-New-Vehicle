package com.liucodex.doggiesnewvehicle.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Low-frequency, bounded, read-only projection of exact-SBW station and weapon state. */
public final class DnvSbwWeaponDiagnostics {
  public static final int SAMPLE_INTERVAL_TICKS = 20;
  public static final int MAX_ENTRIES = 32 * 8;
  private static final int MAX_TEXT_LENGTH = 160;

  private final Map<Key, FireResult> lastFireResults = new LinkedHashMap<>();
  private boolean enabled = true;
  private boolean collecting;
  private long collectingGameTime;
  private int collectingCandidateCount;
  private long collectingCoreRevision;
  private long collectingControlRevision;
  private long lastPublishedGameTime = Long.MIN_VALUE;
  private List<WeaponSnapshot> collectingWeapons = List.of();
  private Snapshot snapshot = Snapshot.empty();

  public boolean beginFrame(long gameTime, int candidateCount) {
    return beginFrame(gameTime, candidateCount, 0L, 0L);
  }

  public boolean beginFrame(long gameTime, int candidateCount, long coreRevision, long controlRevision) {
    if (gameTime < 0L || candidateCount < 0 || coreRevision < 0L || controlRevision < 0L) {
      throw new IllegalArgumentException("diagnostic frame values must not be negative");
    }
    if (lastPublishedGameTime != Long.MIN_VALUE && gameTime < lastPublishedGameTime) {
      clear();
    }
    collecting = enabled
        && (lastPublishedGameTime == Long.MIN_VALUE
            || gameTime - lastPublishedGameTime >= SAMPLE_INTERVAL_TICKS);
    if (!collecting) {
      return false;
    }
    collectingGameTime = gameTime;
    collectingCandidateCount = candidateCount;
    collectingCoreRevision = coreRevision;
    collectingControlRevision = controlRevision;
    collectingWeapons = new ArrayList<>();
    return true;
  }

  public void record(WeaponDraft draft) {
    Objects.requireNonNull(draft, "draft");
    if (!collecting || collectingWeapons.size() >= MAX_ENTRIES) {
      return;
    }
    FireResult last = lastFireResults.get(new Key(draft.stationId(), draft.weaponName()));
    collectingWeapons.add(new WeaponSnapshot(
        draft.stationId(),
        draft.weaponName(),
        draft.targetId(),
        draft.arcReason(),
        draft.localYaw(),
        draft.localPitch(),
        draft.channelId(),
        draft.physicalOwner(),
        draft.physicalReason(),
        draft.actualYaw(),
        draft.actualPitch(),
        draft.desiredYaw(),
        draft.desiredPitch(),
        draft.alignedTicks(),
        draft.blockLineClear(),
        draft.entityCorridorClear(),
        draft.ammoCount(),
        draft.canShoot(),
        draft.cooldownRemainingTicks(),
        draft.gateReason(),
        last == null ? "NEVER" : last.result(),
        last == null ? 0L : last.gameTime()));
  }

  public void completeFrame() {
    if (!collecting) {
      return;
    }
    snapshot = new Snapshot(
        collectingGameTime,
        collectingCandidateCount,
        List.copyOf(collectingWeapons), collectingCoreRevision, collectingControlRevision);
    lastPublishedGameTime = collectingGameTime;
    collectingWeapons = List.of();
    collecting = false;
  }

  public void recordFireResult(
      String stationId, String weaponName, String result, long gameTime) {
    if (!enabled) {
      return;
    }
    Key key = new Key(stationId, weaponName);
    while (lastFireResults.size() >= MAX_ENTRIES && !lastFireResults.containsKey(key)) {
      lastFireResults.remove(lastFireResults.keySet().iterator().next());
    }
    lastFireResults.put(key, new FireResult(requireText(result, "result"), gameTime));
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    if (!enabled) {
      clear();
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public void clear() {
    collecting = false;
    collectingGameTime = 0L;
    collectingCandidateCount = 0;
    collectingWeapons = List.of();
    lastPublishedGameTime = Long.MIN_VALUE;
    lastFireResults.clear();
    snapshot = Snapshot.empty();
  }

  public record WeaponDraft(
      String stationId,
      String weaponName,
      Optional<UUID> targetId,
      String arcReason,
      float localYaw,
      float localPitch,
      String channelId,
      DnvPhysicalAimChannelAuthority.Owner physicalOwner,
      DnvPhysicalAimChannelAuthority.Reason physicalReason,
      float actualYaw,
      float actualPitch,
      float desiredYaw,
      float desiredPitch,
      int alignedTicks,
      boolean blockLineClear,
      boolean entityCorridorClear,
      int ammoCount,
      boolean canShoot,
      int cooldownRemainingTicks,
      String gateReason) {
    public WeaponDraft {
      stationId = requireText(stationId, "stationId");
      weaponName = requireText(weaponName, "weaponName");
      targetId = Objects.requireNonNull(targetId, "targetId");
      arcReason = requireText(arcReason, "arcReason");
      channelId = requireText(channelId, "channelId");
      Objects.requireNonNull(physicalOwner, "physicalOwner");
      Objects.requireNonNull(physicalReason, "physicalReason");
      gateReason = requireText(gateReason, "gateReason");
      if (alignedTicks < 0 || ammoCount < 0 || cooldownRemainingTicks < 0) {
        throw new IllegalArgumentException("weapon diagnostic counters must not be negative");
      }
    }
  }

  public record WeaponSnapshot(
      String stationId,
      String weaponName,
      Optional<UUID> targetId,
      String arcReason,
      float localYaw,
      float localPitch,
      String channelId,
      DnvPhysicalAimChannelAuthority.Owner physicalOwner,
      DnvPhysicalAimChannelAuthority.Reason physicalReason,
      float actualYaw,
      float actualPitch,
      float desiredYaw,
      float desiredPitch,
      int alignedTicks,
      boolean blockLineClear,
      boolean entityCorridorClear,
      int ammoCount,
      boolean canShoot,
      int cooldownRemainingTicks,
      String gateReason,
      String lastFireResult,
      long lastFireGameTime) {}

  public record Snapshot(
      long capturedGameTime, int candidateCount, List<WeaponSnapshot> weapons,
      long coreRevision, long controlRevision) {
    public Snapshot {
      if (capturedGameTime < 0L || candidateCount < 0 || coreRevision < 0L || controlRevision < 0L) {
        throw new IllegalArgumentException("diagnostic snapshot values must not be negative");
      }
      weapons = List.copyOf(Objects.requireNonNull(weapons, "weapons"));
      if (weapons.size() > MAX_ENTRIES) {
        throw new IllegalArgumentException("diagnostic snapshot exceeds bounded cardinality");
      }
    }

    private static Snapshot empty() {
      return new Snapshot(0L, 0, List.of(), 0L, 0L);
    }

    public boolean matchesRevision(long currentCoreRevision, long currentControlRevision) {
      return coreRevision == currentCoreRevision && controlRevision == currentControlRevision;
    }
  }

  private record Key(String stationId, String weaponName) {
    private Key {
      stationId = requireText(stationId, "stationId");
      weaponName = requireText(weaponName, "weaponName");
    }
  }

  private record FireResult(String result, long gameTime) {
    private FireResult {
      if (gameTime < 0L) {
        throw new IllegalArgumentException("fire result time must not be negative");
      }
    }
  }

  private static String requireText(String value, String label) {
    String checked = Objects.requireNonNull(value, label).trim();
    if (checked.isEmpty() || checked.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(label + " must be bounded text");
    }
    return checked;
  }
}

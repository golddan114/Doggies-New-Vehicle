package com.liucodex.doggiesnewvehicle.core;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Versioned minimal persistence codec; live visibility and authority are never persisted. */
public final class DnvAutonomyMemoryCodec {
  private static final int SCHEMA_VERSION = 1;

  private DnvAutonomyMemoryCodec() {
  }

  public static CompoundTag encode(DnvAutonomyCore.MemorySnapshot memory) {
    CompoundTag tag = new CompoundTag();
    tag.putInt("Schema", SCHEMA_VERSION);
    tag.putLong("Revision", memory.revision());
    tag.putLong("LastGameTime", memory.lastGameTime());
    memory.targetId().ifPresent(target -> tag.putUUID("Target", target));
    tag.putDouble("TargetX", memory.lastKnownX());
    tag.putDouble("TargetY", memory.lastKnownY());
    tag.putDouble("TargetZ", memory.lastKnownZ());
    tag.putDouble("Threat", memory.targetThreat());
    tag.putLong("LastSeen", memory.targetLastSeenGameTime());
    tag.putLong("Acquired", memory.targetAcquiredGameTime());
    tag.putString("State", memory.state().name());
    tag.putLong("StateSince", memory.stateSinceGameTime());
    tag.putLong("BlockedUntil", memory.blockedUntilGameTime());
    return tag;
  }

  public static Optional<DnvAutonomyCore.MemorySnapshot> decode(CompoundTag tag) {
    if (tag == null
        || !tag.contains("Schema", Tag.TAG_INT)
        || tag.getInt("Schema") != SCHEMA_VERSION) {
      return Optional.empty();
    }
    try {
      Optional<java.util.UUID> target = tag.hasUUID("Target")
          ? Optional.of(tag.getUUID("Target"))
          : Optional.empty();
      DnvAutonomyCore.TacticalState state = DnvAutonomyCore.TacticalState.valueOf(
          tag.getString("State"));
      return Optional.of(new DnvAutonomyCore.MemorySnapshot(
          tag.getLong("Revision"),
          tag.getLong("LastGameTime"),
          target,
          tag.getDouble("TargetX"),
          tag.getDouble("TargetY"),
          tag.getDouble("TargetZ"),
          tag.getDouble("Threat"),
          tag.getLong("LastSeen"),
          tag.getLong("Acquired"),
          state,
          tag.getLong("StateSince"),
          Optional.empty(),
          tag.getLong("BlockedUntil")));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}

package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DnvAutonomyMemoryCodecTest {
  @Test
  void roundTripKeepsOnlyBoundedValueMemory() {
    UUID target = UUID.fromString("00000000-0000-0000-0000-000000000777");
    DnvAutonomyCore.MemorySnapshot memory = new DnvAutonomyCore.MemorySnapshot(
        5L, 100L, Optional.of(target), 1.0D, 2.0D, 3.0D, 40.0D,
        99L, 80L, DnvAutonomyCore.TacticalState.FIRE_HOLD, 90L,
        Optional.empty(), 120L);

    DnvAutonomyCore.MemorySnapshot decoded = DnvAutonomyMemoryCodec.decode(
        DnvAutonomyMemoryCodec.encode(memory)).orElseThrow();

    assertEquals(memory, decoded);
  }

  @Test
  void unknownSchemaFailsClosed() {
    CompoundTag tag = new CompoundTag();
    tag.putInt("Schema", 99);

    assertTrue(DnvAutonomyMemoryCodec.decode(tag).isEmpty());
  }
}

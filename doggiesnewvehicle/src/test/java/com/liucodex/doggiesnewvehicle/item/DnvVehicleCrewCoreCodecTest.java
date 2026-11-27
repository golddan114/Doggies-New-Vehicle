package com.liucodex.doggiesnewvehicle.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewCoreCodecTest {
  private static final UUID CORE = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000032");

  @Test
  void snapshotRoundTripProducesARevalidatableDraftInStableStationOrder() {
    DnvVehicleCrewCoreCodec.DecodeResult decoded =
        DnvVehicleCrewCoreCodec.decode(DnvVehicleCrewCoreCodec.encode(snapshot()));

    assertTrue(decoded.accepted());
    DnvVehicleCrewProfile.Draft draft = decoded.draft().orElseThrow();
    assertEquals(CORE, draft.coreId());
    assertEquals(OWNER, draft.configuredBy());
    assertEquals(List.of("nose", "tail"), draft.stations().stream()
        .map(DnvVehicleCrewProfile.StationDraft::stationId)
        .toList());
  }

  @Test
  void unknownFutureSchemaStopsBeforeConstructingADraft() {
    CompoundTag encoded = DnvVehicleCrewCoreCodec.encode(snapshot());
    encoded.getCompound(DnvVehicleCrewCoreCodec.ROOT_KEY).putInt("SchemaVersion", 99);
    DnvVehicleCrewCoreCodec.DecodeResult result = DnvVehicleCrewCoreCodec.decode(encoded);
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewCoreCodec.IssueCode.UNSUPPORTED_SCHEMA));
  }

  @Test
  void invalidUuidAndUnknownModeReturnFieldErrorsWithoutThrowing() {
    CompoundTag encoded = DnvVehicleCrewCoreCodec.encode(snapshot());
    CompoundTag payload = encoded.getCompound(DnvVehicleCrewCoreCodec.ROOT_KEY);
    payload.put("CoreId", new IntArrayTag(new int[] {1, 2}));
    payload.getList("Stations", net.minecraft.nbt.Tag.TAG_COMPOUND)
        .getCompound(0).putString("Mode", "UNSAFE_FIRE_EVERYTHING");

    DnvVehicleCrewCoreCodec.DecodeResult result = DnvVehicleCrewCoreCodec.decode(encoded);
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewCoreCodec.IssueCode.INVALID_UUID
            || issue.field().equals("CoreId")));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewCoreCodec.IssueCode.UNKNOWN_ENUM));
  }

  @Test
  void textAndStationBoundsRejectOversizedItemPayloads() {
    CompoundTag encoded = DnvVehicleCrewCoreCodec.encode(snapshot());
    CompoundTag payload = encoded.getCompound(DnvVehicleCrewCoreCodec.ROOT_KEY);
    payload.putString("AdapterId", "x".repeat(257));
    ListTag stations = new ListTag();
    for (int index = 0; index <= DnvVehicleCrewProfile.MAX_STATIONS; index++) {
      CompoundTag station = new CompoundTag();
      station.putString("StationId", "s" + index);
      station.putString("Mode", DnvControlAuthority.WeaponMode.HOLD_FIRE.name());
      stations.add(station);
    }
    payload.put("Stations", stations);

    DnvVehicleCrewCoreCodec.DecodeResult result = DnvVehicleCrewCoreCodec.decode(encoded);
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewCoreCodec.IssueCode.TEXT_TOO_LONG));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewCoreCodec.IssueCode.TOO_MANY_STATIONS));
  }

  @Test
  void missingNamespacedRootAndWrongStationTagTypeFailClosed() {
    assertFalse(DnvVehicleCrewCoreCodec.decode(new CompoundTag()).accepted());

    CompoundTag encoded = DnvVehicleCrewCoreCodec.encode(snapshot());
    encoded.getCompound(DnvVehicleCrewCoreCodec.ROOT_KEY)
        .putString("Stations", "not-a-list");
    DnvVehicleCrewCoreCodec.DecodeResult wrongType =
        DnvVehicleCrewCoreCodec.decode(encoded);
    assertFalse(wrongType.accepted());
    assertTrue(wrongType.issues().stream().anyMatch(issue ->
        issue.field().equals("Stations")
            && issue.code() == DnvVehicleCrewCoreCodec.IssueCode.WRONG_TYPE));
  }

  private static DnvVehicleCrewProfile.Snapshot snapshot() {
    Map<String, DnvVehicleCrewProfile.StationPolicy> stations = new LinkedHashMap<>();
    stations.put("nose", new DnvVehicleCrewProfile.StationPolicy(
        "nose", 0, true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE));
    stations.put("tail", new DnvVehicleCrewProfile.StationPolicy(
        "tail", 1, false, DnvControlAuthority.WeaponMode.MANUAL_ONLY));
    return new DnvVehicleCrewProfile.Snapshot(
        1,
        CORE,
        OWNER,
        "dnv:test",
        7L,
        "superbwarfare:test_vehicle",
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        stations);
  }
}

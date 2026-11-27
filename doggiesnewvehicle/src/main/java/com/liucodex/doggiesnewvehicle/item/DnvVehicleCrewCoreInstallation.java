package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Atomic server boundary from untrusted item NBT to one vehicle's installed crew policy. */
public final class DnvVehicleCrewCoreInstallation {
  private DnvVehicleCrewCoreInstallation() {}

  public static InstallationResult install(
      CompoundTag itemTag,
      DnvVehicleCrewProfile.AdapterTemplate currentTemplate,
      DnvVehicleCrewProfile.ValidationContext context,
      DnvVehicleCrewController controller,
      long expectedCoreRevision) {
    Objects.requireNonNull(currentTemplate, "currentTemplate");
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(controller, "controller");

    DnvVehicleCrewCoreCodec.DecodeResult decoded =
        DnvVehicleCrewCoreCodec.decode(itemTag);
    if (!decoded.accepted()) {
      List<Issue> issues = decoded.issues().stream()
          .map(issue -> new Issue(
              Stage.DECODE,
              issue.field(),
              issue.code().name()))
          .toList();
      return InstallationResult.rejected(issues, controller.revision());
    }

    DnvVehicleCrewProfile.ValidationResult validated =
        DnvVehicleCrewProfile.validate(
            decoded.draft().orElseThrow(),
            currentTemplate,
            context);
    if (!validated.accepted()) {
      List<Issue> issues = validated.issues().stream()
          .map(issue -> new Issue(
              Stage.PROFILE,
              issue.field(),
              issue.code().name()))
          .toList();
      return InstallationResult.rejected(issues, controller.revision());
    }

    DnvVehicleCrewProfile.Snapshot snapshot = validated.snapshot().orElseThrow();
    DnvVehicleCrewController.MutationResult installed =
        controller.installCore(snapshot, expectedCoreRevision);
    if (!installed.accepted()) {
      return InstallationResult.rejected(
          List.of(new Issue(
              Stage.CONTROLLER,
              "core",
              installed.reason().name())),
          installed.revision());
    }
    return new InstallationResult(
        true,
        Optional.of(snapshot),
        List.of(),
        installed.revision());
  }

  public record InstallationResult(
      boolean accepted,
      Optional<DnvVehicleCrewProfile.Snapshot> snapshot,
      List<Issue> issues,
      DnvVehicleCrewController.RuntimeRevision revision) {
    public InstallationResult {
      snapshot = Objects.requireNonNull(snapshot, "snapshot");
      issues = List.copyOf(issues);
      Objects.requireNonNull(revision, "revision");
      if (accepted != (snapshot.isPresent() && issues.isEmpty())) {
        throw new IllegalArgumentException("inconsistent installation result");
      }
    }

    public Optional<UUID> coreId() {
      return snapshot.map(DnvVehicleCrewProfile.Snapshot::coreId);
    }

    private static InstallationResult rejected(
        List<Issue> issues,
        DnvVehicleCrewController.RuntimeRevision revision) {
      List<Issue> copy = new ArrayList<>(issues);
      if (copy.isEmpty()) {
        throw new IllegalArgumentException("rejected installation requires an issue");
      }
      return new InstallationResult(false, Optional.empty(), copy, revision);
    }
  }

  public record Issue(Stage stage, String field, String code) {
    public Issue {
      Objects.requireNonNull(stage, "stage");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(code, "code");
    }
  }

  public enum Stage {
    DECODE,
    PROFILE,
    CONTROLLER
  }
}

package com.liucodex.doggiesnewvehicle.item;

import java.util.Objects;
import java.util.function.Consumer;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Client-installed projection callbacks; owns no world, screen, or queued state. */
public final class DnvVehicleCrewClientProjection {
  private static volatile Handlers handlers = new Handlers(value -> {}, value -> {});

  private DnvVehicleCrewClientProjection() {}

  /** Publishes one complete callback set; the client installs it before connecting. */
  public static void install(
      Consumer<DnvVehicleCrewConfigSessions.View> open,
      Consumer<CommitResponse> respond) {
    handlers = new Handlers(open, respond);
  }

  public static void open(DnvVehicleCrewConfigSessions.View view) {
    handlers.open().accept(view);
  }

  public static void respond(CommitResponse response) {
    handlers.respond().accept(response);
  }

  private record Handlers(
      Consumer<DnvVehicleCrewConfigSessions.View> open,
      Consumer<CommitResponse> respond) {
    private Handlers {
      Objects.requireNonNull(open, "open");
      Objects.requireNonNull(respond, "respond");
    }
  }

  public record CommitResponse(
      UUID token,
      boolean accepted,
      DnvVehicleCrewConfigSessions.Issue issue,
      Optional<String> savedFlightModeName,
      List<String> savedStationIds,
      List<String> savedStationModeNames) {
    public CommitResponse {
      Objects.requireNonNull(token, "token");
      Objects.requireNonNull(issue, "issue");
      savedFlightModeName = Objects.requireNonNull(
          savedFlightModeName, "savedFlightModeName");
      savedStationIds = List.copyOf(savedStationIds);
      savedStationModeNames = List.copyOf(savedStationModeNames);
      if (accepted != (issue == DnvVehicleCrewConfigSessions.Issue.NONE)
          || accepted != savedFlightModeName.isPresent()
          || savedStationIds.size() != savedStationModeNames.size()
          || savedStationIds.size() > DnvVehicleCrewProfile.MAX_STATIONS
          || (!accepted && !savedStationIds.isEmpty())) {
        throw new IllegalArgumentException("inconsistent configuration response");
      }
    }
  }
}

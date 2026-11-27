package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic authority for one physical aim register shared by logical control domains.
 * Manual input wins immediately for a short lease; AI commands must carry the current revision.
 */
public final class DnvPhysicalAimChannelAuthority {
  public static final int MANUAL_PRIORITY_TICKS = 8;
  public static final int MAX_COMMAND_AGE_TICKS = 2;
  public static final int MAX_ID_LENGTH = 96;

  private final String channelId;
  private Decision decision;
  private long lastGameTime = -1L;
  private long manualLeaseUntil;
  private long revision;

  public DnvPhysicalAimChannelAuthority(String channelId) {
    this.channelId = requireId(channelId);
    decision = new Decision(
        this.channelId, 0L, Owner.NONE, Optional.empty(), Reason.NO_AI_REQUEST, 0L);
  }

  public Decision observe(Observation observation) {
    Objects.requireNonNull(observation, "observation");
    if (lastGameTime >= 0L && observation.gameTime() < lastGameTime) {
      lastGameTime = observation.gameTime();
      manualLeaseUntil = 0L;
      return transition(
          observation.gameTime(), Owner.NONE, Optional.empty(), Reason.TIME_REGRESSION);
    }
    lastGameTime = observation.gameTime();
    if (!observation.loaded()) {
      manualLeaseUntil = 0L;
      return transition(
          observation.gameTime(), Owner.NONE, Optional.empty(), Reason.SOURCE_UNLOADED);
    }
    if (!observation.channelValid()) {
      manualLeaseUntil = 0L;
      return transition(
          observation.gameTime(), Owner.NONE, Optional.empty(), Reason.CHANNEL_INVALID);
    }

    Optional<UUID> manualPlayer = observation.manualPlayer();
    if (manualPlayer.isPresent()) {
      if (!observation.manualSignalReliable()) {
        manualLeaseUntil = 0L;
        return transition(
            observation.gameTime(), Owner.NONE, manualPlayer,
            Reason.MANUAL_SIGNAL_UNCERTAIN);
      }
      if (observation.manualAimActive() || observation.manualFireActive()) {
        manualLeaseUntil = saturatingAdd(observation.gameTime(), MANUAL_PRIORITY_TICKS);
        return transition(
            observation.gameTime(), Owner.MANUAL, manualPlayer, Reason.MANUAL_INPUT);
      }
      if (decision.owner() == Owner.MANUAL
          && decision.manualPlayer().equals(manualPlayer)
          && observation.gameTime() < manualLeaseUntil) {
        return transition(
            observation.gameTime(), Owner.MANUAL, manualPlayer, Reason.MANUAL_LEASE);
      }
    } else {
      manualLeaseUntil = 0L;
    }

    return observation.aiRequested()
        ? transition(observation.gameTime(), Owner.AI, Optional.empty(), Reason.AI_AUTHORIZED)
        : transition(observation.gameTime(), Owner.NONE, Optional.empty(), Reason.NO_AI_REQUEST);
  }

  public boolean authorizeAiCommand(
      long expectedRevision, long issuedGameTime, long currentGameTime) {
    return decision.owner() == Owner.AI
        && expectedRevision == revision
        && issuedGameTime >= 0L
        && currentGameTime >= issuedGameTime
        && currentGameTime - issuedGameTime <= MAX_COMMAND_AGE_TICKS;
  }

  public Decision snapshot() {
    return decision;
  }

  /** Entity unload and server stop/restart never retain a manual or AI lease. */
  public void clear() {
    lastGameTime = -1L;
    manualLeaseUntil = 0L;
    transition(0L, Owner.NONE, Optional.empty(), Reason.CLEARED);
  }

  private Decision transition(
      long gameTime, Owner owner, Optional<UUID> player, Reason reason) {
    if (decision.owner() != owner
        || !decision.manualPlayer().equals(player)
        || decision.reason() != reason) {
      if (revision == Long.MAX_VALUE) {
        throw new IllegalStateException("physical aim authority revision exhausted");
      }
      revision++;
    }
    decision = new Decision(
        channelId, gameTime, owner, player, reason,
        owner == Owner.MANUAL ? manualLeaseUntil : 0L,
        revision);
    return decision;
  }

  private static long saturatingAdd(long value, int delta) {
    return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
  }

  private static String requireId(String value) {
    String checked = Objects.requireNonNull(value, "channelId").trim();
    if (checked.isEmpty() || checked.length() > MAX_ID_LENGTH) {
      throw new IllegalArgumentException("channelId must be bounded text");
    }
    return checked;
  }

  public record Observation(
      long gameTime,
      boolean loaded,
      boolean channelValid,
      boolean aiRequested,
      Optional<UUID> manualPlayer,
      boolean manualSignalReliable,
      boolean manualAimActive,
      boolean manualFireActive) {
    public Observation {
      if (gameTime < 0L) {
        throw new IllegalArgumentException("gameTime must not be negative");
      }
      manualPlayer = Objects.requireNonNull(manualPlayer, "manualPlayer");
      if (manualPlayer.isEmpty() && (manualAimActive || manualFireActive)) {
        throw new IllegalArgumentException("manual input requires a manual player");
      }
    }
  }

  public record Decision(
      String channelId,
      long evaluatedGameTime,
      Owner owner,
      Optional<UUID> manualPlayer,
      Reason reason,
      long manualLeaseUntil,
      long revision) {
    public Decision {
      requireId(channelId);
      if (evaluatedGameTime < 0L || manualLeaseUntil < 0L || revision < 0L) {
        throw new IllegalArgumentException("authority values must not be negative");
      }
      Objects.requireNonNull(owner, "owner");
      manualPlayer = Objects.requireNonNull(manualPlayer, "manualPlayer");
      Objects.requireNonNull(reason, "reason");
    }

    private Decision(
        String channelId,
        long evaluatedGameTime,
        Owner owner,
        Optional<UUID> manualPlayer,
        Reason reason,
        long revision) {
      this(channelId, evaluatedGameTime, owner, manualPlayer, reason, 0L, revision);
    }
  }

  public enum Owner {
    AI,
    MANUAL,
    NONE
  }

  public enum Reason {
    AI_AUTHORIZED,
    MANUAL_INPUT,
    MANUAL_LEASE,
    MANUAL_SIGNAL_UNCERTAIN,
    NO_AI_REQUEST,
    CHANNEL_INVALID,
    SOURCE_UNLOADED,
    TIME_REGRESSION,
    CLEARED
  }
}

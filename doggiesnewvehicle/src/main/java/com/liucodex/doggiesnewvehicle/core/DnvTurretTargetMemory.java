package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transient target memory shared by intrinsic DNV turrets. It chooses an identity; it never grants
 * relation, line-of-sight, resource, aim, or fire permission.
 */
public final class DnvTurretTargetMemory {
  public static final int AUTONOMOUS_PRIORITY = 100;
  public static final int COMMAND_PRIORITY = 200;

  private Optional<TargetSnapshot> target = Optional.empty();
  private long configurationRevision = -1L;
  private long lastGameTime = -1L;
  private Decision lastDecision = new Decision(
      Optional.empty(), DecisionReason.NO_VALID_TARGET, Optional.empty());

  public Decision decide(Input input) {
    Objects.requireNonNull(input, "input");
    if (lastGameTime >= 0L && input.gameTime() < lastGameTime) {
      configurationRevision = input.configurationRevision();
      lastGameTime = input.gameTime();
      return clear(DecisionReason.CLOCK_ROLLBACK, InvalidationReason.CLOCK_ROLLBACK);
    }

    Optional<InvalidationReason> priorInvalidation = Optional.empty();
    if (configurationRevision >= 0L
        && configurationRevision != input.configurationRevision()) {
      target = Optional.empty();
      priorInvalidation = Optional.of(InvalidationReason.CONFIGURATION_CHANGED);
    }
    configurationRevision = input.configurationRevision();
    lastGameTime = input.gameTime();

    if (!input.coreActive()) {
      return clear(DecisionReason.CORE_INACTIVE, InvalidationReason.CORE_INACTIVE);
    }

    Optional<Candidate> command = input.command().filter(value -> value.validAt(input.gameTime()));
    if (command.isPresent()) {
      return select(command.orElseThrow(), input.gameTime(), priorInvalidation);
    }
    if (input.commandMode() == CommandMode.ACTIVE) {
      return clear(DecisionReason.COMMAND_TARGET_INVALID, InvalidationReason.TARGET_UNAVAILABLE);
    }
    if (input.commandMode() == CommandMode.DISABLE) {
      return clear(DecisionReason.COMMAND_DISABLED, InvalidationReason.COMMAND_ENDED);
    }
    if (input.commandMode() == CommandMode.HOLD) {
      return clear(DecisionReason.COMMAND_HOLD_NO_VALID_TARGET,
          InvalidationReason.TARGET_UNAVAILABLE);
    }

    if (priorInvalidation.isEmpty()
        && target.filter(value -> value.source().isCommand()).isPresent()) {
      priorInvalidation = Optional.of(target
          .filter(value -> value.expiresGameTime() < input.gameTime())
          .map(value -> InvalidationReason.TTL_EXPIRED)
          .orElse(InvalidationReason.COMMAND_ENDED));
    }
    Optional<Candidate> autonomous = input.autonomous()
        .filter(value -> value.validAt(input.gameTime()));
    if (autonomous.isPresent()) {
      return select(autonomous.orElseThrow(), input.gameTime(), priorInvalidation);
    }

    InvalidationReason reason = target
        .filter(value -> value.source().isCommand())
        .map(value -> InvalidationReason.COMMAND_ENDED)
        .orElse(InvalidationReason.TARGET_UNAVAILABLE);
    if (target.filter(value -> value.expiresGameTime() < input.gameTime()).isPresent()) {
      reason = InvalidationReason.TTL_EXPIRED;
    }
    return clear(DecisionReason.NO_VALID_TARGET, reason, priorInvalidation);
  }

  public Decision invalidate(long gameTime, InvalidationReason reason) {
    if (gameTime < 0L) {
      throw new IllegalArgumentException("negative game time");
    }
    lastGameTime = gameTime;
    return clear(DecisionReason.INVALIDATED, Objects.requireNonNull(reason, "reason"));
  }

  public Decision current() {
    return lastDecision;
  }

  private Decision select(
      Candidate candidate,
      long gameTime,
      Optional<InvalidationReason> priorInvalidation) {
    TargetSnapshot next = new TargetSnapshot(
        candidate.targetId(), candidate.source(), candidate.priority(), candidate.revision(),
        candidate.observedGameTime(), candidate.expiresGameTime(),
        candidate.lastVerifiedPosition());
    boolean retained = target.filter(previous -> previous.targetId().equals(next.targetId())
        && previous.source() == next.source()
        && previous.revision() == next.revision()).isPresent();
    target = Optional.of(next);
    DecisionReason reason = next.source().isCommand()
        ? retained ? DecisionReason.COMMAND_RETAINED : DecisionReason.COMMAND_SELECTED
        : retained ? DecisionReason.AUTONOMOUS_RETAINED : DecisionReason.AUTONOMOUS_SELECTED;
    lastDecision = new Decision(target, reason, priorInvalidation);
    lastGameTime = gameTime;
    return lastDecision;
  }

  private Decision clear(DecisionReason reason, InvalidationReason invalidation) {
    return clear(reason, invalidation, Optional.empty());
  }

  private Decision clear(
      DecisionReason reason,
      InvalidationReason invalidation,
      Optional<InvalidationReason> priorInvalidation) {
    target = Optional.empty();
    lastDecision = new Decision(
        target, reason, priorInvalidation.isPresent()
            ? priorInvalidation : Optional.of(invalidation));
    return lastDecision;
  }

  public record Input(
      long gameTime,
      long configurationRevision,
      boolean coreActive,
      CommandMode commandMode,
      Optional<Candidate> command,
      Optional<Candidate> autonomous) {
    public Input {
      Objects.requireNonNull(commandMode, "commandMode");
      command = Objects.requireNonNull(command, "command");
      autonomous = Objects.requireNonNull(autonomous, "autonomous");
      if (gameTime < 0L || configurationRevision < 0L
          || command.filter(value -> !value.source().isCommand()).isPresent()
          || autonomous.filter(value -> value.source() != Source.AUTONOMOUS).isPresent()) {
        throw new IllegalArgumentException("invalid target-memory input");
      }
    }
  }

  /** Adapter-owned live configuration identity; reading it does not issue an execution frame. */
  public record Configuration(
      boolean coreActive,
      long profileRevision,
      long controlRevision) {
    public Configuration {
      if (profileRevision < 0L || controlRevision < 0L) {
        throw new IllegalArgumentException("negative turret configuration revision");
      }
    }
  }

  /** Generic command observation keeps platform entities independent of adapter implementation. */
  public record CommandObservation<T>(
      Optional<T> target,
      long revision,
      long validUntilTick,
      boolean explicitDesignation,
      CommandMode commandMode,
      String reason) {
    public CommandObservation {
      target = Objects.requireNonNull(target, "target");
      Objects.requireNonNull(commandMode, "commandMode");
      Objects.requireNonNull(reason, "reason");
      if (revision < -1L || validUntilTick < -1L) {
        throw new IllegalArgumentException("invalid command observation");
      }
    }
  }

  public record Candidate(
      UUID targetId,
      Source source,
      int priority,
      long revision,
      long observedGameTime,
      long expiresGameTime,
      Position lastVerifiedPosition) {
    public Candidate {
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(lastVerifiedPosition, "lastVerifiedPosition");
      int expectedPriority = source.isCommand() ? COMMAND_PRIORITY : AUTONOMOUS_PRIORITY;
      if (priority != expectedPriority || revision < 0L || observedGameTime < 0L
          || expiresGameTime < observedGameTime) {
        throw new IllegalArgumentException("invalid target candidate");
      }
    }

    public static Candidate command(
        UUID targetId,
        boolean explicit,
        long revision,
        long observedGameTime,
        long expiresGameTime,
        Position lastVerifiedPosition) {
      return new Candidate(targetId,
          explicit ? Source.EXPLICIT_COMMAND : Source.EXTERNAL_ASSIGNMENT,
          COMMAND_PRIORITY, revision, observedGameTime, expiresGameTime,
          lastVerifiedPosition);
    }

    public static Candidate autonomous(
        UUID targetId,
        long revision,
        long observedGameTime,
        long expiresGameTime,
        Position lastVerifiedPosition) {
      return new Candidate(targetId, Source.AUTONOMOUS, AUTONOMOUS_PRIORITY, revision,
          observedGameTime, expiresGameTime, lastVerifiedPosition);
    }

    private boolean validAt(long gameTime) {
      return observedGameTime <= gameTime && gameTime <= expiresGameTime;
    }
  }

  public record TargetSnapshot(
      UUID targetId,
      Source source,
      int priority,
      long revision,
      long observedGameTime,
      long expiresGameTime,
      Position lastVerifiedPosition) {
    public TargetSnapshot {
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(lastVerifiedPosition, "lastVerifiedPosition");
    }
  }

  public record Position(double x, double y, double z) {
    public Position {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
        throw new IllegalArgumentException("target position must be finite");
      }
    }
  }

  public record Decision(
      Optional<TargetSnapshot> target,
      DecisionReason reason,
      Optional<InvalidationReason> invalidatedBy) {
    public Decision {
      target = Objects.requireNonNull(target, "target");
      Objects.requireNonNull(reason, "reason");
      invalidatedBy = Objects.requireNonNull(invalidatedBy, "invalidatedBy");
    }
  }

  public enum Source {
    EXPLICIT_COMMAND,
    EXTERNAL_ASSIGNMENT,
    AUTONOMOUS;

    public boolean isCommand() {
      return this != AUTONOMOUS;
    }
  }

  public enum CommandMode {
    NONE,
    ACTIVE,
    HOLD,
    DISABLE
  }

  public enum DecisionReason {
    COMMAND_SELECTED,
    COMMAND_RETAINED,
    AUTONOMOUS_SELECTED,
    AUTONOMOUS_RETAINED,
    COMMAND_TARGET_INVALID,
    COMMAND_DISABLED,
    COMMAND_HOLD_NO_VALID_TARGET,
    NO_VALID_TARGET,
    CORE_INACTIVE,
    CLOCK_ROLLBACK,
    INVALIDATED
  }

  public enum InvalidationReason {
    CONFIGURATION_CHANGED,
    TTL_EXPIRED,
    COMMAND_ENDED,
    TARGET_UNAVAILABLE,
    CORE_INACTIVE,
    CLOCK_ROLLBACK,
    ENTITY_INVALIDATED
  }
}

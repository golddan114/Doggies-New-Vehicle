package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** One non-persistent, single-use execution. The platform still owns its queue and ammunition. */
public final class DnvDeferredShot {
  public static final int MAX_AGE_TICKS = DnvPhysicalAimChannelAuthority.MAX_COMMAND_AGE_TICKS;
  private final Intent intent;
  private State state = State.CREATED;
  private String reason = "NOT_QUEUED";
  private long evaluatedTick;
  private Admission admission;

  public DnvDeferredShot(Intent intent) {
    this.intent = Objects.requireNonNull(intent, "intent");
    evaluatedTick = intent.issuedTick();
  }

  public boolean queued(boolean accepted) {
    if (state != State.CREATED) return false;
    state = accepted ? State.QUEUED : State.REJECTED;
    reason = accepted ? "QUEUED" : "QUEUE_REJECTED";
    return accepted;
  }

  public void execute(long now, Supplier<String> revalidate, Runnable nativeExecution) {
    if (state != State.QUEUED) return;
    evaluatedTick = Math.max(0L, now);
    if (now < intent.issuedTick() || now - intent.issuedTick() > MAX_AGE_TICKS) {
      cancel("EXPIRED");
      return;
    }
    // Claim before calling out: reentrant execution may never run the native owner twice.
    state = State.EXECUTING;
    try {
      String rejection = revalidate.get();
      if (state != State.EXECUTING) return;
      if (rejection != null) {
        cancel(rejection);
        return;
      }
      nativeExecution.run();
      if (state == State.EXECUTING) {
        state = admission != null && admission.accepted() ? State.ACCEPTED : State.REJECTED;
        reason = admission == null ? "NO_ADMISSION" : admission.accepted() ? "WORLD_ACCEPTED" : "WORLD_REJECTED";
      }
    } catch (RuntimeException | LinkageError exception) {
      // Preserve any admission already observed. A later exception cannot erase a spawned entity.
      state = State.FAILED;
      reason = "EXECUTION_FAILED";
    }
  }

  public boolean admit(UUID projectile, DnvProjectilePreflight.Point position,
      DnvProjectilePreflight.Point velocity, BooleanSupplier worldAdmission) {
    if (state != State.EXECUTING || admission != null) return false;
    Objects.requireNonNull(projectile, "projectile");
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(velocity, "velocity");
    // Reserve the one projectile slot before the world/event callback can reenter.
    admission = new Admission(projectile, position, velocity, Acceptance.UNKNOWN);
    boolean accepted = worldAdmission.getAsBoolean();
    admission = new Admission(projectile, position, velocity, accepted ? Acceptance.ACCEPTED : Acceptance.REJECTED);
    return accepted;
  }

  public void cancel(String rejection) {
    if (terminal()) return;
    state = State.REJECTED;
    reason = Objects.requireNonNull(rejection, "rejection");
  }

  public boolean terminal() { return state == State.ACCEPTED || state == State.REJECTED || state == State.FAILED; }
  public void cancel(String rejection, long now) {
    if (!terminal()) evaluatedTick = Math.max(0L, now);
    cancel(rejection);
  }
  public Receipt receipt() { return new Receipt(intent, state, reason, evaluatedTick, Optional.ofNullable(admission)); }

  public record Intent(UUID operationId,
      UUID platformId, String station, String weapon, UUID targetId,
      UUID missionId, long missionRevision, long coreRevision, long controlRevision, long orderRevision,
      long issuedTick, int fireIndex, DnvProjectilePreflight.Point aimPoint) {

    public Intent {
      Objects.requireNonNull(operationId, "operationId");
      Objects.requireNonNull(platformId, "platformId");
      Objects.requireNonNull(targetId, "targetId");
      Objects.requireNonNull(missionId, "missionId");
      Objects.requireNonNull(aimPoint, "aimPoint");
      if (station == null || station.isBlank() || weapon == null || weapon.isBlank()
          || missionRevision < 0 || coreRevision < 0 || controlRevision < 0
          || issuedTick < 0 || fireIndex < 0) {
        throw new IllegalArgumentException("invalid shot identity");
      }
    }
  }

  public record Admission(UUID projectileId, DnvProjectilePreflight.Point position,
      DnvProjectilePreflight.Point velocity, Acceptance acceptance) {
    public boolean accepted() { return acceptance == Acceptance.ACCEPTED; }
  }
  public record Submission(Intent intent, Supplier<String> revalidate) {}
  public record Receipt(Intent intent, State state, String reason, long evaluatedTick, Optional<Admission> admission) {}
  public enum State { CREATED, QUEUED, EXECUTING, ACCEPTED, REJECTED, FAILED }
  public enum Acceptance { ACCEPTED, REJECTED, UNKNOWN }
}

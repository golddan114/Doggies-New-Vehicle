package com.liucodex.doggiesnewvehicle.core;

/** Opt-in guard around a native queued shot; it never replaces native ammunition or projectile logic. */
public interface DnvNativeRemoteShotGuard {
  Runnable guardNativeShot(Runnable nativeWork);

  /** A rejected native queue must release any transient order awaiting its callback. */
  default void onNativeShotQueueResult(boolean accepted) {}

  /** Hosts opting in publish the native recoil/sound only after actual projectile acceptance. */
  default boolean deferNativeShotEffects() { return false; }
}

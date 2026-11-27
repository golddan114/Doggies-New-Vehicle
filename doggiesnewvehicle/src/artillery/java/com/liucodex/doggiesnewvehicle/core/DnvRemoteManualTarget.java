package com.liucodex.doggiesnewvehicle.core;

import java.util.UUID;

/** Explicit entity opt-in consumed by RWS pairing. This contract provides no firing or camera capability. */
public interface DnvRemoteManualTarget {
  DnvTurretDefinition remoteManualDefinition();
  UUID remoteManualOwner();
}

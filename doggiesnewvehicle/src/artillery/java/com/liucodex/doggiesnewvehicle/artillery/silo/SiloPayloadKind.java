package com.liucodex.doggiesnewvehicle.artillery.silo;

/** Stable payload identity frozen in the existing launch/impact ledger. */
public enum SiloPayloadKind {
    NUCLEAR, DOGEGG;

    public static SiloPayloadKind read(String value) {
        try { return valueOf(value); }
        catch (RuntimeException unknown) { throw new IllegalArgumentException("Unknown silo payload: " + value, unknown); }
    }
}

package com.liucodex.doggiesnewvehicle.artillery.silo;

/** The spawn event runs before irrevocable native inventory/FE debit; unarmed candidates cannot tick. */
public final class SiloLaunchTransaction {
    public interface Port {
        boolean resourcesAvailable();
        boolean spawnUnarmed();
        void discardCandidate();
        void debitAndArm();
    }
    private SiloLaunchTransaction() { }
    public static boolean execute(Port port) {
        try {
            if (!port.resourcesAvailable()) { port.discardCandidate(); return false; }
            if (!port.spawnUnarmed()) { port.discardCandidate(); return false; }
            // addFreshEntity can invoke other mods. Recheck the live native resources afterwards.
            if (!port.resourcesAvailable()) { port.discardCandidate(); return false; }
        } catch (RuntimeException spawnFailure) {
            port.discardCandidate();throw spawnFailure;
        }
        port.debitAndArm();
        return true;
    }
}

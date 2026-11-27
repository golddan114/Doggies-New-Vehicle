package com.liucodex.doggiesnewvehicle.platform;

import java.util.Objects;
import java.util.UUID;

/** Platform-owned fixed stations; neither station can take over the other. */
public final class DnvHoverPlatformStations {
    public static final String AI_STATION = "AI";
    public static final String PLAYER_STATION = "PLAYER";
    public static final int PLAYER_LEASE_TICKS = 200;

    private UUID player;
    private long playerLeaseUntil;
    private long revision;

    public long revision() {
        return revision;
    }

    public ClaimResult claimPlayer(
            UUID playerId, UUID ownerId, long serverTick, long expectedRevision) {
        Objects.requireNonNull(playerId, "playerId");
        if (!playerId.equals(ownerId)) {
            return new ClaimResult(false, RejectReason.NOT_OWNER, revision);
        }
        if (expectedRevision != revision) {
            return new ClaimResult(false, RejectReason.STALE_REVISION, revision);
        }
        if (player != null && serverTick < playerLeaseUntil
                && !player.equals(playerId)) {
            return new ClaimResult(false, RejectReason.OCCUPIED, revision);
        }
        if (revision == Long.MAX_VALUE) {
            return new ClaimResult(false, RejectReason.REVISION_EXHAUSTED, revision);
        }
        player = playerId;
        playerLeaseUntil = serverTick > Long.MAX_VALUE - PLAYER_LEASE_TICKS
                ? Long.MAX_VALUE : serverTick + PLAYER_LEASE_TICKS;
        return new ClaimResult(true, RejectReason.NONE, ++revision);
    }

    public boolean playerMayFire(UUID playerId, UUID ownerId, long serverTick) {
        return playerId != null && playerId.equals(ownerId)
                && playerId.equals(player) && serverTick < playerLeaseUntil;
    }

    public boolean aiMayFire(long serverTick) {
        return true;
    }

    public void clearPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (playerId.equals(player)) {
            player = null;
            playerLeaseUntil = 0L;
            if (revision < Long.MAX_VALUE) {
                revision++;
            }
        }
    }

    public void clearAll() {
        if (player != null) {
            clearPlayer(player);
        }
    }

    public enum RejectReason {
        NONE, NOT_OWNER, STALE_REVISION, OCCUPIED, REVISION_EXHAUSTED
    }

    public record ClaimResult(boolean accepted, RejectReason reason, long revision) {
    }
}

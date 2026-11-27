package com.liucodex.doggiesnewvehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Stateless, client-only distance policy shared by independently owned mesh caches. */
public final class DnvRenderDetail {
    private DnvRenderDetail() { }
    public static int level(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return 0;
        return distanceSquared < 32 * 32 ? 0 : distanceSquared < 64 * 64 ? 1 : 2;
    }
    public static double distanceSquared(Entity entity) {
        return entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
    /** A resource pack changing the full mesh must opt in with matching derived resources. */
    public static boolean matches(byte[] full, byte[] lod, byte[] proof) {
        try {
            String[] hashes = new String(proof, java.nio.charset.StandardCharsets.US_ASCII).strip().split("\\s+");
            if (hashes.length != 2) return false;
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hex = java.util.HexFormat.of();
            return hashes[0].equals(hex.formatHex(digest.digest(full))) && hashes[1].equals(hex.formatHex(digest.digest(lod)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

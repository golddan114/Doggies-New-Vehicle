package com.liucodex.doggiesnewvehicle.core;

/** Exact optional utility identity; ordinary invisible mobs and other Spore entities are not scents. */
public final class DnvSporeScentIdentity {
    private DnvSporeScentIdentity() { }

    public static boolean matches(String className, String registryId) {
        return "com.Harbinger.Spore.Sentities.Utility.ScentEntity".equals(className)
                && "spore:scent".equals(registryId);
    }
}

package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.world.entity.Entity;

/** Read-only nuclear interception boundary. The projectile owns armed state, actor and health. */
public interface DnvNuclearInterceptionTarget {
    boolean armed();
    Entity interceptionOwner();
}

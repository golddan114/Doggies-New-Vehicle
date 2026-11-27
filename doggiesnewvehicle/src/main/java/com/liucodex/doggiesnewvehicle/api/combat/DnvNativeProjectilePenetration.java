package com.liucodex.doggiesnewvehicle.api.combat;

/** An optional turret owner supplies penetration for its already validated native shot. */
public interface DnvNativeProjectilePenetration {
    float directPenetration(String weapon);
}

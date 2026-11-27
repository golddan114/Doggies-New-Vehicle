package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.world.saveddata.TDMSavedData;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNuclearInterceptionTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Adds only our armed missile to the native tower's existing threat and affiliation queries. */
public final class NuclearAntiMissile {
    private NuclearAntiMissile(){ }
    public static boolean hostile(AutoAimableEntity tower,Entity missile){
        if(!(missile instanceof DnvNuclearInterceptionTarget target)||!target.armed()
                ||missile.isRemoved()||tower.level()!=missile.level())return false;
        var owner=target.interceptionOwner();var defender=tower.getOwner();
        // Mirrors the pinned SBW projectile resolver, including its TDM mode and unknown-owner refusal.
        if(owner==null||defender==null||owner==defender)return false;
        if(owner.getTeam()!=null&&TDMSavedData.Companion.enabledTDM(owner))return true;
        return !owner.isAlliedTo(defender);
    }
    public static boolean threatening(AutoAimableEntity tower,Entity missile,double size,Vec3 origin){
        return Double.isFinite(size)&&size>=0&&!missile.onGround()
                &&(missile.getBbWidth()>=size||missile.getBbHeight()>=size)
                &&hostile(tower,missile)&&tower.checkNoClip(missile,origin);
    }
}

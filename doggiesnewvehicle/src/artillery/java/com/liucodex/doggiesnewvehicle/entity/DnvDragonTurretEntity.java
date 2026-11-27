package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Five additional authored turrets share native weapons and the existing remote-crew owner. */
public final class DnvDragonTurretEntity extends DnvM1YunlongEntity {
    private final DnvDragonTurretProfile profile;
    private final DnvTurretDefinition definition;
    public DnvDragonTurretEntity(EntityType<? extends DnvDragonTurretEntity> type,Level level) {
        super(type,level);
        profile=DnvDragonTurretProfile.forEntity(EntityType.getKey(type).getPath());
        if(profile.seats==0)servoEnergy.configureCost(profile.servo);
        definition=new DnvTurretDefinition("doggievehicle:"+profile.entity,"doggiesnewvehicle:"+profile.entity+"_manual",
                Map.of("vehicle",Set.of("Main")),profile.seats==0,Optional.empty());
        setCustomName(Component.translatable("entity.doggievehicle."+profile.entity));
    }
    @Override public DnvDragonTurretProfile profile(){return profile==null?DnvDragonTurretProfile.M1:profile;}
    @Override protected boolean mountedCrew(){return profile().seats>0;}
    @Override public DnvTurretDefinition remoteManualDefinition(){return definition;}
    @Override public boolean canBindIndicator(Player player){return !mountedCrew()&&super.canBindIndicator(player);}
    @Override public float ordinaryArmorReduction(){return mountedCrew()?.10F:.20F;}
    @Override public float directPercent(String weapon){
        if (!"Main".equals(weapon)) return 0;
        var gun = getGunData(weapon);
        if (mountedCrew() && gun != null && "HE".equals(gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.SHELL_TYPE))) return 0;
        return com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction(profile().entity);
    }
}

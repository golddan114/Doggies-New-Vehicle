package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.atsuishio.superbwarfare.resource.model.ProjectileModelReloadListener;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.liucodex.doggiesnewvehicle.api.combat.DnvUpperAirspaceProjectile;
import com.liucodex.doggiesnewvehicle.compat.DnvVlsFlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Native Javelin with fixed-slot launch, bounded interception and the player's current target policy. */
public final class DnvApocalypseMissile extends JavelinMissileEntity implements DnvUpperAirspaceProjectile {
    private static final String ALLIED_SOURCE = "DnvAlliedApocalypseSource";
    public DnvApocalypseMissile(EntityType<? extends DnvApocalypseMissile> type, Level level) { super(type, level); }

    void markAllied(DnvAlliedApocalypseEntity source) { getPersistentData().putUUID(ALLIED_SOURCE, source.getUUID()); }

    @Override public void tick() {
        getPersistentData().putBoolean(DnvVlsFlight.OWNED, true);
        if (level() instanceof ServerLevel server && !isDistracted()) {
            Entity target = EntityFindUtil.findEntity(level(), getTargetUUID());
            Entity owner = getOwner();
            if (target != null && server.getEntity(target.getUUID()) != target) target = null;
            Entity source = owner == null ? this : owner.getRootVehicle();
            boolean valid;
            if (getPersistentData().hasUUID(ALLIED_SOURCE)) {
                Entity alliedSource = server.getEntity(getPersistentData().getUUID(ALLIED_SOURCE));
                valid = alliedSource instanceof DnvAlliedApocalypseEntity tank && tank.autonomousReady()
                        && ApocalypseAlliedTargets.eligible(tank, target);
            } else valid = ApocalypseTargeting.eligible(source, owner, target);
            if (!valid) setTargetUUID("none");
        }
        setTop(false);
        getPersistentData().putBoolean(DnvVlsFlight.ACTIVE, true);
        super.m_8119_();
    }
    @Override
    public BakedModelInstance getModelInstance() {
        var id = ResourceLocation.fromNamespaceAndPath("superbwarfare", "models/bedrock/projectile/javelin_missile.geo.json");
        var model = ProjectileModelReloadListener.INSTANCE.getModel(id);
        return model == null ? null : model.createInstance();
    }

    @Override public boolean forceLoadChunk() { return false; }
    @Override public int getFlareHiddenTicks() { return 3; }
    @Override public boolean admitsUpperAirspaceCollision() { return getOwner() instanceof Player; }
}

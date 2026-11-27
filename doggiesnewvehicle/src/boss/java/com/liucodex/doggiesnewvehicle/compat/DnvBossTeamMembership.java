package com.liucodex.doggiesnewvehicle.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One vanilla-scoreboard writer for newly created DNV bosses. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvBossTeamMembership {
    public static final String DEFAULT_TEAM = DnvDefaultTeams.HELLDIVERS;
    private static final String MANAGED_KEY =
            "doggiesnewvehicle.boss_team_managed";
    private static final String ASSIGNED_TEAM_KEY =
            "doggiesnewvehicle.boss_team_assigned_name";
    private static final String INITIALIZED_KEY = "doggiesnewvehicle.boss_team_initialized";

    private DnvBossTeamMembership() {
    }

    public static boolean assignIfUnassigned(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getTeam() != null) {
            return false;
        }
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = DnvDefaultTeams.getOrCreate(scoreboard,
                isGuardian(entity) ? DnvDefaultTeams.GUARDIANS : DEFAULT_TEAM);
        return joinManagedTeam(entity, team);
    }

    private static boolean isGuardian(Entity entity) {
        // Stable registry/save identity keeps this relation adapter independent of a concrete boss implementation.
        return "doggievehicle:silent_heaven".equals(entity.getEncodeId());
    }

    /** First server tick also covers command spawns, after a summoner has had time to assign its team. */
    public static void initializeIfNeeded(Entity entity) {
        if (!(entity.level() instanceof ServerLevel) || entity.getPersistentData().getBoolean(INITIALIZED_KEY)) return;
        if (!entity.getPersistentData().getBoolean(MANAGED_KEY)) assignIfUnassigned(entity);
        entity.getPersistentData().putBoolean(INITIALIZED_KEY, true);
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        reconcileManagedDefault(event.getEntity());
    }

    /** Only the old, still-managed default changes; cleared/reassigned/custom teams are untouched. */
    public static void reconcileManagedDefault(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !(entity.getTeam() instanceof PlayerTeam team)
                || !entity.getPersistentData().getBoolean(MANAGED_KEY)
                || !DEFAULT_TEAM.equals(team.getName())
                || !DEFAULT_TEAM.equals(entity.getPersistentData().getString(ASSIGNED_TEAM_KEY))) return;
        if (isGuardian(entity))
            joinManagedTeam(entity, DnvDefaultTeams.getOrCreate(level.getScoreboard(), DnvDefaultTeams.GUARDIANS));
        else DnvDefaultTeams.getOrCreate(level.getScoreboard(), DEFAULT_TEAM);
    }

    /** Copies the summoner's actual vanilla team after the child is in the world. */
    public static boolean inheritIfUnassigned(Entity child, PlayerTeam team) {
        if (!(child.level() instanceof ServerLevel) || child.getTeam() != null) {
            return false;
        }
        return joinManagedTeam(child, team);
    }

    private static boolean joinManagedTeam(Entity entity, PlayerTeam team) {
        entity.level().getScoreboard().addPlayerToTeam(entity.getScoreboardName(), team);
        if (entity.getTeam() != team) {
            return false;
        }
        entity.getPersistentData().putBoolean(MANAGED_KEY, true);
        entity.getPersistentData().putString(ASSIGNED_TEAM_KEY, team.getName());
        return true;
    }

    public static void releaseOnTerminalRemoval(
            Entity entity,
            Entity.RemovalReason reason) {
        if (reason==null || !(entity.level() instanceof ServerLevel level)
                || !entity.getPersistentData().getBoolean(MANAGED_KEY)
                || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        if (entity.getTeam() instanceof PlayerTeam team
                && entity.getPersistentData().getString(ASSIGNED_TEAM_KEY)
                        .equals(team.getName())) {
            level.getScoreboard().removePlayerFromTeam(entity.getScoreboardName(), team);
        }
        entity.getPersistentData().remove(MANAGED_KEY);
        entity.getPersistentData().remove(ASSIGNED_TEAM_KEY);
    }
}

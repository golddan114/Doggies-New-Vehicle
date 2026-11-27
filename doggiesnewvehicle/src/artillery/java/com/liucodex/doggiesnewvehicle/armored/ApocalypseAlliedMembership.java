package com.liucodex.doggiesnewvehicle.armored;

import com.liucodex.doggiesnewvehicle.compat.DnvDefaultTeams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** One-time vanilla team assignment; never repairs over an administrator's later team choice. */
final class ApocalypseAlliedMembership {
    static final String DEFAULT_TEAM = DnvDefaultTeams.PLAYERS;
    private boolean initialized;
    private String requested = DEFAULT_TEAM;
    private String managed = "";

    void request(String name) { if (!initialized) requested = name == null ? DEFAULT_TEAM : name; }

    void added(Entity entity) {
        if (initialized || !(entity.level() instanceof ServerLevel level)) return;
        if (entity.getTeam() == null) {
            var scoreboard = level.getScoreboard();
            var team = scoreboard.getPlayerTeam(requested);
            // A deployment team removed before insertion must not be silently re-created.
            if (team == null) {
                team = DnvDefaultTeams.getOrCreate(scoreboard, DEFAULT_TEAM);
            }
            if (scoreboard.addPlayerToTeam(entity.getScoreboardName(), team)) managed = team.getName();
        }
        initialized = true;
    }

    boolean initialized() { return initialized; }

    void removed(Entity entity, Entity.RemovalReason reason) {
        if (!(entity.level() instanceof ServerLevel level) || !reason.shouldDestroy()) return;
        var team = level.getScoreboard().getPlayersTeam(entity.getScoreboardName());
        if (team != null && managed.equals(team.getName()))
            level.getScoreboard().removePlayerFromTeam(entity.getScoreboardName(), team);
    }

    void save(CompoundTag tag) {
        tag.putBoolean("TeamInitialized", initialized);
        tag.putString("RequestedTeam", requested);
        tag.putString("ManagedTeam", managed);
    }

    void load(CompoundTag tag) {
        initialized = tag.getBoolean("TeamInitialized");
        requested = tag.contains("RequestedTeam") ? tag.getString("RequestedTeam") : DEFAULT_TEAM;
        managed = tag.getString("ManagedTeam");
    }
}

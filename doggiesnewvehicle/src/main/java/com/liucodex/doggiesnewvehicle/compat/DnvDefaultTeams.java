package com.liucodex.doggiesnewvehicle.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Default identities only. Vanilla scoreboard membership remains the sole relation authority. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvDefaultTeams {
    public static final String PLAYERS = "dnv_allied", HELLDIVERS = "dnv_boss", GUARDIANS = "dnv_guardians";
    private static final String PLAYER_INITIALIZED = "doggiesnewvehicle.default_player_team_initialized";
    private DnvDefaultTeams() { }

    public static PlayerTeam getOrCreate(Scoreboard scoreboard, String name) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) team = scoreboard.addPlayerTeam(name);
        // Preserve an administrator's custom display name as well as actual membership.
        if (team.getDisplayName().getString().equals(name)) {
            String key = switch (name) {
                case PLAYERS -> "players";
                case HELLDIVERS -> "helldivers";
                case GUARDIANS -> "guardians";
                default -> null;
            };
            if (key != null) team.setDisplayName(Component.translatable("team.doggiesnewvehicle." + key));
        }
        return team;
    }

    public static void initializePlayer(Player player) {
        if (!(player.level() instanceof ServerLevel level) || player.getPersistentData().getBoolean(PLAYER_INITIALIZED)) return;
        if (player.getTeam() == null) {
            var team = getOrCreate(level.getScoreboard(), PLAYERS);
            if (!level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team)) return;
        }
        player.getPersistentData().putBoolean(PLAYER_INITIALIZED, true);
    }

    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        initializePlayer(event.getEntity());
    }

    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(PLAYER_INITIALIZED))
            event.getEntity().getPersistentData().putBoolean(PLAYER_INITIALIZED, true);
    }
}

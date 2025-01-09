package com.starkrealm_essential;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import java.util.HashSet;
import java.util.Set;

public class SkipNightVoteCommand {
    private static boolean voteInProgress = false;
    private static Set<ServerPlayerEntity> votedPlayers = new HashSet<>();
    private static long voteStartTime;
    private static final long VOTE_DURATION = 30000; // 30 seconds in milliseconds

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("skipnight")
                .executes(context -> executeSkipNightVote(context.getSource())));
        });
    }

    private static int executeSkipNightVote(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("This command can only be used by players"), false);
            return 0;
        }

        // Check if it's night or thundering
        long timeOfDay = source.getWorld().getTimeOfDay() % 24000;
        boolean isNightOrStorming = (timeOfDay >= 13000 && timeOfDay <= 23000) || source.getWorld().isThundering();

        if (!isNightOrStorming) {
            source.sendFeedback(() -> Text.literal("You can only start a vote during night or thunderstorms"), false);
            return 0;
        }

        // Check if a vote is already in progress
        if (voteInProgress) {
            // Check if this player has already voted
            if (votedPlayers.contains(player)) {
                source.sendFeedback(() -> Text.literal("You have already voted!"), false);
                return 0;
            }

            // Add player's vote
            votedPlayers.add(player);
            
            // Calculate voting results
            int totalPlayers = source.getServer().getCurrentPlayerCount();
            int votesNeeded = (int) Math.ceil(totalPlayers * 0.5);
            int currentVotes = votedPlayers.size();

            // Broadcast vote status
            source.getServer().getPlayerManager().broadcast(
                Text.literal(String.format("%s voted to skip! (%d/%d votes needed)", 
                    player.getName().getString(), currentVotes, votesNeeded)), false);

            // Check if vote passed
            if (currentVotes >= votesNeeded) {
                // Skip night/clear weather
                source.getWorld().setTimeOfDay((timeOfDay + 24000 - timeOfDay % 24000) % 24000);
                if (source.getWorld().isThundering()) {
                    source.getWorld().setWeather(0, 6000, false, false);
                }
                
                // Broadcast success message
                source.getServer().getPlayerManager().broadcast(
                    Text.literal("Vote passed! Skipping to day time..."), false);
                
                // Reset voting state
                resetVote();
            }
        } else {
            // Start new vote
            voteInProgress = true;
            votedPlayers.clear();
            votedPlayers.add(player);
            voteStartTime = System.currentTimeMillis();

            // Broadcast vote start
            source.getServer().getPlayerManager().broadcast(
                Text.literal(String.format("%s started a vote to skip night/storm! Use /skipnight to vote", 
                    player.getName().getString())), true);
            
            // Start vote timeout thread
            new Thread(() -> {
                try {
                    Thread.sleep(VOTE_DURATION);
                    if (voteInProgress) {
                        source.getServer().getPlayerManager().broadcast(
                            Text.literal("Vote timed out! Not enough votes to skip."), false);
                        resetVote();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        return 1;
    }

    private static void resetVote() {
        voteInProgress = false;
        votedPlayers.clear();
    }
}
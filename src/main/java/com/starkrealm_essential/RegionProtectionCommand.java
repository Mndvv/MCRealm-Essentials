package com.starkrealm_essential;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegionProtectionCommand {
    private static final Map<UUID, BlockPos[]> playerSelections = new HashMap<>();
    private static final Map<String, Box> protectedRegions = new HashMap<>();
    private static final Map<UUID, String> lastRegionNotification = new HashMap<>();
    private static final Map<UUID, Boolean> playerInRegion = new HashMap<>();
    private static Connection connection;

    public static void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:config/protected_regions.db");
            
            // Create regions table if it doesn't exist
            Statement stmt = connection.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS regions (
                    name TEXT PRIMARY KEY,
                    x1 INTEGER,
                    y1 INTEGER,
                    z1 INTEGER,
                    x2 INTEGER,
                    y2 INTEGER,
                    z2 INTEGER
                )
            """);
            stmt.close();
            
            // Load existing regions
            loadRegions();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadRegions() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM regions");
            
            while (rs.next()) {
                String name = rs.getString("name");
                Box region = new Box(
                    rs.getInt("x1"),
                    rs.getInt("y1"),
                    rs.getInt("z1"),
                    rs.getInt("x2") + 1,
                    rs.getInt("y2") + 1,
                    rs.getInt("z2") + 1
                );
                protectedRegions.put(name, region);
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register pos1 command
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("pos1")
                .executes(context -> setPosition(context, 0)));

            // Register pos2 command
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("pos2")
                .executes(context -> setPosition(context, 1)));

            // Register protect command with name argument
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("protect")
                .requires(source -> source.hasPermissionLevel(2))
                .then(net.minecraft.server.command.CommandManager.argument("name", StringArgumentType.word())
                    .executes(RegionProtectionCommand::createProtectedRegion)));

            // Register remove command
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("unprotect")
                .requires(source -> source.hasPermissionLevel(2))
                .then(net.minecraft.server.command.CommandManager.argument("name", StringArgumentType.word())
                    .executes(RegionProtectionCommand::removeProtectedRegion)));

            // Register list command
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("regions")
                .executes(RegionProtectionCommand::listRegions));
        });
    }

    private static int setPosition(CommandContext<ServerCommandSource> context, int index) {
        ServerCommandSource source = context.getSource();
        // Fix: Correctly convert player position to BlockPos
        BlockPos pos = new BlockPos(
            (int)Math.floor(source.getPosition().x),
            (int)Math.floor(source.getPosition().y),
            (int)Math.floor(source.getPosition().z)
        );
        UUID playerUuid = source.getPlayer().getUuid();

        BlockPos[] positions = playerSelections.computeIfAbsent(playerUuid, k -> new BlockPos[2]);
        positions[index] = pos;

        source.sendMessage(Text.literal("Position " + (index + 1) + " set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return Command.SINGLE_SUCCESS;
    }

    private static int createProtectedRegion(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        UUID playerUuid = source.getPlayer().getUuid();
        String name = StringArgumentType.getString(context, "name");
        
        if (protectedRegions.containsKey(name)) {
            source.sendMessage(Text.literal("A region with that name already exists!"));
            return 0;
        }

        BlockPos[] positions = playerSelections.get(playerUuid);
        if (positions == null || positions[0] == null || positions[1] == null) {
            source.sendMessage(Text.literal("Please set both positions first using /pos1 and /pos2"));
            return 0;
        }

        Box region = new Box(
            Math.min(positions[0].getX(), positions[1].getX()),
            Math.min(positions[0].getY(), positions[1].getY()),
            Math.min(positions[0].getZ(), positions[1].getZ()),
            Math.max(positions[0].getX(), positions[1].getX()) + 1,
            Math.max(positions[0].getY(), positions[1].getY()) + 1,
            Math.max(positions[0].getZ(), positions[1].getZ()) + 1
        );

        try {
            PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO regions (name, x1, y1, z1, x2, y2, z2) VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            pstmt.setString(1, name);
            pstmt.setInt(2, (int) region.minX);
            pstmt.setInt(3, (int) region.minY);
            pstmt.setInt(4, (int) region.minZ);
            pstmt.setInt(5, (int) (region.maxX - 1));
            pstmt.setInt(6, (int) (region.maxY - 1));
            pstmt.setInt(7, (int) (region.maxZ - 1));
            pstmt.executeUpdate();
            pstmt.close();

            protectedRegions.put(name, region);
            source.sendMessage(Text.literal("Protected region created: " + name));
        } catch (SQLException e) {
            e.printStackTrace();
            source.sendMessage(Text.literal("Failed to create region due to database error"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int removeProtectedRegion(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "name");

        if (!protectedRegions.containsKey(name)) {
            source.sendMessage(Text.literal("Region not found: " + name));
            return 0;
        }

        try {
            PreparedStatement pstmt = connection.prepareStatement("DELETE FROM regions WHERE name = ?");
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            pstmt.close();

            protectedRegions.remove(name);
            source.sendMessage(Text.literal("Region removed: " + name));
        } catch (SQLException e) {
            e.printStackTrace();
            source.sendMessage(Text.literal("Failed to remove region due to database error"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int listRegions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (protectedRegions.isEmpty()) {
            source.sendMessage(Text.literal("No protected regions exist"));
        } else {
            source.sendMessage(Text.literal("Protected regions:"));
            protectedRegions.keySet().forEach(name -> 
                source.sendMessage(Text.literal("- " + name))
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    // Check if a position is within any protected region and return the region name
    public static String getProtectedRegionAt(BlockPos pos) {
        for (Map.Entry<String, Box> entry : protectedRegions.entrySet()) {
            if (entry.getValue().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Check if a player should be notified about entering a region
    public static boolean shouldNotifyPlayer(UUID playerUuid, String regionName) {
        String lastRegion = lastRegionNotification.get(playerUuid);
        if (!regionName.equals(lastRegion)) {
            lastRegionNotification.put(playerUuid, regionName);
            return true;
        }
        return false;
    }

    // AREA EFFECT
    public static void applyRegionEffects(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        BlockPos playerPos = player.getBlockPos();
        String regionName = getProtectedRegionAt(playerPos);
        
        boolean wasInRegion = playerInRegion.getOrDefault(playerUuid, false);
        boolean isInRegion = regionName != null;
        
        if (isInRegion) {
            if (!wasInRegion) {
                // Player just entered the region
                player.sendMessage(Text.literal("Entering protected area..."), true); // true makes it appear in action bar
            }
            // Apply regeneration effect
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 210, 0, false, false));
            playerInRegion.put(playerUuid, true);
        } else if (wasInRegion) {
            // Player just left the region
            player.sendMessage(Text.literal("Leaving protected area..."), true);
            playerInRegion.put(playerUuid, false);
        }
    }
}
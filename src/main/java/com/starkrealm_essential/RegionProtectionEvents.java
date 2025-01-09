package com.starkrealm_essential;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
// import net.minecraft.util.math.BlockPos;
// import net.fabricmc.fabric.api.event.Event.ExplosionCallback;
// import net.minecraft.world.explosion.Explosion;
// import java.util.ArrayList;
// import java.util.Iterator;
// import java.util.List;

public class RegionProtectionEvents {
    public static void register() {
        // Register block break prevention
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity && !player.hasPermissionLevel(2)) {
                if (RegionProtectionCommand.getProtectedRegionAt(pos) != null) {
                    player.sendMessage(Text.literal("This block is protected!"), true);
                    return false;
                }
            }
            return true;
        });

        // Register block place prevention
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity && !player.hasPermissionLevel(2)) {
                if (RegionProtectionCommand.getProtectedRegionAt(hitResult.getBlockPos()) != null) {
                    player.sendMessage(Text.literal("This area is protected!"), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Register explosion protection
        // ExplosionCallback.EVENT.register((world, explosion, list) -> {
        //     // Remove any blocks that are in protected regions from the explosion
        //     Iterator<BlockPos> iterator = list.iterator();
        //     while (iterator.hasNext()) {
        //         BlockPos pos = iterator.next();
        //         if (RegionProtectionCommand.getProtectedRegionAt(pos) != null) {
        //             iterator.remove();
        //         }
        //     }
        //     return list;
        // });

        // Register player position checking for effects
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    RegionProtectionCommand.applyRegionEffects(player);
                }
            }
        });
    }
}
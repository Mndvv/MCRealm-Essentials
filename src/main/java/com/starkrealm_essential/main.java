package com.starkrealm_essential;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.function.Supplier;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.StringArgumentType;

public class main implements ModInitializer {
    public static final String MOD_ID = "starkrealm_essential";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private void registerTimePlaceholder() {
        // Your existing time placeholder code
        Placeholders.register(Identifier.of(MOD_ID, "time"), (ctx, arg) -> {
            ZonedDateTime time = ZonedDateTime.now(ZoneId.of("GMT+7"));
            
            String pattern = "HH:mm:ss";
            
            if (arg != null && !arg.isEmpty()) {
                pattern = arg;
            }
            
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return PlaceholderResult.value(Text.literal(time.format(formatter)));
            } catch (IllegalArgumentException e) {
                return PlaceholderResult.value(Text.literal("Invalid time format pattern"));
            }
        });
    }

    @Override
    public void onInitialize() {
        // Register the time placeholder
        registerTimePlaceholder();
        
        // Initialize region protection system
        RegionProtectionCommand.initializeDatabase();
        RegionProtectionCommand.register();
        RegionProtectionEvents.register();
        
        // Register existing greet command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("greet")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        ServerCommandSource source = context.getSource();
                        source.sendFeedback(() -> Text.literal("Hello, " + name + "!"), false);
                        return 1;
                    })));
        });
        
        LOGGER.info("StarkRealm-Essentials loaded with region protection.");
    }
}
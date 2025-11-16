package com.example.worldmanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Command handler for world management operations.
 * Provides a user-friendly interface for world creation, switching, and management.
 */
public class WorldCommand {
    
    private final WorldManager worldManager;
    private final ProxyServer server;
    private final Logger logger;
    
    public WorldCommand(WorldManager worldManager, ProxyServer server, Logger logger) {
        this.worldManager = worldManager;
        this.server = server;
        this.logger = logger;
    }
    
    public BrigadierCommand createCommand() {
        return new BrigadierCommand(
            LiteralArgumentBuilder.<CommandSource>literal("world")
                // /world create <name> [type]
                .then(LiteralArgumentBuilder.<CommandSource>literal("create")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("type", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                Arrays.stream(WorldType.values())
                                    .forEach(type -> builder.suggest(type.toString()));
                                return builder.buildFuture();
                            })
                            .executes(context -> createWorld(
                                context.getSource(),
                                StringArgumentType.getString(context, "name"),
                                StringArgumentType.getString(context, "type")
                            )))
                        .executes(context -> createWorld(
                            context.getSource(),
                            StringArgumentType.getString(context, "name"),
                            "survival" // default
                        ))))
                
                // /world switch <name>
                .then(LiteralArgumentBuilder.<CommandSource>literal("switch")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            worldManager.listWorlds().forEach(world -> 
                                builder.suggest(world.getName()));
                            return builder.buildFuture();
                        })
                        .executes(context -> switchWorld(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))))
                
                // /world fork <source> <new-name>
                .then(LiteralArgumentBuilder.<CommandSource>literal("fork")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("source", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            worldManager.listWorlds().forEach(world -> 
                                builder.suggest(world.getName()));
                            return builder.buildFuture();
                        })
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("new-name", StringArgumentType.word())
                            .executes(context -> forkWorld(
                                context.getSource(),
                                StringArgumentType.getString(context, "source"),
                                StringArgumentType.getString(context, "new-name")
                            )))))
                
                // /world suspend <name>
                .then(LiteralArgumentBuilder.<CommandSource>literal("suspend")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            worldManager.listWorlds().stream()
                                .filter(world -> world.getStatus() == WorldInfo.Status.RUNNING)
                                .forEach(world -> builder.suggest(world.getName()));
                            return builder.buildFuture();
                        })
                        .executes(context -> suspendWorld(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))))
                
                // /world resume <name>
                .then(LiteralArgumentBuilder.<CommandSource>literal("resume")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            worldManager.listWorlds().stream()
                                .filter(world -> world.getStatus() == WorldInfo.Status.SUSPENDED)
                                .forEach(world -> builder.suggest(world.getName()));
                            return builder.buildFuture();
                        })
                        .executes(context -> resumeWorld(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))))
                
                // /world list
                .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                    .executes(context -> listWorlds(context.getSource())))
                
                // /world info <name>
                .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            worldManager.listWorlds().forEach(world -> 
                                builder.suggest(world.getName()));
                            return builder.buildFuture();
                        })
                        .executes(context -> showWorldInfo(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))))
                
                // Default: show help
                .executes(context -> showHelp(context.getSource()))
        );
    }
    
    private int createWorld(CommandSource source, String name, String typeStr) {
        if (worldManager.worldExists(name)) {
            source.sendMessage(Component.text("World '" + name + "' already exists!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        WorldType type;
        try {
            type = WorldType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendMessage(Component.text("Invalid world type: " + typeStr)
                .color(NamedTextColor.RED));
            source.sendMessage(Component.text("Available types: " + Arrays.toString(WorldType.values()))
                .color(NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        
        source.sendMessage(Component.text("Creating world '" + name + "' of type " + type + "...")
            .color(NamedTextColor.YELLOW));
        
        worldManager.createWorld(name, type)
            .thenAccept(world -> {
                source.sendMessage(Component.text("Successfully created world: " + world.getName())
                    .color(NamedTextColor.GREEN));
                logger.info("World created by {}: {}", getSourceName(source), world);
            })
            .exceptionally(throwable -> {
                source.sendMessage(Component.text("Failed to create world: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
                logger.error("Failed to create world {} requested by {}", name, getSourceName(source), throwable);
                return null;
            });
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int switchWorld(CommandSource source, String worldName) {
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("Only players can switch worlds!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        Player player = (Player) source;
        
        if (!worldManager.worldExists(worldName)) {
            player.sendMessage(Component.text("World '" + worldName + "' not found!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        if (worldManager.isWorldSuspended(worldName)) {
            player.sendMessage(Component.text("World '" + worldName + "' is suspended. Use /world resume " + worldName + " first.")
                .color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        Optional<RegisteredServer> targetServer = server.getServer(worldName);
        if (targetServer.isEmpty()) {
            player.sendMessage(Component.text("World '" + worldName + "' is not available right now.")
                .color(NamedTextColor.RED));
            
            // Debug: List all available servers
            logger.warn("Server '{}' not found. Available servers: {}", worldName, 
                server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .collect(java.util.stream.Collectors.toList()));
            
            // Also check if the world exists in our world manager
            Optional<WorldInfo> worldInfo = worldManager.getWorld(worldName);
            if (worldInfo.isPresent()) {
                logger.warn("World '{}' exists in WorldManager but not in Velocity: {}", 
                    worldName, worldInfo.get());
            }
            
            return Command.SINGLE_SUCCESS;
        }
        
        player.sendMessage(Component.text("Switching to world: " + worldName + "...")
            .color(NamedTextColor.YELLOW));
        
        player.createConnectionRequest(targetServer.get()).connect()
            .thenAccept(result -> {
                if (result.isSuccessful()) {
                    logger.info("Player {} switched to world {}", player.getUsername(), worldName);
                } else {
                    player.sendMessage(Component.text("Failed to switch to world: " + 
                        result.getReasonComponent().orElse(Component.text("Unknown error")))
                        .color(NamedTextColor.RED));
                }
            });
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int forkWorld(CommandSource source, String sourceWorld, String newWorldName) {
        if (!worldManager.worldExists(sourceWorld)) {
            source.sendMessage(Component.text("Source world '" + sourceWorld + "' not found!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        if (worldManager.worldExists(newWorldName)) {
            source.sendMessage(Component.text("World '" + newWorldName + "' already exists!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        source.sendMessage(Component.text("Forking world '" + sourceWorld + "' to '" + newWorldName + "'...")
            .color(NamedTextColor.YELLOW));
        
        worldManager.forkWorld(sourceWorld, newWorldName)
            .thenAccept(world -> {
                source.sendMessage(Component.text("Successfully forked world: " + world.getName())
                    .color(NamedTextColor.GREEN));
                logger.info("World forked by {}: {} -> {}", getSourceName(source), sourceWorld, newWorldName);
            })
            .exceptionally(throwable -> {
                source.sendMessage(Component.text("Failed to fork world: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
                return null;
            });
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int suspendWorld(CommandSource source, String worldName) {
        if (!worldManager.worldExists(worldName)) {
            source.sendMessage(Component.text("World '" + worldName + "' not found!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        source.sendMessage(Component.text("Suspending world: " + worldName + "...")
            .color(NamedTextColor.YELLOW));
        
        worldManager.suspendWorld(worldName)
            .thenRun(() -> {
                source.sendMessage(Component.text("Successfully suspended world: " + worldName)
                    .color(NamedTextColor.GREEN));
                logger.info("World suspended by {}: {}", getSourceName(source), worldName);
            })
            .exceptionally(throwable -> {
                source.sendMessage(Component.text("Failed to suspend world: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
                return null;
            });
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int resumeWorld(CommandSource source, String worldName) {
        if (!worldManager.worldExists(worldName)) {
            source.sendMessage(Component.text("World '" + worldName + "' not found!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        source.sendMessage(Component.text("Resuming world: " + worldName + "...")
            .color(NamedTextColor.YELLOW));
        
        worldManager.resumeWorld(worldName)
            .thenRun(() -> {
                source.sendMessage(Component.text("Successfully resumed world: " + worldName)
                    .color(NamedTextColor.GREEN));
                logger.info("World resumed by {}: {}", getSourceName(source), worldName);
            })
            .exceptionally(throwable -> {
                source.sendMessage(Component.text("Failed to resume world: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
                return null;
            });
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int listWorlds(CommandSource source) {
        Collection<WorldInfo> worlds = worldManager.listWorlds();
        
        if (worlds.isEmpty()) {
            source.sendMessage(Component.text("No worlds available. Use /world create <name> to create one!")
                .color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        source.sendMessage(Component.text("Available Worlds:")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        String currentWorld = null;
        if (source instanceof Player) {
            Player player = (Player) source;
            currentWorld = player.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .orElse(null);
        }
        
        for (WorldInfo world : worlds) {
            Component worldComponent = Component.text("• " + world.getName())
                .color(world.getName().equals(currentWorld) ? NamedTextColor.GREEN : NamedTextColor.WHITE);
            
            worldComponent = worldComponent.append(Component.text(" [" + world.getType() + "]")
                .color(NamedTextColor.GRAY));
            
            worldComponent = worldComponent.append(Component.text(" (" + world.getStatus() + ")")
                .color(getStatusColor(world.getStatus())));
            
            if (world.isForked()) {
                worldComponent = worldComponent.append(Component.text(" forked from " + world.getParentWorld())
                    .color(NamedTextColor.DARK_GRAY));
            }
            
            source.sendMessage(worldComponent);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int showWorldInfo(CommandSource source, String worldName) {
        Optional<WorldInfo> worldOpt = worldManager.getWorld(worldName);
        
        if (worldOpt.isEmpty()) {
            source.sendMessage(Component.text("World '" + worldName + "' not found!")
                .color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        
        WorldInfo world = worldOpt.get();
        
        source.sendMessage(Component.text("World Information: " + world.getName())
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        source.sendMessage(Component.text("ID: " + world.getId()).color(NamedTextColor.GRAY));
        source.sendMessage(Component.text("Type: " + world.getType()).color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("Status: " + world.getStatus()).color(getStatusColor(world.getStatus())));
        source.sendMessage(Component.text("Address: " + world.getConnectionString()).color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("Created: " + world.getCreatedAt()).color(NamedTextColor.GRAY));
        
        if (world.isForked()) {
            source.sendMessage(Component.text("Forked from: " + world.getParentWorld()).color(NamedTextColor.YELLOW));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private int showHelp(CommandSource source) {
        source.sendMessage(Component.text("World Management Commands:")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        source.sendMessage(Component.text("/world create <name> [type] - Create a new world")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world switch <name> - Switch to a world")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world fork <source> <new-name> - Fork an existing world")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world suspend <name> - Suspend a world to save resources")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world resume <name> - Resume a suspended world")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world list - List all worlds")
            .color(NamedTextColor.WHITE));
        source.sendMessage(Component.text("/world info <name> - Show world information")
            .color(NamedTextColor.WHITE));
        
        source.sendMessage(Component.text("Available world types: " + Arrays.toString(WorldType.values()))
            .color(NamedTextColor.GRAY));
        
        return Command.SINGLE_SUCCESS;
    }
    
    private NamedTextColor getStatusColor(WorldInfo.Status status) {
        switch (status) {
            case RUNNING: return NamedTextColor.GREEN;
            case SUSPENDED: return NamedTextColor.YELLOW;
            case CREATING: return NamedTextColor.BLUE;
            case STOPPING: return NamedTextColor.GOLD;
            case ERROR: return NamedTextColor.RED;
            default: return NamedTextColor.GRAY;
        }
    }
    
    private String getSourceName(CommandSource source) {
        if (source instanceof Player) {
            return ((Player) source).getUsername();
        }
        return "Console";
    }
}
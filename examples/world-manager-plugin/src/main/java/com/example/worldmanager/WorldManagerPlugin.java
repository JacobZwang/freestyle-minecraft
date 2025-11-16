package com.example.worldmanager;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Example plugin demonstrating how to use the VM abstraction system
 * to create a world management experience. This plugin allows players to:
 * 
 * - Create new worlds on-demand (/world create <name>)
 * - Switch between existing worlds (/world switch <name>)  
 * - Fork existing worlds to create copies (/world fork <source> <new-name>)
 * - Suspend worlds to save resources (/world suspend <name>)
 * - List available worlds (/world list)
 * 
 * This demonstrates how the VM abstraction makes it easy to build
 * Minecraft-focused experiences without dealing with VM infrastructure directly.
 */
@Plugin(
    id = "world-manager",
    name = "WorldManager",
    version = "1.0.0",
    description = "Example plugin for managing on-demand Minecraft worlds using VM abstraction",
    authors = {"Example"}
)
public class WorldManagerPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private WorldManager worldManager;

    @Inject
    public WorldManagerPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("WorldManager plugin has been initialized!");
        
        // Add a small delay to ensure Freestyle plugin is fully loaded
        server.getScheduler().buildTask(this, () -> {
            try {
                // Initialize our world manager (uses the VM abstraction under the hood)
                this.worldManager = new WorldManager(server, logger);
                
                // Register world management commands
                registerCommands();
                
                logger.info("WorldManager plugin successfully connected to Freestyle plugin");
            } catch (Exception e) {
                logger.error("Failed to initialize WorldManager: {}", e.getMessage());
                logger.error("Make sure the Freestyle plugin is installed and your API key is configured");
            }
        }).delay(1, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }
    
    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();
        
        // Register world command with subcommands
        WorldCommand worldCommand = new WorldCommand(worldManager, server, logger);
        CommandMeta worldMeta = commandManager.metaBuilder("world")
            .aliases("w", "worlds", "dimension")
            .build();
        commandManager.register(worldMeta, worldCommand.createCommand());
        
        logger.info("Registered world management commands: /world, /w, /worlds, /dimension");
    }
}
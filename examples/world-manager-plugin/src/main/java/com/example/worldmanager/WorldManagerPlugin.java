package com.example.worldmanager;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

@Plugin(
        id = "world-manager-plugin",
        name = "World Manager",
        version = "1.0.0",
        description = "A Minecraft world management plugin built on Freestyle VMs",
        authors = {"Example"},
        dependencies = {
            @Dependency(id = "freestyle-plugin")
        }
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
        logger.info("WorldManagerPlugin has been initialized!");

        // Initialize the world manager
        this.worldManager = new WorldManager(server, logger);

        logger.info("WorldManager initialized successfully!");
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public static WorldManager getWorldManagerInstance(ProxyServer server) {
        return server.getPluginManager()
                .getPlugin("world-manager-plugin")
                .flatMap(container -> container.getInstance())
                .filter(instance -> instance instanceof WorldManagerPlugin)
                .map(instance -> ((WorldManagerPlugin) instance).getWorldManager())
                .orElse(null);
    }
}

package com.example.worldmanager;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;



import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * WorldManager provides a Minecraft-focused API for managing worlds.
 * It abstracts over VM operations to provide world semantics:
 * - Creating worlds creates new VMs with fresh Minecraft servers
 * - Forking worlds clones existing VMs 
 * - Suspending worlds saves VM state
 * - Switching worlds connects players to different servers
 * 
 * This demonstrates how to build user-friendly abstractions on top
 * of the generic VM management system.
 */
public class WorldManager {
    
    private final ProxyServer server;
    private final Logger logger;
    private final Object freestyleVMService;
    private final Map<String, WorldInfo> worlds = new ConcurrentHashMap<>();
    private final Set<String> suspendedWorlds = ConcurrentHashMap.newKeySet();
    private final Map<String, RegisteredServer> activeRegisteredServers = new ConcurrentHashMap<>();
    
    public WorldManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.freestyleVMService = getFreestyleVMService();
        
        // Initialize with existing servers from velocity config
        initializeExistingWorlds();
    }
    
    private Object getFreestyleVMService() {
        try {
            // Get VM service from the Freestyle plugin
            Class<?> freestylePluginClass = Class.forName("com.example.velocityplugin.FreestylePlugin");
            Object vmService = freestylePluginClass.getMethod("getVMService").invoke(null);
            
            if (vmService == null) {
                throw new RuntimeException("Freestyle plugin service is null - check if API key is configured");
            }
            
            logger.info("Successfully connected to Freestyle plugin VM service");
            return vmService;
            
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Freestyle plugin not found - make sure freestyle-plugin.jar is installed", e);
        } catch (Exception e) {
            logger.error("Could not access Freestyle plugin VM service: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to Freestyle plugin: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates a new world by provisioning a VM and Minecraft server
     */
    public CompletableFuture<WorldInfo> createWorld(String worldName, WorldType type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Creating new world: {} of type {}", worldName, type);
                
                // Use the Freestyle VM service to create a new server
                Object vmManager = freestyleVMService.getClass().getMethod("getVMManager").invoke(freestyleVMService);
                Object serverInstance = vmManager.getClass().getMethod("createServer", String.class).invoke(vmManager, worldName);
                
                // Extract server details using reflection
                String serverId = (String) serverInstance.getClass().getMethod("getId").invoke(serverInstance);
                InetSocketAddress address = (InetSocketAddress) serverInstance.getClass().getMethod("getAddress").invoke(serverInstance);
                
                WorldInfo world = new WorldInfo(serverId, worldName, type, address, WorldInfo.Status.RUNNING);
                worlds.put(worldName, world);
                
                // Since createRawRegisteredServer doesn't work for dynamic servers,
                // let's try using registerServer instead
                ServerInfo serverInfo = new ServerInfo(worldName, address);
                try {
                    // Try the direct registerServer method instead
                    RegisteredServer registeredServer = server.registerServer(serverInfo);
                    activeRegisteredServers.put(worldName, registeredServer);
                    
                    logger.info("Successfully registered server with registerServer: {} -> {}", worldName, address);
                    
                    // Verify it worked
                    if (server.getServer(worldName).isPresent()) {
                        logger.info("✓ Server found in registry after registerServer: {}", worldName);
                    } else {
                        logger.warn("✗ Server still not found after registerServer: {}", worldName);
                    }
                    
                } catch (Exception e) {
                    logger.error("registerServer failed, falling back to createRawRegisteredServer: {}", e.getMessage());
                    
                    // Fallback to the original method
                    try {
                        RegisteredServer registeredServer = server.createRawRegisteredServer(serverInfo);
                        activeRegisteredServers.put(worldName, registeredServer);
                        logger.info("Fallback: used createRawRegisteredServer for: {} -> {}", worldName, address);
                    } catch (Exception e2) {
                        logger.error("Both registration methods failed: {} -> {}", worldName, address, e2);
                        throw new RuntimeException("Failed to register server: " + e2.getMessage(), e2);
                    }
                }
                
                logger.info("Successfully created world: {}", world);
                logger.info("Available servers after registration: {}", server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .collect(java.util.stream.Collectors.toList()));
                
                return world;
                
            } catch (Exception e) {
                logger.error("Failed to create world: {}", worldName, e);
                throw new RuntimeException("Failed to create world: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Forks an existing world to create a copy
     */
    public CompletableFuture<WorldInfo> forkWorld(String sourceWorldName, String newWorldName) {
        return CompletableFuture.supplyAsync(() -> {
            WorldInfo sourceWorld = worlds.get(sourceWorldName);
            if (sourceWorld == null) {
                throw new IllegalArgumentException("Source world not found: " + sourceWorldName);
            }
            
            try {
                logger.info("Forking world {} to create {}", sourceWorldName, newWorldName);
                
                // Use the Freestyle VM service to fork the server
                Object vmManager = freestyleVMService.getClass().getMethod("getVMManager").invoke(freestyleVMService);
                Object newServerInstance = vmManager.getClass().getMethod("forkServer", String.class, String.class)
                    .invoke(vmManager, sourceWorld.getId(), newWorldName);
                
                // Extract server details using reflection
                String newServerId = (String) newServerInstance.getClass().getMethod("getId").invoke(newServerInstance);
                InetSocketAddress newAddress = (InetSocketAddress) newServerInstance.getClass().getMethod("getAddress").invoke(newServerInstance);
                
                WorldInfo newWorld = new WorldInfo(newServerId, newWorldName, sourceWorld.getType(), newAddress, WorldInfo.Status.RUNNING);
                newWorld.setParentWorld(sourceWorldName);
                worlds.put(newWorldName, newWorld);
                
                // Register with Velocity using the same method that works for createWorld
                ServerInfo serverInfo = new ServerInfo(newWorldName, newAddress);
                try {
                    // Try the direct registerServer method instead
                    RegisteredServer registeredServer = server.registerServer(serverInfo);
                    activeRegisteredServers.put(newWorldName, registeredServer);
                    
                    logger.info("Successfully registered forked server with registerServer: {} -> {}", newWorldName, newAddress);
                    
                    // Verify it worked
                    if (server.getServer(newWorldName).isPresent()) {
                        logger.info("✓ Forked server found in registry: {}", newWorldName);
                    } else {
                        logger.warn("✗ Forked server still not found after registerServer: {}", newWorldName);
                    }
                    
                } catch (Exception e) {
                    logger.error("registerServer failed for forked world, falling back to createRawRegisteredServer: {}", e.getMessage());
                    
                    // Fallback to the original method
                    try {
                        RegisteredServer registeredServer = server.createRawRegisteredServer(serverInfo);
                        activeRegisteredServers.put(newWorldName, registeredServer);
                        logger.info("Fallback: used createRawRegisteredServer for forked world: {} -> {}", newWorldName, newAddress);
                    } catch (Exception e2) {
                        logger.error("Both registration methods failed for forked world: {} -> {}", newWorldName, newAddress, e2);
                        throw new RuntimeException("Failed to register forked server: " + e2.getMessage(), e2);
                    }
                }
                
                logger.info("Successfully forked world {} to {}", sourceWorldName, newWorldName);
                return newWorld;
                
            } catch (Exception e) {
                logger.error("Failed to fork world {} to {}", sourceWorldName, newWorldName, e);
                throw new RuntimeException("Failed to fork world: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Suspends a world to save resources
     */
    public CompletableFuture<Void> suspendWorld(String worldName) {
        return CompletableFuture.runAsync(() -> {
            WorldInfo world = worlds.get(worldName);
            if (world == null) {
                throw new IllegalArgumentException("World not found: " + worldName);
            }
            
            try {
                logger.info("Suspending world: {}", worldName);
                
                // Use the Freestyle VM service to suspend the server
                Object vmManager = freestyleVMService.getClass().getMethod("getVMManager").invoke(freestyleVMService);
                vmManager.getClass().getMethod("suspendServer", String.class).invoke(vmManager, world.getId());
                
                world.setStatus(WorldInfo.Status.SUSPENDED);
                suspendedWorlds.add(worldName);
                
                // Unregister from Velocity
                server.getServer(worldName).ifPresent(registeredServer -> {
                    server.unregisterServer(registeredServer.getServerInfo());
                });
                
                logger.info("Successfully suspended world: {}", worldName);
                
            } catch (Exception e) {
                logger.error("Failed to suspend world: {}", worldName, e);
                throw new RuntimeException("Failed to suspend world: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Resumes a suspended world
     */
    public CompletableFuture<Void> resumeWorld(String worldName) {
        return CompletableFuture.runAsync(() -> {
            WorldInfo world = worlds.get(worldName);
            if (world == null || !suspendedWorlds.contains(worldName)) {
                throw new IllegalArgumentException("Suspended world not found: " + worldName);
            }
            
            try {
                logger.info("Resuming world: {}", worldName);
                
                // Use the Freestyle VM service to resume the server
                Object vmManager = freestyleVMService.getClass().getMethod("getVMManager").invoke(freestyleVMService);
                vmManager.getClass().getMethod("resumeServer", String.class).invoke(vmManager, world.getId());
                
                world.setStatus(WorldInfo.Status.RUNNING); 
                suspendedWorlds.remove(worldName);
                
                // Re-register with Velocity
                ServerInfo serverInfo = new ServerInfo(worldName, world.getAddress());
                server.createRawRegisteredServer(serverInfo);
                
                logger.info("Successfully resumed world: {}", worldName);
                
            } catch (Exception e) {
                logger.error("Failed to resume world: {}", worldName, e);
                throw new RuntimeException("Failed to resume world: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Lists all available worlds
     */
    public Collection<WorldInfo> listWorlds() {
        return new ArrayList<>(worlds.values());
    }
    
    /**
     * Gets information about a specific world
     */
    public Optional<WorldInfo> getWorld(String worldName) {
        return Optional.ofNullable(worlds.get(worldName));
    }
    
    /**
     * Checks if a world exists
     */
    public boolean worldExists(String worldName) {
        return worlds.containsKey(worldName);
    }
    
    /**
     * Checks if a world is suspended
     */
    public boolean isWorldSuspended(String worldName) {
        return suspendedWorlds.contains(worldName);
    }
    
    private void initializeExistingWorlds() {
        // Initialize with any existing servers from velocity config
        server.getAllServers().forEach(registeredServer -> {
            String name = registeredServer.getServerInfo().getName();
            InetSocketAddress address = registeredServer.getServerInfo().getAddress();
            
            WorldInfo world = new WorldInfo(
                UUID.randomUUID().toString(),
                name,
                WorldType.SURVIVAL, // Default type for existing servers
                address,
                WorldInfo.Status.RUNNING
            );
            
            worlds.put(name, world);
            logger.info("Registered existing server as world: {}", name);
        });
    }
}
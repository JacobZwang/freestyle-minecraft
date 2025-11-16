package com.example.velocityplugin;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandshakeListener {

    private final ProxyServer server;
    private final Logger logger;
    
    // Store original virtual hosts for players
    private final Map<String, InetSocketAddress> playerVirtualHosts = new ConcurrentHashMap<>();

    public HandshakeListener(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        
        initializeHostnameMappings();
    }
    
    private void initializeHostnameMappings() {
        // Get all registered servers and log them
        for (RegisteredServer registeredServer : server.getAllServers()) {
            String serverName = registeredServer.getServerInfo().getName();
            String serverHost = registeredServer.getServerInfo().getAddress().getHostString();
            
            logger.info("Server '{}' is at address: {}", serverName, serverHost);
        }
        
        logger.info("Hostname mapping will be done dynamically based on target server");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        
        // Store the original virtual host for this player
        player.getCurrentServer().ifPresent(serverConnection -> {
            InetSocketAddress virtualHost = player.getRemoteAddress();
            playerVirtualHosts.put(player.getUsername(), virtualHost);
            logger.info("Stored virtual host for {}: {}", player.getUsername(), virtualHost);
        });
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        return EventTask.async(() -> {
            if (event.getResult().getServer().isEmpty()) {
                return;
            }

            Player player = event.getPlayer();
            RegisteredServer targetServer = event.getResult().getServer().get();
            String targetServerName = targetServer.getServerInfo().getName();
            String targetHostname = targetServer.getServerInfo().getAddress().getHostString();
            
            // Get the original virtual host from the player's connection
            InetSocketAddress originalVirtualHost = player.getVirtualHost().orElse(null);
            String originalHostname = originalVirtualHost != null ? originalVirtualHost.getHostString() : "unknown";
            
            logger.info("Player {} connecting to server '{}' at {} (original virtual host: {})", 
                player.getUsername(),
                targetServerName,
                targetHostname,
                originalHostname);
            
            // Always rewrite the hostname to match the target server's hostname
            // This ensures the backend server receives its own hostname instead of the proxy hostname
            if (!originalHostname.equals(targetHostname) && !originalHostname.equals("unknown")) {
                logger.info("Rewriting hostname from '{}' to '{}' for player {} connecting to server '{}'", 
                    originalHostname, targetHostname, player.getUsername(), targetServerName);
                
                // Try to modify the virtual host using reflection
                try {
                    boolean success = modifyVirtualHost(player, targetHostname, originalVirtualHost != null ? originalVirtualHost.getPort() : 25565);
                    if (success) {
                        logger.info("Successfully modified virtual host for {} to {}", player.getUsername(), targetHostname);
                    } else {
                        logger.warn("Failed to modify virtual host for player {} - reflection approach not working", player.getUsername());
                        logger.info("Backend server will receive original hostname: {}", originalHostname);
                        logger.info("Consider using Velocity's forced-hosts configuration as an alternative");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to modify virtual host for player {}: {}", player.getUsername(), e.getMessage());
                    logger.info("Hostname rewriting failed, backend server will receive original hostname: {}", originalHostname);
                }
            } else if (originalHostname.equals(targetHostname)) {
                logger.debug("Hostname already matches target server, no rewriting needed: {}", originalHostname);
            } else {
                logger.warn("Cannot rewrite unknown hostname for player {}", player.getUsername());
            }
        });
    }
    
    private boolean modifyVirtualHost(Player player, String newHostname, int port) throws Exception {
        // Based on maintainer advice: modify ConnectedPlayer's virtualHost and rawVirtualHost final fields
        try {
            // The player should be a ConnectedPlayer instance
            Object connectedPlayer = player;
            
            logger.debug("Player class: {}", connectedPlayer.getClass().getName());
            
            // Create the new virtual host address
            InetSocketAddress newVirtualHost = new InetSocketAddress(newHostname, port);
            
            boolean foundField = false;
            
            // Try to modify the virtualHost field
            try {
                Field virtualHostField = connectedPlayer.getClass().getDeclaredField("virtualHost");
                virtualHostField.setAccessible(true);
                virtualHostField.set(connectedPlayer, newVirtualHost);
                logger.info("Successfully modified virtualHost field to: {}", newVirtualHost);
                foundField = true;
            } catch (NoSuchFieldException e) {
                logger.debug("virtualHost field not found in class: {}", connectedPlayer.getClass().getName());
            }
            
            // Try to modify the rawVirtualHost field if it exists
            try {
                Field rawVirtualHostField = connectedPlayer.getClass().getDeclaredField("rawVirtualHost");
                rawVirtualHostField.setAccessible(true);
                
                // Set the new value (might need to be a string representation)
                if (rawVirtualHostField.getType() == String.class) {
                    rawVirtualHostField.set(connectedPlayer, newHostname + ":" + port);
                    logger.info("Successfully modified rawVirtualHost field to: {}:{}", newHostname, port);
                } else {
                    rawVirtualHostField.set(connectedPlayer, newVirtualHost);
                    logger.info("Successfully modified rawVirtualHost field to: {}", newVirtualHost);
                }
                foundField = true;
            } catch (NoSuchFieldException e) {
                logger.debug("rawVirtualHost field not found in class: {}", connectedPlayer.getClass().getName());
            }
            
            // Log all available fields for debugging if we didn't find what we're looking for
            if (!foundField) {
                logger.warn("Could not find virtualHost or rawVirtualHost fields");
                Field[] fields = connectedPlayer.getClass().getDeclaredFields();
                logger.debug("Available fields in {}:", connectedPlayer.getClass().getSimpleName());
                for (Field field : fields) {
                    logger.debug("  - {} ({})", field.getName(), field.getType().getSimpleName());
                }
            }
            
            return foundField;
            
        } catch (Exception e) {
            logger.error("Error modifying virtual host fields", e);
            throw e;
        }
    }
}
package com.example.velocityplugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class HostnameForwardingListener {

    private final ProxyServer server;
    private final Logger logger;
    private final Map<String, String> serverHostnameMap;

    public HostnameForwardingListener(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.serverHostnameMap = new HashMap<>();
        
        // Initialize server hostname mappings
        // This maps server names to the hostnames they should receive
        initializeServerHostnames();
    }

    private void initializeServerHostnames() {
        // Configure hostname mappings for each server
        // Format: serverHostnameMap.put("server-name", "hostname-to-send");
        
        // For your specific case where you want wfqgc.vm.freestyle.sh to receive wfqgc.vm.freestyle.sh:
        serverHostnameMap.put("lobby", "wfqgc.vm.freestyle.sh");
        
        // Get server information
        for (var registeredServer : server.getAllServers()) {
            String serverName = registeredServer.getServerInfo().getName();
            String serverHost = registeredServer.getServerInfo().getAddress().getHostString();
            
            logger.info("Server '{}' is at address: {}", serverName, serverHost);
            
            // Map each server to its own hostname
            serverHostnameMap.put(serverName, serverHost);
        }
        
        logger.info("Server hostname mappings configured: {}", serverHostnameMap);
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        // Log the original connection details
        InetSocketAddress virtualHost = event.getConnection().getVirtualHost().orElse(null);
        if (virtualHost != null) {
            logger.info("Player {} connecting with virtual host: {}:{}", 
                event.getUsername(), 
                virtualHost.getHostString(), 
                virtualHost.getPort());
        }
    }

    @Subscribe  
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getResult().getServer().isEmpty()) {
            return;
        }

        String serverName = event.getResult().getServer().get().getServerInfo().getName();
        String playerName = event.getPlayer().getUsername();
        
        // Check if we have a custom hostname configured for this server
        if (serverHostnameMap.containsKey(serverName)) {
            String targetHostname = serverHostnameMap.get(serverName);
            
            logger.info("Player {} connecting to server '{}' - should receive hostname '{}'", 
                playerName, serverName, targetHostname);
            
            // Note: The actual hostname modification would require implementing a custom
            // connection handler or using Velocity's internal APIs, which is complex.
            // 
            // A more practical approach is to:
            // 1. Configure your Paper servers to accept connections with any hostname
            // 2. Use server-specific configuration files
            // 3. Use Velocity's forced-hosts feature in velocity.toml
        } else {
            logger.debug("No hostname mapping configured for server '{}'", serverName);
        }
    }
}
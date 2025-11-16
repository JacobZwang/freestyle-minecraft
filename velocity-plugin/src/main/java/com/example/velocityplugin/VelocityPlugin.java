package com.example.velocityplugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(
    id = "velocity-plugin",
    name = "VelocityPlugin",
    version = "1.0.0",
    description = "A plugin to fix handshake hostname forwarding",
    authors = {"YourName"}
)
public class VelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("VelocityPlugin has been initialized!");
        
        // Register listeners
        server.getEventManager().register(this, new HandshakeListener(server, logger));
        server.getEventManager().register(this, new HostnameForwardingListener(server, logger));
        server.getEventManager().register(this, new PlayerListener(logger));
    }
}
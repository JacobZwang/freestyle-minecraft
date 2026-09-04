package com.example.velocityplugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.example.velocityplugin.vm.FreestyleVMManager;
import org.slf4j.Logger;

@Plugin(
        id = "freestyle-plugin",
        name = "Freestyle",
        version = "1.0.0",
        description = "On demand Minecraft server hosting powered by Freestyle VMs",
        authors = {"JacobZwang"}
)
public class FreestylePlugin {

    private final ProxyServer server;
    private final Logger logger;
    private static FreestyleVMManager vmManager;

    @Inject
    public FreestylePlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("FreestylePlugin has been initialized!");

        try {
            // Initialize the VM manager
            vmManager = new FreestyleVMManager(logger);

            // Register listeners
            server.getEventManager().register(this, new AddressRewriter(server, logger));

            logger.info("FreestylePlugin loaded successfully. VM management API available for other plugins.");
            logger.info("New servers will be forked from VM '{}'", vmManager.getConfig().getBaseVm());

        } catch (Exception e) {
            logger.error("Failed to initialize FreestylePlugin: {}", e.getMessage());
            logger.error("Set FREESTYLE_API_KEY in the environment or freestyle.api.key in "
                    + "freestyle-config.properties. Keys come from https://dash.freestyle.sh");
            // Don't throw - let the plugin load but mark manager as unavailable
            vmManager = null;
        }
    }

    /**
     * Get the VM manager instance. This can be called by other plugins.
     */
    public static FreestyleVMManager getVMManager() {
        return vmManager;
    }
}

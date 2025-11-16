package com.example.velocityplugin.vm;

/**
 * Service interface for accessing the Freestyle VM manager from other plugins.
 * This allows other plugins to get the configured VMManager instance.
 */
public interface FreestyleVMService {
    
    /**
     * Get the configured VM manager instance.
     * This will be either LocalVMManager for testing or FreestyleVMClient for production.
     */
    VMManager getVMManager();
    
    /**
     * Check if the service is configured to use the real Freestyle API
     */
    boolean isUsingFreestyleAPI();
}
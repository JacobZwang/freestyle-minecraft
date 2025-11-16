package com.example.velocityplugin.vm;

import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/**
 * Implementation of FreestyleVMService that loads configuration and provides
 * the appropriate VMManager instance.
 */
public class FreestyleVMServiceImpl implements FreestyleVMService {
    
    private final VMManager vmManager;
    private final boolean usingFreestyleAPI;
    private final Logger logger;
    
    public FreestyleVMServiceImpl(Logger logger) {
        this.logger = logger;
        
        try {
            // Load configuration and create Freestyle VM manager
            Properties config = loadConfiguration();
            String apiUrl = config.getProperty("freestyle.api.url", "https://api.freestyle.sh");
            String apiKey = config.getProperty("freestyle.api.key");
            
            if (apiKey == null || apiKey.equals("your-api-key-here") || apiKey.trim().isEmpty()) {
                logger.error("Freestyle API key is not configured!");
                logger.error("Please edit freestyle-config.properties and set your API key");
                throw new IllegalStateException("Freestyle API key must be configured in freestyle-config.properties");
            }
            
            logger.info("Configuring Freestyle VM Client with API: {}", apiUrl);
            this.vmManager = new FreestyleVMClient(URI.create(apiUrl), apiKey);
            this.usingFreestyleAPI = true;
            logger.info("Freestyle VM service initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Freestyle VM service: {}", e.getMessage());
            throw e;
        }
    }
    
    @Override
    public VMManager getVMManager() {
        return vmManager;
    }
    
    @Override
    public boolean isUsingFreestyleAPI() {
        return usingFreestyleAPI;
    }
    
    private Properties loadConfiguration() {
        Properties config = new Properties();
        
        // Try to load from server directory
        String configPath = "freestyle-config.properties";
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
            logger.info("Loaded Freestyle configuration from {}", configPath);
        } catch (IOException e) {
            logger.info("No configuration file found at {}, using defaults", configPath);
        }
        
        return config;
    }
}
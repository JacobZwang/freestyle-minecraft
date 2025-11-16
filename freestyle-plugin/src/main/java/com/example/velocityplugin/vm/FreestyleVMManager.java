package com.example.velocityplugin.vm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * Manages Freestyle VM operations for Minecraft servers.
 * Handles creating, suspending, and forking VMs through the Freestyle API.
 */
public class FreestyleVMManager {
    private final HttpClient http;
    private final URI apiBase;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    public FreestyleVMManager(Logger logger) {
        this.logger = logger;
        this.http = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        
        try {
            Properties config = loadConfiguration();
            String apiUrl = config.getProperty("freestyle.api.url", "https://api.freestyle.sh");
            String apiKey = config.getProperty("freestyle.api.key");
            
            if (apiKey == null || apiKey.equals("your-api-key-here") || apiKey.trim().isEmpty()) {
                logger.error("Freestyle API key is not configured!");
                logger.error("Please edit freestyle-config.properties and set your API key");
                throw new IllegalStateException("Freestyle API key must be configured in freestyle-config.properties");
            }
            
            this.apiBase = URI.create(apiUrl);
            this.apiKey = apiKey;
            
            logger.info("Freestyle VM Manager initialized with API: {}", apiUrl);
            
        } catch (Exception e) {
            logger.error("Failed to initialize Freestyle VM Manager: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create a new VM for a fresh Minecraft server by forking from the base VM "yrtby".
     */
    public ServerInstance createServer(String name) throws Exception {
        // Always fork from the base VM "yrtby" as requested
        return forkServer("yrtby", name);
    }

    /**
     * Suspend a running VM (save state).
     */
    public void suspendServer(String id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(apiBase.resolve("/v1/vms/" + id + "/shutdown"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
            
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Failed to suspend VM: " + resp.statusCode() + " " + resp.body());
        }
    }

    /**
     * Fork an existing suspended server to create a new VM copy.
     */
    public ServerInstance forkServer(String id, String newName) throws Exception {
        String requestBody = String.format("{\"name\":\"%s\"}", newName);
        
        HttpRequest req = HttpRequest.newBuilder(apiBase.resolve("/v1/vms/" + id + "/fork"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
            
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            JsonNode responseJson = objectMapper.readTree(resp.body());
            String vmId = responseJson.get("id").asText();
            
            JsonNode domainsNode = responseJson.get("domains");
            if (domainsNode != null && domainsNode.isArray() && domainsNode.size() > 0) {
                String firstDomain = domainsNode.get(0).asText();
                InetSocketAddress address = new InetSocketAddress(firstDomain, 25565);
                return new ServerInstance(vmId, newName, address);
            } else {
                throw new RuntimeException("No domains returned in fork response");
            }
        }
        
        throw new RuntimeException("Failed to fork VM: " + resp.statusCode() + " " + resp.body());
    }

    /**
     * Lookup a server instance by VM id.
     */
    public Optional<ServerInstance> getServer(String id) {
        try {
            HttpRequest req = HttpRequest.newBuilder(apiBase.resolve("/v1/vms/" + id))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
                
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode responseJson = objectMapper.readTree(resp.body());
                String vmId = responseJson.get("id").asText();
                String name = responseJson.get("name").asText();
                
                JsonNode domainsNode = responseJson.get("domains");
                if (domainsNode != null && domainsNode.isArray() && domainsNode.size() > 0) {
                    String firstDomain = domainsNode.get(0).asText();
                    InetSocketAddress address = new InetSocketAddress(firstDomain, 25565);
                    return Optional.of(new ServerInstance(vmId, name, address));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get server info for VM {}: {}", id, e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private Properties loadConfiguration() {
        Properties config = new Properties();
        
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
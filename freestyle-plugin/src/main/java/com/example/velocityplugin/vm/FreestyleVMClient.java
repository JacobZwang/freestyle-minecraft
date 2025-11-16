package com.example.velocityplugin.vm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Skeleton Freestyle VM client. This is intentionally lightweight — it demonstrates
 * how to plug a real provider and is NOT a full production implementation.
 *
 * To use in production wire up correct endpoints, authentication, and error handling.
 */
public class FreestyleVMClient implements VMManager {
    private final HttpClient http;
    private final URI apiBase;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public FreestyleVMClient(URI apiBase, String apiKey) {
        this.http = HttpClient.newHttpClient();
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ServerInstance createServer(String name) throws Exception {
        // Always fork from the base VM "yrtby" as requested
        return forkServer("yrtby", name);
    }

    @Override
    public void suspendServer(String id) throws Exception {
        // Suspend VM via Freestyle API
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

    @Override
    public ServerInstance forkServer(String id, String newName) throws Exception {
        // Fork VM via Freestyle API
        String requestBody = String.format("{\"name\":\"%s\"}", newName);
        
        HttpRequest req = HttpRequest.newBuilder(apiBase.resolve("/v1/vms/" + id + "/fork"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
            
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            // Parse the response JSON
            JsonNode responseJson = objectMapper.readTree(resp.body());
            String vmId = responseJson.get("id").asText();
            
            // Get the domains array and use the first one
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

    @Override
    public Optional<ServerInstance> get(String id) {
        try {
            // Get VM details via Freestyle API
            HttpRequest req = HttpRequest.newBuilder(apiBase.resolve("/v1/vms/" + id))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
                
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                // Parse the response JSON
                JsonNode responseJson = objectMapper.readTree(resp.body());
                String vmId = responseJson.get("id").asText();
                String name = responseJson.get("name").asText();
                
                // Get the domains array and use the first one
                JsonNode domainsNode = responseJson.get("domains");
                if (domainsNode != null && domainsNode.isArray() && domainsNode.size() > 0) {
                    String firstDomain = domainsNode.get(0).asText();
                    InetSocketAddress address = new InetSocketAddress(firstDomain, 25565);
                    return Optional.of(new ServerInstance(vmId, name, address));
                }
            }
        } catch (Exception e) {
            // Return empty if lookup fails
        }
        
        return Optional.empty();
    }
}

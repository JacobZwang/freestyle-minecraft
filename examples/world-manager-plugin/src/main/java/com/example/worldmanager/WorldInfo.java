package com.example.worldmanager;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Represents a Minecraft world backed by a VM.
 * This provides world-focused semantics on top of the VM abstraction.
 */
public class WorldInfo {
    private final String id;
    private final String name;
    private final WorldType type;
    private final InetSocketAddress address;
    private final Instant createdAt;
    private volatile Status status;
    private String parentWorld; // For forked worlds
    
    public WorldInfo(String id, String name, WorldType type, InetSocketAddress address, Status status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.address = address;
        this.status = status;
        this.createdAt = Instant.now();
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public WorldType getType() { return type; }
    public InetSocketAddress getAddress() { return address; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getParentWorld() { return parentWorld; }
    
    // Setters
    public void setStatus(Status status) { this.status = status; }
    public void setParentWorld(String parentWorld) { this.parentWorld = parentWorld; }
    
    /**
     * Returns the connection string for this world
     */
    public String getConnectionString() {
        return address.getHostString() + ":" + address.getPort();
    }
    
    /**
     * Checks if this world was forked from another world
     */
    public boolean isForked() {
        return parentWorld != null;
    }
    
    public enum Status {
        CREATING,   // World/VM is being provisioned
        RUNNING,    // World is active and ready for players
        SUSPENDED,  // World is hibernated to save resources
        STOPPING,   // World is shutting down
        ERROR       // Something went wrong
    }
    
    @Override
    public String toString() {
        return String.format("WorldInfo{name='%s', type=%s, status=%s, address='%s'}", 
                           name, type, status, getConnectionString());
    }
}
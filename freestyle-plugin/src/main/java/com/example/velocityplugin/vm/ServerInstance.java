package com.example.velocityplugin.vm;

import java.net.InetSocketAddress;
import java.time.Instant;

/** Simple data holder for a VM-backed Minecraft server instance. */
public class ServerInstance {
    private final String id;
    private final String name;
    private final InetSocketAddress address; // where the server can be reached
    private final Instant createdAt;
    private volatile State state = State.RUNNING;

    public enum State { RUNNING, SUSPENDED, STOPPED }

    public ServerInstance(String id, String name, InetSocketAddress address) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public InetSocketAddress getAddress() { return address; }
    public Instant getCreatedAt() { return createdAt; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
}

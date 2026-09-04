package com.example.velocityplugin.vm;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * A VM-backed Minecraft server.
 *
 * <p>Players never reach the VM directly. Freestyle's edge reads the server address out of the
 * Minecraft handshake — the routing key, the way {@code Host} is over HTTP — and splices the
 * session through to the VM. So {@link #getDomain()} is what identifies this server on the wire,
 * and every instance resolves to the same edge address on port 25565.
 */
public class ServerInstance {

    /** Mirrors Freestyle's VM states. */
    public enum State {
        STARTING, RUNNING, PAUSING, PAUSED, STOPPED;

        public static State from(String apiState) {
            if (apiState == null) {
                return RUNNING;
            }
            try {
                return valueOf(apiState.toUpperCase());
            } catch (IllegalArgumentException e) {
                return RUNNING;
            }
        }
    }

    private final String id;
    private final String name;
    private final String domain;
    private final InetSocketAddress address;
    private final Instant createdAt;
    private volatile State state;

    public ServerInstance(String id, String name, String domain, int port, State state) {
        this.id = id;
        this.name = name;
        this.domain = domain;
        // Unresolved: the domain is the routing key, so it has to survive to the handshake
        // rather than being collapsed into the edge IP it happens to resolve to right now.
        this.address = InetSocketAddress.createUnresolved(domain, port);
        this.createdAt = Instant.now();
        this.state = state;
    }

    /** The Freestyle VM id, {@code vm-…}. */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** The public domain players and the proxy connect to, e.g. {@code mc-survival-a1b2c3.style.dev}. */
    public String getDomain() {
        return domain;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "ServerInstance{id='" + id + "', name='" + name + "', domain='" + domain
                + "', state=" + state + '}';
    }
}

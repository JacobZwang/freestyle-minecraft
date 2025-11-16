package com.example.worldmanager;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Information about a managed Minecraft world. Each world corresponds to a VM
 * instance running a Minecraft server.
 */
public class WorldInfo {

    /**
     * Status of a world
     */
    public enum Status {
        RUNNING,
        SUSPENDED,
        STOPPED
    }

    private final String id;
    private final String name;
    private final WorldType type;
    private final InetSocketAddress address;
    private Status status;
    private String parentWorld;

    public WorldInfo(String id, String name, WorldType type, InetSocketAddress address, Status status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.address = address;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WorldType getType() {
        return type;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getParentWorld() {
        return parentWorld;
    }

    public void setParentWorld(String parentWorld) {
        this.parentWorld = parentWorld;
    }

    @Override
    public String toString() {
        return "WorldInfo{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", type=" + type
                + ", address=" + address
                + ", status=" + status
                + ", parentWorld='" + parentWorld + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorldInfo worldInfo = (WorldInfo) o;
        return Objects.equals(id, worldInfo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

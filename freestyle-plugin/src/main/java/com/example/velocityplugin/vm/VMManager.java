package com.example.velocityplugin.vm;

import java.util.Optional;

/**
 * VMManager abstracts VM lifecycle operations (create, suspend, fork).
 * Implementations may call a real VM provider (Freestyle) or local stubs for testing.
 */
public interface VMManager {
    /** Create a new VM for a fresh Minecraft server. Returns a ServerInstance representing it. */
    ServerInstance createServer(String name) throws Exception;

    /** Suspend a running VM (save state). */
    void suspendServer(String id) throws Exception;

    /** Fork an existing suspended server to create a new VM copy. */
    ServerInstance forkServer(String id, String newName) throws Exception;

    /** Lookup an instance by id. */
    Optional<ServerInstance> get(String id);
}

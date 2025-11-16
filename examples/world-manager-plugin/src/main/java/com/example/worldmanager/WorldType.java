package com.example.worldmanager;

/**
 * Different types of Minecraft worlds that can be created
 */
public enum WorldType {
    SURVIVAL("Survival world with standard gameplay"),
    CREATIVE("Creative world with unlimited resources"),
    ADVENTURE("Adventure world for custom maps and experiences"),
    SPECTATOR("Spectator-only world for viewing"),
    HARDCORE("Hardcore survival with permanent death"),
    SKYBLOCK("Skyblock survival challenge"),
    AMPLIFIED("Amplified terrain generation"),
    FLAT("Superflat world"),
    CUSTOM("Custom world type");
    
    private final String description;
    
    WorldType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
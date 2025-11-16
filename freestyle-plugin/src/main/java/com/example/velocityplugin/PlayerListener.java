package com.example.velocityplugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

public class PlayerListener {

    private final Logger logger;

    public PlayerListener(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        String playerName = event.getPlayer().getUsername();
        logger.info("Player {} joined the network!", playerName);
        
        // Send a welcome message to the player
        event.getPlayer().sendMessage(
            Component.text("Welcome to the server, " + playerName + "!")
                .color(NamedTextColor.GREEN)
        );
    }

    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        String playerName = event.getPlayer().getUsername();
        String serverName = event.getServer().getServerInfo().getName();
        logger.info("Player {} connected to server {}", playerName, serverName);
    }
}
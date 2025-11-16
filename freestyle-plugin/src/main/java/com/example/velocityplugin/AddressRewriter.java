package com.example.velocityplugin;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AddressRewriter {

    private final ProxyServer server;
    private final Logger logger;

    public AddressRewriter(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) throws Exception {
        return EventTask.async(() -> {
            if (event.getResult().getServer().isEmpty()) {
                return;
            }

            Player player = event.getPlayer();
            RegisteredServer targetServer = event.getResult().getServer().get();
            String targetHostname = targetServer.getServerInfo().getAddress().getHostString();
            InetSocketAddress originalVirtualHost = player.getVirtualHost().orElse(null);

            try {
                modifyVirtualHost(player, targetHostname, originalVirtualHost != null ? originalVirtualHost.getPort() : 25565);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean modifyVirtualHost(Player player, String newHostname, int port) throws Exception {
        Object connectedPlayer = player;
        InetSocketAddress newVirtualHost = new InetSocketAddress(newHostname, port);

        Field virtualHostField = connectedPlayer.getClass().getDeclaredField("virtualHost");
        virtualHostField.setAccessible(true);
        virtualHostField.set(connectedPlayer, newVirtualHost);
        return true;
    }
}

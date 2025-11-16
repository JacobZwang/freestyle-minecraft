package com.example.worldmanager;

import com.example.velocityplugin.FreestylePlugin;
import com.example.velocityplugin.vm.FreestyleVMManager;
import com.example.velocityplugin.vm.ServerInstance;
import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Super simple example showing how to use the Freestyle plugin.
 * Provides just two commands:
 * - /server create <name> - Creates a new Minecraft server using Freestyle VMs
 * - /server connect <name> - Connects player to the server
 */
@Plugin(
    id = "simple-server-manager",
    name = "SimpleServerManager",
    version = "1.0.0",
    description = "Simple example of using Freestyle VMs for on-demand servers",
    authors = {"Example"}
)
public class SimpleServerManager {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<String, ServerInstance> servers = new ConcurrentHashMap<>();

    @Inject
    public SimpleServerManager(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("SimpleServerManager initialized!");
        
        // Register the /server command
        proxy.getCommandManager().register(
            proxy.getCommandManager().metaBuilder("server").build(),
            new BrigadierCommand(
                LiteralArgumentBuilder.<CommandSource>literal("server")
                    .then(LiteralArgumentBuilder.<CommandSource>literal("create")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                            .executes(context -> {
                                String serverName = context.getArgument("name", String.class);
                                createServer(context.getSource(), serverName);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("connect")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                            .executes(context -> {
                                String serverName = context.getArgument("name", String.class);
                                connectToServer(context.getSource(), serverName);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                        .executes(context -> {
                            listServers(context.getSource());
                            return Command.SINGLE_SUCCESS;
                        })
                    )
            )
        );
        
        logger.info("Registered /server command with create, connect, and list subcommands");
    }

    private void createServer(CommandSource source, String serverName) {
        source.sendMessage(Component.text("Creating server: " + serverName + "...", NamedTextColor.YELLOW));
        
        CompletableFuture.runAsync(() -> {
            try {
                FreestyleVMManager vmManager = FreestylePlugin.getVMManager();
                if (vmManager == null) {
                    source.sendMessage(Component.text("Freestyle plugin not available! Check API key configuration.", NamedTextColor.RED));
                    return;
                }
                
                // Create the server using Freestyle VMs
                ServerInstance server = vmManager.createServer(serverName);
                servers.put(serverName, server);
                
                // Register with Velocity proxy so players can connect
                ServerInfo serverInfo = new ServerInfo(serverName, server.getAddress());
                proxy.registerServer(serverInfo);
                
                source.sendMessage(Component.text("✓ Server created: " + serverName + " at " + server.getAddress(), NamedTextColor.GREEN));
                source.sendMessage(Component.text("Use /server connect " + serverName + " to join!", NamedTextColor.AQUA));
                
            } catch (Exception e) {
                logger.error("Failed to create server: " + serverName, e);
                source.sendMessage(Component.text("Failed to create server: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void connectToServer(CommandSource source, String serverName) {
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("Only players can connect to servers!", NamedTextColor.RED));
            return;
        }
        
        Player player = (Player) source;
        
        if (!servers.containsKey(serverName)) {
            source.sendMessage(Component.text("Server '" + serverName + "' doesn't exist! Use /server create first.", NamedTextColor.RED));
            return;
        }
        
        proxy.getServer(serverName).ifPresentOrElse(
            server -> {
                source.sendMessage(Component.text("Connecting to " + serverName + "...", NamedTextColor.YELLOW));
                player.createConnectionRequest(server).fireAndForget();
            },
            () -> source.sendMessage(Component.text("Server '" + serverName + "' not found in proxy registry!", NamedTextColor.RED))
        );
    }

    private void listServers(CommandSource source) {
        if (servers.isEmpty()) {
            source.sendMessage(Component.text("No servers created yet. Use /server create <name>", NamedTextColor.YELLOW));
            return;
        }
        
        source.sendMessage(Component.text("Available servers:", NamedTextColor.AQUA));
        servers.forEach((name, server) -> {
            source.sendMessage(Component.text("  • " + name + " - " + server.getAddress(), NamedTextColor.WHITE));
        });
    }
}
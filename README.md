# Freestyle Minecraft Velocity Plugin

A Velocity plugin that enables **on-demand Minecraft server creation** using the Freestyle VM API. Create, manage, and switch between Minecraft servers dynamically without infrastructure complexity.

## ✨ Features

- Create and fork Minecraft servers ~2 seconds. Can be 10s of milliseconds with optimization.
- Servers are memory snapshots, so they can be suspended and resumed near instantly.
- Servers automatically suspend when there's no network activity and resume upon request.

## 🚀 Quick Example

```java
// Get the Freestyle VM service
FreestyleVMService vmService = FreestylePlugin.getVMService();

// Create a new world
CompletableFuture<WorldInfo> world = worldManager.createWorld("myworld");

// Fork an existing world  
CompletableFuture<WorldInfo> copy = worldManager.forkWorld("myworld", "myworld-copy");

// Switch a player to a world
player.createConnectionRequest(server.getServer("myworld").get()).connect();
```

## 🎮 In-Game Commands (via world manager example plugin)

```
/world create <name> [type]     - Create a new world
/world fork <source> <new>      - Fork an existing world (even the one you're currently in)
/world switch <name>            - Switch to a world
/worlds                         - List all worlds
```

## 🔧 Setup

1. **Build the plugins:**
```bash
cd freestyle-plugin && gradle build
cd ../examples/world-manager-plugin && gradle build
```

2. **Configure Freestyle API:**
```properties
# freestyle-config.properties
freestyle.api.url=https://api.freestyle.sh
freestyle.api.key=your-api-key-here
```

3. **Deploy to Velocity:**
```bash
cp freestyle-plugin/build/libs/freestyle-plugin-*.jar velocity/plugins/
cp examples/world-manager-plugin/build/libs/world-manager-plugin-*.jar velocity/plugins/
```

## 🏗️ Architecture

- **freestyle-plugin/** - Core VM management and API integration
- **examples/world-manager-plugin/** - Example consumer with commands
- **examples/basic-server/** - Velocity server configuration

## 📖 Plugin Development

Create your own plugins that use the Freestyle service:

```java
// Add dependency in build.gradle
dependencies {
    compileOnly project(':freestyle-plugin')
}

// Add dependency in velocity-plugin.json
"dependencies": [
    {
        "id": "freestyle-plugin",
        "optional": false
    }
]

// In your plugin class
import com.example.velocityplugin.FreestylePlugin;
import com.example.velocityplugin.vm.FreestyleVMService;
import com.example.velocityplugin.vm.VMManager;
import com.example.velocityplugin.vm.ServerInstance;

private FreestyleVMService freestyleVMService;
private VMManager vmManager;

@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    // Get the service with proper typing
    freestyleVMService = FreestylePlugin.getVMService();
    vmManager = freestyleVMService.getVMManager();
}

public void createCustomServer(String name) throws Exception {
    ServerInstance serverInstance = vmManager.createServer(name);
    String serverId = serverInstance.getId();
    InetSocketAddress address = serverInstance.getAddress();
    
    // Register with Velocity
    ServerInfo serverInfo = new ServerInfo(name, address);
    server.registerServer(serverInfo);
}
```

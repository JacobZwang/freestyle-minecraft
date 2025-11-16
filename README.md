# Freestyle Minecraft Plugin

A Velocity plugin that enables **on-demand Minecraft server creation** using the Freestyle VM API. Create, manage, and switch between Minecraft servers dynamically without infrastructure complexity.

## ✨ Features

- **🌍 Dynamic World Creation** - Create new Minecraft servers on-demand
- **🔄 World Forking** - Copy existing servers to create backups or variations
- **⚡ Seamless Switching** - Switch players between servers instantly
- **🎮 Simple Commands** - Easy-to-use in-game commands
- **🔧 Plugin API** - Clean API for other plugins to use

## 🚀 Quick Example

```java
// Get the Freestyle VM service
FreestyleVMService vmService = FreestylePlugin.getVMService();

// Create a new world
CompletableFuture<WorldInfo> world = worldManager.createWorld("myworld", WorldType.SURVIVAL);

// Fork an existing world  
CompletableFuture<WorldInfo> copy = worldManager.forkWorld("myworld", "myworld-copy");

// Switch a player to a world
player.createConnectionRequest(server.getServer("myworld").get()).connect();
```

## 🎮 In-Game Commands (via world manager example plugin)

```
/world create <name> [type]     - Create a new world
/world fork <source> <new>      - Fork an existing world
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
// In your plugin class
private Object freestyleVMService;

@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    // Get the service via reflection
    Class<?> freestylePluginClass = Class.forName("com.example.velocityplugin.FreestylePlugin");
    freestyleVMService = freestylePluginClass.getMethod("getVMService").invoke(null);
}

// Create servers dynamically
public void createCustomServer(String name) {
    Object vmManager = freestyleVMService.getClass().getMethod("getVMManager").invoke(freestyleVMService);
    Object serverInstance = vmManager.getClass().getMethod("createServer", String.class).invoke(vmManager, name);
}
```

## 🛠️ Development Notes

- Uses `registerServer()` for dynamic server registration (not `createRawRegisteredServer()`)
- All new servers are forked from VM `yrtby` via Freestyle API
- Plugins communicate via reflection to avoid tight coupling
- Jackson JSON library is bundled in the freestyle-plugin JAR

---

**Ready to build dynamic Minecraft experiences!** 🚀

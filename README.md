# Freestyle Minecraft Velocity Plugin

A Velocity plugin that enables **on-demand Minecraft server creation** using the [Freestyle VM API](https://www.freestyle.sh/docs/vms). Create, manage, and switch between Minecraft servers dynamically without infrastructure complexity.

## ✨ Features

- Fork a running Minecraft server in well under a second. Forks capture memory *and* disk, so the copy resumes mid-tick rather than booting.
- Servers are memory snapshots, so they can be paused and resumed near instantly.
- Servers automatically pause when there's no network activity, and a player joining wakes them.

## 🚀 Quick Example

```java
// Get the Freestyle VM manager
FreestyleVMManager vmManager = FreestylePlugin.getVMManager();

// Create a new world (forks the configured base VM)
CompletableFuture<WorldInfo> world = worldManager.createWorld("myworld", WorldType.SURVIVAL);

// Fork an existing world
CompletableFuture<WorldInfo> copy = worldManager.forkWorld("myworld", "myworld-copy");

// Switch a player to a world
player.createConnectionRequest(server.getServer("myworld").get()).connect();
```

## 🎮 In-Game Commands

`examples/simple-server-manager/` registers a `/server` command:

```
/server create <name>           - Create a new server (forks the base VM)
/server connect <name>          - Switch to a server
/server list                    - List created servers
```

It is a standalone example and is not part of the root `settings.gradle` build; build it from its own directory.

`examples/world-manager-plugin/` registers no commands — it exposes `WorldManager` as an API for other plugins to build on.

## 🔧 Setup

1. **Build the plugins:**
```bash
./gradlew build
```

2. **Configure Freestyle.** Every setting can come from the environment (which takes precedence) or from `freestyle-config.properties`. Copy the template to get started:

```bash
cp examples/basic-server/freestyle-config.properties.example \
   examples/basic-server/freestyle-config.properties
```

```properties
freestyle.api.key=your-api-key-here     # or FREESTYLE_API_KEY
freestyle.base.vm=minecraft             # VM to fork new worlds from
freestyle.domain.suffix=style.dev       # worlds are published under this domain
freestyle.idle.timeout.seconds=300      # pause a world after 5 min idle
```

Get an API key at [dash.freestyle.sh](https://dash.freestyle.sh).

3. **Prepare a base VM.** `freestyle.base.vm` points at a VM that already runs a Minecraft server on port 25565. Every world is a fork of it. Because Freestyle's edge sits between the player and the server, the guest's `server.properties` needs:

```properties
prevent-proxy-connections=false
```

Otherwise an online-mode server rejects every login. Java Edition only — Bedrock is a different protocol over UDP.

4. **Deploy to Velocity:**
```bash
./build-and-deploy.sh
```

## 🏗️ Architecture

- **freestyle-plugin/** - Core VM management and Freestyle API integration
- **examples/world-manager-plugin/** - Example consumer with commands
- **examples/basic-server/** - Velocity server configuration

### How a world gets its address

Players never reach a VM directly. Each world is published on its own domain by a Freestyle [TLS rule](https://www.freestyle.sh/docs/vms/network/tls) with `protocol: "minecraft"`. The rule is created inline with the VM, so deleting the VM deletes the route too.

The edge reads the server address out of the Minecraft handshake — the routing key, the way `Host` is over HTTP — and splices the session through to the right VM. Every world therefore resolves to the same edge address on port 25565, and the *hostname* is what distinguishes them.

That is why [`AddressRewriter`](freestyle-plugin/src/main/java/com/example/velocityplugin/AddressRewriter.java) exists: it rewrites the player's virtual host to the target world's domain before Velocity opens the backend connection, so the handshake Velocity sends carries the name the edge routes on.

### How a fork works

Forking is two API calls:

1. `POST /v5/vms/{id}/snapshot` — captures the source VM's memory and disk while the Minecraft server is running.
2. `POST /v5/vms` with that `snapshotId` — the new VM resumes into the same process state, with a `minecraft` TLS rule and firewall rules created alongside it.

The intermediate snapshot is created with a one-hour `autoDeleteSeconds`, so it cleans itself up once the fork has booted from it.

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
```

```java
import com.example.velocityplugin.FreestylePlugin;
import com.example.velocityplugin.vm.FreestyleVMManager;
import com.example.velocityplugin.vm.ServerInstance;

private FreestyleVMManager vmManager;

@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    vmManager = FreestylePlugin.getVMManager();
}

public void createCustomServer(String name) throws Exception {
    ServerInstance instance = vmManager.createServer(name);

    String vmId = instance.getId();              // vm-...
    String domain = instance.getDomain();        // mc-name-a1b2c3.style.dev
    InetSocketAddress address = instance.getAddress();

    server.registerServer(new ServerInfo(name, address));
}
```

### `FreestyleVMManager` API

| Method | Freestyle call | Notes |
| --- | --- | --- |
| `createServer(name)` | snapshot + create | Forks the configured base VM |
| `forkServer(vmId, name)` | snapshot + create | Forks any VM by id or slug |
| `suspendServer(vmId)` | `POST /v5/vms/{id}/pause` | Freezes memory; resume continues the same process |
| `resumeServer(vmId)` | `POST /v5/vms/{id}/start` | Joining the domain also wakes a paused world |
| `deleteServer(vmId)` | `DELETE /v5/vms/{id}` | Permanent; takes the domain and rules with it |
| `getServer(vmId)` | `GET /v5/vms/{id}` | Returns `Optional.empty()` when absent |
| `listServers()` | `GET /v5/vms` | Only worlds this plugin created |

Worlds created by this plugin are tagged with a `world` metadata key, which is how `listServers()` tells them apart from other VMs in the account.

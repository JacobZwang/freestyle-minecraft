# World Manager Plugin Example

This example demonstrates how to use the VM abstraction system from the main Velocity plugin to create a world management experience. It provides a Minecraft-focused API that abstracts away VM infrastructure concerns.

## Features

The World Manager plugin allows players to:

- **Create new worlds** on-demand with different types (survival, creative, etc.)
- **Switch between worlds** seamlessly 
- **Fork existing worlds** to create copies with shared history
- **Suspend worlds** to save server resources when not in use
- **Resume suspended worlds** when needed again
- **List and manage** all available worlds

## Commands

### `/world create <name> [type]`
Creates a new world with the specified name and type.

**Examples:**
- `/world create MyWorld` - Creates a survival world
- `/world create CreativeHub creative` - Creates a creative world
- `/world create SkyIsland skyblock` - Creates a skyblock world

**Available world types:**
- `survival` - Standard survival gameplay (default)
- `creative` - Creative mode with unlimited resources
- `adventure` - Adventure mode for custom experiences
- `hardcore` - Hardcore survival with permanent death
- `skyblock` - Skyblock survival challenge
- `amplified` - Amplified terrain generation
- `flat` - Superflat world
- `custom` - Custom world configuration

### `/world switch <name>`
Switches the player to the specified world.

**Example:**
- `/world switch MyWorld` - Connects you to MyWorld

### `/world fork <source> <new-name>`
Creates a copy of an existing world, preserving its current state.

**Example:**
- `/world fork MyWorld MyWorld-Backup` - Creates a copy of MyWorld

### `/world suspend <name>`
Suspends a world to save server resources. The world's state is preserved but players cannot connect until it's resumed.

**Example:**
- `/world suspend OldWorld` - Suspends OldWorld to save resources

### `/world resume <name>`
Resumes a suspended world, making it available for players again.

**Example:**
- `/world resume OldWorld` - Makes OldWorld available again

### `/world list`
Shows all available worlds with their status and type.

### `/world info <name>`
Shows detailed information about a specific world.

**Example:**
- `/world info MyWorld` - Shows details about MyWorld

## How It Works

### VM Abstraction Layer
The plugin builds on top of the VM abstraction system from the main Velocity plugin:

1. **World Creation** → VM provisioning + Minecraft server setup
2. **World Forking** → VM cloning + server configuration
3. **World Suspension** → VM hibernation for resource saving
4. **World Switching** → Player connection routing

### Implementation Details

#### WorldManager
The core service that handles world lifecycle operations:
- Manages world metadata and state
- Coordinates with the VM abstraction layer
- Handles Velocity server registration/unregistration
- Provides async operations for all world management tasks

#### WorldInfo
Data class representing a world instance:
- World metadata (name, type, creation time)
- Connection information (hostname, port)
- Status tracking (running, suspended, creating, etc.)
- Parent-child relationships for forked worlds

#### WorldCommand
Command handler providing the user interface:
- Tab completion for world names and types
- Input validation and error handling
- User-friendly messages and help text
- Permission checking (players only for switching)

## Integration with VM System

This example shows how to build higher-level abstractions on top of the generic VM management system:

```java
// Creating a world uses the VM abstraction under the hood
public CompletableFuture<WorldInfo> createWorld(String name, WorldType type) {
    // 1. Use VMManager to provision new VM
    // 2. Install and configure Minecraft server via systemd
    // 3. Wait for server to be ready
    // 4. Register with Velocity proxy
    // 5. Return world information to user
}
```

## Configuration

The plugin uses the same VM manager configuration as the main Velocity plugin:
- **Local development**: Uses `LocalVMManager` for testing
- **Production**: Can be configured to use `FreestyleVMClient` for real VM provisioning

## Development Usage

This example demonstrates how other developers can:

1. **Build domain-specific abstractions** on top of generic VM operations
2. **Create user-friendly commands** that hide infrastructure complexity  
3. **Manage server lifecycle** through Minecraft-focused semantics
4. **Handle async operations** gracefully with proper error handling
5. **Integrate with Velocity** for seamless player experience

The goal is to show how the VM abstraction layer enables rapid development of on-demand server experiences without requiring developers to understand VM infrastructure details.

## Future Enhancements

Potential improvements for a production system:

- **World templates** - Predefined world configurations
- **Resource limits** - CPU/memory constraints per world  
- **Auto-suspension** - Automatically suspend inactive worlds
- **Permissions system** - Control who can create/manage worlds
- **World sharing** - Allow players to invite others to their worlds
- **Backup/restore** - Automated world backup and restoration
- **Monitoring** - World performance and health monitoring
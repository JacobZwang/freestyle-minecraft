# Simple Server Manager

A minimal example plugin demonstrating how to use the Freestyle plugin to create on-demand Minecraft servers.

## Features

This plugin provides just 3 simple commands:

- `/server create <name>` - Creates a new Minecraft server using Freestyle VMs
- `/server connect <name>` - Connects the player to the specified server  
- `/server list` - Lists all created servers

## Usage

1. Make sure the Freestyle plugin is installed and configured with a valid API key
2. Install this plugin alongside the Freestyle plugin
3. Use `/server create myworld` to create a new server
4. Use `/server connect myworld` to connect to it

## How it works

This plugin is a single Java file that shows the basics of using the Freestyle API:

1. It gets the `FreestyleVMManager` from the main Freestyle plugin
2. Calls `createServer()` to spin up a new VM with a Minecraft server
3. Registers the new server with Velocity so players can connect
4. Provides simple commands to manage the servers

This demonstrates how easy it is to build on top of the Freestyle VM abstraction!

#!/bin/bash

echo "Building and deploying Freestyle Minecraft plugins..."

# Create gradle wrapper if it doesn't exist
if [ ! -f gradlew ]; then
    echo "Creating gradle wrapper..."
    gradle wrapper --gradle-version 8.3
fi

# Build all projects using the multi-project setup
echo "Building all plugins using multi-project build..."
./gradlew build
if [ $? -ne 0 ]; then
    echo "Failed to build plugins"
    exit 1
fi

# Copy plugins to server
echo "Deploying plugins to server..."
cp freestyle-plugin/build/libs/freestyle-plugin-1.0.0.jar examples/basic-server/plugins/
cp examples/world-manager-plugin/build/libs/world-manager-plugin-1.0.0.jar examples/basic-server/plugins/

echo "✅ Plugins built and deployed successfully!"
echo ""
echo "To test:"
echo "1. cd examples/basic-server"  
echo "2. bash start.sh"
echo "3. Connect to localhost:25565"
echo "4. Try commands like:"
echo "   /world create MyWorld survival"
echo "   /world list"
echo "   /world switch MyWorld"
echo "   /world fork MyWorld MyWorld-Backup"
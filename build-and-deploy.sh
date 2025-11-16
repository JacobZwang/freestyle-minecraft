#!/bin/bash

echo "Building and deploying Freestyle Minecraft plugins..."

# Build main freestyle plugin (contains VM API and Freestyle integration)
echo "Building freestyle plugin (contains VM API)..."
cd freestyle-plugin
gradle build
if [ $? -ne 0 ]; then
    echo "Failed to build freestyle plugin"
    exit 1
fi

# Build example world manager plugin (lightweight wrapper around freestyle plugin)
echo "Building world manager plugin (lightweight wrapper)..."
cd ../examples/world-manager-plugin
./gradlew build
if [ $? -ne 0 ]; then
    echo "Failed to build world manager plugin"
    exit 1
fi

# Copy plugins to server
echo "Deploying plugins to server..."
cd ../..
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
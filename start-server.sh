#!/bin/bash
# Quick start script for Malefirc IRC Server

echo "=== Malefirc IRC Server ==="
echo "Building server..."

./gradlew :irc-server:installDist -q

if [ $? -eq 0 ]; then
    echo "Starting IRC server on port 6667..."
    echo "Press Ctrl+C to stop"
    echo ""
    ./irc-server/build/install/irc-server/bin/irc-server
else
    echo "Build failed!"
    exit 1
fi

#!/bin/bash
# Quick start script for Malefirc IRC Client

echo "=== Malefirc IRC Client ==="
echo "Building client..."

./gradlew :irc-client:installDist -q

if [ $? -eq 0 ]; then
    echo "Starting IRC client..."
    echo ""
    ./irc-client/build/install/irc-client/bin/irc-client
else
    echo "Build failed!"
    exit 1
fi

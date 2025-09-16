#!/bin/bash

# Build Release APK for gplay flavor
# This script builds a signed release APK for the gplay flavor

set -e

echo "Building Release APK for gplay flavor..."

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build the release APK for gplay flavor
echo "Building gplayRelease APK..."
./gradlew assembleGplayRelease

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📱 Release APK location:"
    echo "   app/build/outputs/apk/gplay/release/app-gplay-release.apk"
    
    # Check if APK file exists
    if [ -f "app/build/outputs/apk/gplay/release/app-gplay-release.apk" ]; then
        echo "📦 APK file size:"
        ls -lh "app/build/outputs/apk/gplay/release/app-gplay-release.apk"
    else
        echo "❌ APK file not found!"
    fi
else
    echo "❌ Build failed!"
    exit 1
fi

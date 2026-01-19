#!/data/data/com.termux/files/usr/bin/bash
# Build script for Claude Assistant Android App
# Run this script from the project root directory

set -e

echo "=== Building Claude Assistant ==="

# Check for gradle
if ! command -v gradle &>/dev/null; then
    echo "ERROR: Gradle not installed"
    echo "Install with: pkg install gradle"
    exit 1
fi

# Build debug APK
echo "[1/2] Building debug APK..."
gradle assembleDebug

# Check result
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=== Build Successful ==="
    echo "APK location: $APK_PATH"
    echo ""
    echo "To install:"
    echo "  adb install $APK_PATH"
    echo ""
    echo "Or copy to device and install manually."
else
    echo "ERROR: APK not found"
    exit 1
fi

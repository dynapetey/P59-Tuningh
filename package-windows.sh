#!/bin/bash

# Windows packaging script (cross-platform compatible)
# This script prepares the application for Windows packaging
# Final exe/msi creation should be done on Windows with jpackage

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "Building Windows application..."
./gradlew :windowsApp:installDist

INPUT_DIR="$PROJECT_ROOT/windowsApp/build/install/windowsApp/lib"
MAIN_JAR=$(find "$INPUT_DIR" -name "windowsApp*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)

if [ -z "$MAIN_JAR" ]; then
    echo "ERROR: Unable to locate the Windows application JAR in $INPUT_DIR"
    exit 1
fi

echo "Main JAR found: $(basename $MAIN_JAR)"

# Create output directory
DIST_DIR="$PROJECT_ROOT/dist/windows"
mkdir -p "$DIST_DIR"

echo ""
echo "=========================================="
echo "Build preparation complete!"
echo "=========================================="
echo ""
echo "Application is ready for Windows packaging."
echo "To create the exe/msi installers, run on Windows:"
echo ""
echo "  powershell -ExecutionPolicy Bypass -File windowsApp\package-windows.ps1"
echo ""
echo "Or manually with jpackage:"
echo ""
echo "  jpackage --type exe \"
echo "    --name P59Tuner \"
echo "    --input windowsApp/build/install/windowsApp/lib \"
echo "    --main-jar $(basename $MAIN_JAR) \"
echo "    --main-class com.p59.windows.MainKt \"
echo "    --dest dist/windows \"
echo "    --app-version 1.0.0 \"
echo "    --vendor Dynapetey \"
echo "    --description \"OBDX Pro GM P59 tuning and diagnostics runtime\" \"
echo "    --win-dir-chooser --win-menu --win-shortcut"
echo ""

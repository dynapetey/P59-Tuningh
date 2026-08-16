#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_root"

if ! command -v jpackage >/dev/null 2>&1; then
    echo "ERROR: jpackage is required. Install a JDK 17 distribution that includes jpackage." >&2
    exit 1
fi

./gradlew :windowsApp:clean :windowsApp:test :windowsApp:installDist

input_dir="$project_root/windowsApp/build/install/windowsApp/lib"
main_jar="$(find "$input_dir" -maxdepth 1 -name 'windowsApp-*.jar' -print -quit)"
if [[ -z "$main_jar" ]]; then
    echo "ERROR: Unable to locate the desktop application JAR in $input_dir" >&2
    exit 1
fi

dist_dir="$project_root/dist/linux"
rm -rf "$dist_dir/P59Tuner"
mkdir -p "$dist_dir"

jpackage \
    --type app-image \
    --name P59Tuner \
    --input "$input_dir" \
    --main-jar "$(basename "$main_jar")" \
    --main-class com.p59.windows.MainKt \
    --dest "$dist_dir" \
    --app-version 1.0.0 \
    --vendor Dynapetey \
    --description "OBDX Pro GM P59 tuning and diagnostics runtime"

tar -C "$dist_dir" -czf "$dist_dir/P59Tuner-linux-x64.tar.gz" P59Tuner
echo "Linux runtime created at dist/linux/P59Tuner-linux-x64.tar.gz"

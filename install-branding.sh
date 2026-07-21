#!/usr/bin/env bash
set -euo pipefail

PROJECT="${1:-/home/bp/StudioProjects/P59-Tuningh}"
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RES="$PROJECT/app/src/main/res"

if [[ ! -f "$PROJECT/app/build.gradle.kts" ]]; then
  echo "Android project not found at: $PROJECT" >&2
  exit 1
fi

BACKUP="$PROJECT/branding-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
for path in \
  "$RES/values/themes.xml" \
  "$RES/values/colors.xml" \
  "$RES/mipmap-anydpi-v26/ic_launcher.xml" \
  "$RES/mipmap-anydpi-v26/ic_launcher_round.xml"; do
  if [[ -f "$path" ]]; then
    mkdir -p "$BACKUP/$(dirname "${path#$PROJECT/}")"
    cp "$path" "$BACKUP/${path#$PROJECT/}"
  fi
done

cp -R "$SOURCE_DIR/app/src/main/res/." "$RES/"

echo "P59 launcher icon and splash screen installed."
echo "Backup: $BACKUP"
echo
echo "Building debug APK..."
cd "$PROJECT"
./gradlew assembleDebug

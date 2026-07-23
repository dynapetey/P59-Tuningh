#!/usr/bin/env bash
set -Eeuo pipefail

trap 'echo; echo "FAILED at line $LINENO"; echo "Command: $BASH_COMMAND"' ERR

ROOT="$(pwd)"

echo "Repository: $ROOT"

required_files=(
  "app/build.gradle.kts"
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/com/example/MainActivity.kt"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: Cannot find $file"
    echo "Run this script from the root of the P59-Tuningh repository."
    exit 1
  fi
done

BACKUP=".icon-splash-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"

cp --parents \
  app/build.gradle.kts \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/example/MainActivity.kt \
  "$BACKUP"

if [[ -f app/src/main/res/values/themes.xml ]]; then
  cp --parents app/src/main/res/values/themes.xml "$BACKUP"
fi

echo "Backup created: $BACKUP"

python3 <<'PYTHON'
from pathlib import Path
import re
import sys

root = Path.cwd()

# ---------------------------------------------------------
# Add AndroidX SplashScreen dependency
# ---------------------------------------------------------
gradle_path = root / "app/build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")

dependency = 'implementation("androidx.core:core-splashscreen:1.0.1")'

if "androidx.core:core-splashscreen" not in gradle:
    match = re.search(r"dependencies\s*\{", gradle)

    if not match:
        sys.exit("Could not find dependencies { in app/build.gradle.kts")

    position = match.end()

    gradle = (
        gradle[:position]
        + "\n"
        + "    // Android startup splash screen\n"
        + f"    {dependency}\n"
        + gradle[position:]
    )

    gradle_path.write_text(gradle, encoding="utf-8")
    print("Added SplashScreen dependency")
else:
    print("SplashScreen dependency already exists")

# ---------------------------------------------------------
# Create splash and application themes
# ---------------------------------------------------------
themes_path = root / "app/src/main/res/values/themes.xml"
themes_path.parent.mkdir(parents=True, exist_ok=True)

themes_path.write_text(
'''<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style
        name="Theme.MyApplication"
        parent="android:Theme.Material.NoActionBar">

        <item name="android:windowLightStatusBar">false</item>
        <item name="android:statusBarColor">#0F1115</item>
        <item name="android:navigationBarColor">#0F1115</item>
        <item name="android:windowBackground">#0F1115</item>

    </style>

    <style
        name="Theme.MyApplication.Starting"
        parent="Theme.SplashScreen.IconBackground">

        <item name="windowSplashScreenBackground">#0F1115</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="windowSplashScreenIconBackgroundColor">#121824</item>
        <item name="postSplashScreenTheme">@style/Theme.MyApplication</item>

    </style>

</resources>
''',
encoding="utf-8",
)

print("Created startup splash theme")

# ---------------------------------------------------------
# Update AndroidManifest.xml
# ---------------------------------------------------------
manifest_path = root / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")

activity_pattern = re.compile(
    r'<activity\b(?=[^>]*android:name\s*=\s*["\']\.MainActivity["\'])[^>]*>',
    re.DOTALL,
)

def replace_activity(match):
    tag = match.group(0)

    if re.search(r'android:theme\s*=', tag):
        tag = re.sub(
            r'android:theme\s*=\s*["\'][^"\']+["\']',
            'android:theme="@style/Theme.MyApplication.Starting"',
            tag,
            count=1,
        )
    else:
        tag = (
            tag[:-1]
            + '\n            android:theme="@style/Theme.MyApplication.Starting">'
        )

    return tag

manifest, count = activity_pattern.subn(
    replace_activity,
    manifest,
    count=1,
)

if count != 1:
    sys.exit("Could not find .MainActivity in AndroidManifest.xml")

# Ensure application icon declarations exist.
application_pattern = re.compile(r"<application\b[^>]*>", re.DOTALL)
application_match = application_pattern.search(manifest)

if not application_match:
    sys.exit("Could not find <application> in AndroidManifest.xml")

application_tag = application_match.group(0)

if "android:icon=" not in application_tag:
    application_tag = application_tag[:-1] + (
        '\n        android:icon="@mipmap/ic_launcher">'
    )

if "android:roundIcon=" not in application_tag:
    application_tag = application_tag[:-1] + (
        '\n        android:roundIcon="@mipmap/ic_launcher_round">'
    )

manifest = (
    manifest[:application_match.start()]
    + application_tag
    + manifest[application_match.end():]
)

manifest_path.write_text(manifest, encoding="utf-8")
print("Updated AndroidManifest.xml")

# ---------------------------------------------------------
# Update MainActivity.kt
# ---------------------------------------------------------
activity_path = root / "app/src/main/java/com/example/MainActivity.kt"
activity = activity_path.read_text(encoding="utf-8")

splash_import = "import androidx.core.splashscreen.installSplashScreen"

if splash_import not in activity:
    anchor = "import androidx.core.content.ContextCompat"

    if anchor in activity:
        activity = activity.replace(
            anchor,
            anchor + "\n" + splash_import,
            1,
        )
    else:
        package_match = re.search(r"^package\s+[^\n]+\n", activity)

        if not package_match:
            sys.exit("Could not find package line in MainActivity.kt")

        activity = (
            activity[:package_match.end()]
            + "\n"
            + splash_import
            + "\n"
            + activity[package_match.end():]
        )

if "installSplashScreen()" not in activity:
    on_create = re.compile(
        r'(override\s+fun\s+onCreate\s*\(\s*savedInstanceState\s*:\s*Bundle\?\s*\)\s*\{)'
    )

    activity, count = on_create.subn(
        r"\1\n        installSplashScreen()",
        activity,
        count=1,
    )

    if count != 1:
        sys.exit("Could not locate MainActivity.onCreate()")

activity_path.write_text(activity, encoding="utf-8")
print("Updated MainActivity.kt")

# ---------------------------------------------------------
# Create launcher resources
# ---------------------------------------------------------
drawable = root / "app/src/main/res/drawable"
adaptive = root / "app/src/main/res/mipmap-anydpi-v26"
fallback = root / "app/src/main/res/mipmap-anydpi"

drawable.mkdir(parents=True, exist_ok=True)
adaptive.mkdir(parents=True, exist_ok=True)
fallback.mkdir(parents=True, exist_ok=True)

background = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:fillColor="#121824"
        android:pathData="M0,0 h108 v108 h-108 z" />

</vector>
'''

foreground = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:fillColor="#00E5FF"
        android:pathData="M54,24 A30,30 0 1,0 54,84 A30,30 0 1,0 54,24 Z" />

    <path
        android:fillColor="#FFFFFF"
        android:pathData="M44,40 h20 v28 h-20 z M49,36 v4 M54,36 v4 M59,36 v4 M49,68 v4 M54,68 v4 M59,68 v4 M40,46 h4 M40,54 h4 M40,62 h4 M64,46 h4 M64,54 h4 M64,62 h4" />

</vector>
'''

adaptive_icon = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''

fallback_icon = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:fillColor="#121824"
        android:pathData="M0,0 h108 v108 h-108 z" />

    <path
        android:fillColor="#00E5FF"
        android:pathData="M54,24 A30,30 0 1,0 54,84 A30,30 0 1,0 54,24 Z" />

    <path
        android:fillColor="#FFFFFF"
        android:pathData="M44,40 h20 v28 h-20 z M49,36 v4 M54,36 v4 M59,36 v4 M49,68 v4 M54,68 v4 M59,68 v4 M40,46 h4 M40,54 h4 M40,62 h4 M64,46 h4 M64,54 h4 M64,62 h4" />

</vector>
'''

(drawable / "ic_launcher_background.xml").write_text(
    background,
    encoding="utf-8",
)

(drawable / "ic_launcher_foreground.xml").write_text(
    foreground,
    encoding="utf-8",
)

(adaptive / "ic_launcher.xml").write_text(
    adaptive_icon,
    encoding="utf-8",
)

(adaptive / "ic_launcher_round.xml").write_text(
    adaptive_icon,
    encoding="utf-8",
)

(fallback / "ic_launcher.xml").write_text(
    fallback_icon,
    encoding="utf-8",
)

(fallback / "ic_launcher_round.xml").write_text(
    fallback_icon,
    encoding="utf-8",
)

print("Created launcher icon resources")
PYTHON

chmod +x gradlew

echo
echo "Running Gradle build..."
./gradlew --no-daemon clean assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

echo
echo "=============================================="
echo "SUCCESS"
echo "APK created at:"
echo "$APK"
echo "=============================================="
echo
echo "Commit the changes with:"
echo "git add app"
echo 'git commit -m "Add launcher icon and splash screen"'
echo "git push"

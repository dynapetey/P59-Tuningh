#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pcmhammer_revision="bde8eeae3b4ea6ae9be877873cc5c6e9120fbb24"
source_dir="$project_root/build/pcmhammer-source"
output_dir="$project_root/windowsApp/build/install/windowsApp/lib/pcmhammer"

if ! command -v dotnet >/dev/null 2>&1; then
    echo "ERROR: .NET SDK 10 is required to build the official PCM Hammer Linux backend." >&2
    exit 1
fi

if [[ ! -d "$source_dir/.git" ]]; then
    git clone https://github.com/PcmHammer/PcmHammer.git "$source_dir"
fi
git -C "$source_dir" fetch --depth 1 origin "$pcmhammer_revision"
git -C "$source_dir" switch --detach "$pcmhammer_revision"

rm -rf "$output_dir"
mkdir -p "$output_dir/kernels"
dotnet publish "$source_dir/Apps/UI/PcmHammerLinux/PcmHammerLinux.csproj" \
    --configuration Release \
    --runtime linux-x64 \
    --self-contained true \
    --output "$output_dir"

cp "$project_root/windowsApp/src/main/resources/kernel_p01.bin" \
    "$output_dir/kernels/Kernel-P01.bin"
chmod +x "$output_dir/pcmhammer-cli"

# P59 Tuning

An open-source Android, Linux, and Windows tuning and diagnostics utility for the GM P59 powertrain control module used in many Gen III LS/Vortec vehicles.

> **Status:** Active development. Always create and preserve a verified PCM backup before changing a calibration.

## Highlights

- Track Tech desktop interface with high-contrast live telemetry
- OBDX Pro USB and Bluetooth virtual serial-port support
- Live RPM, speed, MAP, coolant, throttle, MAF, spark, fuel trims, commanded EQ, intake temperature, wideband AFR, and adapter-voltage data
- Read and clear diagnostic trouble codes
- Verified, read-only 1 MiB P59 extraction using the P01/P59 PCM Hammer kernel
- Full-image test-write and write through a pinned official PCM Hammer CLI backend
- Android application for mobile tuning and logging workflows

## Tuning tools

The desktop **TUNE** workspace provides:

- Wideband AFR fuel-error and correction calculations
- VE and MAF value scaling from commanded versus measured AFR
- Idle airflow recommendations from steady-state RPM error
- Conservative spark analysis using timing, knock retard, and wideband AFR

These tools validate their inputs and produce recommendations. They do not silently edit or flash a calibration.

## PCM writing safeguards

Desktop full writes are delegated to the official PCM Hammer engine. Before writing, the application requires:

1. A connected OBDX Pro interface
2. An exact 1 MiB P59 image
3. A readable supply voltage of at least 12.0 V
4. A typed destructive-operation confirmation
5. A successful non-destructive PCM Hammer test-write
6. A final confirmation

PCM Hammer performs image and PCM validation, flash-chip detection, erase/program retries, range CRC verification, and recovery handling. The application prevents normal shutdown while a write is active.

Calibration-only desktop writing remains disabled because the official PCM Hammer CLI does not currently expose that operation.

## Supported vehicles

The project targets compatible GM vehicles equipped with a P59 PCM, including many Chevrolet Silverado, Tahoe, and Suburban; GMC Sierra and Yukon; Cadillac Escalade; and other Gen III LS/Vortec applications using a supported P59 operating system.

Hardware and operating-system compatibility must be verified before any write.

## Requirements

- JDK 17
- An OBDX Pro-compatible interface for vehicle communication
- A stable regulated power supply for PCM writing
- Android SDK for Android builds
- .NET SDK 10 and `jpackage` for the self-contained Linux package
- Windows and WiX Toolset for `.exe` and `.msi` installers

Linux users need read/write access to the serial device, commonly `/dev/ttyUSB0` or `/dev/ttyACM0`. On distributions using the `dialout` group, add the user to that group and sign in again.

## Build from source

```bash
git clone https://github.com/dynapetey/P59-Tuningh.git
cd P59-Tuningh
```

### Linux desktop runtime

```bash
./package-linux.sh
```

The build tests the desktop module, builds the pinned official PCM Hammer backend, and produces `dist/linux/P59Tuner-linux-x64.tar.gz`. Extract it and launch `P59Tuner/bin/P59Tuner`.

For a source run using an existing official PCM Hammer CLI:

```bash
export PCM_HAMMER_CLI=/absolute/path/to/pcmhammer-cli
./gradlew :windowsApp:run
```

### Windows desktop runtime

```powershell
.\gradlew.bat :windowsApp:installDist
powershell -ExecutionPolicy Bypass -File windowsApp\package-windows.ps1
```

Installers are written to `dist\windows`.

### Android application

```bash
./gradlew :app:assembleDebug
```

The debug APK is written beneath `app/build/outputs/apk/debug`.

## Tests

```bash
./gradlew :windowsApp:test
./gradlew :app:testDebugUnitTest
```

GitHub Actions builds the Linux runtime and Windows installers. Workflow artifacts contain the packaged applications.

## Safety

PCM programming can render a controller unusable if power or communication is interrupted. Use a bench PCM during development, maintain stable voltage, keep a known-good backup, and never disconnect the interface during an erase or write operation.

## Licensing and attribution

This repository includes PCM Hammer-derived protocol work and kernel resources under GPL-3.0-only. See [`LICENSES/PCM_HAMMER_GPL-3.0.txt`](LICENSES/PCM_HAMMER_GPL-3.0.txt) and [`THIRD_PARTY_NOTICES_WINDOWS.md`](THIRD_PARTY_NOTICES_WINDOWS.md) for details.

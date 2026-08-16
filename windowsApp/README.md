# P59 Tuner Desktop Runtime

This module is the JVM desktop runtime for the OBDX Pro P59 Tuner. It supports Windows and Linux while leaving the Android application separate.

## Supported functions

- Windows 10/11 x64 and Linux x64 desktop interface
- USB or Bluetooth virtual COM-port connection
- OBDX Pro ELM-compatible initialization
- Live RPM, speed, MAP, coolant, TPS, MAF, spark, trims, commanded EQ, IAT, battery voltage, and A/C-input wideband data
- Wideband conversion: `AFR = (A/C input voltage / 0.5) + 9.37`
- Read and clear diagnostic trouble codes
- Read-only, verified 1 MiB P59 extraction through OBDX DVI and the official P01/P59 PCM Hammer kernel

PCM writing remains disabled.

## Run from source

```powershell
gradle :windowsApp:run
```

With a Gradle wrapper:

```powershell
.\gradlew.bat :windowsApp:run
```

## Build the portable application

```powershell
.\gradlew.bat :windowsApp:installDist
```

The launcher is created at:

```text
windowsApp\build\install\windowsApp\bin\windowsApp.bat
```

## Build Windows installers

Run:

```powershell
powershell -ExecutionPolicy Bypass -File windowsApp\package-windows.ps1
```

The `.exe` and `.msi` files are written to `dist\windows`.

Installers must be produced on Windows. The included GitHub Actions workflow uses a Windows runner and uploads both formats as a workflow artifact.

## Build the Linux runtime

On an x64 Linux host with JDK 17 (including `jpackage`) installed:

```bash
./package-linux.sh
```

The script runs the desktop tests and creates a self-contained application image, including a Java runtime, at `dist/linux/P59Tuner-linux-x64.tar.gz`. Extract it and launch `P59Tuner/bin/P59Tuner`.

Linux users need read/write permission for the OBDX Pro serial device (commonly `/dev/ttyUSB0` or `/dev/ttyACM0`). On distributions that use the `dialout` group, add the user to that group and sign in again. Distribution-specific udev rules may also be used.

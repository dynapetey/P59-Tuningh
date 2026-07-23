# P59 Tuner Windows Runtime

This module is the native Windows desktop runtime for the OBDX Pro P59 Tuner.

## Supported functions

- Windows 10/11 x64 desktop interface
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

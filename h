P59 Tuning icon and splash installer

Default project:
  /home/bp/StudioProjects/P59-Tuningh

Run:
  chmod +x install-branding.sh
  ./install-branding.sh

Or specify another project:
  ./install-branding.sh /path/to/project

The installer:
- backs up existing launcher/theme resources
- installs launcher icons for all densities
- installs adaptive icons
- installs pre-Android-12 and Android-12+ splash screens
- runs ./gradlew assembleDebug

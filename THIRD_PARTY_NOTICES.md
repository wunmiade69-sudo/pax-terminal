# Third-party notices

PAX Terminal packages open-source software into its Android build. Respect the original license and copyright notices for each component.

## PRoot Android build scripts

- Project: `green-green-avk/build-proot-android`
- Source: https://github.com/green-green-avk/build-proot-android
- License: MIT. The upstream `LICENSE` file should be consulted for its full terms.
- The build workflow retrieves these scripts at build time, pins talloc 2.4.3 for Python 3-compatible build tooling, and compiles Android-compatible PRoot native binaries.

## PRoot runtime

The PRoot source is obtained through the build scripts. Preserve the applicable upstream PRoot license and copyright information for the exact revision fetched by the build. Review `https://github.com/green-green-avk/proot` and `https://github.com/proot-me/proot` before redistributing a release build.

## Debian ARM64 userspace

- Base image: `debian:trixie-slim`, official Debian image for ARM64.
- Debian packages retain their individual licenses and copyright notices. The generated root filesystem includes those notices under `/usr/share/doc/*/copyright` where provided.
- Package documentation: https://www.debian.org/legal/ and https://www.debian.org/releases/trixie/.

## Apache Commons Compress

- Maven coordinate: `org.apache.commons:commons-compress:1.28.0`
- Project: https://commons.apache.org/proper/commons-compress/
- License: Apache License 2.0.

## Android SDK and build tools

The GitHub Actions workflow uses the Android SDK/NDK and Gradle tooling for the build. Their respective license terms apply to those build tools. These tools are installed by the workflow and are not redistributed as standalone installers by this source project.

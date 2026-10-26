# PAX Terminal changelog

## v0.4.3

- Fixed the PRoot binary staging path after native compilation. The upstream `make-proot-for-apk.sh` script installs to `build/root-aarch64/root-apk/bin`; the previous workflow copied from a non-existent `build/root-aarch64-apk/root/bin` directory.
- Added a pre-copy check and diagnostic file listing so any future output-layout mismatch reports the files actually generated.
- Bumped Android app version and APK artifact name to v0.4.3.

## v0.4.2

- Fixed the native PRoot/talloc link failure caused by `telldir` and `seekdir` being unavailable at Android API 21.
- Set the native ARM64 toolchain baseline to Android API 26, matching the app's `minSdk` and the API level required for these Bionic functions.
- Added a workflow guard to verify the API 26 setting before starting the native build.
- Bumped the Android app version and APK artifact name to v0.4.2.

## v0.4.1

- Fixed the native PRoot build workflow: use talloc 2.4.3 instead of legacy talloc 2.1.14, whose bundled Waf fails on Python 3 runners with `except Utils.WafError, e` syntax.
- Added a workflow guard that fails clearly if the talloc version pin could not be applied.
- Expanded the bundled Debian ARM64 developer environment with Deno and Bun runtimes.
- Added global pnpm, TypeScript compiler (`tsc`), and `tsx` for TypeScript scripts.
- Added the `python` command alias and Android SDK/platform-tools packages to the base environment.
- Expanded startup and help text with Node.js, npm, pnpm, Deno, Bun, TypeScript, Python, Git, and SDK commands.
- Added build-time runtime version checks so missing runtime installations fail the container build rather than producing a misleading artifact.
- Bumped the Android app version and APK artifact name to v0.4.1.

## v0.3.0
- Introduced a Debian ARM64 userspace under PRoot with a Debian-backed `pkg` wrapper.
- Added package install/update, project workspace, and background-process helpers.

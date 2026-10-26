# Changelog

## v0.4.0
- Expanded the bundled Debian ARM64 developer environment with Deno and Bun runtimes.
- Added global pnpm, TypeScript compiler (`tsc`), and `tsx` for TypeScript scripts.
- Added the `python` command alias and Android SDK/platform-tools packages to the base environment.
- Expanded startup and help text with Node.js, npm, pnpm, Deno, Bun, TypeScript, Python, Git, and SDK commands.
- Added build-time runtime version checks so failed or missing runtime installations fail the container build instead of producing a misleading artifact.
- Updated the Android app version and APK artifact name to v0.4.0.

## v0.3.0
- Introduced a Debian ARM64 userspace under PRoot with a Debian-backed `pkg` wrapper.
- Added package install/update, project workspace, and background-process helpers.

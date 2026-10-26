# PAX Terminal changelog

## v0.4.1

- Fixed the native PRoot build workflow: use talloc 2.4.3 instead of legacy talloc 2.1.14, whose bundled Waf fails on Python 3 runners with `except Utils.WafError, e` syntax.
- Added a workflow guard that fails clearly if the talloc version pin could not be applied.
- Bumped the Android app version and APK artifact name to v0.4.1.

## v0.4.1
- Expanded the bundled Debian ARM64 developer environment with Deno and Bun runtimes.
- Added global pnpm, TypeScript compiler (`tsc`), and `tsx` for TypeScript scripts.
- Added the `python` command alias and Android SDK/platform-tools packages to the base environment.
- Expanded startup and help text with Node.js, npm, pnpm, Deno, Bun, TypeScript, Python, Git, and SDK commands.
- Added build-time runtime version checks so failed or missing runtime installations fail the container build instead of producing a misleading artifact.
- Updated the Android app version and APK artifact name to v0.4.1.

## v0.3.0
- Introduced a Debian ARM64 userspace under PRoot with a Debian-backed `pkg` wrapper.
- Added package install/update, project workspace, and background-process helpers.

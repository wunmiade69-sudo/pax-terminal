# PAX Terminal v0.4.6

PAX Terminal is an Android terminal app that runs a Debian ARM64 userspace inside the app's private storage using PRoot. This release targets developer workflows on compatible 64-bit ARM Android devices.

The native build workflow pins talloc 2.4.3 to avoid the Python 2-only Waf syntax in the older talloc 2.1.14 build. It also targets Android API 26 for the native PRoot build, matching this app's `minSdk`, because Android's `telldir()` and `seekdir()` are available from API 23 and the talloc test fixture otherwise fails to link at API 21.

## Included development tools

The Gradle APK configuration stores the already-compressed `.tar.gz` root filesystem without recompressing it. This avoids Zipflinger loading the large filesystem archive into a temporary compression byte array, which previously exhausted the Gradle heap. Gradle uses a 4 GiB heap and at most two workers on the hosted runner.

The build workflow creates a Debian root filesystem containing:

- Node.js and npm
- pnpm
- TypeScript (`tsc`) and `tsx`
- Deno
- Bun
- Python 3 (`python` and `python3`), pip and venv support
- Git and OpenSSH client
- Build essentials, Java build utilities, and Debian Android SDK/platform-tools packages where available
- `pkg`, a convenience wrapper backed by Debian `apt-get`

Examples:

```sh
pkg update
pkg install git nodejs npm python deno build-essential
node --version
npm install
npm run dev
pnpm install
pnpm dev
deno task dev
deno test
bun install
bun run dev
npx tsc --noEmit
python -m venv .venv
python -m unittest
git status
```

Use each project's actual `package.json` scripts, `pnpm-workspace.yaml`, `deno.json`, or test configuration. Commands such as `npm run dev` only work when the current project defines a `dev` script. Framework servers can be started in the foreground; use `pax-bg NAME COMMAND ARGS...`, `pax-jobs`, `pax-logs NAME`, and `pax-stop NAME` for supported background commands.

## Package installation

`pkg update` updates Debian package indexes. `pkg install NAME` installs Debian package names through apt. It is a compatibility helper, not Termux's package repository, so package names and versions differ from Termux. Deno, Bun, pnpm, TypeScript, and `tsx` are installed during image construction and checked in the build workflow. Updating the root filesystem package set requires rebuilding the APK; apt can update or install additional packages inside the running environment when network access and free storage are available.

## Android SDK note

The base environment includes Debian's SDK/platform-tools packages when available. Android SDK platform/build-tools downloads, Gradle builds, emulator use, and native builds may require substantial extra storage and memory and are not guaranteed to work for every Android SDK package on-device. The app itself is built by the GitHub Actions workflow. `adb` access to the host phone is not automatically granted to an app running in Android's sandbox.

## Build the APK

1. Push this source tree to the `main` branch of your GitHub repository.
2. Open **Actions** and select **Build PAX Terminal APK**.
3. Wait for the workflow to complete successfully.
4. Download `pax-terminal-v0.4.6-debug-apk` and extract `app-debug.apk`.
5. Install the APK on a compatible ARM64 Android device.

The workflow builds the PRoot runtime and Debian root filesystem on a GitHub-hosted runner, verifies the expected runtime commands, builds the Android app, and checks that the APK contains the Linux runtime assets. The source ZIP is not itself an APK. A successful workflow and on-device testing are still required before describing the build as verified.

## Limits

- Requires a 64-bit ARM Android device (arm64-v8a) for this build.
- Commands run inside PRoot and Android's app sandbox. This does not grant Android root access.
- This is a line-oriented terminal interface, not a full PTY emulator. Interactive full-screen programs, terminal escape handling, and job control may be limited.
- Build tooling availability depends on Debian repositories, network access, architecture support, and device resources.

## License

See `LICENSE` and `THIRD_PARTY_NOTICES.md`. Debian packages and bundled runtime components retain their respective licenses.

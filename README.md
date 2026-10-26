# PAX Terminal

PAX Terminal is an Android terminal app foundation with a custom dark interface and a real local Android shell. It runs `/system/bin/sh` as the app's own Android user; it is not a fake command-output simulator.

## Current status — v0.1.0 foundation

Implemented:
- Native Android app written in Java (no Expo, server, AI service, or API key required).
- PAX Terminal branding and dark terminal-style interface.
- Commands sent to a persistent `/system/bin/sh` process.
- Working-directory tracking between commands, basic help and clear controls.
- GitHub Actions workflow to build and publish the debug APK as an artifact.

Not yet included:
- A full Linux userspace or `pkg`/`apt` package manager.
- Bundled Python, Git, SSH, compilers, or other extra Linux packages.
- A PTY/terminal emulator supporting every interactive TUI program, job control, and full ANSI behavior.
- Root access. Android's app sandbox still applies.

The first build is intentionally a foundation. Available commands depend on the Android OS and its `toybox` utilities. The app-private home folder is used as the initial working directory.

## Build APK on GitHub (free for a public repository under GitHub's applicable Actions rules)

1. Create a public repository named `pax-terminal`.
2. Upload/push this source tree to the repository's `main` branch.
3. Open **Actions** in the repository and select **Build PAX Terminal APK**.
4. Wait for the workflow to finish successfully, then open that workflow run.
5. Under **Artifacts**, download `pax-terminal-debug-apk` and extract it to get `app-debug.apk`.
6. Transfer the APK to your Android phone and install it. Android may ask permission to install apps from the browser or file manager used to open it.

This workflow creates a debug APK for testing. A stable public release/update channel should use a private signing key stored in GitHub Actions secrets; do not upload a keystore or passwords to a public repository.

## License note

This initial PAX-specific code is released under the MIT License in this repository. If Termux or other third-party code is added in later versions, comply with their respective licenses and preserve required notices.

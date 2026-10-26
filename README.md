# PAX Terminal

**PAX Terminal v0.2.0** is a native Android terminal foundation with a custom dark interface and a persistent, real `/system/bin/sh` process. It is free to build and use for local shell commands; no AI service or API key is required.

## What changed in v0.2.0

- More polished PAX Terminal interface and live shell-status indicator.
- Command history with Up/Down navigation when a hardware keyboard sends arrow keys.
- Exit-code indicator after shell commands.
- Output buffer limit to prevent very long sessions from growing the UI indefinitely.
- Android 15 edge-to-edge insets so controls stay clear of system bars on recent devices.
- Built-in `help`, `clear`, `history`, `files`, `pax-files`, `pax-about`, and `pax-version` helpers.
- File browser for folders readable by the app, with selectable text previews.
- Import an individual file into PAX Terminal's HOME folder using Android's document picker.
- Export a top-level HOME file using Android's document picker.
- Explicit Android SDK setup, APK verification, pull-request builds, and versioned artifact name in GitHub Actions.

## Current technical status

Implemented in the source:

- Native Android Java app (no React Native, hosted backend, or AI API dependency).
- `com.paxterminal.app` package ID, app-private `HOME`, persistent `/system/bin/sh`, and working-directory tracking.
- Android Storage Access Framework import/export, without requesting broad all-files storage permission.
- GitHub Actions debug APK build workflow.

Not yet implemented or verified:

- This is **not** a full Linux distribution or a Termux package environment. `pkg`/`apt`, Python, Git, SSH, compilers, and extra packages are not bundled.
- It does not yet use a PTY/terminal emulator. Full-screen terminal programs, job control, terminal escape sequences, and interactive Ctrl+C are not guaranteed.
- No root access. Commands run under this app's Android UID and remain subject to Android's sandbox and device policy.
- The Android app has been source-updated, but this source ZIP is **not a compiled or device-tested APK**. The GitHub Actions workflow must pass before an installable artifact is available.

## Try the shell after installing

Start with these commands, one at a time:

```sh
pwd
echo PAX Terminal works
mkdir demo
cd demo
echo hello from PAX > hello.txt
cat hello.txt
```

Open **FILES** to browse the current folder. Use **MORE → Go to HOME directory** to return to the app-private workspace, or **MORE → Import/Export** to transfer a single file using Android's document picker. Available system commands vary by Android device.

## Build the APK on GitHub

1. Push this source tree to the `main` branch of a GitHub repository.
2. Open the repository's **Actions** tab and choose **Build PAX Terminal APK** if needed.
3. Open the latest successful workflow run.
4. Under **Artifacts**, download `pax-terminal-v0.2.0-debug-apk` and extract it to obtain `app-debug.apk`.
5. Transfer the APK to your Android phone and install it. Android may ask the browser or file manager for permission to install unknown apps.

The workflow uses GitHub-hosted runners and the Android SDK setup action. Public repositories are generally eligible for free standard GitHub-hosted runner usage under GitHub's current policies; check [GitHub Actions billing](https://docs.github.com/en/actions/concepts/billing-and-usage) for the current rules. This is a **debug APK** intended for testing. A proper release/update path should use a private signing key saved in GitHub Actions secrets; never commit a keystore or its passwords to a public repository.

## License

The PAX-specific source in this initial project is released under the MIT License. If third-party code is added later, follow its license and preserve all required notices.

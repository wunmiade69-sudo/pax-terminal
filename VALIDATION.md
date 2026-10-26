# v0.4.3 validation status

This update fixes the latest reported workflow failure. The native PRoot compile reached the end, but the workflow tried to copy the libraries from a path that the upstream script does not create. The upstream config defines `INSTALL_ROOT="$BUILD_DIR/root-$ARCH/root"`, and `make-proot-for-apk.sh` uses `PREFIX="$INSTALL_ROOT-apk"`, so for ARM64 the output directory is `build/root-aarch64/root-apk/bin`. The workflow now copies from that path and prints generated files if the expected binaries are missing.

The local source validation checks source/configuration consistency and XML parsing. It does not run the complete Android NDK, Docker rootfs, or Gradle APK build. A successful GitHub Actions run and on-device test are still required before the APK can be called verified.

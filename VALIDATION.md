# v0.4.5 source validation status

This release addresses the reported Gradle `java.lang.OutOfMemoryError: Java heap space` in `CompressAssetsWorkAction` / Zipflinger. The Debian filesystem is already a `.tar.gz` archive, so the Android Gradle configuration now puts the `gz` extension in `androidResources.noCompress` to avoid recompressing the large archive inside the APK. Gradle heap is set to 4 GiB, parallel mode is disabled, and the worker count is capped at two in CI.

Source validation checks terminal implementation markers, Android manifest/resource XML, app version, native PRoot build fixes, no-compress configuration, Gradle memory settings, and workflow/artifact consistency. These checks do not execute the complete Android NDK/Docker rootfs/Gradle APK build. A successful GitHub Actions run and on-device test are still required before the APK can be called verified.

The verification workflow now writes `unzip -Z1` output to a file before exact-path checks, and reports missing APK outputs or missing runtime entries explicitly. These checks still do not replace a successful CI run or on-device test.

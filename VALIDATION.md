# v0.4.0 validation status

Source checks cover Android manifest/resource XML parsing, expected runtime and developer-command references, and version consistency. The GitHub workflow builds the ARM64 root filesystem and fails if the runtime version checks do not pass, then builds and inspects the APK.

This source package has not yet been compiled by GitHub Actions or tested on a physical Android device. Do not treat the presence of a workflow or source validation as proof that the APK works; review the workflow result and test the installed artifact.

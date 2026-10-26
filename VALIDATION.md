# v0.4.2 validation status

This update addresses the latest reported native build failure. The talloc `replace_testsuite` target was compiling against Android API 21, but Android Bionic exposes `telldir()` and `seekdir()` starting at API 23. The PAX Terminal app's minimum Android API is 26, so the workflow now sets the PRoot/talloc native build baseline to API 26 and verifies the change before building.

Local source validation checks XML resources, terminal command references, runtime setup, the app version, the talloc dependency pin, and the native API setting. The full native/Android build and device runtime still require a successful GitHub Actions run and a physical-device test. This source package has not itself produced a verified APK.

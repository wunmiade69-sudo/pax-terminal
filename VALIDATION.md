# v0.4.1 validation status

This update addresses the reported GitHub Actions failure by changing the Android PRoot build dependency from talloc 2.1.14 (legacy Waf/Python 2 syntax) to talloc 2.4.3. The workflow checks that a talloc 2.4.3 pin is present before compiling PRoot.

Source validation checks Android manifest/resources, terminal command references, runtime setup, the version number, and the talloc fix in the build workflow. The full Android build and device runtime still require a successful GitHub Actions run and a physical-device test. This source package has not itself produced a verified APK.

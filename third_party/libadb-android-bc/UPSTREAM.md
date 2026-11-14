# Bundled LibADB Android BC

Source: https://github.com/osservatorionessuno/libadb-android-bc
Commit: b6c301dab4b83ae5892a7e68fdb96b6554e046a8 (3.5.1 source).
This project elects Apache-2.0 from the upstream dual license. Original SPDX headers and other notices are preserved; see COPYING and LICENSES.

Only the library sources and protocol/crypto tests are bundled. Gradle configuration targets this application's API 30–34 setup; USB integration/example UI is not exposed by OpenWorkbench. Transport and pairing timeouts/cancellation are tightened locally. OpenWorkbench limits all ADB connections to loopback and exposes only its fixed helper startup command, not a general shell.

OpenWorkbench 0.15.1 validates and exposes the authenticated pairing server GUID (ADB_DEVICE_GUID, type 1) so automatic activation can match the TLS-connect service to the device just paired. The GUID is session-only; peer keys and pairing codes are not logged. Parser regression tests cover malformed type, length and encoding. This follows [AOSP's pairing-to-connect matching](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/client/adb_wifi.cpp).

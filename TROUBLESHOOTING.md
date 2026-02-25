# TROUBLESHOOTING

## Build fails with "SDK location not found"
Set `local.properties` with `sdk.dir=<path-to-android-sdk>`.

## Build fails before compilation with unresolved provider
If app depends on `:shell-loader:stub` as compile-only and AGP resolves no artifact, ensure dependency is regular implementation in `app/build.gradle`.

## PROOT bootstrap diagnostics
Search logcat for:
- `PROOT_BOOTSTRAP ABI_SELECTED`
- `PROOT_BOOTSTRAP URL_NORMALIZED`
- `PROOT_BOOTSTRAP PRECHECK_FAIL`
- `PROOT_PREFLIGHT_FAIL:`

These identify ABI selection, URL normalization, and filesystem prerequisite failures.

## First-run setup reports missing binaries
Run setup again after clearing app data. The app now checks:
- `$files/usr/bin/proot` (exists + executable)
- `$files/distro/bin/busybox` (exists + executable)
- `$files/distro/bin/sh` (exists + executable)
- `$files/usr/tmp` (writable)

If any check fails, setup stops with explicit reason codes.

## Bootstrap URL errors
The app validates host/scheme and normalizes duplicated slashes in URL path.
If metadata contains malformed URLs or unsupported host, remote setup is rejected.

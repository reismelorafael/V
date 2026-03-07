# BUILDING

## CLI prerequisites
- JDK 17 (baseline runtime)
- Android SDK Platform 35 + Build Tools 35.0.0
- NDK 27.2.12479018
- CMake 3.22.1

> Baseline único de CMake: host (raiz) e Android JNI usam 3.22.1 para manter paridade de toolchain.

## Setup environment (example)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK_ROOT=/workspace/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
```

If `local.properties` is missing, `./tools/gradle_with_jdk21.sh` now auto-writes `sdk.dir`
from `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) when the directory exists.

## Build commands
```bash
./gradlew --version
./gradlew clean
./gradlew :app:assembleDebug --stacktrace
./gradlew :app:assembleRelease --stacktrace
./gradlew :app:lintDebug --stacktrace
```

## ABI policy
Configured by `APP_ABI_POLICY` and `SUPPORTED_ABIS` in `gradle.properties`.
Default is arm64-only:
- `APP_ABI_POLICY=arm64-only`
- `SUPPORTED_ABIS=arm64-v8a`

To include 32-bit ARM:
```bash
./gradlew -PAPP_ABI_POLICY=with-32bit -PSUPPORTED_ABIS=arm64-v8a,armeabi-v7a :app:assembleDebug
```


## Supported version matrix
All values below are defaults from `gradle.properties` and can be overridden with `-P`.

| Area | Property | Min | Default | Max/Policy |
|---|---|---:|---:|---:|
| Compile SDK | `compile.api` → fallback `COMPILE_API` | 35 | 35 | follows Android baseline updates |
| Target SDK | `target.api` → fallback `TARGET_API` | 35 (`release.min.target.api`) | 35 | follows Android baseline updates |
| Build Tools | `tools.version` → fallback `TOOLS_VERSION` | 35.0.0 | 35.0.0 | keep aligned with compile SDK |
| NDK | `ndk.version` → fallback `NDK_VERSION` | 23.x | 27.2.12479018 | latest validated in CI |
| CMake | `cmake.version` → fallback `CMAKE_VERSION` | 3.22.1 | 3.22.1 | keep host+JNI parity |
| Java language level | `java.language.version` → fallback `JAVA_LANGUAGE_VERSION` | 17 | 17 | 21 (when toolchain validated) |
| Gradle runtime JVM | `gradle.java.runtime.version` → fallback `GRADLE_JAVA_RUNTIME_VERSION` | 17 | 17 | `gradle.max.runtime.java.version` (default 21) |


Property precedence rule (to avoid config drift):
- Canonical property names use dotted lowercase keys (for example: `compile.api`, `tools.version`).
- Legacy aliases in uppercase snake case (for example: `COMPILE_API`, `TOOLS_VERSION`) are fallback-only for backward compatibility.
- When a legacy alias is used, the Gradle bootstrap emits a deprecation warning and continues.

Strictness control by pipeline context:
- `-PbuildStrict=false` (default): local/debug mode; validations emit warnings where allowed.
- `-PbuildStrict=true`: official CI/release mode; validations are blocking.

## CI blocking checks by pipeline type
- Official release CI (`buildStrict=true`):
  - `verifyGradleRuntimeJvm` (blocking)
  - API/ABI validations (`verifyMinApiAbiCompatibility`, release target checks) (blocking)
  - `verifyBootstrapAssets` + `verifyRepoFileDependencies` (blocking; requires Python)
- Local/dev or debug CI (`buildStrict=false`):
  - Same checks run, but max-JVM/API-ABI non-release gates can warn.
  - Python-dependent checks are skipped with warning if Python is unavailable.

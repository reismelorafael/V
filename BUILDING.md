# BUILDING

## CLI prerequisites
- JDK 17
- Android SDK Platform 34 + Build Tools 34.0.0
- NDK 27.2.12479018
- CMake 3.22.1

> Baseline único de CMake: host (raiz) e Android JNI usam 3.22.1 para manter paridade de toolchain.

## Setup environment (example)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK_ROOT=/workspace/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
```

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

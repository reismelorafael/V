# Build Environment Alignment (Android / Gradle / JDK)

Checklist objetivo para resolver “não compila” por desalinhamento de SDK/JDK/Gradle.

## Correções cobertas no projeto
- O build agora usa propriedades centralizadas para `compileSdk`, `buildTools`, `CMake`, `NDK` e Java em todos os módulos Android.
- Baseline operacional de release definido: `TARGET_API=35` com guardrail `RELEASE_MIN_TARGET_API=34` para bloquear releases abaixo do mínimo oficial.
- O módulo `shell-loader:stub` não fixa mais API 35 hardcoded: agora herda do root (`COMPILE_API` / `TOOLS_VERSION`).
- `KOTLIN_VERSION` e `AGP_VERSION` também podem ser ajustados por propriedade.

## 30 checks rápidos
1. Android API (`platforms;android-<api>`) instalada.
2. Build-Tools (`build-tools;<versão>`) instalada.
3. `compileSdk` alinhado ao SDK instalado.
4. `buildToolsVersion` alinhado ao Build-Tools instalado.
5. `JAVA_HOME` apontando para JDK 17+.
6. `java -version` confirmando versão esperada.
7. `./gradlew --version` usando o mesmo Java.
8. `local.properties` com `sdk.dir=...` válido.
9. Permissão de leitura/escrita no SDK.
10. `platform-tools` instalado (adb/aapt2 auxiliares).
11. CMake na versão exigida (`3.22.1`).
12. NDK na versão exigida (`27.2.12479018`) quando há código nativo.
13. Sem conflito de proxy/rede para baixar wrapper e dependências.
14. `distributionUrl` do wrapper válido.
15. Cache Gradle íntegro (limpar `.gradle` em caso de corrupção).
16. Sem mismatch entre AGP e Gradle wrapper.
17. Sem mismatch entre Kotlin plugin e AGP.
18. Sem fixar `compileSdk` diferente em submódulo.
19. Sem fixar `buildToolsVersion` diferente em submódulo.
20. `minSdk/targetSdk` consistentes entre módulos críticos.
21. Dependências AndroidX resolvendo sem conflito de metadata.
22. Repositórios `google()` e `mavenCentral()` habilitados.
23. Assinatura debug/release com keystore disponível.
24. Espaço em disco suficiente para cache/SDK.
25. Relógio/sistema de certificados do host corretos (TLS).
26. Sem bloqueio por antivírus/firewall no download.
27. Em CI, exportar variáveis de ambiente antes do Gradle.
28. Em Termux, validar path do cmdline-tools no `PATH`.
29. Em builds nativos, toolchain clang/cmake detectável.
30. Se ainda falhar, rodar com `--stacktrace --info` e corrigir causa raiz explícita.

## Exemplos de override

```bash
./gradlew :app:assembleDebug \
  -PCOMPILE_API=35 \
  -PTARGET_API=35 \
  -PRELEASE_MIN_TARGET_API=34 \
  -PTOOLS_VERSION=35.0.0 \
  -PJAVA_LANGUAGE_VERSION=17 \
  -PCMAKE_VERSION=3.22.1
```

```bash
./gradlew :app:assembleDebug \
  -PAGP_VERSION=8.5.2 \
  -PKOTLIN_VERSION=1.9.24
```

## Comandos de diagnóstico

```bash
./tools/check_android_toolchain.sh
java -version
./gradlew --version
./gradlew :app:tasks --all
./tools/check_target_api_baseline.sh gradle.properties
./gradlew :app:assembleDebug --stacktrace --info
```

## Wrapper JDK resiliente

- `tools/gradle_with_jdk21.sh` agora tenta automaticamente JDK 21 e 17 em paths comuns antes de falhar.
- O script também dispara uma validação rápida de toolchain (`--quick`) para antecipar erros de JNI/NDK/CMake/SDK.


## Notas de build nativo (arm64-v8a)

- `app/build.gradle` fixa `defaultConfig.externalNativeBuild.cmake.cppFlags` com `-march=armv8-a` para tornar explícita a arquitetura alvo e evitar variação silenciosa por mudança de toolchain padrão.
- `NATIVE_MCPU` pode ser fornecido via `-PNATIVE_MCPU=<cpu>` para tuning específico por perfil/dispositivo quando necessário, sem remover o baseline portátil `-march=armv8-a`.
- O projeto valida `ext.ndkVersion` com a task `verifyArm64ToolchainCompatibility`, exigindo NDK r23+ para garantir suporte consistente ao alvo `arm64-v8a`.
- Em CI, a verificação `Validate native binaries are arm64-v8a` inspeciona os `.so` gerados e falha se não forem ELF `Machine: AArch64`.

### Comandos recomendados (regressão)

```bash
./gradlew verifyArm64ToolchainCompatibility -PNDK_VERSION=27.2.12479018
./gradlew :app:assembleDebug -PNDK_VERSION=27.2.12479018
```

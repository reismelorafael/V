# Build Environment Alignment (Android / Gradle / JDK)

Checklist objetivo para resolver “não compila” por desalinhamento de SDK/JDK/Gradle.

## Correções cobertas no projeto
- O build agora usa propriedades centralizadas para `compileSdk`, `buildTools`, `CMake`, `NDK` e Java em todos os módulos Android.
- Baseline operacional de release definido: `TARGET_API=35` com guardrail `RELEASE_MIN_TARGET_API=34` para bloquear releases abaixo do mínimo oficial.
- O módulo `shell-loader:stub` não fixa mais API 35 hardcoded: agora herda do root (`COMPILE_API` / `TOOLS_VERSION`).
- `KOTLIN_VERSION` e `AGP_VERSION` também podem ser ajustados por propriedade.


## Política explícita de JDK para Gradle

- Runtime padrão obrigatório do Gradle: `GRADLE_JAVA_RUNTIME_VERSION=17` (ver `gradle.properties`).
- Limite superior temporário de compatibilidade: `GRADLE_MAX_RUNTIME_JAVA_VERSION=21`.
- O build raiz registra `verifyGradleRuntimeJvm` e também valida automaticamente em tarefas de build/assemble/test/lint/verify.
- Erro de versão incompatível orienta ajuste de `JAVA_HOME` ou `org.gradle.java.home` com mensagem direta.

Configuração recomendada:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./tools/gradle_with_jdk21.sh verifyGradleRuntimeJvm
```

Ou em `~/.gradle/gradle.properties`:

```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk
```


## Política de suporte (API x ABI)

Política explicitada no build raiz (`build.gradle`):

- `APP_ABI_POLICY=arm64-only` (padrão atual de distribuição).
- `SUPPORTED_ABIS=arm64-v8a` quando `arm64-only`.
- `MIN_API` deve ser compatível com a maior exigência mínima das ABIs empacotadas.

### Matriz de compatibilidade para release/distribuição

| Política | SUPPORTED_ABIS esperado | Baseline de API (mínimo técnico) | Recomendação de distribuição |
|---|---|---:|---|
| `arm64-only` | `arm64-v8a` | `MIN_API >= 21` | Padrão recomendado para footprint menor e foco em dispositivos modernos |
| `with-32bit` | inclui ao menos uma ABI 32-bit (`armeabi-v7a` ou `x86`) | `MIN_API >= 21` se incluir `arm64-v8a`/`x86_64`; caso só 32-bit, `>= 16` | Usar apenas quando houver necessidade explícita de legado 32-bit |

> No estado atual do projeto, a distribuição oficial permanece em **ARM64-only**, com `MIN_API=23`, portanto compatível com `arm64-v8a`.

## 30 checks rápidos
1. Android API (`platforms;android-<api>`) instalada.
2. Build-Tools (`build-tools;<versão>`) instalada.
3. `compileSdk` alinhado ao SDK instalado.
4. `buildToolsVersion` alinhado ao Build-Tools instalado.
5. `JAVA_HOME` apontando para JDK 17+.
6. `java -version` confirmando versão esperada.
7. `./tools/gradle_with_jdk21.sh --version` usando o mesmo Java.
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
27. Em CI, usar `./tools/gradle_with_jdk21.sh` (ou exportar `JAVA_HOME` 17/21 antes do Gradle).
28. Em Termux, validar path do cmdline-tools no `PATH`.
29. Em builds nativos, toolchain clang/cmake detectável.
30. Se ainda falhar, rodar com `--stacktrace --info` e corrigir causa raiz explícita.

## Exemplos de override

```bash
./tools/gradle_with_jdk21.sh :app:assembleDebug \
  -PCOMPILE_API=35 \
  -PTARGET_API=35 \
  -PMIN_API=23 \
  -PAPP_ABI_POLICY=arm64-only \
  -PSUPPORTED_ABIS=arm64-v8a \
  -PRELEASE_MIN_TARGET_API=34 \
  -PTOOLS_VERSION=35.0.0 \
  -PJAVA_LANGUAGE_VERSION=17 \
  -PCMAKE_VERSION=3.22.1
```

```bash
./tools/gradle_with_jdk21.sh :app:assembleDebug \
  -PAGP_VERSION=8.5.2 \
  -PKOTLIN_VERSION=1.9.24
```

## Comandos de diagnóstico

```bash
./tools/check_android_toolchain.sh
java -version
./tools/gradle_with_jdk21.sh --version
./tools/gradle_with_jdk21.sh :app:tasks --all
./tools/check_target_api_baseline.sh gradle.properties
./tools/gradle_with_jdk21.sh :app:assembleDebug --stacktrace --info
```


## Obfuscação (R8/ProGuard) — símbolos estáveis de release

Para habilitar obfuscação sem quebrar reflexão/entry points declarativos, os símbolos abaixo devem permanecer estáveis:

- `com.vectras.qemu.MainSettingsManager$AppPreferencesFragment` (referenciado por `android:name`/`android:fragment` em XML).
- `com.vectras.qemu.MainSettingsManager$QemuPreferencesFragment` (referenciado por `android:fragment` em XML).
- `com.vectras.vm.settings.LanguageModulesActivity` (`android:targetClass` em XML de preferências).
- `com.vectras.vm.settings.VNCSettingsActivity` (`android:targetClass` em XML de preferências).
- `com.vectras.vm.settings.X11DisplaySettingsActivity` (`android:targetClass` em XML de preferências).
- `com.vectras.vm.settings.ImportExportSettingsActivity` (`android:targetClass` em XML de preferências).
- `com.vectras.vm.settings.UpdaterActivity` (`android:targetClass` em XML de preferências).
- `com.vectras.vm.x11.LoriePreferences` (`android:settingsActivity` em XML de acessibilidade).
- `com.antlersoft.android.bc.BCActivityManagerV5`, `BCHapticDefault`, `BCMotionEvent4`, `BCMotionEvent5`, `BCStorageContext7`, `BCStorageContext8` (carregadas via `ClassLoader.loadClass(...)` em runtime).

Validação mínima de pipeline para regressão de reflexão/obfuscação:

```bash
./tools/gradle_with_jdk21.sh :app:assembleRelease --stacktrace
```

Após gerar a APK/AAB de release, execute smoke test de inicialização em dispositivo/emulador (instalação + abertura da activity principal + navegação até tela de preferências).

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
./tools/gradle_with_jdk21.sh verifyArm64ToolchainCompatibility -PNDK_VERSION=27.2.12479018
./tools/gradle_with_jdk21.sh :app:assembleDebug -PNDK_VERSION=27.2.12479018
```


## Estratégia de longo prazo (wrapper/AGP e matriz de validação)

Para ampliar suporte oficial a JVMs mais novas no ambiente, avaliar upgrade conjunto:

1. Atualizar `gradle/wrapper/gradle-wrapper.properties` para versão de Gradle oficialmente compatível com AGP alvo.
2. Atualizar `AGP_VERSION` em `gradle.properties` e validar compatibilidade com Kotlin plugin.
3. Rodar matriz mínima antes de promover baseline:
   - JDK 17 + `./tools/gradle_with_jdk21.sh clean assembleDebug test`
   - JDK alvo do ambiente + `./tools/gradle_with_jdk21.sh clean assembleDebug test`
4. Só elevar `GRADLE_JAVA_RUNTIME_VERSION`/`GRADLE_MAX_RUNTIME_JAVA_VERSION` após matriz verde.

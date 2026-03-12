# External Forks & Build Inputs Matrix

## Objetivo
Mapear **o que vem de fora do repositório** durante compilação/execução e apontar refatorações para reduzir processo desnecessário, aumentar determinismo e unificar trilhas de performance.

## 1) Fontes externas usadas no build

### 1.1 Dependências Maven (resolvidas fora do repo)
Repositórios remotos ativos no Gradle raiz:
- `mavenCentral()`
- `google()`

Dependências externas declaradas no app:
- `androidx.appcompat:appcompat:1.7.1`
- `com.google.android.material:material:1.13.0`
- `androidx.annotation:annotation:1.9.1`
- `androidx.core:core-ktx:1.13.1`
- `androidx.drawerlayout:drawerlayout:1.2.0`
- `androidx.preference:preference-ktx:1.2.1`
- `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- `androidx.viewpager:viewpager:1.1.0`
- `com.google.code.gson:gson:2.13.2`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `androidx.window:window:1.5.1`
- `org.apache.commons:commons-compress:1.28.0`
- `androidx.activity:activity-ktx:1.9.2`
- `androidx.constraintlayout:constraintlayout:2.2.1`
- `androidx.documentfile:documentfile:1.1.0`
- `androidx.work:work-runtime:2.9.1`
- `com.github.bumptech.glide:glide:4.16.0`
- `com.github.bumptech.glide:compiler:4.16.0` (annotation processor)

Dependências externas declaradas em outros módulos:
- `terminal-view/build.gradle`: `androidx.annotation:annotation:1.9.1`
- `shell-loader/stub/build.gradle`: `androidx.annotation:annotation:1.9.1`

### 1.2 Código forkado/vendorizado dentro do repo
Embora estejam no repo, esses módulos têm origem de projetos externos (import/fork histórico):
- `terminal-emulator/` com pacote `com.termux.terminal`
- `terminal-view/` com pacote `com.termux.view`
- Integrações X11/Termux no app (`com.termux.*`)

### 1.3 Artefatos externos exigidos em runtime de integração
- `shell-loader/build.gradle` descreve obtenção/instalação de APK `termux-x11` via workflow GitHub Actions externo.
- Documentação e notices citam upstreams externos: QEMU, Termux, 3DFX patch, Glide, Gson, OkHttp etc.

## 2) O que está efetivamente “fora do repositório”

### Fora no momento da compilação
1. Artefatos Maven/Google (`google()` + `mavenCentral()`).
2. Android SDK/NDK/CMake instalados localmente (toolchain externo ao Git).

### Fora no momento de operação (dependendo do fluxo)
1. APK de `termux-x11` (download de workflow externo).
2. Serviços/URLs externos referenciados para integração/usuário final.

## 3) Refatoração proposta para reduzir dependência externa (roadmap)

### Fase A — Inventário determinístico (curto prazo)
1. Congelar lista de coordenadas Maven com lockfile (dependency locking Gradle).
2. Criar verificador CI para detectar coordenada nova sem atualização explícita de inventário.
3. Versionar matriz “externo obrigatório vs opcional” por módulo.

### Fase B — Unificação de núcleo de matrizes (médio prazo)
1. Consolidar operações de matriz/memória quente em um único módulo nativo autoral (C) com ABI estável.
2. Expor API mínima Java/JNI para operações críticas (mul/add/transpose/accumulate/checksum).
3. Seleção determinística de backend por arquitetura (`arm64`, `x86_64`) com fallback puro.
4. Manter caminhos sem alocação em hot-path (buffers reutilizáveis).

### Fase C — Performance + redução de overhead (médio/longo)
1. Eliminar duplicação de loops em Java para rotinas já cobertas pelo core nativo.
2. Padronizar benchmarks por perfil (`LOW_LATENCY`, `THROUGHPUT`, `DETERMINISTIC`) e registrar ledger.
3. Medir ganho por métricas fixas: latência p95/p99, throughput, footprint de memória, jitter.

## 4) Checklist prático para “arquivos externos de outros forks”

Quando houver build que depende de upstream externo, validar:
1. A origem está registrada em `THIRD_PARTY_NOTICES.md`.
2. O módulo local vendorizado/forkado tem fronteira clara em docs (`FILES_MAP.md` / README local).
3. Há fallback quando artefato externo não estiver disponível.
4. A versão externa está pinada (sem floating tag).

## 5) Comandos de auditoria usados para montar este arquivo

```bash
rg -n "(includeBuild|maven|jcenter|google\(|classpath|implementation|api|git|fork|submodule|external|project\()" -g"*.gradle*" -g"settings*"
rg -n "https://github.com|git@|fork|upstream|termux" docs app shell-loader terminal-emulator terminal-view build.gradle settings.gradle README.md THIRD_PARTY_NOTICES.md
```

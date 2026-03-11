# External Dependency Hotspots (Performance/GC)

Relatório gerado automaticamente a partir de `app/build.gradle` + imports em `app/src`. Foco: pontos para refatoração visando reduzir GC, overhead e fricção de runtime.

## Dependências externas detectadas

- `implementation` (produção) → `androidx.appcompat:appcompat:1.7.1`
- `implementation` (produção) → `com.google.android.material:material:1.13.0`
- `implementation` (produção) → `androidx.annotation:annotation:1.9.1`
- `implementation` (produção) → `androidx.core:core-ktx:1.13.1`
- `implementation` (produção) → `androidx.drawerlayout:drawerlayout:1.2.0`
- `implementation` (produção) → `androidx.preference:preference-ktx:1.2.1`
- `implementation` (produção) → `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- `implementation` (produção) → `androidx.viewpager:viewpager:1.1.0`
- `implementation` (produção) → `com.google.code.gson:gson:2.13.2`
- `implementation` (produção) → `com.squareup.okhttp3:okhttp:4.12.0`
- `implementation` (produção) → `androidx.window:window:1.5.1`
- `implementation` (produção) → `org.apache.commons:commons-compress:1.28.0`
- `implementation` (produção) → `androidx.activity:activity-ktx:1.9.2`
- `implementation` (produção) → `androidx.constraintlayout:constraintlayout:2.2.1`
- `implementation` (produção) → `androidx.documentfile:documentfile:1.1.0`
- `implementation` (produção) → `androidx.work:work-runtime:2.9.1`
- `implementation` (produção) → `com.github.bumptech.glide:glide:4.16.0`
- `annotationProcessor` (build-time) → `com.github.bumptech.glide:compiler:4.16.0`
- `testImplementation` (teste-local) → `junit:junit:4.13.2`
- `testImplementation` (teste-local) → `org.robolectric:robolectric:4.14.1`
- `testImplementation` (teste-local) → `androidx.test:core:1.6.1`
- `testImplementation` (teste-local) → `org.mockito:mockito-core:5.12.0`
- `testImplementation` (teste-local) → `org.mockito:mockito-inline:5.2.0`
- `androidTestImplementation` (teste-instrumentado) → `androidx.test.ext:junit:1.3.0`
- `androidTestImplementation` (teste-instrumentado) → `androidx.test.espresso:espresso-core:3.7.0`

## Conceitos (AndroidX, JDK, SDK e tipos)

- **AndroidX (Jetpack)**: conjunto de bibliotecas Android mantidas pelo Google, distribuídas via Maven (não fazem parte do Java SE puro).
- **Android SDK**: APIs da plataforma Android (`android.*`) fornecidas pelo sistema e pelo compile SDK; não aparecem como coordenadas Maven em `dependencies {}`.
- **JDK/JVM**: toolchain de compilação/execução Java/Kotlin no build e testes locais; também não aparece como dependência de app em `build.gradle`.
- **Tipos de dependência Gradle**: `implementation` (runtime de produção), `annotationProcessor` (build-time), `testImplementation` (teste local), `androidTestImplementation` (teste instrumentado).
- **Foco de otimização**: para reduzir GC/overhead, priorizar primeiro bibliotecas de `implementation` em caminhos quentes de UI, I/O, rede e parse.

## Classificação conceitual por dependência

- `androidx.appcompat:appcompat:1.7.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `com.google.android.material:material:1.13.0`: Material Components: toolkit de UI do Android para componentes visuais padronizados.
- `androidx.annotation:annotation:1.9.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.core:core-ktx:1.13.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.drawerlayout:drawerlayout:1.2.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.preference:preference-ktx:1.2.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.viewpager:viewpager:1.1.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `com.google.code.gson:gson:2.13.2`: Serialização JSON em runtime (parse/mapeamento de objetos), frequentemente sensível a alocação/GC.
- `com.squareup.okhttp3:okhttp:4.12.0`: Stack HTTP cliente (rede, pooling e conexões), impacta latência, throughput e uso de memória.
- `androidx.window:window:1.5.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `org.apache.commons:commons-compress:1.28.0`: Utilitários Java de propósito geral (aqui: compressão/arquivamento), com impacto de I/O e buffers.
- `androidx.activity:activity-ktx:1.9.2`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.constraintlayout:constraintlayout:2.2.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.documentfile:documentfile:1.1.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.work:work-runtime:2.9.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `com.github.bumptech.glide:glide:4.16.0`: Pipeline de imagem (decode/cache/transform), tipicamente um hotspot de heap e GC em listas.
- `com.github.bumptech.glide:compiler:4.16.0`: Pipeline de imagem (decode/cache/transform), tipicamente um hotspot de heap e GC em listas.
- `junit:junit:4.13.2`: Framework de testes unitários (não embarca em runtime de produção).
- `org.robolectric:robolectric:4.14.1`: Ambiente de teste Android em JVM local (somente testes).
- `androidx.test:core:1.6.1`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `org.mockito:mockito-core:5.12.0`: Mocking para testes unitários/instrumentados (somente testes).
- `org.mockito:mockito-inline:5.2.0`: Mocking para testes unitários/instrumentados (somente testes).
- `androidx.test.ext:junit:1.3.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.
- `androidx.test.espresso:espresso-core:3.7.0`: Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.

## Itens priorizados para refatoração low-level autoral

### #1 `com.google.code.gson:gson:2.13.2` | prioridade=230
- Módulo autoral alvo: `vectra_json_det`
- Entrega low-level: parser JSON autoral orientado a tokens (scanner determinístico), sem reflexão dinâmica
- Arquivos impactados agora: 10
  - `app/src/main/java/com/vectras/vm/CqcmActivity.java`
  - `app/src/main/java/com/vectras/vm/ExportRomActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMManager.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/DataRoms.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreFragment.java`
  - `... +4 arquivos`
- Passos de migração determinística:
  - Mapear schemas fixos de VM metadata e store payloads
  - Implementar scanner de bytes com tabela de estados e arena de strings reutilizável
  - Trocar parsing quente em JSONUtils/VMManager por caminho autoral

### #2 `com.github.bumptech.glide:glide:4.16.0` | prioridade=230
- Módulo autoral alvo: `vectra_img_det`
- Entrega low-level: pipeline autoral de decode/caching com política de blocos fixos
- Arquivos impactados agora: 10
  - `app/src/main/java/com/vectras/vm/ImagePrvActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdapterSearch.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdpater.java`
  - `... +4 arquivos`
- Passos de migração determinística:
  - Criar cache slab para thumbnails e capas
  - Converter decode para tamanho-alvo fixo por viewport
  - Migrar adapters de listagem para loader autoral

### #3 `androidx.work:work-runtime:2.9.1` | prioridade=226
- Módulo autoral alvo: `vectra_sched_det`
- Entrega low-level: scheduler autoral orientado a state-machine com reexecução idempotente
- Arquivos impactados agora: 6
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadCoordinator.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadStateReconciler.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadViewModel.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadWorker.java`
  - `app/src/main/java/com/vectras/vm/importer/ImportSessionWorker.java`
- Passos de migração determinística:
  - Unificar jobs de download/import em fila única
  - Persistir estado mínimo em estrutura compacta
  - Adicionar reconciliador com backoff determinístico

### #4 `com.squareup.okhttp3:okhttp:4.12.0` | prioridade=222
- Módulo autoral alvo: `vectra_net_det`
- Entrega low-level: cliente HTTP autoral com pool fixo de conexões e buffers reaproveitáveis
- Arquivos impactados agora: 2
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadWorker.java`
- Passos de migração determinística:
  - Introduzir dispatcher determinístico com fila fixa
  - Separar handshake/retry em estado explícito sem alocação por request
  - Migrar DownloadWorker e RomInfo para camada autoral

### #5 `org.apache.commons:commons-compress:1.28.0` | prioridade=221
- Módulo autoral alvo: `vectra_archive_det`
- Entrega low-level: stream de tar/compactação autoral com buffers fixos e cópia zero quando possível
- Arquivos impactados agora: 1
  - `app/src/main/java/com/vectras/vm/utils/TarUtils.java`
- Passos de migração determinística:
  - Implementar leitura de headers em bloco
  - Padronizar buffer único por operação
  - Migrar TarUtils para rotinas autorais de I/O

## Hotspots por dependência

### `androidx.appcompat:appcompat:1.7.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (47):
  - `app/src/main/java/android/androidVNC/Utils.java`
  - `app/src/main/java/android/androidVNC/VncCanvas.java`
  - `app/src/main/java/android/androidVNC/VncCanvasActivity.java`
  - `app/src/main/java/com/vectras/qemu/MainSettingsManager.java`
  - `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
  - `app/src/main/java/com/vectras/vm/AboutActivity.java`
  - `app/src/main/java/com/vectras/vm/CqcmActivity.java`
  - `app/src/main/java/com/vectras/vm/DataExplorerActivity.java`
  - `app/src/main/java/com/vectras/vm/ExportRomActivity.java`
  - `app/src/main/java/com/vectras/vm/Fragment/CreateImageDialogFragment.java`
  - `app/src/main/java/com/vectras/vm/ImagePrvActivity.java`
  - `app/src/main/java/com/vectras/vm/Minitools.java`
  - `app/src/main/java/com/vectras/vm/QemuParamsEditorActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/RomReceiverActivity.java`
  - `app/src/main/java/com/vectras/vm/SetArchActivity.java`
  - `app/src/main/java/com/vectras/vm/SplashActivity.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/VMManager.java`
  - `app/src/main/java/com/vectras/vm/WebViewActivity.java`
  - `app/src/main/java/com/vectras/vm/benchmark/BenchmarkActivity.java`
  - `app/src/main/java/com/vectras/vm/crashtracker/LastCrashActivity.java`
  - `app/src/main/java/com/vectras/vm/localization/LanguageModulesFragment.kt`
  - `app/src/main/java/com/vectras/vm/main/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
  - `... +22 arquivos`

### `com.google.android.material:material:1.13.0` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (28):
  - `app/src/main/java/com/termux/app/ExtraKeysView.java`
  - `app/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java`
  - `app/src/main/java/com/vectras/qemu/MainSettingsManager.java`
  - `app/src/main/java/com/vectras/vm/AboutActivity.java`
  - `app/src/main/java/com/vectras/vm/ExportRomActivity.java`
  - `app/src/main/java/com/vectras/vm/Fragment/CreateImageDialogFragment.java`
  - `app/src/main/java/com/vectras/vm/ImagePrvActivity.java`
  - `app/src/main/java/com/vectras/vm/Minitools.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/VMManager.java`
  - `app/src/main/java/com/vectras/vm/VectrasApp.java`
  - `app/src/main/java/com/vectras/vm/benchmark/BenchmarkActivity.java`
  - `app/src/main/java/com/vectras/vm/main/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
  - `app/src/main/java/com/vectras/vm/main/core/RomOptionsDialog.java`
  - `app/src/main/java/com/vectras/vm/main/monitor/SystemMonitorFragment.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreFragment.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreFragment.java`
  - `app/src/main/java/com/vectras/vm/main/vms/VmsFragment.java`
  - `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaBenchManager.kt`
  - `app/src/main/java/com/vectras/vm/settings/LanguageModulesActivity.kt`
  - `app/src/main/java/com/vectras/vm/settings/ThemeActivity.java`
  - `app/src/main/java/com/vectras/vm/setupwizard/SetupWizard2Activity.java`
  - `app/src/main/java/com/vectras/vm/tools/ProfessionalToolsActivity.java`
  - `app/src/main/java/com/vectras/vm/utils/DialogUtils.java`
  - `... +3 arquivos`

### `androidx.annotation:annotation:1.9.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (61):
  - `app/src/main/java/android/androidVNC/VncCursorView.java`
  - `app/src/main/java/com/termux/app/ExtraKeysInfos.java`
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
  - `app/src/main/java/com/termux/app/TermuxOpenReceiver.java`
  - `app/src/main/java/com/termux/app/TermuxPreferences.java`
  - `app/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeyButton.java`
  - `app/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysInfo.java`
  - `app/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java`
  - `app/src/main/java/com/termux/shared/termux/extrakeys/SpecialButton.java`
  - `app/src/main/java/com/vectras/qemu/MainSettingsManager.java`
  - `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
  - `app/src/main/java/com/vectras/qemu/SettingsFragment.java`
  - `app/src/main/java/com/vectras/vm/AboutActivity.java`
  - `app/src/main/java/com/vectras/vm/Fragment/CreateImageDialogFragment.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/RomReceiverActivity.java`
  - `app/src/main/java/com/vectras/vm/VMManager.java`
  - `app/src/main/java/com/vectras/vm/VectrasApp.java`
  - `app/src/main/java/com/vectras/vm/adapters/GithubUserAdapter.java`
  - `app/src/main/java/com/vectras/vm/core/HardwareProfileBridge.java`
  - `app/src/main/java/com/vectras/vm/core/ProcessLaunch.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadCoordinator.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadItemState.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadPathResolver.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadStateReconciler.java`
  - `... +36 arquivos`

### `androidx.core:core-ktx:1.13.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (15):
  - `app/src/main/java/com/k2/zoomimageview/ZoomImageView.kt`
  - `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`
  - `app/src/main/java/com/vectras/vm/DataExplorerActivity.java`
  - `app/src/main/java/com/vectras/vm/MainService.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadWorker.java`
  - `app/src/main/java/com/vectras/vm/main/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/utils/FileUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/NotificationUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/UIUtils.java`
  - `app/src/main/java/com/vectras/vm/x11/LoriePreferences.java`
  - `app/src/main/java/com/vectras/vm/x11/X11Activity.java`
  - `app/src/main/java/com/vectras/vm/x11/input/TouchInputHandler.java`

### `androidx.drawerlayout:drawerlayout:1.2.0` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (3):
  - `app/src/main/java/com/termux/app/ExtraKeysView.java`
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
  - `app/src/main/java/com/termux/app/TermuxViewClient.java`

### `androidx.preference:preference-ktx:1.2.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (9):
  - `app/src/main/java/com/vectras/qemu/MainSettingsManager.java`
  - `app/src/main/java/com/vectras/qemu/SettingsFragment.java`
  - `app/src/main/java/com/vectras/vm/SplashActivity.java`
  - `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaBenchManager.kt`
  - `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaConfig.kt`
  - `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaSettings.kt`
  - `app/src/main/java/com/vectras/vm/setupwizard/FirstRunPermissionOrchestrator.kt`
  - `app/src/main/java/com/vectras/vm/setupwizard/SetupWizard2Activity.java`
  - `app/src/main/java/com/vectras/vm/x11/LoriePreferences.java`

### `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

### `androidx.viewpager:viewpager:1.1.0` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (4):
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
  - `app/src/main/java/com/vectras/vm/x11/X11Activity.java`
  - `app/src/main/java/com/vectras/vm/x11/utils/TermuxX11ExtraKeys.java`
  - `app/src/main/java/com/vectras/vm/x11/utils/X11ToolbarViewPager.java`

### `com.google.code.gson:gson:2.13.2` (implementation)
- Oportunidade de refatoração: Reduzir alocações evitando parse completo para objetos grandes; priorizar streaming em caminhos críticos.
- Arquivos impactados (10):
  - `app/src/main/java/com/vectras/vm/CqcmActivity.java`
  - `app/src/main/java/com/vectras/vm/ExportRomActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMManager.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/DataRoms.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreFragment.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreFragment.java`
  - `app/src/main/java/com/vectras/vm/setupwizard/SetupWizard2Activity.java`
  - `app/src/main/java/com/vectras/vm/utils/JSONUtils.java`
  - `app/src/test/java/com/vectras/vm/VMManagerRestoreVMsJsonAppendTest.java`

### `com.squareup.okhttp3:okhttp:4.12.0` (implementation)
- Oportunidade de refatoração: Reutilizar singleton de cliente HTTP e pools, evitando novos clients por request para diminuir GC e overhead de conexão.
- Arquivos impactados (2):
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadWorker.java`

### `androidx.window:window:1.5.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

### `org.apache.commons:commons-compress:1.28.0` (implementation)
- Oportunidade de refatoração: Usar buffers fixos maiores em I/O pesado para reduzir churn de objetos.
- Arquivos impactados (1):
  - `app/src/main/java/com/vectras/vm/utils/TarUtils.java`

### `androidx.activity:activity-ktx:1.9.2` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (20):
  - `app/src/main/java/com/vectras/vm/ExportRomActivity.java`
  - `app/src/main/java/com/vectras/vm/Minitools.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/WebViewActivity.java`
  - `app/src/main/java/com/vectras/vm/benchmark/BenchmarkActivity.java`
  - `app/src/main/java/com/vectras/vm/crashtracker/LastCrashActivity.java`
  - `app/src/main/java/com/vectras/vm/main/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/ExternalVNCSettingsActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/ImportExportSettingsActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/LanguageModulesActivity.kt`
  - `app/src/main/java/com/vectras/vm/settings/ThemeActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/UpdaterActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/VNCSettingsActivity.java`
  - `app/src/main/java/com/vectras/vm/settings/X11DisplaySettingsActivity.java`
  - `app/src/main/java/com/vectras/vm/setupwizard/SetupWizard2Activity.java`
  - `app/src/main/java/com/vectras/vm/tools/ProfessionalToolsActivity.java`
  - `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/UIUtils.java`
  - `app/src/main/java/com/vectras/vm/x11/X11Activity.java`

### `androidx.constraintlayout:constraintlayout:2.2.1` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

### `androidx.documentfile:documentfile:1.1.0` (implementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (3):
  - `app/src/main/java/com/vectras/qemu/utils/FileInstaller.java`
  - `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`

### `androidx.work:work-runtime:2.9.1` (implementation)
- Oportunidade de refatoração: Consolidar jobs periódicos e evitar enfileiramento redundante para reduzir wakeups.
- Arquivos impactados (6):
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadCoordinator.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadStateReconciler.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadViewModel.java`
  - `app/src/main/java/com/vectras/vm/download/DownloadWorker.java`
  - `app/src/main/java/com/vectras/vm/importer/ImportSessionWorker.java`

### `com.github.bumptech.glide:glide:4.16.0` (implementation)
- Oportunidade de refatoração: Fixar tamanhos alvo, downsampling e recycle de targets para reduzir picos de heap/GC em listas.
- Arquivos impactados (10):
  - `app/src/main/java/com/vectras/vm/ImagePrvActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdapterSearch.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdpater.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreHomeAdapter.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreHomeAdapterSearch.java`
  - `app/src/main/java/com/vectras/vm/main/vms/VmsHomeAdapter.java`
  - `app/src/main/java/com/vectras/vm/utils/NotificationUtils.java`

### `com.github.bumptech.glide:compiler:4.16.0` (annotationProcessor)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (10):
  - `app/src/main/java/com/vectras/vm/ImagePrvActivity.java`
  - `app/src/main/java/com/vectras/vm/RomInfo.java`
  - `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`
  - `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdapterSearch.java`
  - `app/src/main/java/com/vectras/vm/main/romstore/RomStoreHomeAdpater.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreHomeAdapter.java`
  - `app/src/main/java/com/vectras/vm/main/softwarestore/SoftwareStoreHomeAdapterSearch.java`
  - `app/src/main/java/com/vectras/vm/main/vms/VmsHomeAdapter.java`
  - `app/src/main/java/com/vectras/vm/utils/NotificationUtils.java`

### `junit:junit:4.13.2` (testImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

### `org.robolectric:robolectric:4.14.1` (testImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (10):
  - `app/src/test/java/android/androidVNC/VncCanvasHoverMouseTest.java`
  - `app/src/test/java/com/termux/app/BackgroundJobTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMEnvNullCdromPathTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMVncPasswordCliRegressionTest.java`
  - `app/src/test/java/com/vectras/vm/benchmark/BenchmarkManagerTest.java`
  - `app/src/test/java/com/vectras/vm/crashtracker/CrashHandlerTest.java`
  - `app/src/test/java/com/vectras/vm/setupwizard/SetupFeatureCoreBootstrapValidationTest.java`
  - `app/src/test/java/com/vectras/vm/setupwizard/SetupWizard2ActivityUrlSanitizationTest.java`
  - `app/src/test/java/com/vectras/vm/utils/FileUtilsGetPathExternalStorageTest.java`
  - `app/src/test/java/com/vectras/vm/utils/FileUtilsOpenModeTest.java`

### `androidx.test:core:1.6.1` (testImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (2):
  - `app/src/test/java/com/vectras/vm/localization/LocaleManagerTest.kt`
  - `app/src/test/java/com/vectras/vm/utils/FileUtilsGetPathExternalStorageTest.java`

### `org.mockito:mockito-core:5.12.0` (testImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (4):
  - `app/src/test/java/com/termux/app/BackgroundJobTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMEnvNullCdromPathTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMVncPasswordCliRegressionTest.java`
  - `app/src/test/java/com/vectras/vm/core/ProotCommandBuilderTest.java`

### `org.mockito:mockito-inline:5.2.0` (testImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados (4):
  - `app/src/test/java/com/termux/app/BackgroundJobTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMEnvNullCdromPathTest.java`
  - `app/src/test/java/com/vectras/vm/StartVMVncPasswordCliRegressionTest.java`
  - `app/src/test/java/com/vectras/vm/core/ProotCommandBuilderTest.java`

### `androidx.test.ext:junit:1.3.0` (androidTestImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

### `androidx.test.espresso:espresso-core:3.7.0` (androidTestImplementation)
- Oportunidade de refatoração: Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.
- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.

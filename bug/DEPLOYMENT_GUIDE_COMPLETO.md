# Guia de Implantação
## Vectras-VM-Android v3.6.5 - Correção de Bugs e Compatibilidade Android 15

**Versão do Documento:** 2.0  
**Data de Emissão:** 14 de fevereiro de 2026  
**Objetivo:** Fornecer instruções passo-a-passo para implementação de correções de segurança, confiabilidade e compatibilidade  

---

## I. Preparação do Ambiente

### Fase de Setup Inicial

Antes de iniciar qualquer implementação, o ambiente de desenvolvimento deve ser configurado com as versões corretas de ferramentas e dependências. Execute os seguintes passos em sequência para garantir compatibilidade com Android 15 e versões recentes de NDK.

Primeiro, remova todas as versões antigas de ferramentas do sistema. No macOS ou Linux, execute `gradle --version` para verificar a versão atual do Gradle wrapper. A versão deve ser 8.1 ou superior. Se a versão for inferior, proceda à atualização imediata.

Baixe o Android SDK versão 35 (para Android 15) através do Android Studio SDK Manager. Navegue até Tools > SDK Manager, selecione a aba SDK Platforms, e marque a caixa para Android 15 (API 35). Simultaneamente, atualize o SDK Tools para as versões mais recentes, incluindo a build-tools versão 35.0.0 ou superior.

Para o NDK (Native Development Kit), instale a versão 27.0.11902837 especificamente. A versão NDK é crítica para compatibilidade com C++20. Na aba SDK Tools, localize NDK (Side by side) e instale a versão 27.x. O Android Studio colocará essa versão em `$ANDROID_HOME/ndk/27.0.11902837/`.

Instale CMake versão 3.22 ou superior. No SDK Manager, localize CMake na aba SDK Tools e instale a versão 3.22+. Esta versão é necessária para suportar C++20 e as otimizações de compilação nativa.

### Configuração de Java

O projeto requer Java Development Kit (JDK) versão 21. Instale o Temurin JDK 21 a partir de `https://adoptium.net/`. Após instalação, verifique definindo a variável de ambiente `JAVA_HOME` apontando para o diretório do JDK 21.

No arquivo `gradle.properties` do projeto, adicione ou atualize a seguinte linha:

```properties
org.gradle.java.home=/path/to/jdk-21
```

Substitua `/path/to/jdk-21` pelo caminho absoluto da instalação JDK 21 no seu sistema.

### Verificação de Espaço em Disco

O projeto completo com todas as dependências baixadas ocupará aproximadamente 15-20 GB de espaço em disco. Verifique a disponibilidade de espaço antes de prosseguir com downloads de dependências.

---

## II. Remoção de Credenciais Expostas

### Passo 1: Limpeza do Histórico Git

As credenciais de assinatura foram expostas no histórico do repositório Git. Antes de fazer qualquer mudança de código, o histórico deve ser limpo para remover as credenciais de forma permanente. Esta é uma operação crítica que afeta todo o repositório.

Execute o seguinte comando para listar todos os commits que mencionam a senha:

```bash
git log --all --full-history -S "856856" --oneline
```

Se commits forem encontrados, execute o comando de filtragem de história. Para remover completamente a senha do histórico Git, execute:

```bash
git filter-branch --tree-filter '
  find . -type f \( -name "build.gradle" -o -name "*.properties" \) \
    -exec sed -i "s/856856/PLACEHOLDER/g" {} +
' -- --all
```

Após executar `git filter-branch`, força a atualização de todas as referências remotas. **Aviso:** Esta operação reescreve o histórico de commits. Todos os colaboradores devem clonar novamente o repositório após essa operação.

Execute os seguintes comandos:

```bash
git for-each-ref --format="delete %(refname)" refs/original | git update-ref --stdin
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

### Passo 2: Remoção de Arquivo Keystore do Versionamento

O arquivo `vectras.jks` contendo a chave privada de assinatura deve ser imediatamente removido do repositório versionado. Se o arquivo ainda está rastreado pelo Git, execute:

```bash
git rm --cached vectras.jks
echo "vectras.jks" >> .gitignore
git add .gitignore
git commit -m "Remove keystore from version control"
```

Se o arquivo não existe no repositório, apenas adicione ao `.gitignore`:

```bash
echo "vectras.jks" >> .gitignore
git add .gitignore
git commit -m "Add keystore to .gitignore"
```

### Passo 3: Atualização de build.gradle

Abra o arquivo `app/build.gradle` e localize o bloco `signingConfigs`. Substitua o conteúdo conforme demonstrado na seção anterior deste documento. O novo bloco deve usar `findProperty()` para ler valores de `local.properties` e variáveis de ambiente.

Após modificar o arquivo, valide a sintaxe executando:

```bash
./gradlew validateSigningConfig
```

Se nenhum erro for reportado, proceda ao próximo passo.

### Passo 4: Configuração de Credenciais Locais

Crie um arquivo `local.properties` no diretório raiz do projeto com o seguinte conteúdo:

```properties
# Keystore para assinatura de debug (pode ser compartilhado entre desenvolvedores)
VECTRAS_STORE_FILE=../vectras.jks
VECTRAS_KEY_ALIAS=vectras
VECTRAS_STORE_PASSWORD=your_actual_password_here
VECTRAS_KEY_PASSWORD=your_actual_password_here
```

Substitua `your_actual_password_here` pelas credenciais reais. Este arquivo deve estar listado em `.gitignore` e nunca ser versionado.

### Passo 5: Configuração de CI/CD (GitHub Actions)

Se o projeto usa GitHub Actions para compilação e assinatura, adicione os seguintes secrets ao repositório:

1. `VECTRAS_KEYSTORE_BASE64`: Conteúdo do arquivo `.jks` codificado em Base64
2. `VECTRAS_KEY_ALIAS`: Nome do alias da chave (ex: "vectras")
3. `VECTRAS_STORE_PASSWORD`: Senha do keystore
4. `VECTRAS_KEY_PASSWORD`: Senha da chave privada

Para codificar o keystore em Base64, execute:

```bash
base64 -i vectras.jks -o vectras_base64.txt
cat vectras_base64.txt
```

Copie o conteúdo e adicione como secret `VECTRAS_KEYSTORE_BASE64` no GitHub.

---

## III. Implementação de Sincronização em ProcessSupervisor

### Passo 1: Backup do Arquivo Original

Antes de realizar modificações, crie um backup:

```bash
cp app/src/main/java/com/vectras/vm/core/ProcessSupervisor.java \
   app/src/main/java/com/vectras/vm/core/ProcessSupervisor.java.backup
```

### Passo 2: Adição de Locks Explícitos

Abra o arquivo `ProcessSupervisor.java` e localize a declaração de campos da classe (aproximadamente linha 72-80). Adicione após o campo `clock`:

```java
    // Synchronization for state transitions
    private final Object stateLock = new Object();
    private final Object processLock = new Object();
```

### Passo 3: Refatoração de Métodos de Transição de Estado

Todos os métodos que modificam o campo `state` devem ser envolvidos em blocos `synchronized(stateLock)`. Procure por padrões como `state = ` no arquivo e envolva em sincronização.

Exemplo de refatoração:

```java
// ANTES (não sincronizado)
public void transitionToRun() {
    state = State.RUN;
}

// DEPOIS (sincronizado)
public void transitionToRun() {
    synchronized (stateLock) {
        State previousState = state;
        state = State.RUN;
        stateLock.notifyAll();
    }
}
```

### Passo 4: Implementação de Watchdog para Timeouts

Adicione a classe interna `ProcessWatchdog` após a declaração de enums no arquivo `ProcessSupervisor.java`. Esta classe deve implementar `Runnable` e monitorar o tempo de execução de operações críticas, forçando terminação se timeout for excedido.

Após adicionar a classe, crie um método público que inicia a watchdog:

```java
public ProcessWatchdog startWatchdog(long timeoutMs) {
    ProcessWatchdog watchdog = new ProcessWatchdog(this, timeoutMs);
    new Thread(watchdog).start();
    return watchdog;
}
```

### Passo 5: Validação de Compilação

Execute compilação para verificar se não há erros de sintaxe:

```bash
./gradlew compileDebugJava
```

Se erros forem reportados, verifique a indentação e sintaxe Java.

---

## IV. Correção de Race Condition em FileInstaller

### Passo 1: Adição de Imports

Abra `app/src/main/java/com/vectras/qemu/utils/FileInstaller.java` e adicione os seguintes imports no início do arquivo:

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
```

### Passo 2: Adição de Mapa de Locks

Logo após a declaração da classe `FileInstaller`, adicione:

```java
/**
 * Per-file locking to prevent concurrent writes to same file.
 */
private static final Map<String, ReadWriteLock> FILE_LOCKS = 
    Collections.synchronizedMap(new HashMap<>());

private static ReadWriteLock getLockForFile(String filePath) {
    return FILE_LOCKS.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
}
```

### Passo 3: Refatoração de installAssetFile

O método `installAssetFile` deve ser completamente reescrito para incluir sincronização. Use a implementação fornecida no documento de patches técnicos. A mudança principal é envolver operações de arquivo em `fileLock.writeLock()`.

### Passo 4: Refatoração de installFiles

No método `installFiles`, cada operação de escrita deve adquirir o lock antes de executar. Procure pelos loops que chamam `installAssetFile` e envolva em sincronização.

### Passo 5: Teste de Compilação

```bash
./gradlew compileDebugJava -x lint
```

---

## V. Implementação de Compatibilidade Android 15 (SAF)

### Passo 1: Criar SAFFileManager

Crie um novo arquivo `app/src/main/java/com/vectras/qemu/utils/SAFFileManager.java` com a implementação completa fornecida no documento de patches técnicos. Este arquivo fornece uma camada de abstração sobre o Scoped Access Framework.

### Passo 2: Atualizar AndroidManifest.xml

Remova qualquer permissão de acesso direto a `/sdcard/`. O arquivo `app/src/main/AndroidManifest.xml` deve conter apenas:

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Remova qualquer referência a `android.permission.MANAGE_EXTERNAL_STORAGE` se presente.

### Passo 3: Integração em Activities Principais

Em qualquer Activity que inicia instalação de arquivos, adicione código para inicializar `SAFFileManager` usando `ACTION_OPEN_DOCUMENT_TREE`:

```java
private SAFFileManager safManager = new SAFFileManager(this);

private void requestDirectoryAccess() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    startActivityForResult(intent, REQUEST_DIRECTORY_CODE);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_DIRECTORY_CODE && resultCode == RESULT_OK) {
        Uri treeUri = data.getData();
        safManager.setRootDirectoryUri(treeUri);
        // Proceed with file operations using safManager
    }
}
```

### Passo 4: Validação de Android 14/15

Build e teste a aplicação em emulador ou dispositivo com Android 15 (API 35):

```bash
./gradlew installDebug
```

Verifique que operações de arquivo funcionam sem erros de permissão.

---

## VI. Atualização de Build e Dependências

### Passo 1: Atualizar gradle/wrapper/gradle-wrapper.properties

Localize o arquivo `gradle/wrapper/gradle-wrapper.properties` e atualize a versão do Gradle:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.1.1-all.zip
```

### Passo 2: Atualizar CMakeLists.txt

Edite o arquivo `CMakeLists.txt` na raiz do projeto e atualize a versão mínima e o padrão C++:

```cmake
cmake_minimum_required(VERSION 3.22)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
```

### Passo 3: Atualizar build.gradle Root

Edite o arquivo `build.gradle` (root) e atualize as versões de plugin:

```gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.20' apply false
}

ext {
    compileApi = 35
    targetApi = 35
    minApi = 21
    toolsVersion = "35.0.0"
    ndkVersion = "27.0.11902837"
    cmakeVersion = "3.22.1"
    javaLanguageVersion = 21
}
```

### Passo 4: Gradle Sync

Execute sync com Gradle para validar todas as dependências:

```bash
./gradlew --refresh-dependencies build --dry-run
```

---

## VII. Implementação de Object Pooling para Memory Management

### Passo 1: Criar ObjectPool Base Class

Crie arquivo `app/src/main/java/com/vectras/vm/util/ObjectPool.java` com a implementação fornecida no documento de patches técnicos. Esta classe fornece framework reutilizável para qualquer tipo de objeto.

### Passo 2: Criar DeterministicMatrixPool

Crie arquivo `app/src/main/java/com/vectras/vm/rafaelia/DeterministicMatrixPool.java` específico para gerenciar pools de matrizes de ponto flutuante.

### Passo 3: Integração em RafaeliaKernelV22

No arquivo `RafaeliaKernelV22.java`, adicione uma instância estática do pool:

```java
private static final DeterministicMatrixPool MATRIX_POOL = 
    new DeterministicMatrixPool(10, 5, 20);
```

### Passo 4: Refatoração de Loops Críticos

Identifique loops que allocam matrizes (procure por `new double[][]` ou `new Matrix`). Refatore para usar o pool:

```java
// ANTES
for (int i = 0; i < iterations; i++) {
    double[][] temp = new double[10][10];
    processMatrix(temp);
}

// DEPOIS
for (int i = 0; i < iterations; i++) {
    double[][] temp = MATRIX_POOL.acquire();
    try {
        processMatrix(temp);
    } finally {
        MATRIX_POOL.release(temp);
    }
}
```

---

## VIII. Correção de AuditLedger Clock Drift

### Passo 1: Implementar MonotonicClock

No arquivo `app/src/main/java/com/vectras/vm/audit/AuditLedger.java`, adicione as classes internas `MonotonicClock` e `AuditTimestamp` conforme fornecidas no documento de patches técnicos.

### Passo 2: Refatoração de Timestamps

Substitua todas as instâncias de `System.currentTimeMillis()` por `clock.now()` que retorna `AuditTimestamp` contendo tanto wall clock quanto monotonic time.

### Passo 3: Logging de Anomalias

Quando houver detecção de anomalia temporal (tempo retrocedendo), log automaticamente:

```java
if (currentWallMs < lastWallMs) {
    Log.w("AuditLedger", "Time anomaly: wall clock went backward " + 
          (lastWallMs - currentWallMs) + "ms");
}
```

---

## IX. Correção de Benchmark Counter

### Passo 1: Substituição de Long por BigInteger

No arquivo de teste `app/src/test/java/com/vectras/vm/benchmark/VectraBenchmarkTest.java`, localize contadores que são declarados como `long`:

```java
// ANTES
private long operationCounter = 0;

// DEPOIS
import java.math.BigInteger;
private BigInteger operationCounter = BigInteger.ZERO;
```

### Passo 2: Atualização de Incrementos

Substitua incrementos simples:

```java
// ANTES
operationCounter++;

// DEPOIS
operationCounter = operationCounter.add(BigInteger.ONE);
```

### Passo 3: Validação de Overflow

Adicione verificação para alertar se contador exceder limite de Long:

```java
if (operationCounter.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
    Log.w("Benchmark", "Counter exceeded Long.MAX_VALUE: " + operationCounter);
}
```

---

## X. Execução de Testes

### Fase 1: Unit Tests

Execute todos os testes unitários:

```bash
./gradlew testDebugUnitTest --info
```

Verifique se todos os testes passam. Se algum teste falhar, analise o erro e corrija o código correspondente.

### Fase 2: Integration Tests

Execute testes de integração em um dispositivo ou emulador conectado:

```bash
./gradlew connectedDebugAndroidTest --info
```

### Fase 3: Lint Analysis

Execute análise estática Lint:

```bash
./gradlew lint
```

Corrija qualquer aviso crítico ou erro reportado.

### Fase 4: Memory Profiling

Abra o Android Studio Profiler e execute a aplicação em modo debug. Monitore a memória durante operações críticas para verificar se vazamentos foram eliminados.

---

## XI. Build Final e Assinatura

### Fase 1: Build Debug

```bash
./gradlew assembleDebug
```

O APK debug estará em `app/build/outputs/apk/debug/app-debug.apk`.

### Fase 2: Build Release

```bash
./gradlew assembleRelease
```

O APK release estará em `app/build/outputs/apk/release/app-release.apk`.

A assinatura será aplicada automaticamente usando as credenciais do `local.properties`.

### Fase 3: Verificação de Assinatura

Verifique a assinatura do APK:

```bash
jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk
```

---

## XII. Implantação em Produção

### Pré-requisitos de Produção

Antes de implantar em produção, verifique os seguintes itens:

1. Todos os testes passaram com sucesso
2. Análise Lint não reporta erros críticos
3. Profiling de memória mostra vazamentos eliminados
4. Testes de carga de 1 hora completam sem deadlock
5. Compatibilidade com Android 15 verificada em dispositivo real

### Implantação em Play Store

Se distribuindo através do Google Play Store, siga o procedimento padrão:

1. Aumente `versionCode` em `app/build.gradle`
2. Execute `./gradlew bundleRelease` para criar Android App Bundle
3. Upload para Google Play Console
4. Configure testes beta com grupo limitado de usuários
5. Após validação, promova para produção

### Rollback Plan

Se problemas forem descobertos em produção, mantenha a versão anterior disponível como fallback. O versionCode anterior pode ser re-publicado imediatamente.

---

## XIII. Monitoramento Pós-Implantação

Após implantação em produção, monitore os seguintes indicadores:

1. Taxa de crashes reportada pelo Firebase Crashlytics
2. Métrica de ANR (Application Not Responding)
3. Uso de memória médio em dispositivos reais
4. Taxa de abandono de usuários
5. Reviews e feedback de usuários sobre estabilidade

Se qualquer métrica mostrar degradação, considere rollback para versão anterior e investigação adicional.

---

**Fim do Guia de Implantação**

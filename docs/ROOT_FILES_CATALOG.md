# Catálogo de Arquivos Raiz (Root) — Vectras-VM-Android

Este catálogo documenta **todos os arquivos soltos no diretório raiz** do repositório, descrevendo o papel de cada um, seu conteúdo essencial e como se conecta aos módulos, documentação e processo de build. O objetivo é garantir rastreabilidade e navegação clara para auditoria e gestão documental.  

---

## 1) `README.md`

**Papel**: Documento principal de apresentação do projeto, com aviso de atribuição, visão geral e links de navegação para documentação técnica e público-alvo. Também destaca o Vectra Core MVP e os módulos de benchmark e matemática bitwise.  
**Conexões**:
- Referencia diretamente o diretório `docs/` para documentação aprofundada e navegação por público.  
- Aponta para `VECTRA_CORE.md` como documentação específica do framework Vectra Core.  
- Introduz o módulo `BitwiseMath` citado nas classes do pacote `com.vectras.vm.core`.  
**Evidência**: o README contém o aviso de atribuição e links de documentação, além de menções a Vectra Core e BitwiseMath.  

---

## 2) `VECTRA_CORE.md`

**Papel**: Documento técnico completo do Vectra Core MVP, detalhando arquitetura, ciclo determinístico, modelo triádico, paridade 4x4, logging append-only, performance e APIs de uso.  
**Conexões**:
- Serve como referência direta para o framework usado no app (classe `VectraCore` e correlatas).  
- Está explicitamente linkado no `README.md`.  
- Explica o uso do `BuildConfig.VECTRA_CORE_ENABLED`, que é configurado em `app/build.gradle` para controlar a ativação.  
**Evidência**: descreve conceitos, runtime, API e instruções de build/ativação.  

---

## 3) `ADVANCED_OPTIMIZATIONS.md`

**Papel**: Documentação das otimizações avançadas introduzidas no pacote `com.vectras.vm.core`, incluindo algoritmos de análise, estratégias de otimização e melhorias de operações bitwise.  
**Conexões**:
- Complementa as classes Java criadas para otimização e análise (ex.: `AdvancedAlgorithms`, `AlgorithmAnalyzer`, `OptimizationStrategies`).  
- Relaciona-se com as descrições de conclusão em `IMPLEMENTATION_COMPLETE.md`, que detalha o que foi implementado.  
**Evidência**: contém a visão geral e exemplos de uso dos módulos avançados.  

---

## 4) `BENCHMARK_REFACTORING_SUMMARY.md`

**Papel**: Sumário formal da refatoração profissional do módulo de benchmarks, detalhando requisitos, validações, monitoramento ambiental e testes.  
**Conexões**:
- Relaciona-se ao fluxo de benchmark em `app/` e documentação dedicada (ex.: `BENCHMARK_MANAGER.md`, citado na descrição do sumário).  
- Complementa o `README.md`, que cita o módulo de benchmarks e suas métricas.  
**Evidência**: lista classes e responsabilidades implementadas, além de estratégias de validação.  

---

## 5) `IMPLEMENTATION_SUMMARY.md`

**Papel**: Sumário de implementação do Vectra Core MVP, com histórico, lista de arquivos criados/modificados e justificativas técnicas.  
**Conexões**:
- Referencia diretamente `VECTRA_CORE.md` como documentação principal do framework.  
- Relaciona alterações no `app/build.gradle` (flag `VECTRA_CORE_ENABLED`) e ajustes no `README.md`.  
**Evidência**: lista arquivos, componentes e comportamento de build/debug/release.  

---

## 6) `IMPLEMENTATION_COMPLETE.md`

**Papel**: Relatório de conclusão das “Advanced Optimization Modules”, com escopo de implementação, métodos adicionados, testes e QA.  
**Conexões**:
- Complementa `ADVANCED_OPTIMIZATIONS.md` com status e métricas de implementação.  
- Indica módulos de teste associados, reforçando rastreabilidade técnica.  
**Evidência**: detalha módulos, métodos, testes e status de compilação.  

---

## 7) `LICENSE`

**Papel**: Licença GPL v2.0 com seção de atribuição explícita ao autor original e ao mantenedor do fork.  
**Conexões**:
- Vincula juridicamente o projeto e os arquivos derivados, em linha com as políticas de atribuição do `README.md`.  
**Evidência**: inclui texto completo da GPL v2.0 e atribuição explícita.  

---

## 8) `build.gradle` (nível raiz)

**Papel**: Configuração de build de nível superior: versões de plugins, repositórios, tarefa `clean` e propriedades globais (`toolsVersion`, `compileApi`, `targetApi`, `minApi`).  
**Conexões**:
- Define `kotlin_version` e plugins que impactam o build dos módulos.  
- Exporta propriedades consumidas pelos módulos, especialmente `app/build.gradle` via `minApi`, `targetApi` etc.  
**Evidência**: mostra a configuração do `buildscript`, `allprojects` e `ext` com versões.  

---

## 9) `settings.gradle`

**Papel**: Declara o nome do projeto e inclui os módulos Gradle (`:app`, `:terminal-emulator`, `:terminal-view`, `:shell-loader:stub`, `:shell-loader`).  
**Conexões**:
- Controla quais diretórios/módulos participam do build.  
- Garante que os módulos listados estejam disponíveis para dependências entre projetos.  
**Evidência**: lista explícita dos módulos incluídos.  

---

## 10) `gradle.properties`

**Papel**: Propriedades globais do Gradle; define parâmetros de JVM, compatibilidade e flags AndroidX.  
**Conexões**:
- `org.gradle.jvmargs` afeta performance/estabilidade do build.  
- `android.useAndroidX=true` influencia dependências e APIs do app.  
**Evidência**: valores definidos para JVM args, AndroidX e `SDK_VERSION`.  

---

## 11) `gradlew`

**Papel**: Script de inicialização do Gradle Wrapper para sistemas Unix-like (Linux/macOS), responsável por resolver o ambiente Java e invocar o wrapper.  
**Conexões**:
- Usa `gradle/wrapper/gradle-wrapper.jar` para garantir versão consistente do Gradle.  
- Permite builds reproduzíveis via `./gradlew`, alinhado às práticas de CI.  
**Evidência**: script padrão do Gradle com resolução de JAVA_HOME e wrapper jar.  

---

## 12) `gradlew.bat`

**Papel**: Script equivalente do Gradle Wrapper para Windows, com lógica para localização do Java e execução do wrapper jar.  
**Conexões**:
- Complementa `gradlew` para ambientes Windows e CI heterogênea.  
- Consome o mesmo `gradle/wrapper/gradle-wrapper.jar`.  
**Evidência**: script batch padrão do Gradle com verificação de `JAVA_HOME`.  

---

## 13) `.gitignore`

**Papel**: Define padrões de exclusão para builds, artefatos gerados, caches e arquivos sensíveis (ex.: `google-services.json`).  
**Conexões**:
- Alinha com o uso do Firebase no `app/` ao ignorar `google-services.json`.  
- Evita commit de artefatos de build e metadados de IDE.  
**Evidência**: lista explícita de padrões para APKs, build outputs, IDE e Firebase.  

---

## 14) `.firebaserc`

**Papel**: Define o projeto padrão do Firebase associado ao repositório.  
**Conexões**:
- Usado pelo CLI do Firebase para sincronizar com o projeto correto.  
- Relaciona-se a configurações em `app/` que dependem dos serviços Firebase.  
**Evidência**: contém o projeto `"vectras-1678b"` como padrão.  

---

## 15) `vectras.jks`

**Papel**: Keystore Java (arquivo binário) usado para assinatura de builds **debug** e **release**.  
**Conexões**:
- Referenciado diretamente em `app/build.gradle` como `storeFile` nas configurações de assinatura (`debug` e `release`).  
- Implica necessidade de controle rigoroso de acesso, por conter material de assinatura.  
**Evidência**: arquivo binário (conteúdo ASN.1/PKCS-like) com tamanho ~2,7 KB, e referência explícita nas configurações de signing do módulo `app`.  

---

## Relação Consolidada (Visão de Encadeamento)

1. **Entrada institucional e licenciamento**: `README.md` ↔ `LICENSE` garantem atribuição e base legal.  
2. **Documentação técnica**: `README.md` aponta para `VECTRA_CORE.md` e `docs/`, enquanto `ADVANCED_OPTIMIZATIONS.md`, `IMPLEMENTATION_SUMMARY.md`, `IMPLEMENTATION_COMPLETE.md` e `BENCHMARK_REFACTORING_SUMMARY.md` formam o conjunto de documentação especializada e status de implementação.  
3. **Build e módulos**: `settings.gradle` lista módulos; `build.gradle` define plugins e versões; `gradle.properties` controla flags globais; `gradlew/gradlew.bat` garantem execução consistente.  
4. **Integrações externas**: `.firebaserc` define o projeto Firebase; `.gitignore` evita versionamento de `google-services.json`.  
5. **Assinatura**: `vectras.jks` é o artefato de signing referenciado por `app/build.gradle`.  

---

### Observação de Conformidade
Este catálogo prioriza rastreabilidade e navegabilidade documental para auditoria, assegurando que cada arquivo raiz esteja descrito, conectado ao fluxo de build/execução e referenciado nos pontos em que impacta o sistema.  

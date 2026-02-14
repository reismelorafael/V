# BUGFIX REPORT — Sprint HOTFIX

## 1) Paths/tokens: `replaceAll` indevido em decoding de paths
- **Bug:** uso de `replaceAll(...)` para tokens literais em paths (`%3A`, `%2F`, `^^^`) em utilitários de arquivo.
- **Causa:** `replaceAll` usa regex; para tokens fixos de path/shell isso adiciona custo/risco de interpretação regex desnecessária.
- **Correção:** troca cirúrgica para `replace(...)` literal em todos os pontos de conversão/reversão.
- **Trechos corrigidos:**
  - `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`
- **Como testar:**
  1. Rodar `FileUtilsPathReplaceTest`.
  2. Validar round-trip `content://...%3A...%2F...` → `convert` → `unconvert` sem alteração.
- **Risco de regressão:** baixo (semântica preservada para substituição literal).

## 2) Encerramento de processo: timeout/cancel com fallback forçado
- **Bug:** em `streamLog`, timeout e cancel chamavam apenas `destroy()` em alguns fluxos sem garantir fallback completo para `destroyForcibly()` + espera final.
- **Causa:** encerramento parcial em caminhos de timeout/cancel de stream.
- **Correção:** introduzido helper `stopProcessWithTimeout(...)` com política determinística:
  - `destroy()` + `waitFor(gracefulTimeout)`;
  - se não parar, `destroyForcibly()` + `waitFor(forcedTimeout)`.
- **Trechos corrigidos:**
  - `app/src/main/java/com/vectras/vterm/Terminal.java`
- **Como testar:**
  1. Rodar `TerminalProcessTerminationTest`.
  2. Verificar cenário de escalonamento (TERM→KILL) e cenário de timeout mesmo após força.
- **Risco de regressão:** baixo/médio (somente caminho de teardown; melhora robustez em processos travados).

## 3) Corrida start-stop/supervisor: supervisor stale durante stop
- **Bug:** `stopVmProcess` podia operar com supervisor stale/remanescente de corrida de lifecycle.
- **Causa:** ausência de poda imediata antes da consulta e tratamento explícito de supervisor já morto.
- **Correção:**
  - poda de supervisores inativos no início de `stopVmProcess`;
  - remoção determinística e retorno de sucesso quando processo já está morto (`stale`).
- **Trechos corrigidos:**
  - `app/src/main/java/com/vectras/vm/VMManager.java`
- **Como testar:**
  1. Rodar `VMManagerStopVmProcessTest` (caso `stopVmProcess_shouldPruneStaleSupervisorWithoutStopCall`).
- **Risco de regressão:** baixo (apenas reduz estados residuais e evita stop redundante).

---

## Evidência de reprodução (ambiente)
- Ao executar os comandos padrão de validação (`./gradlew test` e `./gradlew assembleDebug`), o ambiente retornou erro de toolchain:
  - `Unsupported class file major version 66`.
- Isso bloqueou a execução do suite Gradle neste container, mas os testes HOTFIX foram adicionados de forma determinística para execução assim que a JVM/Gradle do runner estiver alinhada.

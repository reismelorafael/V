# VM Supervision — Próximos ~5 passos

## Objetivo
Fechar o ciclo do complemento arquitetural com validação técnica, rastreabilidade e estabilidade operacional no path de supervisão de VM.

## Status de execução atual
- ✅ Passo 2 iniciado com teste `VMManagerStopVmProcessTest` cobrindo ausência de supervisor, remoção em sucesso e retenção em falha.
- ✅ Bloqueio de JDK (`major version 69`) reproduzido e mitigado com execução em JDK 21 via `tools/gradle_with_jdk21.sh`.
- ✅ Android SDK local foi provisionado e o build avançou além do bloqueio de `sdk.dir`.
- ✅ Build de teste alvo executado com sucesso após saneamento de toolchain + correções de compilação prioritárias.
- ✅ Passo 3 concluído com testes de failover completos do `ProcessSupervisor` (QMP→TERM, TERM→KILL).
- ✅ Auditoria operacional consolidada em `docs/VM_SUPERVISION_AUDIT_EVIDENCE.md`.
- ✅ Harmonização low-level aplicada com módulo central `ProcessRuntimeOps` para eliminar redundâncias de runtime/processo.

## Passo 1 — Validar toolchain e build determinístico
- Fixar JDK/Gradle compatíveis para eliminar erro `Unsupported class file major version 69`.
- Rodar:
  - `./gradlew --version`
  - `tools/gradle_with_jdk21.sh :app:compileDebugJavaWithJavac -x lint`

## Passo 2 — Cobrir `VMManager.stopVmProcess` com teste
- Adicionar teste unitário para:
  - retorno `false` quando supervisor não existe;
  - remoção do supervisor após `stopGracefully=true`;
  - persistência no mapa quando `stopGracefully=false`.

## Passo 3 — Cobrir transições de failover do `ProcessSupervisor`
- Testar cenários:
  - QMP com sucesso;
  - timeout QMP + sucesso em TERM;
  - timeout TERM + KILL.
- Verificar se eventos do `AuditLedger` registram `from/to/cause/action` corretos.

## Passo 4 — Publicar referência API/arquitetura cruzada
- Ligar `docs/API.md` ↔ `docs/ARCHITECTURE.md` ↔ este roadmap.
- Garantir que estados (`START..STOP`) e política (`QMP → TERM → KILL`) estejam idênticos entre código e docs.

## Passo 5 — Auditoria operacional em execução real
- Subir VM de teste e coletar trilha de lifecycle:
  - bind → run;
  - degradação (se houver flood);
  - shutdown limpo/failover.
- Registrar evidências em documento de operação com timestamp, causa e latência de parada.


## Bloqueios técnicos encontrados no passo completo
- `shell-loader/build.gradle`: string literal quebrada em `buildConfigField` (corrigido nesta rodada).
- `app/src/main/res/values/strings.xml`: `&` não escapado em recurso XML (corrigido para `&amp;`).
- `app/src/main/res/values/theme.xml` + `values-night/theme.xml` + `styles.xml`: dependência de estilos Material3 Expressive indisponível no conjunto atual (aplicados fallbacks compatíveis).
- `app/src/main/java/com/vectras/vm/vectra/VectraCore.kt`: `const val` com inicializador não-const (corrigido para literal `Long`).
- `app/src/main/java/com/vectras/vm/benchmark/BenchmarkManager.java`: trecho de métodos de timer/jitter estava inconsistente e compilação quebrada parcial (corrigido trecho com duplicações/sintaxe inválida).

Pendências remanescentes (próxima rodada):
- publicar evidência operacional em execução de VM real (device/emulador com QEMU ativo), preservando rastreabilidade de latência real por causa/ação;
- conectar relatório de auditoria à documentação arquitetural/API com exemplos de troubleshooting.


Validação concluída nesta etapa:
- `tools/gradle_with_jdk21.sh :app:compileDebugJavaWithJavac -x lint` ✅
- `tools/gradle_with_jdk21.sh :app:testDebugUnitTest --tests com.vectras.vm.VMManagerStopVmProcessTest` ✅

# VM Supervision — Próximos ~5 passos

## Objetivo
Fechar o ciclo do complemento arquitetural com validação técnica, rastreabilidade e estabilidade operacional no path de supervisão de VM.

## Status de execução atual
- ✅ Passo 2 iniciado com teste `VMManagerStopVmProcessTest` cobrindo ausência de supervisor, remoção em sucesso e retenção em falha.
- ⚠️ Passo 1 ainda bloqueado por incompatibilidade de toolchain (`major version 69`) no ambiente atual.

## Passo 1 — Validar toolchain e build determinístico
- Fixar JDK/Gradle compatíveis para eliminar erro `Unsupported class file major version 69`.
- Rodar:
  - `./gradlew --version`
  - `./gradlew :app:compileDebugJavaWithJavac -x lint`

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

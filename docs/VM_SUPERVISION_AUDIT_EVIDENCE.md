# VM Supervision — Audit Evidence (Failover/Lifecycle)

## Escopo desta rodada
- validação determinística do lifecycle em supervisor;
- validação de transições de failover QMP → TERM e TERM → KILL;
- validação de trilha auditável em transições capturadas (`cause_code`, `action_taken`, `stall_ms`) via sink determinístico de teste.

## Cenários validados
1. `qmp_success`:
   - `RUN -> STOP` com `cause_code=qmp_shutdown`, `action_taken=qmp`.
2. `qmp_timeout_then_term_success`:
   - `RUN -> FAILOVER` com `cause_code=qmp_timeout`, `action_taken=term_kill`;
   - `FAILOVER -> STOP` com `cause_code=term_success`, `action_taken=term`.
3. `term_timeout_then_kill_success`:
   - `RUN -> FAILOVER` com `cause_code=no_qmp`, `action_taken=term_kill`;
   - `FAILOVER -> STOP` com `cause_code=kill_success`, `action_taken=kill`.
4. Auditoria operacional de transição:
   - presença de eventos START/VERIFY/RUN/FAILOVER/STOP;
   - `stall_ms >= 0` validado nas transições de parada.

## Fontes de validação
- `app/src/test/java/com/vectras/vm/core/ProcessSupervisorFailoverTest.java`
- `app/src/main/java/com/vectras/vm/core/ProcessSupervisor.java`

## Harmonização low-level aplicada
- centralização de operações runtime em `ProcessRuntimeOps` (`safePid`, `monoMs`, `wallMs`, `isQmpAck`) para reduzir redundância e fragilidade entre módulos.
- `ProcessSupervisor` usa defaults estáticos (`DEFAULT_QMP_TRANSPORT`, `NOOP_TRANSITION_SINK`, `SYSTEM_CLOCK`) para menor overhead de instanciação e coerência operacional.

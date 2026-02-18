# Fullstack Source Map / Mapeamento Fullstack do Código-Fonte

Este documento aplica a visão **fullstack** sobre o código real do projeto, conectando ponta a ponta:

`UI Android → Runtime VM/QEMU → Native/JNI (C) → Integridade/Coerência → Segurança → Governança`

---

## 1) Fluxo E2E canônico

```text
Usuário (UI)
  ↓
MainActivity / SetupQemuActivity
  ↓
QEMU orchestration (MainVNCActivity + QmpClient + ShellExecutor)
  ↓
NativeFastPath JNI + vectra_core_accel.c (contratos hardware/kernel + aceleração determinística)
  ↓
VectraCore (ciclo determinístico, paridade/hash, índice append-only)
  ↓
Permission/File boundaries (PermissionUtils + FileUtils + RomReceiver)
  ↓
Governança documental (MANIFEST + TRACEABILITY + IMAGES_INDEX)
```

---

## 2) Camada de UI (Frontend Android)

Arquivos principais:
- `app/src/main/java/com/vectras/vm/MainActivity.java`
- `app/src/main/java/com/vectras/vm/SetupQemuActivity.java`
- `app/src/main/java/com/vectras/vm/Fragment/LoggerFragment.java`
- `app/src/main/java/com/vectras/vm/DataExplorerActivity.java`

Responsabilidade:
- iniciar/operar VM,
- expor status/log ao operador,
- orquestrar ações que disparam runtime e validações.

Invariante:
- operação crítica precisa refletir estado visível em UI (sucesso/falha/progresso).

---

## 3) Camada Runtime (QEMU/VM)

Arquivos principais:
- `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
- `app/src/main/java/com/vectras/qemu/utils/QmpClient.java`
- `app/src/main/java/com/vectras/vm/core/ShellExecutor.java`
- `app/src/main/java/com/vectras/vm/core/TermuxX11.java`
- `app/src/main/java/com/vectras/vm/core/PulseAudio.java`

Responsabilidade:
- ciclo de execução da VM,
- canal de controle (QMP),
- integração display/áudio/X11.

Invariante:
- comandos runtime devem ter retorno observável (log/status) para camadas superiores.

---


## 4) Camada Native/JNI (Low-level C)

Arquivos principais:
- `app/src/main/cpp/vectra_core_accel.c`
- `app/src/main/jniLibs/arm64-v8a/libXlorie.so`
- `app/src/main/jniLibs/armeabi-v7a/libXlorie.so`
- `app/src/main/jniLibs/x86/libXlorie.so`
- `app/src/main/jniLibs/x86_64/libXlorie.so`

Responsabilidade:
- ponte Java↔C via JNI para hotpaths determinísticos,
- leitura de contrato de hardware/kernel (`nativeReadHardwareContract`, `nativeReadKernelUnitContract`),
- operações de checksum/auditoria com baixa sobrecarga (`nativeXorChecksum`, `nativeAudit`).

Invariante:
- toda otimização nativa deve preservar contrato de retorno (`RMR_KERNEL_OK`/`RMR_KERNEL_ERR_*`) e trilha observável para camadas superiores.

---

## 5) Camada Integridade/Coerência

Arquivos principais:
- `app/src/main/java/com/vectras/vm/vectra/VectraCore.kt`
- `app/src/main/java/com/vectras/vm/logger/VMStatus.java`
- `app/src/main/java/com/vectras/vm/logger/VectrasStatus.java`

Responsabilidade:
- coerência de ciclo,
- validação por hash/paridade,
- trilha append-only de evidências e estado.

Invariante:
- nenhuma promoção de artefato/estado sem checagem de integridade.

---

## 6) Camada Segurança

Arquivos principais:
- `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`
- `app/src/main/java/com/vectras/vm/utils/FileUtils.java`
- `app/src/main/java/com/vectras/vm/RomReceiverActivity.java`

Responsabilidade:
- fronteira de permissões Android,
- validação de entrada de arquivos,
- controle de superfície de ingestão externa.

Invariante:
- entrada externa nunca deve contornar validação/permissão antes do runtime.

---

## 7) Camada Governança

Arquivos principais:
- `docs/SOURCE_TRACEABILITY_MATRIX.md`
- `docs/assets/MANIFEST.md`
- `docs/assets/CHAT_PROMPT_PROVENANCE.md`
- `docs/IMAGES_INDEX.md`
- `docs/SECURITY.md`

Responsabilidade:
- rastreabilidade de ativo e origem,
- vínculo documentação ⇄ código,
- controle de coerência para auditoria técnica.

Invariante:
- artefato citado precisa de origem rastreável e referência canônica no manifesto.

---

## 8) Matriz rápida de handoff (Fullstack)

| Eixo | Entrada | Processamento | Saída | Evidência |
|---|---|---|---|---|
| UI → Runtime | ação do operador | QMP/shell/orquestração VM | estado da VM | `LoggerFragment`, `VMStatus` |
| Runtime → Native | evento técnico da VM | JNI encaminha hotpath para `vectra_core_accel.c` | contrato kernel/hardware consistente | `nativeReadHardwareContract`, `nativeAudit` |
| Native → Integridade | resultado low-level | `VectraCore` consolida validação/paridade | estado coerente | índice/log append-only |
| Integridade → Segurança | artefato/caminho | checagem permissão/origem | artefato autorizado | `PermissionUtils`, `FileUtils` |
| Segurança → Governança | release/doc update | registro canônico | trilha auditável | `MANIFEST`, `TRACEABILITY` |



---

## 9) Resposta de revisão (inline comments/questions)

- Contexto recebido: não houve comentários inline textuais específicos anexados no payload além de “sim”.
- Ação aplicada: fortalecimento fullstack com camada **Native/JNI (C)** explícita, cobrindo ponte Java↔C e contratos de kernel/hardware para coerência ponta a ponta.
- Resultado: mapa fullstack agora inclui caminho operacional completo até baixo nível (`app/src/main/cpp/vectra_core_accel.c`).

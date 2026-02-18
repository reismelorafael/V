# rafaelia-coherence-layers (ASCII)

```text
┌──────────────────────────────────────────────────────────┐
│ L5 GOVERNANÇA / AUDITORIA                               │
│ políticas, checklist, evidência de release              │
├──────────────────────────────────────────────────────────┤
│ L4 SEGURANÇA                                             │
│ permissões, validação de origem, assinatura/verificação │
├──────────────────────────────────────────────────────────┤
│ L3 INTEGRIDADE                                           │
│ hash, paridade, consistência de estado, índice append   │
├──────────────────────────────────────────────────────────┤
│ L2 RUNTIME / ORQUESTRAÇÃO                               │
│ ciclo VM, QEMU, VNC, subprocessos e observabilidade     │
├──────────────────────────────────────────────────────────┤
│ L1 UI / OPERAÇÃO                                         │
│ ações do operador, feedback, logs e alertas             │
└──────────────────────────────────────────────────────────┘
                ▲                              │
                └────── feedback operacional ──┘
```

## 1) Interpretação técnica (blueprint)

- **Componentes por camada**:
  - `L1 UI`: entrada humana, confirmação de operações, leitura de estado.
  - `L2 Runtime`: execução real de VM e coordenação de serviços auxiliares.
  - `L3 Integridade`: validação determinística de dados/estado e registro.
  - `L4 Segurança`: fronteira de confiança e autorização.
  - `L5 Governança`: trilha corporativa/documental para conformidade.
- **Ligações e direção de fluxo**:
  - Fluxo funcional principal sobe de `L1` para `L5` (operação → execução → validação → segurança → auditoria).
  - Fluxo de feedback retorna de camadas altas para `L1` com status e ação corretiva.
- **Invariantes de coerência**:
  - Nenhuma execução crítica sem visibilidade operacional na UI.
  - Nenhum artefato promovido sem validação de integridade.
  - Nenhuma evidência considerada final sem referência de governança versionada.

## 2) Legenda operacional

- **UI**: Activities, Fragments e componentes de interação do app Android.
- **Runtime**: QEMU/VNC/Termux/X11/Pulse e orchestration local.
- **Integridade**: mecanismos de verificação, índice imutável e medições de consistência.
- **Segurança**: permissões Android, fronteiras de acesso e validações de origem.
- **Governança**: documentos normativos, manifesto de ativos e matriz de rastreabilidade.

## 3) Mapeamento para arquivos/camadas reais

- **L1 UI / Operação**
  - `app/src/main/java/com/vectras/vm/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/SetupQemuActivity.java`
  - `app/src/main/java/com/vectras/vm/Fragment/LoggerFragment.java`
- **L2 Runtime / Orquestração**
  - `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
  - `app/src/main/java/com/vectras/qemu/utils/QmpClient.java`
  - `app/src/main/java/com/vectras/vm/core/PulseAudio.java`
- **L3 Integridade**
  - `app/src/main/java/com/vectras/vm/vectra/VectraCore.kt`
  - `app/src/main/java/com/vectras/vm/logger/VMStatus.java`
- **L4 Segurança**
  - `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`
  - `app/src/main/java/com/vectras/vm/RomReceiverActivity.java`
- **L5 Governança / Auditoria**
  - `docs/assets/CHAT_PROMPT_PROVENANCE.md`
  - `docs/assets/MANIFEST.md`
  - `docs/IMAGES_INDEX.md`
  - `docs/SOURCE_TRACEABILITY_MATRIX.md`

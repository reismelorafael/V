# additional-image-01 (ASCII)

```text
VECTRAS VM EXECUTION ARCHITECTURE (SOURCE-ALIGNED FLOW)

┌──────────────────────────────────────────────────────────┐
│ ENTRADA / UI                                             │
│ MainActivity.java + MainStartVM.java                    │
│ seleção ROM/ISO, CPU/RAM/DISCO, ação do operador        │
└──────────────────────────────┬───────────────────────────┘
                               │
                               v
┌──────────────────────────────────────────────────────────┐
│ PROCESSAMENTO / ORQUESTRAÇÃO                            │
│ StartVM.java + MainVNCActivity.java                     │
│ montagem cmd QEMU → bootstrap VNC/áudio/rede/log        │
└──────────────────────────────┬───────────────────────────┘
                               │
                               v
┌──────────────────────────────────────────────────────────┐
│ SAÍDA / EXECUÇÃO E ESTADO                               │
│ VMStatus.java + LoggerFragment.java                     │
│ sessão ativa, telemetria, feedback operacional          │
└──────────────────────────────────────────────────────────┘

Legenda: VM = Virtual Machine; UI = User Interface; VNC = Virtual Network Computing.
```

Este artefato representa o fluxo real da execução de VM no app, ancorado em classes existentes do código-fonte.
A composição segue o padrão visual dos demais `*.ascii.md` com blocos encadeados, título técnico e legenda curta.

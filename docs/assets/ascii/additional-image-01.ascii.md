# additional-image-01 (ASCII)

```text
VECTRAS VM ORCHESTRATION FLOW (INPUT → PROCESSING → OUTPUT)

┌──────────────────────────────┐
│ ENTRADA (Input)              │
│ - Perfil da VM (ROM/ISO)     │
│ - Parâmetros CPU/RAM/Disco   │
│ - Ação do operador na UI      │
└──────────────┬───────────────┘
               │
               v
┌──────────────────────────────┐
│ PROCESSAMENTO (Core Runtime) │
│ - Builder de comando QEMU    │
│ - Inicialização de serviços  │
│   (VNC, áudio, rede, logs)   │
│ - Validação de integridade   │
└──────────────┬───────────────┘
               │
               v
┌──────────────────────────────┐
│ SAÍDA (Execution/Feedback)   │
│ - Sessão VM ativa            │
│ - Telemetria e status        │
│ - Registro para auditoria    │
└──────────────────────────────┘

Legenda: VM = Virtual Machine; UI = User Interface; VNC = Virtual Network Computing.
```

O diagrama resume o fluxo operacional do app desde a entrada de configuração até a execução da VM com retorno de status.
Ele mantém o padrão visual dos demais artefatos ASCII com blocos encadeados e nomenclatura técnica objetiva.

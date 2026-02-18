# rafaelia-core-eye-toroid (ASCII)

```text
               ┌───────────────────────────────────────┐
               │         RAFAELIA CORE EYE             │
               │      (Observability + Coherence)      │
               └───────────────────────────────────────┘
                                │
                     telemetry/event intake
                                │
                       ┌────────▼────────┐
                       │   TOROID RING   │
                       │ ingest→verify→  │
                       │ index→feedback  │
                       └───────┬─────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
      ┌──────▼──────┐   ┌──────▼──────┐   ┌──────▼──────┐
      │ INTEGRIDADE │   │ SEGURANÇA   │   │ GOVERNANÇA  │
      │ hash+parity │   │ assinatura  │   │ trilha/audit│
      └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
             │                 │                 │
             └─────────────────┴─────────────────┘
                               │
                      ┌────────▼────────┐
                      │   UI / STATUS   │
                      │ alerts + metrics│
                      └─────────────────┘
```

## 1) Interpretação técnica (blueprint)

- **Componentes**:
  - `Core Eye`: ponto lógico de observação contínua dos eventos do runtime.
  - `Toroid Ring`: pipeline cíclico de processamento (`ingest → verify → index → feedback`).
  - Blocos de domínio: `Integridade`, `Segurança`, `Governança`.
  - `UI / Status`: consumo operacional para operador e diagnóstico.
- **Ligações e direção de fluxo**:
  - Fluxo primário descendente de entrada de eventos até apresentação.
  - Fluxo secundário de realimentação do `Toroid Ring` para nova coleta (ciclo fechado).
  - Fluxos laterais do anel para os três domínios, com retorno convergente ao caminho principal.
- **Invariantes operacionais**:
  - Toda entrada relevante precisa passar por verificação antes de indexação.
  - Registro de evidência deve ser append-only, sem mutação retroativa.
  - Resultado de integridade e segurança precisa ser rastreável por trilha de governança.

## 2) Legenda operacional

- **UI**: superfícies visuais e de interação (`MainActivity`, telas de status/log, diálogos).
- **Runtime**: laços de execução e subprocessos de VM/QEMU/VNC.
- **Integridade**: hash, paridade e validação de consistência de artefatos e estados.
- **Segurança**: assinatura/verificação criptográfica e controles de entrada.
- **Governança**: rastreabilidade, auditoria e políticas de operação/versionamento.

## 3) Mapeamento para arquivos/camadas reais

- **UI**
  - `app/src/main/java/com/vectras/vm/MainActivity.java`
  - `app/src/main/java/com/vectras/vm/Fragment/LoggerFragment.java`
  - `app/src/main/java/com/vectras/vm/logger/VectrasStatus.java`
- **Runtime**
  - `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
  - `app/src/main/java/com/vectras/vm/core/ShellExecutor.java`
  - `app/src/main/java/com/vectras/vm/core/TermuxX11.java`
- **Integridade**
  - `app/src/main/java/com/vectras/vm/vectra/VectraCore.kt`
  - `app/src/main/java/com/vectras/vm/benchmark/VectraBenchmark.java`
- **Segurança**
  - `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`
  - `app/src/main/java/com/vectras/vm/utils/FileUtils.java`
- **Governança**
  - `docs/SOURCE_TRACEABILITY_MATRIX.md`
  - `docs/SECURITY.md`
  - `docs/assets/MANIFEST.md`

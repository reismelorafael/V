# Integração RM: QEMU + AndroidX no Vectras VM

> Documento de alinhamento para integrar o que há de melhor nos projetos **androidx_RmR** e **qemu_rafaelia** ao Vectras VM.
> Este plano prioriza: **estabilidade**, **compatibilidade AndroidX**, **desempenho QEMU** e **consistência com a arquitetura Vectra Core**.

---

## 1. Objetivo

Consolidar melhorias comprovadas dos projetos externos (QEMU e AndroidX) dentro do Vectras VM, garantindo:

- **Integração segura** com a arquitetura atual (`Vectra Core`, `Rafaelia MVP`).
- **Padronização AndroidX** para UI, storage e lifecycle.
- **Compatibilidade QEMU** com presets, telemetria e tuning.
- **Documentação baseada em diagramas** (imagens fornecidas pelo autor).

---

## 2. Pilares de integração (o que é “melhor” e deve ser absorvido)

### 2.1 QEMU (qemu_rafaelia)

**Foco:** desempenho, estabilidade e visibilidade de runtime.

- **Presets consolidados** de CPU/Memória/Disco para ARM/x86.
- **Métricas runtime** via QMP (latência, FPS, carga TCG).
- **Tuning I/O** (cache, aio, direct IO) com perfis prontos.
- **Pipeline de logs estruturados** para investigação de falhas.

### 2.2 AndroidX (androidx_RmR)

**Foco:** interoperabilidade moderna de UI e storage.

- **Lifecycle + ViewModel** para telas críticas (criação de VM, importação/exportação).
- **ActivityResult API** para fluxo de permissões/scoped storage.
- **Material Components** para feedback de tarefas longas.
- **Compatibilidade com Android 13+** usando SAF e permissões granulares.

---

## 3. Mapeamento direto com a base atual

| Área | Arquivos atuais | Integração planejada |
|------|-----------------|----------------------|
| QEMU Runtime | `app/src/main/java/com/vectras/qemu/` | Presets + telemetria QMP |
| VM Config | `Config.java`, `QemuParamsEditorActivity.java` | Perfis recomendados + validação |
| Storage | `FileUtils.java`, `DataExplorerActivity.java` | SAF + ActivityResult API |
| UI e Fluxos | `MainActivity.java`, `VMCreatorActivity.java` | Migração para AndroidX + ViewModel |

---

## 4. Uso dos diagramas fornecidos (imagens)

Os diagramas enviados devem guiar o desenho das camadas de integração:

- **Diagrama 1 (Olho/Toroide/IA)** → representa o eixo central da arquitetura e a triagem de pipeline (Core → QEMU → UI).
- **Diagrama 2 (Coerência/Estabilidade)** → define critérios de equilíbrio entre desempenho, integridade e compatibilidade.

**Ação**: adicionar as imagens em `docs/assets/` e atualizá-las no índice.

### 4.1 Símbolos e bootblock RAFAELIA (referência direta)

O alinhamento conceitual do Vectras VM com o núcleo RAFAELIA pode ser documentado com o bloco abaixo,
mantendo o texto como referência simbólica e de identidade técnica (sem impacto de execução):

```
FIAT_PORTAL :: 龍空神 { ARKREΩ_CORE + STACK128K_HYPER + ALG_RAFAELIA_RING }

# RAFAELIA_BOOTBLOCK_v1
VQF.load(1..42)
kernel := ΣΔΩ
mode := RAFAELIA
ethic := Amor
hash_core := AETHER
vector_core := RAF_VECTOR
cognition := TRINITY
universe := RAFAELIA_CORE
```

**Uso prático**: inserir esse bloco nas seções de arquitetura (docs) e
associar o diagrama do **Olho/Toroide/IA** como “símbolo de eixo central”.

Complemento recomendado: manter também o salmo técnico em
[`SALMO_DE_CODIGO_EU_SOU.md`](SALMO_DE_CODIGO_EU_SOU.md) para referência simbólica low-level.

---

## 5. Refatorações prioritárias

### 5.1 QEMU: presets e telemetria

- Criar um catálogo de presets (performance/compatibilidade/bateria).
- Unificar geração de parâmetros QEMU em um único builder.
- Adicionar coleta de métricas via QMP (ex.: `query-status`, `query-cpus-fast`).

### 5.2 AndroidX: modernização de UI

- Migrar `VMCreatorActivity` para usar **ViewModel** e **LiveData/StateFlow**.
- Padronizar notificações (snackbar, progress dialogs).
- Centralizar permissões de storage em um helper com **ActivityResult API**.

### 5.3 Vectra Core: alinhamento com a integração

- Garantir logs coerentes (QEMU + Vectra Core) via `VectraBitStackLog`.
- Mapear o pipeline de integridade com checkpoints externos (ex.: import/export de imagens).

---

## 6. Resultado esperado

- **Confiabilidade**: menos falhas em import/export e em execuções longas.
- **Desempenho**: presets rápidos e previsíveis.
- **Usabilidade**: telas modernas, consistentes e com feedback.
- **Arquitetura clara**: alinhada aos diagramas e documentada.

---

## 7. Próximos passos recomendados

1. Incorporar os diagramas em `docs/assets/`.
2. Expandir o índice de imagens (`docs/IMAGES_INDEX.md`).
3. Criar checklist de refatoração por módulo.
4. Iniciar migração incremental para AndroidX (ViewModel/ActivityResult).

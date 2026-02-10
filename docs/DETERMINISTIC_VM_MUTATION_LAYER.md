# Deterministic VM Mutation Layer (Policy Kernel C)

Implementação nativa em C no módulo `engine/rmr`, com rastreabilidade append-only e fluxo determinístico para mutação de stream VM sem operações destrutivas.

## Escopo implementado

- Header público: `engine/rmr/include/rmr_policy_kernel.h`
- Core: `engine/rmr/src/rmr_policy_kernel.c`
- CLI mínima: `demo_cli/src/policy_kernel_demo.c`
- Teste de referência: `demo_cli/src/policy_kernel_selftest.c`

## Pipeline obrigatório

1. **PLAN**: leitura sequencial por chunk (default 4096 bytes), sem descompressão.
2. **DIFF**: comparação de baseline (`PLAN`) vs estado mutado (`APPLY`).
3. **APPLY**: mutação determinística (`xor_mask` + `stride`) para novo output.
4. **VERIFY**: releitura do output com validação por chunk.
5. **AUDIT**: evento final de fechamento em log append-only.

## Metadados por chunk

Para cada chunk, o kernel registra:

- `crc32c`
- `hash64` (FNV-1a)
- `entropy_milli` (estimativa inteira)
- flags: `bad_event`, `miss`, `temp_hint`
- `route_id` + `route_target` via route table determinística

## Roteamento determinístico + quorum 2-de-3

Route table padrão:

- `1 -> CPU`
- `2 -> RAM`
- `3 -> DISK`
- `255 -> FALLBACK`

A seleção de rota respeita quorum 2-de-3 (`cpu_ok`, `ram_ok`, `disk_ok`).
Se menos de dois recursos estão disponíveis, o chunk marca `bad_event=1` e segue por fallback determinístico.

## Build e execução

### Build (Make)

```bash
make all
```

### Rodar CLI

```bash
./build/demo/policy_kernel_demo <input> <output> <audit.log> [chunk_size] [xor_hex] [stride] [cpu_ok] [ram_ok] [disk_ok]
```

Exemplo:

```bash
./build/demo/policy_kernel_demo README.md build/demo/mutated.bin build/demo/mutation_audit.log 4096 a5 31 1 1 1
```

## Validação

```bash
./build/demo/policy_kernel_selftest
```

Cobertura atual:

- vetor golden de CRC32C (`"123456789" => 0xE3069283`)
- determinismo de log (mesma entrada + mesma config => log idêntico)
- resistência a corrupção (bitflip em output altera trilha auditável)

## Formato do audit trail

Log textual append-only, uma linha por evento/chunk:

- `event=<n>`
- `stage=<PLAN|DIFF|APPLY|VERIFY|AUDIT>` (internamente codificado em número)
- `off`, `size`, `route`, `target`
- `crc32c`, `hash64`, `entropy_milli`, `flags`

A ausência de truncamento garante preservação histórica dos eventos já emitidos.

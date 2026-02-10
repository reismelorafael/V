# Deterministic VM Mutation Layer (Policy Kernel)

Módulo Rust em `engine/vectra_policy_kernel` para mutação determinística de stream VM, com trilha auditável append-only.

## Pipeline obrigatório

1. **PLAN**: leitura sequencial por chunk (default 4 KiB), sem descompressão, calculando metadados.
2. **DIFF**: comparação CRC32C entre baseline planejado e stream mutado.
3. **APPLY**: mutação determinística por XOR/stride e gravação em novo arquivo de saída.
4. **VERIFY**: releitura do output e validação CRC32C chunk a chunk.
5. **AUDIT**: append-only em JSONL, sem truncamento/sobrescrita.

## Métricas por chunk

Cada chunk grava:
- `crc32c`
- `hash64` (FNV-1a 64-bit)
- `entropy_milli`
- flags `bad_event`, `miss`, `temp_hint`
- `route_id` e `route_target` via route table determinística

## Roteamento determinístico e quorum 2-de-3

A route table padrão:
- `1 -> CPU`
- `2 -> RAM`
- `3 -> DISK`
- `255 -> FALLBACK`

A seleção de rota usa `TriadStatus` (CPU/RAM/DISCO) com quorum 2-de-3. Se menos de dois recursos estiverem OK, marca `bad_event=true` e usa fallback determinístico.

## Como rodar

```bash
cd engine/vectra_policy_kernel
cargo run --bin vectra-policy-cli -- <input_stream> <mutated_output> <audit_log> [chunk_size] [mutation_xor_hex] [mutation_stride]
```

Exemplo:

```bash
cargo run --bin vectra-policy-cli -- ../../README.md ../../tmp_mutated.bin ../../tmp_audit.log 4096 a5 31
```


## Política de artefatos de build

- Binários e artefatos de compilação (`target/`) não são versionados.
- O binário `vectra-policy-cli` é sempre gerado no ato do build (`cargo build`/`cargo run`).

## Como validar

```bash
cd engine/vectra_policy_kernel
cargo test
```

Cobertura atual:
- vetores golden de CRC32C
- determinismo (mesma entrada => mesmo log)
- bitflip simulado detectado no VERIFY

## Como ler o audit trail

O arquivo de auditoria é JSONL append-only, uma linha por evento/chunk, contendo stage (`PLAN`, `DIFF`, `APPLY`, `VERIFY`), offset, checksum, hash, entropia, flags e rota.

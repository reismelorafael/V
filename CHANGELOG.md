# Changelog
All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Changed
- Saneados links locais em `VECTRAS_MEGAPROMPT_DOCS.md` para caminhos reais sob `./docs/` (`ESFERAS_METODOLOGICAS_RAFAELIA`, `DETERMINISTIC_VM_MUTATION_LAYER`, `PERFORMANCE_INTEGRITY`) e executada verificação estática de links markdown locais sem novos quebrados.
- Removida a diretiva global `-dontobfuscate` do `app/proguard-rules.pro`, com redução das regras `-keep` para apenas símbolos exigidos por reflexão/XML e inclusão de registro dos símbolos estáveis no guia de build/release.

## [3.6.6] - 2026-02-10
### Added
- `TokenBucketRateLimiter` para controle de taxa de logs.
- `BoundedStringRingBuffer` para armazenamento bounded de saída.
- `ProcessOutputDrainer` para drenagem concorrente de stdout/stderr.
- `ProcessSupervisor` com máquina de estados e stop escalonado.
- `AuditEvent` e `AuditLedger` (JSONL rotativo).
- Testes unitários para rate limiter, ring buffer e drainer.

### Changed
- `Terminal.streamLog` agora usa backpressure, degradação e stop token.
- `ShellExecutor` retorna resultado estruturado com timeout/cancel.
- `VMManager` prioriza supervisão por processo em vez de `killall` global.
- `PermissionUtils` modernizado para Scoped Storage/SAF em Android 10+.

### Fixed
- Risco de deadlock por leitura sequencial de stdout/stderr.
- Risco de loop bloqueante de `readLine()` em processo longo.
- Risco de explosão de memória por concatenação ilimitada de logs.

# Vectras VM - Navigation Index

## Resumo
Índice de navegação por áreas de pesquisa/operação, cada uma com objetivo, navegação em 5 níveis, trilha de imersão em markdown interno e comandos de validação por rastreabilidade.

## Área 1 — Investors & VCs
**Objetivo:** acelerar diligência técnica com foco em tese verificável, riscos técnicos observáveis e critérios mínimos de validação antes de decisão de investimento.

**Navegação em 5 níveis:**
- Nível 1 (visão executiva): `docs/navigation/HIGH_LEVEL_INVESTORS.md`
- Nível 2 (contexto estratégico): `docs/WHITEPAPER.md`
- Nível 3 (arquitetura): `docs/ARCHITECTURE.md`
- Nível 4 (método técnico): `docs/BENCHMARK_MANAGER.md`
- Nível 5 (auditoria): `docs/SOURCE_TRACEABILITY_MATRIX.md`

**Links de imersão (5):**
- [docs/navigation/HIGH_LEVEL_INVESTORS.md](./HIGH_LEVEL_INVESTORS.md)
- [docs/WHITEPAPER.md](../WHITEPAPER.md)
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- [docs/BENCHMARK_MANAGER.md](../BENCHMARK_MANAGER.md)
- [docs/SOURCE_TRACEABILITY_MATRIX.md](../SOURCE_TRACEABILITY_MATRIX.md)

**Como validar:**
- `sed -n '1,180p' docs/navigation/HIGH_LEVEL_INVESTORS.md`
- `rg -n "dilig|benchmark|trace" docs/navigation/HIGH_LEVEL_INVESTORS.md docs/BENCHMARK_MANAGER.md docs/SOURCE_TRACEABILITY_MATRIX.md`

## Área 2 — Scientists & Researchers
**Objetivo:** suportar experimentos reprodutíveis com protocolo claro de coleta, publicação e interpretação de métricas alinhadas ao código real.

**Navegação em 5 níveis:**
- Nível 1 (guia de pesquisa): `docs/navigation/SCIENTISTS_RESEARCH.md`
- Nível 2 (método de benchmark): `docs/BENCHMARK_MANAGER.md`
- Nível 3 (integridade de medição): `docs/PERFORMANCE_INTEGRITY.md`
- Nível 4 (catálogo de métricas): `docs/BENCHMARKS.md`
- Nível 5 (análise estrutural): `docs/THREE_LAYER_ANALYSIS.md`

**Links de imersão (5):**
- [docs/navigation/SCIENTISTS_RESEARCH.md](./SCIENTISTS_RESEARCH.md)
- [docs/BENCHMARK_MANAGER.md](../BENCHMARK_MANAGER.md)
- [docs/PERFORMANCE_INTEGRITY.md](../PERFORMANCE_INTEGRITY.md)
- [docs/BENCHMARKS.md](../BENCHMARKS.md)
- [docs/THREE_LAYER_ANALYSIS.md](../THREE_LAYER_ANALYSIS.md)

**Como validar:**
- `sed -n '1,220p' docs/navigation/SCIENTISTS_RESEARCH.md`
- `rg -n "reprod|métrica|benchmark|protocolo" docs/navigation/SCIENTISTS_RESEARCH.md docs/BENCHMARK_MANAGER.md docs/BENCHMARKS.md`

## Área 3 — Universities & Academic Labs
**Objetivo:** orientar uso didático em disciplinas de arquitetura/sistemas, com roteiro de laboratório e entregáveis ancorados em documentação técnica do repositório.

**Navegação em 5 níveis:**
- Nível 1 (guia acadêmico): `docs/navigation/UNIVERSITIES_ACADEMIC.md`
- Nível 2 (onboarding didático): `docs/README.md`
- Nível 3 (arquitetura formal): `docs/ARCHITECTURE.md`
- Nível 4 (fluxos de VM): `docs/BLUEPRINT_FLUXOS_VM.md`
- Nível 5 (operação de laboratório): `docs/OPERATIONS.md`

**Links de imersão (5):**
- [docs/navigation/UNIVERSITIES_ACADEMIC.md](./UNIVERSITIES_ACADEMIC.md)
- [docs/README.md](../README.md)
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- [docs/BLUEPRINT_FLUXOS_VM.md](../BLUEPRINT_FLUXOS_VM.md)
- [docs/OPERATIONS.md](../OPERATIONS.md)

**Como validar:**
- `sed -n '1,220p' docs/navigation/UNIVERSITIES_ACADEMIC.md`
- `rg -n "Módulo|Laboratório|arquitetura" docs/navigation/UNIVERSITIES_ACADEMIC.md docs/ARCHITECTURE.md docs/OPERATIONS.md`

## Área 4 — Enterprise Companies
**Objetivo:** apoiar adoção corporativa com critérios operacionais, governança e validação de coesão entre produto, runtime, plataforma e compliance.

**Navegação em 5 níveis:**
- Nível 1 (guia enterprise): `docs/navigation/ENTERPRISE_COMPANIES.md`
- Nível 2 (coerência full-stack): `docs/RAFAELIA_COHESION_ENTERPRISE_STACK.md`
- Nível 3 (supervisão e evidência): `docs/VM_SUPERVISION_AUDIT_EVIDENCE.md`
- Nível 4 (segurança): `docs/SECURITY.md`
- Nível 5 (rastreabilidade): `docs/SOURCE_TRACEABILITY_MATRIX.md`

**Links de imersão (5):**
- [docs/navigation/ENTERPRISE_COMPANIES.md](./ENTERPRISE_COMPANIES.md)
- [docs/RAFAELIA_COHESION_ENTERPRISE_STACK.md](../RAFAELIA_COHESION_ENTERPRISE_STACK.md)
- [docs/VM_SUPERVISION_AUDIT_EVIDENCE.md](../VM_SUPERVISION_AUDIT_EVIDENCE.md)
- [docs/SECURITY.md](../SECURITY.md)
- [docs/SOURCE_TRACEABILITY_MATRIX.md](../SOURCE_TRACEABILITY_MATRIX.md)

**Como validar:**
- `sed -n '1,260p' docs/navigation/ENTERPRISE_COMPANIES.md`
- `rg -n "Governança|compliance|audit|coer" docs/navigation/ENTERPRISE_COMPANIES.md docs/SECURITY.md docs/VM_SUPERVISION_AUDIT_EVIDENCE.md`

## Área 5 — Benchmark Comparisons
**Objetivo:** padronizar comparação de cenários por método reproduzível, evitando divulgação de números sem artefato técnico verificável.

**Navegação em 5 níveis:**
- Nível 1 (guia de comparação): `docs/navigation/BENCHMARK_COMPARISONS.md`
- Nível 2 (orquestração): `docs/BENCHMARK_MANAGER.md`
- Nível 3 (métricas): `docs/BENCHMARKS.md`
- Nível 4 (integridade): `docs/PERFORMANCE_INTEGRITY.md`
- Nível 5 (evidência): `docs/VM_SUPERVISION_AUDIT_EVIDENCE.md`

**Links de imersão (5):**
- [docs/navigation/BENCHMARK_COMPARISONS.md](./BENCHMARK_COMPARISONS.md)
- [docs/BENCHMARK_MANAGER.md](../BENCHMARK_MANAGER.md)
- [docs/BENCHMARKS.md](../BENCHMARKS.md)
- [docs/PERFORMANCE_INTEGRITY.md](../PERFORMANCE_INTEGRITY.md)
- [docs/VM_SUPERVISION_AUDIT_EVIDENCE.md](../VM_SUPERVISION_AUDIT_EVIDENCE.md)

**Como validar:**
- `sed -n '1,240p' docs/navigation/BENCHMARK_COMPARISONS.md`
- `rg -n "METRIC_COUNT|BenchmarkManager|VectraBenchmark" docs/navigation/BENCHMARK_COMPARISONS.md docs/BENCHMARK_MANAGER.md app/src/main/java/com/vectras/vm/benchmark/`

## Área 6 — Performance Operations
**Objetivo:** operar medições e ajustes com runbook de execução incremental, mantendo histórico de decisão e possibilidade de rollback técnico.

**Navegação em 5 níveis:**
- Nível 1 (runbook): `docs/navigation/PERFORMANCE_OPERATIONS.md`
- Nível 2 (operações de performance): `docs/RAFAELIA_PERF_OPS.md`
- Nível 3 (operação geral): `docs/OPERATIONS.md`
- Nível 4 (integridade): `docs/PERFORMANCE_INTEGRITY.md`
- Nível 5 (alinhamento de build): `docs/BUILD_ENV_ALIGNMENT.md`

**Links de imersão (5):**
- [docs/navigation/PERFORMANCE_OPERATIONS.md](./PERFORMANCE_OPERATIONS.md)
- [docs/RAFAELIA_PERF_OPS.md](../RAFAELIA_PERF_OPS.md)
- [docs/OPERATIONS.md](../OPERATIONS.md)
- [docs/PERFORMANCE_INTEGRITY.md](../PERFORMANCE_INTEGRITY.md)
- [docs/BUILD_ENV_ALIGNMENT.md](../BUILD_ENV_ALIGNMENT.md)

**Como validar:**
- `sed -n '1,240p' docs/navigation/PERFORMANCE_OPERATIONS.md`
- `rg -n "rollback|ajuste|ambiente|integridade" docs/navigation/PERFORMANCE_OPERATIONS.md docs/PERFORMANCE_INTEGRITY.md docs/OPERATIONS.md`

## Área 7 — Runtime & Engine Systems
**Objetivo:** mapear runtime e engine para investigação de fluxo de execução VM, integração Android/QEMU e trilhas técnicas ligadas a `engine/rmr/`.

**Navegação em 5 níveis:**
- Nível 1 (guia runtime/engine): `docs/navigation/RUNTIME_ENGINE_SYSTEMS.md`
- Nível 2 (arquitetura): `docs/ARCHITECTURE.md`
- Nível 3 (integração Android↔QEMU): `docs/INTEGRACAO_RM_QEMU_ANDROIDX.md`
- Nível 4 (fluxos): `docs/BLUEPRINT_FLUXOS_VM.md`
- Nível 5 (mapa de repositório): `docs/REPO_XRAY.md`

**Links de imersão (5):**
- [docs/navigation/RUNTIME_ENGINE_SYSTEMS.md](./RUNTIME_ENGINE_SYSTEMS.md)
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- [docs/INTEGRACAO_RM_QEMU_ANDROIDX.md](../INTEGRACAO_RM_QEMU_ANDROIDX.md)
- [docs/BLUEPRINT_FLUXOS_VM.md](../BLUEPRINT_FLUXOS_VM.md)
- [docs/REPO_XRAY.md](../REPO_XRAY.md)

**Como validar:**
- `sed -n '1,220p' docs/navigation/RUNTIME_ENGINE_SYSTEMS.md`
- `rg -n "engine/rmr|QEMU|runtime|fluxo" docs/navigation/RUNTIME_ENGINE_SYSTEMS.md docs/INTEGRACAO_RM_QEMU_ANDROIDX.md docs/REPO_XRAY.md`

## Área 8 — Traceability & Governance
**Objetivo:** consolidar auditoria e rastreabilidade entre requisitos, documentação e implementação para inspeção técnica baseada em evidência.

**Navegação em 5 níveis:**
- Nível 1 (guia de governança): `docs/navigation/TRACEABILITY_GOVERNANCE.md`
- Nível 2 (matriz de rastreabilidade): `docs/SOURCE_TRACEABILITY_MATRIX.md`
- Nível 3 (padrões documentais): `docs/DOCUMENTATION_STANDARDS.md`
- Nível 4 (auditoria de supervisão): `docs/VM_SUPERVISION_AUDIT_EVIDENCE.md`
- Nível 5 (segurança): `docs/SECURITY.md`

**Links de imersão (5):**
- [docs/navigation/TRACEABILITY_GOVERNANCE.md](./TRACEABILITY_GOVERNANCE.md)
- [docs/SOURCE_TRACEABILITY_MATRIX.md](../SOURCE_TRACEABILITY_MATRIX.md)
- [docs/DOCUMENTATION_STANDARDS.md](../DOCUMENTATION_STANDARDS.md)
- [docs/VM_SUPERVISION_AUDIT_EVIDENCE.md](../VM_SUPERVISION_AUDIT_EVIDENCE.md)
- [docs/SECURITY.md](../SECURITY.md)

**Como validar:**
- `sed -n '1,240p' docs/navigation/TRACEABILITY_GOVERNANCE.md`
- `rg -n "trace|rastre|evid|govern" docs/navigation/TRACEABILITY_GOVERNANCE.md docs/SOURCE_TRACEABILITY_MATRIX.md docs/DOCUMENTATION_STANDARDS.md`

## Metadados
- Versão: 2.1
- Última atualização: 2026-02

# RAFCODE❤️PHI — Cabeçalho Técnico do Compilador (C → ASM)

> Documento de contrato técnico para evolução do compilador autoral.
> Regra principal desta linha: **C → ASM** como núcleo.  
> `sh` e `rs` existem apenas como **casca de bootstrap/pipeline**.

## 1) Escopo e direção obrigatória
- O compilador RAFCODE❤️PHI não é orientado a abstrações altas como destino final.
- A rota mandatória de implementação do núcleo é:
  1. **C (preferência de implementação inicial do núcleo)**
  2. **ASM bare-metal (destino principal de execução e otimização)**
- `sh` e `rs` são aceitos somente para:
  - orquestração de build;
  - automação de startup;
  - validação de artefatos.
- `sh` e `rs` **não** definem arquitetura do núcleo final.

## 2) Objetivo de engenharia
- Construir cadeia de compilação autoral com:
  - determinismo de saída;
  - previsibilidade temporal;
  - overhead mínimo;
  - aderência a hardware real (registradores, cache, latência, largura de banda).
- Priorizar geração de código e rotinas capazes de transição segura para ASM bare-metal.

## 3) Política de camadas
- Camada de casca (startup): scripts `sh` + utilitários `rs`.
- Camada de núcleo (compilação/otimização): C autoral.
- Camada de execução crítica: ASM bare-metal por arquitetura.
- Aumentos de abstração só entram se reduzirem risco sem quebrar performance/determinismo.

## 4) Regras de coerência {COERÊNCIA==TRUE}
- Cada incremento deve provar:
  - ganho de controle sobre geração de código;
  - redução de caminhos não determinísticos;
  - rastreabilidade entre fonte C e saída ASM.
- Toda alteração deve manter abertura para tuning por:
  - arquitetura de CPU;
  - topologia de cache;
  - perfil de I/O (IOPS, throughput, latência).

## 5) Mapa inicial de pipeline (mínimo)
1. `sh` inicia ambiente e valida pré-condições.
2. `rs` executa tarefas de apoio do pipeline (sem virar núcleo semântico).
3. Núcleo em C processa frontend/middle-end inicial.
4. Emissão para ASM com trilha de inspeção.
5. Verificação de artefato e métrica de determinismo.

## 6) Critérios de aceite
- Aceito:
  - mudanças que reforcem C → ASM;
  - automações `sh`/`rs` restritas à casca operacional.
- Não aceito:
  - deslocar o núcleo para longe de ASM bare-metal;
  - inflar dependências fora do necessário;
  - sacrificar determinismo por conveniência de framework.

## 7) Nota operacional
Este cabeçalho existe para impedir desvio de direção técnica:  
**pipeline de startup simples, núcleo forte em C, execução crítica em ASM bare-metal**.

## 8) Implementação inicial C/ASM/HEX no repositório
- Base prática disponível em `tools/baremetal/rafcode_phi/`:
  - ABI C↔ASM: `tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h`
  - Casca em C: `tools/baremetal/rafcode_phi/c/rafcode_phi_front_shell.c`
  - Núcleo ASM de emissão: `tools/baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S`
- Política da base: token em C -> opcode em hexadecimal -> gravação determinística via ASM.

# Technical Glossary

## Glossário Técnico

---

<div align="center">

**Vectras VM - Technical Terminology Reference**

*Version 1.0.0 | January 2026*

</div>

---

## Table of Contents / Índice

1. [Virtualization Terms](#1-virtualization-terms)
2. [Computer Architecture Terms](#2-computer-architecture-terms)
3. [Information Theory Terms](#3-information-theory-terms)
4. [Error Detection/Correction Terms](#4-error-detectioncorrection-terms)
5. [Android/Mobile Terms](#5-androidmobile-terms)
6. [Networking Terms](#6-networking-terms)
7. [Vectra Core Terms](#7-vectra-core-terms)
8. [Acronyms and Abbreviations](#8-acronyms-and-abbreviations)

---

## 1. Virtualization Terms

### Binary Translation
**EN:** The process of converting machine code from one instruction set architecture (ISA) to another at runtime. QEMU uses dynamic binary translation (DBT) to execute guest code on a host with a different architecture.

**PT-BR:** Processo de conversão de código de máquina de uma arquitetura de conjunto de instruções (ISA) para outra em tempo de execução. O QEMU usa tradução binária dinâmica (DBT) para executar código guest em um host com arquitetura diferente.

### Guest Operating System
**EN:** The operating system running inside a virtual machine. In Vectras VM, this could be Windows, Linux, macOS, or another OS being emulated.

**PT-BR:** Sistema operacional executando dentro de uma máquina virtual. No Vectras VM, pode ser Windows, Linux, macOS ou outro SO sendo emulado.

### Host Operating System
**EN:** The operating system on which the virtualization software runs. For Vectras VM, this is always Android.

**PT-BR:** Sistema operacional no qual o software de virtualização é executado. Para o Vectras VM, é sempre o Android.

### Hypervisor (Virtual Machine Monitor)
**EN:** Software that creates and manages virtual machines. Type 1 hypervisors run directly on hardware; Type 2 run on top of a host OS. QEMU typically operates as a Type 2 hypervisor or pure emulator.

**PT-BR:** Software que cria e gerencia máquinas virtuais. Hypervisors Tipo 1 executam diretamente no hardware; Tipo 2 executam sobre um SO host. O QEMU tipicamente opera como hypervisor Tipo 2 ou emulador puro.

### QEMU (Quick Emulator)
**EN:** An open-source machine emulator and virtualizer. It can emulate a complete machine including CPU, memory, and devices through software translation, or use hardware acceleration (KVM, HVACCELERATE) when available.

**PT-BR:** Emulador e virtualizador de máquinas open-source. Pode emular uma máquina completa incluindo CPU, memória e dispositivos através de tradução por software, ou usar aceleração de hardware (KVM, HVACCELERATE) quando disponível.

### TCG (Tiny Code Generator)
**EN:** QEMU's portable dynamic code generator. TCG translates guest instructions into an intermediate representation, then into host-native code.

**PT-BR:** Gerador de código dinâmico portátil do QEMU. O TCG traduz instruções guest em uma representação intermediária, depois em código nativo do host.

### Virtual Machine Image
**EN:** A file containing the contents of a virtual machine's disk. Common formats include QCOW2 (QEMU Copy On Write), VDI, VMDK, and raw disk images.

**PT-BR:** Arquivo contendo o conteúdo do disco de uma máquina virtual. Formatos comuns incluem QCOW2 (QEMU Copy On Write), VDI, VMDK e imagens de disco raw.

---

## 2. Computer Architecture Terms

### ARM (Advanced RISC Machine)
**EN:** A family of reduced instruction set computing (RISC) architectures widely used in mobile devices. ARM processors are known for power efficiency.

**PT-BR:** Família de arquiteturas de computação com conjunto de instruções reduzido (RISC) amplamente usada em dispositivos móveis. Processadores ARM são conhecidos pela eficiência energética.

### ARM64 / AArch64
**EN:** The 64-bit execution state of the ARMv8 architecture. Used in modern smartphones and tablets.

**PT-BR:** Estado de execução de 64 bits da arquitetura ARMv8. Usado em smartphones e tablets modernos.

### Instruction Set Architecture (ISA)
**EN:** The abstract model of a computer that defines how instructions are encoded and executed. Examples: x86, ARM, RISC-V.

**PT-BR:** Modelo abstrato de um computador que define como instruções são codificadas e executadas. Exemplos: x86, ARM, RISC-V.

### Pipeline
**EN:** A technique where multiple instructions are overlapped in execution. Modern CPUs have deep pipelines (10-20+ stages) for higher throughput.

**PT-BR:** Técnica onde múltiplas instruções são sobrepostas na execução. CPUs modernas têm pipelines profundos (10-20+ estágios) para maior throughput.

### Register
**EN:** Small, fast storage location within the CPU used to hold data and addresses during instruction execution.

**PT-BR:** Localização de armazenamento pequena e rápida dentro da CPU usada para manter dados e endereços durante a execução de instruções.

### SoC (System on Chip)
**EN:** An integrated circuit that combines CPU, GPU, memory controller, and other components on a single chip. Examples: Qualcomm Snapdragon, Apple A-series.

**PT-BR:** Circuito integrado que combina CPU, GPU, controlador de memória e outros componentes em um único chip. Exemplos: Qualcomm Snapdragon, Apple série A.

### x86 / x86_64
**EN:** The instruction set architecture family developed by Intel. x86_64 (also called AMD64) is the 64-bit extension.

**PT-BR:** Família de arquitetura de conjunto de instruções desenvolvida pela Intel. x86_64 (também chamado AMD64) é a extensão de 64 bits.

---

## 3. Information Theory Terms

### Bit
**EN:** The fundamental unit of information, representing a binary digit (0 or 1).

**PT-BR:** Unidade fundamental de informação, representando um dígito binário (0 ou 1).

### Entropy (Information Entropy)
**EN:** A measure of the average information content or uncertainty in a random variable. High entropy indicates high unpredictability.

**PT-BR:** Medida do conteúdo médio de informação ou incerteza em uma variável aleatória. Alta entropia indica alta imprevisibilidade.

**Formula:** H(X) = -Σ p(x) log₂ p(x)

### Information Content
**EN:** The amount of information conveyed by an event, measured in bits. Rare events carry more information.

**PT-BR:** Quantidade de informação transmitida por um evento, medida em bits. Eventos raros carregam mais informação.

### Noise
**EN:** Unwanted or corrupted data that interferes with signal transmission. In Vectra Core, noise is treated as potentially valuable uninterpreted information.

**PT-BR:** Dados indesejados ou corrompidos que interferem na transmissão de sinal. No Vectra Core, ruído é tratado como informação potencialmente valiosa não interpretada.

### Shannon Limit
**EN:** The theoretical maximum rate at which data can be reliably transmitted over a communication channel.

**PT-BR:** Taxa máxima teórica na qual dados podem ser transmitidos de forma confiável em um canal de comunicação.

---

## 4. Error Detection/Correction Terms

### Checksum
**EN:** A value derived from data used to detect errors. The value is recomputed and compared against the stored checksum.

**PT-BR:** Valor derivado de dados usado para detectar erros. O valor é recalculado e comparado com o checksum armazenado.

### CRC (Cyclic Redundancy Check)
**EN:** An error-detecting code based on polynomial division. CRC32C uses the Castagnoli polynomial for better error detection properties.

**PT-BR:** Código de detecção de erros baseado em divisão polinomial. CRC32C usa o polinômio de Castagnoli para melhores propriedades de detecção de erros.

### ECC (Error-Correcting Code)
**EN:** Codes that can not only detect but also correct errors without retransmission. Used in memory (ECC RAM) and storage systems.

**PT-BR:** Códigos que podem não apenas detectar, mas também corrigir erros sem retransmissão. Usado em memória (ECC RAM) e sistemas de armazenamento.

### Hamming Code
**EN:** An error-correcting code capable of detecting up to two-bit errors and correcting single-bit errors.

**PT-BR:** Código de correção de erros capaz de detectar erros de até dois bits e corrigir erros de bit único.

### Hamming Distance
**EN:** The number of positions at which corresponding symbols differ. Used to measure error detection/correction capability.

**PT-BR:** Número de posições nas quais símbolos correspondentes diferem. Usado para medir capacidade de detecção/correção de erros.

### Parity Bit
**EN:** A single bit added to data to make the total number of 1-bits either even (even parity) or odd (odd parity).

**PT-BR:** Um bit único adicionado aos dados para tornar o número total de bits-1 par (paridade par) ou ímpar (paridade ímpar).

### Syndrome
**EN:** In error detection, the result of checking parity bits against computed values. A non-zero syndrome indicates an error.

**PT-BR:** Na detecção de erros, o resultado de verificar bits de paridade contra valores calculados. Um síndrome não-zero indica um erro.

---

## 5. Android/Mobile Terms

### Activity
**EN:** A single screen with a user interface in an Android application. Activities are the entry points for user interaction.

**PT-BR:** Uma única tela com interface de usuário em uma aplicação Android. Activities são os pontos de entrada para interação do usuário.

### APK (Android Package)
**EN:** The package file format used by the Android operating system for distribution and installation of mobile applications.

**PT-BR:** Formato de arquivo de pacote usado pelo sistema operacional Android para distribuição e instalação de aplicações móveis.

### ART (Android Runtime)
**EN:** The managed runtime used by Android since version 5.0. ART compiles apps ahead-of-time (AOT) for better performance.

**PT-BR:** Runtime gerenciado usado pelo Android desde a versão 5.0. O ART compila apps ahead-of-time (AOT) para melhor desempenho.

### Intent
**EN:** A messaging object used to request an action from another app component (activity, service, or broadcast receiver).

**PT-BR:** Objeto de mensagem usado para solicitar uma ação de outro componente de app (activity, service ou broadcast receiver).

### JNI (Java Native Interface)
**EN:** Framework allowing Java code to call and be called by native applications written in C/C++.

**PT-BR:** Framework que permite que código Java chame e seja chamado por aplicações nativas escritas em C/C++.

### PRoot
**EN:** A userspace implementation of chroot, mount, and binfmt_misc. Allows running Linux environments without root privileges.

**PT-BR:** Implementação em userspace de chroot, mount e binfmt_misc. Permite executar ambientes Linux sem privilégios root.

### Service
**EN:** An Android component that performs long-running operations in the background without a user interface.

**PT-BR:** Componente Android que executa operações de longa duração em segundo plano sem interface de usuário.

---

## 6. Networking Terms

### DHCP (Dynamic Host Configuration Protocol)
**EN:** Protocol for automatically assigning IP addresses and network configuration to devices.

**PT-BR:** Protocolo para atribuir automaticamente endereços IP e configuração de rede a dispositivos.

### NAT (Network Address Translation)
**EN:** Method of mapping private IP addresses to a public IP address. Used by QEMU to provide network access to VMs.

**PT-BR:** Método de mapear endereços IP privados para um endereço IP público. Usado pelo QEMU para fornecer acesso de rede a VMs.

### Port Forwarding
**EN:** Redirecting communication requests from one address/port to another. Used to expose VM services to external networks.

**PT-BR:** Redirecionar solicitações de comunicação de um endereço/porta para outro. Usado para expor serviços de VM a redes externas.

### SLIRP
**EN:** A user-mode network stack used by QEMU that requires no elevated privileges. Provides NAT-based networking.

**PT-BR:** Stack de rede em modo usuário usada pelo QEMU que não requer privilégios elevados. Fornece rede baseada em NAT.

### TAP Device
**EN:** A virtual network kernel device that simulates a network layer device. Used for bridged networking in QEMU.

**PT-BR:** Dispositivo de rede virtual do kernel que simula um dispositivo de camada de rede. Usado para rede em bridge no QEMU.

---

## 7. Vectra Core Terms

### BitStack Log
**EN:** Vectra Core's append-only binary logging format. Each record includes magic number, length, metadata, CRC32C, and payload.

**PT-BR:** Formato de logging binário append-only do Vectra Core. Cada registro inclui número mágico, comprimento, metadados, CRC32C e payload.

### Delta Stage (δ)
**EN:** Processing stage in Vectra Core that performs branchless selection between values using bit masks.

**PT-BR:** Estágio de processamento no Vectra Core que realiza seleção sem branches entre valores usando máscaras de bits.

### Event Bus (VectraEventBus)
**EN:** Thread-safe priority queue for Vectra Core events. Events are processed by priority, then by arrival time.

**PT-BR:** Fila de prioridade thread-safe para eventos do Vectra Core. Eventos são processados por prioridade, depois por tempo de chegada.

### Four-Phase Cycle
**EN:** Vectra Core's deterministic processing loop: Input → Process → Output → Next. Runs at 10 Hz.

**PT-BR:** Loop de processamento determinístico do Vectra Core: Input → Process → Output → Next. Executa a 10 Hz.

### Memory Pool (VectraMemPool)
**EN:** Pre-allocated buffer pool to reduce garbage collection pressure. Buffers are borrowed and released rather than allocated.

**PT-BR:** Pool de buffers pré-alocados para reduzir pressão do garbage collector. Buffers são emprestados e liberados ao invés de alocados.

### Omega Stage (Ω)
**EN:** Vectra Core's finalization stage that combines CRC and entropy hints into a final digest.

**PT-BR:** Estágio de finalização do Vectra Core que combina CRC e dicas de entropia em um digest final.

### Psi Stage (Ψ)
**EN:** Vectra Core's deterministic ingest stage that folds payload data into a running CRC.

**PT-BR:** Estágio de ingestão determinística do Vectra Core que incorpora dados de payload em um CRC corrente.

### Rho (ρ) - Noise as Data
**EN:** Vectra Core's parameter representing information not yet decoded. Treats noise as potentially valuable data rather than errors to discard.

**PT-BR:** Parâmetro do Vectra Core representando informação ainda não decodificada. Trata ruído como dados potencialmente valiosos ao invés de erros a descartar.

**Formula:** ρ = syndrome + event_weight

### Sigma Stage (Σ)
**EN:** Vectra Core's combination stage that mixes two values using XOR and rotation operations.

**PT-BR:** Estágio de combinação do Vectra Core que mistura dois valores usando operações XOR e rotação.

### State Depth
**EN:** The 1024-bit flag array in VectraState (16 × 64-bit longs). Tracks system states and event processing status.

**PT-BR:** Array de flags de 1024 bits em VectraState (16 × longs de 64 bits). Rastreia estados do sistema e status de processamento de eventos.

### Triad (VectraTriad)
**EN:** CPU/RAM/DISK state tracking component using 2-of-3 consensus to detect which component is out of sync.

**PT-BR:** Componente de rastreamento de estado CPU/RAM/DISK usando consenso 2-de-3 para detectar qual componente está fora de sincronia.

### 4×4 Parity Block
**EN:** 16-bit data block with 8-bit 2D parity (4 row + 4 column bits). Can detect and localize single-bit errors.

**PT-BR:** Bloco de dados de 16 bits com paridade 2D de 8 bits (4 bits de linha + 4 de coluna). Pode detectar e localizar erros de bit único.

---

## 8. Acronyms and Abbreviations

| Acronym | Full Form | Portuguese |
|---------|-----------|------------|
| ABI | Application Binary Interface | Interface Binária de Aplicação |
| AARCH64 | ARM Architecture 64-bit | Arquitetura ARM 64 bits |
| API | Application Programming Interface | Interface de Programação de Aplicação |
| APK | Android Package | Pacote Android |
| ART | Android Runtime | Runtime Android |
| BIOS | Basic Input/Output System | Sistema Básico de Entrada/Saída |
| CPU | Central Processing Unit | Unidade Central de Processamento |
| CRC | Cyclic Redundancy Check | Verificação de Redundância Cíclica |
| DBT | Dynamic Binary Translation | Tradução Binária Dinâmica |
| ECC | Error-Correcting Code | Código de Correção de Erros |
| ELF | Executable and Linkable Format | Formato Executável e Linkável |
| FIFO | First In, First Out | Primeiro a Entrar, Primeiro a Sair |
| GC | Garbage Collection | Coleta de Lixo |
| GPU | Graphics Processing Unit | Unidade de Processamento Gráfico |
| HW | Hardware | Hardware |
| IO/I/O | Input/Output | Entrada/Saída |
| IPC | Inter-Process Communication | Comunicação Entre Processos |
| IRQ | Interrupt Request | Requisição de Interrupção |
| ISA | Instruction Set Architecture | Arquitetura de Conjunto de Instruções |
| JIT | Just-In-Time (compilation) | (compilação) Just-In-Time |
| JNI | Java Native Interface | Interface Nativa Java |
| JVM | Java Virtual Machine | Máquina Virtual Java |
| KB/kB | Kilobyte | Kilobyte |
| KVM | Kernel-based Virtual Machine | Máquina Virtual baseada em Kernel |
| MB | Megabyte | Megabyte |
| MMU | Memory Management Unit | Unidade de Gerenciamento de Memória |
| MVP | Minimum Viable Product | Produto Mínimo Viável |
| NAT | Network Address Translation | Tradução de Endereço de Rede |
| NIC | Network Interface Card | Placa de Interface de Rede |
| OS | Operating System | Sistema Operacional |
| PCI | Peripheral Component Interconnect | Interconexão de Componente Periférico |
| QCOW2 | QEMU Copy On Write (version 2) | QEMU Copy On Write (versão 2) |
| QEMU | Quick Emulator | Emulador Rápido |
| RAM | Random Access Memory | Memória de Acesso Aleatório |
| RISC | Reduced Instruction Set Computer | Computador de Conjunto de Instruções Reduzido |
| ROM | Read-Only Memory | Memória Apenas de Leitura |
| SDK | Software Development Kit | Kit de Desenvolvimento de Software |
| SDL | Simple DirectMedia Layer | Camada de Mídia Direta Simples |
| SoC | System on Chip | Sistema em Chip |
| SW | Software | Software |
| TCG | Tiny Code Generator | Gerador de Código Pequeno |
| TLB | Translation Lookaside Buffer | Buffer de Tradução de Endereços |
| UEFI | Unified Extensible Firmware Interface | Interface de Firmware Extensível Unificada |
| UI | User Interface | Interface de Usuário |
| USB | Universal Serial Bus | Barramento Serial Universal |
| VM | Virtual Machine | Máquina Virtual |
| VMM | Virtual Machine Monitor | Monitor de Máquina Virtual |
| VNC | Virtual Network Computing | Computação de Rede Virtual |
| x86 | Intel/AMD 32-bit architecture | Arquitetura Intel/AMD 32 bits |
| x86_64 | Intel/AMD 64-bit architecture | Arquitetura Intel/AMD 64 bits |

---

## Document Cross-References

| Document | Relevance |
|----------|-----------|
| [ABSTRACT.md](ABSTRACT.md) | Uses many terms defined here |
| [RESUMO.md](RESUMO.md) | Portuguese summary using glossary terms |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Technical terms explained |
| [VECTRA_CORE.md](../VECTRA_CORE.md) | Vectra Core-specific terminology |
| [BIBLIOGRAPHY.md](BIBLIOGRAPHY.md) | References for terminology sources |

---

*© 2024-2026 Vectras VM Development Team. Licensed under GNU GPL v2.0*

*Document Version: 1.0.0 | Last Updated: January 2026*

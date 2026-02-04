<div align="center">
<p align="center">
  <img src="resources/vectrasvm.png" style="width: 30%;" />
</p>
<p align="center">
  <a href="https://trendshift.io/repositories/12183" target="_blank"><img src="https://trendshift.io/api/badge/repositories/12183" alt="xoureldeen%2FVectras-VM-Android | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
</p>
</div>

---

## ⚠️ Attribution Notice / Aviso de Atribuição

### English

> **This repository is a fork of the original [Vectras VM](https://github.com/xoureldeen/Vectras-VM-Android) project.**
>
> **Original Author / Creator**: [xoureldeen](https://github.com/xoureldeen)
>
> This fork is maintained by [rafaelmeloreisnovo](https://github.com/rafaelmeloreisnovo) and includes modifications and enhancements to the original work. All credit for the original concept, design, and foundational code belongs to **xoureldeen**.
>
> In accordance with the GNU General Public License v2.0 and ethical principles of open-source development, we acknowledge and honor the original author's contributions. Any derivative work must maintain this attribution.

### Português (PT-BR)

> **Este repositório é um fork do projeto original [Vectras VM](https://github.com/xoureldeen/Vectras-VM-Android).**
>
> **Autor / Criador Original**: [xoureldeen](https://github.com/xoureldeen)
>
> Este fork é mantido por [rafaelmeloreisnovo](https://github.com/rafaelmeloreisnovo) e inclui modificações e melhorias ao trabalho original. Todo o crédito pelo conceito original, design e código fundamental pertence a **xoureldeen**.
>
> De acordo com a GNU General Public License v2.0 e os princípios éticos do desenvolvimento de código aberto, reconhecemos e honramos as contribuições do autor original. Qualquer trabalho derivado deve manter esta atribuição.

---

# Vectras VM
[![Ceasefire Now](https://badge.techforpalestine.org/default)](https://techforpalestine.org/learn-more)

> **📚 Documentation**: Comprehensive technical documentation is available in the [docs/](docs/README.md) directory, including academic-style documentation with preface, abstract, architecture details, and bibliography.
>
> **🎯 Navigation**: Find documentation tailored to your needs - [Audience-Specific Navigation](docs/navigation/INDEX.md) provides guides for investors, scientists, universities, enterprises, and detailed benchmark comparisons.

> **Note**: This project includes an experimental **Vectra Core MVP** - a deterministic event processing framework with integrity verification. See [VECTRA_CORE.md](VECTRA_CORE.md) for details.

> **🚀 Performance Module**: Low-level bitwise and mathematical optimizations are available in `com.vectras.vm.core.BitwiseMath` - featuring vector operations, fast trigonometry, entropy calculations, and spectral analysis using direct bit manipulation.

---

## 🎯 What Makes Vectras VM Different / O que diferencia o Vectras VM

### English

**Vectras VM** stands apart from other Android virtualization solutions through its unique combination of features:

| Feature | Vectras VM | Other Solutions |
|---------|-----------|-----------------|
| **QEMU 9.2.x** | ✅ Latest version | ❌ Often outdated |
| **3Dfx Glide Support** | ✅ Hardware-accelerated | ❌ Rarely available |
| **Multi-Architecture** | ✅ ARM64, ARM32, x86_64, x86 | ⚠️ Limited |
| **No Root Required** | ✅ PRoot-based | ⚠️ Often requires root |
| **Integrity Framework** | ✅ Vectra Core with CRC32C | ❌ Not available |
| **Low-Level Benchmark** | ✅ 79 metrics (AnTuTu-style) | ❌ Not available |

**Key Differentiators:**
- 🔧 **Low-Level Performance**: Built with direct bit operations, mmap, and CRC32C - no unnecessary abstractions
- 📊 **Comprehensive Benchmarking**: 79 metrics covering CPU, Memory, Storage, Integrity, and Emulation
- 🛡️ **Data Integrity**: 4x4 parity blocks with 2D error detection, 2-of-3 triad consensus
- 🚀 **Non-Intrusive Design**: Benchmarks and tests run without impacting user experience

### Português (PT-BR)

**Vectras VM** se destaca das outras soluções de virtualização Android através de sua combinação única de recursos:

| Recurso | Vectras VM | Outras Soluções |
|---------|-----------|-----------------|
| **QEMU 9.2.x** | ✅ Versão mais recente | ❌ Frequentemente desatualizado |
| **Suporte 3Dfx Glide** | ✅ Aceleração por hardware | ❌ Raramente disponível |
| **Multi-Arquitetura** | ✅ ARM64, ARM32, x86_64, x86 | ⚠️ Limitado |
| **Sem Root** | ✅ Baseado em PRoot | ⚠️ Frequentemente requer root |
| **Framework de Integridade** | ✅ Vectra Core com CRC32C | ❌ Não disponível |
| **Benchmark Low-Level** | ✅ 79 métricas (estilo AnTuTu) | ❌ Não disponível |

**Principais Diferenciais:**
- 🔧 **Performance Low-Level**: Construído com operações de bits diretas, mmap e CRC32C - sem abstrações desnecessárias
- 📊 **Benchmarking Abrangente**: 79 métricas cobrindo CPU, Memória, Armazenamento, Integridade e Emulação
- 🛡️ **Integridade de Dados**: Blocos de paridade 4x4 com detecção de erros 2D, consenso de tríade 2-de-3
- 🚀 **Design Não-Intrusivo**: Benchmarks e testes executam sem impactar a experiência do usuário

---

## 📊 Benchmark Module / Módulo de Benchmark

Vectras VM includes a comprehensive **low-level benchmark module** inspired by [AnTuTu](https://www.antutu.com/) methodology, providing **79 metrics** across 6 categories:

### Benchmark Categories / Categorias de Benchmark

| Category | Metrics | Description |
|----------|---------|-------------|
| **CPU Single-threaded** | 20 | Integer, Long, Float, Double operations, bitwise, popcount |
| **CPU Multi-threaded** | 10 | Parallel operations, CAS, barriers, contention |
| **Memory** | 15 | Sequential/random R/W, bandwidth, latency (L1/L2/L3/RAM) |
| **Storage** | 15 | Sequential/random I/O, mmap, 4K/64K/1M blocks |
| **Integrity** | 10 | CRC32C, 2D parity, syndrome, XOR stripe, hash mix |
| **Emulation** | 9 | Context switch, timer precision, triad consensus |

### Approximate Benchmark Scores / Pontuações Aproximadas

> **Note**: These are approximate reference values. Actual scores vary by device.

| Device Class | CPU Score | Memory Score | Total Score |
|--------------|-----------|--------------|-------------|
| High-End (SD8 Gen 3) | ~2500 | ~1800 | ~8000+ |
| Mid-Range (SD 7 Gen 1) | ~1800 | ~1400 | ~5500 |
| Entry (SD 6 Gen 1) | ~1200 | ~1000 | ~3500 |
| Reference Device | 100 | 100 | 600 |

### Usage / Uso

The benchmark module is available at `com.vectras.vm.benchmark.VectraBenchmark`:

```java
// Run all 79 benchmarks
BenchmarkResult[] results = VectraBenchmark.runAllBenchmarks();

// Get total score (AnTuTu-style)
int totalScore = VectraBenchmark.calculateTotalScore(results);

// Get category scores
int[] categoryScores = VectraBenchmark.calculateCategoryScores(results);

// Format report
String report = VectraBenchmark.formatReport(results);
```

### Design Principles / Princípios de Design

- **Low-Level**: Direct bit operations, no unnecessary abstractions
- **Non-Intrusive**: No impact on user experience during benchmarks
- **Deterministic**: Reproducible results across runs
- **AnTuTu-Compatible**: Scoring methodology inspired by AnTuTu v10.x

---

## 🧮 Low-Level Math Module / Módulo Matemático Low-Level

The `BitwiseMath` class provides optimized mathematical operations using bitwise manipulation for maximum performance:

### Features / Funcionalidades

| Category | Operations | Description |
|----------|-----------|-------------|
| **Vector 2D/3D** | pack/unpack, dot, magnitude | Packed integer vectors using bitwise ops |
| **Matrix 4x4** | transpose, determinant, trace | Efficient matrix transformations |
| **Trigonometry** | sine, cosine, atan2 | Fast approximations using lookup tables |
| **Entropy/Harmony** | entropy, syntropy, harmony | Information-theoretic calculations |
| **Spectral** | frequency bin, low-pass filter, resonance | Signal processing helpers |
| **Utilities** | branchless min/max/abs, Morton codes, bit rotation | Branchless operations for performance |

### Usage / Uso

```java
import com.vectras.vm.core.BitwiseMath;

// Vector operations
int vec2d = BitwiseMath.packVec2(100, 200);
int dotProduct = BitwiseMath.dotVec2(vec2d, vec2d);

// Fast trigonometry (fixed-point)
int sinVal = BitwiseMath.fastSineFixed(BitwiseMath.FIXED_PI / 4);
int cosVal = BitwiseMath.fastCosineFixed(BitwiseMath.FIXED_PI / 4);

// Entropy calculations
int harmony = BitwiseMath.computeHarmony(value1, value2);
int syntropy = BitwiseMath.computeSyntropy(data, 0, data.length);

// Branchless operations
int min = BitwiseMath.branchlessMin(a, b);
int abs = BitwiseMath.branchlessAbs(x);

// Morton codes for spatial indexing
int morton = BitwiseMath.interleave16(x, y);
```

### Design Goals / Objetivos de Design

- **Zero allocations**: No heap allocations in hot paths
- **Branchless**: Minimize branch mispredictions
- **SIMD-friendly**: Operations that can be vectorized
- **Fixed-point**: Integer arithmetic for deterministic results

---

## 🔮 What to Expect / O que Esperar

### Performance Expectations / Expectativas de Performance

| Emulated OS | Expected Boot Time | Responsiveness |
|-------------|-------------------|----------------|
| Windows 98/ME | 30-60s | Good |
| Windows XP | 90-180s | Fair |
| Linux (Tiny Core) | 15-30s | Excellent |
| Android x86 | 45-90s | Good |
| macOS (older) | 120-240s | Fair |

### What You Can Do / O que Você Pode Fazer

✅ Run legacy Windows applications  
✅ Test Linux distributions  
✅ Run retro games with 3Dfx support  
✅ Development and testing environments  
✅ Educational purposes  
✅ Benchmark device emulation performance  

### Limitations / Limitações

⚠️ Performance depends on host device  
⚠️ Not suitable for modern games  
⚠️ Heavy workloads may drain battery  
⚠️ Some OSes require specific configurations  

---

![GitHub Repo stars](https://img.shields.io/github/stars/xoureldeen/Vectras-VM-Android)
![GitHub watchers](https://img.shields.io/github/watchers/xoureldeen/Vectras-VM-Android)
![GitHub forks](https://img.shields.io/github/forks/xoureldeen/Vectras-VM-Android)
[![Total downloads](https://img.shields.io/github/downloads/xoureldeen/Vectras-VM-Android/total)](https://github.com/xoureldeen/Vectras-VM-Android/releases)
[![Discord server](https://img.shields.io/discord/911060166810681345)][link-discord]
[![Telegram Channel][ico-telegram]][link-telegram]
[![Software License][ico-license]](LICENSE)

Welcome to Vectras VM! A virtual machine app for Android based on QEMU that lets you emulate various OSes including: [![Windows](https://custom-icon-badges.demolab.com/badge/Windows-0078D6?logo=windows11&logoColor=white)](https://www.microsoft.com/en-us/windows) [![Linux](https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black)](https://www.linux.org/) [![macOS](https://img.shields.io/badge/macOS-000000?logo=apple&logoColor=F0F0F0)](https://www.apple.com/macos) [![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/).

If you need help, check out [our documentation](https://vectras.vercel.app/how.html). For quick answers, join the [Vectras Telegram group](http://t.me/vectras_vm_discussion).

[![Tutorial for beginners](https://img.youtube.com/vi/AlNbverd0xE/mqdefault.jpg)](https://www.youtube.com/watch?v=AlNbverd0xE)

## Nota sobre forks (PT-BR)

Seguimos a mesma ideia aplicada nos forks de Termux, UserLAnd e androidx_rmr: aproveitar cada possibilidade sem alterar a missão original do projeto e mantendo o fluxo de compilação intacto sempre que possível.

### Perfis técnicos dos repositórios relacionados

| Repositório | Stack predominante | Perfil técnico / finalidade | Observação (fork/original) |
| --- | --- | --- | --- |
| UserLAnd | C, ASM, Kotlin/Java, Python, Shell | Base userland Android, mistura baixo nível com app | Fork/origem não especificado |
| Rafaelia_Private | Python + C (Shell/Make) | Núcleo híbrido, engine/tooling | Fork/origem não especificado |
| Magisk_Rafaelia | Python, Rust, Kotlin, C++, Shell | Mod/root Android, orquestra app + nativo | Fork/origem não especificado |
| ZIPRAF_OMEGA_FULL | Python + Shell | Automação/pipelines | Fork/origem não especificado |
| termux-app-rafacodephi | Java, C, Shell | App Android (fork Termux) | Fork de Termux |
| Tegmark | (não informado) | Possível pesquisa/conteúdo | Fork/origem não especificado |
| Unify_Teory_of_mission_holly_espiritual_ciencias_ | Python | Pesquisa 100% Python | Fork/origem não especificado |
| relativity-living-light | Jupyter Notebook | Pesquisa/experimental | Fork/origem não especificado |
| llamaRafaelia | C/C++, CUDA/Metal, Python, Svelte | Inferência LLM de alta performance | Fork/origem não especificado |

## Device Compatibility

Works fine on devices manufactured in 2021 or later and devices equipped with Snapdragon 855 CPU or better. You can try running Vectras VM on unsupported devices, but we cannot guarantee stability or support. Here are the devices tested:

| Stable           | Unstable                                        |
| --------------- | ------------------------------------------- |
| Samsung      | Oppo      |
| Google Pixel      | Realme      |
| Xiaomi      | OnePlus      |
| Redmi      | Huawei      |
| Poco      | Honor      |
| ZTE      | vivo      |
| RedMagic      | IQOO      |

### Minimum System Requirements
- Android 6.0 and up.
- 3GB RAM (1GB of free RAM).
- A good processor.

### Recommended System Requirements
- Android 8.1 and up.
- 8GB RAM (3GB of free RAM).
- CPU and Android OS support 64-bit.
- Snapdragon 855 CPU or better.
- Integrated or removable cooling system (if running operating systems from 2010 to present).
> [!TIP]
> If the OS you are trying to emulate crashes, try using an older version.

# Installation

### Stable Releases

You can download Vectras VM from the [releases](https://github.com/xoureldeen/Vectras-VM-Android/releases) page or the [official website](https://vectras.vercel.app/download.html).

or


[![OpenAPK](https://img.shields.io/badge/Get%20it%20on-OpenAPK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.openapk.net/vectras-vm/com.vectras.vm/)

### Beta Releases

We publish a **new beta release after every commit** — so you can always test the latest features and improvements!

[![Download Beta](https://img.shields.io/badge/Download-Beta-blue?style=for-the-badge&logo=github)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases)

### Bootstraps
QEMU 9.2.4 - 3dfx (only for Vectras VM 3.5.0):
- [For Android ARM (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.0/base-nosve-vectras-vm-arm64-v8a.tar.gz)
- [For Android x86 (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.0/base-vectras-vm-x86_64.tar.gz)

QEMU 9.2.2 - 3dfx (recommended and for Vectras VM 3.5.1+):
- [For Android ARM (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.1/base-genegic-nosve-vectras-vm-arm64-v8a.tar.gz)
- [For Android ARM (32-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.4/base-vectras-vm-armeabi-v7a.tar.gz)
- [For Android x86 (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.1/base-generic-vectras-vm-x86_64.tar.gz)
- [For Android x86 (32-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.5.4/base-vectras-vm-x86.tar.gz)

QEMU 9.2.2 - 3dfx (for Vectras VM 3.2.9 - 3.4.9):
- [For Android ARM (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.2.9/base-vectras-vm-arm64-v8a.tar.gz)
- [For Android x86 (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.2.9/base-vectras-vm-x86_64.tar.gz)

QEMU 8.2.0 - 3dfx (only for Vectras VM 2.9.5):
- [For Android ARM (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.2.9/vectras-vm-arm64-v8a.tar.gz)
- [For Android x86 (64-bit)](https://github.com/AnBui2004/Vectras-VM-Emu-Android/releases/download/3.2.9/vectras-vm-x86_64.tar.gz)

### 3Dfx Wrappers

- [For QEMU 9.2.x - 3dfx](https://github.com/AnBui2004/Vectras-VM-Emu-Android/blob/master/3dfx/3dfx-wrappers-3.5.0.iso)
- [For QEMU 8.2.0 - 3dfx](https://github.com/AnBui2004/Vectras-VM-Emu-Android/blob/master/3dfx/3dfx-wrappers-2.9.5.iso)

# 📚 Documentation / Documentação

Comprehensive technical and academic documentation is available in the [`docs/`](docs/README.md) directory:

| Document | Description | Language |
|----------|-------------|----------|
| [📖 Documentation Index](docs/README.md) | Main documentation index and navigation | EN/PT-BR |
| [📜 Preface](docs/PREFACE.md) | Project context, motivation, and acknowledgments | EN/PT-BR |
| [📋 Abstract](docs/ABSTRACT.md) | Technical abstract (PhD-level) | English |
| [📋 Resumo](docs/RESUMO.md) | Resumo técnico acadêmico | Português |
| [🏗️ Architecture](docs/ARCHITECTURE.md) | System architecture and design patterns | English |
| [📚 Bibliography](docs/BIBLIOGRAPHY.md) | Academic references (IEEE, ACM, ABNT) | EN/PT-BR |
| [📖 Glossary](docs/GLOSSARY.md) | Technical terminology and acronyms | EN/PT-BR |
| [🤝 Contributing](docs/CONTRIBUTING.md) | Contribution guidelines | EN/PT-BR |
| [⚡ Vectra Core](VECTRA_CORE.md) | Experimental runtime framework | English |
| [📌 Project State](PROJECT_STATE.md) | Current project lifecycle state | PT-BR |
| [🧭 Doc Index](DOC_INDEX.md) | Quick index of core documents | PT-BR |
| [📜 Third-Party Notices](THIRD_PARTY_NOTICES.md) | Third-party components and license pointers | PT-BR |

# Donate
Help support the project by contributing!

[![Buy Me a Coffee at ko-fi.com][ico-ko-fi]][link-ko-fi]
[![Support me on Patreon](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Fshieldsio-patreon.vercel.app%2Fapi%3Fusername%3Dendel%26type%3Dpatrons&style=flat)](https://patreon.com/VectrasTeam)

# Thanks to / Agradecimentos

## 🏆 Original Author / Autor Original

**[xoureldeen](https://github.com/xoureldeen)** - Original creator and developer of Vectras VM. This project exists because of his vision, hard work, and dedication to open-source software. We are deeply grateful for his foundational contributions.

**[xoureldeen](https://github.com/xoureldeen)** - Criador e desenvolvedor original do Vectras VM. Este projeto existe graças à sua visão, trabalho árduo e dedicação ao software de código aberto. Somos profundamente gratos por suas contribuições fundamentais.

## Libraries and Projects / Bibliotecas e Projetos

- [3DFX QEMU PATCH](https://github.com/kjliew/qemu-3dfx)
- [Alpine Linux](https://www.alpinelinux.org/)
- [Glide](https://github.com/bumptech/glide)
- [Gson](https://github.com/google/gson)
- [OkHttp](https://github.com/square/okhttp)
- [PROOT](https://proot-me.github.io/)
- [QEMU](https://github.com/qemu/qemu)
- [Termux](https://github.com/termux)
- [ZoomImageView](https://github.com/k1slay/ZoomImageView)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=xoureldeen/Vectras-VM-Android,AnBui2004/Vectras-VM-Emu-Android&type=date&legend=top-left)](https://www.star-history.com/#xoureldeen/Vectras-VM-Android&AnBui2004/Vectras-VM-Emu-Android&type=date&legend=top-left)

[ico-telegram]: https://img.shields.io/badge/Telegram-2CA5E0?logo=telegram&logoColor=white
[ico-discord]: https://img.shields.io/badge/Discord-%235865F2.svg?&logo=discord&logoColor=white
[ico-version]: https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white
[ico-license]: https://img.shields.io/badge/License-GPL_v2-blue.svg
[ico-buymeacoffee]: https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?&logo=buy-me-a-coffee&logoColor=black
[ico-ko-fi]: https://img.shields.io/badge/Ko--fi-FF5E5B?logo=ko-fi&logoColor=white

[link-discord]: https://discord.gg/t8TACrKSk7
[link-telegram]: https://t.me/vectras_os
[link-repo]: https://github.com/xoureldeen/Vectras-VM-Android/
[link-releases]: https://github.com/xoureldeen/Vectras-VM-Android/releases/
[link-ko-fi]: https://ko-fi.com/vectrasvm

<div align="center">
<p align="center">
  <img src="resources/vectrasvm.png" style="width: 30%;" />
</p>
<p align="center">
  <a href="https://trendshift.io/repositories/12183" target="_blank"><img src="https://trendshift.io/api/badge/repositories/12183" alt="xoureldeen%2FVectras-VM-Android | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
</p>
</div>

# Vectras VM
[![Ceasefire Now](https://badge.techforpalestine.org/default)](https://techforpalestine.org/learn-more)

> **📚 Documentation**: Comprehensive technical documentation is available in the [docs/](docs/README.md) directory, including academic-style documentation with preface, abstract, architecture details, and bibliography.

> **Note**: This project includes an experimental **Vectra Core MVP** - a deterministic event processing framework with integrity verification. See [VECTRA_CORE.md](VECTRA_CORE.md) for details.

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

# Donate
Help support the project by contributing!

[![Buy Me a Coffee at ko-fi.com][ico-ko-fi]][link-ko-fi]
[![Support me on Patreon](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Fshieldsio-patreon.vercel.app%2Fapi%3Fusername%3Dendel%26type%3Dpatrons&style=flat)](https://patreon.com/VectrasTeam)

# Thanks to
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

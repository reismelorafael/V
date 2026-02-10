# Vectras VM Android — Plataforma de Virtualização Determinística

> **Resumo (EN):** This release hardens process execution and logging paths to prevent deadlocks/zombies, introduces deterministic process supervision with failover, modernizes Android storage permissions (Scoped Storage/SAF), and adds operational audit ledgering.

![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![QEMU](https://img.shields.io/badge/QEMU-Supervised-00599C)
![Logs](https://img.shields.io/badge/Logs-Backpressure%20Safe-6A1B9A)
![Status](https://img.shields.io/badge/Release-Exec%20Stability%20Patch-0A66C2)

---

## 🎯 Visão do produto
O Vectras VM Android executa VMs com foco em mobilidade, compatibilidade e estabilidade operacional. Nesta atualização, o foco foi tornar o runtime resiliente sob pressão (flood de logs, processos long-running e encerramento controlado).

## 🧱 Arquitetura (alto nível)
```mermaid
flowchart LR
    UI[UI / Activities] --> VM[VMManager]
    VM --> PS[ProcessSupervisor]
    VM --> QMP[QMP Client]
    UI --> TERM[Terminal]
    TERM --> DRN[ProcessOutputDrainer]
    DRN --> RL[TokenBucketRateLimiter]
    RL --> RB[BoundedStringRingBuffer]
    RB --> LOGS[VectrasStatus + Snapshot]
    PS --> AUD[AuditLedger]
```

## 🚀 Como rodar
1. Configure Android SDK/NDK e JDK 17.
2. Build debug:
   ```bash
   ./gradlew :app:assembleDebug
   ```
3. Testes unitários:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

## 📚 Documentação técnica
- [Arquitetura](docs/ARCHITECTURE.md)
- [Segurança](docs/SECURITY.md)
- [Operações](docs/OPERATIONS.md)
- [Release Notes](RELEASE_NOTES.md)
- [Changelog](CHANGELOG.md)

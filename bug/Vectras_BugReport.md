# Vectras-VM-Android — Bug & Error Report (Audit Refresh)

Base anterior revalidada contra o código atual da branch.

---

## ✅ Achados stale marcados como resolvidos / desatualizados

### BUG-01 · CMakeLists.txt — alvo `rmr_core_static` indefinido
- **Status:** Resolved in current branch.
- **Evidência atual:** `vectra_core_accel` não linka mais `rmr_core_static`; o link usa apenas alvos realmente declarados (`rmr_policy_static`, `bitraf_static`) e `${log-lib}`. Referências: `app/src/main/cpp/CMakeLists.txt:59-65`, `:100-113`, `:126-134`.

### BUG-02 · `vectra_core_accel.c` — include de `rmr_policy_kernel.h`
- **Status:** Resolved in current branch.
- **Evidência atual:** include está protegido por macro condicional. Referência: `app/src/main/cpp/vectra_core_accel.c:10-12`.

### BUG-03 · `nativeCopyBytes` — release incorreto em falha de `GetPrimitiveArrayCritical`
- **Status:** Resolved in current branch.
- **Evidência atual:** fluxo agora libera `src` com `JNI_ABORT` quando `dst` falha, e não há caminho com pin leak. Referências: `app/src/main/cpp/vectra_core_accel.c:120-129`, `:136-137`.

### BUG-04 · `nativeXorChecksum` / `nativeArenaWrite` — ausência de bounds check
- **Status:** Resolved in current branch.
- **Evidência atual:** ambos validam offset/length contra `GetArrayLength` antes de aritmética de ponteiro. Referências: `app/src/main/cpp/vectra_core_accel.c:146-149`, `:187`.

### BUG-05 · `nativeCoreVerify` — lógica de retorno invertida / magic value
- **Status:** Resolved in current branch.
- **Evidência atual:** retorno está normalizado para `1/0` quando `rc == RMR_KERNEL_OK`; erros negativos propagados; códigos não documentados viram `RMR_KERNEL_ERR_STATE`. Referência: `app/src/main/cpp/vectra_core_accel.c:376-385`.

### BUG-06 · `nativeAudit` — mistura `RMR_UK_OK` e `RMR_KERNEL_OK`
- **Status:** Not reproducible.
- **Evidência atual:** `RMR_UK_OK` e `RMR_KERNEL_OK` são ambos definidos como `0`; não há divergência semântica no branch atual. Referências: `engine/rmr/include/rmr_unified_jni_base.h:11-12`, `app/src/main/cpp/vectra_core_accel.c:241`.

### BUG-07 · `nativeCoreRoute` — campo `out.route` inexistente
- **Status:** Not reproducible.
- **Evidência atual:** a struct JNI atual contém explicitamente o campo `route`. Referências: `engine/rmr/include/rmr_unified_kernel.h:166-173`, `app/src/main/cpp/vectra_core_accel.c:357-361`.

### BUG-08 · `nativeReadBatch` — 64 KB na stack
- **Status:** Resolved in current branch.
- **Evidência atual:** buffer de batch foi movido para heap com `malloc/free`. Referências: `app/src/main/cpp/vectra_core_accel.c:452-456`, `:479-481`.

### BUG-09 · `QmpClient.java` — injeção JSON por concatenação
- **Status:** Resolved in current branch.
- **Evidência atual:** `migrate`, `changevncpasswd`, `ejectdev` e `changedev` usam `JSONObject` para montar payload. Referências: `app/src/main/java/com/vectras/qemu/utils/QmpClient.java:281-333`.

### BUG-10 · `QmpClient.java` — handshake QMP invertido
- **Status:** Resolved in current branch.
- **Evidência atual:** handshake atual espera greeting (`QMP`) antes de enviar `qmp_capabilities`. Referência: `app/src/main/java/com/vectras/qemu/utils/QmpClient.java:128-134`.

### BUG-11 · `QmpClient.java` — `getQueryMigrateResponse` perdido / dead code
- **Status:** Resolved in current branch.
- **Evidência atual:** `getQueryMigrateResponse` é usado no fluxo principal e delega para parser comum que preserva linhas lidas. Referências: `app/src/main/java/com/vectras/qemu/utils/QmpClient.java:74`, `:228-269`.

### BUG-12 · `MainSettingsManager.java` — `assert` ineficaz
- **Status:** Resolved in current branch.
- **Evidência atual:** fluxo ativo não usa asserts para as validações reportadas; há validação explícita de fragmento nulo/vazio em runtime. Referência: `app/src/main/java/com/vectras/qemu/MainSettingsManager.java:103-106`.

---

## 🔓 Achados ainda abertos (priorizados por explorabilidade + impacto)

1. **W-02 (ALTO relativo dentro dos remanescentes)** — `popen("logcat -v brief", "r")` em bridge JNI:
   - Risco atual baixo por comando hardcoded, mas mantém superfície sensível em código nativo caso evolua para parâmetros externos.
   - Referência: `app/src/main/cpp/vectra_core_accel.c:445`.

2. **W-03 (MÉDIO)** — `sendCommand` `synchronized static` no `QmpClient`:
   - Pode criar contenção global e atrasar comandos urgentes sob concorrência.
   - Referências: `app/src/main/java/com/vectras/qemu/utils/QmpClient.java:31`, `:35`, `:39`.

3. **W-04 (MÉDIO/BAIXO, impacto de produto)** — `VECTRA_CORE_ENABLED=false` em `release`:
   - Risco funcional: código nativo crítico pode não ser exercitado no caminho de produção.
   - Referência: `app/build.gradle:80-95`.

4. **W-01 (BAIXO)** — flags C++ (`-fno-exceptions -fno-rtti`) com base nativa primária em C:
   - Não quebra build, mas sinalização pode induzir expectativa errada de cobertura de flags.
   - Referência: `app/build.gradle:32-37`.

---

## Resumo atualizado

- Itens stale reclassificados: **12/12**.
- Falhas críticas/altas originais do relatório: **sem reprodução direta no estado atual**.
- Backlog aberto atual: **4 avisos**, priorizados acima por explorabilidade e impacto de usuário.

import { useState } from "react";

const data = {
  repos: [
    { id: "vectras", label: "Vectras-VM-Android", sub: "Vectra2 / RMR Engine", status: "present", size: "30MB" },
    { id: "termux", label: "termux-app-rafacodephi", sub: "Baremetal C/ASM Layer", status: "present", size: "2.2MB" },
    { id: "relativity", label: "relativity-living-light", sub: "RLL Physics / Cosmo", status: "present", size: "24MB" },
    { id: "llama", label: "LlamaRafaelia", sub: "BitStack World Model", status: "missing", size: "—" },
    { id: "rafgit", label: "RafGitTools", sub: "Git Tooling App", status: "missing", size: "—" },
  ],
  haveList: [
    { label: "rmr_baremetal_compat.h", detail: "Bump allocator 1MB, rmr_malloc/memset/memcpy — bem escrito", repo: "Vectras" },
    { label: "ASM ARM64 + x86_64 + RISCV64", detail: "rmr_casm_*.S presentes no engine/rmr/interop/", repo: "Vectras" },
    { label: "CMake + Makefile", detail: "Build system estruturado, flags por target", repo: "Vectras" },
    { label: "Android.mk rafaelia module", detail: "9 .c files listados, 16KB page-align flag presente", repo: "Termux" },
    { label: "PHI32 (0x9E3779B9)", detail: "Presente em múltiplos arquivos como magic number", repo: "Ambos" },
    { label: "10-state FSM / policy kernel", detail: "rmr_policy_kernel.c com ciclo ψχρΔΣΩ", repo: "Vectras" },
    { label: "Patches pendentes (_incoming/)", detail: "3 ZIPs: CHANGED-FILES, VECTRA2_PATCHES, DELTA_fix", repo: "Vectras" },
    { label: "MVP ASM puro", detail: "rafaelia_mvp_puro.s + rafaelia_opcodes.hex", repo: "Termux" },
    { label: "RLL cosmo pipeline Python", detail: "scipy/numpy pipeline com mock data e testes", repo: "Relativity" },
    { label: "kernel_bridge.c (RLL)", detail: "Arch detect ARM64/x86_64/ARM32, minimal C", repo: "Relativity" },
  ],
  bugs: [
    {
      sev: "CRÍTICO",
      color: "#ff3b3b",
      label: "zero_compat.h — NÃO EXISTE",
      detail: "O macro layer RAFAELIA ZERO (substituto universal de stdlib) está AUSENTE. Nenhum arquivo zero_compat.h ou zero.h foi encontrado em nenhum dos 3 repos. Bloqueador para RAFAELIA ZERO.",
      repo: "Todos",
    },
    {
      sev: "CRÍTICO",
      color: "#ff3b3b",
      label: "baremetal.c usa malloc/free/stdio",
      detail: "#include <stdio.h> linha 60, #include <stdlib.h> linha 414. mx_malloc() e mx_free() chamam stdlib malloc/free diretamente. Contradição direta com princípio no-libc.",
      repo: "Termux",
    },
    {
      sev: "CRÍTICO",
      color: "#ff3b3b",
      label: "LlamaRafaelia + RafGitTools — AUSENTES",
      detail: "2 de 4 repos do ecossistema não foram enviados. BitStack World Model, Witness validation e Git tooling não estão auditáveis nem integráveis.",
      repo: "—",
    },
    {
      sev: "ALTO",
      color: "#ff8c00",
      label: "rmr_unified_kernel.c: malloc() em modo JNI (default)",
      detail: "RMR_JNI_BUILD=ON por padrão. Isso ativa #define rmr_malloc(sz) malloc(sz) — o bump allocator é bypassado. rmr_neon_simd.c tem o mesmo padrão.",
      repo: "Vectras",
    },
    {
      sev: "ALTO",
      color: "#ff8c00",
      label: "ARM64 ASM desabilitado por padrão (CMake)",
      detail: "RMR_ASM_CORE_ARM64_VALIDATED=OFF no CMakeLists.txt raiz. Hot path ARM64 não compila sem -DRMR_ASM_CORE_ARM64_VALIDATED=ON explícito.",
      repo: "Vectras",
    },
    {
      sev: "ALTO",
      color: "#ff8c00",
      label: "rll_arch_detect_x86_64() — símbolo inexistente",
      detail: "kernel_bridge.c declara extern int rll_arch_detect_x86_64(void) mas a implementação não existe em nenhum arquivo do repo Relativity. Linkagem quebrará em x86_64.",
      repo: "Relativity",
    },
    {
      sev: "ALTO",
      color: "#ff8c00",
      label: "3 patches _incoming/ não aplicados",
      detail: "RAFAELIA-CHANGED-FILES.zip (15K), RAFAELIA_VECTRA2_PATCHES.zip (33K), ZIPDROP_DELTA_fix.zip (2.4K) estão no repo mas não aplicados. Estado do código diverge das correções.",
      repo: "Vectras",
    },
    {
      sev: "MÉDIO",
      color: "#f0c040",
      label: "PHI32 não unificado — magic numbers dispersos",
      detail: "0x9E3779B9 aparece como literal em 8+ arquivos. Em alguns como 0x9E3779B97F4A7C15ULL (64-bit). Sem #define PHI32 centralizado em zero.h.",
      repo: "Ambos",
    },
    {
      sev: "MÉDIO",
      color: "#f0c040",
      label: "if/for/while em hot paths do kernel",
      detail: "rmr_unified_kernel.c tem 20+ C keywords nos paths quentes. Princípio arquitetural: hot-path loops devem usar ASM branches. Não implementado.",
      repo: "Vectras",
    },
    {
      sev: "MÉDIO",
      color: "#f0c040",
      label: "ARM32 sem ASM bridge dedicado",
      detail: "armeabi-v7a está nos abiFilters de ambos os builds mas não existe rmr_casm_arm32.S. Cai em path C genérico.",
      repo: "Termux/Vectras",
    },
    {
      sev: "MÉDIO",
      color: "#f0c040",
      label: "rafaelia module BUILD_SHARED_LIBRARY",
      detail: "Android.mk usa BUILD_SHARED_LIBRARY — linka contra NDK libc. Não é baremetal puro. Para zero-OS-friction precisaria ser static ou entry puro.",
      repo: "Termux",
    },
    {
      sev: "BAIXO",
      color: "#4fc3f7",
      label: "old/ com 40+ .c usando stdlib",
      detail: "Termux/rafaelia/old/ tem printf/malloc/free em todo arquivo. Risco de inclusão acidental no build path. Recomendado isolar em branch ou remover.",
      repo: "Termux",
    },
    {
      sev: "BAIXO",
      color: "#4fc3f7",
      label: "Relativity Python pipeline desconectado do C",
      detail: "Nenhuma FFI/ctypes bridge entre rll_pipeline/*.py e kernel_bridge.c. Os dois mundos não se falam ainda.",
      repo: "Relativity",
    },
    {
      sev: "BAIXO",
      color: "#4fc3f7",
      label: "baremetal_consistency_test.c usa malloc/fprintf",
      detail: "Arquivo de teste usa stdlib diretamente. Testes de consistência não rodarão em contexto baremetal real.",
      repo: "Termux",
    },
  ],
  nextSteps: [
    { pri: 1, action: "Criar zero.h", detail: "Todos os PHI32, TRINITY633, constantes em hex, reunidos num único header." },
    { pri: 2, action: "Criar zero_compat.h", detail: "Macro layer: #define malloc rmr_malloc, #define memset rmr_memset etc. Substituição global de stdlib." },
    { pri: 3, action: "Aplicar _incoming/ patches", detail: "Deszipar e aplicar RAFAELIA_VECTRA2_PATCHES.zip + CHANGED-FILES.zip antes de qualquer novo código." },
    { pri: 4, action: "Corrigir baremetal.c", detail: "Substituir malloc/free/stdio por rmr_baremetal_compat.h. Migrar mx_t para bump allocator." },
    { pri: 5, action: "Habilitar ARM64 ASM no CMake", detail: "-DRMR_ASM_CORE_ARM64_VALIDATED=ON como default para target arm64-v8a." },
    { pri: 6, action: "Implementar rll_arch_detect_x86_64", detail: "Adicionar .S ou .c com a implementação real em Relativity/core/lowlevel_runtime." },
    { pri: 7, action: "Enviar LlamaRafaelia + RafGitTools", detail: "2 repos ausentes bloqueiam auditoria e integração RAFAELIA ZERO." },
  ],
};

const SEV_ORDER = ["CRÍTICO", "ALTO", "MÉDIO", "BAIXO"];

export default function App() {
  const [tab, setTab] = useState("overview");
  const [expandedBug, setExpandedBug] = useState(null);
  const [sevFilter, setSevFilter] = useState("TODOS");

  const filteredBugs =
    sevFilter === "TODOS" ? data.bugs : data.bugs.filter((b) => b.sev === sevFilter);

  const counts = {
    CRÍTICO: data.bugs.filter((b) => b.sev === "CRÍTICO").length,
    ALTO: data.bugs.filter((b) => b.sev === "ALTO").length,
    MÉDIO: data.bugs.filter((b) => b.sev === "MÉDIO").length,
    BAIXO: data.bugs.filter((b) => b.sev === "BAIXO").length,
  };

  return (
    <div style={{ fontFamily: "'Courier New', monospace", background: "#0a0a0f", minHeight: "100vh", color: "#c8d8e8", padding: "0" }}>
      {/* Header */}
      <div style={{ background: "linear-gradient(135deg, #0d1b2a 0%, #0a0f1a 100%)", borderBottom: "1px solid #1a3a5c", padding: "24px 32px" }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 16, flexWrap: "wrap" }}>
          <span style={{ fontSize: 11, color: "#4a7fa8", letterSpacing: 4, textTransform: "uppercase" }}>RAFAELIA ΣΩΔΦ</span>
          <span style={{ color: "#1a3a5c" }}>|</span>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#e8f4ff", letterSpacing: 1 }}>
            Auditoria de Repositórios
          </h1>
          <span style={{ fontSize: 11, color: "#2a5a8c", marginLeft: "auto" }}>ψ→χ→ρ→Δ→Σ→Ω</span>
        </div>
        <div style={{ marginTop: 8, display: "flex", gap: 12, flexWrap: "wrap" }}>
          {[
            ["overview", "Visão Geral"],
            ["have", "✅ O que Existe"],
            ["bugs", "⚠ Problemas"],
            ["next", "→ Próximos Passos"],
          ].map(([id, label]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              style={{
                background: tab === id ? "#1a3a5c" : "transparent",
                border: tab === id ? "1px solid #2a6a9c" : "1px solid #1a2a3c",
                color: tab === id ? "#7fc8f8" : "#4a7fa8",
                padding: "6px 16px",
                cursor: "pointer",
                fontSize: 12,
                letterSpacing: 1,
                textTransform: "uppercase",
                transition: "all 0.15s",
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div style={{ padding: "28px 32px", maxWidth: 900 }}>

        {/* ─── OVERVIEW ─── */}
        {tab === "overview" && (
          <div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12, marginBottom: 28 }}>
              {[
                { label: "Repos Presentes", val: "3 / 5", color: "#f0c040" },
                { label: "CRÍTICO", val: counts["CRÍTICO"], color: "#ff3b3b" },
                { label: "ALTO", val: counts["ALTO"], color: "#ff8c00" },
                { label: "MÉDIO", val: counts["MÉDIO"], color: "#f0c040" },
                { label: "BAIXO", val: counts["BAIXO"], color: "#4fc3f7" },
              ].map((c) => (
                <div key={c.label} style={{ background: "#0d1520", border: `1px solid ${c.color}33`, padding: "14px 16px" }}>
                  <div style={{ fontSize: 11, color: "#4a7fa8", letterSpacing: 2, textTransform: "uppercase", marginBottom: 6 }}>{c.label}</div>
                  <div style={{ fontSize: 32, fontWeight: 700, color: c.color }}>{c.val}</div>
                </div>
              ))}
            </div>

            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 11, color: "#4a7fa8", letterSpacing: 3, textTransform: "uppercase", marginBottom: 12 }}>Estado dos Repositórios</div>
              {data.repos.map((r) => (
                <div key={r.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 0", borderBottom: "1px solid #0d1a28" }}>
                  <span style={{ fontSize: 16 }}>{r.status === "present" ? "✅" : "❌"}</span>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 13, color: r.status === "present" ? "#c8e8ff" : "#ff6060", fontWeight: 600 }}>{r.label}</div>
                    <div style={{ fontSize: 11, color: "#3a6a8a" }}>{r.sub}</div>
                  </div>
                  <span style={{ fontSize: 11, color: "#2a4a6a", fontVariantNumeric: "tabular-nums" }}>{r.size}</span>
                  {r.status === "missing" && (
                    <span style={{ fontSize: 10, background: "#3a0a0a", color: "#ff6060", border: "1px solid #ff3b3b44", padding: "2px 8px", letterSpacing: 1 }}>AUSENTE</span>
                  )}
                </div>
              ))}
            </div>

            <div style={{ background: "#0d1520", border: "1px solid #1a3a5c", padding: 16, fontSize: 12, lineHeight: 1.8, color: "#7a9ab8" }}>
              <span style={{ color: "#4fc3f7" }}>Φ_ethica diagnóstico: </span>
              3 de 5 repos auditados. O bloqueador principal é a ausência de <span style={{ color: "#ff8c00" }}>zero_compat.h</span> e <span style={{ color: "#ff8c00" }}>zero.h</span> — sem eles RAFAELIA ZERO não pode ser sintetizado.
              O motor Vectras/RMR tem estrutura sólida mas opera em modo JNI/stdlib por default. 
              Termux baremetal.c viola a regra no-libc.
            </div>
          </div>
        )}

        {/* ─── HAVE ─── */}
        {tab === "have" && (
          <div>
            <div style={{ fontSize: 11, color: "#4a7fa8", letterSpacing: 3, textTransform: "uppercase", marginBottom: 16 }}>O Que Existe e Funciona</div>
            {data.haveList.map((h, i) => (
              <div key={i} style={{ padding: "12px 0", borderBottom: "1px solid #0d1a28", display: "flex", gap: 12, alignItems: "flex-start" }}>
                <span style={{ color: "#22dd88", fontSize: 14, marginTop: 2 }}>✓</span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, color: "#a8d8f8", fontWeight: 600 }}>{h.label}</div>
                  <div style={{ fontSize: 12, color: "#3a6a8a", marginTop: 3 }}>{h.detail}</div>
                </div>
                <span style={{ fontSize: 10, color: "#2a5a7a", background: "#0a1520", border: "1px solid #1a2a3c", padding: "2px 8px", whiteSpace: "nowrap" }}>{h.repo}</span>
              </div>
            ))}
          </div>
        )}

        {/* ─── BUGS ─── */}
        {tab === "bugs" && (
          <div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
              {["TODOS", ...SEV_ORDER].map((s) => (
                <button
                  key={s}
                  onClick={() => setSevFilter(s)}
                  style={{
                    background: sevFilter === s ? "#1a2a3c" : "transparent",
                    border: `1px solid ${sevFilter === s ? "#2a5a8c" : "#1a2a3c"}`,
                    color: s === "CRÍTICO" ? "#ff3b3b" : s === "ALTO" ? "#ff8c00" : s === "MÉDIO" ? "#f0c040" : s === "BAIXO" ? "#4fc3f7" : "#7a9ab8",
                    padding: "4px 14px",
                    cursor: "pointer",
                    fontSize: 11,
                    letterSpacing: 1,
                  }}
                >
                  {s} {s !== "TODOS" && `(${counts[s]})`}
                </button>
              ))}
            </div>

            {filteredBugs.map((b, i) => (
              <div
                key={i}
                style={{ background: "#0d1520", border: `1px solid ${b.color}33`, marginBottom: 8, cursor: "pointer" }}
                onClick={() => setExpandedBug(expandedBug === i ? null : i)}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px" }}>
                  <span style={{ fontSize: 10, fontWeight: 700, color: b.color, background: `${b.color}18`, border: `1px solid ${b.color}44`, padding: "2px 8px", letterSpacing: 1, minWidth: 58, textAlign: "center" }}>
                    {b.sev}
                  </span>
                  <span style={{ flex: 1, fontSize: 13, color: "#c8e0f8", fontWeight: 600 }}>{b.label}</span>
                  <span style={{ fontSize: 10, color: "#2a5a7a", background: "#0a1520", padding: "2px 8px", border: "1px solid #1a2a3c" }}>{b.repo}</span>
                  <span style={{ color: "#2a5a7a", fontSize: 12 }}>{expandedBug === i ? "▲" : "▼"}</span>
                </div>
                {expandedBug === i && (
                  <div style={{ padding: "0 16px 14px 16px", fontSize: 12, color: "#6a9ab8", lineHeight: 1.8, borderTop: `1px solid ${b.color}22` }}>
                    <div style={{ paddingTop: 12 }}>{b.detail}</div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* ─── NEXT STEPS ─── */}
        {tab === "next" && (
          <div>
            <div style={{ fontSize: 11, color: "#4a7fa8", letterSpacing: 3, textTransform: "uppercase", marginBottom: 16 }}>Retroalimentar[next] — Ordem de Prioridade</div>
            {data.nextSteps.map((s) => (
              <div key={s.pri} style={{ display: "flex", gap: 16, padding: "14px 0", borderBottom: "1px solid #0d1a28", alignItems: "flex-start" }}>
                <div style={{
                  minWidth: 32, height: 32, background: "#0d1520", border: "1px solid #1a3a5c",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: 14, color: "#4fc3f7", fontWeight: 700
                }}>
                  {s.pri}
                </div>
                <div>
                  <div style={{ fontSize: 13, color: "#a8d8f8", fontWeight: 600, marginBottom: 4 }}>{s.action}</div>
                  <div style={{ fontSize: 12, color: "#3a6a8a", lineHeight: 1.7 }}>{s.detail}</div>
                </div>
              </div>
            ))}

            <div style={{ marginTop: 24, background: "#0d1520", border: "1px solid #1a3a5c", padding: 16, fontSize: 11, color: "#3a6a8a", lineHeight: 2 }}>
              <span style={{ color: "#4fc3f7" }}>R_3(s) = </span>
              <span style={{ color: "#22dd88" }}>F_ok</span>: engine RMR + ASM bridges estruturados ·{" "}
              <span style={{ color: "#ff8c00" }}>F_gap</span>: zero.h, zero_compat.h, 2 repos ausentes, stdlib em baremetal.c ·{" "}
              <span style={{ color: "#a8d8f8" }}>F_next</span>: priorizar itens 1→3 antes de qualquer nova feature
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

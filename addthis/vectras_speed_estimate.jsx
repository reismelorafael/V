import { useState } from "react";

// ─── DATA ─────────────────────────────────────────────────────────────────
const layers = [
  {
    id: "jni",
    label: "JNI Bridge",
    sub: "vectra_core_accel.c → Java ↔ C",
    color: "#a78bfa",
    metrics: [
      { name: "Overhead por chamada JNI", val: "100–500 ns", raw: 300, unit: "ns/call", note: "GetPrimitiveArrayCritical + mutex lock/unlock" },
      { name: "Throughput max chamadas/s", val: "~2–10 M calls/s", raw: 5e6, unit: "calls/s", note: "pthread_mutex overhead domina em chamadas curtas" },
      { name: "nativeCopyBytes (1 KB)", val: "~400 ns", raw: 400, unit: "ns", note: "200ns JNI + 200ns rmr_memcpy baremetal" },
      { name: "nativeXorChecksum (4 KB)", val: "~600 ns", raw: 600, unit: "ns", note: "300ns JNI + 300ns XOR fold C (NEON desabilitado)" },
      { name: "nativePopcount32", val: "~150 ns", raw: 150, unit: "ns", note: "trivial mas ainda paga overhead de barreira JNI" },
    ],
  },
  {
    id: "alu",
    label: "ALU / Inteiro",
    sub: "RmR_Bench_Alu — XOR/shift/mul 32-bit",
    color: "#34d399",
    metrics: [
      { name: "Throughput (ARM64 Cortex-A78, 3 GHz)", val: "~1.5–3 B ops/s", raw: 2e9, unit: "ops/s", note: "4 ops/iter × 512M iters/s em pipeline OoO" },
      { name: "Throughput (x86-64 Ryzen 5, 4 GHz)", val: "~2–4 B ops/s", raw: 3e9, unit: "ops/s", note: "Melhor IPC em desktop" },
      { name: "Ciclos por iteração (ARM64)", val: "~2–4 cycles", raw: 3, unit: "cycles/iter", note: "XOR+shift+mul com dependência de cadeia" },
      { name: "Throughput bench padrão (n=2048)", val: "~400–800 M/s", raw: 600e6, unit: "iters/s", note: "Score formula: (ops<<8)/cycles" },
      { name: "Penalidade modo JNI (vs baremetal)", val: "0% (C puro)", raw: 0, unit: "%", note: "ALU não paga overhead extra; compilado igual" },
    ],
  },
  {
    id: "branch",
    label: "Branch / FSM",
    sub: "RmR_Bench_Branch — política ψχρΔΣΩ",
    color: "#fbbf24",
    metrics: [
      { name: "Misprediction penalty (Cortex-A78)", val: "~12–18 cycles", raw: 15, unit: "cycles", note: "BHTE 2-level; padrão XOR é difícil de prever" },
      { name: "Throughput (50% miss rate)", val: "~200–400 M branch/s", raw: 300e6, unit: "branch/s", note: "0x9E3779B9 XOR → padrão pseudo-aleatório" },
      { name: "Throughput (predizível 95%)", val: "~1.5–2.5 B branch/s", raw: 2e9, unit: "branch/s", note: "Melhor caso; loops regulares" },
      { name: "FSM 10-state (rmr_policy_kernel)", val: "~50–150 M transições/s", raw: 100e6, unit: "trans/s", note: "Inclui PHI32 multiply + state table lookup" },
      { name: "Custo pthread_mutex (VM flow)", val: "~50–200 ns/lock", raw: 100, unit: "ns", note: "VmFlow usa mutex em cada nativeVmFlowMark()" },
    ],
  },
  {
    id: "mem",
    label: "Memória / Bandwidth",
    sub: "Bump allocator + cache hierarchy",
    color: "#60a5fa",
    metrics: [
      { name: "rmr_memcpy (LPDDR5, alinhado)", val: "~20–40 GB/s", raw: 30e9, unit: "GB/s", note: "word-by-word 8B, cache quente; limitado por bus" },
      { name: "rmr_memset (bulk zero, ARM64)", val: "~25–50 GB/s", raw: 35e9, unit: "GB/s", note: "stp x0,x0 loop; próximo de bandwidth máximo" },
      { name: "Bump alloc rmr_malloc (1 KB)", val: "~5–15 ns", raw: 10, unit: "ns", note: "Apenas add + compare; sem lock" },
      { name: "Arena 1 MB esgotamento", val: "~100 K allocs (10B avg)", raw: 1e5, unit: "allocs", note: "Após isso retorna NULL; sem free real" },
      { name: "Cache miss L2→L3 penalty", val: "~40–60 ns", raw: 50, unit: "ns", note: "Crítico para bench matrix com n>4" },
      { name: "XOR fold C sem NEON (rafa_cti)", val: "~3–6 GB/s", raw: 4e9, unit: "GB/s", note: "Loop C compilado; sem NEON perde 4× vs target" },
      { name: "XOR fold NEON (se habilitado)", val: "~18–25 GB/s", raw: 20e9, unit: "GB/s", note: "Target >20 GB/s Cortex-A78 — actualmente OFF" },
    ],
  },
  {
    id: "matrix",
    label: "Matrix / Math",
    sub: "RmR_Bench_Matrix n≤8 — 8×8 MMul",
    color: "#f87171",
    metrics: [
      { name: "8×8 MMul u32 (C, ARM64)", val: "~800 K–1.5 M MMul/s", raw: 1e6, unit: "MMul/s", note: "n³=512 ops, ~2000-3000 cycles; sem NEON" },
      { name: "8×8 MMul u32 (NEON se ativado)", val: "~4–8 M MMul/s", raw: 6e6, unit: "MMul/s", note: "vmul.u32 × 16B lanes; estimativa teórica" },
      { name: "Math fabric AutodetectPlan", val: "~uma vez / boot", raw: 1, unit: "call", note: "Chamado uma vez; resultado cacheado em RmR_HW_Info" },
      { name: "PHI32 hash step (u32 mul)", val: "~1 cycle (ARM64)", raw: 1, unit: "cycle", note: "mul w0,w0,w1 latência 3c mas throughput 1c/s" },
      { name: "CRC32C HW (arm_acle __crc32cb)", val: "~1 B bytes/s", raw: 1e9, unit: "bytes/s", note: "1 byte/cycle × 3 GHz; só quando CRC32 feature ativo" },
      { name: "CRC32C SW tabela (fallback)", val: "~300–600 MB/s", raw: 450e6, unit: "bytes/s", note: "Table lookup 256 entries; cache-friendly" },
    ],
  },
  {
    id: "qemu",
    label: "QEMU / VM Guest",
    sub: "Camada de emulação — overhead real",
    color: "#fb923c",
    metrics: [
      { name: "TCG (software translate, x86→ARM)", val: "~200–500 MIPS guest", raw: 350e6, unit: "MIPS", note: "1/5 a 1/10 do host nativo; regra geral TCG" },
      { name: "KVM (se disponível, user Android)", val: "~70–90% host speed", raw: 0.8, unit: "fração", note: "KVM não acessível em user-space Android normal" },
      { name: "Overhead QEMU por instrução guest", val: "~5–15 host cycles", raw: 10, unit: "cycles", note: "TCG block translation + chain jumps" },
      { name: "Block translation cache (TB cache)", val: "~16–64 MB", raw: 32e6, unit: "bytes", note: "Reúsa blocos traduzidos; warm = muito mais rápido" },
      { name: "I/O virtual (VirtIO block)", val: "~50–200 K IOPS", raw: 100e3, unit: "IOPS", note: "Limitado por latência de host filesystem" },
      { name: "Memória guest (mmap anon)", val: "~mesma latência host", raw: 0, unit: "", note: "Sem overhead extra para RAM; mas sem huge pages fáceis" },
    ],
  },
  {
    id: "art",
    label: "ART JIT (camada Java)",
    sub: "Compilação DEX → ARM64 machine code",
    color: "#a3e635",
    metrics: [
      { name: "Inlining ART (métodos pequenos)", val: "Sim — conservador", raw: 1, unit: "", note: "< 32 Dalvik instructions; eficiente" },
      { name: "SIMD auto-vectorização ART", val: "NÃO", raw: 0, unit: "", note: "ART não emite NEON automaticamente; requer JNI" },
      { name: "Boot JIT latência (warm up)", val: "~50–200 ms", raw: 100, unit: "ms", note: "Primeira execução compila; AOT elimina se profile salvo" },
      { name: "Overhead Java → JNI vs C puro", val: "~2–10× para chamadas curtas", raw: 5, unit: "×", note: "Para operações longas (>10μs) overhead amortiza" },
      { name: "ART vs HotSpot desktop", val: "ART ~2–4× mais lento", raw: 3, unit: "×", note: "ART é compilador mais conservador da indústria" },
    ],
  },
];

const summary = [
  { label: "CRC32C HW", current: "300–600 MB/s (SW)", potential: "~3 GB/s (HW arm_acle)", blocker: "arm_acle.h no kernel = contamina baremetal", gain: "×5–10" },
  { label: "XOR Fold / memcpy", current: "3–6 GB/s (C)", potential: "~20 GB/s (NEON)", blocker: "RMR_ASM_CORE_ARM64_VALIDATED=OFF", gain: "×4" },
  { label: "Matrix 8×8", current: "~1 M MMul/s", potential: "~6 M MMul/s", blocker: "NEON path não existe ainda", gain: "×6" },
  { label: "FSM / Policy Kernel", current: "~100 M trans/s", potential: "~400 M trans/s", blocker: "if/for C no hot path; sem ASM dispatch", gain: "×4" },
  { label: "JNI overhead", current: "100–500 ns/call", potential: "~50 ns (batch calls)", blocker: "Sem batching JNI hoje; mutex por operação", gain: "×3–5" },
  { label: "QEMU guest (TCG)", current: "~350 MIPS", potential: "~1.5 GIPS (KVM host)", blocker: "KVM não disponível em user-space Android", gain: "×4 (requer root/KVM)" },
];

function fmt(v) {
  if (typeof v !== "number") return v;
  if (v >= 1e12) return (v / 1e12).toFixed(1) + " T";
  if (v >= 1e9) return (v / 1e9).toFixed(1) + " G";
  if (v >= 1e6) return (v / 1e6).toFixed(1) + " M";
  if (v >= 1e3) return (v / 1e3).toFixed(0) + " K";
  return v;
}

export default function App() {
  const [active, setActive] = useState("jni");
  const [tab, setTab] = useState("layers");
  const cur = layers.find(l => l.id === active);

  return (
    <div style={{ fontFamily: "'Courier New', monospace", background: "#060910", minHeight: "100vh", color: "#b0c8dc" }}>
      {/* header */}
      <div style={{ background: "#08101a", borderBottom: "1px solid #0c1e30", padding: "18px 26px" }}>
        <div style={{ fontSize: 10, color: "#1e4060", letterSpacing: 4, marginBottom: 4 }}>RAFAELIA · VECTRAS-VM · ANÁLISE DE VELOCIDADE</div>
        <div style={{ fontSize: 19, color: "#c8e4ff", fontWeight: 700 }}>Velocidade Estimada da VM por Camada</div>
        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          {[["layers","Por Camada"],["summary","Gap → Potencial"]].map(([id,l]) => (
            <button key={id} onClick={() => setTab(id)} style={{
              background: tab===id?"#0d1e30":"transparent",
              border:`1px solid ${tab===id?"#1a4060":"#0c1a24"}`,
              color: tab===id?"#60b4f4":"#3a6a8a",
              padding:"5px 14px", cursor:"pointer", fontSize:11, letterSpacing:1
            }}>{l}</button>
          ))}
        </div>
      </div>

      {tab === "layers" && (
        <div style={{ display: "flex", height: "calc(100vh - 100px)" }}>
          {/* sidebar */}
          <div style={{ width: 200, borderRight: "1px solid #0c1e30", overflowY: "auto" }}>
            {layers.map(l => (
              <div key={l.id} onClick={() => setActive(l.id)} style={{
                padding: "12px 16px",
                cursor: "pointer",
                background: active===l.id?"#0a1828":"transparent",
                borderLeft: active===l.id?`3px solid ${l.color}`:"3px solid transparent",
                borderBottom: "1px solid #0a1420",
              }}>
                <div style={{ fontSize: 12, color: active===l.id?l.color:"#4a7a9a", fontWeight: 600 }}>{l.label}</div>
                <div style={{ fontSize: 10, color: "#1e3a50", marginTop: 2, lineHeight: 1.4 }}>{l.sub}</div>
              </div>
            ))}
          </div>

          {/* content */}
          <div style={{ flex: 1, overflowY: "auto", padding: "20px 24px" }}>
            <div style={{ fontSize: 10, color: cur.color, letterSpacing: 3, marginBottom: 4, textTransform: "uppercase" }}>
              {cur.label}
            </div>
            <div style={{ fontSize: 12, color: "#3a6a8a", marginBottom: 20 }}>{cur.sub}</div>

            {cur.metrics.map((m, i) => (
              <div key={i} style={{ marginBottom: 10, background: "#080f1a", border: `1px solid ${cur.color}18`, padding: "12px 16px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12, marginBottom: 6 }}>
                  <span style={{ fontSize: 12, color: "#90b8d0", fontWeight: 600 }}>{m.name}</span>
                  <span style={{ fontSize: 14, color: cur.color, fontWeight: 700, whiteSpace: "nowrap" }}>{m.val}</span>
                </div>
                <div style={{ fontSize: 11, color: "#2a5070", lineHeight: 1.6 }}>{m.note}</div>
              </div>
            ))}

            {/* source */}
            <div style={{ marginTop: 16, padding: "10px 14px", background: "#050c14", border: "1px solid #0a1828", fontSize: 10, color: "#1e3a50", lineHeight: 1.8 }}>
              <span style={{ color: "#2a5a7a" }}>Fontes: </span>
              rmr_bench_suite.c · rmr_ll_ops.c · rmr_neon_simd.c · vectra_core_accel.c · ANALISE_REAL_HARDWARE_COMPLETA.md · ANALISE_REAL_COMPILACAO_HARDWARE.md · specs públicas Cortex-A78 / Snapdragon 8 Gen
            </div>
          </div>
        </div>
      )}

      {tab === "summary" && (
        <div style={{ padding: "24px 26px" }}>
          <div style={{ fontSize: 10, color: "#3a6a8a", letterSpacing: 3, marginBottom: 16, textTransform: "uppercase" }}>
            Estado Atual → Potencial com Correções
          </div>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #0c1e30" }}>
                  {["Operação", "ATUAL (bugs ativos)", "POTENCIAL (bugs corrigidos)", "Bloqueador", "Ganho"].map(h => (
                    <th key={h} style={{ padding: "8px 12px", color: "#2a5a7a", fontWeight: 600, textAlign: "left", fontSize: 10, letterSpacing: 1, textTransform: "uppercase" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {summary.map((s, i) => (
                  <tr key={i} style={{ borderBottom: "1px solid #08121c" }}>
                    <td style={{ padding: "10px 12px", color: "#90b8d0", fontWeight: 600 }}>{s.label}</td>
                    <td style={{ padding: "10px 12px", color: "#ff6060" }}>{s.current}</td>
                    <td style={{ padding: "10px 12px", color: "#34d399" }}>{s.potential}</td>
                    <td style={{ padding: "10px 12px", color: "#4a7a9a", fontSize: 11 }}>{s.blocker}</td>
                    <td style={{ padding: "10px 12px", color: "#fbbf24", fontWeight: 700 }}>{s.gain}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ marginTop: 28 }}>
            <div style={{ fontSize: 10, color: "#3a6a8a", letterSpacing: 3, marginBottom: 14 }}>ENVELOPE GERAL DA VM</div>
            {[
              { label: "QEMU guest (TCG mode, hoje)", val: "200–500 MIPS", col: "#ff6060", pct: 12 },
              { label: "RMR kernel C (JNI mode, hoje)", val: "1–3 GOPS ALU", col: "#f87171", pct: 30 },
              { label: "CRC32C SW (hoje)", val: "300–600 MB/s", col: "#fbbf24", pct: 20 },
              { label: "memcpy/XOR fold C (hoje)", val: "3–6 GB/s", col: "#fbbf24", pct: 35 },
              { label: "memcpy NEON (corrigido)", val: "~20–40 GB/s", col: "#34d399", pct: 90 },
              { label: "CRC32C HW arm_acle (corrigido)", val: "~3 GB/s", col: "#34d399", pct: 65 },
              { label: "QEMU com KVM host (raiz+KVM)", val: "~1.5 GIPS", col: "#60a5fa", pct: 70 },
            ].map((b, i) => (
              <div key={i} style={{ marginBottom: 8 }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                  <span style={{ fontSize: 11, color: "#6a9ab8" }}>{b.label}</span>
                  <span style={{ fontSize: 12, color: b.col, fontWeight: 700 }}>{b.val}</span>
                </div>
                <div style={{ background: "#0a1420", height: 6, borderRadius: 2 }}>
                  <div style={{ background: b.col, height: "100%", width: b.pct + "%", borderRadius: 2, transition: "width 0.4s" }} />
                </div>
              </div>
            ))}
          </div>

          <div style={{ marginTop: 24, background: "#080f1a", border: "1px solid #0c1e30", padding: 14, fontSize: 11, color: "#2a5a7a", lineHeight: 2 }}>
            <span style={{ color: "#4fc3f7" }}>Φ_ethica · síntese: </span>
            Estado atual = <span style={{ color: "#ff6060" }}>20–35% do potencial real</span>.
            O RMR engine tem a estrutura certa mas opera com 3 freios ativos:
            <span style={{ color: "#ff8c00" }}> ARM64 ASM desligado</span>,
            <span style={{ color: "#ff8c00" }}> JNI_BUILD=ON defaultando malloc</span>,
            <span style={{ color: "#ff8c00" }}> stdlib no kernel</span>.
            Corrigir esses 3 pontos com zero.h + zero_compat.h + flag flip = ganho imediato de
            <span style={{ color: "#34d399" }}> 3–6× sem mudar 1 linha de algoritmo</span>.
          </div>
        </div>
      )}
    </div>
  );
}

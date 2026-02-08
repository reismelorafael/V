Beleza. Aqui vai um MVP “Caixa-Preta Auditável” (Python) que já registra:

✅ Trilha de auditoria (eventos encadeados tipo “hashchain”)

✅ Prova efêmera por janela (HMAC com chave derivada por auditor_id + nonce + clock_window)

✅ Integridade (CRC32 + paridade XOR simples)

✅ “2 dentro / 1 fora” (commit local + publish no ledger; verificação externa)

✅ Detecção de replay/fora da janela (TTL + window)


> ⚠️ Nota Ethica[8]: isso é MVP didático, não substitui HSM/TPM nem prova física de hardware. Mas já dá pra “registrar mais um” e evoluir.



#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAFAELIA :: BlackBox Audit MVP (Python)
- Hashchain ledger
- Ephemeral audit key (time-windowed)
- CRC32 + XOR parity
- "2-in / 1-out" style: commit locally, publish ledger entry, verify externally
"""

import os, time, json, hmac, hashlib, struct, zlib
from dataclasses import dataclass, asdict
from typing import Optional, Dict, Any, List

# -----------------------------
# Low-level helpers (Integrity)
# -----------------------------

def sha256(b: bytes) -> bytes:
    return hashlib.sha256(b).digest()

def hex256(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def crc32_u32(data: bytes) -> int:
    # Standard CRC32 (zlib). For CRC32C, you'd swap polynomial; MVP keeps CRC32.
    return zlib.crc32(data) & 0xFFFFFFFF

def xor_parity_byte(data: bytes) -> int:
    # Simple parity (XOR reduction) — fast, catches odd flips in aggregate.
    p = 0
    for x in data:
        p ^= x
    return p

def pack_u32(x: int) -> bytes:
    return struct.pack(">I", x & 0xFFFFFFFF)

def pack_u64(x: int) -> bytes:
    return struct.pack(">Q", x & 0xFFFFFFFFFFFFFFFF)

# -----------------------------
# Ephemeral audit key
# -----------------------------

def time_window(now_s: Optional[int] = None, window_s: int = 30) -> int:
    now = int(time.time()) if now_s is None else int(now_s)
    return now // window_s

def derive_ephemeral_key(master_secret: bytes, auditor_id: str, nonce: bytes, window: int) -> bytes:
    """
    Key = HMAC(master, auditor_id || nonce || window)
    """
    msg = auditor_id.encode("utf-8") + b"|" + nonce + b"|" + pack_u64(window)
    return hmac.new(master_secret, msg, hashlib.sha256).digest()

def make_proof(ephemeral_key: bytes, payload_bytes: bytes) -> bytes:
    return hmac.new(ephemeral_key, payload_bytes, hashlib.sha256).digest()

# -----------------------------
# Ledger entry (hashchain)
# -----------------------------

@dataclass
class AuditEntry:
    idx: int
    ts: int
    window: int
    ttl_s: int
    auditor_id: str
    nonce_hex: str

    # What is audited (public meta only):
    block_id: str
    meta: Dict[str, Any]

    # Integrity signals:
    crc32: int
    parity: int
    payload_hash: str  # hash(payload) but not payload itself

    # Proof / chain:
    prev_hash: str
    entry_hash: str
    proof_hex: str

# -----------------------------
# BlackBox Auditor MVP
# -----------------------------

class BlackBoxAuditMVP:
    def __init__(self, master_secret: bytes, window_s: int = 30, ttl_s: int = 60):
        self.master_secret = master_secret
        self.window_s = int(window_s)
        self.ttl_s = int(ttl_s)
        self.ledger: List[AuditEntry] = []
        self._seen_nonces = set()  # anti-replay (process-local MVP)

    def _prev_hash(self) -> str:
        return self.ledger[-1].entry_hash if self.ledger else "0"*64

    def audit_commit(self, auditor_id: str, block_id: str, payload: bytes, meta: Optional[Dict[str, Any]] = None) -> AuditEntry:
        """
        Commit an audit entry:
        - Generates nonce
        - Derives ephemeral key bound to time window
        - Stores only hashes + integrity (CRC/parity), not payload itself
        """
        meta = meta or {}
        ts = int(time.time())
        win = time_window(ts, self.window_s)

        nonce = os.urandom(16)
        nonce_hex = nonce.hex()

        # Anti-replay within this process (MVP)
        if nonce_hex in self._seen_nonces:
            raise RuntimeError("Replay nonce detected (should never happen).")
        self._seen_nonces.add(nonce_hex)

        # Integrity signals for payload (private content never leaves)
        c = crc32_u32(payload)
        p = xor_parity_byte(payload)
        ph = hex256(payload)

        # Public payload for proof: minimal + deterministic
        public_payload = {
            "ts": ts,
            "window": win,
            "ttl_s": self.ttl_s,
            "auditor_id": auditor_id,
            "nonce": nonce_hex,
            "block_id": block_id,
            "meta": meta,
            "crc32": c,
            "parity": p,
            "payload_hash": ph,
            "prev_hash": self._prev_hash(),
        }
        payload_bytes = json.dumps(public_payload, sort_keys=True, separators=(",", ":")).encode("utf-8")

        eph = derive_ephemeral_key(self.master_secret, auditor_id, nonce, win)
        proof = make_proof(eph, payload_bytes)

        entry_hash = hashlib.sha256(payload_bytes + proof).hexdigest()

        entry = AuditEntry(
            idx=len(self.ledger),
            ts=ts,
            window=win,
            ttl_s=self.ttl_s,
            auditor_id=auditor_id,
            nonce_hex=nonce_hex,
            block_id=block_id,
            meta=meta,
            crc32=c,
            parity=p,
            payload_hash=ph,
            prev_hash=self._prev_hash(),
            entry_hash=entry_hash,
            proof_hex=proof.hex(),
        )
        self.ledger.append(entry)
        return entry

    def verify_entry(self, entry: AuditEntry, now_s: Optional[int] = None) -> bool:
        """
        Verify:
        - TTL/window validity (ephemeral)
        - hashchain continuity
        - proof correctness (needs master_secret)
        """
        now = int(time.time()) if now_s is None else int(now_s)
        # TTL check
        if now > entry.ts + entry.ttl_s:
            return False

        # Window check: allow current or previous window to tolerate small drifts
        current_win = time_window(now, self.window_s)
        if entry.window not in (current_win, current_win - 1):
            return False

        # Recompute payload_bytes
        public_payload = {
            "ts": entry.ts,
            "window": entry.window,
            "ttl_s": entry.ttl_s,
            "auditor_id": entry.auditor_id,
            "nonce": entry.nonce_hex,
            "block_id": entry.block_id,
            "meta": entry.meta,
            "crc32": entry.crc32,
            "parity": entry.parity,
            "payload_hash": entry.payload_hash,
            "prev_hash": entry.prev_hash,
        }
        payload_bytes = json.dumps(public_payload, sort_keys=True, separators=(",", ":")).encode("utf-8")

        # Derive key and check proof
        nonce = bytes.fromhex(entry.nonce_hex)
        eph = derive_ephemeral_key(self.master_secret, entry.auditor_id, nonce, entry.window)
        proof = make_proof(eph, payload_bytes)

        if proof.hex() != entry.proof_hex:
            return False

        # Check entry_hash
        expected_entry_hash = hashlib.sha256(payload_bytes + proof).hexdigest()
        if expected_entry_hash != entry.entry_hash:
            return False

        return True

    def verify_chain(self) -> bool:
        """
        Verify full chain linkage. (Does not check TTL; structural only.)
        """
        prev = "0"*64
        for e in self.ledger:
            if e.prev_hash != prev:
                return False
            prev = e.entry_hash
        return True

    def export_ledger(self) -> str:
        """
        Export ledger as JSON lines (good for append-only logs).
        """
        lines = []
        for e in self.ledger:
            lines.append(json.dumps(asdict(e), sort_keys=True, separators=(",", ":")))
        return "\n".join(lines) + ("\n" if lines else "")

# -----------------------------
# Demo (run)
# -----------------------------

def main():
    # Master secret: in real world => stored in HSM/TPM/KMS; here: env or random
    ms = os.environ.get("RAFAELIA_MASTER_SECRET", "").encode("utf-8")
    if not ms:
        ms = sha256(b"demo-secret-only-do-not-use-in-prod")

    bb = BlackBoxAuditMVP(master_secret=ms, window_s=30, ttl_s=60)

    auditor_id = "auditor:∆RafaelVerboΩ"
    block_id = "block:ZIPRAFΩ:42"

    # Private payload (stays local)
    payload = b"SEGREDO_INDUSTRIAL::formula_x + parametros_y + ..."

    entry = bb.audit_commit(
        auditor_id=auditor_id,
        block_id=block_id,
        payload=payload,
        meta={
            "mode": "blackbox-audit",
            "policy": "zero-trust",
            "graph_hint": "2-in-1-out",
            "token": "Trinity633",
        },
    )

    ok_entry = bb.verify_entry(entry)
    ok_chain = bb.verify_chain()

    print("=== RAFAELIA :: BlackBox Audit MVP ===")
    print("entry.idx      :", entry.idx)
    print("entry.hash     :", entry.entry_hash[:16] + "…")
    print("prev.hash      :", entry.prev_hash[:16] + "…")
    print("payload_hash   :", entry.payload_hash[:16] + "…")
    print("crc32          :", hex(entry.crc32))
    print("parity(xor)    :", hex(entry.parity))
    print("verify_entry   :", ok_entry)
    print("verify_chain   :", ok_chain)
    print("\n--- ledger (jsonl) ---")
    print(bb.export_ledger(), end="")

if __name__ == "__main__":
    main()

Como usar (rápido) 🧷

python3 blackbox_audit_mvp.py
# opcional: exportar segredo mestre (não faça isso em prod)
export RAFAELIA_MASTER_SECRET="minha-chave-mestra-local"
python3 blackbox_audit_mvp.py


---

Próximo passo (pra virar teu “ECC + paridade 2-dentro/1-fora” de verdade) 🧬

Se você disser “TriECC agora”, eu te devolvo o upgrade do MVP com:

ECC simples (Hamming SECDED ou BCH pequeno) + paridade por bloco

“2 dentro / 1 fora” como quórum (2 assinaturas internas + 1 verificador externo)

estrutura de grafo (adjacency + fechamento) em vez de lista linear



---

🔻 Retroalimentar[3]

F_ok: MVP já cria prova efêmera, hashchain, CRC/paridade sem vazar payload.

F_gap: ECC real (SECDED/BCH) + quórum/fechamento de grafo ainda não entrou.

F_next: você manda: “TriECC (SECDED) + quorum 2/1 + grafo Flor” que eu entrego o v2. 🌀

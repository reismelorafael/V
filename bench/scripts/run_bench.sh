#!/usr/bin/env bash
set -euo pipefail

runs="${1:-7}"
out_dir="${2:-bench/results}"
mkdir -p "$out_dir"

make run-bench >/dev/null

scores_file="$out_dir/scores.txt"
: > "$scores_file"

for i in $(seq 1 "$runs"); do
  csv="$out_dir/run_${i}.csv"
  json="$out_dir/run_${i}.json"
  ./build/bench/rmr_bench "$csv" "$json" | tee "$out_dir/run_${i}.log"
  score=$(awk -F= '/bench_total_score=/{print $2}' "$out_dir/run_${i}.log")
  echo "$score" >> "$scores_file"
done

python3 - "$scores_file" "$out_dir/summary.json" <<'PY'
import json,sys,statistics,math
p=sys.argv[1]
out=sys.argv[2]
vals=[int(x.strip()) for x in open(p) if x.strip()]
vals_sorted=sorted(vals)
if not vals_sorted:
    raise SystemExit(1)
med=statistics.median(vals_sorted)
idx=max(0, math.ceil(0.95*len(vals_sorted))-1)
p95=vals_sorted[idx]
obj={"runs":len(vals_sorted),"median":med,"p95":p95,"min":vals_sorted[0],"max":vals_sorted[-1],"scores":vals_sorted}
open(out,'w').write(json.dumps(obj,indent=2)+"\n")
print(json.dumps(obj))
PY

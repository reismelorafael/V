#!/usr/bin/env python3
import csv
import pathlib
import sys
from collections import Counter


def main() -> int:
  if len(sys.argv) < 3:
    print("usage: bitomega_csv_to_dot.py <input.csv> <output.dot> [summary.md]", file=sys.stderr)
    return 2

  in_csv = pathlib.Path(sys.argv[1])
  out_dot = pathlib.Path(sys.argv[2])
  summary_md = pathlib.Path(sys.argv[3]) if len(sys.argv) > 3 else None

  if not in_csv.exists():
    print(f"missing input CSV: {in_csv}", file=sys.stderr)
    return 3

  transitions: list[tuple[str, str, str]] = []
  with in_csv.open("r", encoding="utf-8", newline="") as f:
    reader = csv.DictReader(f)
    required = {"state_prev", "state_new", "direction"}
    if reader.fieldnames is None or not required.issubset(set(reader.fieldnames)):
      print("invalid CSV header: expected state_prev,state_new,direction", file=sys.stderr)
      return 4
    for row in reader:
      transitions.append((row["state_prev"], row["state_new"], row["direction"]))

  edge_counts = Counter(transitions)
  out_dot.parent.mkdir(parents=True, exist_ok=True)
  with out_dot.open("w", encoding="utf-8") as f:
    f.write("digraph bitomega_transition_graph {\n")
    f.write("  rankdir=LR;\n")
    f.write("  node [shape=ellipse, style=filled, fillcolor=\"#e6f2ff\", color=\"#1f4d7a\"];\n")
    for (prev, nxt, direction), count in sorted(edge_counts.items()):
      f.write(f"  \"{prev}\" -> \"{nxt}\" [label=\"{direction} ({count})\"];\n")
    f.write("}\n")

  if summary_md is not None:
    summary_md.parent.mkdir(parents=True, exist_ok=True)
    with summary_md.open("w", encoding="utf-8") as f:
      f.write("# BitΩ Smoke Test Summary\n\n")
      f.write(f"Total transitions: **{len(transitions)}**\n\n")
      f.write("| State Prev | State New | Direction | Count |\n")
      f.write("|---|---|---|---:|\n")
      for (prev, nxt, direction), count in sorted(edge_counts.items()):
        f.write(f"| {prev} | {nxt} | {direction} | {count} |\n")

  return 0


if __name__ == "__main__":
  raise SystemExit(main())

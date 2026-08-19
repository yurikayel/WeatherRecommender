#!/usr/bin/env python3
"""Compare Kover XML coverage (and simple duplication) against a baseline.

Used by .github/workflows/quality-gate.yml. Exit codes:
  0 — gate passed
  1 — gate failed (coverage dip or missing current report)
  2 — usage / unexpected error
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any

LINE_EPSILON = 0.01  # percentage points; ignore float noise
LINE_FLOOR = 10.0  # absolute floor; main already beats this
KOVER_COUNTERS = ("LINE", "INSTRUCTION", "METHOD", "BRANCH")
TABLE_LABELS = {
    "LINE": "Lines",
    "INSTRUCTION": "Instructions",
    "METHOD": "Functions",
    "BRANCH": "Branches",
}
MARKER = "<!-- quality-gate -->"
MIN_DUP_LINE_LEN = 20


def _local_tag(tag: str) -> str:
    return tag.split("}", 1)[-1] if "}" in tag else tag


def _parse_xml(path: Path) -> ET.Element:
    # utf-8-sig strips a BOM if present (PowerShell Set-Content -Encoding utf8).
    with path.open("r", encoding="utf-8-sig") as handle:
        tree = ET.parse(handle)
    return tree.getroot()


def parse_kover(path: Path) -> dict[str, dict[str, float | int]]:
    """Parse report-level JaCoCo/Kover counters (not per-package)."""
    root = _parse_xml(path)
    out: dict[str, dict[str, float | int]] = {}
    for child in list(root):
        if _local_tag(child.tag) != "counter":
            continue
        kind = child.attrib.get("type")
        if kind not in KOVER_COUNTERS:
            continue
        missed = int(child.attrib.get("missed", "0"))
        covered = int(child.attrib.get("covered", "0"))
        total = missed + covered
        pct = (100.0 * covered / total) if total else 0.0
        out[kind] = {"covered": covered, "missed": missed, "total": total, "pct": pct}
    return out


def parse_detekt_issues(path: Path | None) -> int | None:
    if path is None or not path.is_file():
        return None
    root = _parse_xml(path)
    count = 0
    for el in root.iter():
        if _local_tag(el.tag) == "error":
            count += 1
    return count


def duplication_from_src(src_root: Path) -> dict[str, float | int]:
    """Exact-line duplication in app/src/main Kotlin (stripped lines ≥ 20 chars).

    Skips package/import lines. This is not CPD/Sonar duplication %; it is an
    honest, cheap figure for the PR comment table.
    """
    counts: Counter[str] = Counter()
    eligible = 0
    if not src_root.is_dir():
        return {"duplicated_lines": 0, "eligible_lines": 0, "percent": 0.0}
    for path in src_root.rglob("*.kt"):
        if "build" in path.parts:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for raw in text.splitlines():
            line = raw.strip()
            if len(line) < MIN_DUP_LINE_LEN:
                continue
            if line.startswith("package ") or line.startswith("import "):
                continue
            counts[line] += 1
            eligible += 1
    duplicated = sum(n for n in counts.values() if n > 1)
    pct = (100.0 * duplicated / eligible) if eligible else 0.0
    return {"duplicated_lines": duplicated, "eligible_lines": eligible, "percent": pct}


def find_kover_xml(root: Path) -> Path | None:
    reports = root / "app" / "build" / "reports" / "kover"
    preferred = [
        reports / "reportDebug.xml",
        reports / "report.xml",
        reports / "xml" / "reportDebug.xml",
        reports / "xml" / "report.xml",
    ]
    for candidate in preferred:
        if candidate.is_file():
            return candidate
    matches = sorted(root.glob("**/build/reports/kover/**/*.xml"))
    return matches[0] if matches else None


def find_detekt_xml(root: Path) -> Path | None:
    preferred = [
        root / "app" / "build" / "reports" / "detekt" / "detekt.xml",
        root / "build" / "reports" / "detekt" / "detekt.xml",
    ]
    for candidate in preferred:
        if candidate.is_file():
            return candidate
    matches = sorted(root.glob("**/build/reports/detekt/*.xml"))
    return matches[0] if matches else None


def _fmt_pct(value: float | None) -> str:
    if value is None:
        return "n/a"
    return f"{value:.2f}%"


def _fmt_delta(delta: float | None, invert_good: bool = False) -> str:
    if delta is None:
        return "n/a"
    if abs(delta) < 0.0005:
        return "0.00%"
    sign = "+" if delta > 0 else ""
    # Coverage: up is good. Duplication / detekt issues: down is good.
    good = (delta > 0) if not invert_good else (delta < 0)
    arrow = "🟢" if good else "🔴"
    return f"{arrow} {sign}{delta:.2f}%"


def _fmt_int_delta(delta: int | None, invert_good: bool = True) -> str:
    if delta is None:
        return "n/a"
    if delta == 0:
        return "0"
    sign = "+" if delta > 0 else ""
    good = (delta < 0) if invert_good else (delta > 0)
    arrow = "🟢" if good else "🔴"
    return f"{arrow} {sign}{delta}"


def evaluate(
    current_kover: dict[str, dict[str, float | int]] | None,
    baseline_kover: dict[str, dict[str, float | int]] | None,
) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    if not current_kover or "LINE" not in current_kover:
        reasons.append("No current Kover LINE counters (unit tests may have failed).")
        return False, reasons
    current_line = float(current_kover["LINE"]["pct"])
    if current_line + 1e-9 < LINE_FLOOR:
        reasons.append(
            f"Line coverage {current_line:.2f}% is below the {LINE_FLOOR:.0f}% floor."
        )
    if baseline_kover and "LINE" in baseline_kover:
        base_line = float(baseline_kover["LINE"]["pct"])
        delta = current_line - base_line
        if delta < -LINE_EPSILON:
            reasons.append(
                f"Line coverage dropped vs baseline ({base_line:.2f}% → {current_line:.2f}%, Δ {delta:.2f}%)."
            )
    elif baseline_kover is None:
        reasons.append("Baseline Kover report missing; cannot compare to main.")
        return False, reasons
    return (len(reasons) == 0), reasons


def render_markdown(
    *,
    passed: bool,
    reasons: list[str],
    current_kover: dict[str, dict[str, float | int]] | None,
    baseline_kover: dict[str, dict[str, float | int]] | None,
    current_dup: dict[str, float | int] | None,
    baseline_dup: dict[str, float | int] | None,
    current_detekt: int | None,
    baseline_detekt: int | None,
    baseline_ref: str,
) -> str:
    status = "✅ Passed" if passed else "❌ Failed"
    lines = [
        MARKER,
        f"## Quality Gate · **Status: {status}**",
        "",
        f"Unit-test **Kover** coverage (domain + data packages) versus `{baseline_ref}`.",
        "Instrumented / emulator tests are not part of this gate.",
        "",
        "### Coverage",
        "",
        "| Metric | Baseline | Current | Δ |",
        "| :--- | ---: | ---: | ---: |",
    ]
    for key in KOVER_COUNTERS:
        label = TABLE_LABELS[key]
        base_pct = (
            float(baseline_kover[key]["pct"])
            if baseline_kover and key in baseline_kover
            else None
        )
        cur_pct = (
            float(current_kover[key]["pct"])
            if current_kover and key in current_kover
            else None
        )
        delta = (
            (cur_pct - base_pct) if (cur_pct is not None and base_pct is not None) else None
        )
        lines.append(
            f"| {label} | {_fmt_pct(base_pct)} | {_fmt_pct(cur_pct)} | {_fmt_delta(delta)} |"
        )

    lines += [
        "",
        "### Duplication",
        "",
        "| Metric | Baseline | Current | Δ |",
        "| :--- | ---: | ---: | ---: |",
    ]
    base_dup = float(baseline_dup["percent"]) if baseline_dup else None
    cur_dup = float(current_dup["percent"]) if current_dup else None
    dup_delta = (
        (cur_dup - base_dup) if (cur_dup is not None and base_dup is not None) else None
    )
    lines.append(
        f"| Duplicated lines | {_fmt_pct(base_dup)} | {_fmt_pct(cur_dup)} | {_fmt_delta(dup_delta, invert_good=True)} |"
    )
    lines += [
        "",
        "_Exact stripped Kotlin lines in `app/src/main` (≥ 20 chars, excluding package/import). "
        "Not CPD/Sonar duplication %._",
        "",
        "### Detekt",
        "",
        "| Metric | Baseline | Current | Δ |",
        "| :--- | ---: | ---: | ---: |",
        f"| Issues | {baseline_detekt if baseline_detekt is not None else 'n/a'} | "
        f"{current_detekt if current_detekt is not None else 'n/a'} | "
        f"{_fmt_int_delta(None if current_detekt is None or baseline_detekt is None else current_detekt - baseline_detekt)} |",
        "",
    ]
    if reasons:
        lines.append("**Why this check failed**")
        lines.append("")
        for reason in reasons:
            lines.append(f"- {reason}")
        lines.append("")
    lines += [
        "<details>",
        "<summary>Gate rules</summary>",
        "",
        f"- Fail if **line** coverage on the PR is lower than `{baseline_ref}` (ε = {LINE_EPSILON}%).",
        f"- Fail if line coverage is below **{LINE_FLOOR:.0f}%** (low floor; not a 65% target).",
        "- The Android CI `koverVerify` job still enforces the 65% domain+data threshold separately.",
        "- A coverage dip does **not** fail the unit-test job; only this Quality Gate check.",
        "- Duplication / detekt deltas are informational and do not fail the gate.",
        "",
        "</details>",
        "",
    ]
    return "\n".join(lines)


def cmd_stage(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)

    kover = find_kover_xml(root)
    if kover:
        dest = out / "kover.xml"
        dest.write_bytes(kover.read_bytes())
        print(f"staged kover: {kover} -> {dest}")
    else:
        print("warning: no Kover XML found", file=sys.stderr)

    detekt = find_detekt_xml(root)
    if detekt:
        dest = out / "detekt.xml"
        dest.write_bytes(detekt.read_bytes())
        print(f"staged detekt: {detekt} -> {dest}")
    else:
        print("warning: no detekt XML found", file=sys.stderr)

    src = root / "app" / "src" / "main"
    dup = duplication_from_src(src)
    (out / "duplication.json").write_text(json.dumps(dup, indent=2) + "\n", encoding="utf-8")
    print(f"staged duplication: {dup}")
    return 0


def _load_kover(dir_path: Path) -> dict[str, dict[str, float | int]] | None:
    path = dir_path / "kover.xml"
    if not path.is_file():
        return None
    return parse_kover(path)


def _load_dup(dir_path: Path) -> dict[str, float | int] | None:
    path = dir_path / "duplication.json"
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8-sig"))


def cmd_compare(args: argparse.Namespace) -> int:
    current_dir = Path(args.current).resolve()
    baseline_dir = Path(args.baseline).resolve()
    current_kover = _load_kover(current_dir)
    baseline_kover = _load_kover(baseline_dir)
    current_dup = _load_dup(current_dir)
    baseline_dup = _load_dup(baseline_dir)
    current_detekt = parse_detekt_issues(current_dir / "detekt.xml")
    baseline_detekt = parse_detekt_issues(baseline_dir / "detekt.xml")

    passed, reasons = evaluate(current_kover, baseline_kover)
    markdown = render_markdown(
        passed=passed,
        reasons=reasons,
        current_kover=current_kover,
        baseline_kover=baseline_kover,
        current_dup=current_dup,
        baseline_dup=baseline_dup,
        current_detekt=current_detekt,
        baseline_detekt=baseline_detekt,
        baseline_ref=args.baseline_ref,
    )
    Path(args.out_md).write_text(markdown, encoding="utf-8")
    payload: dict[str, Any] = {
        "passed": passed,
        "reasons": reasons,
        "current_kover": current_kover,
        "baseline_kover": baseline_kover,
        "current_duplication": current_dup,
        "baseline_duplication": baseline_dup,
        "current_detekt_issues": current_detekt,
        "baseline_detekt_issues": baseline_detekt,
    }
    Path(args.out_json).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    status = "passed" if passed else "failed"
    print(f"quality gate {status}: {'; '.join(reasons) if reasons else 'ok'}")
    return 0 if passed else 1


def cmd_self_test(_args: argparse.Namespace) -> int:
    xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<report name="app">
  <package name="com/example">
    <counter type="LINE" missed="1" covered="1"/>
  </package>
  <counter type="INSTRUCTION" missed="40" covered="60"/>
  <counter type="BRANCH" missed="5" covered="5"/>
  <counter type="LINE" missed="30" covered="70"/>
  <counter type="METHOD" missed="10" covered="90"/>
  <counter type="CLASS" missed="0" covered="10"/>
</report>
"""
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "kover.xml"
        path.write_text(xml, encoding="utf-8")
        parsed = parse_kover(path)
        assert parsed["LINE"]["pct"] == 70.0, parsed
        assert parsed["INSTRUCTION"]["pct"] == 60.0, parsed
        assert parsed["METHOD"]["pct"] == 90.0, parsed
        assert parsed["BRANCH"]["pct"] == 50.0, parsed
        # Package counters must not be mixed into report totals.
        assert parsed["LINE"]["covered"] == 70, parsed

        current = {"LINE": {"pct": 70.0, "covered": 70, "missed": 30, "total": 100}}
        baseline = {"LINE": {"pct": 70.005, "covered": 70, "missed": 30, "total": 100}}
        ok, reasons = evaluate(current, baseline)
        assert ok, reasons

        dipped = {"LINE": {"pct": 69.9, "covered": 699, "missed": 301, "total": 1000}}
        ok, reasons = evaluate(dipped, {"LINE": {"pct": 70.0, "covered": 700, "missed": 300, "total": 1000}})
        assert not ok and reasons, reasons

        low = {"LINE": {"pct": 9.0, "covered": 9, "missed": 91, "total": 100}}
        ok, reasons = evaluate(low, {"LINE": {"pct": 9.0, "covered": 9, "missed": 91, "total": 100}})
        assert not ok, reasons
    print("self-test ok")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Kover quality-gate helper")
    sub = parser.add_subparsers(dest="cmd", required=True)

    stage = sub.add_parser("stage", help="Copy reports + duplication JSON into --out")
    stage.add_argument("--root", default=".")
    stage.add_argument("--out", required=True)
    stage.set_defaults(func=cmd_stage)

    compare = sub.add_parser("compare", help="Write PR comment markdown and JSON")
    compare.add_argument("--current", required=True)
    compare.add_argument("--baseline", required=True)
    compare.add_argument("--out-md", required=True)
    compare.add_argument("--out-json", required=True)
    compare.add_argument("--baseline-ref", default="main")
    compare.set_defaults(func=cmd_compare)

    self_test = sub.add_parser("self-test")
    self_test.set_defaults(func=cmd_self_test)

    args = parser.parse_args(argv)
    try:
        return int(args.func(args))
    except Exception as exc:  # noqa: BLE001 — CI helper; surface the error
        print(f"quality_gate.py error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())

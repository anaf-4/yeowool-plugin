"""Rewrite every item's display_name in an ItemsAdder bundle_ores.yml (bundle_metals pack) to Korean.

Names/tiers come from yeowool-life/src/main/resources/metals.yml (spec 2026-09-30-fantasy-metals §1):
  1 "<이름> 블록", 2 "<이름> 광석", 3 "심층 <이름> 광석", 4 "<이름> 주괴",
  5 "<이름> 원석 블록", 6 "<이름> 원석", 7 "<이름> 괴"; extras from metals.yml extra-names.
Colored by tier (일반 &f, 희귀 &b, 영웅 &d, 전설 &6). Only display_name lines change; a .bak copy is written first.

Usage (run once per server during deployment, then /iazip):
  python apply_metal_names.py <server>/plugins/ItemsAdder/contents/bundle_metals/configs/bundle_ores.yml [--metals PATH] [--dry-run]
  python apply_metal_names.py --self-test
Requires PyYAML (pip install pyyaml).
"""
import argparse
import re
import shutil
import sys
from pathlib import Path

FORMS = {1: "{} 블록", 2: "{} 광석", 3: "심층 {} 광석", 4: "{} 주괴", 5: "{} 원석 블록", 6: "{} 원석", 7: "{} 괴"}
TIER_COLORS = {"common": "&f", "rare": "&b", "epic": "&d", "legendary": "&6"}
DEFAULT_METALS = Path(__file__).resolve().parents[3] / "yeowool-life/src/main/resources/metals.yml"
ITEM_KEY = re.compile(r"^  ([A-Za-z0-9_]+):\s*$")
DISPLAY_NAME = re.compile(r"^(\s{4})display_name:.*$")


def build_names(metals_yml: dict) -> dict:
    names = {}
    for metal_id, metal in (metals_yml.get("metals") or {}).items():
        color = TIER_COLORS.get(str(metal.get("tier", "")).lower(), "&f")
        for suffix, form in FORMS.items():
            names[f"{metal_id}{suffix}"] = color + form.format(metal["name"])
    for item_id, name in (metals_yml.get("extra-names") or {}).items():
        names[item_id] = "&f" + str(name)
    return names


def rewrite(text: str, names: dict):
    """Returns (new text, changed ids, ids without a Korean name)."""
    out, changed, unknown = [], [], []
    current = None
    for line in text.splitlines(keepends=True):
        key = ITEM_KEY.match(line.rstrip("\r\n"))
        if key:
            current = key.group(1)
        match = DISPLAY_NAME.match(line.rstrip("\r\n"))
        if match and current is not None:
            if current in names:
                ending = line[len(line.rstrip("\r\n")):]
                line = f'{match.group(1)}display_name: "{names[current]}"{ending}'
                changed.append(current)
            else:
                unknown.append(current)
        out.append(line)
    return "".join(out), changed, unknown


def self_test():
    names = build_names({"metals": {"blue": {"name": "푸른 강철", "tier": "rare"}}, "extra-names": {"nether7": "네더 괴"}})
    sample = 'info:\n  namespace: bundle_metals\nitems:\n  blue3:\n    display_name: blue3\n  nether7:\n    display_name: "&fnether7"   ####\n  odd1:\n    display_name: odd1\n'
    text, changed, unknown = rewrite(sample, names)
    assert '    display_name: "&b심층 푸른 강철 광석"\n' in text, text
    assert '    display_name: "&f네더 괴"\n' in text, text
    assert changed == ["blue3", "nether7"] and unknown == ["odd1"], (changed, unknown)
    assert names["blue7"] == "&b푸른 강철 괴" and names["blue5"] == "&b푸른 강철 원석 블록"
    print("self-test ok")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("bundle_ores", nargs="?", type=Path)
    parser.add_argument("--metals", type=Path, default=DEFAULT_METALS)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.bundle_ores is None:
        parser.error("bundle_ores.yml 경로가 필요합니다")
    import yaml
    names = build_names(yaml.safe_load(args.metals.read_text(encoding="utf-8")))
    raw = args.bundle_ores.read_bytes().decode("utf-8")
    text, changed, unknown = rewrite(raw, names)
    print(f"{len(changed)}개 이름 변경, 이름 없는 항목 {len(unknown)}개{': ' + ', '.join(unknown) if unknown else ''}")
    if args.dry_run:
        return
    shutil.copy2(args.bundle_ores, args.bundle_ores.with_suffix(args.bundle_ores.suffix + ".bak"))
    args.bundle_ores.write_bytes(text.encode("utf-8"))
    print(f"저장: {args.bundle_ores} (백업 .bak)")


if __name__ == "__main__":
    sys.exit(main())

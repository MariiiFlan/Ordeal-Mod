#!/usr/bin/env python3
"""
sync_talents.py - keep the talent JSONs in step with the procedures.

MCreator regenerates the procedure .java files on every build, so the numbers
you type into blocks are the real ones and the JSON is only a copy. This reads
the generated Java back out and writes those numbers into the JSON, so the
terminal tooltip can never disagree with what the ability actually does.

It only ever touches the fields it can read. Descriptions, icons, kinds, hold
blocks, ability order and anything else you wrote by hand are left alone.

    python sync_talents.py                 one pass over everything
    python sync_talents.py --watch         stay running, sync on every save
    python sync_talents.py --dry           show what would change, write nothing

Point it at the mod with --root if you run it from somewhere else.
"""

import argparse
import collections
import json
import os
import re
import sys
import time

# Where a procedure local ends up in the JSON.
#   java name            json key         how to read it
FIELDS = [
    ("Talent_STR_Req",   "talentStrReq",  "num"),
    ("levelneeded",      "levelNeeded",   "num"),
    ("Chi_Cost",         "chiCost",       "num"),
    ("cooldownTicks",    "cooldownTicks", "num"),
    ("BaseDMG",          "baseDmg",       "num"),
    ("ExtraDMG",         "extraDmg",      "num"),
]

# Optional locals. Add any of these to a procedure and the script carries it
# into the hold block; leave one out and whatever is in the json stays.
HOLD_NUMS = [
    ("holdLevels",          ("levels",)),
    ("holdSecondsPerLevel", ("secondsPerLevel",)),
    ("holdChiPerTick",      ("chiPerTick",)),
    ("holdChiPerSecond",    ("chiPerSecond",)),
    ("holdChiControlMax",   ("chiControlMax",)),
    ("holdTickEvery",       ("tickEvery",)),
    ("holdMaxSeconds",      ("maxSeconds",)),
    ("holdMinLevel",        ("minLevel",)),
    ("holdGraceTicks",      ("graceTicks",)),
    ("holdPowerMin",        ("power", "min")),
    ("holdPowerMax",        ("power", "max")),
]

MODES = {1: "charge", 2: "channel", 3: "toggle"}

# What a brand new hold block looks like, per mode. Only used when the json
# has none - an existing block keeps every number you tuned by hand.
DEFAULTS = {
    "charge": collections.OrderedDict([
        ("mode", "charge"), ("levels", 5), ("secondsPerLevel", 0.5),
        ("chiPerTick", 0.5), ("chiControlMax", 0.7),
        ("power", collections.OrderedDict([("min", 1.0), ("max", 2.2)])),
    ]),
    "channel": collections.OrderedDict([
        ("mode", "channel"), ("maxSeconds", 3.0), ("chiPerSecond", 5), ("tickEvery", 4),
    ]),
    "toggle": collections.OrderedDict([
        ("mode", "toggle"), ("chiPerTick", 0.3), ("tickEvery", 20), ("maxSeconds", 30.0),
    ]),
}

REQ_STATS = [
    ("reqStat_Str",        "strength"),
    ("reqStat_Dura",      "durability"),
    ("reqStat_Agil",      "agility"),
    ("reqStat_perception", "perception"),
    ("reqStat_Chi",       "chi"),
    ("reqStat_ChiControl", "chiControl"),
]

TYPES = r"(?:double|int|float|boolean|String)"


def assignments(src, name):
    """
    Every assignment to this local that is NOT its declaration.

    The declaration is always "double BaseDMG = 0;" - reading that would
    overwrite the JSON with a zero the moment a field is declared and never
    set. Only real assignments count, and the last one wins, because that is
    the one the procedure actually runs with.
    """
    out = []
    for m in re.finditer(r"(?:^|[;{}\n])\s*(" + TYPES + r"\s+)?" + re.escape(name) + r"\s*=\s*([^;]+);", src):
        if m.group(1):
            continue                      # declaration, not a value you typed
        out.append(m.group(2).strip())
    return out


def first_number(expr):
    """
    The number you typed, ignoring the stat curve wrapped around it.

        70 * (1 - Math.min(0.35, ... * 0.0035))   ->  70
        12                                        ->  12
    """
    m = re.match(r"^\(*\s*(-?\d+(?:\.\d+)?)", expr)
    return float(m.group(1)) if m else None


def read_string(src, name):
    vals = assignments(src, name)
    for v in reversed(vals):
        m = re.match(r'^"(.*)"$', v)
        if m:
            return m.group(1)
    return None


def read_bool(src, name):
    vals = assignments(src, name)
    if not vals:
        return None
    v = vals[-1]
    return True if v == "true" else False if v == "false" else None


def ability_name(src, path):
    """The name it sets on abilityName, or one derived from the file name."""
    m = re.search(r'\.abilityName\s*=\s*"([^"]+)"', src)
    if m:
        return m.group(1)
    stem = re.sub(r"\d*Procedure$", "", os.path.basename(path)[:-5])
    return re.sub(r"(?<!^)(?=[A-Z])", " ", stem)


def scrape(path):
    src = open(path, encoding="utf-8", errors="replace").read()
    if "talent_id" not in src:
        return None

    talent = read_string(src, "talent_id")
    if not talent:
        return None

    out = {
        "_file": os.path.basename(path),
        "_talent": talent,
        "_name": ability_name(src, path),
        "_hold": read_bool(src, "hold"),
        "_legacyHold": read_bool(src, "chargeable"),
        "_fireOnPress": read_bool(src, "fireonpress"),
        "_mode": None,
        "holdNums": collections.OrderedDict(),
        "values": collections.OrderedDict(),
        "reqStats": collections.OrderedDict(),
    }

    for java, key, _kind in FIELDS:
        vals = assignments(src, java)
        if not vals:
            continue
        n = first_number(vals[-1])
        if n is None:
            continue
        out["values"][key] = int(n) if n == int(n) else n

    mvals = assignments(src, "mode")
    if mvals:
        n = first_number(mvals[-1])
        if n is not None:
            out["_mode"] = MODES.get(int(n))

    for java, path in HOLD_NUMS:
        vals = assignments(src, java)
        if not vals:
            continue
        n = first_number(vals[-1])
        if n is not None:
            out["holdNums"][path] = int(n) if n == int(n) else n

    for java, key in REQ_STATS:
        vals = assignments(src, java)
        if not vals:
            continue
        n = first_number(vals[-1])
        if n:                              # only carry a requirement that exists
            out["reqStats"][key] = int(n) if n == int(n) else n

    return out


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=collections.OrderedDict)


def find_ability(doc, name):
    slug = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
    for a in doc.get("abilities", []):
        if a.get("name", "").lower() == name.lower() or a.get("id", "") == slug:
            return a
    return None


def sync_hold(scraped, ab, notes):
    """
    The hold block, from the procedure's own switches:

        hold        true/false  - is this a held ability at all
        mode        1/2/3       - charge / channel / toggle
        fireonpress true/false  - charge only

    Turning hold off removes the block, and the removed block is printed so a
    tuning you spent time on is never lost without you seeing it. Turning it on
    writes a default block for that mode; every later run leaves your numbers
    alone and only keeps mode and fireOnPress in step.
    """
    want = scraped["_hold"]
    if want is None:
        want = scraped["_legacyHold"]
    if want is None:
        return False                       # procedure says nothing - leave it

    cur = ab.get("hold") if isinstance(ab.get("hold"), dict) else None

    if not want:
        if cur is None:
            return False
        ab.pop("hold", None)
        notes.append("    hold removed (hold = false). was: " + json.dumps(cur))
        return True

    mode = scraped["_mode"] or (cur or {}).get("mode") or "charge"
    changed = False

    if cur is None:
        cur = collections.OrderedDict(DEFAULTS[mode])
        ab["hold"] = cur
        notes.append(f"    hold added ({mode})")
        changed = True
    elif cur.get("mode") != mode:
        notes.append(f"    hold.mode: {cur.get('mode')} -> {mode}")
        cur["mode"] = mode
        changed = True

    fop = scraped["_fireOnPress"]
    if fop is not None and mode == "charge" and cur.get("fireOnPress", False) != fop:
        notes.append(f"    hold.fireOnPress: {cur.get('fireOnPress', False)} -> {fop}")
        cur["fireOnPress"] = fop
        changed = True

    for path, val in scraped["holdNums"].items():
        if len(path) == 1:
            if cur.get(path[0]) != val:
                notes.append(f"    hold.{path[0]}: {cur.get(path[0])} -> {val}")
                cur[path[0]] = val
                changed = True
        else:
            sub = cur.get(path[0])
            if not isinstance(sub, dict):
                sub = collections.OrderedDict()
                cur[path[0]] = sub
            if sub.get(path[1]) != val:
                notes.append(f"    hold.{path[0]}.{path[1]}: {sub.get(path[1])} -> {val}")
                sub[path[1]] = val
                changed = True

    return changed


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def initials(name):
    parts = [w for w in re.split(r"[^A-Za-z0-9]+", name) if w]
    if not parts:
        return "??"
    if len(parts) == 1:
        return parts[0][:2].upper()
    return (parts[0][0] + parts[1][0]).upper()


def new_talent(talent_id):
    """A talent file that did not exist yet. Placeholders you fill in."""
    return collections.OrderedDict([
        ("id", talent_id),
        ("name", talent_id.replace("_", " ").upper()),
        ("short", talent_id.split("_")[0].upper()),
        ("accent", "7ED8F5"),
        ("types", ["UNSET"]),
        ("abilities", []),
    ])


def new_ability(name):
    """
    A stub for an ability the JSON has never seen. Everything the procedure
    knows gets filled in straight after; what is left is the writing - icon,
    kind and description - which no procedure can tell us.
    """
    return collections.OrderedDict([
        ("id", slug(name)),
        ("icon", initials(name)),
        ("name", name),
        ("kind", "UNSET"),
        ("desc", "TODO - write this."),
    ])


def sync_one(scraped, talents_dir, dry):
    path = os.path.join(talents_dir, scraped["_talent"] + ".json")
    notes = []
    changed = False

    if os.path.isfile(path):
        doc = load(path)
    else:
        doc = new_talent(scraped["_talent"])
        changed = True
        notes.append(f"  + created {scraped['_talent']}.json - set its name, short, accent and types")

    doc.setdefault("abilities", [])
    ab = find_ability(doc, scraped["_name"])
    if ab is None:
        ab = new_ability(scraped["_name"])
        doc["abilities"].append(ab)
        changed = True
        notes.append(f"  + added \"{scraped['_name']}\" to {scraped['_talent']}.json"
                     f" (id {ab['id']}, icon {ab['icon']}) - kind and desc still TODO")

    for key, val in scraped["values"].items():
        old = ab.get(key)
        if old != val:
            ab[key] = val
            changed = True
            notes.append(f"    {key}: {old} -> {val}")

    if scraped["reqStats"]:
        cur = ab.get("reqStats") or collections.OrderedDict()
        for key, val in scraped["reqStats"].items():
            if cur.get(key) != val:
                cur[key] = val
                changed = True
                notes.append(f"    reqStats.{key}: {ab.get('reqStats', {}).get(key)} -> {val}")
        if changed:
            ab["reqStats"] = cur

    changed |= sync_hold(scraped, ab, notes)

    if changed and not dry:
        os.makedirs(talents_dir, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=2)
            f.write("\n")

    if notes:
        head = f"  {scraped['_name']}  ({scraped['_file']} -> {scraped['_talent']}.json)"
        return [head] + notes
    return []


def run(root, dry):
    proc = os.path.join(root, "src", "main", "java", "net", "mcreator", "ordeal", "procedures")
    talents = os.path.join(root, "src", "main", "resources", "assets", "ordeal", "talents")
    if not os.path.isdir(proc):
        print(f"no procedures folder at {proc}")
        return 1
    if not os.path.isdir(talents):
        print(f"no talents folder at {talents}")
        return 1

    lines = []
    for f in sorted(os.listdir(proc)):
        if not f.endswith("Procedure.java"):
            continue
        s = scrape(os.path.join(proc, f))
        if s and (s["values"] or s["_hold"] is not None):
            lines += sync_one(s, talents, dry)

    if lines:
        print(("would change" if dry else "synced") + ":")
        print("\n".join(lines))
    return 0


def stamp(root):
    """Newest mtime under the procedures folder, so a save is noticed."""
    proc = os.path.join(root, "src", "main", "java", "net", "mcreator", "ordeal", "procedures")
    newest = 0
    for f in os.listdir(proc):
        if f.endswith(".java"):
            newest = max(newest, os.path.getmtime(os.path.join(proc, f)))
    return newest


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=here, help="mod folder (the one with src/ in it)")
    ap.add_argument("--watch", action="store_true", help="stay running and sync on every save")
    ap.add_argument("--dry", action="store_true", help="print changes, write nothing")
    ap.add_argument("--every", type=float, default=2.0, help="seconds between checks in watch mode")
    a = ap.parse_args()

    root = os.path.abspath(a.root)
    if not os.path.isdir(os.path.join(root, "src")):
        print(f"{root} does not look like the mod folder - pass --root")
        return 1

    if not a.watch:
        return run(root, a.dry)

    print(f"watching {root}\nctrl-c to stop\n")
    last = 0
    while True:
        try:
            now = stamp(root)
            if now > last:
                last = now
                if run(root, a.dry) == 0:
                    print(time.strftime("  [%H:%M:%S] up to date"))
            time.sleep(a.every)
        except KeyboardInterrupt:
            print("\nstopped")
            return 0
        except Exception as e:
            print(f"  ! {e}")
            time.sleep(a.every)


if __name__ == "__main__":
    sys.exit(main())

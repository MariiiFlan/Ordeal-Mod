#!/usr/bin/env python3
"""
sync_talents.py - keep the talent JSONs in step with the procedures.

MCreator regenerates the procedure .java files on every build, so the numbers
you type into blocks are the real ones and the JSON is only a copy. This reads
the generated Java back out and writes those numbers into the JSON, so the
terminal tooltip can never disagree with what the ability actually does.

It reports what it CANNOT read, too. A local that is declared but never
assigned used to be skipped silently, which looks exactly like "already in
sync" - the JSON kept a stale number and the watcher printed "up to date"
anyway. Those now print as PROBLEMS and the watcher refuses to call it synced.

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
    ("pays",             "pays",          "num"),
]

# Optional locals. Add any of these to a procedure and the script carries it
# into the hold block; leave one out and whatever is in the json stays.
HOLD_NUMS = [
    # Bare aliases first, so an explicit hold-prefixed local always wins if you
    # somehow set both. These exist because every procedure already declares a
    # plain chiPerTick and it read as doing nothing.
    ("chiPerTick",          ("chiPerTick",)),
    ("holdLevels",          ("levels",)),
    ("holdSecondsPerLevel", ("secondsPerLevel",)),
    ("holdChiPerTick",      ("chiPerTick",)),
    ("holdChiControlMax",   ("chiControlMax",)),
    ("holdTickEvery",       ("tickEvery",)),
    ("holdMaxSeconds",      ("maxSeconds",)),
    ("holdMinLevel",        ("minLevel",)),
    ("holdGraceTicks",      ("graceTicks",)),
    ("movementStunTicks",   ("stunTicks",)),
    ("holdPowerMin",        ("power", "min")),
    ("holdPowerMax",        ("power", "max")),
]

# Text locals that ride into the hold block.
#   ThirdPersonAni - a Player Animation API name, broadcast to everyone
#   FirstPersonAni - a COMMAND run as you, e.g. "ordealanimations play @s x"
# Logic locals that ride into the hold block.
HOLD_BOOLS = [
    ("movementStunWhileHold", "stunWhileHold"),
]

HOLD_STRINGS = [
    ("ThirdPersonAni", "anim3p"),
    ("FirstPersonAni", "anim1p"),
]

MODES = {1: "charge", 2: "channel", 3: "toggle", 4: "ramp"}

# A talent_id on the left is written into the json on the right. Use this when a
# group of abilities belongs to no single talent - "enhancement" is not a talent
# anyone holds, so its abilities live in basic.json where every player sees them.
TALENT_ALIASES = {
    "enhancement":  "basic",
    "enhancements": "basic",
}

# The projectile block, from the procedure's own locals. Written only while the
# Projectile switch is on; turning it off removes the block.
#   java name          json key
PROJ_NUMS = [
    ("ProjectileSpeed", "speed"),
    ("Gravity",         "gravity"),
    ("LifeTicks",       "lifeTicks"),
    ("Pierce",          "pierce"),
    ("ExplodeRadius",   "radius"),
    ("Homing",          "homing"),
    ("HomingRange",     "homingRange"),
    ("IgniteSeconds",   "igniteSeconds"),
]

PROJ_BOOLS = [
    ("ExplodeOnImpact", "explodeOnImpact"),
]

PROJ_STRINGS = [
    ("ExploVFX",  "explodeFx"),
    ("TrailVFX",  "trailFx"),
    ("ImpactVFX", "impactFx"),
    ("HitSound",  "hitSound"),
]

# What a brand new hold block looks like, per mode. Only used when the json
# has none - an existing block keeps every number you tuned by hand.
DEFAULTS = {
    "charge": collections.OrderedDict([
        ("mode", "charge"), ("levels", 5), ("secondsPerLevel", 0.5),
        ("chiPerTick", 0.5), ("chiControlMax", 0.7),
        ("power", collections.OrderedDict([("min", 1.0), ("max", 2.2)])),
    ]),
    "channel": collections.OrderedDict([
        ("mode", "channel"), ("maxSeconds", 3.0), ("chiPerTick", 0.25), ("tickEvery", 4),
    ]),
    "toggle": collections.OrderedDict([
        ("mode", "toggle"), ("chiPerTick", 0.3), ("tickEvery", 20), ("maxSeconds", 30.0),
    ]),
    # a channel that climbs: pulse timing from the channel, power curve from
    # the charge. maxSeconds is the whole hold, levels * secondsPerLevel is how
    # long it takes to reach full power inside it.
    "ramp": collections.OrderedDict([
        ("mode", "ramp"), ("maxSeconds", 3.0), ("chiPerTick", 0.25), ("tickEvery", 4),
        ("levels", 5), ("secondsPerLevel", 0.5), ("chiControlMax", 0.7),
        ("power", collections.OrderedDict([("min", 1.0), ("max", 2.2)])),
    ]),
}

REQ_STATS = [
    ("reqStat_Str",        "strength"),
    ("reqStat_Dura",      "durability"),
    ("reqStat_Agil",      "agility"),
    ("reqStat_Health",    "health"),
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


# Locals that are FILLED IN from the player, never typed. A literal assignment
# to one of these is the wrong-block mistake: you meant the requirement next to
# it, the real value gets clobbered, and the gate it feeds stops meaning
# anything. This is the single most expensive silent bug in the setup.
READONLY_LOCALS = {
    "talent_Str": "Talent_STR_Req",
}


def defines_ability(src):
    """
    Is this the procedure that DEFINES an ability, or a helper built from the
    same template?

    MCreator's template declares the whole block of locals in every procedure
    made from it, so Akonito1/2/3 and the OnTick handlers all declare Chi_Cost
    and BaseDMG without ever meaning to own them. Warning about those buries
    the one finding that matters.

    Setting abilityName is the marker, and it splits cleanly: every "0"
    procedure sets it, no helper does.
    """
    return re.search(r'\.abilityName\s*=\s*"', src) is not None


# levelNeeded is opt-in: almost nothing gates on player level, so "declared and
# never set" is the normal case for it, not a fault.
OPTIONAL_FIELDS = {"levelneeded"}


def checks(src, path):
    """
    Only things that are actually wrong. Silence has to mean correct, but so
    does a warning have to mean broken - a checker that cries about every
    procedure is the same as one that says nothing.
    """
    out = []
    name = os.path.basename(path)

    for local, meant in READONLY_LOCALS.items():
        for v in assignments(src, local):
            if first_number(v) is not None and not re.search(r"[A-Za-z_]", v):
                out.append(f"    {local} = {v}  <- {local} holds YOUR ACTUAL talent "
                           f"strength, read from the player. Setting it to a literal "
                           f"overwrites that, so the '{local} >= {meant}' gate always "
                           f"passes. You meant {meant} = {v}.")

    if defines_ability(src):
        for java, key, _kind in FIELDS:
            if java in OPTIONAL_FIELDS:
                continue
            declared = re.search(r"(?:^|\n)\s*" + TYPES + r"\s+" + re.escape(java) + r"\s*=", src)
            if declared and not assignments(src, java):
                out.append(f"    {java} never set - {key} in the json keeps its old value")

    return [f"  {name}"] + out if out else []


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
        "_projectile": read_bool(src, "Projectile"),
        "holdNums": collections.OrderedDict(),
        "holdStrings": collections.OrderedDict(),
        "holdBools": collections.OrderedDict(),
        "projNums": collections.OrderedDict(),
        "projBools": collections.OrderedDict(),
        "projStrings": collections.OrderedDict(),
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
        if n is None:
            continue
        # The bare chiPerTick only counts when it is above zero. Every procedure
        # declares it and resets it to 0 in its defaults block, so honouring that
        # zero would quietly wipe the drain off every charge in the mod. Write
        # holdChiPerTick explicitly if you mean free.
        if java == "chiPerTick" and n == 0:
            continue
        out["holdNums"][path] = int(n) if n == int(n) else n

    for java, key in HOLD_BOOLS:
        b = read_bool(src, java)
        if b is not None:
            out["holdBools"][key] = b

    for java, key in HOLD_STRINGS:
        v = read_string(src, java)
        if v is not None:
            out["holdStrings"][key] = v

    for java, key in PROJ_NUMS:
        vals = assignments(src, java)
        if not vals:
            continue
        n = first_number(vals[-1])
        if n is None:
            continue
        # A projectile with speed 0 never leaves your hand, so that is a local
        # nobody set rather than a value anybody meant. Everything else can
        # legitimately be zero - no homing, no ignite, no blast radius.
        if key == "speed" and n <= 0:
            continue
        out["projNums"][key] = int(n) if n == int(n) else n

    for java, key in PROJ_BOOLS:
        b = read_bool(src, java)
        if b is not None:
            out["projBools"][key] = b

    for java, key in PROJ_STRINGS:
        v = read_string(src, java)
        if v is not None:
            out["projStrings"][key] = v

    for java, key in REQ_STATS:
        vals = assignments(src, java)
        if not vals:
            continue
        n = first_number(vals[-1])
        if n is None:
            continue
        # zeros are carried too - a requirement you set back to 0 has to be able
        # to leave the json, and it used to stay there for ever
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
        mode        1/2/3/4     - charge / channel / toggle / ramp
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

    for key, val in scraped["holdBools"].items():
        if cur.get(key, False) != val:
            notes.append(f"    hold.{key}: {cur.get(key, False)} -> {val}")
            if val:
                cur[key] = val
            else:
                cur.pop(key, None)
            changed = True

    for key, val in scraped["holdStrings"].items():
        if cur.get(key, "") != val:
            notes.append(f"    hold.{key}: {cur.get(key, '')!r} -> {val!r}")
            if val:
                cur[key] = val
            else:
                cur.pop(key, None)
            changed = True

    # chiPerTick is the only drain unit there is. Any chiPerSecond left over
    # from an older json is removed on sight so a block can never carry two.
    if "chiPerSecond" in cur:
        notes.append(f"    hold.chiPerSecond removed ({cur['chiPerSecond']}) - chiPerTick is the only unit")
        cur.pop("chiPerSecond", None)
        changed = True

    return changed


def sync_projectile(scraped, ab, notes):
    """
    The projectile block, gated on the procedure's Projectile switch.

        Projectile  true/false  - does this ability fire one at all

    True writes the block and keeps every projectile local in step with it.
    False removes the block, and prints what was removed so a tuning you spent
    time on is never lost silently. A local the procedure does not set is left
    alone, so anything you added to the json by hand survives.
    """
    want = scraped["_projectile"]
    if want is None:
        return False                       # procedure says nothing - leave it

    cur = ab.get("projectile") if isinstance(ab.get("projectile"), dict) else None

    if not want:
        if cur is None:
            return False
        ab.pop("projectile", None)
        notes.append("    projectile removed (Projectile = false). was: " + json.dumps(cur))
        return True

    changed = False
    if cur is None:
        cur = collections.OrderedDict()
        ab["projectile"] = cur
        notes.append("    projectile added")
        changed = True

    for src in (scraped["projNums"], scraped["projBools"], scraped["projStrings"]):
        for key, val in src.items():
            if cur.get(key) != val:
                notes.append(f"    projectile.{key}: {cur.get(key)} -> {val}")
                cur[key] = val
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
    talent_id = TALENT_ALIASES.get(scraped["_talent"].lower(), scraped["_talent"])
    scraped = dict(scraped)
    scraped["_talent"] = talent_id
    path = os.path.join(talents_dir, talent_id + ".json")
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
            if val:
                if cur.get(key) != val:
                    notes.append(f"    reqStats.{key}: {cur.get(key)} -> {val}")
                    cur[key] = val
                    changed = True
            elif key in cur:
                notes.append(f"    reqStats.{key} removed (set back to 0)")
                cur.pop(key, None)
                changed = True
        if cur:
            ab["reqStats"] = cur
        elif "reqStats" in ab:
            ab.pop("reqStats", None)
            changed = True

    stun = scraped["holdNums"].get(("stunTicks",))
    want_hold = scraped["_hold"] if scraped["_hold"] is not None else scraped["_legacyHold"]
    if stun is not None and not want_hold:
        scraped["holdNums"].pop(("stunTicks",), None)
        if stun:
            if ab.get("stunTicks") != stun:
                notes.append(f"    stunTicks: {ab.get('stunTicks')} -> {stun}")
                ab["stunTicks"] = stun
                changed = True
        elif "stunTicks" in ab:
            notes.append("    stunTicks removed (set back to 0)")
            ab.pop("stunTicks", None)
            changed = True

    changed |= sync_hold(scraped, ab, notes)
    changed |= sync_projectile(scraped, ab, notes)

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
    problems = []
    for f in sorted(os.listdir(proc)):
        if not f.endswith("Procedure.java"):
            continue
        full = os.path.join(proc, f)
        src = open(full, encoding="utf-8", errors="replace").read()
        if "talent_id" in src:
            problems += checks(src, full)
        s = scrape(full)
        if s and (s["values"] or s["_hold"] is not None):
            lines += sync_one(s, talents, dry)

    if lines:
        print(("would change" if dry else "synced") + ":")
        print("\n".join(lines))

    # Warnings print whether or not anything synced. A procedure the script
    # cannot read is exactly the case that used to look identical to success.
    if problems:
        print("PROBLEMS - these are why a number you typed is not in the json:")
        print("\n".join(problems))

    return 2 if problems else 0


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
                code = run(root, a.dry)
                if code == 0:
                    print(time.strftime("  [%H:%M:%S] up to date"))
                else:
                    # never print "up to date" over an unreadable procedure
                    print(time.strftime("  [%H:%M:%S] NOT synced - see above"))
            time.sleep(a.every)
        except KeyboardInterrupt:
            print("\nstopped")
            return 0
        except Exception as e:
            print(f"  ! {e}")
            time.sleep(a.every)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Check that biome tinting is wired up on both sides for every plant.

A block is only tinted when TWO unrelated things agree: its baked model carries
`"tintindex": 0` (inherited from minecraft:block/tinted_cross or tinted_flower_pot_cross),
and HempdustryClient registers a block colour provider for it (client.blockColor up to 1.21.11,
client.blockTint since 26.1, when vanilla's BlockColor became BlockTintSource). Break either
half and nothing errors -- the plant simply renders as drawn, or the provider is never
asked, and the only symptom is a crop that stubbornly refuses to match its biome. That is
exactly the class of failure nobody notices in a screenshot.

Usage:
    python3 scripts/biome_tint_pairing.py

Exits non-zero and names the offenders when the two halves disagree.
"""
import json
import pathlib
import re
import sys

import layout

LAYOUT = layout.of()
CLIENT = LAYOUT.client
ASSETS = LAYOUT.assets

# The vanilla parents that put "tintindex": 0 on a plant's faces.
TINTED_PARENTS = {"minecraft:block/tinted_cross", "minecraft:block/tinted_flower_pot_cross"}

# Blocks, not plants, whose tinted faces come and go with their state on purpose: an id and why.
# An entry that stops being partly tinted is stale and fails, so the list cannot hide a plant.
PARTLY_TINTED_ON_PURPOSE = {
    "stash_jar": "an empty Stash Jar shows no stash pieces, its only tinted faces",
    "gilded_stash_jar": "an empty Stash Jar shows no stash pieces, its only tinted faces",
    "diamond_stash_jar": "an empty Stash Jar shows no stash pieces, its only tinted faces",
}


def registered_blocks():
    """Block ids passed to every client.blockColor(...) or client.blockTint(...) call in HempdustryClient.

    Every call, not the first: the crops have their own provider (it reads the light record) beside
    the one the wild flowers share, and a checker that stopped at the first call reported both
    crops as untinted. Only the block list after the provider's body counts: the crops' provider names
    ``ModBlocks.BELDIA_CROP`` inside it, which would otherwise pass for a registration. A collection
    passed whole (``ModBlocks.DEVICE_BLOCKS.values()``, the placed bongs' load tint) is not a plant
    and not a biome tint, so a name followed by ``.`` is skipped.
    """
    source = CLIENT.read_text(encoding="utf-8")
    calls = [m.start() for m in re.finditer(r"client\.block(?:Color|Tint)\(", source)]
    if not calls:
        sys.exit("no client.blockColor(...) or client.blockTint(...) call in HempdustryClient")
    names = set()
    for start in calls:
        args = source[start:closing(source, source.index("(", start))]
        args = args[args.rfind("}") + 1:]
        args += helper_bodies(source, args)
        names |= {name.lower() for name in re.findall(r"ModBlocks\.([A-Z0-9_]+)\b(?!\.)", args)}
        names |= crop_list_blocks(args)
    return names


def closing(source, open_at):
    """Index of the bracket that closes the one at ``open_at`` (any of ``(`` or ``{``)."""
    pair = {"(": ")", "{": "}"}[source[open_at]]
    depth = 0
    for i in range(open_at, len(source)):
        depth += (source[i] == source[open_at]) - (source[i] == pair)
        if depth == 0:
            return i
    sys.exit(f"unbalanced {source[open_at]} in {CLIENT.name}")


def helper_bodies(source, args):
    """The bodies of HempdustryClient's own no-argument methods a block list calls (``biomeTinted()``).

    A block list built in a helper is read where it is built; the call alone names no block.
    """
    bodies = ""
    for name in re.findall(r"(?<![\w.])([a-z]\w*)\(\)", args):
        method = re.search(r"static\s+[\w<>\[\]]+\s+" + name + r"\(\)\s*\{", source)
        if method:
            bodies += " " + source[method.end() - 1:closing(source, method.end() - 1)]
    return bodies


# Where the crop list lives since the crops stopped being named one by one: one entry per crop, its
# blocks and its Tint. Read here, so a block list drawn from it is checked as one that names them.
CROP_LIST = CLIENT.parent / "block" / "ModCrops.java"
CROP_LIST_TARGETS = {"crop": r"Crop::crop", "flower": r"\.flower\(\)", "potted": r"\.potted\(\)"}


def crop_entries():
    """Every ModCrops.ALL entry as {"crop", "flower", "potted": block id or None, "tint": Tint name}."""
    source = CROP_LIST.read_text(encoding="utf-8")
    entries = []
    for chunk in source.split("new Crop(")[1:]:
        blocks = re.findall(r"ModBlocks\.([A-Z0-9_]+)", chunk.split("Home.")[0])
        tint = re.search(r"Tint\.([A-Z_]+)", chunk)
        if not blocks or not tint:
            sys.exit(f"cannot read a crop entry of {CROP_LIST.name}: {chunk[:80]!r}")
        wild = re.search(r"wild\(ModBlocks\.(\w+), ModBlocks\.(\w+)", chunk)
        bare = re.search(r"new Wild\(ModBlocks\.(\w+), Optional\.empty\(\)", chunk)
        entries.append({
            "crop": blocks[0].lower(),
            "flower": (wild or bare).group(1).lower() if (wild or bare) else None,
            "potted": wild.group(2).lower() if wild else None,
            "tint": tint.group(1),
        })
    if not entries:
        sys.exit(f"no crop entry read in {CROP_LIST.name}")
    return entries


def crop_list_blocks(args):
    """The blocks a block list takes from the crop list: each ``ModCrops.ALL.stream()`` pipeline, read
    for the Tints it keeps and the blocks it maps to (the crop, its wild plant, its potted one).

    A pipeline this cannot read stops the check: a list it guessed at would check nothing.
    """
    pipelines = args.split("ModCrops.ALL.stream()")[1:]
    if not pipelines:
        return set()
    entries = crop_entries()
    names = set()
    for pipeline in pipelines:
        tints = set(re.findall(r"ModCrops\.Tint\.([A-Z_]+)", pipeline))
        targets = [target for target, pattern in CROP_LIST_TARGETS.items() if re.search(pattern, pipeline)]
        if not tints or not targets:
            sys.exit(f"cannot read which crops a block list of {CLIENT.name} takes: "
                     f"ModCrops.ALL.stream(){pipeline[:120]!r}")
        for entry in entries:
            if entry["tint"] in tints:
                names |= {entry[target] for target in targets if entry[target]}
    return names


def models_by_block():
    """block id -> every hempdustry model its blockstate can show, overlays aside."""
    out = {}
    for assets in ASSETS:
        for state in sorted((assets / "blockstates").glob("*.json")):
            refs = set(re.findall(r"hempdustry:block/([a-z0-9_]+)", state.read_text(encoding="utf-8")))
            out.setdefault(state.stem, set()).update(ref for ref in refs if not is_overlay(ref))
    return out


# A layer drawn over a plant rather than the plant itself: mildew's powder and the sulfur's dust
# are their own colours, and must not take the grass's.
OVERLAY_PARENT = "hempdustry:block/crop_overlay"


def is_overlay(name):
    model = find_model(name)
    return model is not None and model.get("parent") == OVERLAY_PARENT


def find_model(name):
    for assets in ASSETS:
        path = assets / "models" / "block" / f"{name}.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
    return None


def is_tinted(name, seen=()):
    """Walk the parent chain until it leaves our namespace, then judge the vanilla parent."""
    if name in seen:
        return False  # a cycle is broken art, but that is not this script's job to report
    model = find_model(name)
    if model is None:
        return False
    # A model of ours that draws its own elements answers for itself. hempdustry:block/tinted_crop
    # has no parent at all -- it is vanilla's tinted_cross moved down a pixel -- and was reported
    # untinted until 2026-09-16 because this only ever judged a vanilla parent.
    if any("tintindex" in face
           for element in model.get("elements", [])
           for face in element.get("faces", {}).values()):
        return True
    parent = model.get("parent", "")
    if parent.startswith("hempdustry:block/"):
        return is_tinted(parent.split("/", 1)[1], (*seen, name))
    return parent in TINTED_PARENTS


def main():
    registered = registered_blocks()
    by_block = models_by_block()
    tinted = {block for block, models in by_block.items()
              if models and all(is_tinted(model) for model in models)}
    partial = {block for block, models in by_block.items()
               if any(is_tinted(model) for model in models)} - tinted

    problems = []
    for block in sorted(set(PARTLY_TINTED_ON_PURPOSE) - partial):
        problems.append(f"{block}: listed as partly tinted on purpose, but it is not any more -- "
                        f"take it off PARTLY_TINTED_ON_PURPOSE")
    partial -= set(PARTLY_TINTED_ON_PURPOSE)
    for block in sorted(registered - tinted - partial - set(PARTLY_TINTED_ON_PURPOSE)):
        problems.append(f"{block}: has a colour provider, but no model of it is tinted "
                        f"(parent minecraft:block/tinted_cross?)")
    for block in sorted(tinted - registered):
        problems.append(f"{block}: models are tinted, but HempdustryClient registers no "
                        f"colour provider -- it will render pitch-tinted by BlockColors' -1 default")
    for block in sorted(partial):
        models = sorted(m for m in by_block[block] if not is_tinted(m))
        problems.append(f"{block}: only some models are tinted, so the plant changes colour "
                        f"as it grows. Untinted: {', '.join(models)}")

    if problems:
        print("biome tint pairing is broken:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(f"biome tint pairing OK: {len(registered)} block(s) tinted on both sides "
          f"({', '.join(sorted(registered))})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

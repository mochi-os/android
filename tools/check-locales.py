#!/usr/bin/env python3
# Copyright © 2026 Mochisoft OÜ
# SPDX-License-Identifier: AGPL-3.0-only
# This file is part of Mochi, licensed under the GNU AGPL v3 with the
# Mochi Application Interface Exception - see license.txt and license-exception.md.

"""Verify every Android string catalogue against its source, and fail if not.

THE single implementation of this rule. The gradle `checkLocaleCompleteness`
task and the CI step both shell out to this file; they used to each carry their
own copy in Kotlin and in YAML, the three drifted, and the two stale ones were
blind to plurals and to Android's fallback chain for years. If a check belongs
here, add it here and nowhere else.

It lives inside clients/android rather than in the umbrella's claude/scripts
because that directory is its own git repo: a standalone checkout has to be able
to fail its own build. claude/scripts/check-android-i18n.py is a thin wrapper
kept for the path CLAUDE.md documents.

Four checks, each catching a class the others cannot:
  key presence         a locale missing a key the source has
  argument survival    a translation that drops a %1$s the English carries, so
                       the value never reaches the reader
  placeholder syntax   a value holding {name} or ICU tag markup, which Android
                       does not substitute - and which no English source in this
                       tree contains, making it a reliable marker that the value
                       came from a different key
  fallback awareness    a region catalogue is judged against its parent, so
                       values-de-rCH is not reported for keys values-de supplies

Usage:
    check-locales.py --discover <dir> [--strict]
    check-locales.py <module-dir> [<module-dir> ...] [--strict]

--discover walks a directory for every Android module, so no caller has to keep
a module list in step with settings.gradle.kts.

Without --strict: prints problems and exits 0 (informational).
With --strict: exits non-zero if anything is wrong.
"""
import argparse
import re
import sys
from pathlib import Path


# English regional variants ship spelling-only diffs and fall through to
# values/, which is neutral English by design (see CLAUDE.md) - so a key absent
# from values-en-rUS resolves to the right text, not to the wrong language.
# Filling them would mean duplicating English under an English qualifier.
OVERLAY_RE = re.compile(r"^values-en(-|$)")

# Both element kinds are user-facing text. Matching only <string> meant every
# <plurals> was invisible to this gate in both directions: absent from the
# source set, so never required, and absent from each locale's set, so never
# reported. A count-bearing string is exactly the kind that needs per-language
# categories, so it is the last thing that should go unchecked.
KEY_RE = re.compile(r'<(?:string|plurals) name="([^"]+)"')


def load_keys(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return set(KEY_RE.findall(path.read_text(encoding="utf-8")))


# Region-qualified locales whose nearest localised catalogue is a script one
# rather than a bare language one. zh-Hant-HK's parent is zh-Hant in CLDR, and
# there is no values-zh in this tree, so a key absent from values-zh-rHK is
# served by values-b+zh+Hant - Traditional Chinese, not English. Kept as an
# explicit list because guessing a script from a region is not something to
# infer: HK and MO are Hant, CN and SG are Hans, and nothing else here needs it.
#
# Cantonese is deliberately NOT in this list. yue is a distinct language, not a
# Chinese variant, so Android matches it by language and finds no zh catalogue
# to fall through to; a key missing from values-yue is served by values/, in
# English. Its gaps are real.
SCRIPT_PARENT = {
    "values-zh-rHK": "values-b+zh+Hant",
    "values-zh-rMO": "values-b+zh+Hant",
    "values-zh-rCN": "values-b+zh+Hans",
    "values-zh-rSG": "values-b+zh+Hans",
}


def ancestors(qualifier: str, present: set[str]) -> list[str]:
    """Localised catalogues Android would consult before falling back to values/.

    Resource resolution walks language+region -> language -> default, per
    resource rather than per file, so a key held only by values-de is still the
    text a de-CH reader sees. values/ is deliberately NOT included: reaching it
    means the reader gets English, which is the gap this gate exists to find.
    """
    script = SCRIPT_PARENT.get(qualifier)
    if script and script in present:
        return [script]
    tag = qualifier[len("values-"):]
    if tag.startswith("b+"):
        language = tag[2:].split("+")[0].lower()
    elif "-r" in tag:
        language = tag.split("-r")[0]
    else:
        return []
    parent = f"values-{language}"
    return [parent] if parent in present and parent != qualifier else []


STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)

# Positional arguments the caller passes to getString. A translation that drops
# one renders the sentence with the value silently missing - "You resigned —
# wins" - and String.format does not complain about a surplus argument, so
# nothing surfaces it at build or run time.
ARGUMENT_RE = re.compile(r"%(?:\d+\$)?[ds]|%s")

# Web placeholder syntax. Android substitutes %1$s, never {name} or ICU tag
# markup, so a locale value carrying one shows the reader a literal
# "{opponentName}". It is also a reliable marker of a translation-memory
# mis-fill: no English source in this tree contains braces, so the value cannot
# have come from the key it is filed under.
PLACEHOLDER_RE = re.compile(r"\{[A-Za-z_][A-Za-z0-9_]*\}|\{\d+\}|&lt;\d+&gt;")


def load_strings(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    return dict(STRING_RE.findall(path.read_text(encoding="utf-8")))


def check_module(module_dir: Path) -> list[tuple[str, set[str]]]:
    """Return list of (locale-qualifier, missing-key-set) for incomplete catalogs."""
    res = module_dir / "src" / "main" / "res"
    source = load_keys(res / "values" / "strings.xml")
    if not source:
        raise SystemExit(f"{module_dir}: no readable values/strings.xml - "
                         "a wrong path here would otherwise report ok")
    present = {d.name for d in res.iterdir() if d.is_dir() and d.name.startswith("values")}
    problems = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not vd.name.startswith("values-"):
            continue
        if OVERLAY_RE.match(vd.name):
            continue
        xml = vd / "strings.xml"
        if not xml.exists():
            continue
        have = load_keys(xml)
        for parent in ancestors(vd.name, present):
            have |= load_keys(res / parent / "strings.xml")
        missing = source - have
        if missing:
            problems.append((vd.name, missing))
    return problems


def check_values(module_dir: Path) -> tuple[list[str], list[str]]:
    """Return (dropped-argument, leftover-placeholder) findings for a module.

    Key presence is what check_module answers; these two are about whether the
    value under a present key is usable. Both are mechanical and neither has a
    judgement call in it, which is why they belong in a gate rather than a
    review: an argument the English carries and the translation does not is a
    value the reader will never see, and brace syntax is not something Android
    substitutes at all.
    """
    res = module_dir / "src" / "main" / "res"
    english = load_strings(res / "values" / "strings.xml")
    if not english:
        return [], []
    arguments, placeholders = [], []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not vd.name.startswith("values-"):
            continue
        if OVERLAY_RE.match(vd.name):
            continue
        for key, value in load_strings(vd / "strings.xml").items():
            source = english.get(key)
            if source is None:
                continue
            for argument in sorted(set(ARGUMENT_RE.findall(source))):
                if argument not in value:
                    arguments.append(f"{vd.name} {key}: {argument} absent")
            if PLACEHOLDER_RE.search(value):
                placeholders.append(f"{vd.name} {key}: {value.strip()[:48]}")
    return arguments, placeholders


def check_overlays(module_dir: Path) -> list[str]:
    """Return findings for English regional overlays.

    These are the one thing OVERLAY_RE skips, and the skip is right in intent -
    a regional English catalogue should not have to restate the neutral English
    default. But it means an overlay holding a WRONG English copy is invisible,
    and a present key beats an inherited one, so English-locale users see the
    wrong text with nothing to catch it.

    An overlay cannot be judged by "differs from the default", because differing
    is the entire point: colour/color, cancelled/canceled, Postcode/ZIP code are
    all legitimate. So only two mechanical classes are flagged, neither of which
    a real regional spelling can trip:

      case    - identical ignoring case. A regional variant differs by more than
                capitalisation, so this is drift, and it silently overrode the
                project's sentence-case convention with Title Case.
      escape  - identical once doubled backslashes collapse. `\\"` renders as a
                literal backslash then a quote, so the reader sees Tag \\"foo\\".
    """
    res = module_dir / "src" / "main" / "res"
    english = load_strings(res / "values" / "strings.xml")
    if not english:
        return []
    findings = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not OVERLAY_RE.match(vd.name):
            continue
        for key, value in load_strings(vd / "strings.xml").items():
            source = english.get(key)
            if source is None or source == value:
                continue
            if value.replace('\\\\"', '\\"') == source:
                findings.append(f"{vd.name} {key}: escaping differs from values/")
            elif value.lower() == source.lower():
                findings.append(f"{vd.name} {key}: case differs from values/ "
                                f"({source.strip()[:32]!r} vs {value.strip()[:32]!r})")
    return findings


def discover(root: Path) -> list[Path]:
    """Every Android module under `root`, found by its source catalogue.

    Keeps callers out of the business of listing modules: gradle would otherwise
    duplicate settings.gradle.kts and CI would duplicate both, which is the same
    drift that let two of the three old copies of this check go stale.
    """
    # strings.xml -> values -> res -> main -> src -> the module itself
    found = {
        path.parents[4]
        for path in root.rglob("src/main/res/values/strings.xml")
    }
    return sorted(found)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("modules", nargs="*", help="Android module directories")
    ap.add_argument("--discover", metavar="DIR", help="find every Android module under DIR")
    ap.add_argument("--strict", action="store_true", help="exit non-zero if anything is wrong")
    args = ap.parse_args()

    modules = [Path(m) for m in args.modules]
    if args.discover:
        modules += discover(Path(args.discover))
    if not modules:
        ap.error("give at least one module directory, or --discover DIR")

    any_problems = False
    for module_str in modules:
        module = Path(module_str).resolve()
        problems = check_module(module)
        arguments, placeholders = check_values(module)
        overlays = check_overlays(module)
        if not problems and not arguments and not placeholders and not overlays:
            print(f"{module}: ok")
            continue
        any_problems = True
        if problems:
            print(f"{module}: {len(problems)} locale(s) incomplete", file=sys.stderr)
            for locale, missing in problems:
                sample = sorted(missing)[:3]
                print(f"  {locale}: {len(missing)} missing ({', '.join(sample)}{'…' if len(missing) > 3 else ''})", file=sys.stderr)
        if arguments:
            print(f"{module}: {len(arguments)} value(s) drop a format argument", file=sys.stderr)
            for finding in arguments[:10]:
                print(f"  {finding}", file=sys.stderr)
        if placeholders:
            print(f"{module}: {len(placeholders)} value(s) carry web placeholder syntax", file=sys.stderr)
            for finding in placeholders[:10]:
                print(f"  {finding}", file=sys.stderr)
        if overlays:
            print(f"{module}: {len(overlays)} English overlay value(s) drifted", file=sys.stderr)
            for finding in overlays[:10]:
                print(f"  {finding}", file=sys.stderr)

    if any_problems and args.strict:
        sys.exit(1)


if __name__ == "__main__":
    main()

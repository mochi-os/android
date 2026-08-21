#!/usr/bin/env python3
# Copyright © 2026 Mochisoft OÜ
# SPDX-License-Identifier: AGPL-3.0-only
# This file is part of Mochi, licensed under the GNU AGPL v3 with the
# Mochi Application Interface Exception - see license.txt and license-exception.md.

"""Verify every Android string catalogue against its source, and fail if not.

The single implementation: the gradle checkLocaleCompleteness task and the CI
step both shell out to this file. Add a new check here and nowhere else.

Checks: key presence; argument survival (a translation dropping a %1$s);
placeholder syntax ({name} or ICU markup, which Android never substitutes);
fallback awareness (a region catalogue judged against its parent); plural
completeness (a quantity the language needs, which Android silently serves from
`other`); locale coverage (an empty locale directory, and keys the source has
dropped). Every check reads every xml file in a values directory, because
Android merges them all.

Usage:
    check-locales.py --discover <dir> [--strict]
    check-locales.py <module-dir> [<module-dir> ...] [--strict]

--discover walks a directory for every Android module.
Without --strict: prints problems and exits 0. With --strict: exits non-zero.
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

# Both element kinds are user-facing text; matching only <string> leaves every
# <plurals> unchecked in both directions.
KEY_RE = re.compile(r'<(?:string|plurals) name="([^"]+)"')


def catalogues(directory: Path) -> list[Path]:
    """Every resource file in a values directory, not just strings.xml.

    Android merges every XML file under values*/, so a split catalogue would
    otherwise be half invisible to this gate.
    """
    if not directory.is_dir():
        return []
    return sorted(directory.glob("*.xml"))


def read_catalogues(directory: Path) -> str:
    return "\n".join(path.read_text(encoding="utf-8") for path in catalogues(directory))


def load_keys(directory: Path) -> set[str]:
    return set(KEY_RE.findall(read_catalogues(directory)))


# Region-qualified locales whose nearest localised catalogue is a script one
# rather than a bare language one: there is no values-zh, so values-zh-rHK falls
# through to values-b+zh+Hant. Cantonese is excluded on purpose - yue matches by
# language, finds no zh catalogue, and falls through to English, so its gaps are
# real.
SCRIPT_PARENT = {
    "values-zh-rHK": "values-b+zh+Hant",
    "values-zh-rMO": "values-b+zh+Hant",
    "values-zh-rCN": "values-b+zh+Hans",
    "values-zh-rSG": "values-b+zh+Hans",
}


def ancestors(qualifier: str, present: set[str]) -> list[str]:
    """Localised catalogues Android would consult before falling back to values/.

    Resolution walks language+region -> language -> default per resource, so a
    key held only by values-de is what a de-CH reader sees. values/ is excluded:
    reaching it means English, which is the gap this gate finds.
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

# Android substitutes %1$s, never {name} or ICU tag markup, so such a value
# renders literally - and, since no English source here has braces, it marks a
# translation-memory mis-fill.
PLACEHOLDER_RE = re.compile(r"\{[A-Za-z_][A-Za-z0-9_]*\}|\{\d+\}|&lt;\d+&gt;")


def load_strings(directory: Path) -> dict[str, str]:
    return dict(STRING_RE.findall(read_catalogues(directory)))


PLURALS_RE = re.compile(r'<plurals name="([^"]+)">(.*?)</plurals>', re.S)
QUANTITY_RE = re.compile(r'quantity="(\w+)"')

# Format arguments inside <plurals> are deliberately not checked: requiring what
# the source's same quantity carries misreports languages that express a count
# lexically. Android lint's ImpliedQuantity implements the rule that works.

# Quantity categories each shipped language needs, generated from CLDR rather
# than written by hand. The set is what an INTEGER count can select plus
# `other`, which Android always requires as the fallback - so `many` is absent
# for cs/sk/lt and the Romance languages (it selects on a fraction), and `other`
# is present for be/pl/ru/uk though no integer selects it. A language not listed
# is not checked.
#
# REGENERATE when a locale is added, with node (its ICU carries CLDR): for each
# shipped language tag L: r = new Intl.PluralRules(L) required = { r.select(n)
# for n in 0..1000 } + { "other" } Verify r.resolvedOptions().locale still
# matches L - an unsupported tag falls back to en-GB silently.
PLURAL_QUANTITY = {
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "cy": {"zero", "one", "two", "few", "many", "other"},

    "ga": {"one", "two", "few", "many", "other"},
    "mt": {"one", "two", "few", "many", "other"},

    "be": {"one", "few", "many", "other"},
    "pl": {"one", "few", "many", "other"},
    "ru": {"one", "few", "many", "other"},
    "uk": {"one", "few", "many", "other"},

    "gd": {"one", "two", "few", "other"},
    "sl": {"one", "two", "few", "other"},

    "bs": {"one", "few", "other"},
    "cs": {"one", "few", "other"},
    "hr": {"one", "few", "other"},
    "lt": {"one", "few", "other"},
    "ro": {"one", "few", "other"},
    "sk": {"one", "few", "other"},
    "sr": {"one", "few", "other"},

    "he": {"one", "two", "other"},

    "lv": {"zero", "one", "other"},

    "af": {"one", "other"},
    "am": {"one", "other"},
    "az": {"one", "other"},
    "bg": {"one", "other"},
    "bho": {"one", "other"},
    "bn": {"one", "other"},
    "ca": {"one", "other"},
    "ckb": {"one", "other"},
    "da": {"one", "other"},
    "de": {"one", "other"},
    "el": {"one", "other"},
    "es": {"one", "other"},
    "et": {"one", "other"},
    "eu": {"one", "other"},
    "fa": {"one", "other"},
    "fi": {"one", "other"},
    "fr": {"one", "other"},
    "gl": {"one", "other"},
    "gu": {"one", "other"},
    "ha": {"one", "other"},
    "hi": {"one", "other"},
    "hu": {"one", "other"},
    "hy": {"one", "other"},
    "is": {"one", "other"},
    "it": {"one", "other"},
    "ka": {"one", "other"},
    "kk": {"one", "other"},
    "kn": {"one", "other"},
    "ku": {"one", "other"},
    "ky": {"one", "other"},
    "mk": {"one", "other"},
    "ml": {"one", "other"},
    "mn": {"one", "other"},
    "mr": {"one", "other"},
    "nb": {"one", "other"},
    "ne": {"one", "other"},
    "nl": {"one", "other"},
    "nn": {"one", "other"},
    "om": {"one", "other"},
    "pa": {"one", "other"},
    "ps": {"one", "other"},
    "pt": {"one", "other"},
    "sd": {"one", "other"},
    "si": {"one", "other"},
    "sq": {"one", "other"},
    "sv": {"one", "other"},
    "sw": {"one", "other"},
    "ta": {"one", "other"},
    "te": {"one", "other"},
    "tk": {"one", "other"},
    "tl": {"one", "other"},
    "tr": {"one", "other"},
    "ur": {"one", "other"},
    "uz": {"one", "other"},
    "xh": {"one", "other"},
    "yi": {"one", "other"},
    "zu": {"one", "other"},

    "id": {"other"},
    "ja": {"other"},
    "jv": {"other"},
    "km": {"other"},
    "ko": {"other"},
    "lo": {"other"},
    "ms": {"other"},
    "my": {"other"},
    "su": {"other"},
    "th": {"other"},
    "vi": {"other"},
    "yo": {"other"},
    "yue": {"other"},
    "zh": {"other"},
}


def language_of(qualifier: str) -> str:
    """The language a values-* qualifier resolves to."""
    tag = qualifier[len("values-"):]
    if tag.startswith("b+"):
        return tag[2:].split("+")[0].lower()
    return tag.split("-r")[0].lower()


def check_plurals(module_dir: Path) -> list[str]:
    """Return findings for <plurals> blocks missing a quantity their language needs.

    Judged per file, unlike key presence: Android resolves a <plurals> as one
    resource, so a child block replaces the parent's outright. An absent block
    inherits correctly; a partial one is a real gap.
    """
    res = module_dir / "src" / "main" / "res"
    findings = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not vd.name.startswith("values-"):
            continue
        if OVERLAY_RE.match(vd.name):
            continue
        required = PLURAL_QUANTITY.get(language_of(vd.name))
        if not required:
            continue
        for name, body in PLURALS_RE.findall(read_catalogues(vd)):
            missing = required - set(QUANTITY_RE.findall(body))
            if missing:
                findings.append(f"{vd.name} {name}: no {', '.join(sorted(missing))}")
    return findings


def check_module(module_dir: Path) -> list[tuple[str, set[str]]]:
    """Return list of (locale-qualifier, missing-key-set) for incomplete catalogs."""
    res = module_dir / "src" / "main" / "res"
    source = load_keys(res / "values")
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
        have = load_keys(vd)
        for parent in ancestors(vd.name, present):
            have |= load_keys(res / parent)
        missing = source - have
        if missing:
            problems.append((vd.name, missing))
    return problems


def check_values(module_dir: Path) -> tuple[list[str], list[str]]:
    """Return (dropped-argument, leftover-placeholder) findings for a module.

    Whether the value under a present key is usable, as opposed to key presence.
    """
    res = module_dir / "src" / "main" / "res"
    english = load_strings(res / "values")
    if not english:
        return [], []
    arguments, placeholders = [], []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not vd.name.startswith("values-"):
            continue
        if OVERLAY_RE.match(vd.name):
            continue
        for key, value in load_strings(vd).items():
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
    """Return findings for English regional overlays, which OVERLAY_RE otherwise skips.

    An overlay cannot be judged by differing from the default - differing is the
    point. Only two classes a real regional spelling cannot trip are flagged:
    identical ignoring case, and identical once doubled backslashes collapse.
    """
    res = module_dir / "src" / "main" / "res"
    english = load_strings(res / "values")
    if not english:
        return []
    findings = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not OVERLAY_RE.match(vd.name):
            continue
        for key, value in load_strings(vd).items():
            source = english.get(key)
            if source is None or source == value:
                continue
            if value.replace('\\\\"', '\\"') == source:
                findings.append(f"{vd.name} {key}: escaping differs from values/")
            elif value.lower() == source.lower():
                findings.append(f"{vd.name} {key}: case differs from values/ "
                                f"({source.strip()[:32]!r} vs {value.strip()[:32]!r})")
    return findings


# values-* qualifiers that are NOT locales. Android's locale qualifier is a two
# or three letter language, so most configuration qualifiers (night, land, v29,
# hdpi) cannot be mistaken for one - but these three can, and a UI-mode
# directory holding no strings is not a translation gap.
NOT_LOCALE = {"tv", "car", "vr"}


def check_coverage(module_dir: Path) -> tuple[list[str], list[str]]:
    """Return (silent-locale, stale-key) findings.

    silent: a values-<locale> directory with no string resources at all, so the
    locale serves English throughout. stale: a key the source no longer has.
    """
    res = module_dir / "src" / "main" / "res"
    source = load_keys(res / "values")
    silent, stale = [], []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not vd.name.startswith("values-"):
            continue
        qualifier = vd.name[len("values-"):]
        if language_of(vd.name) in NOT_LOCALE:
            continue
        keys = load_keys(vd)
        if not keys:
            silent.append(f"{vd.name}: no string resources at all"
                          f"{' (empty directory)' if not catalogues(vd) else ''}")
            continue
        if OVERLAY_RE.match(vd.name):
            continue
        extra = keys - source
        if extra:
            sample = sorted(extra)[:3]
            stale.append(f"{vd.name}: {len(extra)} key(s) the source does not have "
                         f"({', '.join(sample)}{'…' if len(extra) > 3 else ''})")
    return silent, stale


def discover(root: Path) -> list[Path]:
    """Every Android module under `root`, found by its source catalogue, so no
    caller has to duplicate settings.gradle.kts.
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
        plurals = check_plurals(module)
        silent, stale = check_coverage(module)
        if not any((problems, arguments, placeholders, overlays, plurals, silent, stale)):
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
        if plurals:
            print(f"{module}: {len(plurals)} plural(s) missing a quantity the language needs", file=sys.stderr)
            for finding in plurals[:10]:
                print(f"  {finding}", file=sys.stderr)
        if silent:
            print(f"{module}: {len(silent)} locale(s) carry no strings and serve English throughout", file=sys.stderr)
            for finding in silent[:10]:
                print(f"  {finding}", file=sys.stderr)
        if stale:
            print(f"{module}: {len(stale)} locale(s) define keys the source dropped", file=sys.stderr)
            for finding in stale[:10]:
                print(f"  {finding}", file=sys.stderr)

    if any_problems and args.strict:
        sys.exit(1)


if __name__ == "__main__":
    main()

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

Six checks, each catching a class the others cannot:
  key presence         a locale missing a key the source has
  argument survival    a translation that drops a %1$s the English carries, so
                       the value never reaches the reader
  placeholder syntax   a value holding {name} or ICU tag markup, which Android
                       does not substitute - and which no English source in this
                       tree contains, making it a reliable marker that the value
                       came from a different key
  fallback awareness    a region catalogue is judged against its parent, so
                       values-de-rCH is not reported for keys values-de supplies
  plural completeness  a <plurals> missing a quantity its language needs, which
                       Android silently serves from `other` - fluent, wrong text
                       for the counts the missing category covers
  locale coverage      a values-<locale> directory holding no strings at all, so
                       the locale serves English throughout, and keys a locale
                       still defines that the source has dropped

Every check reads EVERY xml file in a values directory rather than strings.xml
alone: Android merges them all, so a split catalogue would otherwise be half
invisible to a gate whose entire purpose is seeing all of it.

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


def catalogues(directory: Path) -> list[Path]:
    """Every resource file in a values directory, not just strings.xml.

    Matching one filename was an assumption, not a rule: Android merges every
    XML file under values*/ and nothing stops a large catalogue being split into
    strings_extra.xml. The checker would then have gone silently blind to the
    split-off half - reporting ok for keys it never read - which is the failure
    mode this gate exists to prevent, so the assumption is now removed rather
    than documented.
    """
    if not directory.is_dir():
        return []
    return sorted(directory.glob("*.xml"))


def read_catalogues(directory: Path) -> str:
    return "\n".join(path.read_text(encoding="utf-8") for path in catalogues(directory))


def load_keys(directory: Path) -> set[str]:
    return set(KEY_RE.findall(read_catalogues(directory)))


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


def load_strings(directory: Path) -> dict[str, str]:
    return dict(STRING_RE.findall(read_catalogues(directory)))


PLURALS_RE = re.compile(r'<plurals name="([^"]+)">(.*?)</plurals>', re.S)
QUANTITY_RE = re.compile(r'quantity="(\w+)"')

# Format arguments inside <plurals> are deliberately NOT checked here. The
# obvious rule - require what the source's same quantity carries - reports
# Arabic "صورة واحدة" and Hebrew "שני קבצים מתנגשים", which are correct: both
# languages express one and two lexically rather than with a numeral, and both
# categories match exactly one number, so nothing is ambiguous. The rule that
# works needs to know which categories span SEVERAL numbers per locale, and
# Android lint already implements it as ImpliedQuantity - which is what found
# the Bengali and Persian `one` and the Gaelic `two` that hardcoded their
# digits. Left to lint rather than approximated here.

# Quantity categories each language needs, keyed by language, for every locale
# this project ships. Generated from CLDR rather than written by hand - see
# REGENERATE below - because the rules are not guessable and getting one wrong
# fails in whichever direction nobody notices: demanding a form that does not
# exist, or accepting a catalogue that is missing one.
#
# A missing category is not a lint nicety. Android serves `other` in its place,
# so Maltese loses its dual, Welsh mishandles zero and Georgian renders its
# plural for a count of one. The reader sees fluent, grammatically wrong text.
#
# The set is what an INTEGER count can actually select, plus `other`, which
# Android requires as the fallback whether or not any number reaches it. That
# distinction is load-bearing in both directions:
#
#   cs, sk, lt      CLDR declares `many` for these, but it selects on a visible
#   fr, es, it, pt  fractional part (1,5 dne) or on compact notation for large
#   ca              numbers. getQuantityString takes an int, so no reader ever
#                   reaches those forms and they are not required here.
#   be, pl, ru, uk  the converse: no integer selects `other` at all, yet it is
#                   still required, because Android falls back to it.
#
# Fourteen languages need ONLY `other` (ja, zh, ko, th, vi and friends), which
# is why there is no one/other default for anything absent from this table -
# such a default would report every one of them for a form their grammar has no
# use for. A language not listed is not checked; ay, gn, ht, qu and tg are
# absent because this CLDR build has no rules for them.
#
# REGENERATE when a locale is added, with node (its ICU carries CLDR):
#   for each shipped language tag L:
#     r = new Intl.PluralRules(L)
#     required = { r.select(n) for n in 0..1000 } + { "other" }
# Verify r.resolvedOptions().locale still matches L - an unsupported tag falls
# back to en-GB silently and would otherwise be recorded as one/other.
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

    Judged per file with no parent merging, unlike key presence: Android resolves
    a <plurals> as ONE resource, so a block present in values-de-rCH replaces the
    values-de block outright rather than topping it up. An absent block therefore
    inherits correctly and is fine, while a partial one is a real gap - the
    opposite of how a missing key behaves.
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

    Key presence is what check_module answers; these two are about whether the
    value under a present key is usable. Both are mechanical and neither has a
    judgement call in it, which is why they belong in a gate rather than a
    review: an argument the English carries and the translation does not is a
    value the reader will never see, and brace syntax is not something Android
    substitutes at all.
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

    Two gaps that key presence cannot see, because both are about catalogues it
    never opens or keys it never asks about:

      silent   a values-<locale> directory carrying no string resources at all.
               The old code skipped a directory with no strings.xml and reported
               the module ok, so a locale that served English for every single
               string looked identical to one that was complete.
      stale    a key a locale defines that the source no longer has. Harmless to
               the reader, but it is dead weight every future translation pass
               re-reads, and it usually means a rename landed in the source and
               nowhere else.
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

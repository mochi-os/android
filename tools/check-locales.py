#!/usr/bin/env python3
# Copyright © 2026 Mochisoft OÜ
# SPDX-License-Identifier: AGPL-3.0-only
# This file is part of Mochi, licensed under the GNU AGPL v3 with the
# Mochi Application Interface Exception - see license.txt and license-exception.md.

"""Verify every Android string catalogue against its source, and fail if not.

The single implementation: the gradle checkLocaleCompleteness task and the CI
step both shell out to this file. Add a new check here and nowhere else.

Checks: key presence; argument survival (a translation dropping a %1$s);
empty values (a present key holding no text, which Android shows as a blank
label where a missing one would at least fall back to English); brand terms
(Mochi, Stripe, OAuth, ... verbatim in Latin script wherever the English names
them); placeholder syntax ({name} or ICU markup, which Android never
substitutes); fallback awareness (a region catalogue judged against its
parent); inherited copies (a region catalogue holding the value its parent
already supplies, which overrides nothing today and keeps a later correction to
the parent from ever reaching it); plural completeness (a quantity the language needs, which Android
silently serves from `other`); locale coverage (an empty locale directory, a
locale the other modules carry and this one lacks, and keys the source has
dropped); bare double quotes (the resource compiler takes an unescaped " as
quoting and drops it, so "%1$s" shows without its quotes and „%2$s" loses its
closing one); decimal conversions (%d writes a number in the locale's own
digits with no grouping, where every count passes Format.formatNumber through
%s, and a %d meeting that String throws). Every check reads every xml file in a
values directory, because Android merges them all.

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


# A source string marked translatable="false" is deliberately English-only
# (a brand, a protocol token), so no catalogue owes it a translation.
UNTRANSLATABLE_RE = re.compile(r'<(?:string|plurals) name="([^"]+)"[^>]*translatable="false"')


def untranslatable(directory: Path) -> set[str]:
    return set(UNTRANSLATABLE_RE.findall(read_catalogues(directory)))


# Region-qualified locales whose nearest parent is not their bare language, as
# Android resolves them (its LocaleDataTables, built from CLDR's parentLocales):
# a Chinese region falls to its script catalogue, there being no values-zh,
# Macau to Hong Kong first, and Latin American Spanish to es-419 before es.
# Every other region the client carries (de-CH, fr-CA, nl-BE, pt-BR, en-CA,
# en-US) falls to its language. Cantonese is not here on purpose - yue matches
# by language, finds no zh catalogue, and falls through to English, so its gaps
# are real. A copy of ANDROID_PARENT in the monorepo's
# claude/scripts/i18n_glossary.py, which this repository cannot import: change
# the two together.
LATIN_AMERICA = ("AR", "BO", "BR", "BZ", "CL", "CO", "CR", "CU", "DO", "EC", "GT",
                 "HN", "MX", "NI", "PA", "PE", "PR", "PY", "SV", "US", "UY", "VE")
PARENT = {
    "values-zh-rHK": "values-b+zh+Hant",
    "values-zh-rMO": "values-zh-rHK",
    "values-zh-rCN": "values-b+zh+Hans",
    "values-zh-rSG": "values-b+zh+Hans",
    **{f"values-es-r{region}": "values-b+es+419" for region in LATIN_AMERICA},
}


def parent_of(qualifier: str) -> str | None:
    """The catalogue a values-* qualifier falls through to next, or None for
    a full locale: values-es-rAR -> values-b+es+419 -> values-es. A script
    qualifier (values-b+zh+Hans) has no language catalogue beneath it."""
    if qualifier in PARENT:
        return PARENT[qualifier]
    tag = qualifier[len("values-"):]
    if tag.startswith("b+"):
        language, *rest = tag[2:].split("+")
        regional = any(len(subtag) != 4 for subtag in rest)
    else:
        language, _, region = tag.partition("-r")
        regional = bool(region)
    return f"values-{language.lower()}" if regional else None


def ancestors(qualifier: str, present: set[str]) -> list[str]:
    """Localised catalogues Android would consult before falling back to values/,
    nearest first.

    Resolution walks the region's parents per resource, so a key held only by
    values-de is what a de-CH reader sees, and an es-AR reader is given
    values-b+es+419 before values-es. A parent the module lacks is passed over
    for the next. values/ is excluded: reaching it means English, which is the
    gap this gate finds.
    """
    out, seen = [], {qualifier}
    parent = parent_of(qualifier)
    while parent and parent not in seen:
        seen.add(parent)
        if parent in present:
            out.append(parent)
        parent = parent_of(parent)
    return out


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
ITEM_RE = re.compile(r'<item quantity="(\w+)">(.*?)</item>', re.S)


def load_plurals(directory: Path) -> dict[str, dict[str, str]]:
    """Each <plurals> block's items, quantity by quantity."""
    return {name: dict(ITEM_RE.findall(body))
            for name, body in PLURALS_RE.findall(read_catalogues(directory))}


# A <string> written with no text at all: `<string name="x"/>` or
# `<string name="x"></string>`. KEY_RE counts either as present.
EMPTY_RE = re.compile(r'<string name="([^"]+)"[^>]*?(?:/>|>\s*</string>)')

# The brand and protocol terms a translation must carry verbatim, in Latin
# script, wherever its English names them: a transliterated "Mochi" reads as a
# different word, and a translated protocol name breaks the instruction it is
# in. A copy of BRAND in the monorepo's claude/scripts/i18n_glossary.py, which
# this repository cannot import: change the two together.
BRAND = [
    "Mochi", "Stripe", "GitHub", "Pushbullet", "ntfy", "OAuth", "OIDC", "PKCE",
    "JWT", "SAML", "libp2p",
    "CalDAV", "CardDAV", "WebDAV", "vCard", "iCalendar", "ICS", "DAVx5", "Thunderbird",
    "ActivityPub", "WebFinger", "NodeInfo", "Mastodon", "Lemmy", "Mbin", "PieFed",
    "Bluesky", "Matrix", "Element", "XMPP", "Jabber", "DID", "OMEMO", "Olm", "Megolm",
]


def brands(text: str) -> list[str]:
    """The brand terms a text holds. Substring matching, as the web and label
    gates do: a term followed by a non-Latin suffix ("Mochiን") still counts."""
    return [term for term in BRAND if term in text]

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
    source -= untranslatable(res / "values")
    present = {d.name for d in res.iterdir() if d.is_dir() and d.name.startswith("values")}
    problems = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not locale_qualifier(vd.name):
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


def check_values(module_dir: Path) -> tuple[list[str], list[str], list[str], list[str]]:
    """Return (dropped-argument, leftover-placeholder, empty-value, lost-brand)
    findings for a module.

    Whether the value under a present key is usable, as opposed to key presence.
    """
    res = module_dir / "src" / "main" / "res"
    english = load_strings(res / "values")
    if not english:
        return [], [], [], []
    english_plurals = load_plurals(res / "values")
    arguments, placeholders, empties, lost = [], [], [], []
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
            for term in brands(source):
                if term not in value:
                    lost.append(f"{vd.name} {key}: {term} absent ({value.strip()[:48]})")
        for key in EMPTY_RE.findall(COMMENT_RE.sub("", read_catalogues(vd))):
            if english.get(key, "").strip():
                empties.append(f"{vd.name} {key}")
        for key, items in load_plurals(vd).items():
            source = english_plurals.get(key)
            if not source:
                continue
            # A term every English form names is owed by every translated form;
            # one only the singular names is not, as languages split counts
            # differently.
            terms = [term for term in BRAND if all(term in text for text in source.values())]
            for quantity, value in sorted(items.items()):
                if not value.strip() and any(text.strip() for text in source.values()):
                    empties.append(f"{vd.name} {key} {quantity}")
                for term in terms:
                    if term not in value:
                        lost.append(f"{vd.name} {key} {quantity}: {term} absent ({value.strip()[:48]})")

    return arguments, placeholders, empties, lost


# A value's text, element by element: every <string> and every plural <item>.
TEXT_RE = re.compile(r'<(string|item)(?=[\s>])[^>]*>(.*?)</\1>', re.S)
COMMENT_RE = re.compile(r'<!--.*?-->', re.S)


def bare_quote(value: str) -> bool:
    """Whether the value holds a double quote the compiler would swallow.

    A value wholly wrapped in quotes is the one deliberate use, to keep
    leading or trailing spaces, and CDATA is taken literally.
    """
    if value.startswith("<![CDATA["):
        return False
    bare = [i for i, c in enumerate(value) if c == '"' and (i == 0 or value[i - 1] != "\\")]
    if len(bare) == 2 and bare[0] == 0 and bare[1] == len(value) - 1:
        return False
    return bool(bare)


def check_quotes(module_dir: Path) -> list[str]:
    """Return values, in any catalogue the source included, with a bare quote."""
    res = module_dir / "src" / "main" / "res"
    if not res.is_dir():
        return []
    findings = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not (vd.name == "values" or vd.name.startswith("values-")):
            continue
        for match in TEXT_RE.finditer(COMMENT_RE.sub("", read_catalogues(vd))):
            if bare_quote(match.group(2)):
                findings.append(f"{vd.name}: {match.group(2).strip()[:48]}")
    return findings


# %d, %1$d, %,d and the like; a %% before it is a literal percent sign.
DECIMAL_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*d")


def check_decimals(module_dir: Path) -> list[str]:
    """Return values, in any catalogue the source included, formatting with %d."""
    res = module_dir / "src" / "main" / "res"
    if not res.is_dir():
        return []
    findings = []
    for vd in sorted(res.iterdir()):
        if not vd.is_dir() or not (vd.name == "values" or vd.name.startswith("values-")):
            continue
        for match in TEXT_RE.finditer(COMMENT_RE.sub("", read_catalogues(vd))):
            if DECIMAL_RE.search(match.group(2).replace("%%", "")):
                findings.append(f"{vd.name}: {match.group(2).strip()[:48]}")
    return findings


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
    # Android 7+ gives an English reader with no catalogue of its own (en-GB,
    # en-CA, plain en) the nearest regional one before values/, so every key a
    # regional overlay changes must also sit in values-en, spelt as values/
    # spells it, or values-en-rUS reaches every English reader.
    neutral = load_strings(res / "values-en")
    for vd in sorted(res.iterdir()):
        if (not vd.is_dir() or not OVERLAY_RE.match(vd.name) or vd.name == "values-en"
                or not locale_qualifier(vd.name)):
            continue
        for key, value in load_strings(vd).items():
            if key in english and value != english[key] and neutral.get(key) != english[key]:
                findings.append(f"{vd.name} {key}: values-en does not carry it as values/ does, "
                                f"so every English reader is given {vd.name}'s text")
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


def check_copies(module_dir: Path) -> list[str]:
    """Return findings for region catalogue values identical to what they inherit.

    A region catalogue (values-nl-rBE, values-b+es+419, values-en-rUS) carries only
    what its readers' wording does differently. A value equal to the one the
    nearest ancestor holding the key supplies changes nothing a reader sees, but
    it wins over that ancestor for good: reword the parent and the region keeps
    the old text. Plurals are judged block by block, item for item.
    """
    res = module_dir / "src" / "main" / "res"
    if not res.is_dir():
        return []
    present = {d.name for d in res.iterdir() if d.is_dir() and locale_qualifier(d.name)}
    strings, plurals = {}, {}

    def held(qualifier):
        if qualifier not in strings:
            strings[qualifier] = load_strings(res / qualifier)
            plurals[qualifier] = load_plurals(res / qualifier)
        return strings[qualifier], plurals[qualifier]

    findings = []
    for qualifier in sorted(present):
        chain = ancestors(qualifier, present)
        if not chain:
            continue
        own_strings, own_plurals = held(qualifier)
        for table, index in ((own_strings, 0), (own_plurals, 1)):
            for key, value in table.items():
                for ancestor in chain:
                    inherited = held(ancestor)[index]
                    if key in inherited:
                        if inherited[key] == value:
                            findings.append(f"{qualifier} {key}: the same as {ancestor}, which it inherits; "
                                            f"drop the copy so a correction there reaches it")
                        break
    return findings


# values-* qualifiers that are NOT locales. Android's locale qualifier is a two
# or three letter language, so most configuration qualifiers (night, land, v29,
# hdpi) cannot be mistaken for one - but the UI modes tv, car and vr and the
# HDR mode hdr can, and such a directory holding no strings is not a
# translation gap.
NOT_LOCALE = {"tv", "car", "vr", "hdr"}

# A values-* directory named by a locale and nothing else: a language, with a
# region (de-rCH) or in BCP 47's b+ form (b+es+419, b+zh+Hant). Any other
# configuration qualifier (night, land, v31, sw600dp, hdpi), alone or beside a
# locale (de-night), names a set of overrides for one configuration, not a
# catalogue a locale's readers are owed whole.
LOCALE_QUALIFIER_RE = re.compile(r"^values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[a-z]{2,3}(?:\+[A-Za-z0-9]{2,8})*)$")


def locale_qualifier(qualifier: str) -> bool:
    """Whether a values-* directory name is a locale's catalogue."""
    return bool(LOCALE_QUALIFIER_RE.match(qualifier)) and language_of(qualifier) not in NOT_LOCALE


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
        keys = load_keys(vd)
        if not keys:
            if locale_qualifier(vd.name):
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
        path.parents[4].resolve()
        for path in root.rglob("src/main/res/values/strings.xml")
    }
    return sorted(found)


# A locale qualifier's language: two or three lower-case letters.
LANGUAGE_RE = re.compile(r"^[a-z]{2,3}$")


def full_locale(qualifier: str) -> bool:
    """Whether a values-* directory is a locale every module must carry.

    A regional catalogue (values-de-rCH, values-b+es+419) is sparse by design,
    falling through to its language, and an English one falls through to
    values/, so neither is owed by a module. A script one (values-b+zh+Hans)
    has no language catalogue beneath it, so it is.
    """
    if OVERLAY_RE.match(qualifier) or not locale_qualifier(qualifier):
        return False
    tag = qualifier[len("values-"):]
    if tag.startswith("b+"):
        language, *rest = tag[2:].split("+")
        if any(len(subtag) != 4 for subtag in rest):
            return False
    elif "-r" in tag:
        return False
    else:
        language = tag
    return bool(LANGUAGE_RE.match(language)) and language not in NOT_LOCALE


def full_locales(module_dir: Path) -> set[str]:
    res = module_dir / "src" / "main" / "res"
    if not res.is_dir():
        return set()
    return {d.name for d in res.iterdir() if d.is_dir() and full_locale(d.name)}


# The smallest legitimate locale set for a module checked with nothing to
# measure it against. Deliberately loose: it only has to catch a set that has
# collapsed, not police a locale or two.
FLOOR = 90


def project_of(module_dir: Path) -> Path | None:
    """The Android project a module belongs to: the nearest directory above it
    holding a settings.gradle.kts (or settings.gradle)."""
    for directory in module_dir.resolve().parents:
        if (directory / "settings.gradle.kts").is_file() or (directory / "settings.gradle").is_file():
            return directory
    return None


def reference_modules(modules: list[Path]) -> tuple[set[Path], bool]:
    """The modules a run's locale set is measured against, and whether there
    are none beyond a lone module outside any project.

    Key presence only judges the locale directories a module has, so a module
    created with three locales, or one that lost a directory in a merge,
    passed and read English throughout. A module checked on its own is
    measured against the rest of its project.
    """
    reference = {m.resolve() for m in modules}
    projects = {p for p in (project_of(m) for m in modules) if p}
    for project in projects:
        reference |= set(discover(project))
    return reference, len(reference) == 1 and not projects


def check_presence(module_dir: Path, expected: set[str], lone: bool) -> list[str]:
    """Return the locales the module lacks: those its project's other modules
    carry, or, for a lone module, a set smaller than the floor."""
    have = full_locales(module_dir)
    if lone:
        if len(have) < FLOOR:
            return [f"only {len(have)} locale directories, below the {FLOOR} floor"]
        return []
    return [f"{name}: absent" for name in sorted(expected - have)]


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

    # The locale set every module owes, worked out once for the run.
    reference, lone = reference_modules(modules)
    expected = set().union(*(full_locales(m) for m in reference))

    any_problems = False
    for module_str in modules:
        module = Path(module_str).resolve()
        problems = check_module(module)
        absent = check_presence(module, expected, lone)
        arguments, placeholders, empties, lost = check_values(module)
        overlays = check_overlays(module)
        copies = check_copies(module)
        plurals = check_plurals(module)
        silent, stale = check_coverage(module)
        quotes = check_quotes(module)
        decimals = check_decimals(module)
        if not any((problems, absent, arguments, placeholders, empties, lost, overlays, copies, plurals,
                    silent, stale, quotes, decimals)):
            print(f"{module}: ok")
            continue
        any_problems = True
        if absent:
            print(f"{module}: locale directories missing, so the module reads English there throughout",
                  file=sys.stderr)
            for finding in absent[:10]:
                print(f"  {finding}", file=sys.stderr)
        if problems:
            print(f"{module}: {len(problems)} locale(s) incomplete", file=sys.stderr)
            for locale, missing in problems:
                sample = sorted(missing)[:3]
                print(f"  {locale}: {len(missing)} missing ({', '.join(sample)}{'…' if len(missing) > 3 else ''})", file=sys.stderr)
        if arguments:
            print(f"{module}: {len(arguments)} value(s) drop a format argument", file=sys.stderr)
            for finding in arguments[:10]:
                print(f"  {finding}", file=sys.stderr)
        if empties:
            print(f"{module}: {len(empties)} value(s) are empty, which Android shows as a blank label", file=sys.stderr)
            for finding in empties[:10]:
                print(f"  {finding}", file=sys.stderr)
        if lost:
            print(f"{module}: {len(lost)} value(s) drop or transliterate a brand term; keep it verbatim in Latin script", file=sys.stderr)
            for finding in lost[:10]:
                print(f"  {finding}", file=sys.stderr)
        if placeholders:
            print(f"{module}: {len(placeholders)} value(s) carry web placeholder syntax", file=sys.stderr)
            for finding in placeholders[:10]:
                print(f"  {finding}", file=sys.stderr)
        if overlays:
            print(f"{module}: {len(overlays)} English overlay value(s) drifted", file=sys.stderr)
            for finding in overlays[:10]:
                print(f"  {finding}", file=sys.stderr)
        if copies:
            print(f"{module}: {len(copies)} region value(s) copy what their parent already supplies",
                  file=sys.stderr)
            for finding in copies[:10]:
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
        if quotes:
            print(f"{module}: {len(quotes)} value(s) hold a bare \" the compiler drops; write \\\"", file=sys.stderr)
            for finding in quotes[:10]:
                print(f"  {finding}", file=sys.stderr)
        if decimals:
            print(f"{module}: {len(decimals)} value(s) format a number with %d; pass Format.formatNumber() through %s", file=sys.stderr)
            for finding in decimals[:10]:
                print(f"  {finding}", file=sys.stderr)

    if any_problems and args.strict:
        sys.exit(1)


if __name__ == "__main__":
    main()

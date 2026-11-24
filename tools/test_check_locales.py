#!/usr/bin/env python3
# Copyright © 2026 Mochisoft OÜ
# SPDX-License-Identifier: AGPL-3.0-only
# This file is part of Mochi, licensed under the GNU AGPL v3 with the
# Mochi Application Interface Exception - see license.txt and license-exception.md.

"""Unit tests for check-locales.py, the Android catalogue gate.

    python3 -B -m unittest discover -s tools -p 'test_*.py'
"""
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

CHECKER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-locales.py")


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def catalogue(*entries):
    return "<resources>\n" + "".join(f"    {entry}\n" for entry in entries) + "</resources>\n"


def run(*arguments):
    return subprocess.run([sys.executable, "-B", CHECKER, "--strict"] + list(arguments),
                          capture_output=True, text=True)


class Module(unittest.TestCase):
    """A temporary Android project holding one module, `lib`."""

    def setUp(self):
        self.root = tempfile.mkdtemp()
        write(os.path.join(self.root, "settings.gradle.kts"), "")
        self.module = os.path.join(self.root, "lib")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def strings(self, directory, *entries, module=None):
        write(os.path.join(module or self.module, "src", "main", "res", directory, "strings.xml"),
              catalogue(*entries))


class Brands(Module):
    """A brand or protocol term the English names stays verbatim, in Latin
    script, in every translation: "ሞቺ" for "Mochi" reads as another word."""

    def setUp(self):
        super().setUp()
        self.strings("values", '<string name="about">Mochi Client</string>')

    def test_a_transliterated_brand_fails(self):
        self.strings("values-am", '<string name="about">ሞቺ ደንበኛ</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-am about: Mochi absent", result.stderr)

    def test_the_brand_kept_in_latin_script_passes(self):
        self.strings("values-am", '<string name="about">Mochi ደንበኛ</string>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_the_brand_with_a_suffix_attached_passes(self):
        self.strings("values-am", '<string name="about">የMochiን ደንበኛ</string>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_protocol_term_inside_a_sentence_is_held_too(self):
        self.strings("values", '<string name="about">Sign in with OAuth</string>')
        self.strings("values-ru", '<string name="about">Войти через ОАут</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OAuth absent", result.stderr)

    def test_a_plural_form_dropping_the_brand_fails(self):
        self.strings("values",
                     '<string name="about">Mochi Client</string>',
                     '<plurals name="devices"><item quantity="one">%1$s Mochi device</item>'
                     '<item quantity="other">%1$s Mochi devices</item></plurals>')
        self.strings("values-am",
                     '<string name="about">Mochi ደንበኛ</string>',
                     '<plurals name="devices"><item quantity="one">%1$s የMochi መሣሪያ</item>'
                     '<item quantity="other">%1$s የሞቺ መሣሪያዎች</item></plurals>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-am devices other: Mochi absent", result.stderr)

    def test_a_brand_only_the_singular_names_is_not_owed_by_every_form(self):
        self.strings("values",
                     '<string name="about">Mochi Client</string>',
                     '<plurals name="devices"><item quantity="one">Your Mochi device</item>'
                     '<item quantity="other">%1$s devices</item></plurals>')
        self.strings("values-am",
                     '<string name="about">Mochi ደንበኛ</string>',
                     '<plurals name="devices"><item quantity="one">የእርስዎ መሣሪያ</item>'
                     '<item quantity="other">%1$s መሣሪያዎች</item></plurals>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


class Empty(Module):
    """A present key with no text satisfies key presence, and Android then shows
    a blank label where a missing key would have fallen back to English."""

    def setUp(self):
        super().setUp()
        self.english = ('<string name="save">Save</string>',
                        '<plurals name="posts"><item quantity="one">%1$s post</item>'
                        '<item quantity="other">%1$s posts</item></plurals>')
        self.strings("values", *self.english)
        self.plural = ('<plurals name="posts"><item quantity="one">%1$s publication</item>'
                       '<item quantity="other">%1$s publications</item></plurals>')

    def test_an_empty_string_fails(self):
        for value in ('<string name="save"></string>', '<string name="save">  </string>',
                      '<string name="save"/>', '<string name="save" />'):
            with self.subTest(value=value):
                self.strings("values-fr", value, self.plural)
                result = run(self.module)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("values-fr save", result.stderr)
                self.assertIn("empty", result.stderr)

    def test_an_empty_plural_item_fails(self):
        self.strings("values-fr", '<string name="save">Enregistrer</string>',
                     '<plurals name="posts"><item quantity="one">%1$s publication</item>'
                     '<item quantity="other"></item></plurals>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-fr posts other", result.stderr)

    def test_a_filled_catalogue_passes(self):
        self.strings("values-fr", '<string name="save">Enregistrer</string>', self.plural)
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_an_empty_english_source_owes_no_text(self):
        self.strings("values", '<string name="spacer"></string>', *self.english)
        self.strings("values-fr", '<string name="spacer"></string>',
                     '<string name="save">Enregistrer</string>', self.plural)
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_commented_out_empty_string_is_not_read(self):
        self.strings("values-fr", '<!-- <string name="save"></string> -->',
                     '<string name="save">Enregistrer</string>', self.plural)
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


class Presence(Module):
    """A module lacking a locale directory its project's other modules carry
    reads English there throughout, and key presence, which judges only the
    directories a module has, cannot see it."""

    def setUp(self):
        super().setUp()
        self.other = os.path.join(self.root, "apps", "chess")
        for module in (self.module, self.other):
            self.strings("values", '<string name="save">Save</string>', module=module)
        for locale, value in (("fr", "Enregistrer"), ("de", "Speichern"), ("b+zh+Hans", "保存")):
            self.strings(f"values-{locale}", f'<string name="save">{value}</string>', module=self.module)

    def test_a_module_lacking_a_locale_its_siblings_carry_fails(self):
        self.strings("values-fr", '<string name="save">Enregistrer</string>', module=self.other)
        self.strings("values-b+zh+Hans", '<string name="save">保存</string>', module=self.other)
        result = run("--discover", self.root)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-de: absent", result.stderr)
        self.assertNotIn("values-fr: absent", result.stderr)

    def test_a_module_checked_alone_is_measured_against_its_project(self):
        self.strings("values-fr", '<string name="save">Enregistrer</string>', module=self.other)
        result = run(self.other)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-de: absent", result.stderr)
        self.assertIn("values-b+zh+Hans: absent", result.stderr)

    def test_modules_carrying_the_same_locales_pass(self):
        for locale, value in (("fr", "Enregistrer"), ("de", "Speichern"), ("b+zh+Hans", "保存")):
            self.strings(f"values-{locale}", f'<string name="save">{value}</string>', module=self.other)
        result = run("--discover", self.root)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_regional_or_english_overlay_is_not_owed(self):
        for locale, value in (("fr", "Enregistrer"), ("de", "Speichern"), ("b+zh+Hans", "保存"),
                              ("es", "Guardar")):
            self.strings(f"values-{locale}", f'<string name="save">{value}</string>', module=self.other)
        self.strings("values-es", '<string name="save">Guardar</string>', module=self.module)
        for locale, value in (("de-rCH", "Sichern"), ("b+es+419", "Salvar"), ("en-rUS", "Keep"),
                              ("en", "Save")):
            self.strings(f"values-{locale}", f'<string name="save">{value}</string>', module=self.module)
        result = run("--discover", self.root)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


class Copies(Module):
    """A region catalogue holding the value its parent already supplies wins over
    the parent for good, so a later correction there never reaches its readers."""

    def setUp(self):
        super().setUp()
        self.strings("values", '<string name="save">Save</string>', '<string name="open">Open</string>',
                     '<plurals name="files"><item quantity="one">%1$s file</item>'
                     '<item quantity="other">%1$s files</item></plurals>')
        self.strings("values-nl", '<string name="save">Opslaan</string>', '<string name="open">Openen</string>',
                     '<plurals name="files"><item quantity="one">%1$s bestand</item>'
                     '<item quantity="other">%1$s bestanden</item></plurals>')
        self.strings("values-es", '<string name="save">Guardar</string>', '<string name="open">Abrir</string>',
                     '<plurals name="files"><item quantity="one">%1$s archivo</item>'
                     '<item quantity="other">%1$s archivos</item></plurals>')

    def test_a_copy_of_the_parent_fails(self):
        self.strings("values-nl-rBE", '<string name="save">Opslaan</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-nl-rBE save: the same as values-nl", result.stderr)

    def test_a_copy_of_a_plural_fails(self):
        self.strings("values-nl-rBE", '<plurals name="files"><item quantity="one">%1$s bestand</item>'
                     '<item quantity="other">%1$s bestanden</item></plurals>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-nl-rBE files: the same as values-nl", result.stderr)

    def test_a_plural_differing_in_one_item_passes(self):
        self.strings("values-nl-rBE", '<plurals name="files"><item quantity="one">%1$s bestand</item>'
                     '<item quantity="other">%1$s bestandjes</item></plurals>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_copy_is_judged_against_the_nearest_ancestor_holding_the_key(self):
        # es-AR reads es-419 first: a copy of es-419 is a copy, and a value equal
        # to es that differs from es-419 is the region's own wording.
        self.strings("values-b+es+419", '<string name="save">Salvar</string>')
        self.strings("values-es-rAR", '<string name="save">Salvar</string>', '<string name="open">Abrir</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-es-rAR save: the same as values-b+es+419", result.stderr)
        self.assertIn("values-es-rAR open: the same as values-es", result.stderr)
        self.strings("values-es-rAR", '<string name="save">Guardar</string>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_genuine_regional_wording_passes(self):
        self.strings("values-nl-rBE", '<string name="save">Bewaren</string>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_full_locale_equal_to_another_is_not_a_copy(self):
        # Only a region inherits: values-nl is not judged against anything.
        self.strings("values-af", '<string name="save">Opslaan</string>', '<string name="open">Oop</string>',
                     '<plurals name="files"><item quantity="one">%1$s lêer</item>'
                     '<item quantity="other">%1$s lêers</item></plurals>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


class Configuration(Module):
    """A values-* directory named by a configuration qualifier (night, v31,
    land, sw600dp, hdr) or by a locale beside one (de-night) holds overrides
    for that configuration, not a catalogue a locale's readers are owed whole,
    so it is neither missing keys nor a locale serving English throughout."""

    def setUp(self):
        super().setUp()
        self.strings("values", '<string name="save">Save</string>', '<string name="greeting">Hello %1$s</string>')
        self.strings("values-fr", '<string name="save">Enregistrer</string>',
                     '<string name="greeting">Bonjour %1$s</string>')

    def resource(self, directory, name, text):
        write(os.path.join(self.module, "src", "main", "res", directory, name), f"<resources>{text}</resources>\n")

    def test_a_configuration_directory_is_not_a_locale(self):
        self.resource("values-night", "colors.xml", '<color name="surface">#000000</color>')
        self.resource("values-v31", "themes.xml", '<style name="Theme" parent="Base"/>')
        self.resource("values-land", "dimens.xml", '<dimen name="gutter">24dp</dimen>')
        self.resource("values-sw600dp", "dimens.xml", '<dimen name="gutter">32dp</dimen>')
        self.resource("values-hdr", "colors.xml", '<color name="surface">#101010</color>')
        self.resource("values-fr-night", "colors.xml", '<color name="surface">#000000</color>')
        self.strings("values-land", '<string name="save">Save</string>')
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_configuration_directory_holding_strings_is_still_read(self):
        """Its values are what that configuration shows: one dropping the
        English's argument renders without it all the same."""
        self.strings("values-land", '<string name="greeting">Hello</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-land greeting: %1$s absent", result.stderr)

    def test_a_locale_directory_is_still_judged(self):
        self.strings("values-de", '<string name="save">Speichern</string>')
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("values-de: 1 missing (greeting)", result.stderr)


def load_checker():
    spec = importlib.util.spec_from_file_location("check_locales", CHECKER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Ancestors(unittest.TestCase):
    """The catalogues a region falls through to are Android's, from CLDR's
    parent locales: es-AR reads es-419 before es, and zh-MO reads zh-HK before
    the Traditional script catalogue."""

    PRESENT = {"values", "values-es", "values-b+es+419", "values-es-rAR", "values-de", "values-de-rCH",
               "values-b+zh+Hant", "values-zh-rHK", "values-zh-rMO", "values-pt", "values-pt-rBR"}

    def setUp(self):
        self.checker = load_checker()

    def test_latin_american_spanish_reads_es_419_first(self):
        self.assertEqual(["values-b+es+419", "values-es"], self.checker.ancestors("values-es-rAR", self.PRESENT))
        self.assertEqual(["values-b+es+419", "values-es"], self.checker.ancestors("values-es-rMX", self.PRESENT))

    def test_a_parent_the_module_lacks_is_passed_over(self):
        present = self.PRESENT - {"values-b+es+419"}
        self.assertEqual(["values-es"], self.checker.ancestors("values-es-rAR", present))

    def test_macau_reads_hong_kong_first(self):
        self.assertEqual(["values-zh-rHK", "values-b+zh+Hant"],
                         self.checker.ancestors("values-zh-rMO", self.PRESENT))

    def test_the_other_regions_read_their_language(self):
        for qualifier, parents in (("values-de-rCH", ["values-de"]), ("values-pt-rBR", ["values-pt"]),
                                   ("values-b+es+419", ["values-es"]), ("values-zh-rHK", ["values-b+zh+Hant"]),
                                   ("values-de", []), ("values-b+zh+Hant", []), ("values-yue", [])):
            with self.subTest(qualifier=qualifier):
                self.assertEqual(parents, self.checker.ancestors(qualifier, self.PRESENT))


class Floor(unittest.TestCase):
    """A module outside any project has nothing to be measured against, so a
    set that has collapsed is caught by a floor."""

    def setUp(self):
        self.root = tempfile.mkdtemp()
        self.module = os.path.join(self.root, "lone")
        write(os.path.join(self.module, "src", "main", "res", "values", "strings.xml"),
              catalogue('<string name="save">Save</string>'))

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def locales(self, count):
        for n in range(count):
            language = chr(ord("a") + n // 26) + chr(ord("a") + n % 26)
            write(os.path.join(self.module, "src", "main", "res", f"values-{language}", "strings.xml"),
                  catalogue('<string name="save">Save</string>'.replace("Save", f"Save {language}")))

    def test_a_lone_module_below_the_floor_fails(self):
        self.locales(3)
        result = run(self.module)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("floor", result.stderr)

    def test_a_lone_module_at_the_floor_passes(self):
        self.locales(90)
        result = run(self.module)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()

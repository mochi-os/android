# Atkinson Hyperlegible

Bundled as `lib/src/main/res/font/atkinson_hyperlegible_regular.ttf`,
`atkinson_hyperlegible_italic.ttf`, `atkinson_hyperlegible_bold.ttf` and
`atkinson_hyperlegible_bold_italic.ttf`: the files `AtkinsonHyperlegible-Regular.ttf`,
`AtkinsonHyperlegible-Italic.ttf`, `AtkinsonHyperlegible-Bold.ttf` and
`AtkinsonHyperlegible-BoldItalic.ttf` (version 1.006), unmodified, from the
Google Fonts repository. Only the names were changed, to the lower-case form
Android requires of a resource.

    https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegible

The four static cuts are the same weights and styles the web loads for the
same preference (400 and 700, upright and italic), about 220 KB together.
`Type.kt` builds the family the "Dyslexia-friendly" font preference applies
from them; a weight between the two resolves to the nearer cut.

Licensed under the SIL Open Font License 1.1, copied verbatim into
`license.txt` beside this file, as the licence requires of anyone who
redistributes the font. Copyright 2020 Braille Institute of America, Inc. The
OFL is separate from the app's own licence: the font is not covered by Mochi's
AGPL, and Mochi is not covered by the OFL.

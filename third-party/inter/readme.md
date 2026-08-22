# Inter

Bundled as `lib/src/main/res/font/inter_variable.ttf`: the `InterVariable.ttf`
file from the Inter 4.1 release, unmodified, taken from the project's own
release rather than a mirror.

    https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip

One variable face rather than four static cuts. It carries a `wght` axis over
100-900, which is every weight the type scale asks for and every weight it may
later ask for, and costs 460 KB in the APK against the 808 KB the four static
TTFs compress to. Half of the file is the `gvar` delta table that buys that
axis; the four statics would each carry a full set of outlines instead.

It is the offline half of the font stack. `Type.kt` still asks Google Fonts for
each weight first and lists this file behind it, so a device with Play Services
renders the downloaded cut and a device without one - a de-Googled build, an
emulator image with no GMS - renders Inter from the APK instead of the
platform's default face.

Licensed under the SIL Open Font License 1.1, copied verbatim into
`license.txt` beside this file, as the licence requires of anyone who
redistributes the font. Copyright The Inter Project Authors. The OFL is
separate from the app's own licence: the font is not covered by Mochi's AGPL,
and Mochi is not covered by the OFL.

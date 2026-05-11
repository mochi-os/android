package org.mochios.android.i18n

/**
 * Format-related user preferences. Mirrors the schema in
 * `apps/settings/user/preferences.star` for keys that affect formatting.
 *
 * Each field is the resolved value — `auto` is replaced with whatever the
 * device suggests at fetch time, so consumers never have to redo detection.
 *
 * Defaults match web's [LocaleProvider.defaultResolved] so that an unauth
 * Android session formats the same way an unauth web session would.
 */
data class UserPreferences(
    val dateFormat: DateFormat = DateFormat.YYYY_MM_DD,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val timestampDisplay: TimestampDisplay = TimestampDisplay.AUTO,
    /** 0=Sunday … 6=Saturday */
    val weekStartsOn: Int = 1,
    val numberFormat: NumberFormat = NumberFormat.WESTERN_COMMA_DOT,
    val units: Units = Units.METRIC,
    val timezone: String = "UTC",
    val appearance: Appearance = Appearance.AUTO,
    val density: Density = Density.THEME,
    val radius: Radius = Radius.THEME,
    val font: FontPref = FontPref.THEME,
    val fontSize: FontSizePref = FontSizePref.THEME
)

enum class Appearance {
    LIGHT, DARK, AUTO;

    companion object {
        fun fromString(s: String?): Appearance = when (s) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> AUTO
        }
    }
}

enum class Density(val scale: Float) {
    /** Use whatever the theme/Compose defaults give. */
    THEME(1.0f),
    COMPACT(0.85f),
    COMFORTABLE(1.0f),
    SPACIOUS(1.20f);

    companion object {
        fun fromString(s: String?): Density = when (s) {
            "compact" -> COMPACT
            "comfortable" -> COMFORTABLE
            "spacious" -> SPACIOUS
            else -> THEME
        }
    }
}

/** Corner radius preset. Values mirror the rem values from the server schema
 *  (0rem, 0.375rem, 0.75rem, 1.75rem) treating 1rem = 16dp. */
enum class Radius(val dp: Int?) {
    THEME(null), NONE(0), SMALL(6), MEDIUM(12), LARGE(28);

    companion object {
        fun fromString(s: String?): Radius = when (s) {
            "0rem" -> NONE
            "0.375rem" -> SMALL
            "0.75rem" -> MEDIUM
            "1.75rem" -> LARGE
            else -> THEME
        }
    }
}

enum class FontPref {
    THEME, SYSTEM, SERIF, DYSLEXIA;

    companion object {
        fun fromString(s: String?): FontPref = when (s) {
            "system" -> SYSTEM
            "serif" -> SERIF
            "dyslexia" -> DYSLEXIA
            else -> THEME
        }
    }
}

enum class FontSizePref(val scale: Float) {
    THEME(1.0f), SMALL(0.9f), NORMAL(1.0f), LARGE(1.15f), EXTRA_LARGE(1.30f);

    companion object {
        fun fromString(s: String?): FontSizePref = when (s) {
            "small" -> SMALL
            "normal" -> NORMAL
            "large" -> LARGE
            "extra-large" -> EXTRA_LARGE
            else -> THEME
        }
    }
}

enum class DateFormat(val pattern: String) {
    YYYY_MM_DD("YYYY-MM-DD"),
    DD_SLASH_MM_YYYY("DD/MM/YYYY"),
    DD_DOT_MM_YYYY("DD.MM.YYYY"),
    MM_SLASH_DD_YYYY("MM/DD/YYYY"),
    D_MMM_YYYY("D MMM YYYY");

    companion object {
        fun fromString(s: String?): DateFormat? = when (s) {
            "YYYY-MM-DD" -> YYYY_MM_DD
            "DD/MM/YYYY" -> DD_SLASH_MM_YYYY
            "DD.MM.YYYY" -> DD_DOT_MM_YYYY
            "MM/DD/YYYY" -> MM_SLASH_DD_YYYY
            "D MMM YYYY" -> D_MMM_YYYY
            else -> null
        }
    }
}

enum class TimeFormat {
    H12, H24;

    companion object {
        fun fromString(s: String?): TimeFormat? = when (s) {
            "12h" -> H12
            "24h" -> H24
            else -> null
        }
    }
}

enum class TimestampDisplay {
    AUTO, RELATIVE, ABSOLUTE;

    companion object {
        fun fromString(s: String?): TimestampDisplay = when (s) {
            "relative" -> RELATIVE
            "absolute" -> ABSOLUTE
            else -> AUTO
        }
    }
}

enum class NumberFormat(val groupChar: Char, val decimalChar: Char, val isIndian: Boolean) {
    /** 1,000.00 — US/UK */
    WESTERN_COMMA_DOT(',', '.', false),
    /** 1.000,00 — most of continental Europe */
    EUROPEAN_DOT_COMMA('.', ',', false),
    /** 1 000,00 — French style with narrow no-break space */
    FRENCH_SPACE_COMMA(' ', ',', false),
    /** 1'000.00 — Swiss */
    SWISS_APOSTROPHE_DOT('\'', '.', false),
    /** 1,00,000.00 — Indian grouping */
    INDIAN_LAKH(',', '.', true);

    companion object {
        fun fromString(s: String?): NumberFormat? = when (s) {
            "1,000.00" -> WESTERN_COMMA_DOT
            "1.000,00" -> EUROPEAN_DOT_COMMA
            "1 000,00" -> FRENCH_SPACE_COMMA
            "1'000.00" -> SWISS_APOSTROPHE_DOT
            "1,00,000.00" -> INDIAN_LAKH
            else -> null
        }
    }
}

enum class Units {
    METRIC, IMPERIAL, USA;

    companion object {
        fun fromString(s: String?): Units = when (s) {
            "imperial" -> IMPERIAL
            "usa" -> USA
            else -> METRIC
        }
    }
}

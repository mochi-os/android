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
    val timezone: String = "UTC"
)

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

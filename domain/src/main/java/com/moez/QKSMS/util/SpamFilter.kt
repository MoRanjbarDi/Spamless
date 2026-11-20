package com.moez.QKSMS.util

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Pure matching policy, shared by live delivery and provider imports. */
class SpamFilter(iranAds: Boolean, customRegex: String) {
    private val custom = compile(customRegex.takeIf { it.isNotBlank() })
    private val iran = if (iranAds) IRAN_PATTERN else null

    fun matches(body: String): Boolean =
        iran?.matcher(body)?.find() == true || custom?.matcher(body)?.find() == true

    companion object {
        // Tasker opt-out rule, including Latin, Persian and Arabic-Indic digits.
        const val IRAN_REGEX = ".*لغو[:\\s]*(11|۱۱|١١).*"
        private val IRAN_PATTERN = Pattern.compile(IRAN_REGEX, Pattern.DOTALL)

        fun validationError(regex: String): String? = try {
            if (regex.isNotBlank()) Pattern.compile(regex, Pattern.DOTALL)
            null
        } catch (e: PatternSyntaxException) {
            e.description
        }

        private fun compile(regex: String?): Pattern? = try {
            regex?.let { Pattern.compile(it, Pattern.DOTALL) }
        } catch (e: PatternSyntaxException) {
            null // A malformed preference must never interrupt SMS delivery.
        }
    }
}

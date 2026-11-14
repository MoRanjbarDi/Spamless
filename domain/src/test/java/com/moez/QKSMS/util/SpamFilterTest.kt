package com.moez.QKSMS.util

import org.junit.Assert.*
import org.junit.Test

class SpamFilterTest {
    @Test fun iranRuleMatchesTaskerExamples() {
        val filter = SpamFilter(true, "")
        listOf("offer لغو11", "offer لغو:11", "offer لغو ۱۱", "لغو : ۱۱", "offer\nلغو:\n11\n")
            .forEach { assertTrue(it, filter.matches(it)) }
    }

    @Test fun ordinaryMessagesStayInInbox() {
        val filter = SpamFilter(true, "")
        listOf("Your verification code is 123456", "Meet at 11", "لغو ۱۲", "لغو", "").forEach {
            assertFalse(it, filter.matches(it))
        }
    }

    @Test fun disabledAndBlankRulesNeverMatch() {
        assertFalse(SpamFilter(false, "").matches("لغو 11"))
        assertFalse(SpamFilter(false, "  ").matches("normal text"))
    }

    @Test fun customRuleWorksWithIranRuleDisabled() {
        val filter = SpamFilter(false, "(?i)unsubscribe\\s*$")
        assertTrue(filter.matches("Offer\nUNSUBSCRIBE"))
        assertFalse(filter.matches("لغو ۱۱"))
    }

    @Test fun enabledRulesUseOrSemantics() {
        val filter = SpamFilter(true, "PROMO")
        assertTrue(filter.matches("لغو 11"))
        assertTrue(filter.matches("PROMO: sale today"))
        assertFalse(filter.matches("normal message"))
    }

    @Test fun invalidSavedRuleDoesNotBreakDeliveryOrIranRule() {
        val filter = SpamFilter(true, "[")
        assertTrue(filter.matches("لغو ۱۱"))
        assertFalse(filter.matches("normal message"))
        assertFalse(SpamFilter(false, "[").matches("normal message"))
    }

    @Test fun validationAcceptsEmptyAndRejectsMalformedPatterns() {
        assertNull(SpamFilter.validationError(""))
        assertNull(SpamFilter.validationError("(?i)offer|sale"))
        assertNotNull(SpamFilter.validationError("["))
        assertNotNull(SpamFilter.validationError("("))
    }

    @Test fun sameSenderHasIndependentPartitions() {
        val filter = SpamFilter(true, "")
        val normal = SpamThread.classify(42, true, "verification code", filter)
        val spam = SpamThread.classify(42, true, "sale لغو ۱۱", filter)
        assertEquals(42L, normal)
        assertEquals(-43L, spam)
        assertNotEquals(normal, spam)
        assertEquals(SpamThread.providerId(normal), SpamThread.providerId(spam))
    }

    @Test fun onlyIncomingSmsCanBecomeSpam() {
        assertEquals(42L, SpamThread.classify(42, false, "لغو 11", SpamFilter(true, "")))
    }

    @Test fun reclassificationCanMoveSpamBackWithoutChangingProviderThread() {
        val spam = SpamThread.classify(42, true, "لغو 11", SpamFilter(true, ""))
        assertEquals(42L, SpamThread.classify(spam, true, "لغو 11", SpamFilter(false, "")))
        assertEquals(spam, SpamThread.classify(spam, true, "لغو 11", SpamFilter(true, "")))
    }

    @Test fun firstProviderThreadDoesNotUseTheReservedMinusOneId() {
        assertEquals(-2L, SpamThread.spamId(1))
        assertEquals(1L, SpamThread.providerId(-2))
    }

    @Test fun missingProviderThreadNeverBecomesASentinelConversation() {
        assertEquals(0L, SpamThread.classify(0, true, "لغو11", SpamFilter(true, "")))
    }

    @Test fun customRuleCanMatchAcrossLineBreaks() {
        assertTrue(SpamFilter(false, "sale.*unsubscribe").matches("sale\nends tonight\nunsubscribe"))
    }
}

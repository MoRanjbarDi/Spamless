package com.moez.QKSMS.util

import org.junit.Assert.*
import org.junit.Test

class SearchSnippetTest {
    @Test fun returnsTheMatchingLine() {
        assertEquals("Your code is 1234", SearchSnippet.create("Header\nYour code is 1234\nFooter", "code"))
    }
    @Test fun retainsMatchesDeepInsideLongMessages() {
        val snippet = SearchSnippet.create("a".repeat(300) + "needle" + "b".repeat(300), "needle")
        assertTrue(snippet.contains("needle"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.length < 100)
    }
    @Test fun caseInsensitiveAndPersianQueriesKeepTheirText() {
        assertEquals("My CODE", SearchSnippet.create("My CODE", "code"))
        assertEquals("پیام لغو ۱۱", SearchSnippet.create("سرآیند\nپیام لغو ۱۱", "لغو"))
    }
    @Test fun matchesAtEitherEdgeDoNotLoseCharacters() {
        assertTrue(SearchSnippet.create("code" + "x".repeat(100), "code").startsWith("code"))
        assertTrue(SearchSnippet.create("x".repeat(100) + "code", "code").endsWith("code"))
    }
}

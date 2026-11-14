package com.moez.QKSMS.util

/**
 * Negative IDs identify the spam half of a provider thread in the app database only.
 * Provider message IDs/URIs stay intact. Never pass these IDs to Telephony thread APIs.
 */
object SpamThread {
    fun providerId(threadId: Long): Long = if (threadId < 0) -threadId - 1 else threadId
    fun spamId(threadId: Long): Long = providerId(threadId).let { if (it > 0) -it - 1 else 0 }
    fun isSpam(threadId: Long): Boolean = threadId < -1
    fun classify(threadId: Long, incomingSms: Boolean, body: String, filter: SpamFilter): Long =
        if (incomingSms && filter.matches(body)) spamId(threadId) else providerId(threadId)
}

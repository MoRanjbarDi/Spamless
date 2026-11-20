package com.moez.QKSMS.repository

import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.util.SpamThread
import io.realm.Realm
import io.realm.Sort

/** Rebuild only touched conversation halves, inside the caller's Realm transaction. */
internal object ConversationPartitions {
    fun update(realm: Realm, threadIds: List<Long>) {
        threadIds.map { SpamThread.providerId(it) }.distinct().forEach { id ->
            val normal = realm.where(Conversation::class.java).equalTo("id", id)
                .findFirst() ?: return@forEach
            val lastNormal = realm.where(Message::class.java).equalTo("deletedAt", 0L).equalTo("threadId", id)
                .sort("date", Sort.DESCENDING).findFirst()
            if (normal.lastMessage != lastNormal) normal.lastMessage = lastNormal
            val spamId = SpamThread.spamId(id)
            val lastSpam = realm.where(Message::class.java).equalTo("deletedAt", 0L).equalTo("threadId", spamId)
                .sort("date", Sort.DESCENDING).findFirst()
            val existing = realm.where(Conversation::class.java).equalTo("id", spamId).findFirst()
            if (lastSpam != null) {
                val spam = existing ?: realm.createObject(Conversation::class.java, spamId)
                if (spam.lastMessage != lastSpam) spam.lastMessage = lastSpam
                if (spam.archived) spam.archived = false
                if (spam.pinned) spam.pinned = false
                if (spam.recipients.map { it.id } != normal.recipients.map { it.id }) {
                    spam.recipients.clear()
                    spam.recipients.addAll(normal.recipients)
                }
                if (spam.name != normal.name) spam.name = normal.name
                if (spam.blocked != normal.blocked) spam.blocked = normal.blocked
                if (spam.blockingClient != normal.blockingClient) spam.blockingClient = normal.blockingClient
                if (spam.blockReason != normal.blockReason) spam.blockReason = normal.blockReason
                if (spam.encryptionKey != normal.encryptionKey) spam.encryptionKey = normal.encryptionKey
                if (spam.encryptionEnabled != normal.encryptionEnabled) spam.encryptionEnabled = normal.encryptionEnabled
                if (spam.encodingSchemeId != normal.encodingSchemeId) spam.encodingSchemeId = normal.encodingSchemeId
                if (spam.legacyEncryptionEnabled != normal.legacyEncryptionEnabled) spam.legacyEncryptionEnabled = normal.legacyEncryptionEnabled
                if (spam.deleteEncryptedAfter != normal.deleteEncryptedAfter) spam.deleteEncryptedAfter = normal.deleteEncryptedAfter
                if (spam.deleteReceivedAfter != normal.deleteReceivedAfter) spam.deleteReceivedAfter = normal.deleteReceivedAfter
                if (spam.deleteSentAfter != normal.deleteSentAfter) spam.deleteSentAfter = normal.deleteSentAfter
            } else {
                if (existing?.lastMessage != null) existing.lastMessage = null
            }
        }
    }

}

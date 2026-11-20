/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.repository

import com.moez.QKSMS.util.SpamThread
import android.content.Context
import com.moez.QKSMS.compat.TelephonyCompat
import com.moez.QKSMS.extensions.anyOf
import com.moez.QKSMS.extensions.asObservable
import com.moez.QKSMS.extensions.map
import com.moez.QKSMS.extensions.removeAccents
import com.moez.QKSMS.filter.ConversationFilter
import com.moez.QKSMS.interactor.SetEncryptionEnabled
import com.moez.QKSMS.mapper.CursorToConversation
import com.moez.QKSMS.mapper.CursorToRecipient
import com.moez.QKSMS.model.Contact
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.model.Recipient
import com.moez.QKSMS.model.SearchResult
import com.moez.QKSMS.util.PhoneNumberUtils
import com.moez.QKSMS.util.tryOrNull
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val conversationFilter: ConversationFilter,
    private val cursorToConversation: CursorToConversation,
    private val cursorToRecipient: CursorToRecipient,
    private val prefs: com.moez.QKSMS.util.Preferences,
    private val phoneNumberUtils: PhoneNumberUtils
) : ConversationRepository {

    override fun getConversations(archived: Boolean, contactsOnly: Boolean?): RealmResults<Conversation> {
        return Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .greaterThan("id", 0L)
                .equalTo("archived", archived)
                .let { query ->
                    when (contactsOnly) {
                        true -> query.isNotEmpty("recipients.contact.lookupKey")
                        false -> query.not().isNotEmpty("recipients.contact.lookupKey")
                        null -> query
                    }
                }
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .beginGroup()
                .isNotNull("lastMessage")
                .or()
                .isNotEmpty("draft")
                .endGroup()
                .sort(
                        arrayOf("pinned", "draft", "lastMessage.date"),
                        arrayOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)
                )
                .findAllAsync()
    }

    override fun getConversationsSnapshot(): List<Conversation> {
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.copyFromRealm(realm.where(Conversation::class.java)
                    .greaterThan("id", 0L)
                    .equalTo("archived", false)
                    .equalTo("blocked", false)
                    .isNotEmpty("recipients")
                    .beginGroup()
                    .isNotNull("lastMessage")
                    .or()
                    .isNotEmpty("draft")
                    .endGroup()
                    .sort(
                            arrayOf("pinned", "draft", "lastMessage.date"),
                            arrayOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)
                    )
                    .findAll())
        }
    }

    override fun getTopConversations(): List<Conversation> {
        return Realm.getDefaultInstance().use { realm ->
            realm.copyFromRealm(realm.where(Conversation::class.java)
                    .greaterThan("id", 0L)
                    .isNotNull("lastMessage")
                    .beginGroup()
                    .equalTo("pinned", true)
                    .or()
                    .greaterThan("lastMessage.date", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                    .endGroup()
                    .equalTo("archived", false)
                    .equalTo("blocked", false)
                    .isNotEmpty("recipients")
                    .findAll())
                    .sortedWith(compareByDescending<Conversation> { conversation -> conversation.pinned }
                            .thenByDescending { conversation ->
                                realm.where(Message::class.java).equalTo("deletedAt", 0L)
                                        .equalTo("threadId", conversation.id)
                                        .greaterThan("date", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                                        .count()
                            })
        }
    }

    override fun setConversationName(id: Long, name: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(Conversation::class.java)
                        .equalTo("id", SpamThread.providerId(id))
                        .findFirst()
                        ?.name = name
            }
        }
        updateConversations(id)
    }

    override fun searchConversations(query: CharSequence, spam: Boolean, archived: Boolean,
                                     contactsOnly: Boolean?): List<SearchResult> {
        val text = query.toString().trim()
        if (text.isEmpty()) return emptyList()
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val threads = realm.where(Conversation::class.java)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .let { if (spam) it.lessThan("id", 0L) else it.greaterThan("id", 0L).equalTo("archived", archived) }
                .let {
                    when (contactsOnly) {
                        true -> it.isNotEmpty("recipients.contact.lookupKey")
                        false -> it.not().isNotEmpty("recipients.contact.lookupKey")
                        null -> it
                    }
                }.findAll()
            val conversations = threads.associateBy { it.id }
            val detached = mutableMapOf<Long, Conversation>()
            if (conversations.isEmpty()) return@use emptyList<SearchResult>()
            val matches = realm.where(Message::class.java).equalTo("deletedAt", 0L)
                .beginGroup()
                .contains("body", text, Case.INSENSITIVE)
                .or().contains("parts.text", text, Case.INSENSITIVE)
                .endGroup()
                .sort("date", Sort.DESCENDING)
                .findAll()
            matches.mapNotNull { message ->
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                conversations[message.threadId]?.let { thread ->
                    val conversation = detached.getOrPut(thread.id) { realm.copyFromRealm(thread, 3) }
                    SearchResult(text, conversation, realm.copyFromRealm(message, 1))
                }
            }
        }
    }

    override fun getBlockedConversations(): RealmResults<Conversation> {
        return Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .equalTo("blocked", true)
                .greaterThan("id", 0L)
                .findAll()
    }

    override fun getBlockedConversationsAsync(): RealmResults<Conversation> {
        return Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .equalTo("blocked", true)
                .isNotNull("lastMessage")
                .greaterThan("id", 0L)
                .findAllAsync()
    }

    override fun getConversationAsync(threadId: Long): Conversation {
        return Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirstAsync()
    }

    override fun getConversation(threadId: Long): Conversation? {
        return Realm.getDefaultInstance()
                .apply { refresh() }
                .where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()
    }

    override fun getConversations(vararg threadIds: Long): RealmResults<Conversation> {
        return Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()
    }

    override fun getUnmanagedConversations(): Observable<List<Conversation>> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Conversation::class.java)
                .sort("lastMessage.date", Sort.DESCENDING)
                .greaterThan("id", 0L)
                .isNotNull("lastMessage")
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .limit(5)
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
                .filter { it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
    }

    override fun getRecipients(): RealmResults<Recipient> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Recipient::class.java)
                .findAll()
    }

    override fun getUnmanagedRecipients(): Observable<List<Recipient>> {
        val realm = Realm.getDefaultInstance()
        return realm.where(Recipient::class.java)
                .isNotNull("contact")
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded && it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
    }

    override fun getRecipient(recipientId: Long): Recipient? {
        return Realm.getDefaultInstance()
                .where(Recipient::class.java)
                .equalTo("id", recipientId)
                .findFirst()
    }

    override fun getThreadId(recipient: String): Long? {
        return getThreadId(listOf(recipient))
    }

    override fun getThreadId(recipients: Collection<String>): Long? {
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                    .greaterThan("id", 0L)
                    .findAll()
                    .asSequence()
                    .filter { conversation -> conversation.recipients.size == recipients.size }
                    .find { conversation ->
                        conversation.recipients.map { it.address }.all { address ->
                            recipients.any { recipient -> phoneNumberUtils.compare(recipient, address) }
                        }
                    }?.id
        }
    }

    override fun getOrCreateConversation(threadId: Long): Conversation? {
        if (threadId < 0) {
            getOrCreateConversation(SpamThread.providerId(threadId)) ?: return null
            updateConversations(threadId)
            return getConversation(threadId)
        }
        return getConversation(threadId) ?: getConversationFromCp(threadId)
    }

    override fun getOrCreateConversation(address: String): Conversation? {
        return getOrCreateConversation(listOf(address))
    }

    override fun getOrCreateConversation(addresses: List<String>): Conversation? {
        if (addresses.isEmpty()) {
            return null
        }

        return (getThreadId(addresses) ?: tryOrNull { TelephonyCompat.getOrCreateThreadId(context, addresses.toSet()) })
                ?.takeIf { threadId -> threadId != 0L }
                ?.let { threadId ->
                    getConversation(threadId)
                            ?.let(Realm.getDefaultInstance()::copyFromRealm)
                            ?: getConversationFromCp(threadId)
                }
    }

    override fun saveDraft(threadId: Long, draft: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val conversation = realm.where(Conversation::class.java)
                    .equalTo("id", threadId)
                    .findFirst()

            realm.executeTransaction {
                conversation?.takeIf { it.isValid }?.draft = draft
            }
        }
    }

    override fun getSpamConversations(): RealmResults<Conversation> = Realm.getDefaultInstance()
        .where(Conversation::class.java)
        .lessThan("id", 0L)
        .equalTo("blocked", false)
        .isNotNull("lastMessage")
        .isNotEmpty("recipients")
        .sort("lastMessage.date", Sort.DESCENDING)
        .findAllAsync()

    override fun markNotSpam(threadIds: List<Long>, messageIds: List<Long>): List<Long> {
        if (threadIds.isEmpty() && messageIds.isEmpty()) return emptyList()
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val selected = if (messageIds.isNotEmpty()) realm.where(Message::class.java)
                .anyOf("id", messageIds.toLongArray()).lessThan("threadId", 0L).findAll()
                else realm.where(Message::class.java).anyOf("threadId", threadIds.toLongArray()).findAll()
            // Persist the override before moving any rows, so a concurrent provider sync honors it.
            if (messageIds.isNotEmpty()) prefs.allowSpamMessages(realm.copyFromRealm(selected, 0))
            else prefs.trustSpamSenders(selected.map { it.address }.distinct())
            val isTrusted = prefs.spamSenderMatcher()
            val move = if (messageIds.isNotEmpty()) selected.createSnapshot() else
                realm.where(Message::class.java).lessThan("threadId", 0L).findAll()
                    .filter { isTrusted(it.address) }
            val affected = move.map { SpamThread.providerId(it.threadId) }.distinct()
            if (affected.isEmpty()) return@use emptyList<Long>()
            realm.executeTransaction {
                move.forEach { message -> message.threadId = SpamThread.providerId(message.threadId) }
                affected.forEach { id ->
                    // The positive metadata row normally exists even for a spam-only sender.
                    if (realm.where(Conversation::class.java).equalTo("id", id).findFirst() == null) {
                        realm.where(Conversation::class.java).equalTo("id", SpamThread.spamId(id)).findFirst()?.let {
                            val normal = realm.copyFromRealm(it)
                            normal.id = id
                            realm.insertOrUpdate(normal)
                        }
                    }
                    realm.where(Conversation::class.java).equalTo("id", id).findFirst()?.archived = false
                }
                ConversationPartitions.update(realm, affected)
            }
            affected.flatMap { listOf(it, SpamThread.spamId(it)) }
        }
    }

    override fun reclassifySpam(): List<Long> {
        val changed = mutableSetOf<Long>()
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            // Scan without a write lock. Contact refresh, deletion and incoming SMS remain writable.
            val candidates = realm.where(Message::class.java).findAll().mapNotNull { message ->
                val target = prefs.classifySpam(message.threadId, message.isSms() && !message.isMe(),
                    message.body, message.address, message.type, message.contentId)
                message.id.takeIf { target != message.threadId }
            }
            if (candidates.isNotEmpty()) realm.executeTransaction {
                realm.where(Message::class.java).anyOf("id", candidates.toLongArray()).findAll().forEach { message ->
                    // Recheck after obtaining the lock; a simultaneous 'not spam' action may have won.
                    val target = prefs.classifySpam(message.threadId, message.isSms() && !message.isMe(),
                        message.body, message.address, message.type, message.contentId)
                    if (target != message.threadId) {
                        changed.add(SpamThread.providerId(message.threadId))
                        message.threadId = target
                    }
                }
                ConversationPartitions.update(realm, changed.toList())
            }
        }
        return changed.flatMap { listOf(it, SpamThread.spamId(it)) }
    }

    override fun updateConversations(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.executeTransaction { ConversationPartitions.update(realm, threadIds.toList()) }
        }
    }

    override fun markArchived(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", threadIds)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { it.archived = true }
            }
        }
    }

    override fun markUnarchived(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", threadIds)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { it.archived = false }
            }
        }
    }

    override fun markPinned(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", threadIds)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { it.pinned = true }
            }
        }
    }

    override fun markUnpinned(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", threadIds)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { it.pinned = false }
            }
        }
    }

    override fun markBlocked(threadIds: List<Long>, blockingClient: Int, blockReason: String?) {
        val ids = threadIds.map { SpamThread.providerId(it) }.toLongArray()
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", ids)
                    .equalTo("blocked", false)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = true
                    conversation.blockingClient = blockingClient
                    conversation.blockReason = blockReason
                }
                ConversationPartitions.update(realm, ids.toList())
            }
        }
    }

    override fun markUnblocked(vararg threadIds: Long) {
        val ids = threadIds.map { SpamThread.providerId(it) }.toLongArray()
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", ids)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = false
                    conversation.blockingClient = null
                    conversation.blockReason = null
                }
                ConversationPartitions.update(realm, ids.toList())
            }
        }
    }

    override fun deleteConversations(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val idsToDelete = realm.where(Message::class.java).anyOf("threadId", threadIds)
                .equalTo("deletedAt", 0L).findAll().map { it.id }.toLongArray()
            MessageDeletion.delete(context, prefs, idsToDelete) { transactionRealm, affected ->
                transactionRealm.where(Conversation::class.java).anyOf("id", threadIds).findAll().forEach { it.draft = "" }
                ConversationPartitions.update(transactionRealm, affected)
            }
            realm.refresh()
            realm.executeTransaction {
                // Keep the normal row as metadata for the Spam partition and future incoming SMS.
                realm.where(Conversation::class.java).anyOf("id", threadIds).findAll().forEach {
                    if (it.draft.isNotEmpty()) it.draft = ""
                }
                threadIds.map(SpamThread::providerId).distinct().forEach { id ->
                    val ids = longArrayOf(id, SpamThread.spamId(id))
                    val remaining = realm.where(Message::class.java).anyOf("threadId", ids).count()
                    val halves = realm.where(Conversation::class.java).anyOf("id", ids).findAll()
                    if (remaining == 0L && halves.all { it.draft.isEmpty() }) halves.deleteAllFromRealm()
                }
            }
        }
        updateConversations(*threadIds)
    }

    override fun setEncryptionKey(threadId: Long, encryptionKey: String) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                    .equalTo("id", SpamThread.providerId(threadId))
                    .findFirst()

            realm.executeTransaction {
                conversation?.encryptionKey = encryptionKey
            }
        }
        updateConversations(threadId)
    }

    override fun setEncryptionEnabled(threadId: Long, enabled: Boolean?) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .equalTo(Conversation::id.name, SpamThread.providerId(threadId))
                .findFirst()

            realm.executeTransaction {
                conversation?.encryptionEnabled = enabled
            }
        }
        updateConversations(threadId)
    }

    override fun hasConversationEncryptionKey(): Boolean {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .isNotEmpty("encryptionKey")
                .findFirst()
            return conversation != null
        }
    }

    override fun setEncodingScheme(threadId: Long, encodingSchemeId: Int) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .equalTo(Conversation::id.name, SpamThread.providerId(threadId))
                .findFirst()

            realm.executeTransaction {
                conversation?.encodingSchemeId = encodingSchemeId
            }
        }
        updateConversations(threadId)
    }

    override fun setLegacyEncryptionEnabled(threadId: Long, enabled: Boolean?) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .equalTo(Conversation::id.name, SpamThread.providerId(threadId))
                .findFirst()

            realm.executeTransaction {
                conversation?.legacyEncryptionEnabled = enabled
            }
        }
        updateConversations(threadId)
    }

    override fun setDeleteEncryptedAfter(threadId: Long, durationId: Int) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                    .equalTo("id", SpamThread.providerId(threadId))
                    .findFirst()

            realm.executeTransaction {
                conversation?.deleteEncryptedAfter = durationId
            }
        }
        updateConversations(threadId)
    }

    override fun setDeleteReceivedAfter(threadId: Long, durationId: Int) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                    .equalTo("id", SpamThread.providerId(threadId))
                    .findFirst()

            realm.executeTransaction {
                conversation?.deleteReceivedAfter = durationId
            }
        }
        updateConversations(threadId)
    }

    override fun setDeleteSentAfter(threadId: Long, durationId: Int) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                    .equalTo("id", SpamThread.providerId(threadId))
                    .findFirst()

            realm.executeTransaction {
                conversation?.deleteSentAfter = durationId
            }
        }
        updateConversations(threadId)
    }

    override fun resetHiddenSettings() {
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java).findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.encryptionKey = ""
                    conversation.deleteEncryptedAfter = 0
                    conversation.deleteReceivedAfter = 0
                    conversation.deleteSentAfter = 0
                }
            }
        }
    }

    /**
     * Returns a [Conversation] from the system SMS ContentProvider, based on the [threadId]
     *
     * It should be noted that even if we have a valid [threadId], that does not guarantee that
     * we can return a [Conversation]. On some devices, the ContentProvider won't return the
     * conversation unless it contains at least 1 message
     */
    private fun getConversationFromCp(threadId: Long): Conversation? {
        return cursorToConversation.getConversationsCursor()
                ?.map(cursorToConversation::map)
                ?.firstOrNull { it.id == threadId }
                ?.let { conversation ->
                    val realm = Realm.getDefaultInstance()
                    val contacts = realm.copyFromRealm(realm.where(Contact::class.java).findAll())
                    val lastMessage = realm.where(Message::class.java).equalTo("deletedAt", 0L).equalTo("threadId", threadId)
                            .sort("date", Sort.DESCENDING).findFirst()?.let(realm::copyFromRealm)

                    val recipients = conversation.recipients
                            .map { recipient -> recipient.id }
                            .map { id -> cursorToRecipient.getRecipientCursor(id) }
                            .mapNotNull { recipientCursor ->
                                // Map the recipient cursor to a list of recipients
                                recipientCursor?.use { recipientCursor.map { cursorToRecipient.map(recipientCursor) } }
                            }
                            .flatten()
                            .map { recipient ->
                                recipient.apply {
                                    contact = contacts.firstOrNull {
                                        it.numbers.any { phoneNumberUtils.compare(recipient.address, it.address) }
                                    }
                                }
                            }

                    conversation.recipients.clear()
                    conversation.recipients.addAll(recipients)
                    conversation.lastMessage = lastMessage
                    realm.executeTransaction { it.insertOrUpdate(conversation) }
                    realm.close()
                    updateConversations(threadId)

                    Realm.getDefaultInstance().use { updatedRealm ->
                        updatedRealm.where(Conversation::class.java).equalTo("id", threadId).findFirst()
                            ?.let(updatedRealm::copyFromRealm) ?: conversation
                    }
                }
    }

}
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
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.Telephony
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.moez.QKSMS.extensions.forEach
import com.moez.QKSMS.extensions.insertOrUpdate
import com.moez.QKSMS.extensions.map
import com.moez.QKSMS.manager.KeyManager
import com.moez.QKSMS.mapper.CursorToContact
import com.moez.QKSMS.mapper.CursorToContactGroup
import com.moez.QKSMS.mapper.CursorToContactGroupMember
import com.moez.QKSMS.mapper.CursorToConversation
import com.moez.QKSMS.mapper.CursorToMessage
import com.moez.QKSMS.mapper.CursorToPart
import com.moez.QKSMS.mapper.CursorToRecipient
import com.moez.QKSMS.model.Contact
import com.moez.QKSMS.model.ContactGroup
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.model.MmsPart
import com.moez.QKSMS.model.PhoneNumber
import com.moez.QKSMS.model.Recipient
import com.moez.QKSMS.model.SyncLog
import com.moez.QKSMS.util.PhoneNumberUtils
import com.moez.QKSMS.util.tryOrNull
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val conversationRepo: ConversationRepository,
    private val cursorToConversation: CursorToConversation,
    private val cursorToMessage: CursorToMessage,
    private val cursorToPart: CursorToPart,
    private val cursorToRecipient: CursorToRecipient,
    private val cursorToContact: CursorToContact,
    private val cursorToContactGroup: CursorToContactGroup,
    private val cursorToContactGroupMember: CursorToContactGroupMember,
    private val keys: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val rxPrefs: RxSharedPreferences
) : SyncRepository {

    override val syncProgress: Subject<SyncRepository.SyncProgress> =
            BehaviorSubject.createDefault(SyncRepository.SyncProgress.Idle)

    override val syncedMessage: Subject<Message> = PublishSubject.create()

    override fun syncMessages() {

        // If the sync is already running, don't try to do another one
        if (syncProgress.blockingFirst() is SyncRepository.SyncProgress.Running) return
        syncProgress.onNext(SyncRepository.SyncProgress.Running(0, 0, true))

        val realm = Realm.getDefaultInstance()
        realm.beginTransaction()

        // Match provider identities, not app IDs, because a full sync rebuilds app IDs.
        val trashed = realm.where(Message::class.java).greaterThan("deletedAt", 0L)
            .findAll().associate { (it.type to it.contentId) to it.deletedAt }
        val persistedData = realm.copyFromRealm(realm.where(Conversation::class.java)
            .greaterThan("id", 0L).findAll()).associateBy { it.id }.toMutableMap()

        realm.delete(Contact::class.java)
        realm.delete(ContactGroup::class.java)
        realm.delete(Conversation::class.java)
        realm.delete(Message::class.java)
        realm.delete(MmsPart::class.java)
        realm.delete(Recipient::class.java)

        keys.reset()

        val partsCursor = cursorToPart.getPartsCursor()
        val messageCursor = cursorToMessage.getMessagesCursor()
        val conversationCursor = cursorToConversation.getConversationsCursor()
        val recipientCursor = cursorToRecipient.getRecipientCursor()

        val max = (partsCursor?.count ?: 0) +
                (messageCursor?.count ?: 0) +
                (conversationCursor?.count ?: 0) +
                (recipientCursor?.count ?: 0)

        var progress = 0

        // Sync message parts
        partsCursor?.use {
            partsCursor.forEach {
                tryOrNull {
                    progress++
                    val part = cursorToPart.map(partsCursor)
                    realm.insertOrUpdate(part)
                }
            }
        }

        // Sync messages
        messageCursor?.use {
            val messageColumns = CursorToMessage.MessageColumns(messageCursor)
            messageCursor.forEach { cursor ->
                tryOrNull {
                    progress++
                    syncProgress.onNext(SyncRepository.SyncProgress.Running(max, progress, false))
                    val message = cursorToMessage.map(Pair(cursor, messageColumns)).apply {
                        deletedAt = trashed[type to contentId] ?: 0L
                        if (isMms()) {
                            parts = RealmList<MmsPart>().apply {
                                addAll(realm.where(MmsPart::class.java)
                                        .equalTo("messageId", contentId)
                                        .findAll())
                            }
                        }
                    }
                    realm.insertOrUpdate(message)
                }
            }
        }

        // Migrate blocked conversations from 2.7.3
        val oldBlockedSenders = rxPrefs.getStringSet("pref_key_blocked_senders")
        oldBlockedSenders.get()
                .map { threadIdString -> threadIdString.toLong() }
                .filter { threadId -> !persistedData.contains(threadId) }
                .forEach { threadId -> persistedData[threadId] = Conversation(id = threadId, blocked = true) }

        // Sync conversations
        conversationCursor?.use {
            conversationCursor.forEach { cursor ->
                tryOrNull {
                    progress++
                    syncProgress.onNext(SyncRepository.SyncProgress.Running(max, progress, false))
                    val conversation = cursorToConversation.map(cursor).apply {
                        persistedData[id]?.let { persistedConversation ->
                            archived = persistedConversation.archived
                            blocked = persistedConversation.blocked
                            pinned = persistedConversation.pinned
                            name = persistedConversation.name
                            blockingClient = persistedConversation.blockingClient
                            blockReason = persistedConversation.blockReason
                            encryptionKey = persistedConversation.encryptionKey
                            encryptionEnabled = persistedConversation.encryptionEnabled
                            legacyEncryptionEnabled = persistedConversation.legacyEncryptionEnabled
                            encodingSchemeId = persistedConversation.encodingSchemeId
                            deleteEncryptedAfter = persistedConversation.deleteEncryptedAfter
                            deleteReceivedAfter = persistedConversation.deleteReceivedAfter
                            deleteSentAfter = persistedConversation.deleteSentAfter
                        }
                        lastMessage = realm.where(Message::class.java).equalTo("deletedAt", 0L)
                                .sort("date", Sort.DESCENDING)
                                .equalTo("threadId", id)
                                .findFirst()
                    }
                    realm.insertOrUpdate(conversation)
                }
            }
        }

        // Sync recipients
        recipientCursor?.use {
            val contacts = realm.copyToRealmOrUpdate(getContacts())
            recipientCursor.forEach { cursor ->
                tryOrNull {
                    progress++
                    syncProgress.onNext(SyncRepository.SyncProgress.Running(max, progress, false))
                    val recipient = cursorToRecipient.map(cursor).apply {
                        contact = contacts.firstOrNull { contact ->
                            contact.numbers.any { phoneNumberUtils.compare(address, it.address) }
                        }
                    }
                    realm.insertOrUpdate(recipient)
                }
            }
        }

        syncProgress.onNext(SyncRepository.SyncProgress.Running(0, 0, true))


        realm.insert(SyncLog())
        // Full imports already classified each message in CursorToMessage; build their folder rows once.
        ConversationPartitions.update(realm, realm.where(Conversation::class.java).greaterThan("id", 0L).findAll().map { it.id })
        realm.commitTransaction()
        realm.close()

        conversationRepo.reclassifySpam()

        // Only delete this after the sync has successfully completed
        oldBlockedSenders.delete()

        syncProgress.onNext(SyncRepository.SyncProgress.Idle)
    }

    override fun syncMessage(uri: Uri): Message? {

        // If we don't have a valid type, return null
        val type = when {
            uri.toString().contains("mms") -> "mms"
            uri.toString().contains("sms") -> "sms"
            else -> return null
        }

        // If we don't have a valid id, return null
        val id = tryOrNull(false) { ContentUris.parseId(uri) } ?: return null

        // Check if the message already exists, so we can reuse the id
        val existingId = Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Message::class.java)
                    .equalTo("type", type)
                    .equalTo("contentId", id)
                    .findFirst()
                    ?.let { it.id to it.deletedAt }
        }

        // The uri might be something like content://mms/inbox/id
        // The box might change though, so we should just use the mms/id uri
        val stableUri = when (type) {
            "mms" -> ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
            else -> ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        }

        return contentResolver.query(stableUri, null, null, null, null)?.use { cursor ->

            // If there are no rows, return null. Otherwise, we've moved to the first row
            if (!cursor.moveToFirst()) return null

            val columnsMap = CursorToMessage.MessageColumns(cursor)
            cursorToMessage.map(Pair(cursor, columnsMap)).apply {
                existingId?.let { this.id = it.first; this.deletedAt = it.second }

                if (isMms()) {
                    parts = RealmList<MmsPart>().apply {
                        addAll(cursorToPart.getPartsCursor(contentId)?.map { cursorToPart.map(it) }.orEmpty())
                    }
                }

                conversationRepo.getOrCreateConversation(SpamThread.providerId(threadId))
                insertOrUpdate()
                conversationRepo.updateConversations(threadId)
                syncedMessage.onNext(this)
            }
        }
    }

    private val contactSyncLock = Any()

    override fun syncContacts() = synchronized(contactSyncLock) {
        // Resolve provider data and phone-number matches before acquiring Realm's write lock.
        val contacts = getContacts()
        val groups = getContactGroups(contacts)
        val byKey = contacts.associateBy { it.lookupKey }
        fun addressKey(address: String): String = android.telephony.PhoneNumberUtils.normalizeNumber(address)
            .takeIf { address.none(Char::isLetter) } ?: address.trim().lowercase(java.util.Locale.ROOT)
        val exact = mutableMapOf<String, Contact>()
        val suffixes = mutableMapOf<String, MutableList<Pair<String, Contact>>>()
        contacts.forEach { contact -> contact.numbers.forEach { number ->
            exact.getOrPut(addressKey(number.address)) { contact }
            val digits = number.address.filter(Char::isDigit)
            if (digits.length >= 7 && number.address.none(Char::isLetter))
                suffixes.getOrPut(digits.takeLast(7)) { mutableListOf() }.add(number.address to contact)
        } }
        fun findContact(address: String): String? {
            exact[addressKey(address)]?.let { return it.lookupKey }
            if (address.any(Char::isLetter)) return null
            val digits = address.filter(Char::isDigit)
            if (digits.length < 7) return null
            return suffixes[digits.takeLast(7)]?.firstOrNull { phoneNumberUtils.compare(address, it.first) }?.second?.lookupKey
        }
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val stored = realm.where(Contact::class.java).findAll().associateBy { it.lookupKey }
            fun sameContact(old: Contact, fresh: Contact): Boolean =
                old.name == fresh.name && old.photoUri == fresh.photoUri && old.starred == fresh.starred &&
                    old.lastUpdate == fresh.lastUpdate && old.numbers.size == fresh.numbers.size &&
                    old.numbers.zip(fresh.numbers).all { (a,b) -> a.id == b.id && a.address == b.address &&
                        a.type == b.type && a.accountType == b.accountType && a.isDefault == b.isDefault }
            val changed = contacts.filter { fresh -> stored[fresh.lookupKey]?.let { !sameContact(it, fresh) } ?: true }
            val removed = stored.keys - byKey.keys
            val recipients = realm.where(Recipient::class.java).findAll()
            val links = recipients.mapNotNull { recipient ->
                val key = findContact(recipient.address)
                if (recipient.contact?.lookupKey != key) recipient.id to key else null
            }
            val oldGroups = realm.where(ContactGroup::class.java).findAll().associateBy { it.id }
            val changedGroups = groups.filter { group -> oldGroups[group.id]?.let { old ->
                old.title != group.title || old.contacts.map { it.lookupKey } != group.contacts.map { it.lookupKey }
            } ?: true }
            val removedGroups = oldGroups.keys - groups.map { it.id }.toSet()
            if (changed.isEmpty() && removed.isEmpty() && links.isEmpty() && changedGroups.isEmpty() && removedGroups.isEmpty()) return@use
            val numberIds = contacts.flatMap { it.numbers }.map { it.id }.toSet()
            realm.executeTransaction {
                changed.forEach { contact ->
                    // Avoid traversing unchanged contacts when only a name/number has changed.
                    realm.insertOrUpdate(contact)
                }
                removed.forEach { stored[it]?.deleteFromRealm() }
                realm.where(PhoneNumber::class.java).findAll().createSnapshot().forEach { number ->
                    if (number.id !in numberIds) number.deleteFromRealm()
                }
                changedGroups.forEach { group ->
                    // Use managed contact references so inserting a group cannot rewrite the contact table.
                    val target = oldGroups[group.id] ?: realm.createObject(ContactGroup::class.java, group.id)
                    target.title = group.title
                    target.contacts.clear()
                    group.contacts.forEach { contact -> realm.where(Contact::class.java).equalTo("lookupKey", contact.lookupKey)
                        .findFirst()?.let { target.contacts.add(it) } }
                }
                removedGroups.forEach { oldGroups[it]?.deleteFromRealm() }
                links.forEach { (id, key) ->
                    realm.where(Recipient::class.java).equalTo("id", id).findFirst()?.contact = key?.let {
                        realm.where(Contact::class.java).equalTo("lookupKey", it).findFirst()
                    }
                }
            }
        }
    }

    private fun getContacts(): List<Contact> {
        val defaultNumberIds = Realm.getDefaultInstance().use { realm ->
            realm.where(PhoneNumber::class.java)
                    .equalTo("isDefault", true)
                    .findAll()
                    .map { number -> number.id }.toSet()
        }

        return cursorToContact.getContactsCursor()
                ?.map { cursor -> cursorToContact.map(cursor) }
                ?.groupBy { contact -> contact.lookupKey }
                ?.map { contacts ->
                    // Sometimes, contacts providers on the phone will create duplicate phone number entries. This
                    // commonly happens with Whatsapp. Let's try to detect these duplicate entries and filter them out
                    val uniqueNumbers = mutableListOf<PhoneNumber>()
                    contacts.value
                            .flatMap { it.numbers }
                            .forEach { number ->
                                number.isDefault = number.id in defaultNumberIds
                                val duplicate = uniqueNumbers.find { other ->
                                    phoneNumberUtils.compare(number.address, other.address)
                                }

                                if (duplicate == null) {
                                    uniqueNumbers += number
                                } else if (!duplicate.isDefault && number.isDefault) {
                                    duplicate.isDefault = true
                                }
                            }

                    contacts.value.first().apply {
                        numbers.clear()
                        numbers.addAll(uniqueNumbers)
                    }
                } ?: listOf()
    }

    private fun getContactGroups(contacts: List<Contact>): List<ContactGroup> {
        val groupMembers = cursorToContactGroupMember.getGroupMembersCursor()
                ?.map(cursorToContactGroupMember::map)
                .orEmpty()

        val groups = cursorToContactGroup.getContactGroupsCursor()
                ?.map(cursorToContactGroup::map)
                .orEmpty()

        val membersByGroup = groupMembers.groupBy { it.groupId }
        val byKey = contacts.associateBy { it.lookupKey }
        groups.forEach { group ->
            group.contacts.addAll(membersByGroup[group.id].orEmpty().mapNotNull { byKey[it.lookupKey] })
        }

        return groups
    }

}

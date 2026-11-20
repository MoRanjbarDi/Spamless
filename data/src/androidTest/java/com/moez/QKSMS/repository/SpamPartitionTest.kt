package com.moez.QKSMS.repository

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.test.mock.MockContentResolver
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.moez.QKSMS.filter.*
import com.moez.QKSMS.interactor.ResetSettings
import com.moez.QKSMS.manager.ActiveConversationManager
import com.moez.QKSMS.manager.KeyManager
import com.moez.QKSMS.mapper.CursorToConversation
import com.moez.QKSMS.mapper.CursorToRecipient
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.model.Recipient
import com.moez.QKSMS.util.PhoneNumberUtils
import com.moez.QKSMS.util.Preferences
import io.reactivex.Observable
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

/** Real Realm queries with an isolated fake SMS provider; never touches the device's messages. */
@RunWith(AndroidJUnit4::class)
class SpamPartitionTest {
    private lateinit var realm: Realm
    private lateinit var previousConfig: RealmConfiguration
    private lateinit var prefs: Preferences
    private lateinit var conversations: ConversationRepositoryImpl
    private lateinit var messages: MessageRepositoryImpl
    private val provider = RecordingSmsProvider()

    @Before fun setUp() {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare()
        val app = InstrumentationRegistry.getTargetContext()
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply {
            authority = "sms"
            exported = true
        })
        val resolver = MockContentResolver().apply { addProvider("sms", provider) }
        val context = object : ContextWrapper(app) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val shared = app.getSharedPreferences("spam_partition_test", 0)
        shared.edit().clear().commit()
        prefs = Preferences(app, RxSharedPreferences.create(shared), shared)
        prefs.trashEnabled.set(false) // These legacy fixtures exercise permanent deletion.
        Realm.init(app)
        previousConfig = Realm.getDefaultConfiguration()!!
        Realm.setDefaultConfiguration(RealmConfiguration.Builder().name("spam-partition-tests")
            .inMemory().build())
        realm = Realm.getDefaultInstance()
        val phones = PhoneNumberUtils(app)
        val phoneFilter = PhoneNumberFilter(phones)
        conversations = ConversationRepositoryImpl(context,
            ConversationFilter(RecipientFilter(ContactFilter(phoneFilter), phoneFilter)),
            mock(CursorToConversation::class.java), mock(CursorToRecipient::class.java), prefs, phones)
        val sync = mock(SyncRepository::class.java)
        `when`(sync.syncedMessage).thenReturn(Observable.never())
        messages = MessageRepositoryImpl(mock(ActiveConversationManager::class.java), context,
            mock(KeyManager::class.java), phones, prefs, sync, conversations, ResetSettings(conversations, prefs))
        realm.executeTransaction {
            val recipient = realm.createObject(Recipient::class.java, 1L).apply { address = "1000" }
            realm.createObject(Conversation::class.java, 42L).recipients.add(recipient)
            addMessage(1, "Your code is 123456", 100)
            addMessage(2, "Offer\nلغو ۱۱", 200)
        }
        conversations.reclassifySpam()
        realm.refresh()
    }

    private fun addMessage(id: Long, body: String, date: Long) {
        realm.createObject(Message::class.java, id).apply {
            threadId = 42
            contentId = id
            this.body = body
            this.date = date
            type = "sms"
            boxId = Telephony.Sms.MESSAGE_TYPE_INBOX
        }
    }

    @After fun tearDown() {
        val count = Realm.getLocalInstanceCount(realm.configuration).toInt()
        repeat(count) { realm.close() }
        Realm.setDefaultConfiguration(previousConfig)
    }

    @Test fun mixedSenderHasSeparateMessagesAndPreviews() {
        assertEquals("Your code is 123456", conversations.getConversation(42)!!.snippet)
        assertEquals("Offer\nلغو ۱۱", conversations.getConversation(-43)!!.snippet)
        assertEquals(listOf(42L), conversations.getConversationsSnapshot().map { it.id })
        assertEquals(listOf(1L), messages.getUnreadMessages(42).map { it.id })
        assertEquals(listOf(2L), messages.getUnreadMessages(-43).map { it.id })
    }

    @Test fun disablingRulesReunitesMessagesAndHidesSpamRow() {
        prefs.iranAdsSpam.set(false)
        conversations.reclassifySpam()
        assertEquals(listOf(1L, 2L), messages.getUnreadMessages(42).map { it.id })
        assertNull(conversations.getConversation(-43)!!.lastMessage)
        assertEquals("Offer\nلغو ۱۱", conversations.getConversation(42)!!.snippet)
    }

    @Test fun readingSpamDoesNotReadNormalMessagesOrWholeProviderThread() {
        messages.markRead(-43)
        assertFalse(messages.getMessage(1)!!.read)
        assertTrue(messages.getMessage(2)!!.read)
        assertEquals(listOf(Uri.parse("content://sms/2")), provider.updated)
    }

    @Test fun deletingSpamPreservesInboxAndUsesOnlyItsMessageUri() {
        conversations.deleteConversations(-43)
        assertNotNull(messages.getMessage(1))
        assertNull(messages.getMessage(2))
        assertEquals("Your code is 123456", conversations.getConversation(42)!!.snippet)
        assertEquals(listOf(Uri.parse("content://sms/2")), provider.deleted)
    }

    @Test fun deletingInboxPreservesSpam() {
        conversations.deleteConversations(42)
        assertNull(messages.getMessage(1))
        assertNotNull(messages.getMessage(2))
        assertEquals("Offer\nلغو ۱۱", conversations.getConversation(-43)!!.snippet)
        assertEquals(listOf(Uri.parse("content://sms/1")), provider.deleted)
    }

    @Test fun spamDoesNotUnarchiveTheNormalHalf() {
        conversations.markArchived(42)
        realm.executeTransaction { addMessage(3, "Another offer لغو11", 300) }
        conversations.reclassifySpam()
        assertTrue(conversations.getConversation(42)!!.archived)
        assertFalse(conversations.getConversation(-43)!!.archived)
        assertEquals(3L, conversations.getConversation(-43)!!.lastMessage!!.id)
    }

    @Test fun searchReturnsIndividualMessagesInNewestFirstOrder() {
        realm.executeTransaction { addMessage(3, "Another code is 654321", 300) }
        conversations.reclassifySpam()
        val results = conversations.searchConversations("code")
        assertEquals(listOf(3L, 1L), results.map { it.message.id })
        assertTrue(results.all { it.conversation.id == 42L })
        assertTrue(results.all { it.message.body.contains("code") })
    }

    @Test fun spamSearchDoesNotLeakNormalMessagesOrArchivedScope() {
        assertTrue(conversations.searchConversations("Offer").isEmpty())
        assertEquals(listOf(2L), conversations.searchConversations("Offer", spam = true).map { it.message.id })
        conversations.markArchived(42)
        assertTrue(conversations.searchConversations("code").isEmpty())
        assertEquals(listOf(1L), conversations.searchConversations("code", archived = true).map { it.message.id })
    }

    @Test fun contactTabsAreDisjointEvenWithMixedGroupRecipients() {
        val nonContacts = conversations.getConversations(contactsOnly = false).apply { load() }
        val contacts = conversations.getConversations(contactsOnly = true).apply { load() }
        assertEquals(listOf(42L), nonContacts.map { it.id })
        assertTrue(contacts.isEmpty())
        realm.executeTransaction {
            val contact = realm.createObject(com.moez.QKSMS.model.Contact::class.java, "saved-contact")
            val recipient = realm.createObject(Recipient::class.java, 2L)
            recipient.address = "2000"
            recipient.contact = contact
            realm.where(Conversation::class.java).equalTo("id", 42L).findFirst()!!.recipients.add(recipient)
        }
        assertEquals(listOf(42L), contacts.map { it.id })
        assertTrue(nonContacts.isEmpty())
        assertEquals(listOf(1L), conversations.searchConversations("code", contactsOnly = true).map { it.message.id })
        assertTrue(conversations.searchConversations("code", contactsOnly = false).isEmpty())
    }

    @Test fun combinedViewDoesNotMoveMessagesOrChangeDefaultQueries() {
        val combined = messages.getMessages(42, includeSpam = true).apply { load() }
        assertEquals(listOf(1L, 2L), combined.map { it.id })
        val normal = messages.getMessages(42).apply { load() }
        assertEquals(listOf(1L), normal.map { it.id })
        assertEquals(-43L, messages.getMessage(2)!!.threadId)
        assertEquals("Your code is 123456", conversations.getConversation(42)!!.snippet)
        assertFalse(prefs.showSpamInThread(42).get())
        prefs.showSpamInThread(42).set(true)
        assertTrue(prefs.showSpamInThread(-43).get())
    }

    @Test fun spamRetentionDeletesOnlyExpiredSpamUsingItsProviderUri() {
        val now = System.currentTimeMillis()
        realm.executeTransaction {
            addMessage(3, "A recent normal message", now)
            addMessage(4, "A recent offer لغو11", now)
        }
        conversations.reclassifySpam()
        assertEquals(mapOf(-43L to 1), messages.getOldMessageCounts(30, spamOnly = true))
        messages.deleteOldMessages(30, spamOnly = true)
        conversations.updateConversations(-43)
        assertNotNull(messages.getMessage(1))
        assertNull(messages.getMessage(2))
        assertNotNull(messages.getMessage(3))
        assertNotNull(messages.getMessage(4))
        assertEquals(listOf(Uri.parse("content://sms/2")), provider.deleted)
    }

    @Test fun forkDefaultsUseBlueDeliveryReportsAndNoAutomaticSpamDeletion() {
        assertEquals(0xFF2196F3.toInt(), prefs.theme().get().toInt())
        assertTrue(prefs.delivery.get())
        assertEquals(0, prefs.spamAutoDelete.get().toInt())
    }

    @Test fun allInboxIncludesSavedAndUnsavedSendersWithoutIncludingSpam() {
        realm.executeTransaction {
            val contact = realm.createObject(com.moez.QKSMS.model.Contact::class.java, "saved-contact")
            val person = realm.createObject(Recipient::class.java, 3L).apply { address = "3000"; this.contact = contact }
            val message = realm.createObject(Message::class.java, 3L).apply {
                threadId = 84; body = "Your code is 7890"; date = 300; type = "sms"
                boxId = Telephony.Sms.MESSAGE_TYPE_INBOX
            }
            realm.createObject(Conversation::class.java, 84L).apply { recipients.add(person); lastMessage = message }
        }
        val all = conversations.getConversations().apply { load() }
        val saved = conversations.getConversations(contactsOnly = true).apply { load() }
        val others = conversations.getConversations(contactsOnly = false).apply { load() }
        assertEquals(setOf(42L, 84L), all.map { it.id }.toSet())
        assertEquals(listOf(84L), saved.map { it.id })
        assertEquals(listOf(42L), others.map { it.id })
        assertEquals(listOf(3L, 1L), conversations.searchConversations("code").map { it.message.id })
    }

    private class RecordingSmsProvider : ContentProvider() {
        val updated = mutableListOf<Uri>()
        val deleted = mutableListOf<Uri>()
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int { updated.add(uri); return 1 }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted.add(uri); return 1
        }
    }
}

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
        val app = InstrumentationRegistry.getTargetContext()
        val resolver = MockContentResolver().apply { addProvider("sms", provider) }
        val context = object : ContextWrapper(app) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val shared = app.getSharedPreferences("spam_partition_test", 0)
        shared.edit().clear().commit()
        prefs = Preferences(app, RxSharedPreferences.create(shared), shared)
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

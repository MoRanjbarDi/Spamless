package com.moez.QKSMS.common.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.moez.QKSMS.manager.PermissionManager
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.model.Recipient
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.repository.MessageRepository
import com.moez.QKSMS.util.PhoneNumberUtils
import com.moez.QKSMS.util.Preferences
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

/** Posts only synthetic notifications with isolated preferences and an in-memory database. */
@RunWith(AndroidJUnit4::class)
class SpamNotificationTest {
    private lateinit var realm: Realm
    private lateinit var prefs: Preferences
    private lateinit var system: NotificationManager
    private lateinit var notifications: NotificationManagerImpl
    private lateinit var messages: MessageRepository
    private lateinit var sounds: SpamSoundPlayer

    @Before fun setUp() {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare()
        val app = InstrumentationRegistry.getTargetContext()
        val shared = app.getSharedPreferences("spam_notification_test", 0)
        shared.edit().clear().commit()
        prefs = Preferences(app, RxSharedPreferences.create(shared), shared)
        prefs.qkreply.set(false)
        prefs.spamArrivalSound.set(0)
        system = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        system.cancelAll()
        Realm.init(app)
        realm = Realm.getInstance(RealmConfiguration.Builder().name("spam-notification-tests").inMemory().build())
        realm.executeTransaction {
            listOf(-43L, 42L).forEachIndexed { index, thread ->
                realm.createObject(Message::class.java, index.toLong() + 1).apply {
                    threadId = thread
                    address = "15551234567"
                    body = "Synthetic notification test"
                    type = "sms"
                    boxId = Telephony.Sms.MESSAGE_TYPE_INBOX
                    date = System.currentTimeMillis()
                }
            }
        }
        val conversations = mock(ConversationRepository::class.java)
        messages = mock(MessageRepository::class.java)
        listOf(-43L, 42L).forEach { thread ->
            val result = realm.where(Message::class.java).equalTo("threadId", thread).findAll()
            val conversation = Conversation(id = thread, lastMessage = result.first())
            conversation.recipients.add(Recipient(id = 1, address = "15551234567"))
            `when`(conversations.getConversation(thread)).thenReturn(conversation)
            `when`(messages.getUnreadUnseenMessages(thread)).thenReturn(result)
        }
        val phones = PhoneNumberUtils(app)
        sounds = mock(SpamSoundPlayer::class.java)
        notifications = NotificationManagerImpl(app, Colors(app, phones, prefs), conversations, prefs,
            messages, mock(PermissionManager::class.java), phones, sounds)
    }

    @After fun tearDown() {
        system.cancelAll()
        realm.close()
    }

    private fun waitForNotification(id: Int): android.service.notification.StatusBarNotification? {
        repeat(30) {
            val posted = system.activeNotifications.firstOrNull { it.id == id }
            if (posted != null) return posted
            Thread.sleep(100)
        }
        return null
    }

    @Test fun offIsDefaultAndDoesNotQueryOrPostSpam() {
        assertEquals(0, prefs.spamNotifications.get().toInt())
        notifications.update(-43)
        verify(messages, never()).getUnreadUnseenMessages(-43)
        assertTrue(system.activeNotifications.none { it.id == -43 })
        notifications.update(42)
        assertNotNull(waitForNotification(42))
    }

    @Test fun hiddenNotificationsStillPlayTheSelectedSoundOnArrival() {
        prefs.spamArrivalSound.set(1)
        notifications.update(-43, alert = true)
        verify(sounds).play(android.net.Uri.parse("android.resource://${InstrumentationRegistry.getTargetContext().packageName}/raw/spam_beep"))
        assertTrue(system.activeNotifications.none { it.id == -43 })
    }

    @Test fun hiddenNotificationsRespectSilentAndCustomSoundChoices() {
        prefs.spamArrivalSound.set(0)
        notifications.update(-43, alert = true)
        verifyZeroInteractions(sounds)
        prefs.spamArrivalSound.set(2)
        val chosen = "content://settings/system/notification_sound"
        prefs.ringtone(42).set(chosen)
        notifications.update(-43, alert = true)
        verify(sounds).play(android.net.Uri.parse(chosen))
        assertTrue(system.activeNotifications.none { it.id == -43 })
    }

    @Test fun temporarySpamNotificationExpiresAfterTenSeconds() {
        if (Build.VERSION.SDK_INT < 26) return
        prefs.spamNotifications.set(1)
        notifications.update(-43)
        val posted = waitForNotification(-43)
        assertNotNull(posted)
        assertEquals(10_000L, posted!!.notification.timeoutAfter)
        val deadline = System.currentTimeMillis() + 12_000L
        while (system.activeNotifications.any { it.id == -43 } && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        assertTrue(system.activeNotifications.none { it.id == -43 })
    }

    @Test fun onKeepsNotificationAndOffClearsAnExistingOne() {
        prefs.spamNotifications.set(2)
        notifications.update(-43)
        val posted = waitForNotification(-43)
        assertNotNull(posted)
        if (Build.VERSION.SDK_INT >= 26) assertEquals(0L, posted!!.notification.timeoutAfter)
        prefs.spamNotifications.set(0)
        notifications.update(-43)
        Thread.sleep(300)
        assertTrue(system.activeNotifications.none { it.id == -43 })
    }
}

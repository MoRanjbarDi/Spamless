package com.moez.QKSMS.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.PendingIntents
import com.moez.QKSMS.extensions.anyOf
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.receiver.DeleteMessagesReceiver
import com.moez.QKSMS.receiver.SendSmsReceiver
import com.moez.QKSMS.service.AutoDeleteService
import com.moez.QKSMS.util.Preferences
import io.realm.Realm
import java.util.concurrent.TimeUnit

/** Shared deletion policy for individual, conversation, notification and automatic deletions. */
internal object MessageDeletion {
    // Only primitive identifiers cross a transaction boundary; never copy message/attachment graphs.
    private data class Row(val id: Long, val contentId: Long, val type: String, val threadId: Long)
    fun delete(context: Context, prefs: Preferences, ids: LongArray,
               updateThreads: (Realm, List<Long>) -> Unit = { _, _ -> }): List<Long> {
        if (ids.isEmpty()) return emptyList()
        if (!prefs.trashEnabled.get()) {
            val affected = permanentlyDelete(context, ids, activeOnly = true)
            Realm.getDefaultInstance().use { realm -> realm.executeTransaction { updateThreads(realm, affected) } }
            return affected
        }
        val rows = Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val messages = realm.where(Message::class.java).anyOf("id", ids)
                .equalTo("deletedAt", 0L).findAll()
            val records = messages.map { Row(it.id, it.contentId, it.type, it.threadId) }
            if (records.isNotEmpty()) realm.executeTransaction {
                messages.setBoolean("read", true)
                messages.setBoolean("seen", true)
                // This field changes membership of 'messages', so write it last.
                messages.setLong("deletedAt", System.currentTimeMillis())
                // Publish inbox/Spam removal in this same commit, before any slow system IPC.
                updateThreads(realm, records.map { it.threadId }.distinct())
            }
            records
        }
        rows.forEach { cancelAlarms(context, it.id) }
        rows.groupBy { it.type }.forEach { (type, messages) ->
            val uri = if (type == "mms") android.provider.Telephony.Mms.CONTENT_URI else android.provider.Telephony.Sms.CONTENT_URI
            messages.chunked(250).forEach { batch ->
                try {
                    context.contentResolver.update(uri, ContentValues().apply { put("read", 1); put("seen", 1) },
                        "_id IN (${batch.joinToString(",") { "?" }})", batch.map { it.contentId.toString() }.toTypedArray())
                } catch (error: Exception) { timber.log.Timber.w(error, "Unable to mark trashed messages read in provider") }
            }
        }
        return rows.map { it.threadId }.distinct()
    }

    fun restore(prefs: Preferences, ids: LongArray): List<Long> {
        if (ids.isEmpty()) return emptyList()
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(prefs.trashRetentionDays.get().coerceAtLeast(1).toLong())
            val messages = realm.where(Message::class.java).anyOf("id", ids)
                .greaterThan("deletedAt", cutoff).greaterThan("deletedAt", 0L).findAll()
            val threads = messages.map { it.threadId }.distinct()
            realm.executeTransaction { messages.setLong("deletedAt", 0L) }
            return@use threads
        }
    }

    fun purgeExpired(context: Context, prefs: Preferences): List<Long> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(prefs.trashRetentionDays.get().coerceAtLeast(1).toLong())
        val ids = Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Message::class.java).greaterThan("deletedAt", 0L)
                .lessThanOrEqualTo("deletedAt", cutoff).findAll().map { it.id }.toLongArray()
        }
        return permanentlyDelete(context, ids, trashOnly = true, deletedBefore = cutoff)
    }

    fun permanentlyDelete(context: Context, ids: LongArray, trashOnly: Boolean = false, deletedBefore: Long? = null, activeOnly: Boolean = false): List<Long> {
        if (ids.isEmpty() || android.provider.Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return emptyList()
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val query = realm.where(Message::class.java).anyOf("id", ids)
            if (trashOnly) query.greaterThan("deletedAt", 0L)
            if (activeOnly) query.equalTo("deletedAt", 0L)
            if (deletedBefore != null) query.lessThanOrEqualTo("deletedAt", deletedBefore)
            val rows = query.findAll().map { Row(it.id, it.contentId, it.type, it.threadId) }
            val affected = mutableSetOf<Long>()
            rows.groupBy { it.type }.forEach { (type, messages) ->
                val uri = if (type == "mms") android.provider.Telephony.Mms.CONTENT_URI else android.provider.Telephony.Sms.CONTENT_URI
                messages.chunked(250).forEach { batch ->
                    // Serialize against Restore so a successful restore can never be purged afterwards.
                    realm.executeTransaction {
                        val pending = realm.where(Message::class.java).anyOf("id", batch.map { it.id }.toLongArray())
                        if (trashOnly) pending.greaterThan("deletedAt", 0L)
                        if (activeOnly) pending.equalTo("deletedAt", 0L)
                        if (deletedBefore != null) pending.lessThanOrEqualTo("deletedAt", deletedBefore)
                        val records = pending.findAll()
                        if (records.isNotEmpty()) {
                            try {
                                context.contentResolver.delete(uri, "_id IN (${records.joinToString(",") { "?" }})",
                                    records.map { it.contentId.toString() }.toTypedArray())
                                val selected = records.createSnapshot()
                                selected.forEach { message ->
                                    affected.add(message.threadId)
                                    message.parts.deleteAllFromRealm()
                                }
                                records.deleteAllFromRealm()
                            } catch (error: Exception) { timber.log.Timber.w(error, "Unable to permanently delete message batch") }
                        }
                    }
                    batch.forEach { cancelAlarms(context, it.id) }
                }
            }
            affected.toList()
        }
    }

    private fun cancelAlarms(context: Context, id: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(SendSmsReceiver::class.java, DeleteMessagesReceiver::class.java).forEach { receiver ->
            PendingIntent.getBroadcast(context, id.toInt(), Intent(context, receiver),
                PendingIntents.immutable(PendingIntent.FLAG_NO_CREATE))?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
        }
    }
}

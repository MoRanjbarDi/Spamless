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

import android.content.Context
import android.os.Environment
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import com.moez.QKSMS.model.BackupFile
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.util.Preferences
import com.moez.QKSMS.util.QkFileObserver
import com.moez.QKSMS.util.tryOrNull
import com.squareup.moshi.Moshi
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import io.realm.Realm
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val context: Context,
    private val moshi: Moshi,
    private val prefs: Preferences,
    private val syncRepo: SyncRepository
) : BackupRepository {

    companion object {
        private val BACKUP_DIRECTORY = Environment.getExternalStorageDirectory().toString() + "/Spamless/Backups"
        private val LEGACY_BACKUP_DIRECTORY = Environment.getExternalStorageDirectory().toString() + "/QKSMS/Backups"
    }

    data class Backup(
        val messageCount: Int = 0,
        val messages: List<BackupMessage> = listOf()
    )

    /**
     * Simpler version of [Backup] which allows us to read certain fields from the backup without
     * needing to parse the entire file
     */
    data class BackupMetadata(
        val messageCount: Int = 0
    )

    data class BackupMessage(
        val type: Int,
        val address: String,
        val date: Long,
        val dateSent: Long,
        val read: Boolean,
        val status: Int,
        val body: String,
        val protocol: Int,
        val serviceCenter: String?,
        val locked: Boolean,
        val subId: Int
    )

    // Subjects to emit our progress events to
    private val backupProgress: Subject<BackupRepository.Progress> =
            BehaviorSubject.createDefault(BackupRepository.Progress.Idle())
    private val restoreProgress: Subject<BackupRepository.Progress> =
            BehaviorSubject.createDefault(BackupRepository.Progress.Idle())

    private val backupFilesChanged = BehaviorSubject.createDefault(Unit)
    @Volatile private var stopFlag: Boolean = false

    override fun performBackup() {
        // If a backup or restore is already running, don't do anything
        if (isBackupOrRestoreRunning()) return

        var messageCount = 0

        // Map all the messages into our object we'll use for the Json mapping
        val backupMessages = Realm.getDefaultInstance().use { realm ->
            // Get the messages from realm
            val messages = realm.where(Message::class.java).equalTo("deletedAt", 0L).sort("date").findAll().createSnapshot()
            messageCount = messages.size

            // Map the messages to the new format
            messages.mapIndexed { index, message ->
                // Update the progress
                backupProgress.onNext(BackupRepository.Progress.Running(messageCount, index))
                messageToBackupMessage(message)
            }
        }

        // Update the status, and set the progress to be indeterminate since we can no longer calculate progress
        backupProgress.onNext(BackupRepository.Progress.Saving())

        // Convert the data to json
        val adapter = moshi.adapter(Backup::class.java).indent("\t")
        val json = adapter.toJson(Backup(messageCount, backupMessages)).toByteArray()

        try {
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(System.currentTimeMillis())
            val name = "backup-$timestamp.json"
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // Shared Downloads survive uninstall without broad file-access permission.
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Spamless/Backups/")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw java.io.IOException("Unable to create backup")
                try {
                    (context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Unable to open backup"))
                        .use { it.write(json) }
                    values.clear(); values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                } catch (error: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                val dir = File(BACKUP_DIRECTORY).apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { it.write(json) }
            }
            backupFilesChanged.onNext(Unit)
        } catch (e: Exception) {
            Timber.w(e)
        }

        // Mark the task finished, and set it as Idle a second later
        backupProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { backupProgress.onNext(BackupRepository.Progress.Idle()) }
    }

    private fun messageToBackupMessage(message: Message): BackupMessage = BackupMessage(
            type = message.boxId,
            address = message.address,
            date = message.date,
            dateSent = message.dateSent,
            read = message.read,
            status = message.deliveryStatus,
            body = message.body,
            protocol = 0,
            serviceCenter = null,
            locked = message.locked,
            subId = message.subId
    )

    override fun getBackupProgress(): Observable<BackupRepository.Progress> = backupProgress

    override fun getBackups(): Observable<List<BackupFile>> {
        if (android.os.Build.VERSION.SDK_INT >= 30) return backupFilesChanged
            .observeOn(Schedulers.io()).map { readSharedBackups() }
        return Observable.merge(
            QkFileObserver(BACKUP_DIRECTORY).observable,
            QkFileObserver(LEGACY_BACKUP_DIRECTORY).observable)
            .map { (File(BACKUP_DIRECTORY).listFiles() ?: emptyArray()) +
                (File(LEGACY_BACKUP_DIRECTORY).listFiles() ?: emptyArray()) }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .map { files ->
                files.mapNotNull { file ->
                    val adapter = moshi.adapter(BackupMetadata::class.java)
                    val backup = tryOrNull(false) {
                        file.source().buffer().use(adapter::fromJson)
                    } ?: return@mapNotNull null

                    val path = file.path
                    val date = file.lastModified()
                    val messages = backup.messageCount
                    val size = file.length()
                    BackupFile(path, date, messages, size)
                }
            }
            .map { files -> files.sortedByDescending { file -> file.date } }

    }

    @android.annotation.TargetApi(30)
    private fun readSharedBackups(): List<BackupFile> {
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val columns = arrayOf(android.provider.MediaStore.Downloads._ID,
            android.provider.MediaStore.Downloads.DATE_MODIFIED, android.provider.MediaStore.Downloads.SIZE)
        val rows = mutableListOf<BackupFile>()
        context.contentResolver.query(collection, columns,
            "${android.provider.MediaStore.Downloads.RELATIVE_PATH} = ? AND ${android.provider.MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf("Download/Spamless/Backups/"), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
                val metadata = tryOrNull(false) {
                    context.contentResolver.openInputStream(uri)?.source()?.buffer()?.use {
                        moshi.adapter(BackupMetadata::class.java).fromJson(it)
                    }
                } ?: continue
                rows.add(BackupFile(uri.toString(), cursor.getLong(1) * 1000, metadata.messageCount, cursor.getLong(2)))
            }
        }
        return rows.sortedByDescending { it.date }
    }

    override fun performRestore(filePath: String) {
        // If a backupFile or restore is already running, don't do anything
        if (isBackupOrRestoreRunning()) return

        restoreProgress.onNext(BackupRepository.Progress.Parsing())

        val input = if (filePath.startsWith("content://")) {
            context.contentResolver.openInputStream(android.net.Uri.parse(filePath))?.source()
                ?: throw java.io.IOException("Unable to open backup")
        } else File(filePath).source()
        val backup = input.buffer().use { source ->
            moshi.adapter(Backup::class.java).fromJson(source)
        }

        val messageCount = backup?.messages?.size ?: 0
        var errorCount = 0

        backup?.messages?.forEachIndexed { index, message ->
            if (stopFlag) {
                stopFlag = false
                restoreProgress.onNext(BackupRepository.Progress.Idle())
                return
            }

            // Update the progress
            restoreProgress.onNext(BackupRepository.Progress.Running(messageCount, index))

            try {
                val values = contentValuesOf(
                        Telephony.Sms.TYPE to message.type,
                        Telephony.Sms.ADDRESS to message.address,
                        Telephony.Sms.DATE to message.date,
                        Telephony.Sms.DATE_SENT to message.dateSent,
                        Telephony.Sms.READ to message.read,
                        Telephony.Sms.SEEN to 1,
                        Telephony.Sms.STATUS to message.status,
                        Telephony.Sms.BODY to message.body,
                        Telephony.Sms.PROTOCOL to message.protocol,
                        Telephony.Sms.SERVICE_CENTER to message.serviceCenter,
                        Telephony.Sms.LOCKED to message.locked
                )

                if (prefs.canUseSubId.get()) {
                    values.put(Telephony.Sms.SUBSCRIPTION_ID, message.subId)
                }

                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (e: Exception) {
                Timber.w(e)
                errorCount++
            }
        }

        if (errorCount > 0) {
            Timber.w(Exception("Failed to backup $errorCount/$messageCount messages"))
        }

        // Sync the messages
        restoreProgress.onNext(BackupRepository.Progress.Syncing())
        syncRepo.syncMessages()

        // Mark the task finished, and set it as Idle a second later
        restoreProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { restoreProgress.onNext(BackupRepository.Progress.Idle()) }
    }

    override fun stopRestore() {
        stopFlag = true
    }

    override fun getRestoreProgress(): Observable<BackupRepository.Progress> = restoreProgress

    private fun isBackupOrRestoreRunning(): Boolean {
        return backupProgress.blockingFirst().running || restoreProgress.blockingFirst().running
    }

}
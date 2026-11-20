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
package com.moez.QKSMS.interactor

import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.repository.MessageRepository
import com.moez.QKSMS.util.Preferences
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class DeleteOldMessages @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val prefs: Preferences,
    private val notificationManager: com.moez.QKSMS.manager.NotificationManager,
    private val updateBadge: UpdateBadge,
    private val permissions: com.moez.QKSMS.manager.PermissionManager
) : Interactor<Unit>() {

    override fun buildObservable(params: Unit): Flowable<*> = Flowable.fromCallable {
        if (!permissions.isDefaultSms() || !permissions.hasReadSms()) return@fromCallable
        val affected = messageRepo.purgeExpiredTrash().toMutableSet()
        listOf(prefs.autoDelete.get() to false, prefs.spamAutoDelete.get() to true)
            .filter { it.first > 0 }.forEach { (days, spamOnly) ->
                val counts = messageRepo.getOldMessageCounts(days, spamOnly)
                affected.addAll(counts.keys)
                messageRepo.deleteOldMessages(days, spamOnly)
            }
        if (affected.isNotEmpty()) {
            conversationRepo.updateConversations(*affected.toLongArray())
            affected.forEach { notificationManager.update(it) }
            updateBadge.execute(Unit)
        }
    }

}

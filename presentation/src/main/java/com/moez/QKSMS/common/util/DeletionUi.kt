package com.moez.QKSMS.common.util

import android.content.Context
import com.moez.QKSMS.R
import com.moez.QKSMS.util.Preferences

object DeletionUi {
    fun title(prefs: Preferences) = if (prefs.trashEnabled.get()) R.string.trash_move_title else R.string.dialog_delete_title
    fun button(prefs: Preferences) = if (prefs.trashEnabled.get()) R.string.trash_move else R.string.button_delete
    fun conversations(context: Context, prefs: Preferences, count: Int): String =
        if (prefs.trashEnabled.get()) context.resources.getQuantityString(R.plurals.trash_move_conversations,
            count, count, prefs.trashRetentionDays.get())
        else context.resources.getQuantityString(R.plurals.dialog_delete_message, count, count)
}

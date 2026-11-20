package com.moez.QKSMS.feature.trash

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.interactor.DeleteOldMessages
import com.moez.QKSMS.interactor.UpdateBadge
import com.moez.QKSMS.manager.NotificationManager
import com.moez.QKSMS.manager.PermissionManager
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.util.SpamThread
import dagger.android.AndroidInjection
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Recoverable messages remain separate from active inbox, search, notifications and backups. */
class TrashActivity : QkThemedActivity() {
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var notifications: NotificationManager
    @Inject lateinit var updateBadge: UpdateBadge
    @Inject lateinit var cleanup: DeleteOldMessages
    private val work = CompositeDisposable()
    private lateinit var realm: Realm
    private lateinit var messages: RealmResults<Message>
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private var busy = false
    private var rows: List<TrashRow> = emptyList()
    private val adapter = TrashAdapter { row -> showMessage(row) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_activity)
        setTitle(R.string.title_trash)
        showBackButton(true)
        list = findViewById(R.id.trashList)
        empty = findViewById(R.id.trashEmpty)
        progress = findViewById(R.id.trashProgress)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        findViewById<TextView>(R.id.trashInfo).text = getString(R.string.trash_info, prefs.trashRetentionDays.get())
        realm = Realm.getDefaultInstance()
        messages = realm.where(Message::class.java).greaterThan("deletedAt", 0L)
            .sort("deletedAt", Sort.DESCENDING).findAllAsync()
        messages.addChangeListener { _: RealmResults<Message> -> renderMessages() }
        renderMessages()
    }

    override fun onResume() {
        super.onResume()
        if (permissionManager.isDefaultSms() && permissionManager.hasReadSms()) {
            runWork { cleanup.buildObservable(Unit).blockingSubscribe() }
        }
    }

    private fun renderMessages() {
        if (!messages.isLoaded || !messages.isValid) return
        val retention = TimeUnit.DAYS.toMillis(prefs.trashRetentionDays.get().coerceAtLeast(1).toLong())
        rows = messages.map { message ->
            val conversation = realm.where(Conversation::class.java).equalTo("id", SpamThread.providerId(message.threadId)).findFirst()
            val sender = conversation?.recipients?.firstOrNull { phoneNumberUtils.compare(it.address, message.address) }
                ?.getDisplayName() ?: message.address
            TrashRow(TrashKey(message.type, message.contentId), sender, message.getSummary(), message.date, message.deletedAt + retention)
        }
        adapter.rows = rows
        empty.isVisible = rows.isEmpty()
        list.isVisible = rows.isNotEmpty()
        invalidateOptionsMenu()
    }

    private fun showMessage(row: TrashRow) {
        if (busy) return
        AlertDialog.Builder(this).setTitle(row.sender)
            .setMessage(row.body + "\n\n" + expiryLabel(row.expiresAt))
            .setPositiveButton(R.string.trash_restore) { _, _ -> restore(listOf(row.key)) }
            .setNegativeButton(R.string.trash_delete_permanently) { _, _ -> confirmDelete(listOf(row.key)) }
            .setNeutralButton(android.R.string.cancel, null).show()
    }

    private fun restore(keys: List<TrashKey>) {
        if (!requireDefaultSms()) return
        runWork {
            val affected = messageRepo.restoreMessages(*resolveKeys(keys))
            affected.forEach { notifications.update(it) }
            updateBadge.buildObservable(Unit).blockingSubscribe()
        }
    }

    private fun confirmDelete(keys: List<TrashKey>) {
        if (keys.isEmpty() || busy) return
        AlertDialog.Builder(this).setTitle(R.string.trash_delete_permanently)
            .setMessage(resources.getQuantityString(R.plurals.trash_delete_confirmation, keys.size, keys.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                if (requireDefaultSms()) runWork {
                    val affected = messageRepo.permanentlyDeleteMessages(*resolveKeys(keys))
                    affected.forEach { notifications.update(it) }
                    updateBadge.buildObservable(Unit).blockingSubscribe()
                }
            }.show()
    }

    // App IDs are rebuilt during a full sync. Resolve stable provider keys when the
    // action runs, so an open dialog cannot act on a different message after sync.
    private fun resolveKeys(keys: List<TrashKey>): LongArray = Realm.getDefaultInstance().use { db ->
        db.refresh()
        keys.mapNotNull { key -> db.where(Message::class.java).equalTo("type", key.type)
            .equalTo("contentId", key.contentId).greaterThan("deletedAt", 0L).findFirst()?.id }.toLongArray()
    }

    private fun requireDefaultSms(): Boolean {
        if (permissionManager.isDefaultSms() && permissionManager.hasReadSms()) return true
        navigator.showDefaultSmsDialog(this)
        return false
    }

    private fun runWork(action: () -> Unit) {
        if (busy) return
        busy = true
        progress.isVisible = true
        invalidateOptionsMenu()
        work.add(Completable.fromAction(action).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ finishWork() }, { error ->
                timber.log.Timber.w(error, "Unable to update Trash")
                Toast.makeText(this, R.string.trash_error, Toast.LENGTH_LONG).show()
                finishWork()
            }))
    }

    private fun finishWork() {
        busy = false
        progress.isVisible = false
        realm.refresh()
        renderMessages()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        if (menu == null) return false
        menu.add(0, R.id.trashRestoreAll, 0, R.string.trash_restore_all)
        menu.add(0, R.id.trashEmptyAll, 1, R.string.trash_empty_all)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.trashRestoreAll)?.isEnabled = rows.isNotEmpty() && !busy
        menu.findItem(R.id.trashEmptyAll)?.isEnabled = rows.isNotEmpty() && !busy
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.trashRestoreAll -> { restore(rows.map { it.key }); true }
        R.id.trashEmptyAll -> { confirmDelete(rows.map { it.key }); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun expiryLabel(expiresAt: Long): String = getString(R.string.trash_expires,
        DateUtils.getRelativeTimeSpanString(expiresAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))

    override fun onDestroy() {
        work.dispose()
        if (::messages.isInitialized) messages.removeAllChangeListeners()
        if (::realm.isInitialized) realm.close()
        super.onDestroy()
    }

    private data class TrashKey(val type: String, val contentId: Long)

    private data class TrashRow(val key: TrashKey, val sender: String, val body: String, val date: Long, val expiresAt: Long)

    private inner class TrashAdapter(val open: (TrashRow) -> Unit) : RecyclerView.Adapter<TrashHolder>() {
        var rows: List<TrashRow> = emptyList()
            set(value) { field = value; notifyDataSetChanged() }
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TrashHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.trash_list_item, parent, false))
        override fun onBindViewHolder(holder: TrashHolder, position: Int) {
            val row = rows[position]
            holder.sender.text = row.sender
            holder.body.text = row.body
            holder.expiry.text = DateUtils.formatDateTime(this@TrashActivity, row.date,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME) + " · " + expiryLabel(row.expiresAt)
            holder.itemView.setOnClickListener { open(row) }
        }
    }

    private class TrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.trashSender)
        val body: TextView = view.findViewById(R.id.trashBody)
        val expiry: TextView = view.findViewById(R.id.trashExpiry)
    }
}

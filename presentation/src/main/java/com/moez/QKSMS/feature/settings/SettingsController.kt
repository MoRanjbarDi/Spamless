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
package com.moez.QKSMS.feature.settings

import android.animation.ObjectAnimator
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import com.moez.QKSMS.BuildConfig
import com.moez.QKSMS.R
import com.moez.QKSMS.common.MenuItem
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.QkChangeHandler
import com.moez.QKSMS.common.QkDialog
import com.moez.QKSMS.common.base.QkController
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.extensions.animateLayoutChanges
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.common.widget.KeyInputDialog
import com.moez.QKSMS.common.widget.PreferenceView
import com.moez.QKSMS.common.widget.TextInputDialog
import com.moez.QKSMS.feature.settings.about.AboutController
import com.moez.QKSMS.feature.settings.autodelete.AutoDeleteDialog
import com.moez.QKSMS.feature.settings.swipe.SwipeActionsController
import com.moez.QKSMS.feature.themepicker.ThemePickerController
import com.moez.QKSMS.injection.appComponent
import com.moez.QKSMS.repository.SyncRepository
import com.moez.QKSMS.util.Preferences
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.android.synthetic.main.settings_controller.*
import kotlinx.android.synthetic.main.settings_controller.view.*
import kotlinx.android.synthetic.main.settings_switch_widget.view.*
import kotlinx.android.synthetic.main.settings_theme_widget.*
import javax.inject.Inject
import kotlin.coroutines.resume

class SettingsController : QkController<SettingsView, SettingsState, SettingsPresenter>(), SettingsView {

    @Inject lateinit var context: Context
    @Inject lateinit var colors: Colors
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var nightModeDialog: QkDialog
    @Inject lateinit var textSizeDialog: QkDialog
    @Inject lateinit var sendDelayDialog: QkDialog
    @Inject lateinit var mmsSizeDialog: QkDialog
    @Inject lateinit var deleteEncryptedAfterDialog: QkDialog
    @Inject lateinit var prefs: Preferences


    @Inject override lateinit var presenter: SettingsPresenter

    private val signatureDialog: TextInputDialog by lazy {
        TextInputDialog(activity!!, context.getString(R.string.settings_signature_title), signatureSubject::onNext)
    }
    private val autoDeleteDialog: AutoDeleteDialog by lazy {
        AutoDeleteDialog(activity!!, autoDeleteSubject::onNext)
    }

    private val encryptionKeyDialog: KeyInputDialog by lazy {
        KeyInputDialog(activity!!, context.getString(R.string.conversation_encryption_key_title), globalEncryptionKeySubject::onNext)
    }

    private val smsForResetDialog: TextInputDialog by lazy {
        TextInputDialog(activity!!, context.getString(R.string.sms_for_reset), smsForResetSubject::onNext)
    }

    private val viewQksmsPlusSubject: Subject<Unit> = PublishSubject.create()
    private val startTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val endTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val signatureSubject: Subject<String> = PublishSubject.create()
    private val autoDeleteSubject: Subject<Int> = PublishSubject.create()
    private val spamAutoDeleteSubject: Subject<Int> = PublishSubject.create()
    private val spamAutoDeleteDialog: AutoDeleteDialog by lazy {
        AutoDeleteDialog(activity!!, spamAutoDeleteSubject::onNext, R.string.spam_auto_delete,
            R.string.spam_auto_delete_dialog_message)
    }
    // partisan
    private val globalEncryptionKeySubject: Subject<String> = PublishSubject.create()
    private val smsForResetSubject: Subject<String> = PublishSubject.create()

    private val progressAnimator by lazy { ObjectAnimator.ofInt(syncingProgress, "progress", 0, 0) }

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
        layoutRes = R.layout.settings_controller

        colors.themeObservable()
                .autoDisposable(scope())
                .subscribe { activity?.recreate() }
    }

    private fun renderSpamSettings() {
        spamVibration.checkbox.isChecked = prefs.spamVibration.get()
        iranAdsSpam.checkbox.isChecked = prefs.iranAdsSpam.get()
        customSpamRegex.summary = prefs.customSpamRegex.get().takeIf { it.isNotBlank() }
            ?: context.getString(R.string.spam_custom_regex_summary)
        spamNotifications.summary = context.resources.getStringArray(R.array.spam_notification_modes)[
            prefs.spamNotifications.get().coerceIn(0, 2)]
        spamArrivalSound.summary = context.resources.getStringArray(R.array.spam_arrival_sounds)[
            prefs.spamArrivalSound.get().coerceIn(0, 2)]
    }

    private fun showSpamRegexDialog() {
        val input = android.widget.EditText(activity!!).apply {
            setText(prefs.customSpamRegex.get())
            setSingleLine(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextDirection(View.TEXT_DIRECTION_LTR)
        }
        val dialog = AlertDialog.Builder(activity!!)
            .setTitle(R.string.spam_custom_regex)
            .setMessage(R.string.spam_custom_regex_summary)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val regex = input.text.toString()
                val error = com.moez.QKSMS.util.SpamFilter.validationError(regex)
                if (error != null) {
                    input.error = context.getString(R.string.spam_regex_error, error)
                } else {
                    prefs.customSpamRegex.set(regex)
                    renderSpamSettings()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onViewCreated() {
        preferences.postDelayed({ preferences?.animateLayoutChanges = true }, 100)

        when (Build.VERSION.SDK_INT >= 29) {
            true -> nightModeDialog.adapter.setData(R.array.night_modes)
            false -> nightModeDialog.adapter.data = context.resources.getStringArray(R.array.night_modes)
                    .mapIndexed { index, title -> MenuItem(title, index) }
                    .drop(1)
        }
        textSizeDialog.adapter.setData(R.array.text_sizes)
        sendDelayDialog.adapter.setData(R.array.delayed_sending_labels)
        mmsSizeDialog.adapter.setData(R.array.mms_sizes, R.array.mms_sizes_ids)
        deleteEncryptedAfterDialog.adapter.setData(R.array.delete_message_after_labels)

        renderSpamSettings()
        iranAdsSpam.setOnClickListener {
            prefs.iranAdsSpam.set(!prefs.iranAdsSpam.get())
            renderSpamSettings()
        }
        customSpamRegex.setOnClickListener { showSpamRegexDialog() }
        spamNotifications.setOnClickListener {
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.spam_notifications)
                .setSingleChoiceItems(R.array.spam_notification_modes, prefs.spamNotifications.get().coerceIn(0, 2)) { dialog, which ->
                    prefs.spamNotifications.set(which)
                    // Apply the new policy immediately to notifications that are already visible.
                    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (Build.VERSION.SDK_INT < 23) io.realm.Realm.getDefaultInstance().use { realm ->
                        realm.where(com.moez.QKSMS.model.Conversation::class.java).lessThan("id", 0L)
                            .findAll().forEach {
                                if (which == 0) manager.cancel(it.id.toInt())
                                com.moez.QKSMS.receiver.SpamNotificationTimeoutReceiver.setTimeout(context, it.id, which == 1)
                            }
                    }
                    if (Build.VERSION.SDK_INT >= 23) manager.activeNotifications
                        .filter { it.id < -1 }.forEach { posted ->
                            com.moez.QKSMS.receiver.SpamNotificationTimeoutReceiver.setTimeout(context, posted.id.toLong(), which == 1)
                            if (which == 0) manager.cancel(posted.id)
                            else if (Build.VERSION.SDK_INT >= 26) {
                                val notification = android.app.Notification.Builder.recoverBuilder(context, posted.notification)
                                    .setOnlyAlertOnce(true)
                                    .setTimeoutAfter(if (which == 1) 10_000L else 0L).build()
                                manager.notify(posted.id, notification)
                            }
                        }
                    renderSpamSettings()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        spamVibration.setOnClickListener {
            prefs.spamVibration.set(!prefs.spamVibration.get())
            renderSpamSettings()
        }
        spamArrivalSound.setOnClickListener {
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.spam_arrival_sound)
                .setSingleChoiceItems(R.array.spam_arrival_sounds, prefs.spamArrivalSound.get()) { dialog, which ->
                    prefs.spamArrivalSound.set(which)
                    renderSpamSettings()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        trashEnabled.setOnClickListener { prefs.trashEnabled.set(!prefs.trashEnabled.get()) }
        trashRetention.setOnClickListener { showTrashRetentionDialog() }
        defaultInboxTab.setOnClickListener {
            AlertDialog.Builder(activity!!).setTitle(R.string.settings_default_inbox)
                .setSingleChoiceItems(R.array.default_inbox_tabs, prefs.defaultInboxTab.get().coerceIn(0, 2)) { dialog, index ->
                    prefs.defaultInboxTab.set(index)
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        about.summary = context.getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    private fun showTrashRetentionDialog() {
        val field = android.widget.EditText(activity!!).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.settings_auto_delete_dialog_hint)
            setText(prefs.trashRetentionDays.get().toString())
            selectAll()
        }
        val dialog = AlertDialog.Builder(activity!!).setTitle(R.string.trash_retention)
            .setMessage(R.string.trash_retention_message).setView(field)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val days = field.text.toString().toIntOrNull()
                if (days == null || days !in 1..3650) {
                    field.error = context.getString(R.string.trash_retention_error)
                    return@setOnClickListener
                }
                if (days < prefs.trashRetentionDays.get()) {
                    AlertDialog.Builder(activity!!).setTitle(R.string.trash_retention)
                        .setMessage(R.string.trash_shorter_warning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.button_save) { _, _ ->
                            prefs.trashRetentionDays.set(days)
                            dialog.dismiss()
                        }.show()
                } else {
                    prefs.trashRetentionDays.set(days)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.title_settings)
        showBackButton(true)
    }

    override fun preferenceClicks(): Observable<PreferenceView> = (0 until preferences.childCount)
            .map { index -> preferences.getChildAt(index) }
            .mapNotNull { view -> view as? PreferenceView }
            .filter { it.id !in setOf(R.id.spamVibration, R.id.iranAdsSpam, R.id.customSpamRegex, R.id.spamArrivalSound, R.id.spamNotifications, R.id.trashEnabled, R.id.trashRetention, R.id.defaultInboxTab) }
            .map { preference -> preference.clicks().map { preference } }
            .let { preferences -> Observable.merge(preferences) }

    override fun aboutLongClicks(): Observable<*> = about.longClicks()

    override fun viewQksmsPlusClicks(): Observable<*> = viewQksmsPlusSubject

    override fun nightModeSelected(): Observable<Int> = nightModeDialog.adapter.menuItemClicks

    override fun nightStartSelected(): Observable<Pair<Int, Int>> = startTimeSelectedSubject

    override fun nightEndSelected(): Observable<Pair<Int, Int>> = endTimeSelectedSubject

    override fun textSizeSelected(): Observable<Int> = textSizeDialog.adapter.menuItemClicks

    override fun sendDelaySelected(): Observable<Int> = sendDelayDialog.adapter.menuItemClicks

    override fun signatureChanged(): Observable<String> = signatureSubject

    override fun autoDeleteChanged(): Observable<Int> = autoDeleteSubject
    override fun spamAutoDeleteChanged(): Observable<Int> = spamAutoDeleteSubject

    override fun mmsSizeSelected(): Observable<Int> = mmsSizeDialog.adapter.menuItemClicks

    // partisan
    override fun globalEncryptionKeySet(): Observable<String> = globalEncryptionKeySubject

    override fun smsForResetSet(): Observable<String> = smsForResetSubject

    override fun deleteEncryptedAfterSelected(): Observable<Int> = deleteEncryptedAfterDialog.adapter.menuItemClicks

    override fun render(state: SettingsState) {
        trashEnabled.checkbox.isChecked = state.trashEnabled
        trashRetention.summary = context.resources.getQuantityString(R.plurals.trash_retention_days,
            state.trashRetentionDays, state.trashRetentionDays)
        defaultInboxTab.summary = context.resources.getStringArray(R.array.default_inbox_tabs)[state.defaultInboxTab]
        themePreview.setBackgroundTint(state.theme)
        night.summary = state.nightModeSummary
        nightModeDialog.adapter.selectedItem = state.nightModeId
        nightStart.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        nightStart.summary = state.nightStart
        nightEnd.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        nightEnd.summary = state.nightEnd


        autoEmoji.checkbox.isChecked = state.autoEmojiEnabled

        delayed.summary = state.sendDelaySummary
        sendDelayDialog.adapter.selectedItem = state.sendDelayId

        delivery.checkbox.isChecked = state.deliveryEnabled

        signature.summary = state.signature.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.settings_signature_summary)

        textSize.summary = state.textSizeSummary
        textSizeDialog.adapter.selectedItem = state.textSizeId

        autoColor.checkbox.isChecked = state.autoColor

        systemFont.checkbox.isChecked = state.systemFontEnabled

        unicode.checkbox.isChecked = state.stripUnicodeEnabled
        mobileOnly.checkbox.isChecked = state.mobileOnly

        autoDelete.summary = when (state.autoDelete) {
            0 -> context.getString(R.string.settings_auto_delete_never)
            else -> context.resources.getQuantityString(
                    R.plurals.settings_auto_delete_summary, state.autoDelete, state.autoDelete)
        }

        spamAutoDelete.summary = when (state.spamAutoDelete) {
            0 -> context.getString(R.string.settings_auto_delete_never)
            else -> context.resources.getQuantityString(R.plurals.settings_auto_delete_summary,
                state.spamAutoDelete, state.spamAutoDelete)
        }

        longAsMms.checkbox.isChecked = state.longAsMms

        mmsSize.summary = state.maxMmsSizeSummary
        mmsSizeDialog.adapter.selectedItem = state.maxMmsSizeId

        when (state.syncProgress) {
            is SyncRepository.SyncProgress.Idle -> syncingProgress.isVisible = false

            is SyncRepository.SyncProgress.Running -> {
                syncingProgress.isVisible = true
                syncingProgress.max = state.syncProgress.max
                progressAnimator.apply { setIntValues(syncingProgress.progress, state.syncProgress.progress) }.start()
                syncingProgress.isIndeterminate = state.syncProgress.indeterminate
            }
        }

        // partisan
        globalEncryptionKey.summary = if (state.globalEncryptionKey.isNotEmpty()) "***" else ""

        deleteEncryptedAfter.isVisible = state.globalEncryptionKey.isNotEmpty()
        deleteEncryptedAfter.summary = state.deleteEncryptedAfterSummary
        deleteEncryptedAfterDialog.adapter.selectedItem = state.deleteEncryptedAfterId

        smsForReset.summary = state.smsForReset

        showInTaskSwitcher.checkbox.isChecked = state.showInTaskSwitcher

        if (state.showInTaskSwitcher) {
            activity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity!!.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun showQksmsPlusSnackbar() {
        view?.run {
            Snackbar.make(contentView, R.string.toast_qksms_plus, Snackbar.LENGTH_LONG).run {
                setAction(R.string.button_more) { viewQksmsPlusSubject.onNext(Unit) }
                setActionTextColor(colors.theme().theme)
                show()
            }
        }
    }

    // TODO change this to a PopupWindow
    override fun showNightModeDialog() = nightModeDialog.show(activity!!)

    override fun showStartTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            startTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showEndTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            endTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showTextSizePicker() = textSizeDialog.show(activity!!)

    override fun showDelayDurationDialog() = sendDelayDialog.show(activity!!)

    override fun showSignatureDialog(signature: String) = signatureDialog.setText(signature).show()

    override fun showAutoDeleteDialog(days: Int) = autoDeleteDialog.setExpiry(days).show()
    override fun showSpamAutoDeleteDialog(days: Int) = spamAutoDeleteDialog.setExpiry(days).show()

    override suspend fun showAutoDeleteWarningDialog(messages: Int): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Boolean> { cont ->
            AlertDialog.Builder(activity!!)
                    .setTitle(R.string.settings_auto_delete_warning)
                    .setMessage(if (prefs.trashEnabled.get()) context.resources.getQuantityString(
                        R.plurals.trash_move_messages, messages, messages, prefs.trashRetentionDays.get())
                        else context.getString(R.string.settings_auto_delete_warning_message, messages))
                    .setOnCancelListener { cont.resume(false) }
                    .setNegativeButton(R.string.button_cancel) { _, _ -> cont.resume(false) }
                    .setPositiveButton(R.string.button_yes) { _, _ -> cont.resume(true) }
                    .show()
        }
    }

    override fun showMmsSizePicker() = mmsSizeDialog.show(activity!!)

    override fun showSwipeActions() {
        router.pushController(RouterTransaction.with(SwipeActionsController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showThemePicker() {
        router.pushController(RouterTransaction.with(ThemePickerController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showAbout() {
        router.pushController(RouterTransaction.with(AboutController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showGlobalEncryptionKeySettings() = navigator.showGlobalKeysSettings()

    override fun showSmsForResetDialog(smsForReset: String) = smsForResetDialog.setText(smsForReset).show()

    override fun showDeleteEncryptedAfterDialog() = deleteEncryptedAfterDialog.show(activity!!)

}

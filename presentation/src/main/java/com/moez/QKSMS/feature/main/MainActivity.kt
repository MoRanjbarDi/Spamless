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
package com.moez.QKSMS.feature.main

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.androidxcompat.drawerOpen
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.extensions.autoScrollToStart
import com.moez.QKSMS.common.util.extensions.dismissKeyboard
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.scrapViews
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.common.widget.KeyInputDialog
import com.moez.QKSMS.feature.blocking.BlockingDialog
import com.moez.QKSMS.feature.changelog.ChangelogDialog
import com.moez.QKSMS.feature.conversations.ConversationItemTouchCallback
import com.moez.QKSMS.feature.conversations.ConversationsAdapter
import com.moez.QKSMS.manager.ChangelogManager
import com.moez.QKSMS.repository.SyncRepository
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.drawer_view.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_permission_hint.*
import kotlinx.android.synthetic.main.main_syncing.*
import javax.inject.Inject

class MainActivity : QkThemedActivity(), MainView {

    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var disposables: CompositeDisposable
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var conversationsAdapter: ConversationsAdapter
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var searchAdapter: SearchAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val refreshIntent: Subject<Unit> = PublishSubject.create()
    override val inboxTabIntent: Subject<InboxTab> = PublishSubject.create()
    override val onNewIntentIntent: Subject<Intent> = PublishSubject.create()
    override val activityResumedIntent: Subject<Boolean> = PublishSubject.create()
    override val showGenerateKeyIntent: Subject<Boolean> = PublishSubject.create()
    override val queryChangedIntent by lazy { toolbarSearch.textChanges() }
    override val composeIntent by lazy { compose.clicks() }
    override val drawerOpenIntent: Observable<Boolean> by lazy {
        drawerLayout
                .drawerOpen(Gravity.START)
                .doOnNext { dismissKeyboard() }
    }
    override val homeIntent: Subject<Unit> = PublishSubject.create()
    override val navigationIntent: Observable<NavItem> by lazy {
        Observable.merge(listOf(
                backPressedSubject,
                inbox.clicks().map { NavItem.INBOX },
                archived.clicks().map { NavItem.ARCHIVED },
                spam.clicks().map { NavItem.SPAM },
                trash.clicks().map { NavItem.TRASH },
                backup.clicks().map { NavItem.BACKUP },
                scheduled.clicks().map { NavItem.SCHEDULED },
                blocking.clicks().map { NavItem.BLOCKING },
                settings.clicks().map { NavItem.SETTINGS },
                plus.clicks().map { NavItem.PLUS },
                help.clicks().map { NavItem.HELP },
                invite.clicks().map { NavItem.INVITE }))
    }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val plusBannerIntent by lazy { plusBanner.clicks() }
    override val dismissRatingIntent by lazy { rateDismiss.clicks() }
    override val rateIntent by lazy { rateOkay.clicks() }
    override val conversationsSelectedIntent by lazy { conversationsAdapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val swipeConversationIntent by lazy { itemTouchCallback.swipes }
    override val changelogMoreIntent by lazy { changelogDialog.moreClicks }
    override val undoArchiveIntent: Subject<Unit> = PublishSubject.create()
    override val snackbarButtonIntent: Subject<Unit> = PublishSubject.create()

    private var renderingTabs = false
    private var requestingBasicPermissions = false

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[MainViewModel::class.java] }
    private val toggle by lazy { ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.main_drawer_open_cd, 0) }
    private val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }
    private val progressAnimator by lazy { ObjectAnimator.ofInt(syncingProgress, "progress", 0, 0) }
    private val changelogDialog by lazy { ChangelogDialog(this) }
    private val snackbar by lazy { findViewById<View>(R.id.snackbar) }
    private val syncing by lazy { findViewById<View>(R.id.syncing) }
    private val backPressedSubject: Subject<NavItem> = PublishSubject.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        inboxTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (!renderingTabs) inboxTabIntent.onNext(InboxTab.values()[tab.position])
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        inboxRefresh.setOnRefreshListener { refreshIntent.onNext(Unit) }
        inboxRefresh.setOnChildScrollUpCallback { _, _ ->
            recyclerView.visibility == View.VISIBLE && recyclerView.canScrollVertically(-1)
        }
        viewModel.bindView(this)
        onNewIntentIntent.onNext(intent)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        (snackbar as? ViewStub)?.setOnInflateListener { _, _ ->
            snackbarButton.clicks()
                    .autoDisposable(scope(Lifecycle.Event.ON_DESTROY))
                    .subscribe(snackbarButtonIntent)
        }

        (syncing as? ViewStub)?.setOnInflateListener { _, _ ->
            syncingProgress?.progressTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
            syncingProgress?.indeterminateTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
        }

        toggle.syncState()
        toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            homeIntent.onNext(Unit)
        }

        itemTouchCallback.adapter = conversationsAdapter
        conversationsAdapter.autoScrollToStart(recyclerView)

        // Don't allow clicks to pass through the drawer layout
        drawer.clicks().autoDisposable(scope()).subscribe()

        // Set the theme color tint to the recyclerView, progressbar, and FAB
        theme
                .autoDisposable(scope())
                .subscribe { theme ->
                    // Set the color for the drawer icons
                    val states = arrayOf(
                            intArrayOf(android.R.attr.state_activated),
                            intArrayOf(-android.R.attr.state_activated))

                    resolveThemeColor(android.R.attr.textColorSecondary)
                            .let { textSecondary -> ColorStateList(states, intArrayOf(theme.theme, textSecondary)) }
                            .let { tintList ->
                                inboxIcon.imageTintList = tintList
                                archivedIcon.imageTintList = tintList
                                spamIcon.imageTintList = tintList
                            }

                    // Miscellaneous views
                    listOf(plusBadge1, plusBadge2).forEach { badge ->
                        badge.setBackgroundTint(theme.theme)
                        badge.setTextColor(theme.textPrimary)
                    }
                    syncingProgress?.progressTintList = ColorStateList.valueOf(theme.theme)
                    syncingProgress?.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    plusIcon.setTint(theme.theme)
                    rateIcon.setTint(theme.theme)
                    compose.setBackgroundTint(theme.theme)
                    inboxRefresh.setColorSchemeColors(theme.theme)
                    inboxTabs.setSelectedTabIndicatorColor(theme.theme)
                    inboxTabs.setTabTextColors(resolveThemeColor(android.R.attr.textColorSecondary), theme.theme)

                    // Set the FAB compose icon color
                    compose.setTint(theme.textPrimary)
                }

        // These theme attributes don't apply themselves on API 21
        if (Build.VERSION.SDK_INT <= 22) {
            toolbarSearch.setBackgroundTint(resolveThemeColor(R.attr.bubbleColor))
        }
        if (prefs.globalEncryptionKey.get().isEmpty() && !conversationRepo.hasConversationEncryptionKey()) {
            Snackbar.make(drawerLayout, R.string.global_key_isnt_set, Snackbar.LENGTH_LONG)
                .setAction(R.string.global_key_set) { navigator.showGlobalKeysSettings() }
                .show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.run(onNewIntentIntent::onNext)
    }

    override fun render(state: MainState) {
        if (state.hasError) {
            finish()
            return
        }

        val addContact = when (state.page) {
            is Inbox -> state.page.addContact
            is Archived -> state.page.addContact
            is Spam -> state.page.addContact
            else -> false
        }

        val markPinned = when (state.page) {
            is Inbox -> state.page.markPinned
            is Archived -> state.page.markPinned
            is Spam -> state.page.markPinned
            else -> true
        }

        val markRead = when (state.page) {
            is Inbox -> state.page.markRead
            is Archived -> state.page.markRead
            is Spam -> state.page.markRead
            else -> true
        }

        val selectedConversations = when (state.page) {
            is Inbox -> state.page.selected
            is Archived -> state.page.selected
            is Spam -> state.page.selected
            else -> 0
        }

        toolbarSearch.setVisible((state.page is Inbox || state.page is Spam || state.page is Archived)
            && selectedConversations == 0 || state.page is Searching)
        toolbarSearch.setHint(if (state.page is Spam || (state.page as? Searching)?.spam == true)
            R.string.spam_search_hint else R.string.search_messages_hint)
        inboxTabs.setVisible(state.page is Inbox || (state.page as? Searching)?.let { !it.spam && !it.archived } == true)
        if (inboxTabs.selectedTabPosition != state.inboxTab.ordinal) {
            renderingTabs = true
            inboxTabs.getTabAt(state.inboxTab.ordinal)?.select()
            renderingTabs = false
        }
        toolbar.menu.findItem(R.id.select_all)?.isVisible = state.page is Spam
        toolbarTitle.setVisible(toolbarSearch.visibility != View.VISIBLE)

        toolbar.menu.findItem(R.id.archive)?.isVisible = state.page is Inbox && selectedConversations != 0
        toolbar.menu.findItem(R.id.unarchive)?.isVisible = state.page is Archived && selectedConversations != 0
        toolbar.menu.findItem(R.id.delete)?.isVisible = selectedConversations != 0
        toolbar.menu.findItem(R.id.add)?.isVisible = addContact && selectedConversations != 0
        toolbar.menu.findItem(R.id.pin)?.isVisible = state.page !is Spam && markPinned && selectedConversations != 0
        toolbar.menu.findItem(R.id.unpin)?.isVisible = state.page !is Spam && !markPinned && selectedConversations != 0
        toolbar.menu.findItem(R.id.read)?.isVisible = markRead && selectedConversations != 0
        toolbar.menu.findItem(R.id.unread)?.isVisible = !markRead && selectedConversations != 0
        toolbar.menu.findItem(R.id.not_spam_sender)?.isVisible = state.page is Spam && selectedConversations != 0
        toolbar.menu.findItem(R.id.block)?.isVisible = state.page !is Spam && selectedConversations != 0

        listOf(plusBadge1, plusBadge2).forEach { badge ->
            badge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        }
        plus.isVisible = false // Kept for a future donation UI.
        plusBanner.isVisible = false
        invite.isVisible = false
        rateLayout.setVisible(false)
        inboxRefresh.isEnabled = (state.page is Inbox || state.page is Spam || state.page is Archived) && selectedConversations == 0
        inboxRefresh.isRefreshing = state.refreshing

        compose.setVisible(state.page is Inbox || state.page is Archived)
        conversationsAdapter.emptyView = empty.takeIf { state.page is Inbox || state.page is Archived || state.page is Spam }
        searchAdapter.emptyView = empty.takeIf { state.page is Searching }

        when (state.page) {
            is Inbox -> {
                showBackButton(state.page.selected > 0)
                title = getString(R.string.main_title_selected, state.page.selected)
                if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(recyclerView)
                empty.setText(R.string.inbox_empty_text)
            }

            is Searching -> {
                showBackButton(true)
                if (recyclerView.adapter !== searchAdapter) recyclerView.adapter = searchAdapter
                searchAdapter.data = state.page.data ?: listOf()
                itemTouchHelper.attachToRecyclerView(null)
                empty.setText(if (state.page.loading) R.string.searching_messages else R.string.inbox_search_empty_text)
            }

            is Archived -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(null)
                empty.setText(R.string.archived_empty_text)
            }

            is Spam -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_spam)
                }
                if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(null)
                empty.setText(R.string.spam_empty_text)
            }
        }

        spam.isActivated = state.page is Spam || (state.page as? Searching)?.spam == true
        inbox.isActivated = state.page is Inbox || (state.page as? Searching)?.let { !it.spam && !it.archived } == true
        archived.isActivated = state.page is Archived || (state.page as? Searching)?.archived == true

        if (drawerLayout.isDrawerOpen(GravityCompat.START) && !state.drawerOpen) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (!drawerLayout.isDrawerVisible(GravityCompat.START) && state.drawerOpen) {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        when (state.syncing) {
            is SyncRepository.SyncProgress.Idle -> {
                syncing.isVisible = false
                snackbar.isVisible = !state.defaultSms || !state.smsPermission || !state.contactPermission
            }

            is SyncRepository.SyncProgress.Running -> {
                syncing.isVisible = true
                syncingProgress.max = state.syncing.max
                progressAnimator.apply { setIntValues(syncingProgress.progress, state.syncing.progress) }.start()
                syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbar.isVisible = false
            }
        }

        when {
            !state.defaultSms -> {
                snackbarTitle?.setText(R.string.main_default_sms_title)
                snackbarMessage?.setText(R.string.main_default_sms_message)
                snackbarButton?.setText(R.string.main_default_sms_change)
            }

            !state.smsPermission -> {
                snackbarTitle?.setText(R.string.main_permission_required)
                snackbarMessage?.setText(R.string.main_permission_sms)
                snackbarButton?.setText(R.string.main_permission_allow)
            }

            !state.contactPermission -> {
                snackbarTitle?.setText(R.string.main_permission_required)
                snackbarMessage?.setText(R.string.main_permission_contacts)
                snackbarButton?.setText(R.string.main_permission_allow)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumedIntent.onNext(true)
        window.decorView.post { requestNotificationPermissionIfNeeded() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !requestingBasicPermissions && !isFinishing &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val permissionPrefs = getSharedPreferences("runtime_permissions", MODE_PRIVATE)
            if (!permissionPrefs.getBoolean("notificationsRequested", false)) {
                permissionPrefs.edit().putBoolean("notificationsRequested", true).apply()
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            requestingBasicPermissions = false
            window.decorView.post { requestNotificationPermissionIfNeeded() }
        }
    }

    override fun onPause() {
        super.onPause()
        activityResumedIntent.onNext(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    override fun showBackButton(show: Boolean) {
        toggle.onDrawerSlide(drawer, if (show) 1f else 0f)
        toggle.drawerArrowDrawable.color = when (show) {
            true -> resolveThemeColor(android.R.attr.textColorSecondary)
            false -> resolveThemeColor(android.R.attr.textColorPrimary)
        }
    }

    override fun showGenerateKeyDialog() {
        KeyInputDialog(this, getString(R.string.conversation_encryption_key_title)) {
            prefs.globalEncryptionKey.set(it)
        }.show()
    }

    override fun requestDefaultSms() {
        navigator.showDefaultSmsDialog(this)
    }

    override fun requestPermissions() {
        requestingBasicPermissions = true
        ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS), 0)
    }

    override fun clearSearch() {
        dismissKeyboard()
        toolbarSearch.text = null
    }

    override fun clearSelection() {
        conversationsAdapter.clearSelection()
    }

    override fun themeChanged() {
        recyclerView.scrapViews()
    }

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(this, conversations, block)
    }

    override fun showDeleteDialog(conversations: List<Long>) {
        val count = conversations.size
        AlertDialog.Builder(this)
                .setTitle(com.moez.QKSMS.common.util.DeletionUi.title(prefs))
                .setMessage(com.moez.QKSMS.common.util.DeletionUi.conversations(this, prefs, count))
                .setPositiveButton(com.moez.QKSMS.common.util.DeletionUi.button(prefs)) { _, _ -> confirmDeleteIntent.onNext(conversations) }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
    }

    override fun showChangelog(changelog: ChangelogManager.CumulativeChangelog) {
        changelogDialog.show(changelog)
    }

    override fun showArchivedSnackbar() {
        Snackbar.make(drawerLayout, R.string.toast_archived, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.button_undo) { undoArchiveIntent.onNext(Unit) }
            setActionTextColor(colors.theme().theme)
            show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.select_all) {
            conversationsAdapter.selectItems(conversationsAdapter.data?.map { it.id } ?: emptyList())
        } else optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun onBackPressed() {
        backPressedSubject.onNext(NavItem.BACK)
    }

}

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

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkAdapter
import com.moez.QKSMS.common.base.QkViewHolder
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.extensions.removeAccents
import com.moez.QKSMS.model.SearchResult
import kotlinx.android.synthetic.main.search_list_item.*
import kotlinx.android.synthetic.main.search_list_item.view.*
import javax.inject.Inject

class SearchAdapter @Inject constructor(
    colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator
) : QkAdapter<SearchResult>() {

    private val highlightColor: Int by lazy { colors.theme().highlight }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.search_list_item, parent, false)
        return QkViewHolder(view).apply {
            view.setOnClickListener {
                if (adapterPosition < 0) return@setOnClickListener
                val result = getItem(adapterPosition)
                navigator.showConversation(result.conversation.id, result.query, result.message.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val result = getItem(position)
        holder.resultsHeader.setVisible(false)
        holder.title.text = highlight(result.conversation.getTitle(), result.query)
        holder.avatars.recipients = result.conversation.recipients
        holder.date.setVisible(true)
        holder.date.text = dateFormatter.getConversationTimestamp(result.message.date)
        val snippet = com.moez.QKSMS.util.SearchSnippet.create(result.message.getSummary(), result.query)
        holder.snippet.text = highlight(if (result.message.isMe())
            context.getString(R.string.main_sender_you, snippet) else snippet, result.query)
    }

    private fun highlight(text: String, query: String): SpannableString {
        val result = SpannableString(text)
        if (query.isEmpty()) return result
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            result.setSpan(BackgroundColorSpan(highlightColor), index, index + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
        return result
    }

    override fun areItemsTheSame(old: SearchResult, new: SearchResult) = old.message.id == new.message.id

    override fun areContentsTheSame(old: SearchResult, new: SearchResult) =
        old.query == new.query && old.message.getSummary() == new.message.getSummary() &&
            old.message.date == new.message.date && old.conversation.getTitle() == new.conversation.getTitle()
}

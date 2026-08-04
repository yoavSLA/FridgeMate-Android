package com.project.fridgemate.ui.chat

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.ChatMessageDto
import com.project.fridgemate.databinding.ItemMessageDateHeaderBinding
import com.project.fridgemate.databinding.ItemMessageReceivedBinding
import com.project.fridgemate.databinding.ItemMessageRecipeReceivedBinding
import com.project.fridgemate.databinding.ItemMessageRecipeSentBinding
import com.project.fridgemate.databinding.ItemMessageSentBinding
import com.project.fridgemate.utils.AvatarHelper
import com.squareup.picasso.Picasso
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class ChatItem {
    abstract val key: String

    data class Message(val message: ChatMessageDto) : ChatItem() {
        override val key: String get() = "msg:${message.id}"
    }

    data class DateHeader(val dayKey: String, val label: String) : ChatItem() {
        override val key: String get() = "date:$dayKey"
    }
}

class MessageAdapter(
    private val currentUserId: String?,
    private val onRecipeClick: ((recipeId: String) -> Unit)? = null,
    private val onAuthorClick: ((userId: String) -> Unit)? = null,
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(Diff) {


    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is ChatItem.DateHeader -> TYPE_DATE_HEADER
        is ChatItem.Message -> {
            val mine = item.message.sender?.id == currentUserId
            when (item.message.type) {
                "recipe_share" -> if (mine) TYPE_SENT_RECIPE else TYPE_RECEIVED_RECIPE
                else -> if (mine) TYPE_SENT else TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentVH(ItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED -> ReceivedVH(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            TYPE_SENT_RECIPE -> SentRecipeVH(
                ItemMessageRecipeSentBinding.inflate(inflater, parent, false)
            )
            TYPE_RECEIVED_RECIPE -> ReceivedRecipeVH(
                ItemMessageRecipeReceivedBinding.inflate(inflater, parent, false)
            )
            TYPE_DATE_HEADER -> DateHeaderVH(
                ItemMessageDateHeaderBinding.inflate(inflater, parent, false)
            )
            else -> error("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.Message -> when (holder) {
                is SentVH -> holder.bind(item.message)
                is ReceivedVH -> holder.bind(item.message)
                is SentRecipeVH -> holder.bind(item.message)
                is ReceivedRecipeVH -> holder.bind(item.message)
            }
            is ChatItem.DateHeader -> (holder as DateHeaderVH).bind(item)
        }
    }

    private fun formatTime(iso: String): String {
        val date = parseIso(iso) ?: return ""
        return timeFormat.format(date)
    }

    inner class SentVH(private val b: ItemMessageSentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessageDto) {
            b.tvContent.text = message.content
            b.tvTime.text = formatTime(message.createdAt)
        }
    }

    inner class ReceivedVH(private val b: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessageDto) {
            b.tvContent.text = message.content
            b.tvSender.text = message.sender?.displayName ?: ""
            b.tvTime.text = formatTime(message.createdAt)

            val senderId = message.sender?.id ?: ""
            val colors = getSenderColors(senderId)
            b.tvSender.setTextColor(ContextCompat.getColor(b.root.context, colors.first))
            b.cardBubble.setCardBackgroundColor(ContextCompat.getColor(b.root.context, colors.second))

            val resolved = resolveAvatarUrl(message.sender?.profileImage)
            val placeholder = AvatarHelper.createPlaceholder(b.root.context, message.sender?.displayName)
            
            val authorClickListener = View.OnClickListener {
                message.sender?.id?.let { onAuthorClick?.invoke(it) }
            }
            b.ivAvatar.setOnClickListener(authorClickListener)
            b.tvSender.setOnClickListener(authorClickListener)

            if (resolved != null) {
                Picasso.get()
                    .load(resolved)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(b.ivAvatar)
            } else {
                b.ivAvatar.setImageDrawable(placeholder)
            }
        }
    }

    class DateHeaderVH(private val b: ItemMessageDateHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ChatItem.DateHeader) {
            b.tvDate.text = item.label
        }
    }

    inner class SentRecipeVH(private val b: ItemMessageRecipeSentBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessageDto) {
            val payload = message.payload ?: return
            b.tvRecipeTitle.text = payload.title.orEmpty()
            b.tvRecipeMeta.text = formatRecipeMeta(payload.cookingTime, payload.difficulty)
            b.tvRecipeMeta.visibility = if (b.tvRecipeMeta.text.isBlank()) View.GONE else View.VISIBLE
            b.tvTime.text = formatTime(message.createdAt)
            loadRecipeImage(b.ivRecipeImage, payload.imageUrl)
            b.cardRecipe.setOnClickListener {
                payload.recipeId?.let { onRecipeClick?.invoke(it) }
            }
        }
    }

    inner class ReceivedRecipeVH(private val b: ItemMessageRecipeReceivedBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessageDto) {
            val payload = message.payload ?: return
            b.tvSender.text = message.sender?.displayName ?: ""
            
            val authorClickListener = View.OnClickListener {
                message.sender?.id?.let { onAuthorClick?.invoke(it) }
            }
            b.ivAvatar.setOnClickListener(authorClickListener)
            b.tvSender.setOnClickListener(authorClickListener)

            val senderId = message.sender?.id ?: ""
            val colors = getSenderColors(senderId)
            b.tvSender.setTextColor(ContextCompat.getColor(b.root.context, colors.first))
            b.cardRecipe.setCardBackgroundColor(ContextCompat.getColor(b.root.context, colors.second))

            b.tvRecipeTitle.text = payload.title.orEmpty()
            b.tvRecipeMeta.text = formatRecipeMeta(payload.cookingTime, payload.difficulty)
            b.tvRecipeMeta.visibility = if (b.tvRecipeMeta.text.isBlank()) View.GONE else View.VISIBLE
            b.tvTime.text = formatTime(message.createdAt)
            loadRecipeImage(b.ivRecipeImage, payload.imageUrl)
            bindAvatar(b.ivAvatar, message.sender?.profileImage, message.sender?.displayName)
            b.cardRecipe.setOnClickListener {
                payload.recipeId?.let { onRecipeClick?.invoke(it) }
            }
        }
    }

    private fun formatRecipeMeta(cookingTime: String?, difficulty: String?): String {
        val parts = mutableListOf<String>()
        if (!cookingTime.isNullOrBlank() && cookingTime != "Unknown") parts += cookingTime
        if (!difficulty.isNullOrBlank()) parts += difficulty
        return parts.joinToString("  •  ")
    }

    private fun loadRecipeImage(target: android.widget.ImageView, raw: String?) {
        val resolved = resolveAvatarUrl(raw)
        if (resolved != null) {
            Picasso.get()
                .load(resolved)
                .placeholder(R.drawable.ic_recipes)
                .error(R.drawable.ic_recipes)
                .into(target)
        } else {
            target.setImageResource(R.drawable.ic_recipes)
        }
    }

    private fun bindAvatar(target: com.google.android.material.imageview.ShapeableImageView, raw: String?, name: String?) {
        val resolved = resolveAvatarUrl(raw)
        val placeholder = AvatarHelper.createPlaceholder(target.context, name)
        if (resolved != null) {
            Picasso.get()
                .load(resolved)
                .placeholder(placeholder)
                .error(placeholder)
                .into(target)
        } else {
            target.setImageDrawable(placeholder)
        }
    }

    private fun getSenderColors(userId: String): Pair<Int, Int> {
        val hash = Math.abs(userId.hashCode())
        val index = hash % 15
        return SENDER_COLORS[index] to BUBBLE_COLORS[index]
    }

    companion object {
        private val SENDER_COLORS = intArrayOf(
            R.color.chat_sender_user_1, R.color.chat_sender_user_2, R.color.chat_sender_user_3,
            R.color.chat_sender_user_4, R.color.chat_sender_user_5, R.color.chat_sender_user_6,
            R.color.chat_sender_user_7, R.color.chat_sender_user_8, R.color.chat_sender_user_9,
            R.color.chat_sender_user_10, R.color.chat_sender_user_11, R.color.chat_sender_user_12,
            R.color.chat_sender_user_13, R.color.chat_sender_user_14, R.color.chat_sender_user_15
        )

        private val BUBBLE_COLORS = intArrayOf(
            R.color.chat_bubble_user_1, R.color.chat_bubble_user_2, R.color.chat_bubble_user_3,
            R.color.chat_bubble_user_4, R.color.chat_bubble_user_5, R.color.chat_bubble_user_6,
            R.color.chat_bubble_user_7, R.color.chat_bubble_user_8, R.color.chat_bubble_user_9,
            R.color.chat_bubble_user_10, R.color.chat_bubble_user_11, R.color.chat_bubble_user_12,
            R.color.chat_bubble_user_13, R.color.chat_bubble_user_14, R.color.chat_bubble_user_15
        )

        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE_HEADER = 3
        private const val TYPE_SENT_RECIPE = 4
        private const val TYPE_RECEIVED_RECIPE = 5

        private val Diff = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem) = oldItem == newItem
        }

        private val timeFormat: DateFormat =
            SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
        private val isoParser: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        private val dayKeyFormat: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
        private val fullDateFormat: DateFormat =
            DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())

        private fun parseIso(iso: String): Date? = runCatching { isoParser.parse(iso) }.getOrNull()

        internal fun resolveAvatarUrl(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val baseHost = runCatching { Uri.parse(BuildConfig.BASE_URL).host }.getOrNull()
                ?: return raw

            return when {
                raw.startsWith("/") -> BuildConfig.BASE_URL.trimEnd('/') + raw
                raw.startsWith("http://") || raw.startsWith("https://") -> {
                    val parsed = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
                    val host = parsed.host ?: return raw
                    val isUnreachable = host == "localhost" ||
                        host == "127.0.0.1" ||
                        host == "0.0.0.0" ||
                        host.startsWith("192.168.") ||
                        host.startsWith("10.") && host != "10.0.2.2"
                    if (isUnreachable) {
                        BuildConfig.BASE_URL.trimEnd('/') +
                            (parsed.path ?: "") +
                            (parsed.encodedQuery?.let { "?$it" } ?: "")
                    } else raw
                }
                else -> raw
            }
        }

        fun buildItems(context: Context, messages: List<ChatMessageDto>): List<ChatItem> {
            if (messages.isEmpty()) return emptyList()
            val today = Calendar.getInstance()
            val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val todayKey = dayKeyFormat.format(today.time)
            val yesterdayKey = dayKeyFormat.format(yesterday.time)

            val out = ArrayList<ChatItem>(messages.size + 4)
            var lastKey: String? = null
            for (m in messages) {
                val date = parseIso(m.createdAt) ?: continue
                val key = dayKeyFormat.format(date)
                if (key != lastKey) {
                    val label = when (key) {
                        todayKey -> context.getString(R.string.chat_date_today)
                        yesterdayKey -> context.getString(R.string.chat_date_yesterday)
                        else -> fullDateFormat.format(date)
                    }
                    out.add(ChatItem.DateHeader(key, label))
                    lastKey = key
                }
                out.add(ChatItem.Message(m))
            }
            return out
        }
    }
}

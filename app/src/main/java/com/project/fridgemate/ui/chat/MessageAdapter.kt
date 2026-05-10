package com.project.fridgemate.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.squareup.picasso.Callback
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

            val resolved = resolveAvatarUrl(message.sender?.profileImage)
            if (resolved != null) {
                Picasso.get()
                    .load(resolved)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(b.ivAvatar, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            Log.w("MessageAdapter", "Avatar load failed url=$resolved", e)
                        }
                    })
            } else {
                b.ivAvatar.setImageResource(R.drawable.ic_person)
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
            b.tvRecipeTitle.text = payload.title.orEmpty()
            b.tvRecipeMeta.text = formatRecipeMeta(payload.cookingTime, payload.difficulty)
            b.tvRecipeMeta.visibility = if (b.tvRecipeMeta.text.isBlank()) View.GONE else View.VISIBLE
            b.tvTime.text = formatTime(message.createdAt)
            loadRecipeImage(b.ivRecipeImage, payload.imageUrl)
            bindAvatar(b.ivAvatar, message.sender?.profileImage)
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

    private fun bindAvatar(target: com.google.android.material.imageview.ShapeableImageView, raw: String?) {
        val resolved = resolveAvatarUrl(raw)
        if (resolved != null) {
            Picasso.get()
                .load(resolved)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(target)
        } else {
            target.setImageResource(R.drawable.ic_person)
        }
    }

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE_HEADER = 3
        private const val TYPE_SENT_RECIPE = 4
        private const val TYPE_RECEIVED_RECIPE = 5

        private val Diff = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(old: ChatItem, new: ChatItem) = old.key == new.key
            override fun areContentsTheSame(old: ChatItem, new: ChatItem) = old == new
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

        // Returns a fully-qualified URL Picasso can load, or null if there's
        // nothing usable. Handles three cases:
        //  - relative path "/uploads/x.jpg"      -> prefix with BASE_URL host
        //  - bad absolute "http://localhost/x"   -> rewrite host to BASE_URL host
        //    (the upload route hardcodes whatever req.get("host") was, which on
        //    the dev server is often localhost / 127.0.0.1 / a LAN IP that the
        //    emulator can't resolve to your dev box)
        //  - usable absolute "https://lh3..."    -> return as-is
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
                        // private LAN IPs that the emulator can't reach
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

        // Builds a flat list interleaving date headers between messages whenever
        // the calendar day changes. `messages` is expected in chronological order.
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

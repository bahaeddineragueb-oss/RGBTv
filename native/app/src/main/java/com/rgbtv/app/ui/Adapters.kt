package com.rgbtv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.databinding.ItemAccountBinding
import com.rgbtv.app.databinding.ItemCategoryBinding
import com.rgbtv.app.databinding.ItemChannelBinding
import com.rgbtv.app.databinding.ItemEpgBinding
import com.rgbtv.app.databinding.ItemEpisodeBinding
import com.rgbtv.app.databinding.ItemPosterBinding
import com.rgbtv.app.databinding.ItemSeasonBinding
import com.rgbtv.app.databinding.ItemSettingBinding
import com.rgbtv.app.databinding.ItemTileBinding
import com.rgbtv.app.img.Images

/* ---------- categories ---------- */
class CategoryAdapter(
    private var items: List<Category> = emptyList(),
    private var counts: Map<String, Int> = emptyMap(),
    private var selected: String? = null,
    private val onPick: (Category?) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.H>() {
    inner class H(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemCategoryBinding.inflate(LayoutInflater.from(p.context), p, false)
        Ui.focusScale(b.root, 1.03f)
        return H(b)
    }
    override fun getItemCount() = items.size + 1
    override fun onBindViewHolder(h: H, pos: Int) {
        val c = if (pos == 0) null else items[pos - 1]
        val id = c?.id
        h.b.name.text = c?.name ?: h.b.root.context.getString(com.rgbtv.app.R.string.all)
        val n = if (c == null) counts.values.sum() else counts[id] ?: 0
        h.b.count.text = if (n > 0) n.toString() else ""
        h.b.root.isSelected = selected == id
        h.b.root.setOnClickListener { onPick(c) }
    }
    fun setData(list: List<Category>, counts: Map<String, Int>, sel: String?) {
        items = list; this.counts = counts; selected = sel
        notifyDataSetChanged()
    }
}

/* ---------- live channels (grid) ---------- */
class ChannelAdapter(
    private val onClick: (LiveCh) -> Unit,
    private val onFocus: (LiveCh) -> Unit,
    private val onLong: (LiveCh) -> Unit,
    private val badgeOf: (LiveCh) -> String? = { null }
) : ListAdapter<LiveCh, ChannelAdapter.H>(D) {
    companion object {
        val D = object : DiffUtil.ItemCallback<LiveCh>() {
            override fun areItemsTheSame(a: LiveCh, b: LiveCh) = a.id == b.id
            override fun areContentsTheSame(a: LiveCh, b: LiveCh) = a == b
        }
    }
    inner class H(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemChannelBinding.inflate(LayoutInflater.from(p.context), p, false)
        Ui.focusScale(b.root)
        return H(b)
    }
    override fun onBindViewHolder(h: H, pos: Int) {
        val c = getItem(pos)
        h.b.name.text = c.name
        h.b.num.text = if (c.num > 0) c.num.toString() else ""
        h.b.num.visibility = if (c.num > 0) View.VISIBLE else View.GONE
        val badge = badgeOf(c)
        h.b.badge.text = badge ?: ""
        h.b.badge.visibility = if (badge.isNullOrEmpty()) View.GONE else View.VISIBLE
        Images.load(h.b.logo, c.logo, Images.Kind.LOGO)
        h.b.root.setOnClickListener { onClick(c) }
        h.b.root.setOnLongClickListener { onLong(c); true }
        h.b.root.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
            if (has) onFocus(c)
        }
    }
}

/* ---------- generic poster cards (VOD / series / search / history) ---------- */
data class PosterRow(
    val key: String, val img: String, val title: String, val sub: String = "",
    val progress: Int = -1, val badge: String? = null
)

class PosterAdapter(
    private val onClick: (Int) -> Unit,
    private val onFocus: (Int) -> Unit = {},
    private val onLong: (Int) -> Unit = {}
) : ListAdapter<PosterRow, PosterAdapter.H>(D) {
    companion object {
        val D = object : DiffUtil.ItemCallback<PosterRow>() {
            override fun areItemsTheSame(a: PosterRow, b: PosterRow) = a.key == b.key
            override fun areContentsTheSame(a: PosterRow, b: PosterRow) = a == b
        }
    }
    inner class H(val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemPosterBinding.inflate(LayoutInflater.from(p.context), p, false)
        return H(b)
    }
    override fun onBindViewHolder(h: H, pos: Int) {
        val r = getItem(pos)
        h.b.name.text = r.title
        h.b.sub.text = r.sub
        h.b.sub.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
        h.b.badge.text = r.badge ?: ""
        h.b.badge.visibility = if (r.badge.isNullOrEmpty()) View.GONE else View.VISIBLE
        h.b.progress.visibility = if (r.progress in 1..99) View.VISIBLE else View.GONE
        if (r.progress in 0..100) h.b.progress.progress = r.progress
        Images.load(h.b.poster, r.img, Images.Kind.POSTER)
        h.b.root.setOnClickListener { onClick(h.bindingAdapterPosition) }
        h.b.root.setOnLongClickListener { onLong(h.bindingAdapterPosition); true }
        h.b.root.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
            if (has && h.bindingAdapterPosition >= 0) onFocus(h.bindingAdapterPosition)
        }
    }
}

/* ---------- episodes ---------- */
class EpisodeAdapter(
    private var items: List<EpisodeItem> = emptyList(),
    private var watched: Set<String> = emptySet(),
    private val onClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.H>() {
    inner class H(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemEpisodeBinding.inflate(LayoutInflater.from(p.context), p, false)
        return H(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val e = items[pos]
        h.b.num.text = h.b.root.context.getString(com.rgbtv.app.R.string.episode_n, e.episode)
        h.b.name.text = e.name
        h.b.watched.visibility = if (watched.contains(e.id)) View.VISIBLE else View.GONE
        Images.load(h.b.thumb, e.thumb, Images.Kind.BACKDROP)
        h.b.root.setOnClickListener { onClick(e) }
        h.b.root.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) 1.04f else 1f).scaleY(if (has) 1.04f else 1f).setDuration(120).start()
        }
    }
    fun setData(list: List<EpisodeItem>, w: Set<String>) {
        items = list; watched = w
        notifyDataSetChanged()
    }
}

/* ---------- seasons chips ---------- */
class SeasonAdapter(
    private var items: List<com.rgbtv.app.data.SeasonItem> = emptyList(),
    private var selected: Int = 0,
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.H>() {
    inner class H(val b: ItemSeasonBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemSeasonBinding.inflate(LayoutInflater.from(p.context), p, false)
        return H(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        h.b.root.text = h.b.root.context.getString(com.rgbtv.app.R.string.season, items[pos].num)
        h.b.root.isSelected = pos == selected
        h.b.root.alpha = if (pos == selected) 1f else 0.65f
        h.b.root.setOnClickListener { selected = pos; notifyDataSetChanged(); onPick(pos) }
        h.b.root.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(110).start()
        }
    }
    fun setData(list: List<com.rgbtv.app.data.SeasonItem>, sel: Int) {
        items = list; selected = sel
        notifyDataSetChanged()
    }
}

/* ---------- epg rows ---------- */
data class EpgRow(val ev: EpgEvent, val state: Int, val progress: Int = 0) {
    companion object { const val PAST = 0; const val NOW = 1; const val FUTURE = 2 }
}

class EpgAdapter(
    private var items: List<EpgRow> = emptyList(),
    private val onClick: (EpgRow) -> Unit,
    private val onFocus: (EpgRow) -> Unit = {}
) : RecyclerView.Adapter<EpgAdapter.H>() {
    inner class H(val b: ItemEpgBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemEpgBinding.inflate(LayoutInflater.from(p.context), p, false)
        return H(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val r = items[pos]
        val ctx = h.b.root.context
        h.b.time.text = "${Ui.clock(r.ev.start)} – ${Ui.clock(r.ev.end)}"
        h.b.title.text = r.ev.title.ifEmpty { ctx.getString(com.rgbtv.app.R.string.no_epg) }
        h.b.progress.visibility = if (r.state == EpgRow.NOW) View.VISIBLE else View.GONE
        if (r.state == EpgRow.NOW) h.b.progress.progress = r.progress
        h.b.state.visibility = View.VISIBLE
        h.b.state.text = when (r.state) {
            EpgRow.NOW -> ctx.getString(com.rgbtv.app.R.string.now)
            EpgRow.PAST -> "⏪ ${ctx.getString(com.rgbtv.app.R.string.replay)}"
            else -> ctx.getString(com.rgbtv.app.R.string.future)
        }
        h.b.root.alpha = if (r.state == EpgRow.FUTURE) 0.75f else 1f
        h.b.root.setOnClickListener { onClick(r) }
        h.b.root.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) 1.02f else 1f).scaleY(if (has) 1.02f else 1f).setDuration(110).start()
            if (has) onFocus(r)
        }
    }
    fun setData(list: List<EpgRow>) {
        items = list
        notifyDataSetChanged()
    }
}

/* ---------- settings rows ---------- */
data class SettingRow(val title: String, val desc: String, val value: String)

class SettingAdapter(
    private var items: List<SettingRow> = emptyList(),
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<SettingAdapter.H>() {
    inner class H(val b: ItemSettingBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemSettingBinding.inflate(LayoutInflater.from(p.context), p, false)
        Ui.focusScale(b.root, 1.02f)
        return H(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val r = items[pos]
        h.b.title.text = r.title
        h.b.desc.text = r.desc
        h.b.desc.visibility = if (r.desc.isEmpty()) View.GONE else View.VISIBLE
        h.b.value.text = r.value
        h.b.root.setOnClickListener { onClick(pos) }
    }
    fun setData(list: List<SettingRow>) {
        items = list
        notifyDataSetChanged()
    }
}

/* ---------- accounts (+ add tile at position 0) ---------- */
class AccountAdapter(
    private var items: List<com.rgbtv.app.data.Account> = emptyList(),
    private var subs: Map<String, String> = emptyMap(),
    private val onPick: (com.rgbtv.app.data.Account) -> Unit,
    private val onEdit: (com.rgbtv.app.data.Account) -> Unit,
    private val onAdd: () -> Unit
) : RecyclerView.Adapter<AccountAdapter.H>() {
    inner class H(val b: ItemAccountBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemAccountBinding.inflate(LayoutInflater.from(p.context), p, false)
        Ui.focusScale(b.root)
        return H(b)
    }
    override fun getItemCount() = items.size + 1
    override fun onBindViewHolder(h: H, pos: Int) {
        val ctx = h.b.root.context
        if (pos == 0) {
            h.b.glyph.text = "＋"
            h.b.name.text = ctx.getString(com.rgbtv.app.R.string.add_profile)
            h.b.type.text = ""
            h.b.sub.text = ""
            h.b.root.setOnClickListener { onAdd() }
            h.b.root.setOnLongClickListener(null)
        } else {
            val a = items[pos - 1]
            h.b.glyph.text = when (a.type) { "stalker" -> "📡"; "m3u" -> "📋"; else -> "⚡" }
            h.b.name.text = a.name.ifEmpty { a.url.ifEmpty { "…" } }
            h.b.type.text = when (a.type) {
                "stalker" -> ctx.getString(com.rgbtv.app.R.string.type_stalker)
                "m3u" -> ctx.getString(com.rgbtv.app.R.string.type_m3u)
                else -> ctx.getString(com.rgbtv.app.R.string.type_xtream)
            }
            h.b.sub.text = subs[a.id] ?: ""
            h.b.root.setOnClickListener { onPick(a) }
            h.b.root.setOnLongClickListener { onEdit(a); true }
        }
    }
    fun setData(list: List<com.rgbtv.app.data.Account>, s: Map<String, String>) {
        items = list; subs = s
        notifyDataSetChanged()
    }
}

/* ---------- home tiles ---------- */
data class TileRow(val glyph: String, val label: String, val sub: String = "", val color: Int = 0)

class TileAdapter(
    private var items: List<TileRow> = emptyList(),
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<TileAdapter.H>() {
    inner class H(val b: ItemTileBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
        val b = ItemTileBinding.inflate(LayoutInflater.from(p.context), p, false)
        Ui.focusScale(b.root)
        return H(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val r = items[pos]
        h.b.glyph.text = r.glyph
        h.b.label.text = r.label
        h.b.sub.text = r.sub
        h.b.sub.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
        h.b.root.setBackgroundResource(
            when (r.color) {
                1 -> com.rgbtv.app.R.drawable.hero_magenta
                2 -> com.rgbtv.app.R.drawable.hero_green
                3 -> com.rgbtv.app.R.drawable.hero_orange
                else -> com.rgbtv.app.R.drawable.hero_cyan
            }
        )
        h.b.root.setOnClickListener { onClick(pos) }
    }
    fun setData(list: List<TileRow>) {
        items = list
        notifyDataSetChanged()
    }
}

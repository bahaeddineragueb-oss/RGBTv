package com.rgbtv.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rgbtv.app.R
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.DiskCache
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentAccountEditBinding
import com.rgbtv.app.net.Net
import com.rgbtv.app.net.ProviderFactory
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.launch
import java.net.URLDecoder

class AccountEditFragment : Fragment() {
    private var b: FragmentAccountEditBinding? = null
    private var accId: String? = null
    private var type = "xtream"
    private var testing = false

    companion object {
        fun forId(id: String?): AccountEditFragment {
            val f = AccountEditFragment()
            f.arguments = Bundle().apply { putString("id", id) }
            return f
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentAccountEditBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        accId = arguments?.getString("id")
        val acc = accId?.let { Store.getAccount(it) }
        if (acc != null) {
            type = acc.type
            b.title.text = getString(R.string.edit_profile)
            b.inName.setText(acc.name)
            b.inUrl.setText(acc.url)
            b.inUser.setText(acc.username)
            b.inPass.setText(acc.password)
            b.inMac.setText(acc.mac.ifEmpty { Store.deviceMac() })
            b.inEpg.setText(acc.epgUrl)
            b.btnDelete.visibility = View.VISIBLE
        } else {
            b.inMac.setText(Store.deviceMac())
        }
        paintTypes()
        b.btnXtream.setOnClickListener { type = "xtream"; paintTypes() }
        b.btnStalker.setOnClickListener { type = "stalker"; paintTypes() }
        b.btnM3u.setOnClickListener { type = "m3u"; paintTypes() }
        b.btnNewMac.setOnClickListener { b.inMac.setText(Kit.randomMac()) }
        b.btnTest.setOnClickListener { test() }
        b.btnSave.setOnClickListener { save() }
        b.btnDelete.setOnClickListener { askDelete() }
    }

    private fun accent(): Int {
        return when (Store.settings().accent) {
            "green" -> requireContext().getColor(R.color.accent_green)
            "red" -> requireContext().getColor(R.color.accent_red)
            "purple" -> requireContext().getColor(R.color.accent_purple)
            "gold" -> requireContext().getColor(R.color.accent_gold)
            else -> requireContext().getColor(R.color.accent_blue)
        }
    }

    private fun paintTypes() {
        val b = b ?: return
        val acc = accent()
        listOf(b.btnXtream to "xtream", b.btnStalker to "stalker", b.btnM3u to "m3u").forEach { (btn, t) ->
            paintTypeBtn(btn, t == type, acc)
        }
        b.rowCreds.visibility = if (type == "xtream") View.VISIBLE else View.GONE
        b.rowMac.visibility = if (type == "stalker") View.VISIBLE else View.GONE
        b.hintGetphp.visibility = if (type == "m3u") View.VISIBLE else View.GONE
    }

    private fun paintTypeBtn(btn: Button, sel: Boolean, acc: Int) {
        if (sel) {
            btn.backgroundTintList = ColorStateList.valueOf(acc)
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.backgroundTintList = null
            btn.setTextColor(0xFFF2F5FA.toInt())
        }
    }

    private fun collect(): Account? {
        val b = b ?: return null
        val url = b.inUrl.text.toString().trim()
        var t = type
        var user = b.inUser.text.toString().trim()
        var pass = b.inPass.text.toString()
        // get.php → real Xtream account
        if (t == "m3u") {
            val m = Regex(
                "^(https?://[^/?#]+)(?:/[^?#]*)?/get\\.php\\?(?:.*&)?username=([^&]+)&password=([^&#]+)",
                RegexOption.IGNORE_CASE
            ).find(url)
            if (m != null) {
                t = "xtream"
                try {
                    return Account(
                        id = accId ?: "", name = b.inName.text.toString().trim(),
                        type = "xtream", url = m.groupValues[1],
                        username = URLDecoder.decode(m.groupValues[2], "UTF-8"),
                        password = URLDecoder.decode(m.groupValues[3], "UTF-8"),
                        mac = b.inMac.text.toString().trim().uppercase(),
                        epgUrl = b.inEpg.text.toString().trim()
                    ).also {
                        type = "xtream"
                        b.inUrl.setText(it.url); b.inUser.setText(it.username); b.inPass.setText(it.password)
                        paintTypes()
                        Ui.toast(context, "Xtream API ✓")
                    }
                } catch (e: Exception) { }
            }
        }
        return Account(
            id = accId ?: "", name = b.inName.text.toString().trim(), type = t, url = url,
            username = user, password = pass,
            mac = b.inMac.text.toString().trim().uppercase(),
            epgUrl = b.inEpg.text.toString().trim()
        )
    }

    private fun test() {
        if (testing) return
        val b = b ?: return
        val a = collect() ?: return
        if (a.type == "xtream" && (a.url.isEmpty() || a.username.isEmpty() || a.password.isEmpty())) {
            b.result.text = getString(R.string.fill_all); return
        }
        if ((a.type == "m3u" || a.type == "stalker") && a.url.isEmpty()) {
            b.result.text = getString(R.string.fill_url); return
        }
        testing = true
        b.btnTest.text = getString(R.string.testing)
        b.result.text = ""
        lifecycleScope.launch {
            try {
                val p = ProviderFactory.create(a)
                val s = p.login()
                try { p.close() } catch (e: Exception) { }
                val exp = s.expires?.let { Ui.dateFull(it) } ?: getString(R.string.unlimited)
                b.result.text = "✓ ${s.status} · ${getString(R.string.expires)}: $exp" +
                    (if (s.extra.isNotEmpty()) "\n${s.extra}" else "")
            } catch (e: Exception) {
                b.result.text = "✕ ${Net.userMsg(requireContext(), e)}"
            } finally {
                testing = false
                b.btnTest.text = getString(R.string.test)
            }
        }
    }

    private fun save() {
        val b = b ?: return
        val a = collect() ?: return
        if (a.type == "xtream" && (a.url.isEmpty() || a.username.isEmpty() || a.password.isEmpty())) {
            b.result.text = getString(R.string.fill_all); return
        }
        if ((a.type == "m3u" || a.type == "stalker") && a.url.isEmpty()) {
            b.result.text = getString(R.string.fill_url); return
        }
        if (a.name.isEmpty()) a.name = when (a.type) {
            "stalker" -> getString(R.string.type_stalker)
            "m3u" -> getString(R.string.type_m3u)
            else -> a.username.ifEmpty { getString(R.string.type_xtream) }
        }
        lifecycleScope.launch {
            if (accId == null) {
                val saved = Store.addAccount(a)
                Store.setLastAccount(saved.id)
            } else {
                // URL/credentials may have changed → drop caches & session
                Store.updateAccount(a)
                DiskCache.clearAccount(a.id)
                Repository.dropXmltv(a.id)
                Store.setLastAccount(a.id)
            }
            Repository.logout()
            main().home()
        }
    }

    private fun askDelete() {
        val id = accId ?: return
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.yes_delete) { _, _ ->
                lifecycleScope.launch {
                    if (Repository.accountId == id || Store.lastAccount() == id) Repository.logout()
                    Store.removeAccount(id)
                    DiskCache.clearAccount(id)
                    Repository.dropXmltv(id)
                    try {
                        requireContext().deleteDatabase("epg_$id.db")
                    } catch (e: Exception) { }
                    if (Store.accounts().isEmpty()) main().profiles()
                    else parentFragmentManager.popBackStack()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}

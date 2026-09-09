package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rgbtv.app.App
import com.rgbtv.app.R
import com.rgbtv.app.data.DiskCache
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentSettingsBinding
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var b: FragmentSettingsBinding? = null
    private lateinit var adapter: SettingAdapter
    private var pendingPin: (() -> Unit)? = null

    private val langs = listOf("system", "en", "ar", "fr")
    private val fmts = listOf("auto", "hls", "ts")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentSettingsBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.rows.layoutManager = LinearLayoutManager(requireContext())
        adapter = SettingAdapter(onClick = { onRow(it) })
        b.rows.adapter = adapter
        parentFragmentManager.setFragmentResultListener(PinDialog.REQ, viewLifecycleOwner) { _, r ->
            if (r.getBoolean("ok")) pendingPin?.invoke()
            pendingPin = null
        }
        render()
    }

    private fun langLabel(l: String) = when (l) {
        "en" -> "English"; "ar" -> "العربية"; "fr" -> "Français"
        else -> getString(R.string.lang_system)
    }

    private fun fmtLabel(f: String) = when (f) {
        "hls" -> getString(R.string.fmt_hls)
        "ts" -> getString(R.string.fmt_ts)
        else -> getString(R.string.fmt_auto)
    }

    private fun render() {
        val s = Store.settings()
        val acc = Repository.currentAccount()
        adapter.setData(
            listOf(
                SettingRow(getString(R.string.set_account), acc?.name ?: "", "›", getString(R.string.sec_account)),
                SettingRow(getString(R.string.set_livefmt), getString(R.string.set_livefmt_d), fmtLabel(s.liveFormat), getString(R.string.sec_playback)),
                SettingRow(getString(R.string.set_lang), "", langLabel(s.lang), getString(R.string.sec_appearance)),
                SettingRow(
                    getString(R.string.set_parental), getString(R.string.set_parental_d),
                    getString(if (s.parental) R.string.on else R.string.off), getString(R.string.sec_parental)
                ),
                SettingRow(getString(R.string.set_pin), getString(R.string.set_pin_d), "›"),
                SettingRow(getString(R.string.set_unlockall), getString(R.string.set_unlockall_d), ""),
                SettingRow(getString(R.string.set_refresh), getString(R.string.set_refresh_d), "", getString(R.string.sec_advanced)),
                SettingRow(getString(R.string.set_about), "RGBTv ${App.VERSION}", "›", getString(R.string.sec_about)),
                SettingRow(getString(R.string.set_exit), "", "")
            )
        )
    }

    private fun cycle(list: List<String>, cur: String): String = list[(list.indexOf(cur) + 1 + list.size) % list.size]

    private fun onRow(i: Int) {
        when (i) {
            0 -> main().profiles()
            1 -> {
                Store.updateSettings { it.liveFormat = cycle(fmts, it.liveFormat) }
                render()
            }
            2 -> {
                Store.updateSettings { it.lang = cycle(langs, it.lang) }
                (requireActivity().application as App).applyLocale()
                requireActivity().recreate()
            }
            3 -> toggleParental()
            4 -> changePin()
            5 -> {
                val id = Repository.accountId ?: return
                pendingPin = {
                    Store.clearLocks(id)
                    Ui.toast(context, getString(R.string.unlocked_all))
                }
                if (Store.settings().parental) PinDialog.verify(parentFragmentManager)
                else pendingPin?.invoke().also { pendingPin = null }
            }
            6 -> refresh()
            7 -> AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.about_text, App.VERSION))
                .setPositiveButton("OK", null)
                .show()
            8 -> requireActivity().finishAffinity()
        }
    }

    private fun toggleParental() {
        val s = Store.settings()
        if (!s.parental && Store.pin().isNullOrEmpty()) {
            pendingPin = {
                Store.updateSettings { it.parental = true }
                render()
            }
            PinDialog.setNew(parentFragmentManager)
        } else if (s.parental) {
            pendingPin = {
                Store.updateSettings { it.parental = false }
                render()
            }
            PinDialog.verify(parentFragmentManager)
        } else {
            Store.updateSettings { it.parental = true }
            render()
            Ui.toast(context, getString(R.string.lock_hint))
        }
    }

    private fun changePin() {
        if (Store.pin().isNullOrEmpty()) {
            pendingPin = { render() }
            PinDialog.setNew(parentFragmentManager)
        } else {
            pendingPin = {
                pendingPin = { render() }
                PinDialog.setNew(parentFragmentManager)
            }
            PinDialog.verify(parentFragmentManager)
        }
    }

    private fun refresh() {
        val id = Repository.accountId
        lifecycleScope.launch {
            if (id != null) {
                DiskCache.clearAccount(id)
                Repository.dropXmltv(id)
                try { requireContext().deleteDatabase("epg_$id.db") } catch (e: Exception) { }
            }
            Repository.logout()
            Ui.toast(context, getString(R.string.refreshed))
            main().home()
        }
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("settings")
    }
}

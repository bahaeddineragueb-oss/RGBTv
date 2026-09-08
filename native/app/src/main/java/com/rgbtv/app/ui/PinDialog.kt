package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.DialogPinBinding

/** On-screen PIN pad (works with remotes — no IME needed). Result via fragment result REQ/"ok". */
class PinDialog : DialogFragment() {
    private var b: DialogPinBinding? = null
    private var mode = "verify"
    private var first = ""
    private var entry = ""

    companion object {
        const val REQ = "pin_req"
        fun verify(fm: FragmentManager) {
            PinDialog().apply {
                arguments = Bundle().apply { putString("mode", "verify") }
            }.show(fm, "pin")
        }
        fun setNew(fm: FragmentManager) {
            PinDialog().apply {
                arguments = Bundle().apply { putString("mode", "set") }
            }.show(fm, "pin")
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = DialogPinBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        mode = arguments?.getString("mode", "verify") ?: "verify"
        if (mode == "verify" && Store.pin().isNullOrEmpty()) mode = "set"
        paintTitle()
        buildPad(b)
        b.btnCancel.setOnClickListener { send(false) }
    }

    private fun paintTitle() {
        val b = b ?: return
        b.pinTitle.text = when {
            mode == "set" && first.isEmpty() -> getString(R.string.pin_new)
            mode == "set" -> getString(R.string.pin_confirm)
            else -> getString(R.string.pin_title)
        }
    }

    private fun buildPad(b: DialogPinBinding) {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        var firstBtn: Button? = null
        keys.forEach { k ->
            val btn = Button(requireContext())
            btn.text = k
            btn.textSize = 20f
            btn.minimumWidth = 0
            btn.minWidth = 0
            val dp = resources.displayMetrics.density
            val lp = GridLayout.LayoutParams()
            lp.width = (76 * dp).toInt()
            lp.height = (56 * dp).toInt()
            lp.setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            btn.layoutParams = lp
            btn.setOnClickListener { press(k) }
            b.pad.addView(btn)
            if (firstBtn == null) firstBtn = btn
        }
        b.root.post { firstBtn?.requestFocus() }
    }

    private fun press(k: String) {
        val b = b ?: return
        when (k) {
            "⌫" -> { if (entry.isNotEmpty()) entry = entry.dropLast(1) }
            "✓" -> { submit(); return }
            else -> if (entry.length < 4) entry += k
        }
        paintDots()
        if (entry.length == 4) b.root.postDelayed({ submit() }, 120)
    }

    private fun paintDots() {
        val b = b ?: return
        b.pinDots.text = "●".repeat(entry.length) + "○".repeat(4 - entry.length)
    }

    private fun submit() {
        val b = b ?: return
        if (entry.length < 4) return
        if (mode == "verify") {
            if (entry == Store.pin()) send(true)
            else {
                b.pinError.text = getString(R.string.pin_wrong)
                entry = ""
                paintDots()
            }
            return
        }
        // set mode
        if (first.isEmpty()) {
            first = entry
            entry = ""
            paintDots()
            paintTitle()
        } else {
            if (entry == first) {
                Store.setPin(entry)
                Ui.toast(context, getString(R.string.pin_saved))
                send(true)
            } else {
                b.pinError.text = getString(R.string.pin_mismatch)
                first = ""
                entry = ""
                paintDots()
                paintTitle()
            }
        }
    }

    private fun send(ok: Boolean) {
        parentFragmentManager.setFragmentResult(REQ, Bundle().apply { putBoolean("ok", ok) })
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}

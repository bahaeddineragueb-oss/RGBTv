package com.rgbtv.app.ui

import androidx.fragment.app.Fragment
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Repository

/** Parental gate: runs the action immediately, or after a PIN check. */
object Guard {
    fun run(f: Fragment, accId: String, type: String, id: String, name: String, action: () -> Unit) {
        if (!Store.settings().parental) {
            action(); return
        }
        val key = "$accId:$type:$id"
        if (Repository.unlocked.contains(key)) {
            action(); return
        }
        if (!Store.isLocked(accId, "$type:$id") && !Kit.isAdult(name)) {
            action(); return
        }
        verify(f, key, action)
    }

    /** Always verify (unless session-unlocked): censored categories etc. */
    fun runForce(f: Fragment, key: String, action: () -> Unit) {
        if (!Store.settings().parental || Repository.unlocked.contains(key)) {
            action(); return
        }
        verify(f, key, action)
    }

    private fun verify(f: Fragment, key: String, action: () -> Unit) {
        f.parentFragmentManager.setFragmentResultListener(PinDialog.REQ, f.viewLifecycleOwner) { _, b ->
            if (b.getBoolean("ok")) {
                Repository.unlocked.add(key)
                action()
            }
        }
        PinDialog.verify(f.parentFragmentManager)
    }
}

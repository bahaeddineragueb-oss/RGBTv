package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentProfilesBinding
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.launch

class ProfilesFragment : Fragment() {
    private var b: FragmentProfilesBinding? = null
    private lateinit var adapter: AccountAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentProfilesBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.grid.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = AccountAdapter(
            onPick = { a ->
                Store.setLastAccount(a.id)
                lifecycleScope.launch {
                    Repository.logout()
                    main().home()
                }
            },
            onEdit = { a -> main().open(AccountEditFragment.forId(a.id)) },
            onAdd = { main().open(AccountEditFragment.forId(null)) }
        )
        b.grid.adapter = adapter
        load()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("profiles")
        load()
    }

    private fun load() {
        val b = b ?: return
        val accs = Store.accounts()
        val subs = accs.associate { a ->
            a.id to (Store.lastSession(a.id)?.let { s ->
                val exp = s.expires?.let { Ui.dateFull(it) } ?: getString(R.string.unlimited)
                "${s.status} · $exp"
            } ?: "")
        }
        adapter.setData(accs, subs)
        b.empty.visibility = if (accs.isEmpty()) View.VISIBLE else View.GONE
        Ui.autoSpan(b.grid, 230)
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}

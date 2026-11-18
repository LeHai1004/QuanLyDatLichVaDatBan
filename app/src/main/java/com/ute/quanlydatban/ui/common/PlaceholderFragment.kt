package com.ute.quanlydatban.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentPlaceholderBinding
import com.ute.quanlydatban.util.UiStateHelper

class PlaceholderFragment : Fragment() {
    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
<<<<<<< HEAD:app/src/main/java/com/ute/quanlydatban/ui/PlaceholderFragment.kt
        binding.tvPlaceholderTitle.text =
            arguments?.getString(SCREEN_TITLE) ?: getString(R.string.nav_home)
        UiStateHelper.showLoading(binding.root)
=======
        val titleResId = arguments?.getInt(SCREEN_TITLE, 0) ?: 0
        if (titleResId != 0) {
            binding.tvPlaceholderTitle.setText(titleResId)
        } else {
            binding.tvPlaceholderTitle.text =
                arguments?.getString(SCREEN_TITLE) ?: getString(R.string.nav_home)
        }

        binding.root.findViewById<View>(R.id.btnRetry)?.setOnClickListener {
            UiStateHelper.showLoading(binding.root)
        }
>>>>>>> c19a1da6b4a32680d697e87b35e1441edf11f1d3:app/src/main/java/com/ute/quanlydatban/ui/common/PlaceholderFragment.kt
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val SCREEN_TITLE = "screenTitle"
    }
}

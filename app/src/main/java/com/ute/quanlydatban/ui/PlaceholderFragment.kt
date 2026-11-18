package com.ute.quanlydatban.ui

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
        binding.tvPlaceholderTitle.text =
            arguments?.getString(SCREEN_TITLE) ?: getString(R.string.nav_home)
        UiStateHelper.showLoading(binding.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val SCREEN_TITLE = "screenTitle"
    }
}

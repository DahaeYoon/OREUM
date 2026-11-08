package com.example.oreum.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.oreum.R
import com.example.oreum.ui.common.PlaceholderFragment
import kotlinx.coroutines.launch

class HomeFragment : PlaceholderFragment(R.string.nav_home, R.string.home_desc) {

    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // ViewModel(StateFlow) → 화면 표시 흐름이 동작하는지 확인
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.extra.isVisible = true
                    binding.extra.text = getString(R.string.home_viewmodel_check, state.statusText)
                }
            }
        }
    }
}

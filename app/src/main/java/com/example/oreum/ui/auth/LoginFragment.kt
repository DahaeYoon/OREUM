package com.example.oreum.ui.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.databinding.FragmentLoginBinding
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** 로그인: 이메일 · 비밀번호 · 비밀번호 표시 · 로그인 · 비밀번호 찾기 · 회원가입 이동 */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.inputEmail.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.inputPassword.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.login(); true
            } else {
                false
            }
        }
        binding.btnLogin.setOnClickListener { viewModel.login() }
        binding.btnTestAccount.setOnClickListener { viewModel.fillTestAccount() }
        binding.btnToSignup.setOnClickListener { findNavController().navigate(R.id.action_login_to_signup) }
        binding.btnFindPassword.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.find_password_title)
                .setMessage(R.string.find_password_body)
                .setPositiveButton(R.string.dialog_close, null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: LoginUiState) {
        binding.inputEmail.setTextIfChanged(state.email)
        binding.inputPassword.setTextIfChanged(state.password)
        binding.layoutEmail.error = state.emailError?.let { getString(it.messageRes()) }
        binding.layoutPassword.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.textFormError.isVisible = state.formError != null
        state.formError?.let { binding.textFormError.setText(it.messageRes()) }

        binding.btnLogin.isEnabled = !state.isLoading
        binding.btnLogin.setText(if (state.isLoading) R.string.login_loading else R.string.login_button)

        if (state.loggedIn) {
            viewModel.onNavigated()
            Toast.makeText(requireContext(), R.string.toast_logged_in, Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_login_to_home)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** ViewModel 값이 바뀌었을 때만 입력칸 글자를 바꿔요 (커서가 튀지 않도록) */
internal fun EditText.setTextIfChanged(value: String) {
    if (text?.toString() != value) {
        setText(value)
        setSelection(value.length)
    }
}

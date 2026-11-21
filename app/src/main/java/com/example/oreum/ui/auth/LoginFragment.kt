package com.example.oreum.ui.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.databinding.DialogFindPasswordBinding
import com.example.oreum.databinding.FragmentLoginBinding
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import com.example.oreum.util.showToast

/** 로그인: 이메일 · 비밀번호 · 비밀번호 표시 · 로그인 · 비밀번호 찾기 · 회원가입 이동 (이미 로그인한 기기면 바로 홈) */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        // 이미 로그인한 기기면 (Firebase가 로그인 상태를 기억해요) 바로 홈으로
        if (AuthRepositoryProvider.instance.currentUser.value != null) {
            findNavController().navigate(R.id.action_login_to_home)
            return
        }

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
        binding.btnToSignup.setOnClickListener { findNavController().navigate(R.id.action_login_to_signup) }
        binding.btnFindPassword.setOnClickListener { openFindPassword() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    /** 비밀번호 찾기: 이메일 입력 → 재설정 링크 보내기 (입력한 로그인 이메일을 미리 채워요) */
    private fun openFindPassword() {
        val db = DialogFindPasswordBinding.inflate(layoutInflater)
        db.inputResetEmail.setText(viewModel.uiState.value.email)
        db.inputResetEmail.doAfterTextChanged { db.layoutResetEmail.error = null }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.find_password_title)
            .setView(db.root)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.find_password_send, null)
            .create()
        dialog.setOnShowListener {
            val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            // 확인 버튼을 눌러도 바로 닫지 않고, 보낸 뒤에 닫아요 (오류면 입력칸 아래에 표시)
            send.setOnClickListener {
                val email = db.inputResetEmail.text?.toString().orEmpty().trim()
                send.isEnabled = false
                send.setText(R.string.find_password_sending)
                viewLifecycleOwner.lifecycleScope.launch {
                    val error = viewModel.sendPasswordReset(email)
                    send.isEnabled = true
                    send.setText(R.string.find_password_send)
                    if (error == null) {
                        dialog.dismiss()
                        requireActivity().showToast(R.string.toast_find_password_sent)
                    } else {
                        db.layoutResetEmail.error = getString(error.messageRes())
                    }
                }
            }
        }
        dialog.show()
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
            requireActivity().showToast(R.string.toast_logged_in)
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

package com.example.oreum.ui.auth

import android.os.Bundle
import android.view.View
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
import com.example.oreum.databinding.FragmentSignupBinding
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import kotlinx.coroutines.launch

/** 회원가입: 닉네임 · 이메일 · 비밀번호 · 비밀번호 확인 · 약관 동의 · 가입하기 */
class SignupFragment : Fragment(R.layout.fragment_signup) {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: SignupViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignupBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.inputNickname.doAfterTextChanged { viewModel.onNicknameChanged(it?.toString().orEmpty()) }
        binding.inputEmail.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.inputPassword.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.inputPasswordConfirm.doAfterTextChanged { viewModel.onPasswordConfirmChanged(it?.toString().orEmpty()) }
        binding.checkTerms.setOnCheckedChangeListener { _, checked -> viewModel.onTermsChanged(checked) }
        binding.btnSignup.setOnClickListener { viewModel.signUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SignupUiState) {
        binding.layoutNickname.error = state.nicknameError?.let { getString(it.messageRes()) }
        binding.layoutEmail.error = state.emailError?.let { getString(it.messageRes()) }
        // 비밀번호 규칙은 평소엔 안내 문구(helper), 틀리면 같은 자리에 빨간 오류로
        binding.layoutPassword.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.layoutPasswordConfirm.error = state.passwordConfirmError?.let { getString(it.messageRes()) }
        binding.textFormError.isVisible = state.formError != null
        state.formError?.let { binding.textFormError.setText(it.messageRes()) }
        if (binding.checkTerms.isChecked != state.termsAgreed) binding.checkTerms.isChecked = state.termsAgreed

        binding.btnSignup.isEnabled = !state.isLoading
        binding.btnSignup.setText(if (state.isLoading) R.string.signup_loading else R.string.signup_button)

        state.signedUpNickname?.let { nickname ->
            viewModel.onNavigated()
            Toast.makeText(requireContext(), getString(R.string.toast_signed_up, nickname), Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_signup_to_home)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

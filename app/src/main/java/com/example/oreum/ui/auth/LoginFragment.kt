package com.example.oreum.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.databinding.FragmentLoginBinding

/** STEP 0 임시 로그인 화면: 버튼을 누르면 홈으로 들어가요. 실제 인증은 STEP 1에서 연결해요. */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        binding.btnTempLogin.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_home)
        }
        binding.btnToSignup.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

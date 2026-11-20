package com.example.oreum.ui.mypage

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.FragmentMypageBinding
import com.example.oreum.ui.home.HomePrefs
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 마이 (1차): 프로필 · 샘플 데이터 초기화 · 로그아웃.
 * 학습 알림 · 기본 학습 설정은 2차 범위예요.
 */
class MyPageFragment : Fragment(R.layout.fragment_mypage) {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val auth: AuthRepository = AuthRepositoryProvider.instance
    private val repository: StudyRepository = StudyRepositoryProvider.instance

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMypageBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.appbar.applySystemBarsPadding(top = true, bottom = false)
        binding.textAvatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFCDEBDF.toInt())
            setStroke(dp(2), 0xFFFFFFFF.toInt())
        }

        binding.rowReset.setOnClickListener { confirmReset() }
        binding.rowLogout.setOnClickListener { confirmLogout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                auth.currentUser.collect { user ->
                    val name = user?.nickname.orEmpty().ifBlank { getString(R.string.home_default_name) }
                    binding.textName.text = name
                    binding.textEmail.text = user?.email.orEmpty()
                    binding.textAvatar.text = name.take(1)
                }
            }
        }
    }

    /** 샘플 데이터 초기화: 목표 · 구간 · 학습 기록이 처음 상태로 돌아가요 */
    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mypage_reset_title)
            .setMessage(R.string.mypage_reset_body)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.mypage_reset_ok) { _, _ ->
                repository.resetSampleData()
                HomePrefs.goalId = null
                HomePrefs.picked = false
                Toast.makeText(requireContext(), R.string.toast_mypage_reset, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 로그아웃: 로그인 화면으로 (뒤로 가기로 돌아오지 않게 쌓인 화면을 모두 지워요) */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mypage_logout_title)
            .setMessage(R.string.mypage_logout_body)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.mypage_logout) { _, _ ->
                auth.signOut()
                HomePrefs.goalId = null
                HomePrefs.picked = false
                findNavController().navigate(
                    R.id.loginFragment,
                    null,
                    NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build(),
                )
            }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

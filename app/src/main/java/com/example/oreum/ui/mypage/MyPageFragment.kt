package com.example.oreum.ui.mypage

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.databinding.DialogNicknameBinding
import com.example.oreum.databinding.FragmentMypageBinding
import com.example.oreum.domain.usecase.AuthValidator
import com.example.oreum.ui.auth.messageRes
import com.example.oreum.ui.home.HomePrefs
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import com.example.oreum.util.showToast

/**
 * 마이 (1차): 프로필 · 닉네임 변경 · 로그아웃 · 앱 정보(버전 · 오름 소개).
 * 학습 알림 · 기본 학습 설정은 2차 범위예요.
 */
class MyPageFragment : Fragment(R.layout.fragment_mypage) {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val auth: AuthRepository = AuthRepositoryProvider.instance

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

        binding.rowNickname.setOnClickListener { openNickname() }
        binding.rowAbout.setOnClickListener { showAbout() }
        binding.textVersion.text = versionName()
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

    /** 닉네임 변경: 2~10자, 저장하면 홈 인사말에도 바로 반영돼요 */
    private fun openNickname() {
        val db = DialogNicknameBinding.inflate(layoutInflater)
        db.inputNickname.setText(auth.currentUser.value?.nickname.orEmpty())
        db.inputNickname.setSelection(db.inputNickname.text?.length ?: 0)
        db.inputNickname.doAfterTextChanged { db.layoutNickname.error = null }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mypage_nickname)
            .setView(db.root)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.mypage_nickname_save, null)
            .create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            // 저장 버튼을 눌러도 바로 닫지 않고, 저장한 뒤에 닫아요 (오류면 입력칸 아래에 표시)
            save.setOnClickListener {
                val name = db.inputNickname.text?.toString().orEmpty().trim()
                AuthValidator.nickname(name)?.let {
                    db.layoutNickname.error = getString(it.messageRes())
                    return@setOnClickListener
                }
                save.isEnabled = false
                save.setText(R.string.mypage_nickname_saving)
                viewLifecycleOwner.lifecycleScope.launch {
                    val error = auth.updateNickname(name)
                    save.isEnabled = true
                    save.setText(R.string.mypage_nickname_save)
                    if (error == null) {
                        dialog.dismiss()
                        requireActivity().showToast(R.string.toast_mypage_nickname)
                    } else {
                        db.layoutNickname.error = getString(error.messageRes())
                    }
                }
            }
        }
        dialog.show()
    }

    /** 오름 소개: 앱 설명 · AI 사용 안내 · 버전 */
    private fun showAbout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mypage_about_title)
            .setMessage(getString(R.string.mypage_about_body, versionName()))
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun versionName(): String = runCatching {
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
    }.getOrNull().orEmpty()

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

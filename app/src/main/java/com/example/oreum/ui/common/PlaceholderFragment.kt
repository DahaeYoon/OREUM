package com.example.oreum.ui.common

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.oreum.R
import com.example.oreum.databinding.FragmentPlaceholderBinding

/**
 * STEP 0 임시 화면의 공통 부모.
 * 각 탭은 제목과 설명 문자열만 넘기고, 실제 화면은 이후 STEP에서 각자의 XML로 바꿔요.
 */
abstract class PlaceholderFragment(
    @StringRes private val titleRes: Int,
    @StringRes private val descriptionRes: Int,
) : Fragment(R.layout.fragment_placeholder) {

    private var _binding: FragmentPlaceholderBinding? = null

    /** onViewCreated ~ onDestroyView 사이에서만 사용해요. */
    protected val binding: FragmentPlaceholderBinding
        get() = checkNotNull(_binding) { "View가 만들어지기 전이나 사라진 뒤에는 binding을 쓸 수 없어요." }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlaceholderBinding.bind(view)
        binding.title.setText(titleRes)
        binding.description.setText(descriptionRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

package com.example.oreum

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.oreum.databinding.ActivityMainBinding

/**
 * Single Activity: 화면은 모두 Fragment로 만들고, 이 Activity는
 * 화면이 바뀌는 자리(NavHostFragment)와 하단 탭만 담당해요.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 하단 탭이 보이는 화면 (로그인·회원가입에서는 숨김) */
    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.studyFragment,
        R.id.recordsFragment,
        R.id.myPageFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 프로토타입은 밝은 화면만 있어서, 상태 표시줄 아이콘을 항상 어두운 색으로 표시
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 여백(상태 표시줄 · 키보드)은 각 Fragment가 직접 처리해요.
        // 그래야 로그인 화면의 산 배경이 화면 끝까지 꽉 차요. 하단 탭은 아래 여백을 스스로 처리해요.

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val visible = destination.id in topLevelDestinations
            binding.bottomNav.isVisible = visible
            binding.navDivider.isVisible = visible
            // 홈: 산 카드와 이어지는 크림색 탭 (프로토타입 immersive) · 다른 화면: 흰 탭 + 연한 회색 선
            val home = destination.id == R.id.homeFragment
            binding.bottomNav.backgroundTintList = ColorStateList.valueOf(if (home) 0xFFFBFAF4.toInt() else Color.WHITE)
            binding.bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(if (home) 0xBFFFFFFF.toInt() else 0xFFE3F1EC.toInt())
            binding.navDivider.setBackgroundColor(if (home) 0xFFFFFFFF.toInt() else 0xFFE1E8E5.toInt())
        }
    }
}

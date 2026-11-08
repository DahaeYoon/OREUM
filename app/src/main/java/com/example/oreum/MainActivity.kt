package com.example.oreum

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
        R.id.groupFragment,
        R.id.calendarFragment,
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
            binding.bottomNav.isVisible = destination.id in topLevelDestinations
        }
    }
}

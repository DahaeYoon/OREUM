package com.example.oreum

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        // 상태 표시줄 · 내비게이션 바 영역만큼 안쪽 여백 주기
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in topLevelDestinations
        }
    }
}

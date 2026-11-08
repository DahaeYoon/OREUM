package com.example.oreum.util

import androidx.annotation.DrawableRes
import com.example.oreum.R
import java.time.LocalDate

/** 사계절 산 배경. 지금 날짜의 계절 산을 보여 줘요. */
enum class Season(@DrawableRes val mountainBackground: Int) {
    SPRING(R.drawable.bg_mountain_spring),
    SUMMER(R.drawable.bg_mountain_summer),
    AUTUMN(R.drawable.bg_mountain_autumn),
    WINTER(R.drawable.bg_mountain_winter);

    companion object {
        fun of(date: LocalDate = LocalDate.now()): Season = when (date.monthValue) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }
    }
}

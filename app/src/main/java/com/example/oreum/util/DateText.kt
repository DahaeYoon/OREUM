package com.example.oreum.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 날짜 · 시간 표시 도우미 (화면 공용) */
object DateText {

    private val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
    private val full: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.M.d")

    fun toDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun endOfDayMillis(date: LocalDate): Long =
        date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 예) 11/5 */
    fun monthDay(date: LocalDate): String = date.format(monthDay)

    /** 예) 2026.11.5 */
    fun full(date: LocalDate): String = date.format(full)

    /** 예) D-3 · D-day · D+2 */
    fun dDay(due: LocalDate, today: LocalDate = LocalDate.now()): String {
        val n = ChronoUnit.DAYS.between(today, due)
        return when {
            n > 0 -> "D-$n"
            n == 0L -> "D-day"
            else -> "D+${-n}"
        }
    }

    /** 분 → 예) 1시간 30분 · 2시간 · 40분 */
    fun minutes(total: Int): String {
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "${m}분"
            m == 0 -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }
}

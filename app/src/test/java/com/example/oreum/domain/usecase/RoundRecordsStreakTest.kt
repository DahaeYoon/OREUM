package com.example.oreum.domain.usecase

import com.example.oreum.data.model.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundRecordsStreakTest {
    @Test
    fun countsDistinctStudyDatesAcrossGoalsAndKeepsTheStreakUntilTodayEnds() {
        val sessions = listOf(
            StudySession(date = 8, goalId = "one", minutes = 20),
            StudySession(date = 9, goalId = "one", minutes = 15),
            StudySession(date = 9, goalId = "two", minutes = 10),
            StudySession(date = 10, minutes = 0),
            StudySession(date = 11, minutes = 30),
        )
        val streak = RoundRecords.streak(sessions, emptySet(), today = 10)

        assertEquals(2, streak.days)
        assertFalse(streak.studiedToday)
    }

    @Test
    fun restDaysBridgeStudyDatesWithoutAddingDays() {
        val sessions = listOf(StudySession(date = 7, minutes = 20), StudySession(date = 9, minutes = 15))
        val streak = RoundRecords.streak(sessions, setOf(8, 10), today = 10)

        assertEquals(2, streak.days)
        assertTrue(streak.restingToday)
        assertFalse(streak.studiedToday)
        assertEquals(2, RoundRecords.streak(sessions, setOf(8, 10), today = 11).days)
        assertEquals(0, RoundRecords.streak(sessions, setOf(8), today = 11).days)
    }

    @Test
    fun anUnmarkedMissedDayEndsTheStreakEvenWithALaterRestDay() {
        val sessions = listOf(StudySession(date = 7, minutes = 20))
        assertEquals(0, RoundRecords.streak(sessions, setOf(9), today = 10).days)
    }

    @Test
    fun studyingOnARestDayCountsOnce() {
        val sessions = listOf(StudySession(date = 8, minutes = 20), StudySession(date = 9, minutes = 15))
        val streak = RoundRecords.streak(sessions, setOf(9), today = 9)

        assertEquals(2, streak.days)
        assertTrue(streak.studiedToday)
    }
}

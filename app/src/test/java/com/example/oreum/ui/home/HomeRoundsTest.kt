package com.example.oreum.ui.home

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRoundsTest {
    @Test
    fun combinedCardsPutTheFirstTaskFromEachGoalUpFront() {
        val directory = RoundItem(id = "directory", title = "디렉토리 이동 명령어 연습")
        val pointer = RoundItem(id = "pointer", title = "포인터 개념요약 및 예제 3개")
        val permissions = RoundItem(id = "permissions", title = "권한 설정 연습")
        val linux = StudyRound(id = "linux-round", status = RoundStatus.ACTIVE, items = listOf(directory, permissions))
        val coding = StudyRound(id = "coding-round", status = RoundStatus.ACTIVE, items = listOf(pointer))
        val linuxTitle = "리눅스 공부"
        val codingTitle = "C언어 코딩테스트 준비"
        val cards = combinedNextCards(listOf(
            GoalNextCard(NextCard.Next(linux, directory, linuxTitle), linuxTitle),
            GoalNextCard(NextCard.Next(coding, pointer, codingTitle), codingTitle),
        ))

        val tasks = cards.map { it.next as NextCard.Next }
        assertEquals(listOf(directory, pointer, permissions), tasks.map { it.item })
        assertEquals(listOf(linux.id, coding.id, linux.id), tasks.map { it.round.id })
    }

    @Test
    fun combinedCardsShowLaterReadyItemsAndSkipCompletedItems() {
        val first = RoundItem(id = "first", title = "배열")
        val pointer = RoundItem(id = "pointer", title = "포인터 개념요약 및 예제 3개")
        val done = RoundItem(id = "done", done = true)
        val round = StudyRound(id = "coding-round", status = RoundStatus.READY, items = listOf(done, first, pointer))
        val title = "C언어 코딩테스트 준비"
        val cards = combinedNextCards(listOf(GoalNextCard(NextCard.Ready(round, title), title)))

        val tasks = cards.map { it.next as NextCard.Ready }
        assertEquals(listOf(first, pointer), tasks.map { it.item })
        assertTrue(tasks.all { it.round.id == round.id })
    }

    @Test
    fun combinedItemsIncludeGoalsBeyondTheNextCard() {
        val earlier = Goal(id = "earlier", title = "수학 공부", dueAt = 1L)
        val coding = Goal(id = "coding", title = "C언어 코딩테스트 준비", dueAt = 2L)
        val pointerItem = RoundItem(id = "pointer", title = "포인터 개념요약 및 예제 3개")
        val groups = HomeRounds(
            goals = listOf(coding, earlier),
            rounds = listOf(
                StudyRound(goalId = earlier.id, status = RoundStatus.ACTIVE, items = listOf(RoundItem(id = "math"))),
                StudyRound(goalId = coding.id, status = RoundStatus.READY, items = listOf(pointerItem)),
            ),
        ).combined()

        assertEquals(listOf(earlier.title, coding.title), groups.map { it.goalTitle })
        assertEquals(listOf(pointerItem), groups[1].round.items)
    }

    @Test
    fun combinedItemsUseTheLatestUnfinishedRoundOfEachActiveGoal() {
        val currentGoal = Goal(id = "current")
        val completedGoal = Goal(id = "completed", completedAt = 1L)
        val historyOnly = Goal(id = "history")
        val current = StudyRound(
            id = "current-round",
            goalId = currentGoal.id,
            no = 3,
            status = RoundStatus.ACTIVE,
            items = listOf(RoundItem(id = "checked", done = true), RoundItem(id = "remaining")),
        )
        val rounds = listOf(
            current.copy(id = "older-round", no = 2),
            current.copy(id = "submitted", no = 4, status = RoundStatus.DONE),
            current.copy(id = "completed-goal", goalId = completedGoal.id),
            current.copy(id = "historical-round", goalId = historyOnly.id, status = RoundStatus.DONE),
            current,
        )
        val groups = HomeRounds(listOf(currentGoal, completedGoal, historyOnly), rounds).combined()

        assertEquals(listOf(current), groups.map { it.round })
        assertTrue(HomeRounds(listOf(currentGoal), listOf(current.copy(items = emptyList()))).combined().isEmpty())
    }
}

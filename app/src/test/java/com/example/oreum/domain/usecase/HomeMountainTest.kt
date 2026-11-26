package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoadmapStage
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMountainTest {
    private val earlierGoal = Goal(
        id = "earlier",
        dueAt = 1L,
        planStatus = PlanStatus.APPROVED,
        roadmap = listOf(RoadmapStage("First stage", hours = 1.0)),
    )
    private val laterGoal = Goal(
        id = "later",
        dueAt = 2L,
        planStatus = PlanStatus.APPROVED,
        roadmap = listOf(RoadmapStage("Basics", hours = 1.0), RoadmapStage("Practice", hours = 1.0)),
        stage = 1,
    )
    private val round = StudyRound(
        id = "current",
        goalId = earlierGoal.id,
        status = RoundStatus.ACTIVE,
        items = listOf(
            RoundItem(id = "first", minutes = 15, done = true),
            RoundItem(id = "second", minutes = 45),
        ),
    )

    @Test
    fun combinedTrailShowsEveryActiveApprovedRoadmap() {
        val completedGoal = laterGoal.copy(id = "completed", completedAt = 3L)
        val pendingGoal = earlierGoal.copy(id = "pending", planStatus = PlanStatus.DRAFT)
        val display = HomeMountain.display(
            goals = listOf(laterGoal, completedGoal, pendingGoal, earlierGoal),
            rounds = listOf(round),
            goalId = null,
            round = round,
        )

        assertEquals(display.roadmapProgress, display.trailProgress)
        assertEquals(1, display.trailProgress.done)
        assertEquals(3, display.trailProgress.total)
        assertEquals(1.25f / 3f, display.trailProgress.rate, 0.0001f)
        assertEquals(listOf("earlier", "later", "later"), display.slots.map { it.goalId })
        assertEquals(listOf(false, true, false), display.slots.map { it.done })
    }

    @Test
    fun individualTrailStillShowsCurrentRoundItems() {
        val display = HomeMountain.display(listOf(earlierGoal, laterGoal), listOf(round), earlierGoal.id, round)

        assertEquals(0.25f, display.roadmapProgress.rate, 0.0001f)
        assertEquals(0.5f, display.trailProgress.rate, 0.0001f)
        assertEquals(listOf("it:current:first", "it:current:second"), display.slots.map { it.id })
    }

    @Test
    fun combinedTrailWithoutActiveRoadmapsIsEmpty() {
        val completedGoal = laterGoal.copy(completedAt = 3L)
        val pendingGoal = earlierGoal.copy(planStatus = PlanStatus.DRAFT)
        val display = HomeMountain.display(listOf(completedGoal, pendingGoal), emptyList(), null, null)

        assertEquals(0, display.trailProgress.total)
        assertEquals(0f, display.trailProgress.rate, 0f)
        assertTrue(display.slots.isEmpty())
    }
}

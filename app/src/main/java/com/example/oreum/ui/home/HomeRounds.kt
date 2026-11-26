package com.example.oreum.ui.home

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound

data class HomeRoundGroup(val goalTitle: String, val round: StudyRound)

internal class HomeRounds(goals: List<Goal>, rounds: List<StudyRound>) {
    val activeGoals = goals.filter { it.completedAt == null }.sortedBy { it.dueAt }

    private val currentByGoal = rounds
        .filter { it.status != RoundStatus.DONE }
        .groupBy { it.goalId }
        .mapValues { (_, goalRounds) -> goalRounds.maxBy { it.no } }

    fun current(goal: Goal): StudyRound? = currentByGoal[goal.id]

    fun combined(): List<HomeRoundGroup> = activeGoals.mapNotNull { goal ->
        current(goal)?.takeIf { it.items.isNotEmpty() }?.let { HomeRoundGroup(goal.title, it) }
    }
}

/** 먼저 목표마다 카드 한 장씩, 이어서 아직 끝내지 않은 나머지 항목을 보여 줘요. */
internal fun combinedNextCards(goalCards: List<GoalNextCard>): List<GoalNextCard> {
    val perGoal = goalCards.filter { it.next != NextCard.Empty }.map { card ->
        when (val next = card.next) {
            is NextCard.Next -> next.round.items.filterNot { it.done }.map { item ->
                card.copy(next = next.copy(item = item))
            }
            is NextCard.Ready -> next.round.items.filterNot { it.done }.map { item ->
                card.copy(next = next.copy(item = item))
            }.ifEmpty { listOf(card) }
            else -> listOf(card)
        }
    }
    return (perGoal.mapNotNull { it.firstOrNull() } + perGoal.flatMap { it.drop(1) })
        .ifEmpty { listOf(GoalNextCard(NextCard.Empty)) }
}

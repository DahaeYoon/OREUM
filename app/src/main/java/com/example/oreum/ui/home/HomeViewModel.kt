package com.example.oreum.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.HomeMountain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 홈에서 고른 산 (앱이 켜져 있는 동안 기억). null = 전체 목표 합쳐 보기 */
object HomePrefs {
    var goalId: String? = null
    var picked: Boolean = false
    /** 방금 정상에 도착한 목표: 홈에 돌아오면 정상 도착 팝업을 한 번 보여 줘요 */
    var summitGoalId: String? = null
}

/** 산 아래 패널의 카드 종류 (프로토타입 홈 next-card) */
sealed interface NextCard {
    /** 로드맵이 없어요 → AI 로드맵 (ai = 만드는 중 · 실패) */
    data class NoPlan(val goalId: String, val ai: AiRequests.State? = null) : NextCard
    /** AI 로드맵 승인 대기 → 확인 */
    data class Draft(val goalId: String) : NextCard
    /** AI 다음 구간 추천 도착 → 확인 (round = null이면 마지막 → 정상 도착) */
    data class Proposal(val goalId: String, val round: StudyRound?) : NextCard
    /** 구간 결과를 제출했는데 다음 구간 추천이 아직 없어요 → AI 추천 받기 (ai = 만드는 중 · 실패) */
    data class NeedProposal(val goalId: String, val lastNo: Int, val ai: AiRequests.State?) : NextCard
    /** 승인했지만 시작 전 구간 → 시작 */
    data class Ready(val round: StudyRound, val goalTitle: String, val item: RoundItem? = null) : NextCard
    /** 진행 중 구간의 다음 항목 → 완료 */
    data class Next(val round: StudyRound, val item: RoundItem, val goalTitle: String) : NextCard
    /** 구간 항목을 모두 끝냄 → 결과 제출 */
    data class AllDone(val round: StudyRound, val goalTitle: String) : NextCard
    data object Empty : NextCard
    data object Top : NextCard
}

data class GoalNextCard(val next: NextCard, val goalTitle: String? = null)

data class HomeUiState(
    val loaded: Boolean = false,
    val nickname: String = "",
    /** null = 전체 목표 */
    val goal: Goal? = null,
    /** 마감 표시 · 카드에 쓰는 목표 (전체일 때는 지금 구간이 있는 가장 가까운 목표) */
    val focusGoal: Goal? = null,
    /** 로드맵 전체 진행률 (패널의 단계 k/n · % · 막대) */
    val progress: HomeMountain.Progress = HomeMountain.Progress(0, 0, 0f, 0),
    /** 산 진행률: 전체 로드맵 또는 선택한 목표의 이번 구간 */
    val climb: HomeMountain.Progress = HomeMountain.Progress(0, 0, 0f, 0),
    /** 산 위 점: 전체 로드맵 단계 또는 이번 구간 항목 */
    val slots: List<HomeMountain.Slot> = emptyList(),
    val nextCards: List<GoalNextCard> = listOf(GoalNextCard(NextCard.Empty)),
    /** 패널 버튼(항목 모두 보기)에 쓰는 지금 구간 */
    val round: StudyRound? = null,
    val roundGroups: List<HomeRoundGroup> = emptyList(),
    val goals: List<Goal> = emptyList(),
)

/** 홈의 산과 다음 학습 활동 */
class HomeViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
    private val auth: AuthRepository = AuthRepositoryProvider.instance,
) : ViewModel() {

    private val selected = MutableStateFlow(HomePrefs.goalId)

    init {
        // 처음 들어오면 첫 번째 진행 중인 목표의 산
        if (!HomePrefs.picked) {
            HomePrefs.goalId = repository.goals.value.firstOrNull { it.completedAt == null }?.id
            HomePrefs.picked = true
        }
        selected.value = HomePrefs.goalId
    }

    /** 완료 애니메이션 시작점 (화면이 한 번 읽고 비워요) */
    private var animateFrom: Float? = null

    private fun today() = LocalDate.now().toEpochDay()

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(repository.goals, repository.rounds) { g, r -> g to r },
            combine(repository.roadmapDrafts, AiRequests.states) { d, a -> d to a },
            repository.proposals,
            auth.currentUser,
            selected,
        ) { (goals, rounds), (drafts, ai), proposals, user, sel0 ->
            val sel = sel0?.takeIf { id -> goals.any { it.id == id } }
            val goal = goals.firstOrNull { it.id == sel }
            val homeRounds = HomeRounds(goals, rounds)

            // 전체 보기: 추천 · 지금 구간이 있는 가장 가까운 목표 → 승인 대기 목표
            val active = homeRounds.activeGoals
            val needNext = { g: Goal -> AiRequests.needsProposal(g, rounds, proposals) }
            val focus = goal ?: active.firstOrNull { proposals.containsKey(it.id) || homeRounds.current(it) != null || needNext(it) }
                ?: active.firstOrNull { drafts.containsKey(it.id) } ?: active.firstOrNull()

            val round = focus?.let(homeRounds::current)
            // 개별 산은 지금 구간이나 마지막으로 마친 구간을 보여 줘요.
            val climbRound = goal?.let { g ->
                round ?: rounds.filter { it.goalId == g.id && it.status == RoundStatus.DONE }.maxByOrNull { it.no }
            }
            val mountain = HomeMountain.display(goals, rounds, sel, climbRound)
            fun nextFor(target: Goal): NextCard {
                val current = homeRounds.current(target)
                return when {
                    target.completedAt != null -> NextCard.Top
                    drafts.containsKey(target.id) -> NextCard.Draft(target.id)
                    target.planStatus == PlanStatus.NONE || target.roadmap.isEmpty() ->
                        NextCard.NoPlan(target.id, ai[AiRequests.roadmapKey(target.id)])
                    proposals.containsKey(target.id) -> NextCard.Proposal(target.id, proposals.getValue(target.id).round)
                    current == null && needNext(target) -> NextCard.NeedProposal(
                        target.id,
                        rounds.filter { it.goalId == target.id }.maxOf { it.no },
                        ai[AiRequests.nextKey(target.id)],
                    )
                    current == null -> NextCard.Empty
                    current.status == RoundStatus.READY -> NextCard.Ready(current, target.title)
                    else -> current.items.firstOrNull { !it.done }?.let { NextCard.Next(current, it, target.title) }
                        ?: NextCard.AllDone(current, target.title)
                }
            }
            val next = focus?.let(::nextFor) ?: NextCard.Empty
            val nextCards = if (goal != null || focus == null) {
                listOf(GoalNextCard(next))
            } else {
                combinedNextCards(
                    listOf(GoalNextCard(next, focus.title)) +
                        active.filterNot { it.id == focus.id }.map { GoalNextCard(nextFor(it), it.title) },
                )
            }
            HomeUiState(
                loaded = true,
                nickname = user?.nickname.orEmpty(),
                goal = goal,
                focusGoal = focus,
                progress = mountain.roadmapProgress,
                climb = mountain.trailProgress,
                slots = mountain.slots,
                nextCards = nextCards,
                round = round?.takeIf { next is NextCard.Next || next is NextCard.Ready || next is NextCard.AllDone },
                roundGroups = if (goal == null) homeRounds.combined() else emptyList(),
                goals = goals,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectGoal(goalId: String?) {
        HomePrefs.goalId = goalId
        HomePrefs.picked = true
        selected.value = goalId
    }

    fun takeAnimateFrom(): Float? = animateFrom.also { animateFrom = null }

    private fun climbRate(goalId: String?, roundId: String): Float =
        if (goalId == null) {
            HomeMountain.roundProgress(repository.goals.value, repository.rounds.value, null).rate
        } else {
            HomeMountain.climbProgress(repository.round(roundId)).rate
        }

    /** 항목 완료 체크 · 해제: 앞뒤 산 진행률을 돌려줘요 (점이 다음 점으로 올라가요) */
    fun toggleItem(roundId: String, itemId: String): TickResult? {
        val title = repository.round(roundId)?.items?.firstOrNull { it.id == itemId }?.title ?: return null
        val goalId = uiState.value.goal?.id
        val before = climbRate(goalId, roundId)
        // 저장하면 화면이 바로 다시 그려질 수 있어서, 시작점을 먼저 넣어 둬요
        animateFrom = before
        val r = repository.toggleItem(roundId, itemId, today())
        if (r == null) {
            animateFrom = null
            return null
        }
        val after = climbRate(goalId, roundId)
        if (after <= before) animateFrom = null
        val done = r.items.firstOrNull { it.id == itemId }?.done == true
        return TickResult(title, before, after, done, r.allDone, r.no, r.doneCount, r.items.size, goalId)
    }

    fun startRound(roundId: String): StudyRound? {
        repository.startRound(roundId, today())
        return repository.round(roundId)
    }

    data class TickResult(
        val title: String,
        val before: Float,
        val after: Float,
        val nowDone: Boolean,
        val roundAllDone: Boolean,
        val roundNo: Int,
        val doneCount: Int = 0,
        val total: Int = 0,
        val displayedGoalId: String? = null,
    )
}

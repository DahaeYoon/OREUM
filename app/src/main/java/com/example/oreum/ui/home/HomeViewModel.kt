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
    data class Ready(val round: StudyRound, val goalTitle: String) : NextCard
    /** 진행 중 구간의 다음 항목 → 완료 */
    data class Next(val round: StudyRound, val item: RoundItem, val goalTitle: String) : NextCard
    /** 구간 항목을 모두 끝냄 → 결과 제출 */
    data class AllDone(val round: StudyRound, val goalTitle: String) : NextCard
    data object Empty : NextCard
    data object Top : NextCard
}

data class HomeUiState(
    val loaded: Boolean = false,
    val nickname: String = "",
    /** null = 전체 목표 */
    val goal: Goal? = null,
    /** 마감 표시 · 카드에 쓰는 목표 (전체일 때는 지금 구간이 있는 가장 가까운 목표) */
    val focusGoal: Goal? = null,
    /** 로드맵 전체 진행률 (패널의 단계 k/n · % · 막대) */
    val progress: HomeMountain.Progress = HomeMountain.Progress(0, 0, 0f, 0),
    /** 산 = 이번 구간 진행 (항목 k/n). 지금 구간이 없으면 마지막으로 마친 구간 */
    val climb: HomeMountain.Progress = HomeMountain.Progress(0, 0, 0f, 0),
    /** 산 위 점 = 이번 구간 항목 */
    val slots: List<HomeMountain.Slot> = emptyList(),
    val next: NextCard = NextCard.Empty,
    /** 패널 버튼(항목 모두 보기)에 쓰는 지금 구간 */
    val round: StudyRound? = null,
    val goals: List<Goal> = emptyList(),
)

/** 홈: 산(진행률 · 체크포인트 = 로드맵 단계) + 이번 구간 */
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
            val curOf = { g: Goal -> rounds.filter { it.goalId == g.id && it.status != RoundStatus.DONE }.maxByOrNull { it.no } }

            // 전체 보기: 추천 · 지금 구간이 있는 가장 가까운 목표 → 승인 대기 목표
            val active = goals.filter { it.completedAt == null }.sortedBy { it.dueAt }
            val needNext = { g: Goal -> AiRequests.needsProposal(g, rounds, proposals) }
            val focus = goal ?: active.firstOrNull { proposals.containsKey(it.id) || curOf(it) != null || needNext(it) }
                ?: active.firstOrNull { drafts.containsKey(it.id) } ?: active.firstOrNull()

            val round = focus?.let(curOf)
            // 산에 보여 줄 구간: 지금 구간, 없으면 마지막으로 마친 구간 (정상에 도착한 모습)
            val climbRound = round ?: focus?.let { g -> rounds.filter { it.goalId == g.id && it.status == RoundStatus.DONE }.maxByOrNull { it.no } }
            val next: NextCard = when {
                focus == null -> NextCard.Empty
                focus.completedAt != null -> NextCard.Top
                drafts.containsKey(focus.id) -> NextCard.Draft(focus.id)
                focus.planStatus == PlanStatus.NONE || focus.roadmap.isEmpty() ->
                    NextCard.NoPlan(focus.id, ai[AiRequests.roadmapKey(focus.id)])
                proposals.containsKey(focus.id) -> NextCard.Proposal(focus.id, proposals.getValue(focus.id).round)
                round == null && needNext(focus) -> NextCard.NeedProposal(
                    focus.id,
                    rounds.filter { it.goalId == focus.id }.maxOf { it.no },
                    ai[AiRequests.nextKey(focus.id)],
                )
                round == null -> NextCard.Empty
                round.status == RoundStatus.READY -> NextCard.Ready(round, focus.title)
                else -> round.items.firstOrNull { !it.done }?.let { NextCard.Next(round, it, focus.title) }
                    ?: NextCard.AllDone(round, focus.title)
            }
            HomeUiState(
                loaded = true,
                nickname = user?.nickname.orEmpty(),
                goal = goal,
                focusGoal = focus,
                progress = HomeMountain.roundProgress(goals, rounds, sel),
                climb = HomeMountain.climbProgress(climbRound),
                slots = HomeMountain.climbSlots(climbRound),
                next = next,
                round = round?.takeIf { next is NextCard.Next || next is NextCard.Ready || next is NextCard.AllDone },
                goals = goals,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectGoal(goalId: String?) {
        HomePrefs.goalId = goalId
        HomePrefs.picked = true
        selected.value = goalId
    }

    fun takeAnimateFrom(): Float? = animateFrom.also { animateFrom = null }

    /** 산 진행률 = 이번 구간 항목 완료 비율 */
    private fun climbRate(roundId: String): Float = HomeMountain.climbProgress(repository.round(roundId)).rate

    /** 항목 완료 체크 · 해제: 앞뒤 산 진행률을 돌려줘요 (점이 다음 점으로 올라가요) */
    fun toggleItem(roundId: String, itemId: String): TickResult? {
        val title = repository.round(roundId)?.items?.firstOrNull { it.id == itemId }?.title ?: return null
        val before = climbRate(roundId)
        // 저장하면 화면이 바로 다시 그려질 수 있어서, 시작점을 먼저 넣어 둬요
        animateFrom = before
        val r = repository.toggleItem(roundId, itemId, today())
        if (r == null) {
            animateFrom = null
            return null
        }
        val after = climbRate(roundId)
        if (after <= before) animateFrom = null
        val done = r.items.firstOrNull { it.id == itemId }?.done == true
        return TickResult(title, before, after, done, r.allDone, r.no, r.doneCount, r.items.size)
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
    )
}

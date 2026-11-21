package com.example.oreum.data.remote

import com.example.oreum.data.local.LocalStudyRepository
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import com.example.oreum.data.repository.StudyRepository
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 2번: Firestore에 계정별로 저장하는 저장소.
 *
 * 화면 · AI 계산은 지금처럼 LocalStudyRepository(메모리)를 그대로 쓰고, 이 클래스는 저장만 맡아요.
 * - 로그인하면 users/{uid}/... 에서 내 데이터를 한 번 불러와 메모리에 채워요.
 * - 메모리 데이터가 바뀌면 (0.3초 모아서) 바뀐 문서만 Firestore에 쓰고, 없어진 문서는 지워요.
 * - 인터넷이 끊겨도 Firestore가 기기에 먼저 저장해 두었다가 연결되면 보내요.
 *
 * 저장 구조: users/{uid}/goals · rounds · sessions · roadmapDrafts(문서 id = 목표 id) · proposals(문서 id = 목표 id)
 * 보안 규칙: 로그인한 본인만 자기 users/{uid} 아래를 읽고 쓸 수 있어요 (Firebase 콘솔 → Firestore → 규칙).
 */
@OptIn(FlowPreview::class)
class FirestoreStudyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val local: LocalStudyRepository = LocalStudyRepository(ownerId = "", withSample = false),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : StudyRepository by local {

    /** 내 데이터를 불러오는 중이면 true (화면에서 '불러오는 중' 표시에 쓸 수 있어요) */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var uid: String? = null
    private var job: Job? = null

    /** Firestore에 마지막으로 보낸 상태 (바뀐 것만 보내려고 기억해요) */
    private data class Snapshot(
        val goals: Map<String, Goal> = emptyMap(),
        val rounds: Map<String, StudyRound> = emptyMap(),
        val sessions: Map<String, StudySession> = emptyMap(),
        val drafts: Map<String, RoadmapDraft> = emptyMap(),
        val proposals: Map<String, NextProposal> = emptyMap(),
    )
    private var synced = Snapshot()

    override fun switchOwner(ownerId: String?) {
        if (ownerId == uid) return
        job?.cancel()
        uid = ownerId
        synced = Snapshot()
        local.switchOwner(ownerId)
        if (ownerId == null) return
        // 다른 기기 · 이전 실행에서 저장한 내용이 기준이라, 메모리에 남아 있던 내용은 비우고 불러와요
        local.clearAll()
        job = scope.launch {
            _loading.value = true
            val loaded = runCatching { load(ownerId) }.getOrNull()
            _loading.value = false
            if (loaded != null) {
                synced = loaded
                local.replaceAll(
                    goals = loaded.goals.values.sortedBy { it.createdAt },
                    rounds = loaded.rounds.values.sortedWith(compareBy({ it.goalId }, { it.no })),
                    sessions = loaded.sessions.values.sortedWith(compareBy({ it.date }, { it.id })),
                    drafts = loaded.drafts,
                    proposals = loaded.proposals,
                )
            }
            // 이제부터 바뀌는 내용을 저장해요
            combine(local.goals, local.rounds, local.sessions, local.roadmapDrafts, local.proposals) { g, r, s, d, p ->
                Snapshot(g.associateBy { it.id }, r.associateBy { it.id }, s.associateBy { it.id }, d, p)
            }
                .drop(1)
                .debounce(SAVE_DELAY_MS)
                .collectLatest { now -> save(ownerId, now) }
        }
    }

    // ---------------- 불러오기 ----------------
    private suspend fun load(uid: String): Snapshot {
        val goals = scope.async { col(uid, GOALS).get().await() }
        val rounds = scope.async { col(uid, ROUNDS).get().await() }
        val sessions = scope.async { col(uid, SESSIONS).get().await() }
        val drafts = scope.async { col(uid, DRAFTS).get().await() }
        val proposals = scope.async { col(uid, PROPOSALS).get().await() }
        fun docs(q: com.google.firebase.firestore.QuerySnapshot) = q.documents.mapNotNull { d -> d.data?.let { d.id to it } }
        return Snapshot(
            goals = docs(goals.await()).associate { (id, m) -> id to FirestoreMapper.toGoal(m) },
            rounds = docs(rounds.await()).associate { (id, m) -> id to FirestoreMapper.toRound(m) },
            sessions = docs(sessions.await()).associate { (id, m) -> id to FirestoreMapper.toSession(m) },
            drafts = docs(drafts.await()).associate { (id, m) -> id to FirestoreMapper.toDraft(m) },
            proposals = docs(proposals.await()).associate { (id, m) -> id to FirestoreMapper.toProposal(m) },
        )
    }

    // ---------------- 저장 (바뀐 문서만) ----------------
    private sealed interface Op {
        data class Set(val ref: DocumentReference, val data: Map<String, Any?>) : Op
        data class Delete(val ref: DocumentReference) : Op
    }

    private suspend fun save(uid: String, now: Snapshot) {
        val before = synced
        val ops = mutableListOf<Op>()
        fun <T> diff(name: String, old: Map<String, T>, new: Map<String, T>, enc: (T) -> Map<String, Any?>) {
            new.forEach { (id, v) -> if (old[id] != v) ops += Op.Set(col(uid, name).document(id), enc(v)) }
            old.keys.filterNot { it in new }.forEach { id -> ops += Op.Delete(col(uid, name).document(id)) }
        }
        diff(GOALS, before.goals, now.goals, FirestoreMapper::goal)
        diff(ROUNDS, before.rounds, now.rounds, FirestoreMapper::round)
        diff(SESSIONS, before.sessions, now.sessions, FirestoreMapper::session)
        diff(DRAFTS, before.drafts, now.drafts, FirestoreMapper::draft)
        diff(PROPOSALS, before.proposals, now.proposals, FirestoreMapper::proposal)
        if (ops.isEmpty()) return
        // 먼저 '보냈다'고 기억해요: 인터넷이 끊겨 있어도 Firestore가 기기에 저장해 두었다가 보내요
        synced = now
        ops.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { op ->
                when (op) {
                    is Op.Set -> batch.set(op.ref, op.data)
                    is Op.Delete -> batch.delete(op.ref)
                }
            }
            runCatching { batch.commit().await() }
        }
    }

    private fun col(uid: String, name: String): CollectionReference =
        db.collection(USERS).document(uid).collection(name)

    private companion object {
        const val USERS = "users"
        const val GOALS = "goals"
        const val ROUNDS = "rounds"
        const val SESSIONS = "sessions"
        const val DRAFTS = "roadmapDrafts"
        const val PROPOSALS = "proposals"
        const val SAVE_DELAY_MS = 300L
        /** Firestore 한 번에 묶어 쓸 수 있는 최대 개수(500)보다 조금 작게 */
        const val BATCH_LIMIT = 400
    }
}

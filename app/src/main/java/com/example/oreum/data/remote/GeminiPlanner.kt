package com.example.oreum.data.remote

import android.util.Log
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import com.example.oreum.data.repository.AiFailure
import com.example.oreum.data.repository.AiPlanner
import com.example.oreum.data.repository.AiResult
import com.example.oreum.domain.usecase.AiPlanSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Firebase AI Logic (Gemini Developer API)로 계획 만들기.
 * - API 키는 앱에 없어요. Firebase가 우리 프로젝트의 Gemini를 대신 불러 주고, App Check로 진짜 앱인지 확인해요.
 * - 결과는 JSON 형식(responseSchema)으로 받고, AiPlanSpec이 검사해요.
 * - 실패하면 시뮬레이션으로 대신하지 않고 실패 이유를 돌려줘요.
 */
class GeminiPlanner(
    private val modelName: String = MODEL,
) : AiPlanner {

    private val roadmapModel: GenerativeModel by lazy { model(ROADMAP_SCHEMA) }
    private val nextModel: GenerativeModel by lazy { model(NEXT_SCHEMA) }

    override suspend fun roadmap(goal: Goal, today: Long): AiResult<RoadmapDraft> =
        call { AiPlanSpec.parseRoadmap(ask(roadmapModel, AiPlanSpec.roadmapPrompt(goal, today)), goal, today) }

    override suspend fun nextRound(
        goal: Goal,
        rounds: List<StudyRound>,
        sessions: List<StudySession>,
        today: Long,
        baseline: NextProposal,
    ): AiResult<NextProposal> = call {
        val prompt = AiPlanSpec.nextPrompt(goal, rounds, sessions, today, baseline)
        AiPlanSpec.parseNext(ask(nextModel, prompt), goal, rounds, today, baseline)
    }

    private fun model(schema: Schema): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.4f
                // 답 길이 상한 (사용량 아끼기). 생각 과정까지 포함되는 모델이 있어 넉넉히 둬요
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(AiPlanSpec.SYSTEM) },
        )

    /** 요청을 보내고 JSON 답을 Map으로 바꿔요 (60초 안에 답이 없으면 실패) */
    private suspend fun ask(model: GenerativeModel, prompt: String): Map<String, Any?> {
        val text = withTimeout(TIMEOUT_MS) { model.generateContent(prompt).text }
            ?: throw AiPlanSpec.BadResponse("빈 응답")
        val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return toMap(JSONObject(json))
    }

    private suspend fun <T> call(block: suspend () -> T): AiResult<T> = try {
        AiResult.Ok(block())
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "AI 시간 초과", e)
        AiResult.Fail(AiFailure.NETWORK)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "AI 실패: ${e::class.java.simpleName}: ${e.message}", e)
        AiResult.Fail(classify(e))
    }

    /** 오류 → 실패 이유 (클래스 이름 · 메시지로 판단해요) */
    private fun classify(e: Throwable): AiFailure {
        val chain = generateSequence(e) { it.cause }.take(5).toList()
        val names = chain.joinToString(" ") { it::class.java.simpleName }
        val text = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            e is AiPlanSpec.BadResponse || names.contains("JSONException") || names.contains("SerializationException") -> AiFailure.BAD_RESPONSE
            chain.any { it is IOException } || "unable to resolve host" in text || "timeout" in text -> AiFailure.NETWORK
            names.contains("QuotaExceeded") || "429" in text || "resource_exhausted" in text || "quota" in text ->
                // 하루 한도 (예: GenerateRequestsPerDay...)인지, 분당 한도인지 구분해요
                if ("perday" in text || "per day" in text || "per_day" in text || "daily" in text) AiFailure.DAILY_LIMIT else AiFailure.BUSY
            "app check" in text || "appcheck" in text || "app-check" in text || names.contains("ServiceDisabled") ||
                names.contains("InvalidAPIKey") || "403" in text || "permission" in text -> AiFailure.SETUP
            else -> AiFailure.UNKNOWN
        }
    }

    private fun toMap(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { k -> toValue(o.opt(k)) }
    private fun toValue(v: Any?): Any? = when (v) {
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { toValue(v.opt(it)) }
        JSONObject.NULL -> null
        else -> v
    }

    companion object {
        private const val TAG = "OreumAI"
        private const val TIMEOUT_MS = 60_000L

        /** 쓰는 모델 (Firebase 콘솔 · AI Logic에서 지원 모델을 확인하고 바꿀 수 있어요) */
        const val MODEL = "gemini-3.5-flash"

        private val ITEM = Schema.obj(
            mapOf("title" to Schema.string(), "minutes" to Schema.integer()),
        )
        private val NEXT_ITEM = Schema.obj(
            mapOf(
                "title" to Schema.string(),
                "minutes" to Schema.integer(),
                "kind" to Schema.enumeration(listOf("NEW", "CARRY", "REVIEW")),
            ),
        )

        /** 로드맵 + 1구간 */
        private val ROADMAP_SCHEMA = Schema.obj(
            mapOf(
                "stages" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "title" to Schema.string(),
                            "topics" to Schema.array(Schema.string()),
                            "minutes" to Schema.integer(),
                        ),
                    ),
                ),
                "round" to Schema.obj(
                    mapOf(
                        "topic" to Schema.string(),
                        "topics" to Schema.array(Schema.string()),
                        "items" to Schema.array(ITEM),
                        "method" to Schema.array(Schema.string()),
                        "criteria" to Schema.array(Schema.string()),
                    ),
                ),
            ),
        )

        /** 다음 구간 */
        private val NEXT_SCHEMA = Schema.obj(
            mapOf(
                "stage" to Schema.integer(),
                "topic" to Schema.string(),
                "topics" to Schema.array(Schema.string()),
                "items" to Schema.array(NEXT_ITEM),
                "method" to Schema.array(Schema.string()),
                "criteria" to Schema.array(Schema.string()),
                "why" to Schema.array(Schema.string()),
                "fixes" to Schema.array(Schema.string()),
            ),
        )
    }
}

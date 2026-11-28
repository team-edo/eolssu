package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.UserActionEvent
import com.ssafy.eolssu.core.diagnostics.QaKind
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.QaOutcome
import com.ssafy.eolssu.core.diagnostics.QaInteractionContext
import com.ssafy.eolssu.core.diagnostics.EvaluationTiming
import com.ssafy.eolssu.core.diagnostics.PerformanceFlow
import com.ssafy.eolssu.core.diagnostics.PerfKind
import com.ssafy.eolssu.core.diagnostics.PerfMethod
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceLoad
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceReason
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 서버 주소. 앱은 [Production], 웹은 페이지와 같은 주소를 쓴다. 각 플랫폼 모듈이 등록한다. */
class ServerAddress(val baseUrl: String) {
    companion object {
        val Production = ServerAddress("https://eolssu.site")
    }
}

/**
 * 서버 응답의 공통 형식 `{httpStatus, isSuccess, message, code, result}`. 실패 응답의 [result]는 [ApiError]다.
 * [httpStatus]는 상태 이름(`"SERVICE_UNAVAILABLE"`), [code]는 상태 번호(503)다. 응답 코드는 `HttpResponse.status`로도 볼 수 있다.
 */
@Serializable
class ApiResponse<T>(
    val isSuccess: Boolean,
    val httpStatus: String = "",
    val message: String = "",
    val code: Int = 0,
    val result: T? = null,
)

/** 실패 응답의 `result`. */
@Serializable
class ApiError(val errorCode: String? = null, val details: kotlinx.serialization.json.JsonObject? = null)

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val authRefreshSkipped = AttributeKey<Unit>("AuthRefreshSkipped")

/**
 * 앱 전체가 쓰는 HTTP 클라이언트. 응답 코드는 부르는 쪽이 판단한다(`expectSuccess = false`).
 * 로그인 쿠키는 [cookies]에 둔다. 웹은 브라우저가 쿠키를 보관하고 보내므로 null이다.
 *
 * - 시간 제한 기본값은 연결 10초, 요청 전체 30초다. 녹음 업로드처럼 오래 걸리는 요청은 요청마다
 *   `timeout { requestTimeoutMillis = ... }`로 바꾼다.
 * - 로그인이 필요한 요청이 401이면 `/api/auth/refresh`로 토큰을 갱신하고 같은 요청을 한 번 더 보낸다. 본문도 다시 보내므로,
 *   multipart의 파일 칸은 스트림을 매번 새로 여는 `ChannelProvider { ... }`나 바이트 배열로 넣는다(이미 연 스트림은 두 번째에 비어 있다).
 *   1회용 값(간편 로그인 state·mergeTicket)이나 비밀번호 시도 횟수를 쓰는 요청은 갱신·재전송하지 않고 원래 401을 돌려준다([skipsRefresh]).
 * - 웹은 [sharedRefresh]로 탭 사이에서도 갱신을 한 번에 하나만 한다. 다른 탭이 먼저 갱신했으면 다시 갱신하지 않고 재전송만 한다.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    server: ServerAddress,
    cookies: CookiesStorage? = null,
    sharedRefresh: SharedRefreshLock? = null,
): HttpClient {
    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
        if (cookies != null) install(HttpCookies) { storage = cookies }
        defaultRequest { url(server.baseUrl) }
    }
    val refresher = TokenRefresher(sharedRefresh)
    client.plugin(HttpSend).intercept { request ->
        val seen = refresher.generation
        val path = request.url.encodedPath
        val method = request.method
        val call = liveAttempt(request, server) { qaAttempt(request) { observedAttempt(path, method) { execute(request) } } }
        if (call.response.status != HttpStatusCode.Unauthorized ||
            request.attributes.contains(authRefreshSkipped) || skipsRefresh(method, path)
        ) return@intercept call
        val refreshed = refresher.refresh(seen) { client.post(REFRESH_PATH).status.isSuccess() }
        if (refreshed) liveAttempt(request, server, PerfReason.REPLAY) {
            qaAttempt(request) { observedAttempt(path, method, PerfReason.REPLAY) { execute(request) } }
        } else call
    }
    return client
}

/** Each actual QA HTTP attempt has its own header, including authentication replay. */
@OptIn(ExperimentalUuidApi::class)
private suspend fun liveAttempt(
    request: HttpRequestBuilder,
    server: ServerAddress,
    reason: PerfReason? = null,
    send: suspend () -> HttpClientCall,
): HttpClientCall {
    if (!PerformanceLog.liveRequestsEnabled || server.baseUrl != "http://127.0.0.1:18180" ||
        request.url.host != "127.0.0.1" || request.url.port != 18180 ||
        !request.url.encodedPath.startsWith("/api/")
    ) return send()

    val method = PerfMethod.entries.find { it.name == request.method.value } ?: return send()
    val target = when (request.url.encodedPath) {
        "/api/auth/sign-in" -> PerfTarget.AUTH_SIGN_IN
        "/api/auth/sign-up" -> PerfTarget.AUTH_SIGN_UP
        "/api/practice-sessions" -> PerfTarget.SESSION_CREATE
        else -> if (recordingEndpoint.matches(request.url.encodedPath)) PerfTarget.RECORDING_UPLOAD
            else observedTarget(request.url.encodedPath)?.first ?: PerfTarget.API
    }
    val visit = currentCoroutineContext()[PerformanceVisit] ?: PerformanceVisit(PerformanceFlow(), target)
    val requestId = Uuid.random().toString()
    request.headers.remove("X-QA-Request-Id")
    request.headers.append("X-QA-Request-Id", requestId)
    var timingId = EvaluationTiming.parseId(request.headers[EvaluationTiming.HEADER])
    val started = PerformanceLog.now()
    var status: Int? = null
    var outcome = PerfReason.FAILED
    visit.event(PerfKind.REQUEST_STARTED, target, method = method, reason = reason,
        requestId = requestId, evaluationTimingId = timingId)
    try {
        return send().also { call ->
            status = call.response.status.value
            timingId = EvaluationTiming.parseId(call.response.headers[EvaluationTiming.HEADER]) ?: timingId
            outcome = if (call.response.status.isSuccess()) PerfReason.READY else PerfReason.FAILED
        }
    } catch (error: Exception) {
        outcome = when (error) {
            is CancellationException -> PerfReason.CANCELLED
            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> PerfReason.HTTP_TIMEOUT
            else -> PerfReason.FAILED
        }
        throw error
    } finally {
        visit.event(PerfKind.REQUEST_FINISHED, target, method = method, reason = reason,
            requestId = requestId, evaluationTimingId = timingId, status = status, outcome = outcome,
            durationMs = PerformanceLog.now() - started)
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun qaAttempt(request: HttpRequestBuilder, send: suspend () -> HttpClientCall): HttpClientCall {
    if (UserActionLog.sink == null) return send()
    val interaction = currentCoroutineContext()[QaInteractionContext]?.interaction ?: UserActionLog.capture().copy(estimated = true)
    val requestId = Uuid.random().toString()
    val target = when (request.url.encodedPath) {
        "/api/auth/sign-in" -> QaTarget.LOGIN_SUBMIT
        "/api/auth/sign-up" -> QaTarget.SIGN_UP
        "/api/auth/sign-out" -> QaTarget.SIGN_OUT
        else -> QaTarget.HTTP_API
    }
    val screen = UserActionLog.screenId
    val start = UserActionLog.now()
    var status: Int? = null
    var outcome = QaOutcome.FAILED
    fun emit(kind: QaKind) = UserActionLog.emit(UserActionEvent(kind, target, interaction, screen,
        UserActionLog.now(), outcome = if (kind == QaKind.HTTP_FINISHED) outcome else null,
        requestId = requestId, status = status, durationMs = if (kind == QaKind.HTTP_FINISHED) UserActionLog.now() - start else null))
    emit(QaKind.HTTP_STARTED)
    try {
        return send().also { status = it.response.status.value; outcome = if (it.response.status.isSuccess()) QaOutcome.READY else QaOutcome.FAILED }
    } catch (cancelled: CancellationException) {
        outcome = QaOutcome.CANCELLED
        throw cancelled
    } finally { emit(QaKind.HTTP_FINISHED) }
}

private val recordingEndpoint = Regex("/api/practice-sessions/[1-9][0-9]{0,18}/recording")
private val songEndpoint = Regex("/api/songs/([1-9][0-9]{0,18})(/score|/model-audio(?:/performance|/play)?)?")
private val sessionEndpoint = Regex("/api/practice-sessions/([1-9][0-9]{0,18})(/evaluation|/weak-sections|/comparison|/retry)?")

private fun observedTarget(path: String): Pair<PerfTarget, String?>? = when (path) {
    "/api/auth/refresh" -> PerfTarget.AUTH_REFRESH to null
    "/api/users/me/practice-records", "/api/users/me/practice-records/recent" -> PerfTarget.RECORD_PAGE to null
    "/api/instruments" -> PerfTarget.INSTRUMENTS to null
    "/api/songs" -> PerfTarget.SONG_LIST to null
    else -> songEndpoint.matchEntire(path)?.let {
        when (it.groupValues[2]) {
            "/score" -> PerfTarget.SCORE
            "/model-audio" -> PerfTarget.MODEL_AUDIO_METADATA
            "/model-audio/performance" -> PerfTarget.MODEL_AUDIO_TIMELINE
            "/model-audio/play" -> PerfTarget.MODEL_AUDIO
            else -> PerfTarget.SONG_DETAIL
        } to it.groupValues[1]
    } ?: sessionEndpoint.matchEntire(path)?.let {
        when (it.groupValues[2]) {
            "/evaluation" -> PerfTarget.EVALUATION
            "/weak-sections" -> PerfTarget.WEAK_SECTIONS
            "/comparison" -> PerfTarget.COMPARISON
            else -> PerfTarget.PRACTICE_STATUS
        } to it.groupValues[1]
    }
}

private suspend fun observedAttempt(
    path: String,
    method: HttpMethod,
    overrideReason: PerfReason? = null,
    send: suspend () -> HttpClientCall,
): HttpClientCall {
    val visit = currentCoroutineContext()[PerformanceVisit] ?: return send()
    val (target, rawKey) = observedTarget(path) ?: return send()
    val verb = when (method) {
        HttpMethod.Get -> PerfMethod.GET
        HttpMethod.Post -> PerfMethod.POST
        else -> return send()
    }
    val load = currentCoroutineContext()[PerformanceLoad]
    val resource = if (target == PerfTarget.AUTH_REFRESH) null else load?.resource ?: rawKey?.let {
        val category = if (target in setOf(PerfTarget.SCORE, PerfTarget.SONG_DETAIL)) PerfTarget.SONG_BUNDLE else target
        visit.flow.alias(category, it)
    }
    val reason = overrideReason ?: if (target == PerfTarget.AUTH_REFRESH) PerfReason.REFRESH
        else currentCoroutineContext()[PerformanceReason]?.reason ?: PerfReason.INITIAL
    val start = PerformanceLog.now()
    var status: Int? = null
    var outcome = PerfReason.FAILED
    try {
        return send().also {
            status = it.response.status.value
            outcome = if (it.response.status.isSuccess()) PerfReason.READY else PerfReason.FAILED
        }
    } catch (cancelled: CancellationException) {
        outcome = PerfReason.CANCELLED
        throw cancelled
    } catch (timeout: HttpRequestTimeoutException) {
        outcome = PerfReason.HTTP_TIMEOUT
        throw timeout
    } catch (timeout: ConnectTimeoutException) {
        outcome = PerfReason.HTTP_TIMEOUT
        throw timeout
    } catch (timeout: SocketTimeoutException) {
        outcome = PerfReason.HTTP_TIMEOUT
        throw timeout
    } finally {
        if (reason != PerfReason.POLL || outcome != PerfReason.READY) {
            visit.event(PerfKind.HTTP_ATTEMPT, target, load = load?.id, resource = resource,
                reason = reason, status = status, durationMs = PerformanceLog.now() - start,
                method = verb, outcome = outcome)
        }
    }
}

/** 요청 결과. 응답을 받았거나([Received]), 받지 못한 이유. 응답 코드(503·410·413 등)는 [Received.response]에서 본다. */
sealed interface NetworkResult {
    class Received(val response: HttpResponse) : NetworkResult

    /** 서버에 닿지 못했다(네트워크 끊김, 주소를 찾지 못함, 연결 거부 등). */
    data object ConnectionFailed : NetworkResult

    /** 연결·요청 시간 제한을 넘었다. 서버가 요청을 받았을 수도 있다. */
    data object TimedOut : NetworkResult
}

/** 요청을 보내고 결과를 [NetworkResult]로 돌려준다. 코루틴 취소는 그대로 전한다. */
suspend fun HttpClient.sendCatching(request: suspend HttpClient.() -> HttpResponse): NetworkResult =
    sendCatching(accountBound = true, request)

private suspend fun HttpClient.sendCatching(
    accountBound: Boolean,
    request: suspend HttpClient.() -> HttpResponse,
): NetworkResult = try {
    ensureAccountSession()
    val work = if (accountBound) currentCoroutineContext()[AccountWork] else null
    val response = if (work == null) request() else work.member.request { request() }
    ensureAccountSession()
    NetworkResult.Received(response)
} catch (e: HttpRequestTimeoutException) {
    NetworkResult.TimedOut
} catch (e: ConnectTimeoutException) {
    NetworkResult.TimedOut
} catch (e: SocketTimeoutException) {
    NetworkResult.TimedOut
} catch (e: CancellationException) {
    throw e
} catch (ignored: Throwable) {
    NetworkResult.ConnectionFailed
}

/** 요청을 보내고 응답을 받는다. 연결 실패와 시간 초과를 나눌 필요가 없을 때 쓴다. 응답을 받지 못하면 null. */
suspend fun HttpClient.sendOrNull(request: suspend HttpClient.() -> HttpResponse): HttpResponse? =
    (sendCatching(request) as? NetworkResult.Received)?.response

/** 취소될 수 있는 선조회 등은 401을 바로 처리한다. 요청의 계정 결속은 호출 방식이 정한다. */
internal fun HttpRequestBuilder.skipAuthRefresh() {
    attributes.put(authRefreshSkipped, Unit)
}

/** 공개 GET은 인증 변경과 독립적으로 실행하고, 응답을 사용할 때는 호출자의 로그인 세대를 확인한다. */
internal suspend fun HttpClient.getPublic(
    path: String,
    configure: HttpRequestBuilder.() -> Unit = {},
): NetworkResult = sendCatching(accountBound = false) {
    get(path) {
        skipAuthRefresh()
        configure()
    }
}

/** [getPublic]에서 연결 실패와 시간 초과를 나눌 필요가 없을 때 쓴다. 응답을 받지 못하면 null. */
internal suspend fun HttpClient.getPublicOrNull(
    path: String,
    configure: HttpRequestBuilder.() -> Unit = {},
): HttpResponse? = (getPublic(path, configure) as? NetworkResult.Received)?.response

/** 본문을 공통 형식으로 읽는다. 형식이 아니면(프록시 오류 페이지 등) null. */
suspend inline fun <reified T> HttpResponse.apiResponse(): ApiResponse<T>? = try {
    body<ApiResponse<T>>().also { ensureAccountSession() }
} catch (e: CancellationException) {
    throw e
} catch (ignored: Throwable) {
    null
}

/**
 * 갱신은 한 번에 하나만 한다. 리프레시 토큰은 쓸 때마다 바뀌므로, 동시에 401을 받은 요청들이 각자 갱신하면
 * 늦게 보낸 쪽이 이미 바뀐 토큰을 써서 로그아웃된다. 기다리는 동안 다른 요청이 갱신했으면 그 결과를 쓴다.
 */
private class TokenRefresher(private val shared: SharedRefreshLock?) {
    private val mutex = Mutex()

    @Volatile
    private var local = 0L

    /** 요청을 보내기 전에 읽어 둔다. 탭 사이 잠금이 있으면 모든 탭이 함께 보는 세대를 쓴다. */
    val generation: Long get() = shared?.generation ?: local

    suspend fun refresh(seen: Long, refresh: suspend () -> Boolean): Boolean = mutex.withLock {
        if (generation != seen) return@withLock true
        if (shared == null) return@withLock finish(refresh) { local++ }
        shared.withLock {
            // 잠금을 기다리는 동안 다른 탭이 갱신했으면 새 쿠키로 재전송만 한다. 같은 쿠키로 다시 갱신하면 서버가 재사용으로 본다.
            if (generation != seen) true else finish(refresh) { shared.advance() }
        }
    }

    /**
     * 시작한 갱신은 부른 쪽이 취소돼도(화면을 닫음, 다른 탭의 계정 변경) 응답까지 받는다. 중간에 끊으면 서버는 리프레시 토큰을 이미
     * 바꿨는데 쿠키는 옛 토큰으로 남아, 다음 갱신이 재사용으로 감지되어 그 회원의 모든 기기가 로그아웃된다.
     */
    private suspend fun finish(refresh: suspend () -> Boolean, onSuccess: () -> Unit): Boolean =
        withContext(NonCancellable) { refresh().also { if (it) onSuccess() } }
}

private const val REFRESH_PATH = "/api/auth/refresh"

/** 401이 '로그인 정보가 틀림'이거나 갱신 자체인 경로. 이때는 갱신하지 않는다. */
private val NO_REFRESH_PATHS = setOf(REFRESH_PATH, "/api/auth/sign-in", "/api/auth/sign-up")

/**
 * 간편 로그인 start·완료·계정 연결(merge)·소셜 가입(sign-up)과 마이페이지 연결 완료·탈퇴 재인증 완료. 완료는 state를, merge는 mergeTicket과
 * 비밀번호 시도 횟수를, sign-up은 signupTicket을 한 번 쓰므로 같은 요청을 다시 보내면 안 된다.
 * 마이페이지 연결·탈퇴 재인증 start(`.../{provider}/start`)와 해제(DELETE)는 1회용 값을 쓰지 않아 평소처럼 갱신한다.
 */
private val NO_REFRESH_OAUTH_PATHS = listOf(
    Regex("/api/auth/oauth/[^/]+(/start)?"),
    Regex("/api/users/me/social-accounts/[^/]+"),
    Regex("/api/users/me/withdrawal/oauth/[^/]+"),
)

internal fun skipsRefresh(method: HttpMethod, path: String): Boolean =
    path in NO_REFRESH_PATHS || method == HttpMethod.Post && NO_REFRESH_OAUTH_PATHS.any { it.matches(path) }

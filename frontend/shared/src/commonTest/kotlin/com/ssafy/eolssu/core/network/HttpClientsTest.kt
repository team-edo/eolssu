package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.diagnostics.PerfKind
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceEvent
import com.ssafy.eolssu.core.diagnostics.PerformanceFlow
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.storage.FakeSecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 공통 HTTP 클라이언트의 토큰 갱신과 앱 로그인 쿠키 보관. 서버는 MockEngine으로 대신한다. */
class HttpClientsTest {

    private val requests = mutableListOf<String>()

    private fun client(
        cookies: SecureCookiesStorage? = null,
        sharedRefresh: SharedRefreshLock? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = createHttpClient(
        MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            handler(request)
        },
        ServerAddress.Production,
        cookies,
        sharedRefresh,
    )

    /** 웹 탭 사이 잠금의 대역. 모든 탭이 같은 잠금과 세대를 본다. */
    private class TabsRefreshLock : SharedRefreshLock {
        private val mutex = Mutex()
        override var generation = 0L
            private set

        override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

        override fun advance() {
            generation++
        }
    }

    @Test
    fun unauthorizedRefreshesOnceAndResends() = runTest {
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.Created)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        assertEquals(HttpStatusCode.Created, client.post("/api/users/me/score-views").status)
        assertEquals(listOf("POST /api/users/me/score-views", "POST /api/auth/refresh", "POST /api/users/me/score-views"), requests)
    }

    @Test
    fun diagnosticFlowDistinguishesReplayAndAliasesRepeatedSongRequests() = runTest {
        val events = mutableListOf<PerformanceEvent>()
        val flow = PerformanceFlow()
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }
        PerformanceLog.sink = { events += it }
        try {
            withContext(PerformanceVisit(flow, PerfTarget.SONG_DETAIL)) {
                client.get("/api/songs/471901")
            }
            withContext(PerformanceVisit(flow, PerfTarget.PRACTICE)) {
                client.get("/api/songs/471901/score")
            }
            assertEquals(
                listOf(PerfReason.INITIAL, PerfReason.REFRESH, PerfReason.REPLAY, PerfReason.INITIAL),
                events.map { it.reason },
            )
            assertEquals(listOf(401, 200, 200, 200), events.map { it.status })
            assertTrue(events.all { it.kind == PerfKind.HTTP_ATTEMPT && "471901" !in it.encode() })
            assertEquals(1, events.map { it.flow }.distinct().size)
            assertEquals(2, events.map { it.visit }.distinct().size)
            assertNotNull(events[0].resource)
            assertEquals(events[0].resource, events[3].resource)
        } finally {
            PerformanceLog.sink = null
            client.close()
        }
    }

    @Test
    fun failedRefreshReturnsOriginalUnauthorized() = runTest {
        val client = client { respond("", HttpStatusCode.Unauthorized) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/me").status)
        assertEquals(listOf("GET /api/users/me", "POST /api/auth/refresh"), requests)
    }

    @Test
    fun wrongPasswordIsNotRefreshed() = runTest {
        val client = client { respond("", HttpStatusCode.Unauthorized) }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/auth/sign-in").status)
        assertEquals(listOf("POST /api/auth/sign-in"), requests)
    }

    @Test
    fun oneTimeOAuthRequestsAreNotResent() = runTest {
        val client = client { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond("", HttpStatusCode.OK) else respond("", HttpStatusCode.Unauthorized)
        }
        val oneTime = listOf(
            "/api/auth/oauth/kakao/start",
            "/api/auth/oauth/kakao",
            "/api/auth/oauth/merge",
            "/api/auth/oauth/sign-up",
            "/api/users/me/social-accounts/naver",
            "/api/users/me/withdrawal/oauth/google",
        )

        oneTime.forEach { assertEquals(HttpStatusCode.Unauthorized, client.post(it).status) }
        assertEquals(oneTime.map { "POST $it" }, requests)

        // 마이페이지 연결·탈퇴 재인증 start는 1회용 값을 쓰지 않아 평소처럼 갱신 뒤 다시 보낸다.
        listOf("/api/users/me/social-accounts/naver/start", "/api/users/me/withdrawal/oauth/google/start").forEach { path ->
            requests.clear()
            client.post(path)
            assertEquals(listOf("POST $path", "POST /api/auth/refresh", "POST $path"), requests)
        }
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runTest {
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        val statuses = listOf("/api/a", "/api/b").map { path -> async { client.get(path).status } }.awaitAll()
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), statuses)
        assertEquals(1, requests.count { it == "POST /api/auth/refresh" })
    }

    @Test
    fun tabsSharingTheRefreshLockRefreshOnceAndBothResend() = runTest {
        val tabs = TabsRefreshLock()
        var refreshes = 0
        // 두 탭의 첫 요청이 모두 도착한 뒤에 401을 돌려준다. MockEngine은 실제 스레드에서 돌아,
        // 막지 않으면 한 탭이 갱신을 끝낸 뒤에야 다른 탭의 첫 요청이 도착해 바로 200을 받을 수 있다.
        val firstRequests = Mutex()
        var arrived = 0
        val bothArrived = CompletableDeferred<Unit>()
        // 서버는 리프레시 토큰을 쓸 때마다 바꾸고, 이미 바뀐 토큰이 다시 오면 재사용으로 보고 거절한다(401).
        val server: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> {
                    delay(10)
                    if (++refreshes == 1) respond("", HttpStatusCode.OK) else respond("", HttpStatusCode.Unauthorized)
                }
                refreshes > 0 -> respond("", HttpStatusCode.OK)
                else -> {
                    firstRequests.withLock { if (++arrived == 2) bothArrived.complete(Unit) }
                    bothArrived.await()
                    respond("", HttpStatusCode.Unauthorized)
                }
            }
        }
        val tabA = client(sharedRefresh = tabs, handler = server)
        val tabB = client(sharedRefresh = tabs, handler = server)

        val statuses = listOf(tabA to "/api/a", tabB to "/api/b").map { (tab, path) -> async { tab.get(path).status } }.awaitAll()

        assertEquals(setOf("GET /api/a", "GET /api/b"), requests.take(2).toSet(), "두 탭이 모두 만료된 토큰으로 401을 받았다")
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), statuses)
        assertEquals(1, requests.count { it == "POST /api/auth/refresh" }, "늦은 탭은 다시 갱신하지 않고 새 쿠키로 재전송만 한다")
        assertEquals(1L, tabs.generation)
    }

    @Test
    fun cancelledCallerStillFinishesTheRefreshItStarted() = runTest {
        val tabs = TabsRefreshLock()
        val refreshing = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        val client = client(sharedRefresh = tabs) { request ->
            if (request.url.encodedPath == "/api/auth/refresh") {
                refreshing.complete(Unit)
                answer.await() // 서버는 이미 리프레시 토큰을 바꿨고 응답만 남았다.
                respond("", HttpStatusCode.OK)
            } else {
                respond("", HttpStatusCode.Unauthorized)
            }
        }

        val call = launch { client.get("/api/a") }
        refreshing.await()
        call.cancel() // 화면을 닫았거나 다른 탭의 계정 변경으로 작업을 버렸다.
        answer.complete(Unit)
        call.join()

        assertEquals(1L, tabs.generation, "끊지 않고 새 쿠키를 받아 세대를 올린다. 끊으면 다음 갱신이 옛 토큰을 보내 재사용으로 감지된다")
    }

    @Test
    fun failedSharedRefreshDoesNotAdvanceTheGeneration() = runTest {
        val tabs = TabsRefreshLock()
        val client = client(sharedRefresh = tabs) { respond("", HttpStatusCode.Unauthorized) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/a").status)
        assertEquals(0L, tabs.generation, "갱신에 실패하면 다른 탭이 재전송만 하지 않도록 세대를 그대로 둔다")
    }

    @Test
    fun appCookiesAreSentByPathAndSurviveRelaunch() = runTest {
        val store = FakeSecureStore()
        var now = 0L
        val signIn = client(SecureCookiesStorage(store) { now }) { request ->
            if (request.url.encodedPath == "/api/auth/sign-in") {
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, listOf(ACCESS, REFRESH)))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        signIn.post("/api/auth/sign-in")

        // 앱을 다시 켬: 같은 저장소로 새 클라이언트
        val sentCookies = mutableMapOf<String, String?>()
        val relaunched = client(SecureCookiesStorage(store) { now }) { request ->
            sentCookies[request.url.encodedPath] = request.headers[HttpHeaders.Cookie]
            respond("", HttpStatusCode.OK)
        }
        relaunched.get("/api/users/me")
        relaunched.post("/api/auth/refresh")
        assertEquals("access_token=a1", sentCookies["/api/users/me"])
        assertEquals("access_token=a1; refresh_token=r1", sentCookies["/api/auth/refresh"])

        now = 31 * 60 * 1000L // 액세스 토큰(30분) 만료
        relaunched.get("/api/users/me")
        assertNull(sentCookies["/api/users/me"])
    }

    @Test
    fun clearedCookiesAreRemovedFromStore() = runTest {
        val store = FakeSecureStore()
        val cookies = SecureCookiesStorage(store) { 0L }
        val client = client(cookies) { request ->
            val setCookie = if (request.url.encodedPath == "/api/auth/sign-in") listOf(ACCESS, REFRESH) else listOf(CLEAR_ACCESS)
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, setCookie))
        }
        client.post("/api/auth/sign-in")
        client.post("/api/auth/sign-out") // 서버가 access_token을 Max-Age=0으로 지움

        val remaining = SecureCookiesStorage(store) { 0L }
        assertEquals(listOf("refresh_token"), remaining.get(io.ktor.http.Url("https://eolssu.site/api/auth/refresh")).map { it.name })

        cookies.clear()
        assertEquals(emptyMap(), store.values)
    }

    @Test
    fun errorResponseKeepsStatusNameCodeAndErrorCode() = runTest {
        val client = client {
            respond(
                """{"httpStatus":"SERVICE_UNAVAILABLE","isSuccess":false,"message":"잠시 후 다시","code":503,"result":{"errorCode":"SERVICE_UNAVAILABLE"}}""",
                HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client.sendCatching { post("/api/practice-sessions/1/recording") }
        val response = assertIs<NetworkResult.Received>(result).response
        assertEquals(503, response.status.value)
        val body = assertNotNull(response.apiResponse<ApiError>())
        assertEquals("SERVICE_UNAVAILABLE", body.httpStatus)
        assertEquals(503, body.code)
        assertEquals("SERVICE_UNAVAILABLE", body.result?.errorCode)
    }

    @Test
    fun connectionFailureAndTimeoutAreDifferent() = runTest {
        assertEquals(NetworkResult.ConnectionFailed, client { error("연결 거부") }.sendCatching { get("/api/users/me") })
        assertEquals(NetworkResult.TimedOut, client { throw ConnectTimeoutException("연결 시간 초과") }.sendCatching { get("/api/users/me") })
    }

    @Test
    fun requestTimeoutCanBeSetPerRequest() = runTest {
        val slow = client {
            delay(300)
            respond("", HttpStatusCode.Created)
        }

        val short = slow.sendCatching { post("/api/a") { timeout { requestTimeoutMillis = 50 } } }
        assertEquals(NetworkResult.TimedOut, short)
        val long = slow.sendCatching { post("/api/a") { timeout { requestTimeoutMillis = 5_000 } } }
        assertEquals(HttpStatusCode.Created, assertIs<NetworkResult.Received>(long).response.status)
    }

    @Test
    fun multipartBodyIsSentAgainAfterRefresh() = runTest {
        val audio = ByteArray(4_096) { it.toByte() }
        val bodies = mutableListOf<ByteArray>()
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                else -> {
                    bodies += request.body.toByteArray()
                    respond("", if (refreshed) HttpStatusCode.Created else HttpStatusCode.Unauthorized)
                }
            }
        }
        val fileHeaders = Headers.build {
            append(HttpHeaders.ContentType, "audio/mp4")
            append(HttpHeaders.ContentDisposition, "filename=\"take.m4a\"")
        }

        val response = client.post("/api/practice-sessions/1/recording") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("bytes", audio, fileHeaders)
                        append("stream", ChannelProvider(audio.size.toLong()) { ByteReadChannel(audio) }, fileHeaders)
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(2, bodies.size)
        assertContentEquals(bodies[0], bodies[1])
        assertTrue(bodies[1].size > audio.size * 2)
    }

    private companion object {
        const val ACCESS = "access_token=a1; Path=/api; Max-Age=1800; Secure; HttpOnly; SameSite=Lax"
        const val REFRESH = "refresh_token=r1; Path=/api/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Lax"
        const val CLEAR_ACCESS = "access_token=; Path=/api; Max-Age=0; Secure; HttpOnly; SameSite=Lax"
    }
}

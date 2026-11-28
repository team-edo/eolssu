package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.IosOAuthRelay
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthReturn
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.WebOAuthCallback
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 플랫폼 모듈이 등록한 복귀 경로(`OAuthRoute`)를 앱 모듈의 start·시도 상태가 모두 쓰는지. 없으면 Android 주소다.
 * 시도 상태는 이름 붙은 [OAuthAttemptStore](웹 탭 저장소)가 있으면 그것에, 없으면 일반 [SecureStore]에 남는지도 본다.
 */
class OAuthRouteWiringTest {
    private val sent = mutableListOf<String>()

    private fun platform(route: OAuthRoute?, secure: SecureStore? = null, attemptStore: SecureStore? = null): Module = module {
        single<KeyValueStore> { FakeKeyValueStore() }
        single { ServerAddress.Production }
        single<HttpClientEngine> {
            MockEngine { request ->
                sent += (request.body as TextContent).text
                respond(
                    """{"isSuccess":true,"result":{"authorizationUrl":"${OAuthAttemptsTest.authorizationUrl()}","expiresAt":"2026-10-30T01:10:00Z"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        if (route != null) single<OAuthRoute> { route }
        if (secure != null) single<SecureStore> { secure }
        if (attemptStore != null) single<SecureStore>(OAuthAttemptStore) { attemptStore }
    }

    // appModule의 single 인스턴스는 모듈에 남으므로 테스트마다 앱을 닫아 다른 테스트로 새지 않게 한다.
    private suspend fun redirectsSentBy(route: OAuthRoute?): List<String> {
        val app = koinApplication { modules(platform(route), appModule) }
        try {
            return redirectsSentBy(route, app.koin)
        } finally {
            app.close()
        }
    }

    private suspend fun redirectsSentBy(route: OAuthRoute?, koin: Koin): List<String> {
        koin.get<AuthRepository>().startSocialLogIn(SocialProvider.Kakao)
        koin.get<UserRepository>().startLink(SocialProvider.Kakao)
        koin.get<UserRepository>().startWithdrawalReauth(SocialProvider.Kakao)
        val attempts = koin.get<OAuthAttempts>()
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, OAuthAttemptsTest.authorizationUrl(), Long.MAX_VALUE))
        val returned = when (route) {
            null -> OAuthAttemptsTest.callback()
            is WebOAuthCallback -> "${route.uriFor(SocialProvider.Kakao)}?code=code-1&state=${OAuthAttemptsTest.STATE}"
            else -> "com.ssafy.eolssu://oauth/kakao/callback?code=code-1&state=${OAuthAttemptsTest.STATE}"
        }
        sent += assertIs<OAuthReturn.Accepted>(attempts.accept(returned, OAuthPurpose.LogIn)).callback.redirectUri
        return sent.toList()
    }

    @Test
    fun registeredIosRouteReachesEveryStartAndAttempt() = runTest {
        val ios = """{"redirectUri":"https://eolssu.site/api/auth/ios/oauth/kakao/callback"}"""
        assertEquals(listOf(ios, ios, ios, "https://eolssu.site/api/auth/ios/oauth/kakao/callback"), redirectsSentBy(IosOAuthRelay))
    }

    @Test
    fun withoutARegisteredRouteTheAndroidAddressStays() = runTest {
        val android = """{"redirectUri":"https://eolssu.site/auth/android/oauth/kakao/callback"}"""
        assertEquals(
            listOf(android, android, android, "https://eolssu.site/auth/android/oauth/kakao/callback"),
            redirectsSentBy(null),
        )
    }

    @Test
    fun registeredWebRouteReachesEveryStartAndAttempt() = runTest {
        val web = """{"redirectUri":"https://eolssu.site/auth/oauth/kakao/callback"}"""
        assertEquals(
            listOf(web, web, web, "https://eolssu.site/auth/oauth/kakao/callback"),
            redirectsSentBy(WebOAuthCallback("https://eolssu.site")),
        )
    }

    @Test
    fun webKeepsTheAttemptInTheTabStoreWithoutRegisteringASecureStore() {
        val tab = FakeSecureStore()
        val app = koinApplication { modules(platform(WebOAuthCallback("https://eolssu.site"), attemptStore = tab), appModule) }
        try {
            // 로그인 쿠키·자동 로그인이 쓰는 일반 SecureStore는 웹에 여전히 없다(S15P31E102-1264 범위).
            assertNull(app.koin.getOrNull<SecureStore>())
            assertTrue(app.koin.get<OAuthAttempts>().begin(SocialProvider.Kakao, OAuthPurpose.LogIn, OAuthAttemptsTest.authorizationUrl(), Long.MAX_VALUE))
            assertTrue(tab.values.isNotEmpty())
        } finally {
            app.close()
        }
    }

    @Test
    fun namedAttemptStoreWinsOverTheSecureStoreAndAndroidKeepsTheSecureStore() {
        val secure = FakeSecureStore()
        val tab = FakeSecureStore()
        val both = koinApplication { modules(platform(null, secure = secure, attemptStore = tab), appModule) }
        try {
            assertTrue(both.koin.get<OAuthAttempts>().begin(SocialProvider.Kakao, OAuthPurpose.LogIn, OAuthAttemptsTest.authorizationUrl(), Long.MAX_VALUE))
            assertTrue(tab.values.isNotEmpty())
            assertTrue(secure.values.isEmpty())
        } finally {
            both.close()
        }

        // Android·iOS처럼 이름 붙은 저장소가 없으면 일반 SecureStore(암호화 저장소)에 남긴다.
        val android = koinApplication { modules(platform(null, secure = secure), appModule) }
        try {
            assertTrue(android.koin.get<OAuthAttempts>().begin(SocialProvider.Kakao, OAuthPurpose.LogIn, OAuthAttemptsTest.authorizationUrl(), Long.MAX_VALUE))
            assertTrue(secure.values.isNotEmpty())
        } finally {
            android.close()
        }
    }
}

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.STATE
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 웹 복귀(`docs/specs/social-auth.md` 웹 간편 로그인, S15P31E102-1263). 페이지와 같은 주소의 `/auth/oauth/<제공자>/callback`을
 * redirectUri로 보내고, 같은 탭으로 돌아온 그 주소의 코드·state를 같은 시도 규칙으로 받는다.
 */
class WebOAuthCallbackTest {
    private val production = WebOAuthCallback("https://eolssu.site")
    private val store = FakeSecureStore()
    private val attempts = OAuthAttempts(store, SignedInMember(FakeKeyValueStore()), production) { 1_000L }

    private fun webReturn(provider: String = "kakao", query: String = "code=code-1&state=$STATE", origin: String = ORIGIN) =
        "$origin/auth/oauth/$provider/callback?$query"

    @Test
    fun redirectIsThePageOriginPlusTheProviderPath() {
        assertEquals("https://eolssu.site/auth/oauth/kakao/callback", production.uriFor(SocialProvider.Kakao))
        // window.location.origin은 끝에 /가 없지만, 붙어 와도 같은 주소를 만든다.
        assertEquals("https://eolssu.site/auth/oauth/naver/callback", WebOAuthCallback("https://eolssu.site/").uriFor(SocialProvider.Naver))
        assertEquals("http://localhost:8080/auth/oauth/google/callback", WebOAuthCallback("http://localhost:8080").uriFor(SocialProvider.Google))
    }

    @Test
    fun webOffersTheSameProvidersAsAndroid() {
        assertEquals(OAuthRedirect.providers, production.providers)
        assertTrue(SocialProvider.Apple !in production.providers)
    }

    @Test
    fun onlyTheExactSameOriginPathIsOurs() {
        assertEquals(SocialProvider.Kakao, production.providerOf(Url(webReturn())))
        assertEquals(SocialProvider.Google, production.providerOf(Url(webReturn("google"))))
        assertEquals(SocialProvider.Naver, production.providerOf(Url(webReturn("naver").replace("eolssu", "EOLSSU"))))

        listOf(
            webReturn().replace("https://", "http://"),
            webReturn().replace("eolssu.site", "eolssu.site.evil.test"),
            webReturn().replace("eolssu.site", "evil.test"),
            webReturn().replace("eolssu.site", "eolssu.site:8443"),
            webReturn().replace("https://", "https://x@"),
            webReturn().replace("https://", "https://x:y@"),
            webReturn().replace("/callback", "/callback/extra"),
            webReturn().replace("/auth/oauth/", "/auth/android/oauth/"),
            webReturn().replace("/auth/oauth/", "/api/auth/ios/oauth/"),
            "$ORIGIN/auth/oauth/kakao/callback/../callback?code=code-1&state=$STATE",
            webReturn("apple"),
            webReturn("unknown"),
        ).forEach { assertNull(production.providerOf(Url(it)), it) }
    }

    @Test
    fun localOriginKeepsItsPort() {
        val local = WebOAuthCallback("http://localhost:8080")
        assertEquals(SocialProvider.Kakao, local.providerOf(Url(webReturn(origin = "http://localhost:8080"))))
        assertNull(local.providerOf(Url(webReturn(origin = "http://localhost:8081"))))
        assertNull(local.providerOf(Url(webReturn(origin = "http://localhost"))))
        assertNull(local.providerOf(Url(webReturn(origin = "https://localhost:8080"))))
    }

    @Test
    fun webReturnCompletesWithTheWebRedirect() {
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT))
        // 같은 탭이 다시 열려도 시도는 저장소(웹은 sessionStorage)에서 이어 간다.
        val restored = OAuthAttempts(store, SignedInMember(FakeKeyValueStore()), production) { 1_000L }
        assertEquals(OAuthTarget(SocialProvider.Kakao, OAuthPurpose.LogIn), restored.pending())

        val callback = assertIs<OAuthReturn.Accepted>(restored.accept(webReturn(), OAuthPurpose.LogIn)).callback
        assertEquals("code-1", callback.authorizationCode)
        assertEquals("https://eolssu.site/auth/oauth/kakao/callback", callback.redirectUri)
        assertTrue(store.values.isEmpty())
        // 같은 주소를 다시 받아도 완료 요청을 다시 만들지 않는다.
        assertEquals(OAuthReturn.Ignored, restored.accept(webReturn(), OAuthPurpose.LogIn))
    }

    @Test
    fun androidOrForeignAddressesLeaveTheWebAttemptRunning() {
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.Link, authorizationUrl(), EXPIRES_AT))

        listOf(
            OAuthAttemptsTest.callback(),
            webReturn(origin = "https://evil.test"),
            webReturn(query = "code=code-1&state=${OAuthAttemptsTest.OTHER_STATE}"),
        ).forEach { assertEquals(OAuthReturn.Ignored, attempts.accept(it, OAuthPurpose.Link), it) }

        assertIs<OAuthReturn.Accepted>(attempts.accept(webReturn(), OAuthPurpose.Link))
    }

    @Test
    fun otherProviderOrProviderErrorRestarts() {
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT))
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(webReturn("naver"), OAuthPurpose.LogIn))

        assertTrue(attempts.begin(SocialProvider.Google, OAuthPurpose.Withdraw, authorizationUrl(OAuthAttemptsTest.OTHER_STATE), EXPIRES_AT))
        assertEquals(
            OAuthReturn.Restart(OAuthRestartReason.Cancelled),
            attempts.accept(webReturn("google", "error=access_denied&state=${OAuthAttemptsTest.OTHER_STATE}"), OAuthPurpose.Withdraw),
        )
    }

    private companion object {
        const val ORIGIN = "https://eolssu.site"
    }
}

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.STATE
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * iOS 복귀(`docs/specs/social-auth.md` iOS 인증 복귀). 서버 중계 주소를 redirectUri로 보내고,
 * 앱 전용 주소(`com.ssafy.eolssu://oauth/<제공자>/callback`)로 돌아온 코드·state를 같은 시도 규칙으로 받는다.
 */
class IosOAuthRelayTest {
    private val member = SignedInMember(FakeKeyValueStore())
    private val attempts = OAuthAttempts(FakeSecureStore(), member, IosOAuthRelay) { 1_000L }

    private fun appReturn(provider: String = "kakao", query: String = "code=code-1&state=$STATE") =
        "com.ssafy.eolssu://oauth/$provider/callback?$query"

    @Test
    fun acceptsTheAppAddressAndCompletesWithTheRelayRedirect() {
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT))

        val callback = assertIs<OAuthReturn.Accepted>(attempts.accept(appReturn(), OAuthPurpose.LogIn)).callback
        assertEquals("code-1", callback.authorizationCode)
        assertEquals("https://eolssu.site/api/auth/ios/oauth/kakao/callback", callback.redirectUri)
    }

    @Test
    fun appleIsOfferedFirstOnIosOnly() {
        assertEquals(SocialProvider.Apple, IosOAuthRelay.providers.first())
        assertEquals(SocialProvider.entries.toSet(), IosOAuthRelay.providers.toSet())
        assertTrue(SocialProvider.Apple !in OAuthRedirect.providers)
    }

    @Test
    fun appleNativeSignInReadsStateAndNonceAndBuildsTheAppReturn() {
        val url = "https://appleid.apple.com/auth/authorize?response_type=code&client_id=com.ssafy.eolssu&scope=email&state=$STATE&nonce=nonce-1"
        val request = assertNotNull(AppleNativeSignIn.request(url))
        assertEquals(STATE, request.state)
        assertEquals("nonce-1", request.nonce)
        assertTrue(attempts.begin(SocialProvider.Apple, OAuthPurpose.LogIn, url, EXPIRES_AT))

        val callback = assertIs<OAuthReturn.Accepted>(
            attempts.accept(AppleNativeSignIn.returnAddress(request, code = "apple+code/1"), OAuthPurpose.LogIn),
        ).callback
        assertEquals(SocialProvider.Apple, callback.provider)
        assertEquals("apple+code/1", callback.authorizationCode)
        assertEquals("https://eolssu.site/api/auth/ios/oauth/apple/callback", callback.redirectUri)
    }

    @Test
    fun appleNativeCancelRestartsAndForeignAddressesAreNotApple() {
        val request = assertNotNull(AppleNativeSignIn.request("https://appleid.apple.com/auth/authorize?state=$STATE&nonce=n"))
        assertTrue(attempts.begin(SocialProvider.Apple, OAuthPurpose.Link, "https://appleid.apple.com/auth/authorize?state=$STATE&nonce=n", EXPIRES_AT))
        assertEquals(
            OAuthReturn.Restart(OAuthRestartReason.Cancelled),
            attempts.accept(AppleNativeSignIn.returnAddress(request, error = "canceled"), OAuthPurpose.Link),
        )
        listOf(
            "https://kauth.kakao.com/oauth/authorize?state=$STATE&nonce=n",
            "http://appleid.apple.com/auth/authorize?state=$STATE&nonce=n",
            "https://appleid.apple.com/auth/authorize?state=$STATE",
            "https://appleid.apple.com/auth/authorize?nonce=n",
        ).forEach { assertNull(AppleNativeSignIn.request(it), it) }
    }

    @Test
    fun otherAddressesLeaveTheAttemptRunning() {
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT))

        listOf(
            OAuthAttemptsTest.callback(),
            appReturn().replace("com.ssafy.eolssu://", "eolssu://"),
            appReturn().replace("://oauth/", "://evil/"),
            appReturn().replace("/callback", "/callback/extra"),
            "com.ssafy.eolssu://oauth/kakao/callback/../callback?code=code-1&state=$STATE",
            appReturn().replace("://oauth/", "://oauth:1/"),
            appReturn().replace("://oauth/", "://x@oauth/"),
        ).forEach { assertEquals(OAuthReturn.Ignored, attempts.accept(it, OAuthPurpose.LogIn), it) }

        assertIs<OAuthReturn.Accepted>(attempts.accept(appReturn(), OAuthPurpose.LogIn))
    }

    @Test
    fun relayedProviderErrorRestarts() {
        assertTrue(attempts.begin(SocialProvider.Naver, OAuthPurpose.Link, authorizationUrl(), EXPIRES_AT))

        assertEquals(
            OAuthReturn.Restart(OAuthRestartReason.Cancelled),
            attempts.accept(appReturn("naver", "state=$STATE&error=access_denied"), OAuthPurpose.Link),
        )
    }

    @Test
    fun everyStartSendsTheRelayRedirect() = runTest {
        val sent = mutableListOf<String>()
        val client = createHttpClient(
            MockEngine { request ->
                sent += "${request.url.encodedPath} ${(request.body as TextContent).text}"
                respond(
                    """{"isSuccess":true,"result":{"authorizationUrl":"${authorizationUrl()}","expiresAt":"2026-10-30T01:10:00Z"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            ServerAddress.Production,
        )
        val auth = ApiAuthRepository(client, null, member, CoroutineScope(Dispatchers.Unconfined), IosOAuthRelay)
        val user = ApiUserRepository(client, null, member = member, route = IosOAuthRelay)

        assertIs<OAuthStartResult.Started>(auth.startSocialLogIn(SocialProvider.Google))
        assertIs<OAuthStartResult.Started>(user.startLink(SocialProvider.Naver))
        assertIs<OAuthStartResult.Started>(user.startWithdrawalReauth(SocialProvider.Kakao))
        assertEquals(
            listOf(
                """/api/auth/oauth/google/start {"redirectUri":"https://eolssu.site/api/auth/ios/oauth/google/callback"}""",
                """/api/users/me/social-accounts/naver/start {"redirectUri":"https://eolssu.site/api/auth/ios/oauth/naver/callback"}""",
                """/api/users/me/withdrawal/oauth/kakao/start {"redirectUri":"https://eolssu.site/api/auth/ios/oauth/kakao/callback"}""",
            ),
            sent,
        )
    }

    @Test
    fun developmentAppSendsItsOwnAppleAddressAndKeepsEverythingElse() {
        val dev = IosDevOAuthRelay.forBundle("com.ssafy.eolssu.dev")
        assertEquals("https://eolssu.site/api/auth/ios-dev/oauth/apple/callback", dev.uriFor(SocialProvider.Apple))
        assertEquals(IosOAuthRelay.uriFor(SocialProvider.Kakao), dev.uriFor(SocialProvider.Kakao))
        assertEquals(IosOAuthRelay.providers, dev.providers)
        assertEquals(SocialProvider.Apple, dev.providerOf(io.ktor.http.Url(appReturn("apple"))))
        for (bundle in listOf("com.ssafy.eolssu", "com.ssafy.eolssu.eunseo", null)) {
            assertEquals("https://eolssu.site/api/auth/ios/oauth/apple/callback", IosDevOAuthRelay.forBundle(bundle).uriFor(SocialProvider.Apple))
        }
    }
}

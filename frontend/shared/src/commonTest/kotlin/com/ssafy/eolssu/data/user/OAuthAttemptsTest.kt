package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 간편 로그인 시도 상태 보관과 복귀 주소 확인. 복귀 주소는 인가 코드를 담으므로 한 번만 받아들여야 한다. */
class OAuthAttemptsTest {
    private var now = 1_000L
    private val store = FakeSecureStore()
    private val member = SignedInMember(FakeKeyValueStore())
    private val attempts = OAuthAttempts(store, member) { now }

    @Test
    fun acceptsOnlyTheExactCallbackOnceWithTheStartRedirectUri() {
        begin()

        val accepted = assertIs<OAuthReturn.Accepted>(attempts.accept(callback(), OAuthPurpose.LogIn)).callback
        assertEquals(SocialProvider.Kakao, accepted.provider)
        assertEquals("code-1", accepted.authorizationCode)
        assertEquals(STATE, accepted.state)
        assertEquals("https://eolssu.site/auth/android/oauth/kakao/callback", accepted.redirectUri)
        // 같은 주소가 다시 오면(중복 복귀·재생) 완료 요청을 다시 만들지 않는다.
        assertEquals(OAuthReturn.Ignored, attempts.accept(callback(), OAuthPurpose.LogIn))
        assertNull(attempts.pending())
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun foreignAddressesAndOtherStatesLeaveTheAttemptRunning() {
        begin()

        listOf(
            callback().replace("https://", "http://"),
            callback().replace("eolssu.site", "eolssu.site.evil.test"),
            callback().replace("eolssu.site", "eolssu.site:8443"),
            callback().replace("/callback", "/callback/extra"),
            callback().replace("/auth/android/", "/auth/"),
            callback(state = OTHER_STATE),
            "not a url",
        ).forEach { assertEquals(OAuthReturn.Ignored, attempts.accept(it, OAuthPurpose.LogIn), it) }

        assertIs<OAuthReturn.Accepted>(attempts.accept(callback(), OAuthPurpose.LogIn))
    }

    @Test
    fun providerOrPurposeMismatchRestarts() {
        begin()
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(callback(provider = "naver"), OAuthPurpose.LogIn))
        assertNull(attempts.pending())

        // 서버 state는 시도마다 새로 만든다. 이미 처리한 state는 무시되므로 새 값으로 시작한다.
        begin(state = OTHER_STATE)
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(callback(state = OTHER_STATE), OAuthPurpose.Link))
    }

    @Test
    fun providerCancelAndExpiryRestartFromTheBeginning() {
        begin()
        assertEquals(
            OAuthReturn.Restart(OAuthRestartReason.Cancelled),
            attempts.accept("https://eolssu.site/auth/android/oauth/kakao/callback?error=access_denied&state=$STATE", OAuthPurpose.LogIn),
        )

        begin(state = OTHER_STATE)
        now = EXPIRES_AT
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Expired), attempts.accept(callback(state = OTHER_STATE), OAuthPurpose.LogIn))
        assertNull(attempts.pending())
    }

    @Test
    fun returnWithoutAnyAttemptRestarts() {
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(callback(), OAuthPurpose.LogIn))
    }

    @Test
    fun attemptSurvivesProcessRestartButNotForAnotherMember() {
        runImmediately { member.changing { member.set("7") } }
        begin(purpose = OAuthPurpose.Link)

        // 브라우저에 다녀오는 동안 앱이 꺼졌다 켜져도 같은 저장소에서 이어 간다.
        val restored = OAuthAttempts(store, member) { now }
        assertEquals(OAuthTarget(SocialProvider.Kakao, OAuthPurpose.Link), restored.pending())

        runImmediately { member.changing { member.set("8") } }
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), restored.accept(callback(), OAuthPurpose.Link))
    }

    @Test
    fun startResponseMustCarryAServerFormatState() {
        assertEquals(false, attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, "http://kauth.kakao.com/oauth/authorize?state=$STATE", EXPIRES_AT))
        assertEquals(false, attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, "https://kauth.kakao.com/oauth/authorize?state=short", EXPIRES_AT))
        assertNull(attempts.pending())
    }

    @Test
    fun mergeOfferStaysInMemoryUntilItExpires() {
        attempts.offerMerge(MergeOffer(SocialProvider.Kakao, "us***@example.test", needsPassword = false, ticket = TICKET, expiresAtMillis = EXPIRES_AT))
        assertEquals(TICKET, attempts.mergeOffer()?.ticket)
        assertTrue(store.values.isEmpty())
        now = EXPIRES_AT
        assertNull(attempts.mergeOffer())
    }

    @Test
    fun signUpTicketStaysInMemoryUntilItExpiresOrIsCleared() {
        attempts.offerSignUp(SignUpTicket(SocialProvider.Naver, TICKET, EXPIRES_AT))
        assertEquals(TICKET, attempts.signUpTicket()?.ticket)
        assertTrue(store.values.isEmpty())
        // 티켓 원문은 로그·화면 이동 경로로 새지 않게 문자열로 바꿔도 드러나지 않는다.
        assertEquals("SignUpTicket(provider=Naver)", attempts.signUpTicket().toString())
        attempts.clearSignUp()
        assertNull(attempts.signUpTicket())

        attempts.offerSignUp(SignUpTicket(SocialProvider.Naver, TICKET, EXPIRES_AT))
        now = EXPIRES_AT
        assertNull(attempts.signUpTicket())
    }

    @Test
    fun withdrawalReauthIsOnlyAcceptedForTheMemberWhoStartedIt() {
        runImmediately { member.changing { member.set("7") } }
        begin(purpose = OAuthPurpose.Withdraw)
        // 탈퇴 재인증의 복귀는 로그인 화면이 받지 않는다.
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(callback(), OAuthPurpose.LogIn))

        begin(state = OTHER_STATE, purpose = OAuthPurpose.Withdraw)
        runImmediately { member.changing { member.set("8") } }
        assertEquals(OAuthReturn.Restart(OAuthRestartReason.Invalid), attempts.accept(callback(state = OTHER_STATE), OAuthPurpose.Withdraw))
    }

    private fun begin(state: String = STATE, purpose: OAuthPurpose = OAuthPurpose.LogIn) =
        assertTrue(attempts.begin(SocialProvider.Kakao, purpose, authorizationUrl(state), EXPIRES_AT))

    companion object {
        const val STATE = "sssssssssssssssssssssssssssssssssssssssssss"
        const val OTHER_STATE = "ttttttttttttttttttttttttttttttttttttttttttt"
        const val TICKET = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm"
        const val EXPIRES_AT = 601_000L

        fun authorizationUrl(state: String = STATE) =
            "https://kauth.kakao.com/oauth/authorize?client_id=test&redirect_uri=x&response_type=code&state=$state"

        fun callback(state: String = STATE, provider: String = "kakao", code: String = "code-1") =
            "https://eolssu.site/auth/android/oauth/$provider/callback?code=$code&state=$state"
    }
}

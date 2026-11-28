package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.MergeOffer
import com.ssafy.eolssu.data.user.SignUpTicket
import com.ssafy.eolssu.data.user.SocialRegistration
import com.ssafy.eolssu.data.user.TermAgreement
import com.ssafy.eolssu.data.user.MergeResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.STATE
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.TICKET
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.callback
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.SocialLoginResult
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.InstrumentCatalog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AUTH-05·09 간편 로그인 흐름. 서버는 가짜 저장소, 브라우저는 연 주소만 남기는 대역으로 대신한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class OAuthViewModelTest {
    private var now = 1_000L
    private var links = IncomingLinks()
    private val browser = FakeBrowser()
    private val auth = FakeAuth()
    private var attempts = newAttempts()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun newAttempts() = OAuthAttempts(FakeSecureStore(), SignedInMember(FakeKeyValueStore())) { now }

    private fun viewModel(browser: ExternalBrowser? = this.browser) =
        OAuthViewModel(SocialProvider.Kakao, auth, attempts, links, browser, SampleUserRepository())

    @Test
    fun opensTheProviderOnceAndCompletesOnlyOnce() = runTest {
        val viewModel = viewModel()
        assertEquals(OAuthStep.InBrowser, viewModel.step.value)
        assertEquals(listOf(authorizationUrl()), browser.opened)

        links.deliver(callback())
        assertEquals(OAuthStep.LoggedIn, viewModel.step.value)
        assertEquals(listOf("code-1"), auth.completed.map { it.authorizationCode })

        // 브라우저가 같은 복귀를 다시 보내도 완료 요청은 한 번뿐이다.
        links.deliver("${callback()}&again")
        links.deliver(callback())
        assertEquals(1, auth.completed.size)
        assertNull(links.pending.value)
    }

    @Test
    fun serverResultsChooseTheNextScreen() = runTest {
        val expected = mapOf(
            SocialLoginResult.Success(withdrawalCancelled = true) to OAuthStep.WithdrawalCancelled,
            SocialLoginResult.NotRegistered to OAuthStep.NoAccount,
            SocialLoginResult.Restart to OAuthStep.Stopped(OAuthProblem.Invalid),
            SocialLoginResult.Unavailable to OAuthStep.Stopped(OAuthProblem.Unavailable),
        )
        expected.forEach { (result, step) ->
            // 앞 화면의 ViewModel이 복귀를 가져가지 않도록 화면마다 새 앱처럼 시작한다.
            attempts = newAttempts()
            links = IncomingLinks()
            auth.loginResult = result
            val viewModel = viewModel()
            links.deliver(callback())
            assertEquals(step, viewModel.step.value, result.toString())
        }
    }

    @Test
    fun mergeOfferIsHandedToAccountLinkInMemory() = runTest {
        auth.loginResult = SocialLoginResult.MergeAvailable(offer(needsPassword = true))
        val viewModel = viewModel()
        links.deliver(callback())

        assertEquals(OAuthStep.MergeAvailable, viewModel.step.value)
        assertEquals(TICKET, attempts.mergeOffer()?.ticket)
    }

    @Test
    fun providerCancelStopsAndRetryStartsOver() = runTest {
        val viewModel = viewModel()
        links.deliver("https://eolssu.site/auth/android/oauth/kakao/callback?error=access_denied&state=$STATE")
        assertEquals(OAuthStep.Stopped(OAuthProblem.Cancelled), viewModel.step.value)
        assertTrue(auth.completed.isEmpty())

        viewModel.start()
        assertEquals(OAuthStep.InBrowser, viewModel.step.value)
        assertEquals(2, auth.starts)
        assertNotNull(attempts.pending())
    }

    @Test
    fun expiredAttemptRestarts() = runTest {
        val viewModel = viewModel()
        now = EXPIRES_AT
        links.deliver(callback())
        assertEquals(OAuthStep.Stopped(OAuthProblem.Expired), viewModel.step.value)
        assertTrue(auth.completed.isEmpty())
    }

    @Test
    fun startFailuresStopWithoutAnAttempt() = runTest {
        auth.startResult = OAuthStartResult.Unavailable
        assertEquals(OAuthStep.Stopped(OAuthProblem.Unavailable), viewModel().step.value)

        auth.startResult = OAuthStartResult.Started(authorizationUrl(), EXPIRES_AT)
        browser.available = false
        assertEquals(OAuthStep.Stopped(OAuthProblem.NoBrowser), viewModel().step.value)
        assertNull(attempts.pending())
    }

    @Test
    fun platformsWithoutBrowserDoNotStart() = runTest {
        assertEquals(OAuthStep.Stopped(OAuthProblem.Unsupported), viewModel(browser = null).step.value)
        assertEquals(0, auth.starts)
    }

    @Test
    fun returnThatArrivedBeforeTheScreenIsHandledWithoutANewStart() = runTest {
        // 앱이 꺼진 사이 돌아와 화면 이동이 이 화면을 연 경우.
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT))
        links.deliver(callback())

        val viewModel = viewModel()
        assertEquals(OAuthStep.LoggedIn, viewModel.step.value)
        assertEquals(0, auth.starts)
        assertEquals(1, auth.completed.size)
    }

    @Test
    fun accountLinkAsksPasswordOnlyWhenNeededAndKeepsTheOfferAfterAWrongOne() = runTest {
        attempts.offerMerge(offer(needsPassword = true))
        val viewModel = AccountLinkViewModel(attempts, auth)
        assertEquals("us***@example.test", viewModel.uiState.value.email)

        assertFalse(viewModel.link())
        assertEquals(FieldError.PasswordEmpty, viewModel.uiState.value.passwordError)
        assertTrue(auth.merges.isEmpty())

        viewModel.changePassword("wrong1!!")
        auth.mergeResult = MergeResult.WrongPassword
        assertFalse(viewModel.link())
        assertEquals(FieldError.MergeWrongPassword, viewModel.uiState.value.passwordError)

        viewModel.changePassword("eolssu1!")
        auth.mergeResult = MergeResult.Success
        assertTrue(viewModel.link())
        assertEquals(listOf<String?>("wrong1!!", "eolssu1!"), auth.merges)
        assertNull(attempts.mergeOffer())
    }

    @Test
    fun accountLinkWithoutPasswordAndExpiredTicket() = runTest {
        attempts.offerMerge(offer(needsPassword = false))
        val viewModel = AccountLinkViewModel(attempts, auth)
        auth.mergeResult = MergeResult.Expired
        assertFalse(viewModel.link())
        assertEquals(listOf<String?>(null), auth.merges)
        assertTrue(viewModel.uiState.value.expired)

        assertTrue(AccountLinkViewModel(attempts, auth).uiState.value.expired)
    }

    @Test
    fun newProviderAccountHandsTheSignUpTicketToProfileSetup() = runTest {
        auth.loginResult = SocialLoginResult.SignUpRequired(SignUpTicket(SocialProvider.Kakao, TICKET, EXPIRES_AT))
        val viewModel = viewModel()
        links.deliver(callback())

        assertEquals(OAuthStep.SignUpRequired, viewModel.step.value)
        assertEquals(TICKET, attempts.signUpTicket()?.ticket)
    }

    @Test
    fun socialProfileSetupSignsUpOnceWithTheTicketAndAgreements() = runTest {
        attempts.offerSignUp(SignUpTicket(SocialProvider.Kakao, TICKET, EXPIRES_AT))
        val draft = AccountDraft().apply { agreements = listOf(TermAgreement("5", "v1", true)) }
        val viewModel = socialProfile(draft)
        viewModel.changeNickname(" 새회원 ")

        auth.signUpResult = MemberResult.SocialAccountInUse
        assertFalse(viewModel.complete())
        assertEquals(FieldError.SocialAccountInUse, viewModel.uiState.value.formError)
        // 다른 회원이 된 티켓은 다시 보내지 않는다.
        assertNull(attempts.signUpTicket())
        assertFalse(viewModel.complete())
        assertEquals(FieldError.SignUpExpired, viewModel.uiState.value.formError)
        assertEquals(1, auth.signUps.size)

        attempts.offerSignUp(SignUpTicket(SocialProvider.Kakao, TICKET, EXPIRES_AT))
        auth.signUpResult = MemberResult.Done
        assertTrue(viewModel.complete())
        val sent = auth.signUps.last()
        assertEquals("새회원", sent.nickname)
        assertEquals(listOf("5"), sent.agreements.map { it.termId })
        assertEquals("1", sent.instrumentId)
        assertNull(attempts.signUpTicket())
        assertTrue(draft.agreements.isEmpty())
    }

    @Test
    fun socialProfileSetupNeedsAgreementsAndAnUnexpiredTicket() = runTest {
        attempts.offerSignUp(SignUpTicket(SocialProvider.Kakao, TICKET, EXPIRES_AT))
        val viewModel = socialProfile(AccountDraft())
        viewModel.changeNickname("새회원")
        assertFalse(viewModel.complete())
        assertEquals(FieldError.TermsAgain, viewModel.uiState.value.formError)

        now = EXPIRES_AT
        assertFalse(socialProfile(AccountDraft().apply { agreements = listOf(TermAgreement("5", "v1", true)) }).apply { changeNickname("새회원") }.complete())
        assertTrue(auth.signUps.isEmpty())
    }

    /** 활성 악기 목록이 단소 1번뿐인 서버. 가입 요청의 instrumentId를 확인하려고 쓴다. */
    private val catalogClient = createHttpClient(
        MockEngine { respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"1","code":"DANSO","name":"단소"}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json")) },
        ServerAddress.Production,
    )

    private fun socialProfile(draft: AccountDraft) = ProfileSetupViewModel(
        InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(catalogClient)), SampleUserRepository(), auth, draft, attempts, social = true,
    )

    private fun offer(needsPassword: Boolean) = MergeOffer(SocialProvider.Kakao, "us***@example.test", needsPassword, TICKET, EXPIRES_AT)

    private class FakeBrowser : ExternalBrowser {
        val opened = mutableListOf<String>()
        var available = true

        override fun open(url: String): Boolean = available.also { if (it) opened += url }
    }

    private class FakeAuth : AuthRepository by SampleAuthRepository({ 0L }) {
        var startResult: OAuthStartResult = OAuthStartResult.Started(authorizationUrl(), EXPIRES_AT)
        var loginResult: SocialLoginResult = SocialLoginResult.Success(withdrawalCancelled = false)
        var mergeResult: MergeResult = MergeResult.Success
        var signUpResult: MemberResult = MemberResult.Done
        var starts = 0
        val completed = mutableListOf<OAuthCallback>()
        val merges = mutableListOf<String?>()
        val signUps = mutableListOf<SocialRegistration>()

        override suspend fun socialSignUp(request: SocialRegistration): MemberResult = signUpResult.also { signUps += request }

        override suspend fun startSocialLogIn(provider: SocialProvider): OAuthStartResult = startResult.also { starts++ }

        override suspend fun completeSocialLogIn(callback: OAuthCallback): SocialLoginResult = loginResult.also { completed += callback }

        override suspend fun mergeAccount(offer: MergeOffer, password: String?): MergeResult = mergeResult.also { merges += password }
    }
}

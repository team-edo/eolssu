package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.OTHER_STATE
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.STATE
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.TICKET
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.callback
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.ReauthResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.WithdrawalProof
import com.ssafy.eolssu.data.user.signsInWithPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MY-04 간편 로그인 재인증 탈퇴와 MY-01의 비밀번호 없는 회원 처리. */
@OptIn(ExperimentalCoroutinesApi::class)
class WithdrawReauthTest {
    private val links = IncomingLinks()
    private val opened = mutableListOf<String>()
    private val store = FakeKeyValueStore()
    private val attempts = OAuthAttempts(FakeSecureStore(), SignedInMember(store)) { 1_000L }
    private val timeQueue = PracticeTimeQueue(store, api = null, SignedInMember(store), CoroutineScope(Dispatchers.Unconfined))
    /** 서버는 시도마다 새 state를 만든다. 이미 처리한 state의 복귀는 무시되므로 다시 시작할 때 바꾼다. */
    private var startState = STATE
    private var reauthResult: ReauthResult = ReauthResult.Proven(WithdrawalProof(SocialProvider.Kakao, TICKET))
    private var withdrawResult = MemberResult.Done
    private val confirmed = mutableListOf<OAuthCallback>()
    private val withdrawn = mutableListOf<WithdrawalProof>()
    private val profile = MutableStateFlow(UserProfile("소셜회원", "", setOf(SocialProvider.Kakao)))
    private val users: UserRepository = object : UserRepository by SampleUserRepository() {
        override val profile = this@WithdrawReauthTest.profile
        override suspend fun startWithdrawalReauth(provider: SocialProvider) = OAuthStartResult.Started(authorizationUrl(startState), EXPIRES_AT)
        override suspend fun confirmWithdrawalReauth(callback: OAuthCallback) = reauthResult.also { confirmed += callback }
        override suspend fun withdraw(proof: WithdrawalProof) = withdrawResult.also { withdrawn += proof }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WithdrawViewModel(users, timeQueue, attempts, links, browser = com.ssafy.eolssu.core.browser.ExternalBrowser { opened += it; true })

    @Test
    fun reauthWithdrawsOnceWithTheProof() = runTest {
        val viewModel = viewModel()
        viewModel.startReauth(SocialProvider.Kakao)
        assertEquals(WithdrawReauth(SocialProvider.Kakao, WithdrawReauth.Step.InBrowser), viewModel.uiState.value.reauth)
        assertEquals(listOf(authorizationUrl()), opened)
        // 진행 중에는 비밀번호 신청·다른 재인증을 받지 않는다.
        viewModel.changePassword("eolssu1!")
        assertFalse(viewModel.submit())
        viewModel.startReauth(SocialProvider.Naver)
        assertEquals(1, opened.size)

        links.deliver(callback())
        links.deliver(callback())
        assertTrue(viewModel.uiState.value.withdrawn)
        assertEquals(1, confirmed.size)
        assertEquals(listOf(TICKET), withdrawn.map { it.ticket })
    }

    @Test
    fun cancelledOrExpiredProofStopsAndCanStartOver() = runTest {
        val viewModel = viewModel()
        viewModel.startReauth(SocialProvider.Kakao)
        links.deliver("https://eolssu.site/auth/android/oauth/kakao/callback?error=access_denied&state=$STATE")
        assertEquals(SocialLinkProblem.Cancelled, viewModel.uiState.value.reauth?.problem)
        assertTrue(confirmed.isEmpty())

        // 멈춘 뒤 다시 누르면 처음부터 한다. 이번엔 증명이 탈퇴 전에 만료됐다.
        startState = OTHER_STATE
        viewModel.startReauth(SocialProvider.Kakao)
        assertEquals(WithdrawReauth.Step.InBrowser, viewModel.uiState.value.reauth?.step)
        withdrawResult = MemberResult.TicketExpired
        links.deliver(callback(state = OTHER_STATE))
        assertEquals(WithdrawReauth.Step.Stopped, viewModel.uiState.value.reauth?.step)
        assertEquals(ProfileFieldError.ProofExpired, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.withdrawn)
    }

    @Test
    fun platformsWithoutBrowserDoNotStartReauth() = runTest {
        val viewModel = WithdrawViewModel(users, timeQueue, attempts, links, browser = null)
        viewModel.startReauth(SocialProvider.Kakao)
        assertEquals(SocialLinkProblem.Unsupported, viewModel.uiState.value.reauth?.problem)
    }

    @Test
    fun passwordlessMemberCannotUnlinkTheLastProviderOrOpenPasswordChange() = runTest {
        val myPage = MyPageViewModel(users, com.ssafy.eolssu.data.user.InstrumentRepository(store), timeQueue, com.ssafy.eolssu.data.user.SampleAuthRepository { 0L })
        assertFalse(profile.value.signsInWithPassword)
        assertFalse(myPage.askUnlink(SocialProvider.Kakao))
        assertEquals(null, myPage.dialog.value)
        assertFalse(myPage.canChangePassword())

        // 다른 간편 로그인이 하나 더 있으면 해제를 물을 수 있다.
        profile.value = profile.value.copy(linkedProviders = setOf(SocialProvider.Kakao, SocialProvider.Naver))
        assertTrue(myPage.askUnlink(SocialProvider.Kakao))

        // 이메일·비밀번호 회원은 마지막 간편 로그인도 해제할 수 있다.
        profile.value = UserProfile("회원", "a@b.test", setOf(SocialProvider.Kakao))
        assertTrue(myPage.askUnlink(SocialProvider.Kakao))
        assertTrue(myPage.canChangePassword())
    }
}

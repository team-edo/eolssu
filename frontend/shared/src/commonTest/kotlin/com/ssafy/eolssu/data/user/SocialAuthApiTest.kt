package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 간편 로그인·연결 API의 요청 본문과 응답 코드별 결과. 서버는 MockEngine으로 대신한다. */
class SocialAuthApiTest {
    private val requests = mutableListOf<String>()
    private val member = SignedInMember(FakeKeyValueStore())
    private val callback = OAuthCallback(SocialProvider.Kakao, "code-1", OAuthRedirect.uriFor(SocialProvider.Kakao), OAuthAttemptsTest.STATE)

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = createHttpClient(
        MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            handler(request)
        },
        ServerAddress.Production,
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun auth(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ApiAuthRepository(client(handler), null, member, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun startSendsTheAppRedirectAndReadsTheProviderAddress() = runTest {
        val repo = auth { request ->
            assertEquals("""{"redirectUri":"https://eolssu.site/auth/android/oauth/kakao/callback"}""", (request.body as TextContent).text)
            json("""{"isSuccess":true,"result":{"authorizationUrl":"${OAuthAttemptsTest.authorizationUrl()}","expiresAt":"2026-10-30T01:10:00Z"}}""")
        }
        val started = assertIs<OAuthStartResult.Started>(repo.startSocialLogIn(SocialProvider.Kakao))
        assertEquals(OAuthAttemptsTest.authorizationUrl(), started.authorizationUrl)
        assertEquals(listOf("POST /api/auth/oauth/kakao/start"), requests)
    }

    @Test
    fun loginCompletionMapsEveryServerOutcome() = runTest {
        var status = HttpStatusCode.OK
        var body = """{"isSuccess":true,"result":{"userId":"42","nickname":"회원","instrumentId":"1","notationMode":"HANJA","withdrawalCancelled":true}}"""
        val repo = auth { request ->
            assertEquals(
                """{"authorizationCode":"code-1","redirectUri":"https://eolssu.site/auth/android/oauth/kakao/callback","state":"${OAuthAttemptsTest.STATE}"}""",
                (request.body as TextContent).text,
            )
            json(body, status)
        }

        assertEquals(SocialLoginResult.Success(withdrawalCancelled = true), repo.completeSocialLogIn(callback))
        assertEquals("42", member.id)

        status = HttpStatusCode.Conflict
        body = """{"isSuccess":false,"result":{"errorCode":"ACCOUNT_MERGE_AVAILABLE","details":{"mergeTicket":"${OAuthAttemptsTest.TICKET}","maskedEmail":"us***@example.test","providerEmailVerified":false,"expiresAt":"2026-10-30T01:10:00Z"}}}"""
        val offer = assertIs<SocialLoginResult.MergeAvailable>(repo.completeSocialLogIn(callback)).offer
        assertEquals("us***@example.test", offer.maskedEmail)
        assertEquals(true, offer.needsPassword)
        assertEquals(OAuthAttemptsTest.TICKET, offer.ticket)

        status = HttpStatusCode.Unauthorized
        body = """{"isSuccess":false,"result":{"errorCode":"SOCIAL_ACCOUNT_NOT_REGISTERED"}}"""
        assertEquals(SocialLoginResult.NotRegistered, repo.completeSocialLogIn(callback))
        body = """{"isSuccess":false,"result":{"errorCode":"OAUTH_VERIFICATION_FAILED"}}"""
        assertEquals(SocialLoginResult.Restart, repo.completeSocialLogIn(callback))

        status = HttpStatusCode.ServiceUnavailable
        body = """{"isSuccess":false,"result":{"errorCode":"SERVICE_UNAVAILABLE"}}"""
        assertEquals(SocialLoginResult.Unavailable, repo.completeSocialLogIn(callback))
        // state는 1회용이라 401이어도 토큰 갱신·재전송을 하지 않는다.
        assertEquals(List(5) { "POST /api/auth/oauth/kakao" }, requests)
    }

    @Test
    fun newProviderAccountGetsASignUpTicketAndSignsUpOnce() = runTest {
        var status = HttpStatusCode.Conflict
        var body = """{"isSuccess":false,"result":{"errorCode":"SOCIAL_SIGN_UP_REQUIRED","details":{"signupTicket":"${OAuthAttemptsTest.TICKET}","expiresAt":"2026-10-30T01:10:00Z"}}}"""
        val sent = mutableListOf<String>()
        val repo = auth { request ->
            sent += (request.body as TextContent).text
            json(body, status)
        }

        val ticket = assertIs<SocialLoginResult.SignUpRequired>(repo.completeSocialLogIn(callback)).ticket
        assertEquals(SocialProvider.Kakao, ticket.provider)
        assertEquals(OAuthAttemptsTest.TICKET, ticket.ticket)
        assertEquals(null, member.id)

        status = HttpStatusCode.Created
        body = """{"isSuccess":true,"result":{"userId":"77","nickname":"새회원","instrumentId":"1","notationMode":"HANJA","createdAt":"2026-10-30T01:00:00Z"}}"""
        val agreements = listOf(TermAgreement("5", "2026-11-02", true), TermAgreement("8", "2026-11-02", false))
        assertEquals(MemberResult.Done, repo.socialSignUp(SocialRegistration(ticket, "새회원", agreements, "1")))
        assertEquals("77", member.id)
        // 이메일·비밀번호 없이 티켓·닉네임·약관·악기만 보낸다.
        assertEquals(
            """{"signupTicket":"${OAuthAttemptsTest.TICKET}","nickname":"새회원","agreements":[{"termId":"5","version":"2026-11-02","agreed":true},{"termId":"8","version":"2026-11-02","agreed":false}],"instrumentId":"1"}""",
            sent.last(),
        )

        suspend fun failure(code: String): MemberResult {
            body = """{"isSuccess":false,"result":{"errorCode":"$code"}}"""
            return repo.socialSignUp(SocialRegistration(ticket, "새회원", agreements, null))
        }
        status = HttpStatusCode.Gone
        assertEquals(MemberResult.TicketExpired, failure("SIGNUP_TICKET_EXPIRED"))
        status = HttpStatusCode.Conflict
        assertEquals(MemberResult.SocialAccountInUse, failure("SOCIAL_ACCOUNT_IN_USE"))
        assertEquals(MemberResult.EmailExists, failure("EMAIL_ALREADY_EXISTS"))
        status = HttpStatusCode.UnprocessableEntity
        assertEquals(MemberResult.TermsRequired, failure("TERMS_AGREEMENT_REQUIRED"))
        // 티켓은 1회용이라 401이어도 갱신·재전송하지 않는다.
        status = HttpStatusCode.Unauthorized
        assertEquals(MemberResult.Unauthenticated, failure("UNAUTHENTICATED"))
        assertEquals(0, requests.count { it == "POST /api/auth/refresh" })
    }

    @Test
    fun withdrawalReauthGetsAProofAndWithdrawsWithIt() = runTest {
        var confirm = HttpStatusCode.OK
        var confirmCode = ""
        var withdrawal = HttpStatusCode.OK
        val sent = mutableListOf<String>()
        val repo = ApiUserRepository(client { request ->
            val path = request.url.encodedPath
            when {
                request.method.value == "GET" -> json("""{"isSuccess":true,"result":{"nickname":"n","instrumentId":"1","notationMode":"HANJA"}}""")
                path == "/api/users/me/withdrawal/oauth/kakao/start" ->
                    json("""{"isSuccess":true,"result":{"authorizationUrl":"${OAuthAttemptsTest.authorizationUrl()}","expiresAt":"2026-10-30T01:10:00Z"}}""")
                path == "/api/users/me/withdrawal/oauth/kakao" -> if (confirm == HttpStatusCode.OK) {
                    json("""{"isSuccess":true,"result":{"withdrawalTicket":"${OAuthAttemptsTest.TICKET}","expiresAt":"2026-10-30T01:10:00Z"}}""")
                } else {
                    json("""{"isSuccess":false,"result":{"errorCode":"$confirmCode"}}""", confirm)
                }
                else -> {
                    sent += (request.body as TextContent).text
                    if (withdrawal == HttpStatusCode.OK) json("""{"isSuccess":true,"result":{"withdrawn":true}}""")
                    else json("""{"isSuccess":false,"result":{"errorCode":"WITHDRAWAL_TICKET_EXPIRED"}}""", withdrawal)
                }
            }
        }, null, member = member)

        assertIs<OAuthStartResult.Started>(repo.startWithdrawalReauth(SocialProvider.Kakao))
        val proof = assertIs<ReauthResult.Proven>(repo.confirmWithdrawalReauth(callback)).proof
        assertEquals(OAuthAttemptsTest.TICKET, proof.ticket)
        assertEquals("Proven(WithdrawalProof(provider=Kakao))", ReauthResult.Proven(proof).toString())

        withdrawal = HttpStatusCode.Gone
        assertEquals(MemberResult.TicketExpired, repo.withdraw(proof))
        withdrawal = HttpStatusCode.OK
        assertEquals(MemberResult.Done, repo.withdraw(proof))
        assertEquals("""{"withdrawalTicket":"${OAuthAttemptsTest.TICKET}"}""", sent.last())
        assertEquals(null, member.id)

        confirm = HttpStatusCode.Unauthorized
        confirmCode = "OAUTH_VERIFICATION_FAILED"
        assertEquals(ReauthResult.Restart, repo.confirmWithdrawalReauth(callback))
        confirmCode = "UNAUTHENTICATED"
        assertEquals(ReauthResult.Unauthenticated, repo.confirmWithdrawalReauth(callback))
        confirm = HttpStatusCode.ServiceUnavailable
        assertEquals(ReauthResult.Unavailable, repo.confirmWithdrawalReauth(callback))
        assertEquals(0, requests.count { it == "POST /api/auth/refresh" })
    }

    @Test
    fun lastLoginMethodUnlinkIsRefusedAndPasswordFlagIsRead() = runTest {
        var hasPassword = ""
        val repo = ApiUserRepository(client { request ->
            if (request.method.value == "GET") {
                json("""{"isSuccess":true,"result":{"nickname":"n","email":null,"instrumentId":"1","notationMode":"HANJA"$hasPassword,"linkedProviders":[{"provider":"NAVER"}]}}""")
            } else {
                json("""{"isSuccess":false,"result":{"errorCode":"LAST_LOGIN_METHOD"}}""", HttpStatusCode.Conflict)
            }
        }, null)

        repo.refresh()
        // 서버가 아직 비밀번호 유무를 주지 않으면 이메일이 없는 회원을 비밀번호 없는 회원으로 본다.
        assertEquals(null, repo.profile.value.hasPassword)
        assertEquals(false, repo.profile.value.signsInWithPassword)
        assertEquals(MemberResult.LastLoginMethod, repo.unlink(SocialProvider.Naver))
        assertEquals(setOf(SocialProvider.Naver), repo.profile.value.linkedProviders)

        hasPassword = ""","hasPassword":true"""
        repo.refresh()
        assertEquals(true, repo.profile.value.signsInWithPassword)
        // 이메일이 있어도 서버가 비밀번호가 없다고 하면 그 값을 따른다.
        assertEquals(false, UserProfile("n", "a@b.test", emptySet(), hasPassword = false).signsInWithPassword)
    }

    @Test
    fun mergeSendsPasswordOnlyWhenNeededAndMapsFailures() = runTest {
        var status = HttpStatusCode.OK
        val sent = mutableListOf<String>()
        val repo = auth { request ->
            sent += (request.body as TextContent).text
            json(if (status == HttpStatusCode.OK) """{"isSuccess":true,"result":{"userId":"9","nickname":"n","instrumentId":"1","notationMode":"HANJA","linkedProviders":["KAKAO"]}}""" else """{"isSuccess":false,"result":{}}""", status)
        }
        val verified = MergeOffer(SocialProvider.Kakao, "us***@example.test", needsPassword = false, ticket = OAuthAttemptsTest.TICKET, expiresAtMillis = 0)
        val unverified = MergeOffer(SocialProvider.Naver, "us***@example.test", needsPassword = true, ticket = OAuthAttemptsTest.TICKET, expiresAtMillis = 0)

        assertEquals(MergeResult.Success, repo.mergeAccount(verified, "ignored"))
        assertEquals("9", member.id)
        assertEquals("""{"mergeTicket":"${OAuthAttemptsTest.TICKET}"}""", sent.last())

        status = HttpStatusCode.Unauthorized
        assertEquals(MergeResult.WrongPassword, repo.mergeAccount(unverified, "eolssu1!"))
        assertEquals("""{"mergeTicket":"${OAuthAttemptsTest.TICKET}","password":"eolssu1!"}""", sent.last())
        status = HttpStatusCode.UnprocessableEntity
        assertEquals(MergeResult.PasswordRequired, repo.mergeAccount(verified, null))
        status = HttpStatusCode.Gone
        assertEquals(MergeResult.Expired, repo.mergeAccount(verified, null))
        status = HttpStatusCode.TooManyRequests
        assertEquals(MergeResult.Locked, repo.mergeAccount(unverified, "eolssu1!"))
        assertEquals(List(5) { "POST /api/auth/oauth/merge" }, requests)
    }

    @Test
    fun linkRefreshesTheSessionFirstAndMapsConflicts() = runTest {
        var status = HttpStatusCode.Created
        var errorCode = ""
        val repo = ApiUserRepository(client { request ->
            if (request.method.value == "GET") json("""{"isSuccess":true,"result":{"nickname":"n","instrumentId":"1","notationMode":"HANJA"}}""")
            else if (status == HttpStatusCode.Created) json("""{"isSuccess":true,"result":{"provider":"KAKAO","linkedAt":"2026-10-30T01:00:00Z"}}""", status)
            else json("""{"isSuccess":false,"result":{"errorCode":"$errorCode"}}""", status)
        }, null)

        assertEquals(LinkResult.Linked, repo.link(callback))
        assertEquals(setOf(SocialProvider.Kakao), repo.profile.value.linkedProviders)
        assertEquals(listOf("GET /api/users/me", "POST /api/users/me/social-accounts/kakao"), requests)

        status = HttpStatusCode.Conflict
        errorCode = "SOCIAL_ACCOUNT_IN_USE"
        assertEquals(LinkResult.InUse, repo.link(callback))
        errorCode = "PROVIDER_ALREADY_LINKED"
        assertEquals(LinkResult.Linked, repo.link(callback))
        status = HttpStatusCode.Unauthorized
        errorCode = "OAUTH_VERIFICATION_FAILED"
        assertEquals(LinkResult.Restart, repo.link(callback))
        errorCode = "UNAUTHENTICATED"
        assertEquals(LinkResult.Unauthenticated, repo.link(callback))
        // 연결 완료의 401은 갱신·재전송하지 않는다(GET은 성공해 갱신이 필요 없었다).
        assertEquals(0, requests.count { it == "POST /api/auth/refresh" })
    }

    @Test
    fun unlinkTreatsMissingLinkAsDone() = runTest {
        var status = HttpStatusCode.NoContent
        val repo = ApiUserRepository(client { request ->
            if (request.method.value == "GET") json("""{"isSuccess":true,"result":{"nickname":"n","instrumentId":"1","notationMode":"HANJA","linkedProviders":[{"provider":"KAKAO"},{"provider":"NAVER"}]}}""")
            else respond("", status)
        }, null)
        repo.refresh()

        assertEquals(MemberResult.Done, repo.unlink(SocialProvider.Kakao))
        status = HttpStatusCode.NotFound
        assertEquals(MemberResult.Done, repo.unlink(SocialProvider.Naver))
        assertEquals(emptySet(), repo.profile.value.linkedProviders)
        status = HttpStatusCode.ServiceUnavailable
        assertEquals(MemberResult.Unavailable, repo.unlink(SocialProvider.Google))
        assertEquals("DELETE /api/users/me/social-accounts/kakao", requests[1])
    }
}

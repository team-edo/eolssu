package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.browser.APP_CALLBACK_SCHEME
import com.ssafy.eolssu.core.model.SocialProvider
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.formUrlEncode

/**
 * 간편 로그인·계정 연결의 공통 계약(`docs/specs/social-auth.md` Android 계약안).
 * 앱이 서버 start를 부르고 받은 제공자 주소를 외부 브라우저로 연다. 제공자는 [OAuthRedirect]의 복귀 주소(App Links)로 앱을 다시 열고,
 * 앱은 그 주소의 코드·state를 같은 HTTP 클라이언트(start 때 받은 `oauth_flow_<제공자>` 쿠키 보유)로 완료 API에 보낸다.
 * 인가 코드·state·mergeTicket·쿠키는 로그에 남기지 않는다. 이 파일의 값 클래스는 `toString`에서 그 값을 가린다.
 */
enum class OAuthPurpose {
    /** 로그인 화면의 간편 로그인(AUTH-05). 공개 start·완료 API. */
    LogIn,

    /** 마이페이지의 간편 로그인 연결(MY-05). 로그인한 회원의 start·완료 API. */
    Link,

    /** 회원 탈퇴(MY-04)의 간편 로그인 재인증. 로그인한 회원의 start·완료 API로 탈퇴 증명을 받는다. */
    Withdraw,
}

/** 서버 경로와 복귀 주소에 쓰는 제공자 이름(`kakao`·`google`·`naver`). 서버는 소문자만 받는다. */
val SocialProvider.pathName: String get() = name.lowercase()

/**
 * 플랫폼별 복귀 경로. [uriFor]는 start·완료 요청에 보내는 redirectUri(제공자 콘솔·서버 허용 목록과 같아야 함)이고,
 * [providerOf]는 앱이 받은 복귀 주소가 우리 경로면 그 제공자를 돌려준다. 플랫폼 모듈이 등록하지 않으면 [OAuthRedirect]를 쓴다.
 */
interface OAuthRoute {
    fun uriFor(provider: SocialProvider): String

    /** 이 플랫폼의 로그인·마이페이지에 보여 줄 제공자. Apple은 기본 Apple 로그인이 있는 iOS만 넣는다. */
    val providers: List<SocialProvider> get() = SocialProvider.entries - SocialProvider.Apple

    fun providerOf(url: Url): SocialProvider?
}

/**
 * Android 복귀 주소(App Links). 로그인과 연결이 같은 주소를 쓰고, 앱은 redirectUri 그대로 돌아온다.
 * 호스트·경로는 `androidApp`의 Manifest App Links `intent-filter`와 같아야 한다.
 */
object OAuthRedirect : OAuthRoute {
    const val HOST = "eolssu.site"

    override fun uriFor(provider: SocialProvider) = "https://$HOST${pathFor(provider)}"

    fun pathFor(provider: SocialProvider) = "/auth/android/oauth/${provider.pathName}/callback"

    /** https·정확한 호스트·기본 포트·`/auth/android/oauth/<제공자>/callback` 경로일 때 그 제공자. */
    override fun providerOf(url: Url): SocialProvider? {
        if (url.protocol.name != "https" || !url.host.equals(HOST, ignoreCase = true)) return null
        if (url.specifiedPort != 0 && url.specifiedPort != 443) return null
        if (url.user != null || url.password != null) return null
        return SocialProvider.entries.firstOrNull { url.encodedPath == pathFor(it) }
    }
}

/**
 * iOS 복귀(`docs/specs/social-auth.md` iOS 인증 복귀). 무료 Apple 계정은 HTTPS 복귀를 앱으로 받을 수 없어,
 * 제공자는 서버 중계 주소로 돌아가고 서버가 코드·state를 앱 전용 주소 `com.ssafy.eolssu://oauth/<제공자>/callback`으로 옮긴다.
 */
object IosOAuthRelay : OAuthRoute {
    override fun uriFor(provider: SocialProvider) = "https://${OAuthRedirect.HOST}/api/auth/ios/oauth/${provider.pathName}/callback"

    /**
     * App Store 심사 4.8에 따라 Apple 로그인을 다른 간편 로그인과 함께 맨 앞에 둔다. Apple은 서버 중계를 거치지 않는다:
     * start의 authorizationUrl에서 state·nonce를 꺼내 기본 Apple 로그인을 띄우고, 받은 코드로 같은 복귀 주소를 앱이 만든다(`IosExternalBrowser`).
     * 위 [uriFor] 주소는 start·완료 요청의 redirectUri로만 쓰인다.
     */
    override val providers = listOf(SocialProvider.Apple) + (SocialProvider.entries - SocialProvider.Apple)

    override fun providerOf(url: Url): SocialProvider? {
        if (url.protocol.name != APP_CALLBACK_SCHEME || url.host != "oauth" || url.specifiedPort != 0) return null
        if (url.user != null || url.password != null) return null
        return SocialProvider.entries.firstOrNull { url.encodedPath == "/${it.pathName}/callback" }
    }
}

/**
 * 개발 앱(얼쑤 개발, 번들 ID `com.ssafy.eolssu.dev`)의 iOS 복귀. Apple만 개발 앱 전용 주소를 보내 서버가 개발 앱 번들 ID로 코드를 교환하게 한다.
 * Apple 코드는 받은 앱의 번들 ID로만 교환된다. 다른 제공자와 앱 복귀는 [IosOAuthRelay]와 같다.
 */
object IosDevOAuthRelay : OAuthRoute by IosOAuthRelay {
    const val BUNDLE_ID = "com.ssafy.eolssu.dev"

    override fun uriFor(provider: SocialProvider) =
        if (provider == SocialProvider.Apple) "https://${OAuthRedirect.HOST}/api/auth/ios-dev/oauth/${provider.pathName}/callback"
        else IosOAuthRelay.uriFor(provider)

    /** 실행 중인 앱의 번들 ID에 맞는 iOS 복귀 경로. */
    fun forBundle(bundleId: String?): OAuthRoute = if (bundleId == BUNDLE_ID) IosDevOAuthRelay else IosOAuthRelay
}

/**
 * 웹 복귀(`docs/specs/social-auth.md` 웹 간편 로그인). 웹 앱은 같은 탭에서 제공자 화면으로 이동하고, 제공자는 페이지와 같은 주소(origin)의
 * `/auth/oauth/<제공자>/callback`으로 돌려보낸다. `index.html`이 그 주소를 받아 둔 뒤 주소창을 `/`로 바꾸고 웹 앱을 띄운다.
 * [origin]은 `window.location.origin`이다(운영 `https://eolssu.site`). 이 주소가 제공자 콘솔·서버 허용 목록에 있어야 한다.
 */
class WebOAuthCallback(origin: String) : OAuthRoute {
    private val base = Url(origin)
    private val origin = origin.trimEnd('/')

    override fun uriFor(provider: SocialProvider) = "$origin${pathFor(provider)}"

    /**
     * 페이지와 같은 프로토콜·호스트·포트이고 `/auth/oauth/<제공자>/callback` 경로일 때 그 제공자.
     * 웹에서 보여 주는 제공자([providers], Apple 제외)만 받는다. `index.html`도 이 세 경로만 받아 둔다.
     */
    override fun providerOf(url: Url): SocialProvider? {
        if (url.protocol != base.protocol || !url.host.equals(base.host, ignoreCase = true) || url.port != base.port) return null
        if (url.user != null || url.password != null) return null
        return providers.firstOrNull { url.encodedPath == pathFor(it) }
    }

    companion object {
        fun pathFor(provider: SocialProvider) = "/auth/oauth/${provider.pathName}/callback"
    }
}

/**
 * iOS 기본 Apple 로그인(`docs/specs/social-auth.md` Sign in with Apple). 서버 start가 준 Apple 인증 주소를 열지 않고
 * `state`·`nonce`만 꺼내 기본 로그인 요청에 넣는다. 끝나면 다른 제공자의 서버 중계 복귀와 같은 앱 주소를 만들어 같은 확인([OAuthAttempts.accept])을 거친다.
 */
object AppleNativeSignIn {
    class Request(val state: String, val nonce: String) {
        override fun toString() = "AppleNativeSignIn.Request"
    }

    /** https `appleid.apple.com` 주소이고 state·nonce가 있으면 그 값. 아니면 null(Apple 기본 로그인 대상이 아님). */
    fun request(authorizationUrl: String): Request? {
        val url = runCatching { Url(authorizationUrl) }.getOrNull() ?: return null
        if (url.protocol.name != "https" || url.host != "appleid.apple.com") return null
        val state = url.parameters["state"]?.takeIf(String::isNotBlank) ?: return null
        val nonce = url.parameters["nonce"]?.takeIf(String::isNotBlank) ?: return null
        return Request(state, nonce)
    }

    /** 성공하면 [code], 취소·실패면 [error]를 담은 `com.ssafy.eolssu://oauth/apple/callback` 주소. */
    fun returnAddress(request: Request, code: String? = null, error: String? = null): String {
        val parameters = Parameters.build {
            if (code != null) append("code", code) else append("error", error ?: "unknown")
            append("state", request.state)
        }
        return "$APP_CALLBACK_SCHEME://oauth/${SocialProvider.Apple.pathName}/callback?${parameters.formUrlEncode()}"
    }
}

/** start 응답. [Started.authorizationUrl]을 외부 브라우저로 연다. */
sealed interface OAuthStartResult {
    class Started(val authorizationUrl: String, val expiresAtMillis: Long) : OAuthStartResult {
        override fun toString() = "Started(expiresAtMillis=$expiresAtMillis)"
    }

    /** 연결 start에서 로그인이 풀렸다(401). */
    data object Unauthenticated : OAuthStartResult

    /** 제공자 설정이 없거나 장애(503), 연결 실패. 잠시 뒤 다시 시도하게 한다. */
    data object Unavailable : OAuthStartResult
}

/** 복귀 주소에서 꺼낸 완료 요청 값. [redirectUri]는 start 때 보낸 주소 그대로다. */
class OAuthCallback(
    val provider: SocialProvider,
    val authorizationCode: String,
    val redirectUri: String,
    val state: String,
) {
    override fun toString() = "OAuthCallback(provider=$provider)"
}

/** 간편 로그인 완료(`POST /api/auth/oauth/{provider}`) 결과. */
sealed interface SocialLoginResult {
    /** 연결된 계정으로 로그인했다. [withdrawalCancelled]: 탈퇴 대기 계정이라 탈퇴가 취소됐다. */
    data class Success(val withdrawalCancelled: Boolean) : SocialLoginResult

    /** 같은 이메일로 가입한 계정이 있다(409 `ACCOUNT_MERGE_AVAILABLE`). 계정 연결 확인(AUTH-09)으로 간다. */
    class MergeAvailable(val offer: MergeOffer) : SocialLoginResult {
        override fun toString() = "MergeAvailable($offer)"
    }

    /** 처음 온 제공자 계정이다(409 `SOCIAL_SIGN_UP_REQUIRED`). 약관 동의·프로필 설정 뒤 [ticket]으로 가입한다. */
    class SignUpRequired(val ticket: SignUpTicket) : SocialLoginResult {
        override fun toString() = "SignUpRequired($ticket)"
    }

    /** 연결된 계정도 같은 이메일 계정도 없다(401 `SOCIAL_ACCOUNT_NOT_REGISTERED`). 소셜 가입(356) 전 서버의 응답이라 이메일 가입을 안내한다. */
    data object NotRegistered : SocialLoginResult

    /** state·코드 확인에 실패했다(401 `OAUTH_VERIFICATION_FAILED` 등). 처음부터 다시 한다. */
    data object Restart : SocialLoginResult

    /** 제공자·서버 장애(503), 연결 실패. state는 이미 썼을 수 있으므로 같은 코드로 다시 보내지 않고 처음부터 다시 한다. */
    data object Unavailable : SocialLoginResult
}

/**
 * 계정 연결 제안. [ticket]은 10분 동안 한 번 쓸 수 있고, 이 흐름의 메모리에만 둔다(주소·저장소에 남기지 않는다).
 * [needsPassword]: 제공자가 이메일을 확인해 주지 않아(`providerEmailVerified=false`) 얼쑤 비밀번호로 본인을 확인해야 한다.
 */
class MergeOffer(
    val provider: SocialProvider,
    val maskedEmail: String,
    val needsPassword: Boolean,
    val ticket: String,
    val expiresAtMillis: Long,
) {
    override fun toString() = "MergeOffer(provider=$provider, needsPassword=$needsPassword)"
}

/**
 * 소셜 가입 티켓. [ticket]은 10분 동안 한 번 쓸 수 있고 start 때 받은 `oauth_flow_<제공자>` 쿠키와 묶여 있어,
 * 같은 HTTP 클라이언트의 이 흐름 메모리에만 둔다(주소·저장소에 남기지 않는다).
 */
class SignUpTicket(val provider: SocialProvider, val ticket: String, val expiresAtMillis: Long) {
    override fun toString() = "SignUpTicket(provider=$provider)"
}

/** 소셜 가입(`POST /api/auth/oauth/sign-up`)에 보내는 값. 이메일·비밀번호는 받지 않는다. */
class SocialRegistration(val ticket: SignUpTicket, val nickname: String, val agreements: List<TermAgreement>, val instrumentId: String?) {
    override fun toString() = "SocialRegistration(provider=${ticket.provider})"
}

/** 탈퇴 증명. [ticket]은 10분 동안 한 번 쓸 수 있고 start 때 받은 쿠키와 묶여 있어 이 흐름의 메모리에만 둔다. */
class WithdrawalProof(val provider: SocialProvider, val ticket: String) {
    override fun toString() = "WithdrawalProof(provider=$provider)"
}

/** 탈퇴 재인증 완료(`POST /api/users/me/withdrawal/oauth/{provider}`) 결과. */
sealed interface ReauthResult {
    class Proven(val proof: WithdrawalProof) : ReauthResult {
        override fun toString() = "Proven($proof)"
    }

    /** state·코드 확인에 실패했거나 연결이 바뀌었다. 처음부터 다시 한다. */
    data object Restart : ReauthResult

    /** 로그인이 풀렸다(401 `UNAUTHENTICATED`). */
    data object Unauthenticated : ReauthResult

    data object Unavailable : ReauthResult
}

/** 계정 연결(`POST /api/auth/oauth/merge`) 결과. */
sealed interface MergeResult {
    data object Success : MergeResult

    /** 비밀번호 없이 보냈는데 본인 확인이 필요하다(422 `IDENTITY_CONFIRMATION_REQUIRED`). */
    data object PasswordRequired : MergeResult

    /** 비밀번호가 맞지 않는다(401 `INVALID_CREDENTIALS`). */
    data object WrongPassword : MergeResult

    /** 15분 안에 5번 틀려 잠겼다(429). */
    data object Locked : MergeResult

    /** 연결 시간(10분)이 지났거나 이미 썼다(410 `MERGE_TICKET_EXPIRED`). 간편 로그인부터 다시 한다. */
    data object Expired : MergeResult

    data object Unavailable : MergeResult
}

/** 마이페이지 연결 완료(`POST /api/users/me/social-accounts/{provider}`) 결과. */
sealed interface LinkResult {
    /** 연결했다(201). 이미 연결돼 있던 제공자(409 `PROVIDER_ALREADY_LINKED`)도 결과가 같아 여기로 본다. */
    data object Linked : LinkResult

    /** 이 제공자 계정이 다른 얼쑤 계정에 연결돼 있다(409 `SOCIAL_ACCOUNT_IN_USE`). MY-05 안내를 띄운다. */
    data object InUse : LinkResult

    /** state·코드 확인에 실패했다. 처음부터 다시 한다. */
    data object Restart : LinkResult

    /** 로그인이 풀렸다(401 `UNAUTHENTICATED`). */
    data object Unauthenticated : LinkResult

    data object Unavailable : LinkResult
}

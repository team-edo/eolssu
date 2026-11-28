package com.ssafy.eolssu.platform.browser

import com.ssafy.eolssu.app.di.OAuthAttemptStore
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.platform.webPlatformModule
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 웹 간편 로그인의 브라우저 쪽(S15P31E102-1263). 시도 상태는 탭 저장소(sessionStorage)에만 남고,
 * `index.html`이 받아 둔 복귀 주소는 웹 플랫폼 모듈이 [IncomingLinks]로 한 번만 넘긴다.
 * 제공자 화면으로 실제 이동하면 테스트 페이지를 떠나므로 [WebExternalBrowser.open]은 부르지 않는다.
 */
class WebOAuthTest {
    private val key = "eolssu.test.tab-session-store"

    @AfterTest
    fun clear() {
        sessionStorage.removeItem(key)
        sessionStorage.removeItem(RETURN_KEY)
    }

    @Test
    fun tabStoreKeepsValuesInSessionStorageOnly() {
        val store = TabSessionStore()
        assertNull(store.getString(key))
        store.putString(key, "attempt")
        assertEquals("attempt", sessionStorage.getItem(key))
        assertNull(localStorage.getItem(key))
        // 같은 탭에서 다시 연 페이지처럼 새 객체도 같은 값을 읽는다.
        assertEquals("attempt", TabSessionStore().getString(key))
        store.remove(key)
        assertNull(sessionStorage.getItem(key))
    }

    @Test
    fun savedReturnIsTakenOnce() {
        assertNull(takeWebOAuthReturn())
        sessionStorage.setItem(RETURN_KEY, RETURN)
        assertEquals(RETURN, takeWebOAuthReturn())
        assertNull(takeWebOAuthReturn())
        assertNull(sessionStorage.getItem(RETURN_KEY))
    }

    @Test
    fun platformModuleHandsTheSavedReturnToIncomingLinks() {
        sessionStorage.setItem(RETURN_KEY, RETURN)
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            assertEquals(RETURN, app.koin.get<IncomingLinks>().pending.value)
            assertNull(sessionStorage.getItem(RETURN_KEY))
        } finally {
            app.close()
        }
    }

    @Test
    fun platformModuleRegistersTheWebRouteBrowserAndTabStoreOnly() {
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            assertNull(app.koin.get<IncomingLinks>().pending.value)
            assertEquals(
                "${window.location.origin}/auth/oauth/kakao/callback",
                app.koin.get<OAuthRoute>().uriFor(SocialProvider.Kakao),
            )
            assertIs<WebExternalBrowser>(app.koin.get<ExternalBrowser>())
            assertIs<TabSessionStore>(app.koin.get<SecureStore>(OAuthAttemptStore))
            // 로그인 쿠키·자동 로그인이 쓰는 일반 SecureStore는 등록하지 않는다.
            assertNull(app.koin.getOrNull<SecureStore>())
        } finally {
            app.close()
        }
    }

    private companion object {
        /** `index.html`과 `WebOAuth.kt`가 함께 쓰는 키. */
        const val RETURN_KEY = "eolssu.oauth_return"
        const val RETURN = "https://eolssu.site/auth/oauth/kakao/callback?code=code-1&state=sssssssssssssssssssssssssssssssssssssssssss"
    }
}

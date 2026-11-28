package com.ssafy.eolssu.backend.common.security

import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 운영처럼 Nginx가 HTTPS를 끝내고 http로 넘기는 경우. Host에는 포트가 없고(proxy_set_header Host $host),
 * 개발 주소(SECURITY_ALLOWED_ORIGINS)는 비어 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxiedRequestSecurityTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `accepts a same site HTTPS request forwarded over HTTP`() {
        mockMvc.perform(jsonPost(host = "eolssu.site", origin = "https://eolssu.site"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }

    @Test
    fun `accepts the same host on a non default port when the proxy drops the port`() {
        mockMvc.perform(jsonPost(host = "localhost", origin = "http://localhost:8080"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `compares ports when the Host header carries one`() {
        // 개발 주소가 없어 Spring CORS가 꺼진 상태라, 403은 StateChangingRequestFilter의 포트 비교에서만 나온다.
        mockMvc.perform(jsonPost(host = "localhost:8081", origin = "http://localhost:9999"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.result.errorCode").value("FORBIDDEN"))
        mockMvc.perform(jsonPost(host = "localhost:8081", origin = "http://localhost:8081"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `rejects other sites including look-alike hosts`() {
        for (origin in listOf("https://evil.example", "https://eolssu.site.evil.example", "https://www.eolssu.site")) {
            mockMvc.perform(jsonPost(host = "eolssu.site", origin = origin))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.result.errorCode").value("FORBIDDEN"))
        }
    }

    @Test
    fun `accepts multipart only for the recording upload regardless of the servlet path`() {
        // MockMvc는 servletPath를 비워 둔다. 업로드 판정이 servletPath에 기대면 여기서 415가 난다.
        val multipart = MediaType.parseMediaType("multipart/form-data; boundary=test-boundary")
        mockMvc.perform(post("/api/practice-sessions/1/recording").contentType(multipart).content("--test-boundary--"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/auth/sign-in").contentType(multipart).content("--test-boundary--"))
            .andExpect(status().isUnsupportedMediaType)
    }

    @Test
    fun `keeps CORS closed without configured development origins`() {
        val response = mockMvc.perform(
            options("/api/auth/sign-in").behindProxy("eolssu.site")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"),
        ).andReturn().response

        assertNull(response.getHeader("Access-Control-Allow-Origin"))
    }

    private fun jsonPost(host: String, origin: String) = post("/api/auth/sign-in").behindProxy(host)
        .header("Origin", origin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")

    private fun MockHttpServletRequestBuilder.behindProxy(host: String) = header("Host", host).with { request ->
        request.scheme = "http"
        request.serverName = host
        request.serverPort = 80
        request
    }
}

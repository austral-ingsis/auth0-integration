package org.austral.ingsis.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(properties = ["AUTH0_DOMAIN=test.invalid", "AUTH0_AUDIENCE=https://test-api"])
@AutoConfigureMockMvc
class AuthenticationServiceApplicationTests(@Autowired val mockMvc: MockMvc) {

    // Replaces the real decoder so the tests never call Auth0.
    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `index is public`() {
        mockMvc.get("/").andExpect { status { isOk() } }
    }

    @Test
    fun `snippets require a token`() {
        mockMvc.get("/snippets").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `snippets are readable with any valid token`() {
        mockMvc.get("/snippets") { with(jwt()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `creating a snippet works without a CSRF token`() {
        mockMvc.post("/snippets") {
            with(jwt())
            content = "hello"
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `scoped endpoint rejects a token without read-snippets scope`() {
        mockMvc.get("/scopedAuthenticate") { with(jwt()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `scoped endpoint accepts a token with read-snippets scope`() {
        mockMvc.get("/scopedAuthenticate") {
            with(jwt().jwt { it.claim("scope", "read:snippets") })
        }.andExpect { status { isOk() } }
    }
}

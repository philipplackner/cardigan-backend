package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.none
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper

/** Helpers for E2E tests that go through the auth endpoints. */
abstract class AuthE2eTestBase : E2eTest() {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected val password = "Correct horse 7"

    protected fun json(vararg fields: Pair<String, Any?>): String = jsonMapper.writeValueAsString(mapOf(*fields))

    protected fun post(path: String, json: String, token: String? = null, cookie: String? = null): HttpResult =
        call(
            HttpMethod.POST,
            "/api/v1/auth/$path",
            token = token,
            json = json,
            headers = cookie?.let { mapOf("Cookie" to "$COOKIE_NAME=$it") }.orEmpty(),
        )

    protected fun register(
        email: String = "ann@example.com",
        password: String = this.password,
        transport: String = "BODY",
    ): HttpResult = post("register", json("email" to email, "password" to password, "refreshTokenTransport" to transport))

    protected fun login(
        email: String = "ann@example.com",
        password: String = this.password,
        transport: String = "BODY",
    ): HttpResult = post("login", json("email" to email, "password" to password, "refreshTokenTransport" to transport))

    protected fun refreshWithBody(refreshToken: String?, cookie: String? = null): HttpResult =
        post("refresh", transportJson("BODY", refreshToken), cookie = cookie)

    protected fun refreshWithCookie(cookie: String?, bodyToken: String? = null): HttpResult =
        post("refresh", transportJson("COOKIE", bodyToken), cookie = cookie)

    protected fun logoutWithBody(refreshToken: String?, token: String? = null, cookie: String? = null): HttpResult =
        post("logout", transportJson("BODY", refreshToken), token = token, cookie = cookie)

    protected fun logoutWithCookie(cookie: String?, bodyToken: String? = null): HttpResult =
        post("logout", transportJson("COOKIE", bodyToken), cookie = cookie)

    private fun transportJson(transport: String, refreshToken: String?): String =
        if (refreshToken == null) {
            json("refreshTokenTransport" to transport)
        } else {
            json("refreshTokenTransport" to transport, "refreshToken" to refreshToken)
        }

    protected val HttpResult.accessToken: String get() = json()["accessToken"] as String

    protected val HttpResult.refreshToken: String get() = json()["refreshToken"] as String

    protected val HttpResult.userId: String get() = (json()["user"] as Map<*, *>)["id"] as String

    protected val HttpResult.setCookies: List<String> get() = headers["Set-Cookie"].orEmpty()

    /** The value of the refresh cookie this response sets. */
    protected val HttpResult.refreshCookie: String
        get() = setCookies.single().substringBefore(';').removePrefix("$COOKIE_NAME=")

    protected fun HttpResult.assertStatus(expected: Int): HttpResult {
        assertThat(status, "status of $body").isEqualTo(expected)
        return this
    }

    protected fun HttpResult.assertNoCookie(): HttpResult {
        assertThat(headers["Set-Cookie"], "Set-Cookie").isNull()
        return this
    }

    /** Asserts the one `Set-Cookie` header with every agreed attribute and returns the cookie value. */
    protected fun HttpResult.assertRefreshCookie(maxAge: Long = THIRTY_DAYS_IN_SECONDS): String {
        val parts = setCookies.single().split(";").map { it.trim() }
        val (name, value) = parts.first().split("=", limit = 2)
        val attributes = parts.drop(1)
        assertThat(name).isEqualTo(COOKIE_NAME)
        assertThat(attributes).contains("HttpOnly")
        assertThat(attributes).contains("Secure")
        assertThat(attributes).contains("SameSite=Strict")
        assertThat(attributes).contains("Path=/api/v1/auth")
        assertThat(attributes).contains("Max-Age=$maxAge")
        assertThat(attributes).none { it.transform { attribute -> attribute.startsWith("Domain", ignoreCase = true) }.isEqualTo(true) }
        return value
    }

    protected fun HttpResult.assertClearingCookie() {
        assertThat(assertRefreshCookie(maxAge = 0)).isEqualTo("")
    }

    protected fun HttpResult.assertInvalidRefreshToken(): HttpResult {
        assertProblem(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(headers["WWW-Authenticate"]).isNull()
        return this
    }

    protected fun HttpResult.assertFieldErrors(code: ErrorCode, vararg expected: Map<String, Any?>) {
        assertThat(assertProblem(code).fieldErrors).isEqualTo(expected.toList())
    }

    protected fun fieldError(field: String, code: String, vararg limits: Pair<String, Int>): Map<String, Any?> =
        mapOf("field" to field, "code" to code) + limits

    protected fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM user_service.$table", Int::class.java)!!

    protected fun currentUser(accessToken: String): HttpResult = call(HttpMethod.GET, "/api/v1/users/me", accessToken)

    protected fun assertThatBodyIsEmpty(result: HttpResult) {
        assertThat(result.body).isEqualTo("")
        assertThat(result.contentType).isNull()
    }

    protected fun HttpResult.assertSessionTokens(vararg expectedKeys: String): Map<String, Any?> {
        val body = json()
        assertThat(body.keys).isEqualTo(expectedKeys.toSet())
        assertThat(body["accessToken"]).isNotNull()
        return body
    }

    companion object {
        const val COOKIE_NAME = "cardigan_refresh_token"
        const val THIRTY_DAYS_IN_SECONDS = 2_592_000L

        private val jsonMapper: JsonMapper = JsonMapper.builder().build()
    }
}

package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Duration

class AuthRateLimitE2eTest : AuthE2eTestBase() {

    private fun HttpResult.assertRateLimited(retryAfter: String? = null): HttpResult {
        val body = assertProblem(ErrorCode.RATE_LIMITED)
        assertThat(body.fieldErrors).isEqualTo(emptyList())
        val header = headers["Retry-After"].orEmpty().single()
        assertThat(header).matches(Regex("^[1-9][0-9]*$"))
        retryAfter?.let { assertThat(header).isEqualTo(it) }
        assertThat(headers["WWW-Authenticate"]).isNull()
        assertNoCookie()
        return this
    }

    private fun useUpLogin(email: String = "ann@example.com", password: String = "Wrong horse 7") {
        repeat(5) { assertThat(login(email, password).status, "login ${it + 1}").isNotEqualTo(429) }
    }

    private fun useUpRegister(email: String = "ann@example.com") {
        repeat(5) { assertThat(register(email, "weak").status, "register ${it + 1}").isEqualTo(400) }
    }

    @Test
    fun `sixth login for one email is 429 with Retry-After`() {
        val statuses = (1..6).map { login(password = "Wrong horse 7") }

        statuses.take(5).forEach { it.assertProblem(ErrorCode.INVALID_CREDENTIALS) }
        statuses.last().assertRateLimited(retryAfter = "12")
    }

    @Test
    fun `sixth register for one email is 429 with Retry-After`() {
        val first = register().assertStatus(201)
        val next = (2..5).map { register() }
        val sixth = register()

        next.forEach { it.assertProblem(ErrorCode.CONFLICT) }
        sixth.assertRateLimited(retryAfter = "12")
        assertThat(first.status).isEqualTo(201)
    }

    @Test
    fun `limit is full again in the next test`() {
        useUpLogin()

        login().assertRateLimited()
    }

    @Test
    fun `limit is full again in the test after that`() {
        useUpLogin()

        login().assertRateLimited()
    }

    @Test
    fun `one more login passes 12 seconds after the limit was used up, and no sooner`() {
        useUpLogin()

        clock.advanceBy(Duration.ofMillis(11_999))
        login().assertRateLimited(retryAfter = "1")

        clock.advanceBy(Duration.ofMillis(1))
        login().assertProblem(ErrorCode.INVALID_CREDENTIALS)
        login().assertRateLimited(retryAfter = "12")
    }

    @Test
    fun `Retry-After counts down in whole seconds`() {
        useUpLogin()

        login().assertRateLimited(retryAfter = "12")
        clock.advanceBy(Duration.ofSeconds(5))
        login().assertRateLimited(retryAfter = "7")
        clock.advanceBy(Duration.ofMillis(6_500))
        login().assertRateLimited(retryAfter = "1")
    }

    @Test
    fun `waiting for Retry-After is enough`() {
        useUpLogin()
        clock.advanceBy(Duration.ofMillis(2_700))

        val retryAfter = login().assertRateLimited().headers["Retry-After"]!!.single().toLong()
        clock.advanceBy(Duration.ofSeconds(retryAfter))

        login().assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `correct password over the limit is 429 and creates no session`() {
        register().assertStatus(201)
        useUpLogin()

        login(password = password).assertRateLimited()

        assertThat(count("sessions")).isEqualTo(1)
    }

    @Test
    fun `successful logins count toward the limit`() {
        register().assertStatus(201)
        repeat(5) { login().assertStatus(200) }

        login().assertRateLimited()

        assertThat(count("sessions")).isEqualTo(6)
    }

    @Test
    fun `another client is limited for the same email`() {
        register().assertStatus(201)
        useUpLogin()

        val otherClient = call(
            HttpMethod.POST,
            "/api/v1/auth/login",
            json = json("email" to "ann@example.com", "password" to password, "refreshTokenTransport" to "COOKIE"),
            headers = mapOf("X-Forwarded-For" to "203.0.113.9", "User-Agent" to "another-client"),
        )

        otherClient.assertRateLimited()
    }

    @Test
    fun `weak password over the register limit is 429, not 400`() {
        useUpRegister()

        register(password = "weak").assertRateLimited()
    }

    @Test
    fun `invalid register requests count, so a valid one is limited too`() {
        useUpRegister()

        register(password = password).assertRateLimited()

        assertThat(count("users")).isEqualTo(0)
    }

    @Test
    fun `used email over the register limit is 429, not 409`() {
        register().assertStatus(201)
        repeat(4) { register().assertProblem(ErrorCode.CONFLICT) }

        register().assertRateLimited()

        assertThat(count("users")).isEqualTo(1)
    }

    @Test
    fun `email forms that normalize to one address share a limit`() {
        listOf("ann@example.com", " Ann@Example.com ", "ANN@EXAMPLE.COM", "ann@example.com ", "aNN@eXAMPLE.cOM")
            .forEach { login(email = it, password = "Wrong horse 7").assertProblem(ErrorCode.INVALID_CREDENTIALS) }

        login(email = "  ANN@example.com").assertRateLimited()
    }

    @Test
    fun `another email is not limited`() {
        useUpLogin("ann@example.com")

        login(email = "bob@example.com", password = "Wrong horse 7").assertProblem(ErrorCode.INVALID_CREDENTIALS)
        login(email = "ann@example.com").assertRateLimited()
    }

    @Test
    fun `login and register count separately`() {
        useUpLogin()

        register().assertStatus(201)

        useUpRegister("bob@example.com")
        login(email = "bob@example.com", password = "Wrong horse 7").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `invalid emails are limited like any other`() {
        repeat(5) { login(email = "not an email").assertProblem(ErrorCode.INVALID_CREDENTIALS) }

        login(email = "not an email").assertRateLimited()
    }

    @Test
    fun `20 logins without an email are all 400 REQUIRED`() {
        val bodies = listOf(
            json("password" to password, "refreshTokenTransport" to "BODY"),
            json("email" to null, "password" to password, "refreshTokenTransport" to "BODY"),
            json("email" to "", "password" to password, "refreshTokenTransport" to "BODY"),
            json("email" to "   ", "password" to password, "refreshTokenTransport" to "BODY"),
        )

        repeat(20) { i ->
            post("login", bodies[i % bodies.size])
                .assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("email", "REQUIRED"))
        }
    }

    @Test
    fun `20 registers without an email are all 400 REQUIRED`() {
        repeat(20) {
            post("register", json("password" to password, "refreshTokenTransport" to "BODY"))
                .assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("email", "REQUIRED"))
        }
    }

    @Test
    fun `unparseable bodies use up no limit`() {
        register().assertStatus(201)

        repeat(6) {
            post("login", """{"email": "ann@example.com", "password": """).assertProblem(ErrorCode.MALFORMED_REQUEST)
        }

        login().assertStatus(200)
    }

    @Test
    fun `unsupported content type uses up no limit`() {
        register().assertStatus(201)
        val body = json("email" to "ann@example.com", "password" to password, "refreshTokenTransport" to "BODY")

        repeat(6) {
            call(HttpMethod.POST, "/api/v1/auth/login", json = body, headers = mapOf("Content-Type" to "text/plain"))
                .assertProblem(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        }

        login().assertStatus(200)
    }

    @Test
    fun `100 refreshes with unknown tokens are all 401, never 429`() {
        val statuses = (1..100).map { refreshWithBody("unknown-token-$it").status }

        assertThat(statuses.toSet()).isEqualTo(setOf(401))
    }

    @Test
    fun `100 refreshes and logouts of one session are never 429`() {
        var token = register().assertStatus(201).refreshToken

        repeat(100) { token = refreshWithBody(token).assertStatus(200).refreshToken }
        val logouts = (1..100).map { logoutWithBody(token).status }

        assertThat(logouts.toSet()).isEqualTo(setOf(204))
    }

    @Test
    fun `2000 requests to another endpoint are never 429`() {
        val token = register().assertStatus(201).accessToken

        val statuses = (1..2000).map { currentUser(token).status }.toSet()

        assertThat(statuses).isEqualTo(setOf(200))
    }
}

package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.sql.Timestamp
import java.time.Duration

class AuthRegisterLoginE2eTest : AuthE2eTestBase() {

    private fun storedPasswordHash(email: String): String =
        jdbcTemplate.queryForObject("SELECT password_hash FROM user_service.users WHERE email = ?", String::class.java, email)!!

    @Test
    fun `register with the body transport answers 201 with tokens and the lowercased user`() {
        val result = register(email = "  Ann@Example.com ", transport = "BODY").assertStatus(201).assertNoCookie()

        val body = result.assertSessionTokens("accessToken", "accessTokenExpiresAt", "refreshToken", "user")
        assertThat(body["accessTokenExpiresAt"]).isEqualTo("2026-11-15T09:30:30.000Z")
        assertThat(body["refreshToken"] as String).matches(Regex("^[A-Za-z0-9_-]{43}$"))
        val user = body["user"] as Map<*, *>
        assertThat(user.keys).isEqualTo(setOf("id", "email", "createdAt"))
        assertThat(user["email"]).isEqualTo("ann@example.com")
        assertThat(user["createdAt"]).isEqualTo("2026-11-15T09:15:30.123Z")
        assertThat(result.contentType).isEqualTo("application/json")
    }

    @Test
    fun `returned access token works and names the registered user`() {
        val registered = register()

        val me = currentUser(registered.accessToken).assertStatus(200)

        assertThat(me.json()).isEqualTo(registered.json()["user"])
        assertThat(me.json()["id"]).isEqualTo(registered.userId)
    }

    @Test
    fun `register with the cookie transport leaves refreshToken out and sets the cookie`() {
        val result = register(transport = "COOKIE").assertStatus(201)

        result.assertSessionTokens("accessToken", "accessTokenExpiresAt", "user")
        assertThat(result.body).doesNotContain("refreshToken")
        val cookie = result.assertRefreshCookie()
        assertThat(cookie).matches(Regex("^[A-Za-z0-9_-]{43}$"))
        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `register with an empty body reports every required field`() {
        post("register", "{}").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("email", "REQUIRED"),
            fieldError("password", "REQUIRED"),
            fieldError("refreshTokenTransport", "REQUIRED"),
        )
        assertThat(count("users")).isEqualTo(0)
    }

    @Test
    fun `register with null fields reports them as required`() {
        post("register", json("email" to null, "password" to null, "refreshTokenTransport" to null)).assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("email", "REQUIRED"),
            fieldError("password", "REQUIRED"),
            fieldError("refreshTokenTransport", "REQUIRED"),
        )
    }

    @Test
    fun `bad email and weak password are both reported and no user is stored`() {
        register(email = "ann@", password = "alllowercase12").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("email", "INVALID_FORMAT"),
            fieldError("password", "PASSWORD_TOO_WEAK"),
        )
        assertThat(count("users")).isEqualTo(0)
        assertThat(count("sessions")).isEqualTo(0)
    }

    @Test
    fun `password length limits carry min and max`() {
        register(password = "Correct hs7").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("password", "TOO_SHORT", "min" to 12),
        )
        register(email = "bob@example.com", password = "Aa1" + "x".repeat(126)).assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("password", "TOO_LONG", "max" to 128),
        )
    }

    @Test
    fun `email over 254 characters is TOO_LONG`() {
        register(email = "a".repeat(243) + "@example.com").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("email", "TOO_LONG", "max" to 254),
        )
    }

    @Test
    fun `unknown transport on register is INVALID_VALUE`() {
        register(transport = "HEADER").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("refreshTokenTransport", "INVALID_VALUE"),
        )
        assertThat(count("users")).isEqualTo(0)
    }

    @Test
    fun `used email in another case is 409 with ALREADY_USED`() {
        register(email = "ann@example.com").assertStatus(201)

        val result = register(email = "ANN@Example.com")

        result.assertFieldErrors(ErrorCode.CONFLICT, fieldError("email", "ALREADY_USED"))
        result.assertNoCookie()
        assertThat(count("users")).isEqualTo(1)
        assertThat(count("sessions")).isEqualTo(1)
    }

    @Test
    fun `used email with a weak password is 400, not 409`() {
        register().assertStatus(201)

        register(password = "weak").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("password", "TOO_SHORT", "min" to 12),
        )
    }

    @Test
    fun `stored password hash is BCrypt with the configured cost and without the password`() {
        register().assertStatus(201)

        val hash = storedPasswordHash("ann@example.com")

        assertThat(hash).matches(Regex("""^\$2[aby]\$04\$[./A-Za-z0-9]{53}$"""))
        assertThat(hash).doesNotContain(password)
    }

    @Test
    fun `two users with the same password get different hashes`() {
        register(email = "ann@example.com").assertStatus(201)
        register(email = "bob@example.com").assertStatus(201)

        assertThat(storedPasswordHash("ann@example.com")).isNotEqualTo(storedPasswordHash("bob@example.com"))
    }

    @Test
    fun `128-character password that differs in the last character doesn't log in`() {
        val longPassword = "Aa1" + "x".repeat(124) + "y"
        register(password = longPassword).assertStatus(201)

        login(password = "Aa1" + "x".repeat(124) + "z").assertProblem(ErrorCode.INVALID_CREDENTIALS)
        login(password = longPassword).assertStatus(200)
    }

    @Test
    fun `multi-byte password over 72 bytes registers and logs in`() {
        val multiByte = "Aa1" + "😀".repeat(100)

        register(password = multiByte).assertStatus(201)

        login(password = multiByte).assertStatus(200)
        login(password = "Aa1" + "😀".repeat(99) + "😁").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `password registered composed logs in decomposed`() {
        register(password = "Café password 1").assertStatus(201)

        login(password = "Café password 1").assertStatus(200)
    }

    @Test
    fun `login with the cookie transport sets the cookie with every attribute, also over plain HTTP`() {
        register().assertStatus(201)

        val result = login(transport = "COOKIE").assertStatus(200)

        assertThat(baseUrl).matches(Regex("^http://.*"))
        result.assertSessionTokens("accessToken", "accessTokenExpiresAt", "user")
        assertThat(result.body).doesNotContain("refreshToken")
        assertThat(result.assertRefreshCookie()).matches(Regex("^[A-Za-z0-9_-]{43}$"))
    }

    @Test
    fun `login with the body transport returns the refresh token and sets no cookie`() {
        val registered = register()

        val result = login(transport = "BODY").assertStatus(200).assertNoCookie()

        val body = result.assertSessionTokens("accessToken", "accessTokenExpiresAt", "refreshToken", "user")
        assertThat(body["user"]).isEqualTo(registered.json()["user"])
        assertThat(body["accessTokenExpiresAt"]).isEqualTo("2026-11-15T09:30:30.000Z")
        assertThat(currentUser(result.accessToken).json()["id"]).isEqualTo(registered.userId)
    }

    @Test
    fun `login without a password is 400 REQUIRED`() {
        post("login", json("email" to "ann@example.com", "refreshTokenTransport" to "BODY"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("password", "REQUIRED"))
    }

    @Test
    fun `login without a transport is 400 REQUIRED`() {
        post("login", json("email" to "ann@example.com", "password" to password))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("refreshTokenTransport", "REQUIRED"))
    }

    @Test
    fun `login with a blank email is 400 REQUIRED`() {
        login(email = "   ").assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("email", "REQUIRED"))
    }

    @Test
    fun `login with an empty password is 400 REQUIRED`() {
        register().assertStatus(201)

        login(password = "").assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("password", "REQUIRED"))
    }

    @Test
    fun `login with a password of only spaces is 401`() {
        register().assertStatus(201)

        login(password = "   ").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `login with a malformed email or a short password is 401, not a field error`() {
        register().assertStatus(201)

        login(email = "not an email", password = "anything").assertProblem(ErrorCode.INVALID_CREDENTIALS)
        login(password = "abc").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `login with an unknown transport is 400 and creates no session`() {
        register().assertStatus(201)

        login(transport = "HEADER").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            fieldError("refreshTokenTransport", "INVALID_VALUE"),
        )
        assertThat(count("sessions")).isEqualTo(1)
    }

    @Test
    fun `login email with U+0000 answers like an unknown email`() {
        register().assertStatus(201)

        val controlCharacter = login(email = "ann\u0000@example.com")
        val unknown = login(email = "nobody@example.com")

        controlCharacter.assertProblem(ErrorCode.INVALID_CREDENTIALS)
        assertThat(controlCharacter.body).isEqualTo(unknown.body)
        assertThat(comparableHeaders(controlCharacter)).isEqualTo(comparableHeaders(unknown))
    }

    @Test
    fun `login email over 254 characters is 401`() {
        login(email = "a".repeat(300) + "@example.com").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `login matches the email in another case`() {
        register(email = "Ann@Example.com").assertStatus(201)

        val result = login(email = " ANN@example.COM ").assertStatus(200)

        assertThat((result.json()["user"] as Map<*, *>)["email"]).isEqualTo("ann@example.com")
    }

    @Test
    fun `login with a space around the password is 401`() {
        register().assertStatus(201)

        login(password = " $password").assertProblem(ErrorCode.INVALID_CREDENTIALS)
        login(password = "$password ").assertProblem(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `unknown email and wrong password answer identically without a bearer challenge`() {
        register().assertStatus(201)

        val unknownEmail = login(email = "bob@example.com")
        val wrongPassword = login(password = "Wrong horse 7")

        unknownEmail.assertProblem(ErrorCode.INVALID_CREDENTIALS)
        wrongPassword.assertProblem(ErrorCode.INVALID_CREDENTIALS)
        assertThat(unknownEmail.body).isEqualTo(wrongPassword.body)
        assertThat(comparableHeaders(unknownEmail)).isEqualTo(comparableHeaders(wrongPassword))
        assertThat(unknownEmail.headers["WWW-Authenticate"]).isNull()
        assertThat(wrongPassword.headers["WWW-Authenticate"]).isNull()
        assertThat(wrongPassword.headers["Set-Cookie"]).isNull()
    }

    @Test
    fun `refresh token is stored only as its hash, expiring 30 days after the login`() {
        register().assertStatus(201)
        clock.advanceBy(Duration.ofHours(2))

        val token = login().refreshToken

        val everyStoredValue = jdbcTemplate.queryForList(
            """
            SELECT token_hash FROM user_service.refresh_tokens
            UNION ALL SELECT id::text FROM user_service.refresh_tokens
            UNION ALL SELECT password_hash FROM user_service.users
            """,
            String::class.java,
        )
        assertThat(everyStoredValue).doesNotContain(token)
        val expiresAt = jdbcTemplate.queryForObject(
            "SELECT expires_at FROM user_service.refresh_tokens WHERE token_hash = ?",
            Timestamp::class.java,
            hashRefreshToken(token),
        )
        assertThat(expiresAt).isNotNull().transform { it.toInstant() }.isEqualTo(clock.instant().plus(Duration.ofDays(30)))
    }

    @Test
    fun `two logins are two sessions with their own tokens`() {
        register().assertStatus(201)

        val first = login().refreshToken
        val second = login().refreshToken

        assertThat(first).isNotEqualTo(second)
        assertThat(count("sessions")).isEqualTo(3)
        assertThat(count("refresh_tokens")).isEqualTo(3)
    }

    @Test
    fun `login needs no Authorization header`() {
        register().assertStatus(201)

        login().assertStatus(200)
    }

    @Test
    fun `auth routes ignore a broken bearer token`() {
        val body = json("email" to "ann@example.com", "password" to password, "refreshTokenTransport" to "BODY")

        post("register", body, token = "garbage").assertStatus(201)
        val login = post("login", body, token = "garbage").assertStatus(200)
        post("refresh", json("refreshTokenTransport" to "BODY", "refreshToken" to login.refreshToken), token = "garbage")
            .assertStatus(200)
    }

    @Test
    fun `other method on an auth path needs a token`() {
        listOf("register", "login", "refresh", "logout").forEach { path ->
            val result = call(HttpMethod.GET, "/api/v1/auth/$path")

            result.assertProblem(ErrorCode.UNAUTHENTICATED)
            assertThat(result.headers["WWW-Authenticate"]).isEqualTo(listOf("Bearer"))
        }
    }

    @Test
    fun `other method on an auth path with a token is ROUTE_NOT_FOUND`() {
        val token = register().accessToken

        call(HttpMethod.GET, "/api/v1/auth/login", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `unparseable register body is MALFORMED_REQUEST`() {
        post("register", "{not json").assertProblem(ErrorCode.MALFORMED_REQUEST)
        post("login", """{"email": 5, "password": [], "refreshTokenTransport": {}}""")
            .assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    private fun comparableHeaders(result: HttpResult): Map<String, List<String>> =
        result.headers.headerNames().filterNot { it.equals("Date", ignoreCase = true) }
            .associate { it.lowercase() to result.headers[it].orEmpty() }
}

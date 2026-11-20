package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.logging.LogLevel
import org.springframework.boot.logging.LoggingSystem
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod

@ExtendWith(OutputCaptureExtension::class)
class AuthLoggingE2eTest : AuthE2eTestBase() {

    @Autowired
    private lateinit var loggingSystem: LoggingSystem

    @BeforeEach
    fun enableDebugLogging() {
        loggingSystem.setLogLevel(PROJECT_LOGGER, LogLevel.DEBUG)
    }

    @AfterEach
    fun resetLogging() {
        loggingSystem.setLogLevel(PROJECT_LOGGER, null)
    }

    @Test
    fun `full flow and its failure cases log no password, hash or token`(output: CapturedOutput) {
        val weakPassword = "weak-marker-password"
        val wrongPassword = "Wrong-marker horse 7"

        val registered = register().assertStatus(201)
        val webRegistered = register(email = "web@example.com", transport = "COOKIE").assertStatus(201)
        val loggedIn = login().assertStatus(200)
        val webLoggedIn = login(email = "web@example.com", transport = "COOKIE").assertStatus(200)
        val refreshed = refreshWithBody(loggedIn.refreshToken).assertStatus(200)
        val webRefreshed = refreshWithCookie(webLoggedIn.refreshCookie).assertStatus(200)
        refreshWithBody(loggedIn.refreshToken).assertProblem(ErrorCode.INVALID_REFRESH_TOKEN)
        refreshWithCookie(webLoggedIn.refreshCookie).assertProblem(ErrorCode.INVALID_REFRESH_TOKEN)
        logoutWithBody(registered.refreshToken).assertStatus(204)
        logoutWithCookie(webRegistered.refreshCookie).assertStatus(204)
        register(email = "bob@example.com", password = weakPassword).assertProblem(ErrorCode.VALIDATION_FAILED)
        register().assertProblem(ErrorCode.CONFLICT)
        login(password = wrongPassword).assertProblem(ErrorCode.INVALID_CREDENTIALS)
        repeat(4) { login(password = wrongPassword) }
        login().assertProblem(ErrorCode.RATE_LIMITED)
        call(HttpMethod.GET, "/api/v1/test/boom", registered.accessToken).assertProblem(ErrorCode.INTERNAL_ERROR)
        LoggerFactory.getLogger(PROJECT_LOGGER).debug("debug-marker of the log test")

        val refreshTokens = listOf(
            registered.refreshToken,
            loggedIn.refreshToken,
            refreshed.refreshToken,
            webRegistered.refreshCookie,
            webLoggedIn.refreshCookie,
            webRefreshed.refreshCookie,
        )
        val accessTokens = listOf(registered, webRegistered, loggedIn, webLoggedIn, refreshed, webRefreshed).map { it.accessToken }
        val passwordHashes = jdbcTemplate.queryForList("SELECT password_hash FROM user_service.users", String::class.java)
            .filterNotNull()
        val tokenHashes = jdbcTemplate.queryForList("SELECT token_hash FROM user_service.refresh_tokens", String::class.java)
            .filterNotNull()
        assertThat(passwordHashes).hasSize(2)
        assertThat(tokenHashes).hasSize(6)
        assertThat(tokenHashes.toSet()).contains(hashRefreshToken(registered.refreshToken))

        val log = output.all
        assertThat(log).contains("debug-marker of the log test")
        assertThat(log).contains("boom-marker from the endpoint")
        val secrets = listOf(password, weakPassword, wrongPassword) + passwordHashes + refreshTokens + tokenHashes + accessTokens
        secrets.forEach { secret -> assertThat(log).doesNotContain(secret) }
    }

    private companion object {
        const val PROJECT_LOGGER = "com.plcoding.cardigan"
    }
}

package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.matches
import com.plcoding.cardigan.user.UserIntegrationTest
import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.IssuedSession
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.COOKIE
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import java.sql.Timestamp
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(AuthServiceIntegrationTest.EventRecorderConfig::class)
class AuthServiceIntegrationTest : UserIntegrationTest() {
    class EventRecorder {
        val events = CopyOnWriteArrayList<Any>()

        @EventListener
        fun on(event: Any) {
            events += event
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class EventRecorderConfig {
        @Bean
        fun eventRecorder() = EventRecorder()
    }

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var eventRecorder: EventRecorder

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    private object ProbeEvent

    private fun isOurs(event: Any): Boolean = event::class.java.name.startsWith("com.plcoding.cardigan")

    private val password = "Correct horse 7"

    private fun credentials(
        email: String = "ann@example.com",
        password: String = this.password,
    ) = Credentials(email, password, BODY)

    private fun register(email: String = "ann@example.com"): IssuedSession = authService.register(credentials(email))

    private fun revokedAtOf(refreshToken: String): Timestamp? =
        jdbcTemplate.queryForObject(
            """
        SELECT s.revoked_at FROM user_service.sessions s
        JOIN user_service.refresh_tokens t ON t.session_id = s.id
        WHERE t.token_hash = ?
        """,
            Timestamp::class.java,
            hashRefreshToken(refreshToken),
        )

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM user_service.$table", Int::class.java)!!

    private fun <T> inParallel(vararg calls: () -> T): List<Result<T>> {
        val barrier = CyclicBarrier(calls.size)
        val executor = Executors.newFixedThreadPool(calls.size)
        try {
            val futures =
                calls.map { call ->
                    executor.submit(
                        Callable {
                            barrier.await(10, TimeUnit.SECONDS)
                            runCatching(call)
                        },
                    )
                }
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `register stores a BCrypt hash with the configured cost and only the token hash`() {
        val issued = register()

        val passwordHash =
            jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_service.users WHERE id = ?",
                String::class.java,
                issued.user.id,
            )
        assertThat(passwordHash).isNotNull().matches(Regex("""^\$2[aby]\$04\$[./A-Za-z0-9]{53}$"""))
        val storedTokens =
            jdbcTemplate.queryForList(
                "SELECT token_hash FROM user_service.refresh_tokens",
                String::class.java,
            )
        assertThat(storedTokens).isEqualTo(listOf(hashRefreshToken(issued.tokens.refreshToken)))
        val expiresAt =
            jdbcTemplate.queryForObject(
                "SELECT expires_at FROM user_service.refresh_tokens",
                Timestamp::class.java,
            )
        assertThat(expiresAt!!.toInstant()).isEqualTo(START.plus(Duration.ofDays(30)))
    }

    @Test
    fun `register, login, refresh and logout work against the database`() {
        val registered = register()
        val login = authService.login(credentials())
        val refreshed = authService.refresh(BODY, login.tokens.refreshToken)

        assertThat(login.user).isEqualTo(registered.user)
        assertThat(refreshed).isInstanceOf<RefreshResult.Refreshed>()
        val newToken = (refreshed as RefreshResult.Refreshed).tokens.refreshToken

        authService.logout(BODY, newToken)

        assertThat(authService.refresh(BODY, newToken)).isEqualTo(RefreshResult.Rejected)
        assertThat(revokedAtOf(registered.tokens.refreshToken)).isNull()
        assertThat(count("sessions")).isEqualTo(2)
        assertThat(count("refresh_tokens")).isEqualTo(3)
    }

    @Test
    fun `two parallel refreshes with one token give at most one success and a revoked session`() {
        repeat(15) { round ->
            val issued = register("user$round@example.com")
            val token = issued.tokens.refreshToken

            val results =
                inParallel(
                    { authService.refresh(BODY, token) },
                    { authService.refresh(BODY, token) },
                ).map { it.getOrThrow() }

            val successes = results.filterIsInstance<RefreshResult.Refreshed>()
            assertThat(successes.size, "successes in round $round").isLessThanOrEqualTo(1)
            assertThat(results.count { it == RefreshResult.Rejected } >= 1, "a rejection in round $round").isTrue()
            assertThat(revokedAtOf(token), "revocation in round $round").isNotNull()
            successes.forEach { winner ->
                assertThat(authService.refresh(BODY, winner.tokens.refreshToken)).isEqualTo(RefreshResult.Rejected)
            }
        }
    }

    @Test
    fun `two parallel registrations of one email give one user and one EmailAlreadyUsedException`() {
        repeat(15) { round ->
            val email = "user$round@example.com"

            val results =
                inParallel(
                    { authService.register(credentials(email)) },
                    { authService.register(credentials(email)) },
                )

            assertThat(results.count { it.isSuccess }, "successes in round $round").isEqualTo(1)
            val failure = results.single { it.isFailure }.exceptionOrNull()
            assertThat(failure, "failure in round $round").isNotNull().isInstanceOf<EmailAlreadyUsedException>()
            val users =
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM user_service.users WHERE email = ?",
                    Int::class.java,
                    email,
                )
            assertThat(users, "users in round $round").isEqualTo(1)
        }
        assertThat(count("sessions")).isEqualTo(15)
        assertThat(count("refresh_tokens")).isEqualTo(15)
    }

    @Test
    fun `reuse revocation is committed although the refresh fails`() {
        val first = register().tokens.refreshToken
        val second = (authService.refresh(BODY, first) as RefreshResult.Refreshed).tokens.refreshToken
        clock.advanceBy(Duration.ofMinutes(1))

        val reuse = authService.refresh(BODY, first)

        assertThat(reuse).isEqualTo(RefreshResult.Rejected)
        assertThat(revokedAtOf(first)?.toInstant()).isEqualTo(clock.instant())
        assertThat(authService.refresh(BODY, second)).isEqualTo(RefreshResult.Rejected)
    }

    @Test
    fun `failed registration leaves no session behind`() {
        register()

        runCatching { register() }

        assertThat(count("users")).isEqualTo(1)
        assertThat(count("sessions")).isEqualTo(1)
        assertThat(count("refresh_tokens")).isEqualTo(1)
    }

    @Test
    fun `transport is stored with the session`() {
        val issued = authService.register(Credentials("ann@example.com", password, COOKIE))

        val transport = jdbcTemplate.queryForObject("SELECT transport FROM user_service.sessions", String::class.java)

        assertThat(transport).isEqualTo("COOKIE")
        assertThat(authService.refresh(BODY, issued.tokens.refreshToken)).isEqualTo(RefreshResult.Rejected)
        assertThat(authService.refresh(COOKIE, issued.tokens.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `auth flows publish no application event of ours`() {
        eventPublisher.publishEvent(ProbeEvent)
        assertThat(eventRecorder.events.filter(::isOurs)).isEqualTo(listOf<Any>(ProbeEvent))
        eventRecorder.events.clear()

        authService.checkRateLimit(AuthAction.REGISTER, "ann@example.com")
        val registered = register()
        authService.checkRateLimit(AuthAction.LOGIN, "ann@example.com")
        val login = authService.login(credentials())
        val refreshed = authService.refresh(BODY, login.tokens.refreshToken) as RefreshResult.Refreshed
        authService.refresh(BODY, login.tokens.refreshToken)
        authService.logout(BODY, refreshed.tokens.refreshToken)
        authService.logout(BODY, registered.tokens.refreshToken)
        runCatching { register() }
        runCatching { authService.login(credentials(password = "wrong")) }

        assertThat(eventRecorder.events.filter(::isOurs)).isEmpty()
        assertThat(count("sessions")).isEqualTo(2)
    }

    @Test
    fun `20 logins leave 20 sessions`() {
        register()

        repeat(20) { authService.login(credentials()) }

        assertThat(count("sessions")).isEqualTo(21)
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT token_hash FROM user_service.refresh_tokens")).hasSize(21)
    }
}

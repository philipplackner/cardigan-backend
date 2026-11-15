package com.plcoding.cardigan

import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.common.api.web.PublicRoute
import com.plcoding.cardigan.common.api.web.PublicRoutes
import com.plcoding.cardigan.common.domain.security.AccessTokenIssuer
import com.plcoding.cardigan.testapi.FailingTestFilter
import com.plcoding.cardigan.testapi.TestApiController
import com.plcoding.cardigan.user.UserFixtures
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

@TestConfiguration(proxyBeanMethods = false)
@Import(TestApiController::class)
class E2eTestConfig {

    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock()

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

    @Bean
    fun userFixtures(userRepository: UserRepository, clock: MutableClock) = UserFixtures(userRepository, clock)

    @Bean
    fun testPublicRoutes() = PublicRoutes(PublicRoute(HttpMethod.GET, "/api/v1/test/public"))

    @Bean
    fun failingTestFilter(): FilterRegistrationBean<FailingTestFilter> =
        FilterRegistrationBean(FailingTestFilter()).apply {
            order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 10
        }
}

/** A response as the client sees it. */
data class HttpResult(
    val status: Int,
    val headers: HttpHeaders,
    val body: String,
) {

    val contentType: String? get() = headers.getFirst(HttpHeaders.CONTENT_TYPE)

    fun json(): Map<String, Any?> = jsonMapper.readValue(body, Map::class.java).mapKeys { it.key as String }

    private companion object {
        val jsonMapper: JsonMapper = JsonMapper.builder().build()
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, E2eTestConfig::class)
abstract class E2eTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var userFixtures: UserFixtures

    @Autowired
    protected lateinit var accessTokenIssuer: AccessTokenIssuer

    protected lateinit var client: RestTestClient

    private val httpClient: HttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    protected val baseUrl: String get() = "http://localhost:$port"

    @BeforeEach
    fun setUpE2e() {
        databaseCleaner.clean("user_service")
        clock.set(START)
        client = RestTestClient.bindToServer().baseUrl(baseUrl).build()
    }

    /** Creates a user and returns a valid access token for it. */
    protected fun signIn(email: String = "ann@example.com"): SignedInUser {
        val user = userFixtures.createUser(email)
        return SignedInUser(user, accessTokenIssuer.issue(user.id).token)
    }

    /** A valid access token for a user ID that has no record. */
    protected fun tokenWithoutUser(userId: UUID = UUID.randomUUID()): String = accessTokenIssuer.issue(userId).token

    protected fun authenticatedClient(token: String): RestTestClient =
        client.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token").build()

    protected fun call(
        method: HttpMethod,
        path: String,
        token: String? = null,
        json: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult {
        val spec = client.method(method).uri(path).headers { h ->
            token?.let { h.setBearerAuth(it) }
            headers.forEach { (name, value) -> h.set(name, value) }
        }
        if (json != null) {
            if (HttpHeaders.CONTENT_TYPE !in headers) spec.contentType(MediaType.APPLICATION_JSON)
            spec.body(json)
        }
        val result = spec.exchange().returnResult()
        return HttpResult(
            status = result.status.value(),
            headers = result.responseHeaders,
            body = result.responseBodyContent?.toString(Charsets.UTF_8).orEmpty(),
        )
    }

    /**
     * Sends a request over a plain HTTP/1.1 client, for cases the regular client hides: chunked
     * bodies ([chunked]), exact byte counts and raw paths.
     */
    protected fun raw(
        method: String,
        path: String,
        token: String? = null,
        body: ByteArray? = null,
        chunked: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult {
        val publisher = when {
            body == null -> HttpRequest.BodyPublishers.noBody()
            chunked -> HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body) }
            else -> HttpRequest.BodyPublishers.ofByteArray(body)
        }
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, publisher)
        token?.let { builder.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val responseHeaders = HttpHeaders()
        response.headers().map().forEach { (name, values) -> responseHeaders.addAll(name, values) }
        return HttpResult(response.statusCode(), responseHeaders, response.body())
    }

    data class SignedInUser(val user: User, val token: String)

    companion object {
        val START: Instant = Instant.parse("2026-11-15T09:15:30.123Z")
    }
}

package com.plcoding.cardigan.testapi

import com.plcoding.cardigan.common.api.requestUserId
import com.plcoding.cardigan.common.api.validation.RequestValidator
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidRange
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.common.api.validation.ValidUuid
import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.boot.test.context.TestComponent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

enum class TestColorDto { RED, GREEN }

data class TestNestedRequest(
    @Required
    @ValidText(min = 1, max = 10)
    val name: String?,
)

data class TestRequest(
    @Required
    @ValidText(min = 1, max = 10)
    val name: String?,
    @Required
    @ValidText(min = 2, max = 5)
    val key: String?,
    @ValidText(max = 20, multiline = true)
    val note: String? = null,
    val labelIds: List<@ValidUuid String>? = null,
    @ValidEnum(TestColorDto::class)
    val color: String? = null,
    @ValidRange(min = 0, max = 100)
    val position: BigInteger? = null,
    @field:Valid
    val nested: TestNestedRequest? = null,
    @field:Valid
    val items: List<TestNestedRequest>? = null,
)

data class WhoAmIDto(val userId: UUID)

data class ThingDto(val id: UUID, val createdAt: Instant)

/**
 * Endpoints that exist only in E2E tests. `@TestComponent` keeps it out of component scanning, so
 * only contexts that import it (the E2E base class) serve it and the OpenAPI snapshot never sees it.
 */
@RestController
@TestComponent
@RequestMapping("/api/v1/test")
class TestApiController(
    private val requestValidator: RequestValidator,
) {

    @GetMapping("/whoami")
    fun whoami(): WhoAmIDto = WhoAmIDto(requestUserId)

    @GetMapping("/boom")
    fun boom(): Nothing = throw IllegalStateException("boom-marker from the endpoint")

    @PostMapping("/echo")
    fun echo(@Valid @RequestBody request: TestRequest): TestRequest = request

    @PutMapping("/things/{id}")
    fun updateThing(@RequestBody request: TestRequest, @PathVariable id: UUID): ThingDto {
        if (id != EXISTING_THING_ID) throw ResourceNotFoundException("No thing $id")
        requestValidator.validate(request)
        return ThingDto(id, Instant.parse("2026-11-15T09:15:30Z"))
    }

    @GetMapping("/things/{id}")
    fun getThing(@PathVariable id: UUID): ThingDto = ThingDto(id, Instant.parse("2026-11-15T09:15:30Z"))

    @PostMapping("/conflict")
    fun conflict(): Nothing = throw UniquenessConflictException("key", "Key ab is used")

    @GetMapping("/public")
    fun publicEndpoint(): ThingDto = ThingDto(EXISTING_THING_ID, Instant.parse("2026-11-15T09:15:30Z"))

    @PostMapping("/no-body")
    fun noBody(): WhoAmIDto = WhoAmIDto(requestUserId)

    companion object {
        val EXISTING_THING_ID: UUID = UUID.fromString("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10")
    }
}

/** Throws on one path, to test failures in filters that run before any endpoint. */
class FailingTestFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.requestURI == "/api/v1/test/boom-filter") {
            throw IllegalStateException("boom-marker from the filter")
        }
        chain.doFilter(request, response)
    }
}

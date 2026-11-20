package com.plcoding.cardigan.common.api.openapi

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.plcoding.cardigan.common.api.validation.PatchRequest
import com.fasterxml.jackson.annotation.JsonInclude
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidRange
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.common.api.validation.ValidUuid
import com.plcoding.cardigan.common.api.web.PublicRoute
import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import com.plcoding.cardigan.common.api.web.PublicRoutes
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

enum class WidgetColorDto { RED, GREEN }

@Schema(enumAsRef = true)
enum class WidgetShapeDto { ROUND, SQUARE }

data class CreateWidgetRequest(
    @Required
    @ValidText(min = 1, max = 10)
    val name: String?,
    @ValidText(max = 10)
    val note: String?,
    @ValidUuid
    val ownerId: String?,
    val labelIds: List<@ValidUuid String>?,
    @ValidEnum(WidgetColorDto::class)
    val color: String?,
    @ValidRange(min = 0, max = 10)
    val position: BigInteger?,
    @ValidEnum(WidgetShapeDto::class)
    @field:Schema(implementation = WidgetShapeDto::class)
    val shape: String?,
)

class UpdateWidgetRequest : PatchRequest() {

    @ValidText(min = 1, max = 10)
    var name: String? = null
        set(value) {
            field = sent("name", value)
        }

    @ValidEnum(WidgetColorDto::class)
    var color: String? = null
        set(value) {
            field = sent("color", value)
        }
}

data class WidgetDto(
    val id: UUID,
    val name: String,
    val note: String?,
    val createdAt: Instant,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val secret: String?,
)

@RestController
@Tag(name = "Widgets")
class WidgetController {

    @PostMapping("/api/v1/widgets")
    @Operation(operationId = "createWidget")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun createWidget(@RequestBody request: CreateWidgetRequest): WidgetDto = error("not called")

    @PatchMapping("/api/v1/widgets/{id}")
    @Operation(operationId = "updateWidget")
    fun updateWidget(@RequestBody request: UpdateWidgetRequest, @PathVariable id: UUID): WidgetDto = error("not called")

    @GetMapping("/api/v1/widgets/{id}")
    @Operation(operationId = "getWidget")
    fun getWidget(@PathVariable id: UUID): WidgetDto = error("not called")

    @GetMapping("/api/v1/public-widgets")
    @Operation(operationId = "listPublicWidgets")
    fun listPublicWidgets(): List<WidgetDto> = error("not called")
}

@SpringBootApplication
@Import(PublicRouteRegistry::class)
class OpenApiTestApplication {

    @Bean
    fun publicWidgetRoutes() = PublicRoutes(PublicRoute(HttpMethod.GET, "/api/v1/public-widgets"))

    @Bean
    fun openSecurity(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

/** Generates a document from a test DTO, since this change has no production request body. */
@SpringBootTest(
    classes = [OpenApiTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "springdoc.api-docs.enabled=true",
        "springdoc.api-docs.version=openapi_3_0",
        "springdoc.override-with-generic-response=false",
        "springdoc.default-produces-media-type=application/json",
    ],
)
class CardiganOpenApiCustomizerTest @Autowired constructor(
    @LocalServerPort private val port: Int,
) {

    private val document: Map<String, Any?> by lazy {
        val raw = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
            .get().uri("/v3/api-docs").exchange()
            .expectStatus().isOk
            .returnResult(String::class.java).responseBody!!
        JsonMapper.builder().build().readValue(raw, Map::class.java).asMap()
    }

    private val requestSchema by lazy { document.at("components", "schemas", "CreateWidgetRequest") }
    private val requestProperties by lazy { requestSchema.at("properties") }

    @Test
    fun `only @Required request properties are required`() {
        assertThat(requestSchema["required"]).isEqualTo(listOf("name"))
    }

    @Test
    fun `no request property is nullable`() {
        requestProperties.forEach { (name, property) ->
            assertThat(property.asMap()["nullable"], name).isNull()
        }
        assertThat(requestProperties.keys).isEqualTo(setOf("name", "note", "ownerId", "labelIds", "color", "position", "shape"))
    }

    @Test
    fun `patch request documents exactly its own properties, none required and none nullable`() {
        val schemas = document.at("components", "schemas")
        val schema = schemas.at("UpdateWidgetRequest")

        assertThat(schema.at("properties").keys).isEqualTo(setOf("name", "color"))
        assertThat(schema["required"]).isNull()
        schema.at("properties").forEach { (name, property) ->
            assertThat(property.asMap()["nullable"], name).isNull()
        }
        assertThat(schema.at("properties", "color")["enum"]).isEqualTo(listOf("RED", "GREEN"))
        assertThat(schemas.containsKey("PatchRequest")).isFalse()
        assertThat(document.at("paths", "/api/v1/widgets/{id}", "patch", "requestBody", "content", "application/json", "schema"))
            .isEqualTo(mapOf("\$ref" to "#/components/schemas/UpdateWidgetRequest"))
    }

    @Test
    fun `request fields keep their real formats`() {
        assertThat(requestProperties.at("ownerId")).isEqualTo(mapOf("type" to "string", "format" to "uuid"))
        assertThat(requestProperties.at("labelIds", "items")).isEqualTo(mapOf("type" to "string", "format" to "uuid"))
        assertThat(requestProperties.at("color")["enum"]).isEqualTo(listOf("RED", "GREEN"))
        assertThat(requestProperties.at("position")["type"]).isEqualTo("integer")
        assertThat(requestProperties.at("position")["minimum"]).isEqualTo(0)
        assertThat(requestProperties.at("position")["maximum"]).isEqualTo(10)
    }

    @Test
    fun `request enum marked as a reference points to its named schema`() {
        assertThat(requestProperties.at("shape")).isEqualTo(mapOf("\$ref" to "#/components/schemas/WidgetShapeDto"))
        assertThat(document.at("components", "schemas", "WidgetShapeDto"))
            .isEqualTo(mapOf("type" to "string", "enum" to listOf("ROUND", "SQUARE")))
        assertThat(document.at("components", "schemas").containsKey("WidgetColorDto")).isFalse()
    }

    @Test
    fun `response property left out when null is neither required nor nullable`() {
        val schema = document.at("components", "schemas", "WidgetDto")

        assertThat((schema["required"] as List<*>).contains("secret")).isFalse()
        assertThat(schema.at("properties", "secret")).isEqualTo(mapOf("type" to "string"))
        assertThat(schema.at("properties", "note")).isEqualTo(mapOf("type" to "string", "nullable" to true))
    }

    @Test
    fun `response properties are required unless nullable in Kotlin`() {
        val schema = document.at("components", "schemas", "WidgetDto")

        assertThat((schema["required"] as List<*>).toSet()).isEqualTo(setOf("id", "name", "createdAt"))
    }

    @Test
    fun `operation with a body documents transport errors, defaults and ApiErrors`() {
        val responses = document.at("paths", "/api/v1/widgets", "post", "responses")

        assertThat(responses.keys).isEqualTo(setOf("200", "400", "401", "404", "413", "415", "500"))
        assertThat(responses.at("400")["description"]).isEqualTo("Error codes: VALIDATION_FAILED, MALFORMED_REQUEST")
        assertThat(responses.at("404")["description"]).isEqualTo("Error codes: NOT_FOUND")
        assertThat(responses.at("413")["description"]).isEqualTo("Error codes: PAYLOAD_TOO_LARGE")
        assertThat(responses.at("415")["description"]).isEqualTo("Error codes: UNSUPPORTED_MEDIA_TYPE")
    }

    @Test
    fun `operation without a body documents no transport errors`() {
        val responses = document.at("paths", "/api/v1/widgets/{id}", "get", "responses")

        assertThat(responses.keys).isEqualTo(setOf("200", "401", "500"))
    }

    @Test
    fun `public route has no security requirement and no 401`() {
        val operation = document.at("paths", "/api/v1/public-widgets", "get")

        assertThat(operation["security"]).isNull()
        assertThat(operation.at("responses").containsKey("401")).isFalse()
    }

    @Test
    fun `protected route requires the bearer scheme`() {
        assertThat(document.at("paths", "/api/v1/widgets/{id}", "get")["security"])
            .isEqualTo(listOf(mapOf("bearerAuth" to emptyList<String>())))
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

    private fun Map<String, Any?>.at(vararg keys: String): Map<String, Any?> =
        keys.fold(this) { node, key -> node[key].asMap() }
}

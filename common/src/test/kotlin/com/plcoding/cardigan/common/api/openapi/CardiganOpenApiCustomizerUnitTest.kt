package com.plcoding.cardigan.common.api.openapi

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.annotation.JsonInclude
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.web.PublicRoute
import com.plcoding.cardigan.common.api.web.PublicRoutes
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.fake.objectProviderOf
import com.plcoding.cardigan.common.fake.publicRouteRegistryOf
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.tags.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.math.BigDecimal
import org.springframework.web.bind.annotation.RequestBody as RequestBodyParam

data class GadgetPartDto(
    @Required
    val name: String?,
    val note: String?,
    val children: List<GadgetPartDto>?,
)

open class GadgetTagDto(
    @Required
    val label: String?,
)

data class CreateGadgetRequest(
    val tags: List<String>?,
    val parts: List<GadgetPartDto>?,
)

data class GadgetDto(
    val id: String?,
    val owner: GadgetOwnerDto,
)

data class GadgetOwnerDto(
    val name: String,
    val nickname: String?,
)

class GadgetLegacyDto {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor()
}

data class GadgetEchoDto(
    @Required
    val text: String?,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val note: String?,
)

/** Registered by hand, so the Spring test's component scan never documents it. */
@Suppress("unused", "UNUSED_PARAMETER")
class GadgetEndpoints {

    fun createGadget(@RequestBodyParam request: CreateGadgetRequest): GadgetDto = error("not called")

    fun tagGadgets(@RequestBodyParam tags: List<GadgetTagDto>): GadgetLegacyDto = error("not called")

    fun echoGadget(@RequestBodyParam echo: GadgetEchoDto): GadgetEchoDto = error("not called")

    @ApiErrors(400, ErrorCode.NOT_FOUND)
    fun brokenGadget(): GadgetDto = error("not called")
}

class CardiganOpenApiCustomizerUnitTest {

    private val handlerMapping = RequestMappingHandlerMapping()
    private val config = RequestMappingInfo.BuilderConfiguration().apply { patternParser = handlerMapping.patternParser }
    private val customizer = CardiganOpenApiCustomizer(
        objectProviderOf(handlerMapping),
        publicRouteRegistryOf(PublicRoutes(PublicRoute(HttpMethod.GET, "/api/v1/public-widgets"))),
    )

    private fun register(handler: Any, name: String, method: RequestMethod, vararg paths: String) {
        val javaMethod = handler.javaClass.declaredMethods.single { it.name == name }
        handlerMapping.registerMapping(RequestMappingInfo.paths(*paths).methods(method).options(config).build(), handler, javaMethod)
    }

    private fun ref(name: String): Schema<Any> = Schema<Any>().`$ref`(name)

    private fun operation(requestSchema: String? = null): Operation = Operation()
        .responses(ApiResponses().addApiResponse("200", ApiResponse().description("OK")))
        .apply {
            if (requestSchema != null) {
                requestBody(RequestBody().content(Content().addMediaType("application/json", MediaType().schema(ref(requestSchema)))))
            }
        }

    private fun nullableString(): Schema<*> = StringSchema().nullable(true)

    private fun widgetDocument(): OpenAPI {
        val widgets = WidgetController()
        register(widgets, "createWidget", RequestMethod.POST, "/api/v1/widgets")
        register(widgets, "updateWidget", RequestMethod.PATCH, "/api/v1/widgets/{id}")
        register(widgets, "getWidget", RequestMethod.GET, "/api/v1/widgets/{id}")
        register(widgets, "listPublicWidgets", RequestMethod.GET, "/api/v1/public-widgets")

        return OpenAPI()
            .paths(
                Paths()
                    .addPathItem("/api/v1/widgets", PathItem().post(operation("CreateWidgetRequest")))
                    .addPathItem(
                        "/api/v1/widgets/{id}",
                        PathItem().patch(operation("UpdateWidgetRequest")).get(operation()),
                    )
                    .addPathItem("/api/v1/public-widgets", PathItem().get(operation())),
            )
            .components(
                Components()
                    .addSchemas(
                        "CreateWidgetRequest",
                        ObjectSchema()
                            .addProperty("name", nullableString())
                            .addProperty("note", nullableString())
                            .addProperty("ownerId", nullableString())
                            .addProperty("labelIds", ArraySchema().items(StringSchema()).nullable(true))
                            .addProperty("color", nullableString())
                            .addProperty("position", IntegerSchema().nullable(true))
                            .addProperty("shape", Schema<Any>().allOf(listOf(ref("WidgetShapeDto"))).nullable(true))
                            .addProperty("unmapped", nullableString())
                            .required(listOf("name", "note")),
                    )
                    .addSchemas(
                        "UpdateWidgetRequest",
                        ObjectSchema().addProperty("name", nullableString()).addProperty("color", nullableString()),
                    )
                    .addSchemas(
                        "WidgetDto",
                        ObjectSchema()
                            .addProperty("id", StringSchema())
                            .addProperty("name", StringSchema())
                            .addProperty("note", nullableString())
                            .addProperty("createdAt", StringSchema())
                            .addProperty("secret", nullableString()),
                    ),
            )
    }

    private fun gadgetDocument(): OpenAPI {
        val gadgets = GadgetEndpoints()
        register(gadgets, "createGadget", RequestMethod.POST, "/api/v1/gadgets")
        register(gadgets, "tagGadgets", RequestMethod.POST, "/api/v1/gadgets/tags")
        register(gadgets, "echoGadget", RequestMethod.POST, "/api/v1/gadgets/echo")

        return OpenAPI()
            .paths(
                Paths()
                    .addPathItem("/api/v1/gadgets", PathItem().post(operation("CreateGadgetRequest")))
                    .addPathItem("/api/v1/gadgets/tags", PathItem().post(operation("GadgetTagDto")))
                    .addPathItem("/api/v1/gadgets/echo", PathItem().post(operation("GadgetEchoDto"))),
            )
            .components(
                Components()
                    .addSchemas(
                        "CreateGadgetRequest",
                        ObjectSchema()
                            .addProperty("tags", ArraySchema().items(StringSchema()).nullable(true))
                            .addProperty("parts", ArraySchema().items(ref("GadgetPartDto")).nullable(true)),
                    )
                    .addSchemas(
                        "GadgetPartDto",
                        ObjectSchema()
                            .addProperty("name", nullableString())
                            .addProperty("note", nullableString())
                            .addProperty("children", ArraySchema().items(ref("GadgetPartDto")).nullable(true)),
                    )
                    .addSchemas("GadgetTagDto", ObjectSchema())
                    .addSchemas(
                        "GadgetDto",
                        ObjectSchema().addProperty("id", nullableString()).addProperty("owner", ref("GadgetOwnerDto")),
                    )
                    .addSchemas(
                        "GadgetOwnerDto",
                        ObjectSchema().addProperty("name", StringSchema()).addProperty("nickname", nullableString()),
                    )
                    .addSchemas("GadgetLegacyDto", ObjectSchema().addProperty("value", StringSchema()).required(listOf("value")))
                    .addSchemas(
                        "GadgetEchoDto",
                        ObjectSchema().addProperty("text", nullableString()).addProperty("note", nullableString()),
                    ),
            )
    }

    private fun customized(document: OpenAPI): OpenAPI = document.also(customizer::customise)

    private fun OpenAPI.schema(name: String): Schema<*> = components.schemas.getValue(name)

    private fun OpenAPI.operation(path: String, method: PathItem.HttpMethod): Operation =
        paths.getValue(path).readOperationsMap().getValue(method)

    private fun Operation.errorDescriptions(): Map<String, String> =
        responses.filterKeys { it != "200" }.mapValues { it.value.description }

    @Test
    fun `only @Required request properties are required`() {
        val document = customized(widgetDocument())

        assertThat(document.schema("CreateWidgetRequest").required).isEqualTo(listOf("name"))
        assertThat(document.schema("UpdateWidgetRequest").required).isNull()
    }

    @Test
    fun `no request property is nullable, also one without a field`() {
        val properties = customized(widgetDocument()).schema("CreateWidgetRequest").properties

        properties.forEach { (name, property) -> assertThat(property.nullable, name).isNull() }
        assertThat(properties.getValue("unmapped").format).isNull()
    }

    @Test
    fun `request fields keep their real formats`() {
        val properties = customized(widgetDocument()).schema("CreateWidgetRequest").properties

        assertThat(properties.getValue("ownerId").format).isEqualTo("uuid")
        assertThat(properties.getValue("name").format).isNull()
        assertThat(properties.getValue("labelIds").items.format).isEqualTo("uuid")
        assertThat(properties.getValue("color").enum).isEqualTo(listOf("RED", "GREEN"))
        assertThat(properties.getValue("name").enum).isNull()
        assertThat(properties.getValue("position").minimum).isEqualTo(BigDecimal.ZERO)
        assertThat(properties.getValue("position").maximum).isEqualTo(BigDecimal.TEN)
        assertThat(properties.getValue("name").minimum).isNull()
    }

    @Test
    fun `list items without @ValidUuid get no format`() {
        val properties = customized(gadgetDocument()).schema("CreateGadgetRequest").properties

        assertThat(properties.getValue("tags").items.format).isNull()
    }

    @Test
    fun `request enum wrapped in allOf becomes a plain reference`() {
        val shape = customized(widgetDocument()).schema("CreateWidgetRequest").properties.getValue("shape")

        assertThat(shape.`$ref`).isEqualTo("#/components/schemas/WidgetShapeDto")
        assertThat(shape.allOf).isNull()
        assertThat(shape.nullable).isNull()
    }

    @Test
    fun `request classes nested in lists, also recursive ones, are request schemas`() {
        val document = customized(gadgetDocument())

        assertThat(document.schema("GadgetPartDto").required).isEqualTo(listOf("name"))
        assertThat(document.schema("GadgetPartDto").properties.getValue("note").nullable).isNull()
        assertThat(document.schema("GadgetTagDto").required).isEqualTo(listOf("label"))
    }

    @Test
    fun `response properties are required unless nullable in Kotlin`() {
        val document = customized(widgetDocument())

        assertThat(document.schema("WidgetDto").required.toSet()).isEqualTo(setOf("id", "name", "createdAt"))
        assertThat(document.schema("WidgetDto").properties.getValue("note").nullable).isTrue()
    }

    @Test
    fun `response property left out when null is not nullable`() {
        val secret = customized(widgetDocument()).schema("WidgetDto").properties.getValue("secret")

        assertThat(secret.nullable).isNull()
    }

    @Test
    fun `response classes nested through fields are response schemas`() {
        val document = customized(gadgetDocument())

        assertThat(document.schema("GadgetDto").required).isEqualTo(listOf("owner"))
        assertThat(document.schema("GadgetOwnerDto").required).isEqualTo(listOf("name"))
        assertThat(document.schema("GadgetOwnerDto").properties.getValue("nickname").nullable).isTrue()
    }

    @Test
    fun `class used as request and response body is documented as a request`() {
        val schema = customized(gadgetDocument()).schema("GadgetEchoDto")

        assertThat(schema.required).isEqualTo(listOf("text"))
        assertThat(schema.properties.getValue("note").nullable).isNull()
    }

    @Test
    fun `response class without a primary constructor is left alone`() {
        assertThat(customized(gadgetDocument()).schema("GadgetLegacyDto").required).isEqualTo(listOf("value"))
    }

    @Test
    fun `operation with a body documents transport errors, defaults and ApiErrors`() {
        val operation = customized(widgetDocument()).operation("/api/v1/widgets", PathItem.HttpMethod.POST)

        assertThat(operation.responses.keys.toList()).containsExactly("200", "400", "401", "404", "413", "415", "500")
        assertThat(operation.errorDescriptions()).isEqualTo(
            mapOf(
                "400" to "Error codes: VALIDATION_FAILED, MALFORMED_REQUEST",
                "401" to "Error codes: UNAUTHENTICATED",
                "404" to "Error codes: NOT_FOUND",
                "413" to "Error codes: PAYLOAD_TOO_LARGE",
                "415" to "Error codes: UNSUPPORTED_MEDIA_TYPE",
                "500" to "Error codes: INTERNAL_ERROR",
            ),
        )
    }

    @Test
    fun `error responses are problem details`() {
        val response = customized(widgetDocument()).operation("/api/v1/widgets", PathItem.HttpMethod.POST).responses.getValue("400")

        assertThat(response.content.keys.toList()).containsExactly("application/problem+json")
        assertThat(response.content.getValue("application/problem+json").schema.`$ref`).isEqualTo("#/components/schemas/Problem")
    }

    @Test
    fun `operation without a body documents no transport errors`() {
        val operation = customized(widgetDocument()).operation("/api/v1/widgets/{id}", PathItem.HttpMethod.GET)

        assertThat(operation.errorDescriptions().keys).isEqualTo(setOf("401", "500"))
    }

    @Test
    fun `protected route requires the bearer scheme`() {
        val operation = customized(widgetDocument()).operation("/api/v1/widgets/{id}", PathItem.HttpMethod.GET)

        assertThat(operation.security).isEqualTo(listOf(SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH)))
    }

    @Test
    fun `public route has no security requirement and no 401`() {
        val operation = customized(widgetDocument()).operation("/api/v1/public-widgets", PathItem.HttpMethod.GET)

        assertThat(operation.security).isNull()
        assertThat(operation.errorDescriptions().keys).isEqualTo(setOf("500"))
    }

    @Test
    fun `public route is public only for its own method`() {
        val document = OpenAPI().components(Components()).paths(Paths().addPathItem("/api/v1/public-widgets", PathItem().post(operation())))

        val operation = customized(document).operation("/api/v1/public-widgets", PathItem.HttpMethod.POST)

        assertThat(operation.errorDescriptions().keys).isEqualTo(setOf("401", "500"))
    }

    @Test
    fun `only the API base path and paths below it are protected`() {
        val document = customized(
            OpenAPI().components(Components()).paths(
                Paths()
                    .addPathItem("/api/v1", PathItem().get(operation()))
                    .addPathItem("/api/v10/things", PathItem().get(operation()))
                    .addPathItem("/health", PathItem().get(operation())),
            ),
        )

        assertThat(document.operation("/api/v1", PathItem.HttpMethod.GET).security).isEqualTo(
            listOf(SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH)),
        )
        assertThat(document.operation("/api/v10/things", PathItem.HttpMethod.GET).security).isNull()
        assertThat(document.operation("/health", PathItem.HttpMethod.GET).errorDescriptions().keys).isEqualTo(setOf("500"))
    }

    @Test
    fun `ApiErrors with a code of another status fails`() {
        register(GadgetEndpoints(), "brokenGadget", RequestMethod.GET, "/api/v1/gadgets/broken")
        val document = OpenAPI().components(Components()).paths(Paths().addPathItem("/api/v1/gadgets/broken", PathItem().get(operation())))

        assertFailure { customizer.customise(document) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("NOT_FOUND has status 404, not 400")
    }

    @Test
    fun `problem and field error schemas are added`() {
        val document = customized(widgetDocument())

        val problem = document.schema("Problem")
        assertThat(problem.properties.keys.toList()).containsExactly("type", "title", "status", "detail", "code", "fieldErrors")
        assertThat(problem.required.toSet()).isEqualTo(setOf("type", "title", "status", "detail", "code", "fieldErrors"))
        assertThat(problem.properties.getValue("status").format).isEqualTo("int32")
        assertThat(problem.properties.getValue("fieldErrors").items.`$ref`).isEqualTo("#/components/schemas/FieldError")

        val fieldError = document.schema("FieldError")
        assertThat(fieldError.properties.keys.toList()).containsExactly("field", "code", "min", "max")
        assertThat(fieldError.required.toSet()).isEqualTo(setOf("field", "code"))
        assertThat(fieldError.properties.getValue("min").format).isEqualTo("int64")
        assertThat(fieldError.properties.getValue("max").format).isEqualTo("int64")
    }

    @Test
    fun `tags are sorted by name`() {
        val document = OpenAPI().components(Components()).tags(listOf(Tag().name("Widgets"), Tag().name("Auth"), Tag().name("Boards")))

        assertThat(customized(document).tags.map { it.name }).containsExactly("Auth", "Boards", "Widgets")
    }

    @Test
    fun `document without paths, schemas or tags gets only the problem schemas`() {
        val document = customized(OpenAPI().components(Components()))

        assertThat(document.paths).isNull()
        assertThat(document.tags).isNull()
        assertThat(document.components.schemas.keys).isEqualTo(setOf("Problem", "FieldError"))
        assertThat(document.components.schemas.containsKey("WidgetDto")).isFalse()
    }
}

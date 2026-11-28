package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the auth part of the committed snapshot, the contract clients generate from.
 * `OpenApiSnapshotTest` makes sure the snapshot equals the generated document.
 */
class AuthOpenApiSnapshotTest {
    private val document: Map<String, Any?> =
        JsonMapper
            .builder()
            .build()
            .readValue(Files.readString(Path.of("src/test/resources/openapi/openapi.json")), Map::class.java)
            .asStringMap()

    private val schemas = document.map("components").map("schemas")

    private fun operation(path: String): Map<String, Any?> {
        val item = document.map("paths").map("/api/v1/auth/$path")
        assertThat(item.keys, "methods of $path").isEqualTo(setOf("post"))
        return item.map("post")
    }

    private fun Map<String, Any?>.responseDescriptions(): Map<String, Any?> =
        map("responses").mapValues { (_, response) -> response.asStringMap()["description"] }

    private fun Map<String, Any?>.schemaOf(status: String): Any? =
        map("responses").map(status).map("content").map("application/json")["schema"]

    private val bodyErrors =
        mapOf(
            "400" to "Error codes: VALIDATION_FAILED, MALFORMED_REQUEST",
            "413" to "Error codes: PAYLOAD_TOO_LARGE",
            "415" to "Error codes: UNSUPPORTED_MEDIA_TYPE",
            "500" to "Error codes: INTERNAL_ERROR",
        )

    @Test
    fun `the four auth operations have the tag Auth and their operation IDs`() {
        listOf("register", "login", "refresh", "logout").forEach { name ->
            val operation = operation(name)

            assertThat(operation["operationId"]).isEqualTo(name)
            assertThat(operation["tags"]).isEqualTo(listOf("Auth"))
        }
        assertThat(
            document
                .map("paths")
                .keys
                .filter { it.startsWith("/api/v1/auth") }
                .toSet(),
        ).isEqualTo(
            setOf("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"),
        )
    }

    @Test
    fun `auth operations have no bearer requirement and document no UNAUTHENTICATED`() {
        listOf("register", "login", "refresh", "logout").forEach { name ->
            val operation = operation(name)

            assertThat(operation["security"], "security of $name").isNull()
            operation.responseDescriptions().values.forEach { description ->
                assertThat(description as String).doesNotContain("UNAUTHENTICATED")
            }
        }
    }

    @Test
    fun `register documents 201 and its errors`() {
        val operation = operation("register")

        assertThat(operation.responseDescriptions()).isEqualTo(
            mapOf("201" to "Created", "409" to "Error codes: CONFLICT", "429" to "Error codes: RATE_LIMITED") +
                bodyErrors,
        )
        assertThat(operation.schemaOf("201")).isEqualTo(ref("AuthSessionDto"))
        assertThat(
            operation.map("requestBody").map("content").map("application/json")["schema"],
        ).isEqualTo(ref("RegisterRequest"))
    }

    @Test
    fun `login documents 200 and its errors`() {
        val operation = operation("login")

        assertThat(operation.responseDescriptions()).isEqualTo(
            mapOf("200" to "OK", "401" to "Error codes: INVALID_CREDENTIALS", "429" to "Error codes: RATE_LIMITED") +
                bodyErrors,
        )
        assertThat(operation.schemaOf("200")).isEqualTo(ref("AuthSessionDto"))
        assertThat(
            operation.map("requestBody").map("content").map("application/json")["schema"],
        ).isEqualTo(ref("LoginRequest"))
    }

    @Test
    fun `refresh documents 200 and its errors, and never 429`() {
        val operation = operation("refresh")

        assertThat(operation.responseDescriptions())
            .isEqualTo(mapOf("200" to "OK", "401" to "Error codes: INVALID_REFRESH_TOKEN") + bodyErrors)
        assertThat(operation.schemaOf("200")).isEqualTo(ref("AuthTokensDto"))
        assertThat(
            operation.map("requestBody").map("content").map("application/json")["schema"],
        ).isEqualTo(ref("RefreshRequest"))
    }

    @Test
    fun `logout documents 204 without a body, and never 401 or 429`() {
        val operation = operation("logout")

        assertThat(operation.responseDescriptions()).isEqualTo(mapOf("204" to "No Content") + bodyErrors)
        assertThat(operation.map("responses").map("204")["content"]).isNull()
        assertThat(
            operation.map("requestBody").map("content").map("application/json")["schema"],
        ).isEqualTo(ref("LogoutRequest"))
    }

    @Test
    fun `refresh cookie is no parameter, and refresh and logout name it`() {
        listOf("register", "login", "refresh", "logout").forEach { name ->
            assertThat(operation(name)["parameters"], "parameters of $name").isNull()
        }
        assertThat(operation("refresh")["description"] as String).contains("cardigan_refresh_token")
        assertThat(operation("logout")["description"] as String).contains("cardigan_refresh_token")
    }

    @Test
    fun `the seven auth schemas exist next to the existing ones`() {
        assertThat(schemas.keys.containsAll(AUTH_SCHEMAS + "CurrentUserDto")).isEqualTo(true)
    }

    @Test
    fun `refreshToken in responses is neither required nor nullable`() {
        val session = schemas.map("AuthSessionDto")
        val tokens = schemas.map("AuthTokensDto")

        assertThat(session["required"]).isEqualTo(listOf("accessToken", "accessTokenExpiresAt", "user"))
        assertThat(tokens["required"]).isEqualTo(listOf("accessToken", "accessTokenExpiresAt"))
        assertThat(
            session.map("properties").keys,
        ).isEqualTo(setOf("accessToken", "accessTokenExpiresAt", "refreshToken", "user"))
        assertThat(
            tokens.map("properties").keys,
        ).isEqualTo(setOf("accessToken", "accessTokenExpiresAt", "refreshToken"))
        listOf(session, tokens).forEach { schema ->
            assertThat(schema.map("properties").map("refreshToken")).isEqualTo(mapOf("type" to "string"))
            assertThat(schema.map("properties").map("accessTokenExpiresAt"))
                .isEqualTo(mapOf("type" to "string", "format" to "date-time"))
        }
        assertThat(session.map("properties")["user"]).isEqualTo(ref("CurrentUserDto"))
    }

    @Test
    fun `request schemas require what the endpoints need and nothing is nullable`() {
        val credentials = listOf("email", "password", "refreshTokenTransport")

        assertThat(schemas.map("RegisterRequest")["required"]).isEqualTo(credentials)
        assertThat(schemas.map("LoginRequest")["required"]).isEqualTo(credentials)
        assertThat(schemas.map("RefreshRequest")["required"]).isEqualTo(listOf("refreshTokenTransport"))
        assertThat(schemas.map("LogoutRequest")["required"]).isEqualTo(listOf("refreshTokenTransport"))
        assertThat(
            schemas.map("RefreshRequest").map("properties").keys,
        ).isEqualTo(setOf("refreshToken", "refreshTokenTransport"))
        assertThat(
            schemas.map("LogoutRequest").map("properties").keys,
        ).isEqualTo(setOf("refreshToken", "refreshTokenTransport"))
        listOf("RegisterRequest", "LoginRequest", "RefreshRequest", "LogoutRequest").forEach { name ->
            val properties = schemas.map(name).map("properties")
            assertThat(properties["refreshTokenTransport"], name).isEqualTo(ref("RefreshTokenTransportDto"))
            properties.values.forEach { assertThat(it.asStringMap()["nullable"], name).isNull() }
        }
    }

    @Test
    fun `transport enum has its two stable values`() {
        assertThat(schemas.map("RefreshTokenTransportDto")).isEqualTo(
            mapOf(
                "type" to "string",
                "enum" to listOf("COOKIE", "BODY"),
            ),
        )
    }

    private fun ref(schema: String) = mapOf("\$ref" to "#/components/schemas/$schema")

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key].asStringMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?> = this as Map<String, Any?>

    private companion object {
        val AUTH_SCHEMAS =
            setOf(
                "RegisterRequest",
                "LoginRequest",
                "RefreshRequest",
                "LogoutRequest",
                "AuthSessionDto",
                "AuthTokensDto",
                "RefreshTokenTransportDto",
            )
    }
}

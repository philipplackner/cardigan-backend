package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LabelE2eTest : E2eTest() {

    private val jsonMapper = JsonMapper.builder().build()

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        token = signIn().token
    }

    private fun body(vararg fields: Pair<String, Any?>): String = jsonMapper.writeValueAsString(mapOf(*fields))

    private fun post(json: String, token: String? = this.token) = call(HttpMethod.POST, LABELS, token, json = json)

    private fun patch(id: Any?, json: String, token: String? = this.token) =
        call(HttpMethod.PATCH, "$LABELS/$id", token, json = json)

    private fun delete(id: Any?, token: String? = this.token) = call(HttpMethod.DELETE, "$LABELS/$id", token)

    private fun create(name: String, color: String = "RED", token: String = this.token): Map<String, Any?> {
        val result = post(body("name" to name, "color" to color), token)
        assertThat(result.status, "create $name").isEqualTo(201)
        return result.json()
    }

    @Suppress("UNCHECKED_CAST")
    private fun list(token: String = this.token): List<Map<String, Any?>> {
        val result = call(HttpMethod.GET, LABELS, token)
        assertThat(result.status, "list").isEqualTo(200)
        val json = result.json()
        assertThat(json.keys).isEqualTo(setOf("labels"))
        return json["labels"] as List<Map<String, Any?>>
    }

    private fun names(token: String = this.token): List<Any?> = list(token).map { it["name"] }

    private fun oneMinuteLater(): String {
        clock.advanceBy(Duration.ofMinutes(1))
        return "2026-11-15T09:16:30.123Z"
    }

    private fun HttpResult.assertFieldErrors(code: ErrorCode, vararg fieldErrors: Map<String, Any?>) {
        assertThat(assertProblem(code).fieldErrors).containsExactly(*fieldErrors)
    }

    // Representation

    @Test
    fun `label has exactly id, name, color, createdAt and updatedAt`() {
        val label = create("Bug", "RED")

        assertThat(label.keys).isEqualTo(setOf("id", "name", "color", "createdAt", "updatedAt"))
        assertThat(label["id"] as String).matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
        assertThat(label - "id").isEqualTo(
            mapOf("name" to "Bug", "color" to "RED", "createdAt" to START_TEXT, "updatedAt" to START_TEXT),
        )
        assertThat(list()).containsExactly(label)
    }

    // Colors

    @Test
    fun `palette name is accepted`() {
        assertThat(create("Bug", "TEAL")["color"]).isEqualTo("TEAL")
    }

    @Test
    fun `every palette name is accepted and returned unchanged`() {
        val colors = listOf(
            "AMBER", "BLUE", "BROWN", "CYAN", "EMERALD", "FUCHSIA", "GREEN", "INDIGO", "LIME", "OLIVE",
            "ORANGE", "PINK", "PURPLE", "RED", "ROSE", "SKY", "SLATE", "TEAL", "VIOLET", "YELLOW",
        )

        colors.forEach { assertThat(create("label $it", it)["color"]).isEqualTo(it) }
    }

    @Test
    fun `mixed-case color is INVALID_VALUE`() {
        post(body("name" to "Bug", "color" to "Red"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "color", "code" to "INVALID_VALUE"))
        assertThat(list()).isEmpty()
    }

    @Test
    fun `lower-case color is INVALID_VALUE`() {
        post(body("name" to "Bug", "color" to "red"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "color", "code" to "INVALID_VALUE"))
    }

    @Test
    fun `color of another type is MALFORMED_REQUEST`() {
        post("""{"name":"Bug","color":14}""").assertProblem(ErrorCode.MALFORMED_REQUEST)
        assertThat(list()).isEmpty()
    }

    // Create

    @Test
    fun `create trims the name and sets both timestamps to the creation time`() {
        val result = post("""{ "name": "  Bug ", "color": "RED" }""")

        assertThat(result.status).isEqualTo(201)
        val label = result.json()
        assertThat(label["name"]).isEqualTo("Bug")
        assertThat(label["color"]).isEqualTo("RED")
        assertThat(label["createdAt"]).isEqualTo(START_TEXT)
        assertThat(label["updatedAt"]).isEqualTo(START_TEXT)
    }

    @Test
    fun `two labels get different ids`() {
        assertThat(create("Bug")["id"]).isNotEqualTo(create("Docs")["id"])
    }

    @Test
    fun `empty body object reports both required fields and stores nothing`() {
        post("{}").assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            mapOf("field" to "color", "code" to "REQUIRED"),
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
        assertThat(list()).isEmpty()
    }

    @Test
    fun `name of 31 code points and unknown color are reported together`() {
        post(body("name" to "a".repeat(31), "color" to "MAGENTA")).assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            mapOf("field" to "color", "code" to "INVALID_VALUE"),
            mapOf("field" to "name", "code" to "TOO_LONG", "max" to 30),
        )
    }

    @Test
    fun `name of 30 supplementary code points is accepted`() {
        val name = "😀".repeat(30)

        assertThat(create(name)["name"]).isEqualTo(name)
    }

    @Test
    fun `name whose key triples in length is accepted`() {
        val name = "ﬃ".repeat(30)

        assertThat(create(name)["name"]).isEqualTo(name)
    }

    @Test
    fun `line break in the name is INVALID_CHARACTER`() {
        post(body("name" to "a\nb", "color" to "RED"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "name", "code" to "INVALID_CHARACTER"))
    }

    @Test
    fun `no token is 401 on every label endpoint`() {
        val id = create("Bug")["id"]

        post(body("name" to "Docs", "color" to "RED"), token = null).assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.GET, LABELS).assertProblem(ErrorCode.UNAUTHENTICATED)
        patch(id, body("color" to "BLUE"), token = null).assertProblem(ErrorCode.UNAUTHENTICATED)
        delete(id, token = null).assertProblem(ErrorCode.UNAUTHENTICATED)

        assertThat(list()).hasSize(1)
        assertThat(list().single()["color"]).isEqualTo("RED")
    }

    // List

    @Test
    fun `list is sorted by the case-folded name`() {
        listOf("frontend", "Bug", "api").forEach { create(it) }

        assertThat(names()).containsExactly("api", "Bug", "frontend")
    }

    @Test
    fun `list orders non-ASCII and supplementary characters by code point`() {
        listOf("Zebra", "Äpfel", "apple", "😀", "ｚ").forEach { create(it) }

        assertThat(names()).containsExactly("apple", "Zebra", "Äpfel", "ｚ", "😀")
    }

    @Test
    fun `list without labels is an empty array`() {
        val result = call(HttpMethod.GET, LABELS, token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(mapOf("labels" to emptyList<Any>()))
    }

    @Test
    fun `user with 600 labels creates another one and lists all 601`() {
        val names = (1..600).map { "label-%03d".format(it) }
        names.forEach { create(it) }

        create("label-601")

        assertThat(names()).isEqualTo(names + "label-601")
    }

    // Update

    @Test
    fun `recolor only keeps the name`() {
        val label = create("Bug", "RED")

        val result = patch(label["id"], body("color" to "BLUE"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label + mapOf("color" to "BLUE"))
        assertThat(list()).containsExactly(result.json())
    }

    @Test
    fun `rename only keeps the color`() {
        val label = create("Bug", "RED")

        val result = patch(label["id"], body("name" to "  Defect "))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label + mapOf("name" to "Defect"))
    }

    @Test
    fun `rename and recolor a minute later moves only updatedAt`() {
        val label = create("Bug", "RED")
        val later = oneMinuteLater()

        val result = patch(label["id"], body("name" to "Defect", "color" to "ROSE"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(
            mapOf(
                "id" to label["id"],
                "name" to "Defect",
                "color" to "ROSE",
                "createdAt" to START_TEXT,
                "updatedAt" to later,
            ),
        )
        assertThat(list()).containsExactly(result.json())
    }

    @Test
    fun `null name is REQUIRED and changes nothing`() {
        val label = create("Bug", "RED")
        oneMinuteLater()

        patch(label["id"], """{"name":null}""")
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `null color is REQUIRED and changes nothing`() {
        val label = create("Bug", "RED")
        oneMinuteLater()

        patch(label["id"], """{"color":null}""")
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "color", "code" to "REQUIRED"))
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `null name next to a valid color changes neither`() {
        val label = create("Bug", "RED")

        patch(label["id"], """{"name":null,"color":"BLUE"}""")
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `unknown color on update is INVALID_VALUE and changes nothing`() {
        val label = create("Bug", "RED")

        patch(label["id"], body("color" to "Red"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "color", "code" to "INVALID_VALUE"))
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `whitespace-only name is REQUIRED`() {
        val label = create("Bug", "RED")

        patch(label["id"], body("name" to "   "))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `valid color with an invalid name changes neither`() {
        val label = create("Bug", "RED")

        patch(label["id"], body("name" to "a".repeat(31), "color" to "BLUE")).assertFieldErrors(
            ErrorCode.VALIDATION_FAILED,
            mapOf("field" to "name", "code" to "TOO_LONG", "max" to 30),
        )
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `color of another type on update is MALFORMED_REQUEST`() {
        val label = create("Bug", "RED")

        patch(label["id"], """{"color":14}""").assertProblem(ErrorCode.MALFORMED_REQUEST)
        assertThat(list()).containsExactly(label)
    }

    // Unchanged values

    @Test
    fun `same color again leaves updatedAt alone`() {
        val label = create("Bug", "RED")
        oneMinuteLater()

        val result = patch(label["id"], body("color" to "RED"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label)
    }

    @Test
    fun `same name with surrounding spaces leaves updatedAt alone`() {
        val label = create("Bug", "RED")
        oneMinuteLater()

        val result = patch(label["id"], body("name" to " Bug "))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label)
    }

    @Test
    fun `empty patch returns the unchanged label`() {
        val label = create("Bug", "RED")
        oneMinuteLater()

        val result = patch(label["id"], "{}")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label)
        assertThat(list()).containsExactly(label)
    }

    @Test
    fun `one differing field of two moves updatedAt`() {
        val label = create("Bug", "RED")
        val later = oneMinuteLater()

        val result = patch(label["id"], body("name" to "Bug", "color" to "BLUE"))

        assertThat(result.json()).isEqualTo(label + mapOf("color" to "BLUE", "updatedAt" to later))
    }

    @Test
    fun `case-only rename is a change`() {
        val label = create("bug", "RED")
        val later = oneMinuteLater()

        val result = patch(label["id"], body("name" to "Bug"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(label + mapOf("name" to "Bug", "updatedAt" to later))
    }

    // Name clashes

    @Test
    fun `create with a used name is a conflict and stores nothing`() {
        val bug = create("Bug")

        post(body("name" to "bug", "color" to "BLUE"))
            .assertFieldErrors(ErrorCode.CONFLICT, mapOf("field" to "name", "code" to "ALREADY_USED"))
        assertThat(list()).containsExactly(bug)
    }

    @Test
    fun `names clash by folding beyond lowercase`() {
        create("Straße")

        post(body("name" to "STRASSE", "color" to "RED"))
            .assertFieldErrors(ErrorCode.CONFLICT, mapOf("field" to "name", "code" to "ALREADY_USED"))
    }

    @Test
    fun `rename to another label's name is a conflict`() {
        val bug = create("Bug")
        val docs = create("Docs")
        oneMinuteLater()

        patch(docs["id"], body("name" to "BUG"))
            .assertFieldErrors(ErrorCode.CONFLICT, mapOf("field" to "name", "code" to "ALREADY_USED"))
        assertThat(list()).containsExactly(bug, docs)
    }

    @Test
    fun `rename to a used name with a new color changes neither`() {
        val bug = create("Bug")
        val docs = create("Docs", "RED")

        patch(docs["id"], body("name" to "bug", "color" to "BLUE"))
            .assertFieldErrors(ErrorCode.CONFLICT, mapOf("field" to "name", "code" to "ALREADY_USED"))
        assertThat(list()).containsExactly(bug, docs)
    }

    @Test
    fun `used name with an unknown color reports only the color`() {
        create("Bug")

        post(body("name" to "bug", "color" to "MAGENTA"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, mapOf("field" to "color", "code" to "INVALID_VALUE"))
    }

    @Test
    fun `same name for another user is created`() {
        create("Bug")
        val other = signIn("bob@example.com").token

        assertThat(create("Bug", token = other)["name"]).isEqualTo("Bug")
    }

    @Test
    fun `name is free again after delete`() {
        val bug = create("Bug")
        assertThat(delete(bug["id"]).status).isEqualTo(204)

        assertThat(create("bug")["name"]).isEqualTo("bug")
    }

    @Test
    fun `two simultaneous creates of one folded name give one label`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(20) { round ->
                val user = signIn("racer$round@example.com").token
                val start = CyclicBarrier(2)
                val results = executor.invokeAll(
                    listOf("Bug", "bug").map { name ->
                        Callable {
                            start.await(10, TimeUnit.SECONDS)
                            raw(
                                "POST",
                                LABELS,
                                user,
                                body("name" to name, "color" to "RED").toByteArray(),
                                headers = mapOf(HttpHeaders.CONTENT_TYPE to MediaType.APPLICATION_JSON_VALUE),
                            )
                        }
                    },
                ).map { it.get(30, TimeUnit.SECONDS) }

                assertThat(results.map { it.status }, "round $round").containsExactlyInAnyOrder(201, 409)
                results.single { it.status == 409 }
                    .assertFieldErrors(ErrorCode.CONFLICT, mapOf("field" to "name", "code" to "ALREADY_USED"))
                val created = results.single { it.status == 201 }.json()
                assertThat(created["name"]).isIn("Bug", "bug")
                assertThat(list(user), "round $round").containsExactly(created)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    // Delete

    @Test
    fun `delete removes the label and answers 204 with no body`() {
        val bug = create("Bug")
        val docs = create("Docs")

        val result = delete(bug["id"])

        assertThat(result.status).isEqualTo(204)
        assertThat(result.body).isEmpty()
        assertThat(list()).containsExactly(docs)
    }

    @Test
    fun `second delete is NOT_FOUND`() {
        val bug = create("Bug")
        delete(bug["id"])

        delete(bug["id"]).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `update of a deleted label is NOT_FOUND and does not bring it back`() {
        val bug = create("Bug")
        delete(bug["id"])

        patch(bug["id"], body("name" to "Defect")).assertProblem(ErrorCode.NOT_FOUND)
        assertThat(list()).isEmpty()
    }

    // Isolation

    @Test
    fun `list shows only own labels`() {
        create("Bug")
        val other = signIn("bob@example.com").token
        val docs = create("Docs", token = other)

        assertThat(list(other)).containsExactly(docs)
    }

    @Test
    fun `update of another user's label equals the response for an unknown id`() {
        val bug = create("Bug", "RED")
        val other = signIn("bob@example.com").token

        val foreign = patch(bug["id"], body("color" to "BLUE"), token = other)
        val unknown = patch(UUID.randomUUID(), body("color" to "BLUE"), token = other)

        foreign.assertProblem(ErrorCode.NOT_FOUND)
        assertThat(foreign.status).isEqualTo(unknown.status)
        assertThat(foreign.json()).isEqualTo(unknown.json())
        assertThat(list()).containsExactly(bug)
    }

    @Test
    fun `delete of another user's label is NOT_FOUND and keeps it`() {
        val bug = create("Bug")
        val other = signIn("bob@example.com").token

        delete(bug["id"], token = other).assertProblem(ErrorCode.NOT_FOUND)
        assertThat(list()).containsExactly(bug)
    }

    @Test
    fun `unknown label id is NOT_FOUND`() {
        patch(UUID.randomUUID(), body("color" to "BLUE")).assertProblem(ErrorCode.NOT_FOUND)
        delete(UUID.randomUUID()).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `malformed label id is NOT_FOUND`() {
        patch("abc", body("color" to "BLUE")).assertProblem(ErrorCode.NOT_FOUND)
        delete("abc").assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `blank label id is NOT_FOUND`() {
        patch(" ", body("color" to "BLUE")).assertProblem(ErrorCode.NOT_FOUND)
        delete(" ").assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `missing label with an invalid body is NOT_FOUND`() {
        patch(UUID.randomUUID(), body("name" to "a".repeat(31))).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `another user's label with an invalid body is NOT_FOUND`() {
        val bug = create("Bug")
        val other = signIn("bob@example.com").token

        patch(bug["id"], """{"name":null,"color":"Red"}""", token = other).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `uppercase label id is accepted and returned lowercase`() {
        val bug = create("Bug", "RED")
        val id = bug["id"] as String

        val result = patch(id.uppercase(), body("color" to "BLUE"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()["color"]).isEqualTo("BLUE")
        assertThat(result.json()["id"]).isEqualTo(id)
    }

    @Test
    fun `uppercase label id is accepted on delete`() {
        val id = create("Bug")["id"] as String

        assertThat(delete(id.uppercase()).status).isEqualTo(204)
        assertThat(list()).isEmpty()
    }

    private companion object {
        const val LABELS = "/api/v1/labels"
        const val START_TEXT = "2026-11-15T09:15:30.123Z"
    }
}

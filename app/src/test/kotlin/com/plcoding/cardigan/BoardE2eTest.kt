package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoardE2eTest : BoardApiE2eTest() {

    private lateinit var token: String

    private val lowercaseUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    @BeforeEach
    fun setUp() {
        token = signIn().token
    }

    private fun createRaw(json: String, headers: Map<String, String> = emptyMap()) =
        call(HttpMethod.POST, "/api/v1/boards", token, json = json, headers = headers)

    private fun keyError(keyJson: String): Json = createRaw("""{"name":"Roadmap","key":$keyJson}""").singleFieldError()

    private fun patchBoard(boardId: String, json: String, token: String = this.token) =
        patch("/api/v1/boards/$boardId", token, json)

    @Test
    fun `full board has exactly id, name, key, createdAt, updatedAt and columns`() {
        val created = createBoard(token, "Roadmap", "CRD")

        val board = getBoard(token, created.id)

        assertThat(board.keys).isEqualTo(setOf("id", "name", "key", "createdAt", "updatedAt", "columns"))
        assertThat(board.id).matches(lowercaseUuid)
        assertThat(board - "id" - "columns").isEqualTo(
            mapOf("name" to "Roadmap", "key" to "CRD", "createdAt" to "2026-11-15T09:15:30.123Z", "updatedAt" to "2026-11-15T09:15:30.123Z"),
        )
        assertThat(board).isEqualTo(created)
    }

    @Test
    fun `board whose columns were all deleted has an empty columns array`() {
        val board = createBoard(token)
        board.columns.forEach { delete("/api/v1/boards/${board.id}/columns/${it.id}", token) }

        assertThat(getBoard(token, board.id)["columns"]).isEqualTo(emptyList<Any>())
    }

    @Test
    fun `list item has exactly id, name, key, createdAt and updatedAt`() {
        val board = createBoard(token)

        assertThat(listBoards(token)).containsExactly(board - "columns")
    }

    @Test
    fun `create trims the name, uppercases the key and sets equal timestamps`() {
        val result = createRaw("""{"name":"  Roadmap ","key":"crd"}""")

        assertThat(result.status).isEqualTo(201)
        val board = result.json()
        assertThat(board.id).matches(lowercaseUuid)
        assertThat(board["name"]).isEqualTo("Roadmap")
        assertThat(board["key"]).isEqualTo("CRD")
        assertThat(board["createdAt"]).isEqualTo(start)
        assertThat(board["updatedAt"]).isEqualTo(start)
    }

    @Test
    fun `empty body object reports key and name as REQUIRED and stores nothing`() {
        val body = createRaw("{}").assertProblem(ErrorCode.VALIDATION_FAILED)

        assertThat(body.fieldErrors).containsExactly(
            mapOf("field" to "key", "code" to "REQUIRED"),
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
        assertThat(listBoards(token)).isEmpty()
    }

    @Test
    fun `line break in the name is INVALID_CHARACTER`() {
        assertThat(createRaw("""{"name":"a\nb","key":"CRD"}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "INVALID_CHARACTER"))
    }

    @Test
    fun `name of 100 code points is accepted and 101 is TOO_LONG with max 100`() {
        assertThat(createRaw("""{"name":"${"😀".repeat(100)}","key":"AA"}""").status).isEqualTo(201)
        assertThat(createRaw("""{"name":"${"a".repeat(101)}","key":"BB"}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "TOO_LONG", "max" to 100))
    }

    @Test
    fun `create without a token is UNAUTHENTICATED`() {
        call(HttpMethod.POST, "/api/v1/boards", json = """{"name":"Roadmap","key":"CRD"}""")
            .assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `user with 300 boards can create another one`() {
        val keys = ('A'..'Z').flatMap { first -> ('A'..'Z').map { second -> "$first$second" } }.take(300)
        keys.forEach { createBoard(token, key = it) }

        assertThat(createRaw("""{"name":"One more","key":"ZZZ"}""").status).isEqualTo(201)
        assertThat(listBoards(token).size).isEqualTo(301)
    }

    @Test
    fun `new board has To do, In progress and Done at 0, 1, 2 with the board's creation time`() {
        val board = createBoard(token)

        assertThat(board.columns.namesAndPositions()).containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
        assertThat(board.columns.map { it.id }.toSet().size).isEqualTo(3)
        board.columns.forEach { column ->
            assertThat(column.id).matches(lowercaseUuid)
            assertThat(column["createdAt"]).isEqualTo(board["createdAt"])
            assertThat(column["updatedAt"]).isEqualTo(board["createdAt"])
        }
    }

    @Test
    fun `request language is ignored for the default column names`() {
        val result = createRaw("""{"name":"Roadmap","key":"CRD"}""", headers = mapOf("Accept-Language" to "de"))

        assertThat(result.json().columns.map { it["name"] }).containsExactly("To do", "In progress", "Done")
    }

    @Test
    fun `used key is CONFLICT with ALREADY_USED and stores no board and no column`() {
        val first = createBoard(token, key = "CRD")

        val body = createRaw("""{"name":"Other","key":" crd"}""").assertProblem(ErrorCode.CONFLICT)

        assertThat(body.fieldErrors).containsExactly(mapOf("field" to "key", "code" to "ALREADY_USED"))
        assertThat(storedBoards()).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM board_service.columns", Int::class.java)).isEqualTo(3)
        assertThat(listBoards(token)).containsExactly(first - "columns")
    }

    @Test
    fun `field validation wins over a used key`() {
        createBoard(token, key = "CRD")

        assertThat(createRaw("""{"name":"","key":"CRD"}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "REQUIRED"))
    }

    @Test
    fun `surrounding whitespace in the key is removed`() {
        val result = createRaw("""{"name":"Roadmap","key":" crd "}""")

        assertThat(result.status).isEqualTo(201)
        assertThat(result.json()["key"]).isEqualTo("CRD")
    }

    @Test
    fun `whitespace-only key is REQUIRED`() {
        assertThat(keyError("\"   \"")).isEqualTo(mapOf("field" to "key", "code" to "REQUIRED"))
    }

    @Test
    fun `tab inside the key is INVALID_FORMAT`() {
        assertThat(keyError("\"C\\tD\"")).isEqualTo(mapOf("field" to "key", "code" to "INVALID_FORMAT"))
    }

    @Test
    fun `unpaired surrogate in the key is INVALID_FORMAT`() {
        assertThat(keyError("\"C\\ud800D\"")).isEqualTo(mapOf("field" to "key", "code" to "INVALID_FORMAT"))
    }

    @Test
    fun `single wrong character is TOO_SHORT with min 2`() {
        assertThat(keyError("\"1\"")).isEqualTo(mapOf("field" to "key", "code" to "TOO_SHORT", "min" to 2))
    }

    @Test
    fun `too long key with wrong characters is TOO_LONG with max 5`() {
        assertThat(keyError("\"ÄBCDEF\"")).isEqualTo(mapOf("field" to "key", "code" to "TOO_LONG", "max" to 5))
    }

    @Test
    fun `space inside the key is INVALID_FORMAT`() {
        assertThat(keyError("\"C D\"")).isEqualTo(mapOf("field" to "key", "code" to "INVALID_FORMAT"))
    }

    @Test
    fun `key of another type is MALFORMED_REQUEST`() {
        createRaw("""{"name":"Roadmap","key":12}""").assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `two simultaneous creates with one key give one 201 and one CONFLICT`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(5) { round ->
                val key = "CR" + ('A' + round)
                val barrier = CyclicBarrier(2)
                val results = listOf(key, key.lowercase()).map { sent ->
                    executor.submit(
                        Callable {
                            barrier.await(10, TimeUnit.SECONDS)
                            createRaw("""{"name":"Roadmap","key":"$sent"}""")
                        },
                    )
                }.map { it.get(30, TimeUnit.SECONDS) }

                assertThat(results.map { it.status }.sorted()).containsExactly(201, 409)
                val conflict = results.single { it.status == 409 }.assertProblem(ErrorCode.CONFLICT)
                assertThat(conflict.fieldErrors).containsExactly(mapOf("field" to "key", "code" to "ALREADY_USED"))
                val board = listBoards(token).single { it["key"] == key }
                assertThat(storedColumns(board.id)).isEqualTo(3)
            }
        } finally {
            executor.shutdownNow()
        }
        assertThat(storedBoards()).isEqualTo(5)
    }

    @Test
    fun `list is a wrapper object holding all of the user's boards`() {
        val first = createBoard(token, key = "AA")
        oneMinuteLater()
        val second = createBoard(token, key = "BB")

        assertThat(listBoards(token)).containsExactly(second - "columns", first - "columns")
    }

    @Test
    fun `list without boards is an empty array`() {
        val result = call(HttpMethod.GET, "/api/v1/boards", token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(mapOf("boards" to emptyList<Any>()))
    }

    @Test
    fun `column rename moves its board to the front of the list`() {
        val a = createBoard(token, key = "AA")
        oneMinuteLater()
        val b = createBoard(token, key = "BB")
        oneMinuteLater()

        patch("/api/v1/boards/${a.id}/columns/${a.columns[0].id}", token, """{"name":"Backlog"}""")

        assertThat(listBoards(token).map { it.id }).containsExactly(a.id, b.id)
    }

    @Test
    fun `column rename to the current name keeps the list order`() {
        val a = createBoard(token, key = "AA")
        oneMinuteLater()
        val b = createBoard(token, key = "BB")
        oneMinuteLater()

        patch("/api/v1/boards/${a.id}/columns/${a.columns[0].id}", token, """{"name":" To do "}""")

        assertThat(listBoards(token).map { it.id }).containsExactly(b.id, a.id)
    }

    @Test
    fun `boards with equal timestamps are listed by ID`() {
        val ids = listOf("AA", "BB", "CC", "DD", "EE", "FF").map { createBoard(token, key = it).id }

        assertThat(listBoards(token).map { it.id }).isEqualTo(ids.sorted())
    }

    @Test
    fun `get returns the columns in position order after a move`() {
        val board = createBoard(token)
        move(token, board.id, board.column("Done").id, 0)

        val result = call(HttpMethod.GET, "/api/v1/boards/${board.id}", token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().columns.namesAndPositions()).containsExactly("Done" to 0, "To do" to 1, "In progress" to 2)
    }

    @Test
    fun `uppercase board ID is accepted and returned lowercase`() {
        val board = createBoard(token)

        val result = call(HttpMethod.GET, "/api/v1/boards/${board.id.uppercase()}", token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().id).isEqualTo(board.id)
    }

    @Test
    fun `rename changes name and updatedAt and keeps key, createdAt and columns`() {
        val board = createBoard(token, "Roadmap", "CRD")
        val later = oneMinuteLater()

        val result = patchBoard(board.id, """{"name":" Platform "}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board + mapOf("name" to "Platform", "updatedAt" to later))
        assertThat(getBoard(token, board.id)).isEqualTo(result.json())
    }

    @Test
    fun `null name is REQUIRED and leaves the board unchanged`() {
        val board = createBoard(token)
        oneMinuteLater()

        assertThat(patchBoard(board.id, """{"name":null}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `whitespace-only name is REQUIRED`() {
        val board = createBoard(token)

        assertThat(patchBoard(board.id, """{"name":"   "}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "REQUIRED"))
    }

    @Test
    fun `key in the rename body is ignored`() {
        val board = createBoard(token, key = "CRD")

        val result = patchBoard(board.id, """{"name":"Platform","key":"NEW"}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()["name"]).isEqualTo("Platform")
        assertThat(result.json()["key"]).isEqualTo("CRD")
    }

    @Test
    fun `only a key in the body changes nothing, updatedAt included`() {
        val board = createBoard(token, key = "CRD")
        oneMinuteLater()

        val result = patchBoard(board.id, """{"key":"NEW"}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board)
    }

    @Test
    fun `same name with surrounding spaces keeps updatedAt`() {
        val board = createBoard(token, "Roadmap")
        oneMinuteLater()

        val result = patchBoard(board.id, """{"name":" Roadmap "}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board)
    }

    @Test
    fun `empty patch returns the unchanged board`() {
        val board = createBoard(token)
        oneMinuteLater()

        val result = patchBoard(board.id, "{}")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board)
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `case-only rename is a change`() {
        val board = createBoard(token, "roadmap")
        val later = oneMinuteLater()

        val result = patchBoard(board.id, """{"name":"Roadmap"}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()["name"]).isEqualTo("Roadmap")
        assertThat(result.json()["updatedAt"]).isEqualTo(later)
    }

    @Test
    fun `delete removes the board with its columns and returns 204 without a body`() {
        val gone = createBoard(token, key = "AA")
        val kept = createBoard(token, key = "BB")

        val result = delete("/api/v1/boards/${gone.id}", token)

        assertThat(result.status).isEqualTo(204)
        assertThat(result.body).isEqualTo("")
        assertThat(listBoards(token)).containsExactly(kept - "columns")
        assertThat(storedColumns(gone.id)).isEqualTo(0)
        assertThat(storedColumns(kept.id)).isEqualTo(3)
    }

    @Test
    fun `second delete of a board is NOT_FOUND`() {
        val board = createBoard(token)
        delete("/api/v1/boards/${board.id}", token)

        delete("/api/v1/boards/${board.id}", token).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `key is free again after its board was deleted`() {
        val board = createBoard(token, key = "CRD")
        delete("/api/v1/boards/${board.id}", token)

        assertThat(createBoard(token, key = "CRD").id).isNotEqualTo(board.id)
    }

    @Test
    fun `list shows only the user's own boards`() {
        createBoard(token)
        val bob = signIn("bob@example.com").token

        assertThat(listBoards(bob)).isEmpty()
    }

    @Test
    fun `another user's board is NOT_FOUND, identical to an ID that never existed`() {
        val board = createBoard(token)
        val bob = signIn("bob@example.com").token

        val foreign = call(HttpMethod.GET, "/api/v1/boards/${board.id}", bob)
        val unknown = call(HttpMethod.GET, "/api/v1/boards/${UUID.randomUUID()}", bob)

        foreign.assertProblem(ErrorCode.NOT_FOUND)
        assertThat(foreign.body).isEqualTo(unknown.body)
        assertThat(foreign.status).isEqualTo(unknown.status)
    }

    @Test
    fun `rename of another user's board is NOT_FOUND and changes nothing`() {
        val board = createBoard(token)
        val bob = signIn("bob@example.com").token
        oneMinuteLater()

        patchBoard(board.id, """{"name":"Mine"}""", bob).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `delete of another user's board is NOT_FOUND and keeps the board and its columns`() {
        val board = createBoard(token)
        val bob = signIn("bob@example.com").token

        delete("/api/v1/boards/${board.id}", bob).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `same key for two users is accepted`() {
        createBoard(token, key = "CRD")
        val bob = signIn("bob@example.com").token

        assertThat(createBoard(bob, key = "CRD")["key"]).isEqualTo("CRD")
    }

    @Test
    fun `malformed board ID is NOT_FOUND for get, rename and delete`() {
        call(HttpMethod.GET, "/api/v1/boards/abc", token).assertProblem(ErrorCode.NOT_FOUND)
        patchBoard("abc", """{"name":"Platform"}""").assertProblem(ErrorCode.NOT_FOUND)
        delete("/api/v1/boards/abc", token).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `own board ID without the leading zeros of a group is NOT_FOUND`() {
        val keys = ('A'..'Z').flatMap { first -> ('A'..'Z').map { second -> "$first$second" } }.iterator()
        val board = generateSequence { createBoard(token, key = keys.next()) }
            .first { board -> board.id.split('-').any { it.startsWith('0') } }
        val shortForm = board.id.split('-').joinToString("-") { it.trimStart('0').ifEmpty { "0" } }

        assertThat(UUID.fromString(shortForm).toString()).isEqualTo(board.id)
        call(HttpMethod.GET, "/api/v1/boards/$shortForm", token).assertProblem(ErrorCode.NOT_FOUND)
        patchBoard(shortForm, """{"name":"Platform"}""").assertProblem(ErrorCode.NOT_FOUND)
        delete("/api/v1/boards/$shortForm", token).assertProblem(ErrorCode.NOT_FOUND)
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `missing board wins over an invalid body`() {
        patchBoard(UUID.randomUUID().toString(), """{"name":"${"a".repeat(101)}"}""").assertProblem(ErrorCode.NOT_FOUND)
        patchBoard(UUID.randomUUID().toString(), """{"name":null}""").assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `unparseable body wins over a missing board`() {
        patchBoard(UUID.randomUUID().toString(), "{").assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `existing board with a too long name is TOO_LONG with max 100`() {
        val board = createBoard(token)

        assertThat(patchBoard(board.id, """{"name":"${"a".repeat(101)}"}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "TOO_LONG", "max" to 100))
    }

    @Test
    fun `token without a user record can create and list a board`() {
        val orphan = tokenWithoutUser()

        val board = createBoard(orphan)

        assertThat(listBoards(orphan)).containsExactly(board - "columns")
    }

    @Test
    fun `board endpoints without a token are UNAUTHENTICATED`() {
        val id = UUID.randomUUID()

        call(HttpMethod.GET, "/api/v1/boards").assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.GET, "/api/v1/boards/$id").assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.PATCH, "/api/v1/boards/$id", json = "{}").assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.DELETE, "/api/v1/boards/$id").assertProblem(ErrorCode.UNAUTHENTICATED)
    }
}

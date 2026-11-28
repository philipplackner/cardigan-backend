package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.util.UUID

class ColumnE2eTest : BoardApiE2eTest() {
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        token = signIn().token
    }

    private fun columnPath(
        board: Json,
        name: String,
    ) = "/api/v1/boards/${board.id}/columns/${board.column(name).id}"

    private fun bob(): String = signIn("bob@example.com").token

    @Test
    fun `column has exactly id, name, position, createdAt and updatedAt`() {
        val board = createBoard(token)

        val column = createColumn(token, board.id, "Review")

        assertThat(column.keys).isEqualTo(setOf("id", "name", "position", "createdAt", "updatedAt"))
        assertThat(column.id).matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
        assertThat(column - "id").isEqualTo(
            mapOf(
                "name" to "Review",
                "position" to 3,
                "createdAt" to "2026-11-15T09:15:30.123Z",
                "updatedAt" to "2026-11-15T09:15:30.123Z",
            ),
        )
        assertThat(getBoard(token, board.id).columns.last()).isEqualTo(column)
    }

    @Test
    fun `there is no column list endpoint`() {
        val board = createBoard(token)

        call(HttpMethod.GET, "/api/v1/boards/${board.id}/columns", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
        call(HttpMethod.GET, columnPath(board, "Done"), token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `new column is appended with a trimmed name`() {
        val board = createBoard(token)

        val result = post("/api/v1/boards/${board.id}/columns", token, """{"name":" Review "}""")

        assertThat(result.status).isEqualTo(201)
        assertThat(result.json()["name"]).isEqualTo("Review")
        assertThat(result.json()["position"]).isEqualTo(3)
        assertThat(board.columns.map { it.id }.contains(result.json().id)).isEqualTo(false)
    }

    @Test
    fun `first column of an empty board gets position 0`() {
        val board = createBoard(token)
        board.columns.forEach { delete("/api/v1/boards/${board.id}/columns/${it.id}", token) }

        assertThat(createColumn(token, board.id, "First")["position"]).isEqualTo(0)
    }

    @Test
    fun `position in the create body is ignored`() {
        val board = createBoard(token)

        val result = post("/api/v1/boards/${board.id}/columns", token, """{"name":"Review","position":0}""")

        assertThat(result.status).isEqualTo(201)
        assertThat(result.json()["position"]).isEqualTo(3)
    }

    @Test
    fun `missing name is REQUIRED and stores no column`() {
        val board = createBoard(token)

        assertThat(post("/api/v1/boards/${board.id}/columns", token, "{}").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `name of 30 code points is accepted and 31 is TOO_LONG with max 30`() {
        val board = createBoard(token)

        assertThat(createColumn(token, board.id, "😀".repeat(30))["position"]).isEqualTo(3)
        assertThat(
            post("/api/v1/boards/${board.id}/columns", token, """{"name":" ${"a".repeat(31)} "}""").singleFieldError(),
        ).isEqualTo(mapOf("field" to "name", "code" to "TOO_LONG", "max" to 30))
    }

    @Test
    fun `board with 200 columns takes another one at position 200`() {
        val board = createBoard(token)
        (3 until 200).forEach { createColumn(token, board.id, "Column $it") }

        assertThat(createColumn(token, board.id, "One more")["position"]).isEqualTo(200)
        assertThat(getBoard(token, board.id).columns.map { it["position"] }).isEqualTo((0..200).toList())
    }

    @Test
    fun `rename changes name and updatedAt and keeps position and createdAt`() {
        val board = createBoard(token)
        val review = createColumn(token, board.id, "Review")
        val later = oneMinuteLater()

        val result = patch("/api/v1/boards/${board.id}/columns/${review.id}", token, """{"name":"QA"}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(review + mapOf("name" to "QA", "updatedAt" to later))
    }

    @Test
    fun `null name is REQUIRED and leaves the column unchanged`() {
        val board = boardAbc(token)
        oneMinuteLater()

        assertThat(patch(columnPath(board, "B"), token, """{"name":null}""").singleFieldError())
            .isEqualTo(mapOf("field" to "name", "code" to "REQUIRED"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `position in the rename body is ignored`() {
        val board = boardAbc(token)

        val result = patch(columnPath(board, "C"), token, """{"name":"QA","position":0}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()["name"]).isEqualTo("QA")
        assertThat(result.json()["position"]).isEqualTo(2)
    }

    @Test
    fun `move to the front returns all columns in a wrapper object`() {
        val board = boardAbc(token)

        val result = move(token, board.id, board.column("C").id, 0)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().keys).isEqualTo(setOf("columns"))
        assertThat(result.json().columns.namesAndPositions()).containsExactly("C" to 0, "A" to 1, "B" to 2)
        assertThat(getBoard(token, board.id).columns).isEqualTo(result.json().columns)
    }

    @Test
    fun `move to the end`() {
        val board = boardAbc(token)

        val result = move(token, board.id, board.column("A").id, 2)

        assertThat(result.json().columns.namesAndPositions()).containsExactly("B" to 0, "C" to 1, "A" to 2)
    }

    @Test
    fun `missing or null position is REQUIRED and moves nothing`() {
        val board = boardAbc(token)

        assertThat(post(columnPath(board, "A") + "/move", token, "{}").singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "REQUIRED"))
        assertThat(move(token, board.id, board.column("A").id, "null").singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "REQUIRED"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `position of another type is MALFORMED_REQUEST`() {
        val board = boardAbc(token)

        move(token, board.id, board.column("A").id, "\"a\"").assertProblem(ErrorCode.MALFORMED_REQUEST)
        move(token, board.id, board.column("A").id, "1.5").assertProblem(ErrorCode.MALFORMED_REQUEST)
        move(token, board.id, board.column("A").id, "\"1\"").assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `position equal to the column count is OUT_OF_RANGE with 0 and n-1 and changes nothing`() {
        val board = boardAbc(token)
        oneMinuteLater()

        assertThat(move(token, board.id, board.column("A").id, 3).singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 2))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `negative position is OUT_OF_RANGE with min 0`() {
        val board = boardAbc(token)

        assertThat(move(token, board.id, board.column("A").id, -1).singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 2))
    }

    @Test
    fun `huge position is OUT_OF_RANGE, not MALFORMED_REQUEST`() {
        val board = boardAbc(token)

        assertThat(move(token, board.id, board.column("A").id, "99999999999999999999").singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 2))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `range follows the columns left after deletes`() {
        val board = boardAbc(token)
        val d = createColumn(token, board.id, "D")
        val e = createColumn(token, board.id, "E")
        delete("/api/v1/boards/${board.id}/columns/${d.id}", token)
        delete("/api/v1/boards/${board.id}/columns/${e.id}", token)

        assertThat(move(token, board.id, board.column("A").id, 4).singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 2))
    }

    @Test
    fun `only column can't move to position 1`() {
        val board = createBoard(token)
        board.columns.drop(1).forEach { delete("/api/v1/boards/${board.id}/columns/${it.id}", token) }

        assertThat(move(token, board.id, board.columns[0].id, 1).singleFieldError())
            .isEqualTo(mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 0))
    }

    @Test
    fun `delete of the middle column returns 204 and closes the gap without touching updatedAt`() {
        val board = boardAbc(token)
        oneMinuteLater()

        val result = delete(columnPath(board, "B"), token)

        assertThat(result.status).isEqualTo(204)
        assertThat(result.body).isEqualTo("")
        assertThat(getBoard(token, board.id).columns)
            .containsExactly(board.column("A"), board.column("C") + mapOf("position" to 1))
    }

    @Test
    fun `delete of the only column leaves an empty columns array`() {
        val board = createBoard(token)
        board.columns.drop(1).forEach { delete("/api/v1/boards/${board.id}/columns/${it.id}", token) }

        assertThat(delete("/api/v1/boards/${board.id}/columns/${board.columns[0].id}", token).status).isEqualTo(204)
        assertThat(getBoard(token, board.id)["columns"]).isEqualTo(emptyList<Any>())
    }

    @Test
    fun `second delete of a column is NOT_FOUND`() {
        val board = boardAbc(token)
        delete(columnPath(board, "B"), token)

        delete(columnPath(board, "B"), token).assertProblem(ErrorCode.NOT_FOUND)
        assertThat(getBoard(token, board.id).columns.namesAndPositions()).containsExactly("A" to 0, "C" to 1)
    }

    @Test
    fun `rename to the same name changes no updatedAt`() {
        val board = boardAbc(token)
        oneMinuteLater()

        val result = patch(columnPath(board, "B"), token, """{"name":" B "}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board.column("B"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `empty patch returns the unchanged column`() {
        val board = boardAbc(token)
        oneMinuteLater()

        val result = patch(columnPath(board, "B"), token, "{}")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(board.column("B"))
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `move to the current position changes nothing`() {
        val board = boardAbc(token)
        oneMinuteLater()

        val result = move(token, board.id, board.column("B").id, 1)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().columns).isEqualTo(board.columns)
        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `create sets the board's updatedAt to the column's createdAt`() {
        val board = createBoard(token)
        val later = oneMinuteLater()

        val column = createColumn(token, board.id, "Review")

        assertThat(column["createdAt"]).isEqualTo(later)
        assertThat(getBoard(token, board.id)["updatedAt"]).isEqualTo(later)
        assertThat(getBoard(token, board.id)["createdAt"]).isEqualTo(start)
    }

    @Test
    fun `move sets the moved column's and the board's updatedAt only`() {
        val board = boardAbc(token)
        val later = oneMinuteLater()

        move(token, board.id, board.column("C").id, 0)

        val after = getBoard(token, board.id)
        assertThat(after["updatedAt"]).isEqualTo(later)
        assertThat(after.columns).containsExactly(
            board.column("C") + mapOf("position" to 0, "updatedAt" to later),
            board.column("A") + mapOf("position" to 1),
            board.column("B") + mapOf("position" to 2),
        )
    }

    @Test
    fun `delete sets the board's updatedAt`() {
        val board = boardAbc(token)
        val later = oneMinuteLater()

        delete(columnPath(board, "B"), token)

        assertThat(getBoard(token, board.id)["updatedAt"]).isEqualTo(later)
    }

    @Test
    fun `create in another user's board is NOT_FOUND and changes nothing`() {
        val board = createBoard(token)
        oneMinuteLater()

        post("/api/v1/boards/${board.id}/columns", bob(), """{"name":"Review"}""").assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `rename of a column under another own board is NOT_FOUND`() {
        val x = boardAbc(token, "XX")
        val y = boardAbc(token, "YY")
        oneMinuteLater()

        patch(
            "/api/v1/boards/${y.id}/columns/${x.column("B").id}",
            token,
            """{"name":"X"}""",
        ).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, x.id)).isEqualTo(x)
        assertThat(getBoard(token, y.id)).isEqualTo(y)
    }

    @Test
    fun `move of another user's column through its own board is NOT_FOUND`() {
        val board = boardAbc(token)
        oneMinuteLater()

        move(bob(), board.id, board.column("C").id, 0).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, board.id)).isEqualTo(board)
    }

    @Test
    fun `delete of another user's column under an own board is NOT_FOUND`() {
        val annBoard = boardAbc(token)
        val bob = bob()
        val bobBoard = boardAbc(bob)
        oneMinuteLater()

        delete(
            "/api/v1/boards/${bobBoard.id}/columns/${annBoard.column("B").id}",
            bob,
        ).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, annBoard.id)).isEqualTo(annBoard)
        assertThat(getBoard(bob, bobBoard.id)).isEqualTo(bobBoard)
    }

    @Test
    fun `move of another user's column under an own board is NOT_FOUND`() {
        val annBoard = boardAbc(token)
        val bob = bob()
        val bobBoard = boardAbc(bob)
        oneMinuteLater()

        move(bob, bobBoard.id, annBoard.column("C").id, 0).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, annBoard.id)).isEqualTo(annBoard)
        assertThat(getBoard(bob, bobBoard.id)).isEqualTo(bobBoard)
    }

    @Test
    fun `rename of another user's column under an own board is NOT_FOUND`() {
        val annBoard = boardAbc(token)
        val bob = bob()
        val bobBoard = boardAbc(bob)
        oneMinuteLater()

        patch("/api/v1/boards/${bobBoard.id}/columns/${annBoard.column("B").id}", bob, """{"name":"X"}""")
            .assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, annBoard.id)).isEqualTo(annBoard)
    }

    @Test
    fun `delete of a column under another own board is NOT_FOUND and both boards keep their columns`() {
        val x = boardAbc(token, "XX")
        val y = boardAbc(token, "YY")
        oneMinuteLater()

        delete("/api/v1/boards/${y.id}/columns/${x.column("A").id}", token).assertProblem(ErrorCode.NOT_FOUND)

        assertThat(getBoard(token, x.id)).isEqualTo(x)
        assertThat(getBoard(token, y.id)).isEqualTo(y)
    }

    @Test
    fun `malformed column or board ID is NOT_FOUND`() {
        val board = boardAbc(token)

        delete("/api/v1/boards/${board.id}/columns/abc", token).assertProblem(ErrorCode.NOT_FOUND)
        patch(
            "/api/v1/boards/${board.id}/columns/1-2-3-4-5",
            token,
            """{"name":"X"}""",
        ).assertProblem(ErrorCode.NOT_FOUND)
        post("/api/v1/boards/abc/columns", token, """{"name":"X"}""").assertProblem(ErrorCode.NOT_FOUND)
        move(token, "abc", board.column("A").id, 0).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `missing column or board wins over an invalid body`() {
        val board = boardAbc(token)
        val unknown = UUID.randomUUID().toString()

        move(token, board.id, unknown, -1).assertProblem(ErrorCode.NOT_FOUND)
        post("/api/v1/boards/${board.id}/columns/$unknown/move", token, "{}").assertProblem(ErrorCode.NOT_FOUND)
        patch(
            "/api/v1/boards/${board.id}/columns/$unknown",
            token,
            """{"name":null}""",
        ).assertProblem(ErrorCode.NOT_FOUND)
        post("/api/v1/boards/$unknown/columns", token, "{}").assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `column of a deleted board is NOT_FOUND`() {
        val board = boardAbc(token)
        delete("/api/v1/boards/${board.id}", token)

        patch(columnPath(board, "A"), token, """{"name":"X"}""").assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `uppercase board and column IDs are accepted`() {
        val board = boardAbc(token)

        val result =
            patch(
                "/api/v1/boards/${board.id.uppercase()}/columns/${board.column("A").id.uppercase()}",
                token,
                """{"name":"X"}""",
            )

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().id).isEqualTo(board.column("A").id)
    }

    @Test
    fun `column endpoints without a token are UNAUTHENTICATED`() {
        val path = "/api/v1/boards/${UUID.randomUUID()}/columns"

        call(HttpMethod.POST, path, json = """{"name":"X"}""").assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.PATCH, "$path/${UUID.randomUUID()}", json = "{}").assertProblem(ErrorCode.UNAUTHENTICATED)
        call(
            HttpMethod.POST,
            "$path/${UUID.randomUUID()}/move",
            json = """{"position":0}""",
        ).assertProblem(ErrorCode.UNAUTHENTICATED)
        call(HttpMethod.DELETE, "$path/${UUID.randomUUID()}").assertProblem(ErrorCode.UNAUTHENTICATED)
    }
}

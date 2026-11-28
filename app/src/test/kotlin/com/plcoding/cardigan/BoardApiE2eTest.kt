package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration

typealias Json = Map<String, Any?>

/** Requests and readers shared by the board and column E2E tests. */
abstract class BoardApiE2eTest : E2eTest() {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected val start: String = START.toString()

    /** Moves the clock one minute forward and returns the new time as it appears in responses. */
    protected fun oneMinuteLater(): String {
        clock.advanceBy(Duration.ofMinutes(1))
        return clock.instant().toString()
    }

    protected fun post(
        path: String,
        token: String,
        json: String,
    ) = call(HttpMethod.POST, path, token, json = json)

    protected fun patch(
        path: String,
        token: String,
        json: String,
    ) = call(HttpMethod.PATCH, path, token, json = json)

    protected fun delete(
        path: String,
        token: String,
    ) = call(HttpMethod.DELETE, path, token)

    protected fun createBoard(
        token: String,
        name: String = "Roadmap",
        key: String = "CRD",
    ): Json {
        val result = post("/api/v1/boards", token, """{"name":"$name","key":"$key"}""")
        assertThat(result.status, "create board").isEqualTo(201)
        return result.json()
    }

    protected fun getBoard(
        token: String,
        boardId: String,
    ): Json {
        val result = call(HttpMethod.GET, "/api/v1/boards/$boardId", token)
        assertThat(result.status, "get board").isEqualTo(200)
        return result.json()
    }

    @Suppress("UNCHECKED_CAST")
    protected fun listBoards(token: String): List<Json> {
        val result = call(HttpMethod.GET, "/api/v1/boards", token)
        assertThat(result.status, "list boards").isEqualTo(200)
        assertThat(result.json().keys, "list fields").isEqualTo(setOf("boards"))
        return result.json()["boards"] as List<Json>
    }

    protected fun createColumn(
        token: String,
        boardId: String,
        name: String,
    ): Json {
        val result = post("/api/v1/boards/$boardId/columns", token, """{"name":"$name"}""")
        assertThat(result.status, "create column").isEqualTo(201)
        return result.json()
    }

    protected fun move(
        token: String,
        boardId: String,
        columnId: String,
        position: Any,
    ) = post("/api/v1/boards/$boardId/columns/$columnId/move", token, """{"position":$position}""")

    /** A board whose columns are named A, B and C, each renamed at the board's creation time. */
    protected fun boardAbc(
        token: String,
        key: String = "CRD",
    ): Json {
        val board = createBoard(token, key = key)
        board.columns.zip(listOf("A", "B", "C")).forEach { (column, name) ->
            val result = patch("/api/v1/boards/${board.id}/columns/${column.id}", token, """{"name":"$name"}""")
            assertThat(result.status, "rename column").isEqualTo(200)
        }
        return getBoard(token, board.id)
    }

    protected fun storedColumns(boardId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM board_service.columns WHERE board_id = ?::uuid",
            Int::class.java,
            boardId,
        )!!

    protected fun storedBoards(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM board_service.boards", Int::class.java)!!

    protected val Json.id: String get() = this["id"] as String

    @Suppress("UNCHECKED_CAST")
    protected val Json.columns: List<Json> get() = this["columns"] as List<Json>

    protected fun Json.column(name: String): Json = columns.single { it["name"] == name }

    protected fun List<Json>.namesAndPositions(): List<Pair<Any?, Any?>> = map { it["name"] to it["position"] }

    protected fun HttpResult.singleFieldError(): Json = assertProblem(ErrorCode.VALIDATION_FAILED).fieldErrors.single()
}

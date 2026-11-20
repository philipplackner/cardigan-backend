package com.plcoding.cardigan.board.api.controller

import com.plcoding.cardigan.board.api.dto.BoardDto
import com.plcoding.cardigan.board.api.dto.BoardListDto
import com.plcoding.cardigan.board.api.dto.CreateBoardRequest
import com.plcoding.cardigan.board.api.dto.UpdateBoardRequest
import com.plcoding.cardigan.board.api.mapper.toBoardDto
import com.plcoding.cardigan.board.api.mapper.toBoardKey
import com.plcoding.cardigan.board.api.mapper.toBoardListDto
import com.plcoding.cardigan.board.api.mapper.toBoardName
import com.plcoding.cardigan.board.service.BoardService
import com.plcoding.cardigan.common.api.openapi.ApiErrors
import com.plcoding.cardigan.common.api.requestUserId
import com.plcoding.cardigan.common.api.validation.RequestValidator
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/boards")
@Tag(name = "Boards")
class BoardController(
    private val boardService: BoardService,
    private val requestValidator: RequestValidator,
) {

    @GetMapping
    @Operation(operationId = "listBoards", summary = "List the signed-in user's boards, without their columns")
    fun listBoards(): BoardListDto = boardService.list(requestUserId).toBoardListDto()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBoard", summary = "Create a board with its three default columns")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(409, ErrorCode.CONFLICT)
    fun createBoard(@Valid @RequestBody request: CreateBoardRequest): BoardDto =
        boardService.create(requestUserId, request.toBoardName(), request.toBoardKey()).toBoardDto()

    @GetMapping("/{boardId}")
    @Operation(operationId = "getBoard", summary = "Get a board with its columns")
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun getBoard(@PathVariable boardId: UUID): BoardDto = boardService.get(requestUserId, boardId).toBoardDto()

    @PatchMapping("/{boardId}")
    @Operation(operationId = "updateBoard", summary = "Rename a board")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun updateBoard(@RequestBody request: UpdateBoardRequest, @PathVariable boardId: UUID): BoardDto {
        boardService.get(requestUserId, boardId)
        requestValidator.validate(request)
        return boardService.rename(requestUserId, boardId, request.toBoardName()).toBoardDto()
    }

    @DeleteMapping("/{boardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteBoard", summary = "Delete a board with its columns")
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun deleteBoard(@PathVariable boardId: UUID) {
        boardService.delete(requestUserId, boardId)
    }
}

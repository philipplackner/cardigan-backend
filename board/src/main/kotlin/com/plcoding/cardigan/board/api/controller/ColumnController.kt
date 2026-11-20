package com.plcoding.cardigan.board.api.controller

import com.plcoding.cardigan.board.api.dto.ColumnDto
import com.plcoding.cardigan.board.api.dto.ColumnListDto
import com.plcoding.cardigan.board.api.dto.CreateColumnRequest
import com.plcoding.cardigan.board.api.dto.MoveColumnRequest
import com.plcoding.cardigan.board.api.dto.UpdateColumnRequest
import com.plcoding.cardigan.board.api.mapper.toColumnDto
import com.plcoding.cardigan.board.api.mapper.toColumnListDto
import com.plcoding.cardigan.board.api.mapper.toColumnName
import com.plcoding.cardigan.board.api.mapper.toPosition
import com.plcoding.cardigan.board.service.BoardService
import com.plcoding.cardigan.board.service.ColumnService
import com.plcoding.cardigan.common.api.openapi.ApiErrors
import com.plcoding.cardigan.common.api.requestUserId
import com.plcoding.cardigan.common.api.validation.RequestValidator
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Columns are read through their board (`getBoard`), so there is no GET mapping here. */
@RestController
@RequestMapping("/api/v1/boards/{boardId}/columns")
@Tag(name = "Columns")
class ColumnController(
    private val boardService: BoardService,
    private val columnService: ColumnService,
    private val requestValidator: RequestValidator,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createColumn", summary = "Add a column at the end of a board")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun createColumn(@RequestBody request: CreateColumnRequest, @PathVariable boardId: UUID): ColumnDto {
        boardService.get(requestUserId, boardId)
        requestValidator.validate(request)
        return columnService.create(requestUserId, boardId, request.toColumnName()).toColumnDto()
    }

    @PatchMapping("/{columnId}")
    @Operation(operationId = "updateColumn", summary = "Rename a column")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun updateColumn(
        @RequestBody request: UpdateColumnRequest,
        @PathVariable boardId: UUID,
        @PathVariable columnId: UUID,
    ): ColumnDto {
        columnService.get(requestUserId, boardId, columnId)
        requestValidator.validate(request)
        return columnService.rename(requestUserId, boardId, columnId, request.toColumnName()).toColumnDto()
    }

    @PostMapping("/{columnId}/move")
    @Operation(operationId = "moveColumn", summary = "Move a column to a position and get all columns of the board")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun moveColumn(
        @RequestBody request: MoveColumnRequest,
        @PathVariable boardId: UUID,
        @PathVariable columnId: UUID,
    ): ColumnListDto {
        columnService.get(requestUserId, boardId, columnId)
        requestValidator.validate(request)
        return columnService.move(requestUserId, boardId, columnId, request.toPosition()).toColumnListDto()
    }

    @DeleteMapping("/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteColumn", summary = "Delete a column")
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun deleteColumn(@PathVariable boardId: UUID, @PathVariable columnId: UUID) {
        columnService.delete(requestUserId, boardId, columnId)
    }
}

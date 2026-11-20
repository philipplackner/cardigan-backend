package com.plcoding.cardigan.board.api.controller

import com.plcoding.cardigan.board.api.dto.CreateLabelRequest
import com.plcoding.cardigan.board.api.dto.LabelDto
import com.plcoding.cardigan.board.api.dto.LabelListDto
import com.plcoding.cardigan.board.api.dto.UpdateLabelRequest
import com.plcoding.cardigan.board.api.mapper.toLabelChanges
import com.plcoding.cardigan.board.api.mapper.toLabelColor
import com.plcoding.cardigan.board.api.mapper.toLabelDto
import com.plcoding.cardigan.board.api.mapper.toLabelListDto
import com.plcoding.cardigan.board.api.mapper.toLabelName
import com.plcoding.cardigan.board.service.LabelService
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
@RequestMapping("/api/v1/labels")
@Tag(name = "Labels")
class LabelController(
    private val labelService: LabelService,
    private val requestValidator: RequestValidator,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createLabel", summary = "Create a label")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(409, ErrorCode.CONFLICT)
    fun createLabel(@Valid @RequestBody request: CreateLabelRequest): LabelDto =
        labelService.create(requestUserId, request.toLabelName(), request.toLabelColor()).toLabelDto()

    @GetMapping
    @Operation(operationId = "listLabels", summary = "List the signed-in user's labels")
    fun listLabels(): LabelListDto = labelService.list(requestUserId).toLabelListDto()

    @PatchMapping("/{labelId}")
    @Operation(operationId = "updateLabel", summary = "Rename or recolor a label")
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    @ApiErrors(409, ErrorCode.CONFLICT)
    fun updateLabel(@RequestBody request: UpdateLabelRequest, @PathVariable labelId: UUID): LabelDto {
        val ownerId = requestUserId
        labelService.get(ownerId, labelId)
        requestValidator.validate(request)
        return labelService.update(ownerId, labelId, request.toLabelChanges()).toLabelDto()
    }

    @DeleteMapping("/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteLabel", summary = "Delete a label")
    @ApiErrors(404, ErrorCode.NOT_FOUND)
    fun deleteLabel(@PathVariable labelId: UUID) {
        labelService.delete(requestUserId, labelId)
    }
}

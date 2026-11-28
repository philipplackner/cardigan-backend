package com.plcoding.cardigan.common.api.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.plcoding.cardigan.common.api.validation.PatchRequest
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidRange
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.common.api.validation.ValidUuid
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
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
    fun createWidget(
        @RequestBody request: CreateWidgetRequest,
    ): WidgetDto = error("not called")

    @PatchMapping("/api/v1/widgets/{id}")
    @Operation(operationId = "updateWidget")
    fun updateWidget(
        @RequestBody request: UpdateWidgetRequest,
        @PathVariable id: UUID,
    ): WidgetDto = error("not called")

    @GetMapping("/api/v1/widgets/{id}")
    @Operation(operationId = "getWidget")
    fun getWidget(
        @PathVariable id: UUID,
    ): WidgetDto = error("not called")

    @GetMapping("/api/v1/public-widgets")
    @Operation(operationId = "listPublicWidgets")
    fun listPublicWidgets(): List<WidgetDto> = error("not called")
}

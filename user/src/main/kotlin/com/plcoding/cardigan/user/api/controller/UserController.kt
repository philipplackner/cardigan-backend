package com.plcoding.cardigan.user.api.controller

import com.plcoding.cardigan.common.api.requestUserId
import com.plcoding.cardigan.user.api.dto.CurrentUserDto
import com.plcoding.cardigan.user.api.mapper.toCurrentUserDto
import com.plcoding.cardigan.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/me")
    @Operation(operationId = "getCurrentUser", summary = "Get the signed-in user")
    fun getCurrentUser(): CurrentUserDto = userService.getCurrentUser(requestUserId).toCurrentUserDto()
}

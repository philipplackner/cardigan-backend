package com.plcoding.cardigan.board.api.controller

import com.plcoding.cardigan.common.api.validation.RequestValidator
import jakarta.validation.Validation
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

/** A [RequestValidator] over plain Bean Validation, as Spring would build it, without a context. */
internal val plainRequestValidator: RequestValidator by lazy {
    RequestValidator(Validation.buildDefaultValidatorFactory().validator)
}

/** Makes [userId] the request user, the way the bearer token filter does. */
internal fun signIn(userId: UUID) {
    val jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(userId.toString()).build()
    SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
}

internal fun signOut() {
    SecurityContextHolder.clearContext()
}

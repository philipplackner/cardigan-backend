package com.plcoding.cardigan.common.api

import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

/**
 * The ID of the user making the current request, taken from the access token's `sub`. There is no
 * database lookup, so the user may have no record.
 */
val requestUserId: UUID
    get() {
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw UnauthenticatedException("No authenticated request")
        return UUID.fromString(authentication.name)
    }

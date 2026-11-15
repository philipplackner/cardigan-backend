package com.plcoding.cardigan.common.infra.security

import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import com.plcoding.cardigan.common.api.web.isApiRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.stereotype.Component

/**
 * Reads the bearer token only on protected API routes. On public routes and outside `/api/v1` the
 * `Authorization` header is ignored, so a stale or broken token never causes a 401 there.
 */
@Component
class ApiBearerTokenResolver(
    private val publicRoutes: PublicRouteRegistry,
) : BearerTokenResolver {

    private val delegate = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? =
        if (request.isApiRequest() && !publicRoutes.isPublic(request)) delegate.resolve(request) else null
}

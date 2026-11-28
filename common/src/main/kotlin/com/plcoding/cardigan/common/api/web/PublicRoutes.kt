package com.plcoding.cardigan.common.api.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

/** A route under `/api/v1` that needs no access token, given by method and exact path. */
data class PublicRoute(
    val method: HttpMethod,
    val path: String,
)

/**
 * The public routes a module contributes. Each module registers its own bean. The security chain,
 * the bearer token resolver and the OpenAPI document all read the merged list.
 */
class PublicRoutes(
    val routes: List<PublicRoute>,
) {
    constructor(vararg routes: PublicRoute) : this(routes.toList())
}

/** All [PublicRoutes] beans merged into one list. */
@Component
class PublicRouteRegistry(
    publicRoutes: ObjectProvider<PublicRoutes>,
) {
    val routes: List<PublicRoute> by lazy {
        publicRoutes.orderedStream().toList().flatMap { it.routes }
    }

    fun isPublic(request: HttpServletRequest): Boolean =
        PublicRoute(HttpMethod.valueOf(request.method), request.pathWithinApplication) in routes
}

const val API_BASE_PATH = "/api/v1"

val HttpServletRequest.pathWithinApplication: String
    get() = requestURI.removePrefix(contextPath)

fun HttpServletRequest.isApiRequest(): Boolean {
    val path = pathWithinApplication
    return path == API_BASE_PATH || path.startsWith("$API_BASE_PATH/")
}

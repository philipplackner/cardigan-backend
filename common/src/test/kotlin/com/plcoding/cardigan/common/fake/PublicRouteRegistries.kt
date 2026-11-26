package com.plcoding.cardigan.common.fake

import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import com.plcoding.cardigan.common.api.web.PublicRoutes

/** A registry over the given module beans, in bean order. */
fun publicRouteRegistryOf(vararg publicRoutes: PublicRoutes) = PublicRouteRegistry(objectProviderOf(*publicRoutes))

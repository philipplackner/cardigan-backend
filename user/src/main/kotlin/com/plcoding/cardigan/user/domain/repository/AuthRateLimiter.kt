package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.AuthAction

interface AuthRateLimiter {

    /**
     * Counts one request for [email], which must already be normalized and lowercased. Throws
     * `AuthRateLimitedException` when the email has no request left for [action].
     */
    fun check(action: AuthAction, email: String)
}

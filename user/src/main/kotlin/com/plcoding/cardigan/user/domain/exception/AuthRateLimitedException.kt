package com.plcoding.cardigan.user.domain.exception

import com.plcoding.cardigan.common.domain.exception.RateLimitedException
import com.plcoding.cardigan.user.domain.model.AuthAction

class AuthRateLimitedException(
    action: AuthAction,
    retryAfterSeconds: Long,
) : RateLimitedException(retryAfterSeconds, "Too many $action requests for one email")

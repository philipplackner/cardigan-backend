package com.plcoding.cardigan.user.domain.exception

import com.plcoding.cardigan.common.domain.exception.RateLimitedException

/** No password hashing slot became free in time. Answered like any other rate limit. */
class PasswordHashingBusyException(
    retryAfterSeconds: Long,
) : RateLimitedException(retryAfterSeconds, "Password hashing is at capacity")

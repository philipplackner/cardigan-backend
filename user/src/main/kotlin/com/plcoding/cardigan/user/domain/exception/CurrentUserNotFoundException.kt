package com.plcoding.cardigan.user.domain.exception

import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import java.util.UUID

/** A valid token names a user with no record, for example after a dev database wipe. */
class CurrentUserNotFoundException(
    userId: UUID,
) : UnauthenticatedException("No user record for $userId")

package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.RefreshToken
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository {

    fun save(refreshToken: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * Marks the token as rotated unless it already is, in one atomic step. Of several concurrent
     * calls for one token, exactly one returns true.
     */
    fun markRotated(id: UUID, at: Instant): Boolean
}

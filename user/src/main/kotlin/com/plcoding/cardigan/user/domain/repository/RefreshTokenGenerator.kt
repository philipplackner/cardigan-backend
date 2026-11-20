package com.plcoding.cardigan.user.domain.repository

/** Creates the value of a new refresh token: unguessable, with at least 256 bits of randomness. */
interface RefreshTokenGenerator {

    fun generate(): String
}

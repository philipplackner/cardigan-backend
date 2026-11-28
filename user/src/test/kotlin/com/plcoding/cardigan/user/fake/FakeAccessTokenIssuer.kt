package com.plcoding.cardigan.user.fake

import com.plcoding.cardigan.common.domain.security.AccessTokenIssuer
import com.plcoding.cardigan.common.domain.security.IssuedAccessToken
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FakeAccessTokenIssuer(
    private val clock: Clock,
) : AccessTokenIssuer {
    val issued = CopyOnWriteArrayList<Pair<UUID, IssuedAccessToken>>()

    override fun issue(userId: UUID): IssuedAccessToken {
        val token =
            IssuedAccessToken(
                token = "access-token-${issued.size + 1}-$userId",
                expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(15)),
            )
        issued += userId to token
        return token
    }
}

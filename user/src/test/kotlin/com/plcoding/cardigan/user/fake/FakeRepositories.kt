package com.plcoding.cardigan.user.fake

import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.RefreshTokenRepository
import com.plcoding.cardigan.user.domain.repository.SessionRepository
import com.plcoding.cardigan.user.domain.repository.UserRepository
import java.time.Instant
import java.util.UUID

class FakeUserRepository : UserRepository {

    val users = linkedMapOf<UUID, User>()
    var emailLookups = 0

    /** When set, `existsByEmail` answers false, like a check that ran before another registration committed. */
    var existenceCheckSeesNothing = false

    override fun findById(id: UUID): User? = users[id]

    override fun findByEmail(email: String): User? {
        emailLookups++
        return users.values.firstOrNull { it.email == email }
    }

    override fun existsByEmail(email: String): Boolean =
        !existenceCheckSeesNothing && users.values.any { it.email == email }

    override fun save(user: User): User {
        if (users.values.any { it.email == user.email && it.id != user.id }) throw EmailAlreadyUsedException()
        users[user.id] = user
        return user
    }
}

class FakeSessionRepository : SessionRepository {

    val sessions = linkedMapOf<UUID, Session>()

    val active: List<Session> get() = sessions.values.filterNot { it.isRevoked }

    override fun save(session: Session): Session {
        sessions[session.id] = session
        return session
    }

    override fun findById(id: UUID): Session? = sessions[id]

    override fun revoke(id: UUID, at: Instant): Boolean {
        val session = sessions[id]?.takeUnless { it.isRevoked } ?: return false
        sessions[id] = session.copy(revokedAt = at)
        return true
    }
}

class FakeRefreshTokenRepository : RefreshTokenRepository {

    val tokens = linkedMapOf<UUID, RefreshToken>()

    /** When set, the next claim fails as if another request rotated the token first. */
    var nextClaimIsLost = false

    fun ofSession(sessionId: UUID): List<RefreshToken> = tokens.values.filter { it.sessionId == sessionId }

    override fun save(refreshToken: RefreshToken): RefreshToken {
        check(tokens.values.none { it.tokenHash == refreshToken.tokenHash && it.id != refreshToken.id }) {
            "Duplicate token hash"
        }
        tokens[refreshToken.id] = refreshToken
        return refreshToken
    }

    override fun findByTokenHash(tokenHash: String): RefreshToken? = tokens.values.firstOrNull { it.tokenHash == tokenHash }

    override fun markRotated(id: UUID, at: Instant): Boolean {
        val token = tokens[id]?.takeUnless { it.isRotated } ?: return false
        if (nextClaimIsLost) {
            nextClaimIsLost = false
            tokens[id] = token.copy(rotatedAt = at)
            return false
        }
        tokens[id] = token.copy(rotatedAt = at)
        return true
    }
}
